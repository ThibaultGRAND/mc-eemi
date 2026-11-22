package fr.eemicraft.tonte;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tonte Royale d'EEMICRAFT (famille Wool Games), mini-jeu maison.
 * Chaque joueur est un mouton dont la toison (5 couches de laine) sert de vies. Un coup de cisailles ennemi
 * retire une couche et donne des blocs de laine au tondeur : c'est la seule ressource de construction, pour
 * jeter des ponts entre les îles flottantes. Un mouton tondu est éliminé au coup ennemi suivant ; on regagne
 * des couches en broutant l'herbe (rare sur les îles de départ, abondante sur le pré central). Toutes les
 * 2 minutes, un berger apparaît : le premier qui le touche reçoit un chien de garde pendant 30 s.
 *
 * Plusieurs parties en même temps : la partie 1 joue dans le monde d'origine (« tonteroyale », construit par
 * le plugin), les parties n ≥ 2 dans des copies « tonteroyale_n » faites depuis le modèle propre
 * « tonteroyale_modele ». Toute la logique d'état est dans {@link Game}.
 */
public final class EemiTonte extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§dTonte Royale§8] §7";
    /** Centre des îles de départ (aux 4 coins) et des îlots. */
    private static final int CORNER = 32, DIAG = 17, AXIS = 25;
    /** Demi-côté de la zone de jeu (tout ce qui est dedans est effacé puis reconstruit). */
    private static final int HALF = 46;

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum TeamColor {
        ROUGE("Rouge", NamedTextColor.RED, Color.fromRGB(0xB02E26), DyeColor.RED, Material.RED_WOOL,
                Material.RED_STAINED_GLASS, "red_terracotta", -1, -1),
        BLEU("Bleu", NamedTextColor.BLUE, Color.fromRGB(0x3C44AA), DyeColor.BLUE, Material.BLUE_WOOL,
                Material.BLUE_STAINED_GLASS, "blue_terracotta", 1, 1),
        VERT("Vert", NamedTextColor.GREEN, Color.fromRGB(0x80C71F), DyeColor.LIME, Material.LIME_WOOL,
                Material.LIME_STAINED_GLASS, "lime_terracotta", 1, -1),
        JAUNE("Jaune", NamedTextColor.YELLOW, Color.fromRGB(0xFED83D), DyeColor.YELLOW, Material.YELLOW_WOOL,
                Material.YELLOW_STAINED_GLASS, "yellow_terracotta", -1, 1);

        final String label;
        final NamedTextColor color;
        final Color leather;
        final DyeColor dye;
        final Material wool;
        final Material glass;
        final String terracotta;
        /** Coin de l'île de départ : signe de x et de z. */
        final int sx, sz;

        TeamColor(String label, NamedTextColor color, Color leather, DyeColor dye, Material wool, Material glass,
                  String terracotta, int sx, int sz) {
            this.label = label;
            this.color = color;
            this.leather = leather;
            this.dye = dye;
            this.wool = wool;
            this.glass = glass;
            this.terracotta = terracotta;
            this.sx = sx;
            this.sz = sz;
        }

        String key() {
            return name().toLowerCase();
        }

        /** Nom accordé avec « équipe » (féminin) : Rouge, Bleue, Verte, Jaune. */
        String fem() {
            return label.equals("Bleu") ? "Bleue" : label.equals("Vert") ? "Verte" : label;
        }
    }

    /** Chien de garde gagné auprès du berger. */
    private record Dog(UUID owner, TeamColor team, long until) {}

    private record Op(int x, int y, int z, BlockData data) {}

    private NamespacedKey kitKey;
    /** Nom du monde de la partie 1 (base des copies). */
    private String base;
    private int cy, voidY;
    private final Map<String, BlockData> dataCache = new HashMap<>();
    private final Random random = new Random();
    /** Vrai pendant que le plugin fait apparaître le berger ou un chien (voir onSpawnForce). */
    private boolean spawningOurs;

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
        }, 2L, 2L);
        // statut initial pour EemiCore (une fois tous les plugins actives)
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Game g : games.values()) {
            g.removeBerger();
            g.removeDogs();
            for (Player p : online(g.players)) {
                g.resetBoard(p);
                if (g.inWorld(p)) sendToHub(p);
            }
            for (Player p : online(g.spectators)) if (g.inWorld(p)) sendToHub(p);
        }
        // les copies et le modèle sont refaits au prochain démarrage
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "tonteroyale");
        cy = cfg("center-y", 100);
        voidY = cfg("void-y", 85);
        for (Game g : games.values()) if (g.world != null) g.attach(g.world);
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private int maxPlayers() {
        return cfg("max-players", 16);
    }

    private String modelName() {
        return base + "_modele";
    }

    /** Charge le monde de la partie 1, ou le crée vide (comme EemiDuels) s'il n'existe pas. */
    private World setupWorld() {
        World w = Bukkit.getWorld(base);
        if (w == null) {
            w = new WorldCreator(base).generator(new VoidGenerator(cfg("lobby-y", 140) + 1)).createWorld();
            if (w != null) getLogger().info("Monde " + base + " cree (vide).");
        }
        if (w == null) getLogger().severe("Impossible de charger ou créer le monde " + base + " : Tonte Royale désactivée.");
        return w;
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

    /** Jour fixe, pas de mobs naturels, herbe qui ne repousse pas, pas de régénération naturelle. */
    private void prepareWorld(World w) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        w.setGameRule(GameRules.FALL_DAMAGE, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.LOCATOR_BAR, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setSpawnLocation(new Location(w, 0.5, cfg("lobby-y", 140) + 1, 0.5));
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private double cfgD(String key, double def) {
        return getConfig().getDouble(key, def);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
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
                prepareWorld(w);
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
            g.attach(w);
            World hub = hubWorld();
            for (Player q : online(new LinkedHashSet<>(g.pending))) {
                g.pending.remove(q.getUniqueId());
                // parti ailleurs entre-temps (autre jeu) : on ne l'emmène pas
                if (hub != null && !q.getWorld().equals(hub)) {
                    q.sendMessage(PREFIX + "§7Ta partie de Tonte Royale est prête, mais tu es ailleurs : inscription annulée.");
                    continue;
                }
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
        g.removeBerger();
        g.removeDogs();
        for (Player p : online(g.players)) g.resetBoard(p);
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
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    private static String clock(int s) {
        s = Math.max(0, s);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    /** Nombre d'équipes (2 à 4) : équilibrées si possible, taille la plus proche de team-size. */
    private int teamCount(int n) {
        int best = 2;
        double bestScore = Double.MAX_VALUE;
        double target = Math.max(1, cfg("team-size", 2));
        for (int t = 2; t <= Math.min(4, n); t++) {
            double score = (n % t == 0 ? 0 : 10) + Math.abs((double) n / t - target);
            if (score < bestScore) { bestScore = score; best = t; }
        }
        return best;
    }

    /** Places de départ : 4 par île, côté intérieur (la grange est vers l'extérieur). */
    private static final int[][] SLOTS = {{-1, -1}, {-2, -5}, {-5, -2}, {-5, -5}};

    // ================================================================== une partie

    /** Une partie de Tonte Royale, dans son propre monde (origine pour n = 1, copie sinon). */
    final class Game {
        final int index;
        World world;
        Location lobby, specPoint;
        final List<Location> bergerPoints = new ArrayList<>();

        final Set<UUID> players = new LinkedHashSet<>();
        /** Inscrits qui attendent au hub que la copie soit prête. */
        final Set<UUID> pending = new LinkedHashSet<>();
        final Map<UUID, TeamColor> teams = new HashMap<>();
        final List<TeamColor> inGameTeams = new ArrayList<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Map<UUID, Integer> layers = new HashMap<>();
        final Map<UUID, Integer> collected = new HashMap<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        /** Dernière tonte subie (invulnérabilité courte). */
        final Map<UUID, Long> lastShorn = new HashMap<>();
        final Map<UUID, Long> lastSelfShear = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        /** Broutage en cours : bloc d'herbe visé et progression (ticks). */
        final Map<UUID, Block> grazeBlock = new HashMap<>();
        final Map<UUID, Integer> grazeProgress = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        final Map<UUID, Dog> dogs = new HashMap<>();
        /** Blocs posés par les joueurs pendant la partie : les seuls qu'on peut casser. */
        final Set<Long> placed = new HashSet<>();
        final List<Block> cages = new ArrayList<>();
        /** Spectateurs (non inscrits) de la partie en cours. */
        final Set<UUID> spectators = new LinkedHashSet<>();

        State state = State.WAITING;
        int timer;
        int cageTimer;
        int bergerTimer;
        UUID berger;
        boolean building;
        int tickCount;
        /** Secondes passées vide (copies seulement : détruite au bout d'un moment). */
        int idle;

        Game(int index) {
            this.index = index;
        }

        void attach(World w) {
            world = w;
            prepareWorld(w);
            lobby = new Location(w, 0.5, cfg("lobby-y", 140) + 1, 0.5);
            specPoint = new Location(w, 0.5, cy + 15, 0.5, 0, 45);
            bergerPoints.clear();
            bergerPoints.add(new Location(w, 0.5, cy + 1, 0.5));
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sz = -1; sz <= 1; sz += 2) bergerPoints.add(new Location(w, sx * DIAG + 0.5, cy + 1, sz * DIAG + 0.5));
            for (int s = -1; s <= 1; s += 2) {
                bergerPoints.add(new Location(w, s * AXIS + 0.5, cy + 1, 0.5));
                bergerPoints.add(new Location(w, 0.5, cy + 1, s * AXIS + 0.5));
            }
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

        boolean here(Location l) {
            return world != null && l.getWorld() != null && l.getWorld().equals(world);
        }

        List<Player> inWorld() {
            List<Player> l = new ArrayList<>();
            for (Player p : online(players)) if (inWorld(p)) l.add(p);
            return l;
        }

        /** Messages aux inscrits présents dans le monde de la partie. */
        void broadcast(String msg) {
            for (Player p : inWorld()) p.sendMessage(PREFIX + msg);
        }

        // -------------------------------------------------------------- boucles

        /** Une fois par seconde. */
        void tick() {
            if (world == null) return;
            switch (state) {
                case WAITING -> {
                    // la partie 1 n'est pas lancée pendant la copie du modèle (la map doit rester propre)
                    if (!building && !(index == 1 && modelBusy) && inWorld().size() >= Math.max(2, cfg("min-players", 2))) {
                        state = State.STARTING;
                        timer = cfg("countdown", 20);
                        broadcast("§eAssez de moutons : la partie commence dans §6" + timer + " s§e.");
                    }
                }
                case STARTING -> {
                    int n = inWorld().size();
                    if (n < Math.max(2, cfg("min-players", 2)) || building) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                    } else {
                        if (n >= maxPlayers()) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0)
                            for (Player p : inWorld()) {
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> {
                    if (cageTimer > 0) {
                        if (--cageTimer == 0) openCages();
                        else for (Player p : online(alive)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    } else {
                        timer--;
                        if (--bergerTimer <= 0) {
                            spawnBerger();
                            bergerTimer = cfg("berger-interval", 120);
                        }
                        dogTick();
                        if (timer <= 0) timeUp();
                    }
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (games.get(index) != this) return; // détruite pendant ce tick
            for (Player p : inWorld()) {
                updateSidebar(p);
                if (state == State.PLAYING && alive.contains(p.getUniqueId()) && !grazeProgress.containsKey(p.getUniqueId()))
                    p.sendActionBar(fleeceBar(p));
            }
            // copie vide en attente : supprimée au bout de 10 s
            if (index > 1 && state == State.WAITING && !building && size() == 0 && spectators.isEmpty()) {
                if (++idle >= 10) destroyGame(this);
            } else idle = 0;
        }

        /** Toutes les 2 ticks : broutage, vide, particules des moutons tondus. */
        void fastTick() {
            if (world == null) return;
            tickCount += 2;
            if (state != State.PLAYING) return;
            for (Player p : online(alive)) {
                if (!inWorld(p) || p.getGameMode() != GameMode.SURVIVAL) continue;
                if (p.getLocation().getY() < voidY) {
                    eliminate(p, killerOf(p), "est tombé dans le vide");
                    continue;
                }
                if (cageTimer == 0) graze(p);
                if (tickCount % 10 == 0 && layersOf(p) == 0)
                    world.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(0xF7A1C4), 1.3f));
            }
        }

        // -------------------------------------------------------------- toison

        int layersOf(Player p) {
            return layers.getOrDefault(p.getUniqueId(), 0);
        }

        Component fleeceBar(Player p) {
            int n = layersOf(p), max = cfg("max-layers", 5);
            TeamColor t = teams.get(p.getUniqueId());
            if (n == 0)
                return Component.text("TONDU ! ", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
                        .append(Component.text("Broute de l'herbe (accroupi) ou fuis !", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false));
            Component c = Component.text("Toison ", NamedTextColor.GRAY);
            for (int i = 0; i < max; i++)
                c = c.append(Component.text("■", i < n ? (t == null ? NamedTextColor.WHITE : t.color) : NamedTextColor.DARK_GRAY));
            return c.append(Component.text(" " + n + "/" + max, NamedTextColor.WHITE));
        }

        /**
         * Apparence de la toison : bloc de laine sur la tête dès 1 couche, puis plastron, jambières et bottes
         * en cuir teint (2, 3 et 4 couches), plastron brillant à 5. Tondu : plus rien (particules roses).
         */
        void applyFleece(Player p) {
            TeamColor t = teams.get(p.getUniqueId());
            if (t == null || !inWorld(p)) return;
            int n = layersOf(p);
            PlayerInventory inv = p.getInventory();
            inv.setHelmet(n >= 1 ? kit(new ItemStack(t.wool), "Toison") : null);
            inv.setChestplate(n >= 2 ? leather(Material.LEATHER_CHESTPLATE, t, n >= cfg("max-layers", 5)) : null);
            inv.setLeggings(n >= 3 ? leather(Material.LEATHER_LEGGINGS, t, false) : null);
            inv.setBoots(n >= 4 ? leather(Material.LEATHER_BOOTS, t, false) : null);
        }

        /**
         * Coup ennemi sur un mouton. Tondu : éliminé. Sinon, une couche en moins si c'est un coup de cisailles,
         * une flèche (option) ou une morsure de chien ; la laine va au tondeur (cisailles et chien).
         */
        void hit(Player victim, Player attacker, Entity source, String kind) {
            UUID id = victim.getUniqueId();
            long now = System.currentTimeMillis();
            if (now - lastShorn.getOrDefault(id, 0L) < cfg("shear-cooldown-ms", 600)) return;
            lastShorn.put(id, now);
            lastHit.put(id, attacker.getUniqueId());
            lastHitTime.put(id, now);
            TeamColor vt = teams.get(id);
            int n = layersOf(victim);
            if (n <= 0) {
                eliminate(victim, attacker, "s'est fait attraper tout nu");
                return;
            }
            knockback(victim, source.getLocation());
            victim.playHurtAnimation(0);
            boolean shears = kind.equals("shears");
            boolean takes = shears || kind.equals("dog") || (kind.equals("arrow") && getConfig().getBoolean("arrow-shears", true));
            if (!takes) {
                victim.playSound(victim.getLocation(), Sound.ENTITY_SHEEP_HURT, 1f, 1f);
                return;
            }
            n--;
            layers.put(id, n);
            applyFleece(victim);
            world.playSound(victim.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
            world.spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1.2, 0), 25, 0.35, 0.4, 0.35, 0, vt.wool.createBlockData());
            if (shears || kind.equals("dog")) {
                int w = cfg("wool-per-layer", 4);
                attacker.getInventory().addItem(new ItemStack(vt.wool, w));
                collected.merge(attacker.getUniqueId(), w, Integer::sum);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                attacker.sendActionBar(Component.text("+" + w + " laine " + vt.label.toLowerCase(), vt.color));
            }
            if (n == 0) {
                victim.showTitle(Title.title(Component.text("TONDU !", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                        Component.text("Va brouter ! Prochain coup : éliminé", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400))));
                victim.playSound(victim.getLocation(), Sound.ENTITY_SHEEP_DEATH, 1f, 1.3f);
                broadcast(code(vt) + victim.getName() + " §7a été tondu par " + code(teams.get(attacker.getUniqueId())) + attacker.getName() + "§7 !");
            }
        }

        /** Accroupi sur l'herbe : la barre progresse ; au bout, +1 couche et l'herbe devient terre. */
        void graze(Player p) {
            UUID id = p.getUniqueId();
            Block under = p.getLocation().subtract(0, 0.1, 0).getBlock();
            if (!p.isSneaking() || !p.isOnGround() || under.getType() != Material.GRASS_BLOCK) {
                grazeProgress.remove(id);
                grazeBlock.remove(id);
                return;
            }
            int max = cfg("max-layers", 5);
            if (layersOf(p) >= max) {
                if (tickCount % 20 == 0) p.sendActionBar(Component.text("Ta toison est déjà complète", NamedTextColor.GRAY));
                grazeProgress.remove(id);
                return;
            }
            if (!under.equals(grazeBlock.get(id))) {
                grazeBlock.put(id, under);
                grazeProgress.put(id, 0);
            }
            int need = Math.max(2, cfg("graze-ticks", 40));
            int prog = grazeProgress.merge(id, 2, Integer::sum);
            if (prog % 8 == 0) {
                world.playSound(under.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.6f, 1.2f);
                world.spawnParticle(Particle.BLOCK, under.getLocation().add(0.5, 1.05, 0.5), 6, 0.25, 0.05, 0.25, 0, under.getBlockData());
            }
            if (prog < need) {
                int filled = Math.round(20f * prog / need);
                p.sendActionBar(Component.text("Broute ", NamedTextColor.GREEN)
                        .append(Component.text("|".repeat(filled), NamedTextColor.GREEN))
                        .append(Component.text("|".repeat(20 - filled), NamedTextColor.DARK_GRAY)));
                return;
            }
            grazeProgress.remove(id);
            grazeBlock.remove(id);
            under.setType(Material.DIRT, false);
            int n = layersOf(p) + 1;
            layers.put(id, n);
            applyFleece(p);
            world.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, 1f);
            world.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 8, 0.4, 0.5, 0.4, 0);
            p.sendActionBar(Component.text("+1 couche de laine ", NamedTextColor.GREEN).append(fleeceBar(p)));
        }

        /** Auto-tonte (accroupi + clic droit avec les cisailles) : une couche contre de la laine. */
        void selfShear(Player p) {
            UUID id = p.getUniqueId();
            long now = System.currentTimeMillis();
            if (now - lastSelfShear.getOrDefault(id, 0L) < 1000) return;
            lastSelfShear.put(id, now);
            int n = layersOf(p);
            int min = Math.max(1, cfg("self-shear-min", 1));
            if (n <= min) {
                p.sendActionBar(Component.text("Tu ne peux pas te tondre davantage", NamedTextColor.RED));
                return;
            }
            TeamColor t = teams.get(id);
            layers.put(id, n - 1);
            applyFleece(p);
            int w = cfg("wool-per-layer", 4);
            p.getInventory().addItem(new ItemStack(t.wool, w));
            collected.merge(id, w, Integer::sum);
            world.playSound(p.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1.1f);
            world.spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1.2, 0), 20, 0.35, 0.4, 0.35, 0, t.wool.createBlockData());
            p.sendActionBar(Component.text("Tu te tonds : +" + w + " laine ", NamedTextColor.YELLOW).append(fleeceBar(p)));
        }

        // -------------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("tonte", Criteria.DUMMY,
                        Component.text("TONTE ", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
                                .append(Component.text("ROYALE", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        /** Remet le scoreboard principal, seulement si le joueur avait le nôtre (ne touche pas aux autres jeux). */
        void resetBoard(Player p) {
            Scoreboard b = boards.remove(p.getUniqueId());
            if (b != null && p.getScoreboard() == b) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("tonte");
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

        int countAlive(TeamColor t) {
            int n = 0;
            for (UUID id : alive) if (teams.get(id) == t) n++;
            return n;
        }

        int teamLayers(TeamColor t) {
            int n = 0;
            for (UUID id : alive) if (teams.get(id) == t) n += layers.getOrDefault(id, 0);
            return n;
        }

        void updateSidebar(Player p) {
            List<Component> l = new ArrayList<>();
            l.add(Component.text(" "));
            if (games.size() > 1 || index > 1)
                l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("#" + index, NamedTextColor.WHITE)));
            boolean inGame = teams.containsKey(p.getUniqueId()) && running();
            if (!inGame) {
                l.add(Component.text("Moutons : ", NamedTextColor.GRAY).append(Component.text(inWorld().size() + "/" + maxPlayers(), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                if (building) l.add(Component.text("Construction de la map...", NamedTextColor.GOLD));
                else if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.LIGHT_PURPLE)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
            } else {
                for (TeamColor t : inGameTeams) {
                    int a = countAlive(t);
                    Component line = Component.text("■ ", t.color).append(Component.text(t.label + " : ", t.color));
                    line = a == 0 ? line.append(Component.text("elimine", NamedTextColor.DARK_GRAY))
                            : line.append(Component.text(a + " mouton" + (a > 1 ? "s" : ""), NamedTextColor.WHITE))
                            .append(Component.text(" (" + teamLayers(t) + ")", NamedTextColor.GRAY));
                    l.add(line);
                }
                l.add(Component.text("  "));
                if (alive.contains(p.getUniqueId())) {
                    int n = layersOf(p);
                    l.add(n == 0 ? Component.text("Toison : ", NamedTextColor.GRAY).append(Component.text("TONDU !", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                            : Component.text("Toison : ", NamedTextColor.GRAY).append(Component.text(n + "/" + cfg("max-layers", 5), NamedTextColor.WHITE)));
                } else l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
                l.add(Component.text("Laine récoltée : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(collected.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                if (state == State.PLAYING && cageTimer == 0)
                    l.add(Component.text("Berger dans : ", NamedTextColor.GRAY).append(Component.text(clock(bergerTimer), NamedTextColor.GOLD)));
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(state == State.ENDING ? 0 : timer), NamedTextColor.WHITE)));
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        /** Pseudos colorés par équipe, sur le scoreboard de chaque joueur de la partie. */
        void colorNames() {
            for (Player viewer : inWorld()) {
                Scoreboard b = board(viewer);
                for (TeamColor t : TeamColor.values()) {
                    Team team = b.getTeam("tr_" + t.key());
                    if (team == null) {
                        team = b.registerNewTeam("tr_" + t.key());
                        team.color(t.color);
                        team.prefix(Component.text("[" + t.label + "] ", t.color));
                        team.setAllowFriendlyFire(false);
                    }
                    for (String e : new ArrayList<>(team.getEntries())) team.removeEntry(e);
                }
                for (Map.Entry<UUID, TeamColor> e : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) b.getTeam("tr_" + e.getValue().key()).addEntry(p.getName());
                }
            }
        }

        void clearNames() {
            for (Scoreboard b : boards.values())
                for (TeamColor t : TeamColor.values()) {
                    Team team = b.getTeam("tr_" + t.key());
                    if (team != null) team.unregister();
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
            broadcast("§a" + p.getName() + " §7a rejoint la Tonte Royale (§f" + players.size() + "§7).");
            reportStatus();
        }

        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || !players.contains(p.getUniqueId()) || !inWorld(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, kit(new ItemStack(Material.RED_BED), "Quitter la Tonte Royale"));
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
            boolean wasAlive = alive.contains(id) && state == State.PLAYING;
            if (wasAlive) eliminate(p, killerOf(p), "a abandonné");
            teams.remove(id);
            alive.remove(id);
            layers.remove(id);
            grazeProgress.remove(id);
            grazeBlock.remove(id);
            removeDogsOf(id);
            resetBoard(p);
            if (teleport) sendToHub(p);
            if (state == State.PLAYING) {
                colorNames();
                checkWin();
            }
            reportStatus();
        }

        // -------------------------------------------------------------- partie

        void startGame() {
            List<Player> list = inWorld();
            if (list.size() < 2 || building) { state = State.WAITING; return; }
            Collections.shuffle(list, random);
            int nTeams = teamCount(list.size());
            teams.clear();
            inGameTeams.clear();
            alive.clear();
            layers.clear();
            collected.clear();
            kills.clear();
            lastShorn.clear();
            lastHit.clear();
            lastHitTime.clear();
            grazeProgress.clear();
            grazeBlock.clear();
            placed.clear();
            removeBerger();
            removeDogs();
            clearEntities();
            for (int i = 0; i < nTeams; i++) inGameTeams.add(TeamColor.values()[i]);
            for (int i = 0; i < list.size(); i++) {
                UUID id = list.get(i).getUniqueId();
                teams.put(id, inGameTeams.get(i % nTeams));
                alive.add(id);
                layers.put(id, cfg("max-layers", 5));
            }
            colorNames();
            state = State.PLAYING;
            timer = cfg("game-time", 900);
            cageTimer = Math.max(1, cfg("cage-time", 5));
            bergerTimer = cfg("berger-interval", 120);
            world.setTime(6000);
            Map<TeamColor, Integer> slot = new EnumMap<>(TeamColor.class);
            for (Player p : list) {
                TeamColor t = teams.get(p.getUniqueId());
                int i = slot.merge(t, 1, Integer::sum) - 1;
                Location at = spawnSlot(t, i);
                buildCage(at, t);
                p.teleport(at);
                later(() -> equip(p));
            }
            StringBuilder sb = new StringBuilder();
            for (TeamColor t : inGameTeams) sb.append(code(t)).append(t.label).append(" (").append(countAlive(t)).append(") ");
            broadcast("§eLa tonte commence ! §7Équipes : " + sb.toString().trim());
            reportStatus();
            broadcast("§7Cisailles = tondre (+laine), §fbroute §7accroupi sur l'herbe pour regagner une couche, "
                    + (getConfig().getBoolean("self-shear", true) ? "§faccroupi + clic droit §7avec les cisailles pour te tondre toi-même. " : "")
                    + "§7Tondu = éliminé au prochain coup !");
        }

        void equip(Player p) {
            TeamColor t = teams.get(p.getUniqueId());
            // téléportation refusée ou joueur reparti entre-temps : on ne touche à rien hors du monde de la partie
            if (!p.isOnline() || t == null || state != State.PLAYING || !inWorld(p)) return;
            resetPlayer(p, GameMode.SURVIVAL);
            PlayerInventory inv = p.getInventory();
            ItemStack shears = kit(new ItemStack(Material.SHEARS), "Cisailles");
            ItemMeta m = shears.getItemMeta();
            m.setUnbreakable(true);
            shears.setItemMeta(m);
            inv.setItem(0, shears);
            int bread = cfg("bread", 3);
            if (bread > 0) inv.setItem(1, kit(new ItemStack(Material.BREAD, bread), null));
            int arrows = cfg("bow-arrows", 4);
            if (arrows > 0) {
                ItemStack bow = kit(new ItemStack(Material.BOW), null);
                ItemMeta bm = bow.getItemMeta();
                bm.setUnbreakable(true);
                bow.setItemMeta(bm);
                inv.setItem(2, bow);
                inv.setItem(8, kit(new ItemStack(Material.ARROW, arrows), null));
            }
            applyFleece(p);
        }

        Location spawnSlot(TeamColor t, int i) {
            int[] s = SLOTS[i % SLOTS.length];
            int x = t.sx * (CORNER + s[0]), z = t.sz * (CORNER + s[1]);
            Location l = new Location(world, x + 0.5, cy + 1, z + 0.5);
            l.setDirection(new Vector(-x, 0, -z));
            return l;
        }

        void buildCage(Location at, TeamColor t) {
            Block baseBlock = at.getBlock();
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = 0; dy <= 3; dy++) {
                        boolean wall = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == 3;
                        if (!wall) continue;
                        Block b = baseBlock.getRelative(dx, dy, dz);
                        if (b.getType().isAir()) {
                            b.setType(t.glass, false);
                            cages.add(b);
                        }
                    }
        }

        void openCages() {
            for (Block b : cages) if (b.getType().name().endsWith("STAINED_GLASS")) b.setType(Material.AIR, false);
            cages.clear();
            for (Player p : online(alive)) {
                p.showTitle(Title.title(Component.text("TONDEZ !", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                        Component.text("Le dernier troupeau en vie gagne", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, 1f);
            }
        }

        Player killerOf(Player p) {
            UUID id = lastHit.get(p.getUniqueId());
            Long t = lastHitTime.get(p.getUniqueId());
            if (id != null && t != null && System.currentTimeMillis() - t < 10_000) return Bukkit.getPlayer(id);
            return null;
        }

        /** Élimination : spectateur au-dessus du pré central. */
        void eliminate(Player p, Player killer, String how) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            TeamColor t = teams.get(id);
            grazeProgress.remove(id);
            grazeBlock.remove(id);
            removeDogsOf(id);
            String msg = code(t) + p.getName() + " §7" + how;
            if (killer != null && killer != p && teams.containsKey(killer.getUniqueId())) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " tonte kill");
                msg += " §7(par " + code(teams.get(killer.getUniqueId())) + killer.getName() + "§7)";
                killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
            int left = countAlive(t);
            broadcast(msg + " §8(" + (t == null ? "" : t.label + " : " + left + " restant" + (left > 1 ? "s" : "")) + ")");
            if (p.isOnline() && inWorld(p)) {
                world.strikeLightningEffect(p.getLocation());
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(specPoint);
                p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("Tu regardes la fin de la partie", NamedTextColor.GRAY)));
            }
            if (t != null && left == 0) broadcast(code(t) + "L'équipe " + t.fem() + " est éliminée !");
            reportStatus();
            Bukkit.getScheduler().runTask(EemiTonte.this, this::checkWin);
        }

        void checkWin() {
            if (state != State.PLAYING) return;
            Set<TeamColor> left = new HashSet<>();
            for (UUID id : alive) if (teams.get(id) != null) left.add(teams.get(id));
            if (left.size() > 1) return;
            TeamColor w = left.isEmpty() ? null : left.iterator().next();
            endGame(w, w == null ? "Plus aucun mouton en vie !" : "Dernier troupeau debout : " + w.label + " !");
        }

        /** Fin du temps : l'équipe avec le plus de couches de laine (moutons vivants) gagne. */
        void timeUp() {
            TeamColor best = null;
            int bestN = -1;
            boolean tie = false;
            StringBuilder sb = new StringBuilder();
            for (TeamColor t : inGameTeams) {
                if (countAlive(t) == 0) continue;
                int n = teamLayers(t);
                sb.append(code(t)).append(t.label).append(" ").append(n).append(" §7- ");
                if (n > bestN) { bestN = n; best = t; tie = false; }
                else if (n == bestN) tie = true;
            }
            broadcast("§cTemps écoulé ! §7Couches de laine : " + sb.toString().replaceAll(" §7- $", ""));
            if (tie) endGame(null, "Égalité au nombre de couches !");
            else endGame(best, best == null ? "Plus aucun mouton en vie !" : "Plus belle toison : " + best.label + " (" + bestN + " couches)");
        }

        void endGame(TeamColor winner, String reason) {
            endGame(winner, reason, true);
        }

        /** rewards = false pour un arrêt admin (pas de récompenses EemiCore). */
        void endGame(TeamColor winner, String reason, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            if (rewards) {
                for (Map.Entry<UUID, TeamColor> en : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p == null) continue;
                    eemi("reward " + p.getName() + " tonte play");
                    if (winner != null && en.getValue() == winner) eemi("reward " + p.getName() + " tonte win");
                }
            }
            reportStatus();
            timer = cfg("end-time", 8);
            removeBerger();
            removeDogs();
            // titre ≤ 16 caractères visibles (« Victoire Jaune ! » = 16)
            Component title = winner == null ? Component.text("Égalité !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                    : Component.text("Victoire " + winner.label + " !", winner.color).decorate(TextDecoration.BOLD);
            for (Player p : inWorld()) {
                if (!teams.containsKey(p.getUniqueId())) continue;
                p.showTitle(Title.title(title, Component.text(shortReason(reason), NamedTextColor.GRAY)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (p.getGameMode() == GameMode.SURVIVAL) p.setGameMode(GameMode.ADVENTURE);
            }
            broadcast("§6" + reason);
            UUID top = null;
            int topN = 0;
            for (Map.Entry<UUID, Integer> e : collected.entrySet()) if (e.getValue() > topN) { topN = e.getValue(); top = e.getKey(); }
            if (top != null && Bukkit.getPlayer(top) != null)
                broadcast("§7Meilleur tondeur : §f" + Bukkit.getPlayer(top).getName() + " §7(" + topN + " laine)");
        }

        /**
         * Fin : partie 1 → retour à la salle d'attente puis reconstruction de la map. Copie → les inscrits
         * rejoignent une autre partie en attente qui a la place pour tous (la copie est détruite), sinon la copie
         * redevient une partie en attente (remise à zéro) ; vide, elle est détruite.
         */
        void backToLobby() {
            clearNames();
            state = State.WAITING;
            World hub = hubWorld();
            List<Player> stay = new ArrayList<>();
            for (Player p : online(players)) {
                boolean here = inWorld(p);
                if (!here && (hub == null || !p.getWorld().equals(hub))) {
                    // parti dans un autre jeu entre-temps : désinscrit sans le déplacer
                    players.remove(p.getUniqueId());
                    resetBoard(p);
                    p.sendMessage(PREFIX + "§7Tu n'étais plus au hub : inscription à la Tonte Royale annulée.");
                    continue;
                }
                stay.add(p);
            }
            teams.clear();
            inGameTeams.clear();
            alive.clear();
            layers.clear();
            cages.clear();
            placed.clear();
            for (Player p : online(spectators)) {
                sendToHub(p);
                p.sendMessage(PREFIX + "§7Partie terminée, retour au hub.");
            }
            spectators.clear();
            if (index > 1) {
                if (stay.isEmpty()) {
                    destroyGame(this);
                    return;
                }
                Game other = openGame(stay.size(), this);
                if (other != null && other.world != null) {
                    for (Player p : stay) {
                        players.remove(p.getUniqueId());
                        resetBoard(p);
                        other.enter(p);
                    }
                    destroyGame(this);
                    return;
                }
            }
            for (Player p : stay) {
                if (!mayTouch(p)) continue; // pas dans un monde du jeu ni au hub : on ne le téléporte pas
                p.teleport(lobby);
                later(() -> prepareLobbyPlayer(p));
            }
            reportStatus();
            Bukkit.getScheduler().runTaskLater(EemiTonte.this, () -> { if (games.get(index) == this) rebuild(null, null); }, 20L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, "Partie arrêtée par un admin.", false);
            if (state == State.ENDING) backToLobby();
            if (state == State.STARTING) state = State.WAITING;
        }

        // -------------------------------------------------------------- le berger et ses chiens

        void spawnBerger() {
            removeBerger();
            Location at = bergerPoints.get(random.nextInt(bergerPoints.size())).clone();
            at.setYaw(random.nextInt(360));
            spawningOurs = true;
            Villager v;
            try {
                v = world.spawn(at, Villager.class, e -> {
                    e.setProfession(Villager.Profession.SHEPHERD);
                    e.setAI(false);
                    e.setPersistent(false);
                    e.setGlowing(true);
                    e.customName(Component.text("Le Berger", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
                    e.setCustomNameVisible(true);
                });
            } finally {
                spawningOurs = false;
            }
            if (v == null || !v.isValid()) {
                getLogger().warning("Le berger n'a pas pu apparaître en " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + " (" + world.getName() + ").");
                return;
            }
            berger = v.getUniqueId();
            world.playSound(at, Sound.ENTITY_VILLAGER_CELEBRATE, 2f, 1f);
            String where = at.getBlockX() == 0 && at.getBlockZ() == 0 ? "sur le pré central" : "sur un îlot";
            broadcast("§6Le Berger est apparu " + where + " ! §7Le premier qui le touche gagne un chien de garde pour "
                    + cfg("dog-time", 30) + " s.");
        }

        void removeBerger() {
            if (berger == null) return;
            Entity e = Bukkit.getEntity(berger);
            if (e != null) e.remove();
            berger = null;
        }

        void claimBerger(Player p, Entity v) {
            if (state != State.PLAYING || cageTimer > 0 || !alive.contains(p.getUniqueId()) || p.getGameMode() != GameMode.SURVIVAL) return;
            TeamColor t = teams.get(p.getUniqueId());
            Location at = v.getLocation();
            removeBerger();
            world.spawnParticle(Particle.POOF, at.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
            spawningOurs = true;
            Wolf w;
            try {
                w = world.spawn(at, Wolf.class, e -> {
                    e.setTamed(true);
                    e.setOwner(p);
                    e.setCollarColor(t.dye);
                    e.setPersistent(false);
                    e.customName(Component.text("Chien de " + p.getName(), t.color));
                    e.setCustomNameVisible(true);
                });
            } finally {
                spawningOurs = false;
            }
            if (w == null || !w.isValid()) {
                getLogger().warning("Le chien n'a pas pu apparaître.");
                return;
            }
            dogs.put(w.getUniqueId(), new Dog(p.getUniqueId(), t, System.currentTimeMillis() + cfg("dog-time", 30) * 1000L));
            world.playSound(at, Sound.ENTITY_WOLF_AMBIENT, 2f, 1f);
            broadcast(code(t) + p.getName() + " §7a trouvé le Berger et gagne un §fchien de garde §7!");
        }

        /** Chaque seconde : le chien attaque l'ennemi vivant le plus proche, disparaît à la fin de son temps. */
        void dogTick() {
            long now = System.currentTimeMillis();
            double range = cfgD("dog-range", 16);
            for (Map.Entry<UUID, Dog> en : new ArrayList<>(dogs.entrySet())) {
                Entity e = Bukkit.getEntity(en.getKey());
                Dog d = en.getValue();
                Player owner = Bukkit.getPlayer(d.owner());
                if (!(e instanceof Wolf w) || !w.isValid() || now > d.until() || owner == null || !alive.contains(d.owner())) {
                    if (e != null) {
                        e.getWorld().spawnParticle(Particle.POOF, e.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.02);
                        e.remove();
                    }
                    dogs.remove(en.getKey());
                    if (owner != null && alive.contains(d.owner())) owner.sendActionBar(Component.text("Ton chien est reparti au berger", NamedTextColor.GRAY));
                    continue;
                }
                if (w.getLocation().getY() < voidY && inWorld(owner)) w.teleport(owner.getLocation());
                Player best = null;
                double bestD = range * range;
                for (Player p : online(alive)) {
                    if (teams.get(p.getUniqueId()) == d.team() || p.getGameMode() != GameMode.SURVIVAL || !p.getWorld().equals(w.getWorld())) continue;
                    double dist = p.getLocation().distanceSquared(w.getLocation());
                    if (dist < bestD) { bestD = dist; best = p; }
                }
                w.setTarget(best);
            }
        }

        void removeDogsOf(UUID owner) {
            for (Map.Entry<UUID, Dog> en : new ArrayList<>(dogs.entrySet()))
                if (en.getValue().owner().equals(owner)) {
                    Entity e = Bukkit.getEntity(en.getKey());
                    if (e != null) e.remove();
                    dogs.remove(en.getKey());
                }
        }

        void removeDogs() {
            for (UUID id : dogs.keySet()) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
            dogs.clear();
        }

        // -------------------------------------------------------------- construction

        /**
         * Vide la zone de jeu puis construit la map (graine fixe : identique à chaque reconstruction).
         * Blocs posés par paquets.
         */
        void rebuild(CommandSender sender, Runnable after) {
            if (world == null || building) return;
            if (running()) {
                if (sender != null) sender.sendMessage(PREFIX + "§cImpossible pendant une partie.");
                return;
            }
            building = true;
            removeBerger();
            removeDogs();
            clearEntities();
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
                Bukkit.getScheduler().runTaskLater(EemiTonte.this, () -> apply(ops, end, sender, after), 1L);
                return;
            }
            building = false;
            clearEntities();
            getLogger().info("Map Tonte Royale construite (" + world.getName() + ", " + ops.size() + " opérations).");
            if (sender != null) sender.sendMessage(PREFIX + "§aMap construite.");
            if (after != null) after.run();
        }

        /** Retire ce qu'une partie laisse : objets, projectiles, mobs (berger, chiens), blocs qui tombent, XP. */
        void clearEntities() {
            if (world == null) return;
            for (Entity e : world.getEntities()) {
                if (e instanceof Player) continue;
                if (e instanceof Item || e instanceof Projectile || e instanceof FallingBlock || e instanceof Mob || e instanceof ExperienceOrb) e.remove();
            }
        }

        /** Regarder la partie en cours en spectateur, au-dessus du pré central (même point que les éliminés). */
        void spectate(Player p) {
            spectators.add(p.getUniqueId());
            // téléporter d'abord (Multiverse-Inventories sauvegarde l'inventaire du monde quitté), puis vider
            p.teleport(specPoint);
            if (!inWorld(p)) {
                spectators.remove(p.getUniqueId());
                return;
            }
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage(PREFIX + "§7Tu regardes la partie #" + index + ", §f/lobby §7pour revenir.");
        }

        String describe() {
            return "§f#" + index + " §7" + (world == null ? "(preparation)" : world.getName()) + " §e" + state.name().toLowerCase()
                    + (building ? " §6(construction)" : "") + " §7joueurs §f" + players.size() + "/" + maxPlayers()
                    + (pending.isEmpty() ? "" : " §7+" + pending.size() + " en attente") + " §7en vie §f" + alive.size()
                    + " §7spectateurs §f" + spectators.size();
        }
    }

    /** Sous-titre de fin ≤ 38 caractères (la raison complète part dans le chat). */
    private static String shortReason(String reason) {
        return reason.length() <= 38 ? reason : reason.substring(0, 37) + "…";
    }

    private void knockback(Player victim, Location from) {
        Vector dir = victim.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        dir.normalize().multiply(cfgD("knockback", 0.45)).setY(cfgD("knockback-y", 0.32));
        victim.setVelocity(dir);
    }

    private ItemStack leather(Material m, TeamColor t, boolean glint) {
        ItemStack it = kit(new ItemStack(m), null);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(t.leather);
        meta.setUnbreakable(true);
        if (glint) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ construction de la map

    private BlockData bd(String s) {
        return dataCache.computeIfAbsent(s, k -> Bukkit.createBlockData(k.contains(":") ? k : "minecraft:" + k));
    }

    /** Liste d'opérations ; la dernière écriture d'une case l'emporte. */
    private final class Builder {
        final List<Op> ops = new ArrayList<>();
        /** Cases (x,z) déjà occupées par un décor, à la hauteur du sol + 1. */
        final Set<Long> used = new HashSet<>();

        void put(int x, int y, int z, String data) {
            ops.add(new Op(x, y, z, data == null ? null : bd(data)));
        }

        void use(int x, int z) {
            used.add(key(x, 0, z));
        }

        boolean free(int x, int z) {
            return !used.contains(key(x, 0, z));
        }

        /** Clôtures reliées entre elles (les connexions sont calculées ici, la pose se fait sans physique). */
        void fences(List<int[]> xz, int y, String type) {
            Set<Long> set = new HashSet<>();
            for (int[] c : xz) set.add(key(c[0], 0, c[1]));
            for (int[] c : xz) {
                boolean n = set.contains(key(c[0], 0, c[1] - 1)), s = set.contains(key(c[0], 0, c[1] + 1));
                boolean e = set.contains(key(c[0] + 1, 0, c[1])), w = set.contains(key(c[0] - 1, 0, c[1]));
                put(c[0], y, c[1], type + "[north=" + n + ",south=" + s + ",east=" + e + ",west=" + w + "]");
                use(c[0], c[1]);
            }
        }
    }

    /** Blocs de la map : zone vidée, 4 îles de départ, îlots, pré central, salle d'attente. */
    private List<Op> mapOps() {
        Builder b = new Builder();
        for (int x = -HALF; x <= HALF; x++)
            for (int z = -HALF; z <= HALF; z++)
                for (int y = voidY - 2; y <= cy + 20; y++) b.put(x, y, z, null);
        Random r = new Random(4242);
        for (TeamColor t : TeamColor.values()) startIsland(b, t, r);
        meadow(b, r);
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) smallIslet(b, sx * DIAG, sz * DIAG, 3.6, 1.6, "coarse_dirt", r);
        for (int s = -1; s <= 1; s += 2) {
            smallIslet(b, s * AXIS, 0, 4.4, 2.3, "rooted_dirt", r);
            smallIslet(b, 0, s * AXIS, 4.4, 2.3, "rooted_dirt", r);
        }
        waitingRoom(b);
        return b.ops;
    }

    /**
     * Masse d'une île : dessus choisi par « top » (coordonnées relatives), terre puis roche en cône
     * irrégulier dessous. Bord légèrement ondulé.
     */
    private void mass(Builder b, int cx, int cz, double radius, Random r, java.util.function.BiFunction<Integer, Integer, String> top) {
        double p1 = r.nextDouble() * Math.PI * 2, p2 = r.nextDouble() * Math.PI * 2;
        int R = (int) Math.ceil(radius) + 2;
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.hypot(dx, dz);
                double a = Math.atan2(dz, dx);
                double edge = radius + 0.5 * Math.sin(3 * a + p1) + 0.4 * Math.sin(5 * a + p2) - 0.3;
                if (d > edge) continue;
                int x = cx + dx, z = cz + dz;
                b.put(x, cy, z, top.apply(dx, dz));
                b.put(x, cy - 1, z, "dirt");
                int depth = Math.min(cy - voidY - 2, 2 + (int) ((edge - d) * 1.15) + r.nextInt(2));
                for (int k = 2; k <= depth; k++) {
                    String m;
                    if (k == 2) m = r.nextInt(3) == 0 ? "coarse_dirt" : "dirt";
                    else {
                        int q = r.nextInt(10);
                        m = q < 5 ? "stone" : q < 7 ? "andesite" : q < 9 ? "cobblestone" : "mossy_cobblestone";
                    }
                    b.put(x, cy - k, z, m);
                }
            }
    }

    private static final String[] FLOWERS = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium", "pink_tulip"};

    /**
     * Île de départ d'une équipe (une bergerie) : sol de terre piétinée avec un seul petit enclos d'herbe
     * (8 blocs à brouter), grange aux murs de la couleur de l'équipe, foin, abreuvoir, fleurs.
     * Coordonnées locales (u, v) : positives vers l'extérieur de la map.
     */
    private void startIsland(Builder b, TeamColor t, Random r) {
        int cx = t.sx * CORNER, cz = t.sz * CORNER, y = cy;
        java.util.function.BiFunction<Integer, Integer, int[]> w = (u, v) -> new int[]{cx + t.sx * u, cz + t.sz * v};
        mass(b, cx, cz, 10.5, r, (dx, dz) -> {
            int q = Math.floorMod(dx * 31 + dz * 17 + dx * dz, 10);
            return q < 4 ? "coarse_dirt" : q < 6 ? "podzol" : q < 8 ? "rooted_dirt" : "dirt";
        });
        // places de départ (cages 3x3) : rien dessus
        for (int[] s : SLOTS)
            for (int du = -1; du <= 1; du++)
                for (int dv = -1; dv <= 1; dv++) { int[] c = w.apply(s[0] + du, s[1] + dv); b.use(c[0], c[1]); }
        // enclos d'herbe : 8 blocs à brouter, clôture avec une entrée côté u = 0
        List<int[]> fence = new ArrayList<>();
        for (int u = 0; u <= 5; u++)
            for (int v = -6; v <= -3; v++) {
                int[] c = w.apply(u, v);
                boolean ring = u == 0 || u == 5 || v == -6 || v == -3;
                if (!ring) { b.put(c[0], y, c[1], "grass_block"); b.use(c[0], c[1]); }
                else if (!(u == 0 && (v == -4 || v == -5))) fence.add(c);
                else b.use(c[0], c[1]);
            }
        b.fences(fence, y + 1, "oak_fence");
        for (int[] l : new int[][]{{0, -6}, {5, -3}}) { int[] c = w.apply(l[0], l[1]); b.put(c[0], y + 2, c[1], "lantern"); }
        // grange : murs de terracotta de l'équipe, poteaux de bouleau, toit à deux pans, faîte en laine
        for (int u = 0; u <= 7; u++) for (int v = 0; v <= 7; v++) { int[] c = w.apply(u, v); b.use(c[0], c[1]); }
        for (int u = 1; u <= 6; u++)
            for (int v = 1; v <= 6; v++) {
                int[] c = w.apply(u, v);
                b.put(c[0], y, c[1], "spruce_planks");
                boolean wall = u == 1 || u == 6 || v == 1 || v == 6;
                boolean corner = (u == 1 || u == 6) && (v == 1 || v == 6);
                for (int dy = 1; dy <= 3; dy++) {
                    if (!wall) continue;
                    boolean door = u == 1 && (v == 3 || v == 4) && dy <= 2;
                    b.put(c[0], y + dy, c[1], door ? null : corner ? "stripped_birch_log" : dy == 3 ? "white_terracotta" : t.terracotta);
                }
            }
        int[][] roof = {{0, 7}, {1, 6}, {2, 5}, {3, 4}};
        for (int k = 0; k < roof.length; k++)
            for (int u = 0; u <= 7; u++)
                for (int v = roof[k][0]; v <= roof[k][1]; v++) {
                    int[] c = w.apply(u, v);
                    b.put(c[0], y + 4 + k, c[1], k == roof.length - 1 ? t.wool.name().toLowerCase() : "dark_oak_planks");
                }
        for (int[] h : new int[][]{{5, 5}, {5, 4}, {4, 5}}) { int[] c = w.apply(h[0], h[1]); b.put(c[0], y + 1, c[1], "hay_block"); }
        int[] hh = w.apply(5, 5);
        b.put(hh[0], y + 2, hh[1], "hay_block");
        int[] lamp = w.apply(3, 3);
        b.put(lamp[0], y + 3, lamp[1], "lantern[hanging=true]");
        // ballots de foin dehors, abreuvoir
        for (int[] h : new int[][]{{-6, 3}, {-6, 4}, {-5, 4}}) { int[] c = w.apply(h[0], h[1]); b.put(c[0], y + 1, c[1], "hay_block"); b.use(c[0], c[1]); }
        int[] top = w.apply(-6, 4);
        b.put(top[0], y + 2, top[1], "hay_block");
        for (int u = -3; u <= -1; u++) { int[] c = w.apply(u, 6); b.put(c[0], y + 1, c[1], "water_cauldron[level=3]"); b.use(c[0], c[1]); }
        // fleurs éparses sur la terre (on ne peut pas les brouter : seule l'herbe compte)
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                int x = cx + dx, z = cz + dz;
                if (Math.hypot(dx, dz) > 8.5 || !b.free(x, z) || r.nextInt(100) >= 9) continue;
                b.put(x, y + 1, z, FLOWERS[r.nextInt(FLOWERS.length)]);
            }
    }

    /** Grand pré central : tout en herbe (zone disputée), deux arbres, une mare, du foin, des fleurs. */
    private void meadow(Builder b, Random r) {
        int y = cy;
        mass(b, 0, 0, 14.5, r, (dx, dz) -> "grass_block");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.use(dx, dz); // berger
        tree(b, 7, -6, r);
        tree(b, -6, 7, r);
        // mare
        for (int x = 5; x <= 7; x++)
            for (int z = 5; z <= 6; z++) { b.put(x, y, z, "water"); b.use(x, z); }
        b.put(6, y + 1, 5, "lily_pad");
        // meule de foin
        for (int[] h : new int[][]{{-7, -5}, {-6, -5}, {-7, -4}}) { b.put(h[0], y + 1, h[1], "hay_block"); b.use(h[0], h[1]); }
        b.put(-7, y + 2, -5, "hay_block");
        for (int dx = -14; dx <= 14; dx++)
            for (int dz = -14; dz <= 14; dz++) {
                if (Math.hypot(dx, dz) > 12.5 || !b.free(dx, dz) || r.nextInt(100) >= 14) continue;
                b.put(dx, y + 1, dz, r.nextInt(4) == 0 ? "short_grass" : FLOWERS[r.nextInt(FLOWERS.length)]);
            }
    }

    private void tree(Builder b, int x, int z, Random r) {
        int y = cy;
        int h = 4 + r.nextInt(2);
        for (int dy = 1; dy <= h; dy++) b.put(x, y + dy, z, "oak_log");
        for (int dy = h - 1; dy <= h + 1; dy++) {
            int rad = dy == h + 1 ? 1 : 2;
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if ((dx == 0 && dz == 0 && dy <= h) || (Math.abs(dx) == 2 && Math.abs(dz) == 2 && r.nextBoolean())) continue;
                    b.put(x + dx, y + dy, z + dz, "oak_leaves[persistent=true]");
                }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) b.use(x + dx, z + dz);
    }

    /** Îlot intermédiaire : un peu d'herbe au centre (le berger peut y apparaître), un buisson ou un ballot. */
    private void smallIslet(Builder b, int cx, int cz, double radius, double grass, String ground, Random r) {
        mass(b, cx, cz, radius, r, (dx, dz) -> Math.hypot(dx, dz) <= grass ? "grass_block" : ground);
        int dx = r.nextBoolean() ? 2 : -2, dz = r.nextBoolean() ? 2 : -2;
        if (radius > 4) b.put(cx + dx, cy + 1, cz + dz, "hay_block");
        else b.put(cx + dx, cy + 1, cz, "oak_leaves[persistent=true]");
        b.put(cx - dz / 2, cy + 1, cz + dx / 2, FLOWERS[r.nextInt(FLOWERS.length)]);
    }

    /** Salle d'attente : plancher de sapin bordé de laine aux 4 couleurs, murs invisibles, foin et lanternes. */
    private void waitingRoom(Builder b) {
        int y = cfg("lobby-y", 140);
        String[] border = {"red_wool", "blue_wool", "lime_wool", "yellow_wool"};
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++) {
                boolean edge = Math.abs(x) == 7 || Math.abs(z) == 7;
                b.put(x, y, z, edge ? border[Math.floorMod(x + z, 4)] : ((x + z) % 2 == 0 ? "spruce_planks" : "stripped_spruce_wood"));
                for (int dy = 1; dy <= 4; dy++) b.put(x, y + dy, z, edge ? "barrier" : null);
            }
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                b.put(sx * 6, y + 1, sz * 6, "hay_block");
                b.put(sx * 6, y + 2, sz * 6, "lantern");
            }
        b.put(0, y + 1, -5, "white_wool");
        b.put(0, y + 2, -5, "white_carpet");
    }

    // ------------------------------------------------------------------ objets

    private ItemStack kit(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        if (name != null) meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    private static String code(TeamColor t) {
        if (t == null) return "§7";
        return switch (t) {
            case ROUGE -> "§c";
            case BLEU -> "§9";
            case VERT -> "§a";
            case JAUNE -> "§e";
        };
    }

    // ------------------------------------------------------------------ événements : entités

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isGameWorld(e.getLocation().getWorld()) && !spawningOurs && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    /** Le berger et les chiens apparaissent même si un autre plugin (WorldGuard) bloque les apparitions. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnForce(CreatureSpawnEvent e) {
        if (spawningOurs && isGameWorld(e.getLocation().getWorld())) e.setCancelled(false);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        // rien ne tombe au sol : la laine va directement dans l'inventaire
        if (isGameWorld(e.getLocation().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        if (!isGameWorld(victim.getWorld())) {
            if (victim instanceof Player p) {
                Game g = gameOf(p.getUniqueId());
                if (g != null && g.running() && g.teams.containsKey(p.getUniqueId())) e.setCancelled(true);
            }
            return;
        }
        Game g = gameAt(victim.getWorld());
        if (g == null) {
            // modèle : rien ne prend de dégâts
            e.setCancelled(true);
            return;
        }
        // berger : le toucher suffit
        if (g.berger != null && victim.getUniqueId().equals(g.berger)) {
            e.setCancelled(true);
            if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player p && gameOf(p.getUniqueId()) == g) g.claimBerger(p, victim);
            return;
        }
        if (g.dogs.containsKey(victim.getUniqueId())) { e.setCancelled(true); return; }
        if (!(victim instanceof Player p)) return;
        e.setCancelled(true); // la vie ne bouge jamais : tout passe par la toison
        UUID id = p.getUniqueId();
        if (!g.players.contains(id) || g.state != State.PLAYING || g.cageTimer > 0 || !g.alive.contains(id) || p.getGameMode() != GameMode.SURVIVAL) return;
        if (!(e instanceof EntityDamageByEntityEvent by)) return;
        Entity d = by.getDamager();
        Player attacker = null;
        String kind = "hand";
        if (d instanceof Player pl) {
            attacker = pl;
            if (pl.getInventory().getItemInMainHand().getType() == Material.SHEARS) kind = "shears";
        } else if (d instanceof Projectile proj && proj.getShooter() instanceof Player pl) {
            attacker = pl;
            kind = "arrow";
            proj.remove();
        } else if (g.dogs.containsKey(d.getUniqueId())) {
            attacker = Bukkit.getPlayer(g.dogs.get(d.getUniqueId()).owner());
            kind = "dog";
        }
        if (attacker == null || attacker == p || !g.alive.contains(attacker.getUniqueId())) return;
        if (g.teams.get(attacker.getUniqueId()) == g.teams.get(id)) return;
        g.hit(p, attacker, d, kind);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        Game g = gameAt(t.getWorld());
        if (g == null) return;
        if (g.berger != null && t.getUniqueId().equals(g.berger)) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND && gameOf(e.getPlayer().getUniqueId()) == g) g.claimBerger(e.getPlayer(), t);
        } else if (g.dogs.containsKey(t.getUniqueId())) e.setCancelled(true); // pas d'assis / debout
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Game g = gameOf(p.getUniqueId());
        if (g == null || !g.teams.containsKey(p.getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        if (g.state == State.PLAYING) g.eliminate(p, g.killerOf(p), "est mort");
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && p.isDead()) p.spigot().respawn(); }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p.getUniqueId());
        if (g == null || g.lobby == null || !g.here(p.getLocation())) return;
        if (g.teams.containsKey(p.getUniqueId()) && g.running()) {
            e.setRespawnLocation(g.specPoint);
            later(() -> {
                if (!g.inWorld(p)) return; // parti ailleurs entre-temps : on n'y touche pas
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
            });
            return;
        }
        e.setRespawnLocation(g.lobby);
        later(() -> g.prepareLobbyPlayer(p));
    }

    // ------------------------------------------------------------------ événements : joueurs

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p.getUniqueId());
        if (g == null || !g.here(e.getTo())) return;
        if (g.state == State.PLAYING && g.alive.contains(p.getUniqueId())) return; // vide : voir fastTick
        // salle d'attente, spectateurs : on ne tombe pas dans le vide
        if (e.getTo().getY() < voidY - 20)
            p.teleport(g.running() && g.teams.containsKey(p.getUniqueId()) ? g.specPoint : g.lobby);
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

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemitonte.admin");
    }

    private boolean inArea(Block b) {
        return Math.abs(b.getX()) <= HALF && Math.abs(b.getZ()) <= HALF && b.getY() >= voidY - 2 && b.getY() <= cy + 18;
    }

    /** Pose : seulement de la laine, pendant la partie, dans la zone de jeu. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!isGameWorld(b.getWorld()) || admin(p)) return;
        Game g = gameAt(b.getWorld());
        boolean ok = g != null && gameOf(p.getUniqueId()) == g && g.state == State.PLAYING && g.cageTimer == 0
                && g.alive.contains(p.getUniqueId()) && !isKit(e.getItemInHand()) && b.getType().name().endsWith("_WOOL") && inArea(b);
        if (!ok) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceRecord(BlockPlaceEvent e) {
        Game g = gameAt(e.getBlock().getWorld());
        if (g != null && g.state == State.PLAYING)
            g.placed.add(key(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()));
    }

    /** Casse : seulement les blocs posés par les joueurs ; la laine revient dans l'inventaire. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!isGameWorld(b.getWorld()) || admin(p)) return;
        Game g = gameAt(b.getWorld());
        long k = key(b.getX(), b.getY(), b.getZ());
        if (g == null || gameOf(p.getUniqueId()) != g || g.state != State.PLAYING || g.cageTimer > 0
                || !g.alive.contains(p.getUniqueId()) || !g.placed.contains(k)) {
            e.setCancelled(true);
            return;
        }
        g.placed.remove(k);
        e.setDropItems(false);
        e.setExpToDrop(0);
        if (b.getType().name().endsWith("_WOOL")) p.getInventory().addItem(new ItemStack(b.getType()));
    }

    /**
     * Avec useInteractedBlock à DENY, Paper ne pose l'objet tenu que si useItemInHand vaut ALLOW
     * (DEFAULT ne suffit pas) : on l'autorise pour la laine, la pose est ensuite filtrée par onPlace.
     */
    private void allowWool(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it != null && it.getType().name().endsWith("_WOOL")) e.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p.getUniqueId());
        if (g == null || !g.here(p.getLocation())) return;
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (e.getHand() != EquipmentSlot.HAND) {
            // pas d'usage d'objets de la main secondaire sur les blocs de la map
            if (a == Action.RIGHT_CLICK_BLOCK && !admin(p)) {
                e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                allowWool(e);
            }
            return;
        }
        ItemStack it = e.getItem();
        if (right && it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté la Tonte Royale.");
            return;
        }
        if (admin(p)) return;
        // pas d'interaction avec les blocs de la map (chaudrons, portillons...)
        if (a == Action.RIGHT_CLICK_BLOCK) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            allowWool(e);
        }
        if (right && it != null && it.getType() == Material.SHEARS) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (p.isSneaking() && getConfig().getBoolean("self-shear", true) && g.state == State.PLAYING && g.cageTimer == 0
                    && g.alive.contains(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL) g.selfShear(p);
        }
    }

    /** La toison (armure) ne s'enlève pas. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Game g = gameOf(p.getUniqueId());
        if (g != null && g.teams.containsKey(p.getUniqueId()) && g.here(p.getLocation())
                && e.getSlotType() == InventoryType.SlotType.ARMOR) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Game g = gameOf(e.getPlayer().getUniqueId());
        if (g != null && g.here(e.getPlayer().getLocation())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = gameOf(p.getUniqueId());
        if (g != null && g.here(p.getLocation()) && !(g.state == State.PLAYING && g.alive.contains(p.getUniqueId()))) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game spec = spectatorGame(p.getUniqueId());
        if (spec != null) {
            spec.spectators.remove(p.getUniqueId());
            sendToHub(p);
        }
        Game g = gameOf(p.getUniqueId());
        if (g != null) g.leave(p, g.here(p.getLocation()));
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
        boolean admin = sender.hasPermission("eemitonte.admin");
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
                Game g = gameOf(p.getUniqueId());
                if (g == null) return true;
                g.leave(p, g.here(p.getLocation()));
                p.sendMessage(PREFIX + "§7Tu as quitté la Tonte Royale.");
            }
            case "start" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null || g.world == null) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cLa partie #" + g.index + " est déjà en cours."); return true; }
                if (g.building) { sender.sendMessage(PREFIX + "§cLa map est en construction."); return true; }
                if (g.inWorld().size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs dans la salle d'attente."); return true; }
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
                if (g1.running()) { sender.sendMessage(PREFIX + "§cImpossible pendant la partie #1."); return true; }
                if (g1.building) { sender.sendMessage(PREFIX + "§cConstruction déjà en cours."); return true; }
                if (modelBusy || copying > 0) { sender.sendMessage(PREFIX + "§cCopie du modèle en cours, réessaie dans un instant."); return true; }
                sender.sendMessage(PREFIX + "§7Construction de la map (partie #1), puis copie du modèle...");
                g1.rebuild(sender, this::remakeModel);
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.state != State.WAITING) { sender.sendMessage(PREFIX + "§cAttends la fin des parties en cours."); return true; }
                loadSettings();
                sender.sendMessage(PREFIX + "§aConfig rechargée.");
            }
            default -> sender.sendMessage(PREFIX + "§7/tonte <join|leave|spectate [joueur]>"
                    + (admin ? " §8- admin : start|stop [n], parties, build, reload, join <joueur>" : ""));
        }
        return true;
    }

    /** Spectateur : la partie du joueur nommé, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String name) {
        if (gameOf(p.getUniqueId()) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/tonte leave §cd'abord."); return; }
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

    /** Statut agrégé de toutes les parties pour EemiCore. playing = moutons encore en vie. */
    private void reportStatus() {
        int waiting = 0, playing = 0;
        boolean anyWaiting = false, anyPlaying = false;
        for (Game g : games.values()) {
            if (g.state == State.WAITING || g.state == State.STARTING) anyWaiting = true;
            else anyPlaying = true;
            waiting += g.pending.size();
            for (Player p : online(g.players)) {
                UUID id = p.getUniqueId();
                if (!g.running() || !g.teams.containsKey(id)) waiting++;
                else if (g.state == State.ENDING || g.alive.contains(id)) playing++;
            }
        }
        String state = anyWaiting || !anyPlaying ? "waiting" : "playing";
        String status = "status tonte " + state + " " + waiting + " " + playing + " " + (maxPlayers() * maxInstances());
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
