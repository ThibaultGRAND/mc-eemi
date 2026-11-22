package fr.eemicraft.hideseek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
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
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cache-cache d'EEMICRAFT (façon Prop Hunt / Hide and Seek d'Hypixel), sur une map « Village » construite
 * par le plugin dans son propre monde vide.
 * Les cacheurs sont invisibles et déguisés en bloc (un BlockDisplay qui les suit) ; immobiles quelques
 * secondes, ils deviennent un vrai bloc fantôme (sendBlockChange) aligné sur la grille. Les chercheurs
 * attendent aveugles pendant le temps de cachette, puis frappent les blocs suspects : un coup est détecté
 * par un lancer de rayon depuis leurs yeux (le cacheur est caché aux chercheurs, il n'a donc pas de hitbox
 * cliquable). Cacheur trouvé = chercheur (ou spectateur). Un cacheur en vie à la fin = victoire des cacheurs.
 *
 * Plusieurs parties en même temps (1.2) : partie 1 = monde « hideseek », parties n ≥ 2 = copies
 * « hideseek_n » faites depuis le modèle « hideseek_modele » (copie propre du monde d'origine).
 * Tout l'état d'une partie est dans {@link Game}.
 */
public final class EemiHideSeek extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§dCache-cache§8] §7";
    private static final String TAG = "eemihideseek";
    private static final String NAMES_TEAM = "hns_noms";

    enum State { PREPARING, WAITING, STARTING, HIDING, SEEKING, ENDING, BUILDING }

    enum Role { HIDER, SEEKER, SPECTATOR }

    /** Déguisement d'un cacheur. */
    private static final class Disguise {
        BlockData data;
        BlockDisplay display;
        /** Position de référence pour l'immobilité (ou position figée quand le bloc est solide). */
        Location anchor;
        int still;
        boolean solid;
        boolean blocked;
        Block block;
        long lastChange;
        long lastTaunt;
    }

    /** Menu de choix du bloc. */
    private static final class Menu implements InventoryHolder {
        Inventory inv;
        final List<Material> items = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private NamespacedKey kitKey;
    private final Map<Material, String> blocks = new LinkedHashMap<>();

    /** Nom du monde de la partie 1 (les copies sont base_2, base_3…, le modèle base_modele). */
    private String base = "hideseek";
    /** Parties par numéro (1 = monde d'origine). */
    private final TreeMap<Integer, Game> games = new TreeMap<>();
    /** Copie propre du monde d'origine, source des copies (personne n'y va). */
    private World model;
    /** Le modèle doit être (re)fait dès que la partie 1 est propre. */
    private boolean modelPending;
    private boolean modelBusy;
    /** Copies en cours depuis le modèle (on ne refait pas le modèle pendant ce temps). */
    private int copying;
    /** Numéros de copies en cours de suppression : pas réutilisés tant que le dossier n'est pas effacé. */
    private final Set<Integer> releasing = new java.util.HashSet<>();

    private String lastStatus;
    private long lastStatusAt;
    private long ticks;
    private final Random random = new Random();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::second, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(this, this::setupWorld, 40L);
        // statut initial pour EemiCore (une fois tous les plugins actives)
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) g.shutdown();
        // les copies restées sur le disque sont supprimées au prochain démarrage (WorldClone.cleanup)
    }

    private void loadSettings() {
        reloadConfig();
        blocks.clear();
        ConfigurationSection s = getConfig().getConfigurationSection("blocks");
        if (s != null)
            for (String k : s.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m == null || !m.isBlock()) {
                    getLogger().warning("Bloc inconnu dans la config : " + k);
                    continue;
                }
                blocks.put(m, s.getString(k, k));
            }
        if (blocks.isEmpty()) blocks.put(Material.BARREL, "Tonneau");
        getLogger().info(blocks.size() + " blocs de déguisement.");
    }

    private int maxInstances() {
        return Math.max(1, getConfig().getInt("max-instances", 3));
    }

    private String modelName() {
        return base + "_modele";
    }

    /** Crée le monde vide s'il manque, règle ses gamerules, nettoie les copies et construit la map si besoin. */
    private void setupWorld() {
        base = getConfig().getString("world", "hideseek");
        World world = Bukkit.getWorld(base);
        if (world == null) {
            world = new WorldCreator(base).generator(new VoidGenerator()).createWorld();
            getLogger().info("Monde " + base + " cree (vide).");
        }
        if (world == null) {
            getLogger().severe("Impossible de charger ou créer le monde " + base + " : cache-cache désactivé.");
            return;
        }
        Game g1 = new Game(1);
        g1.world = world;
        g1.state = State.WAITING;
        games.put(1, g1);
        prepareWorld(world);
        removeDisplays(world);
        WorldClone.cleanup(this, world, base, maxInstances());
        YamlConfiguration st = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "map.yml"));
        if (st.getInt("version", 0) != MapBuilder.VERSION) g1.build(null);
        else modelPending = true;
    }

    private void prepareWorld(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.DROWNING_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setSpawnLocation(point(world, "waiting-spawn"));
    }

    /** Générateur vide : le monde n'a que ce que le plugin construit. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 101, 75.5);
        }
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private double cfgD(String key, double def) {
        return getConfig().getDouble(key, def);
    }

    private Location point(World world, String key) {
        String[] p = getConfig().getString(key, "0.5,101,75.5").split(",");
        Location l = new Location(world, Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
        if (p.length >= 5) {
            l.setYaw(Float.parseFloat(p[3].trim()));
            l.setPitch(Float.parseFloat(p[4].trim()));
        }
        return l;
    }

    private String blockName(BlockData d) {
        String n = blocks.get(d.getMaterial());
        if (n != null) return n;
        String raw = d.getMaterial().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /** Monde d'une partie (index > 0) ou modèle : toutes les protections s'y appliquent. */
    private boolean isGameWorld(World w) {
        return w != null && (WorldClone.index(base, w.getName()) > 0 || w.getName().equals(modelName()));
    }

    // ------------------------------------------------------------------ parties : recherche, copies, modèle

    /** Partie où le joueur est inscrit (en attente, en préparation ou en jeu), sinon null. */
    private Game gameOf(Player p) {
        for (Game g : games.values()) if (g.players.contains(p.getUniqueId())) return g;
        return null;
    }

    /** Partie dont le joueur est spectateur (non inscrit), sinon null. */
    private Game spectatorGameOf(Player p) {
        for (Game g : games.values()) if (g.spectators.contains(p.getUniqueId())) return g;
        return null;
    }

    private Game gameAt(World w) {
        if (w == null) return null;
        for (Game g : games.values()) if (w.equals(g.world)) return g;
        return null;
    }

    /** Partie en attente / décompte / préparation qui a la place pour need joueurs, la plus remplie d'abord. */
    private Game joinable(int need, Game except, boolean preparing) {
        Game best = null;
        for (Game g : games.values()) {
            if (g == except) continue;
            if (g.state != State.WAITING && g.state != State.STARTING && !(preparing && g.state == State.PREPARING)) continue;
            if (g.state != State.PREPARING && g.world == null) continue;
            if (g.players.size() + need > cfg("max-players", 20)) continue;
            if (best == null || g.players.size() > best.players.size()) best = g;
        }
        return best;
    }

    /** Nouvelle partie (copie du modèle) si on est sous max-instances, sinon null. */
    private Game newGame() {
        if (games.size() >= maxInstances()) return null;
        for (int n = 2; n <= maxInstances(); n++) {
            if (games.containsKey(n) || releasing.contains(n)) continue;
            Game g = new Game(n);
            g.state = State.PREPARING;
            games.put(n, g);
            if (model != null) startCopy(g);
            return g;
        }
        return null;
    }

    /** Copie asynchrone du modèle pour la partie g ; les inscrits (restés au hub) y sont téléportés à la fin. */
    private void startCopy(Game g) {
        g.copyStarted = true;
        copying++;
        WorldClone.create(this, model, WorldClone.name(base, g.index), w -> {
            copying--;
            if (games.get(g.index) != g) {
                // partie abandonnée entre-temps
                if (w != null) WorldClone.destroy(this, w, base, this::sendToHub);
                return;
            }
            if (w == null) {
                for (Player p : online(g.players)) p.sendMessage(PREFIX + "§cImpossible de préparer une nouvelle partie, réessaie plus tard.");
                games.remove(g.index);
                reportStatus();
                return;
            }
            g.world = w;
            prepareWorld(w);
            removeDisplays(w);
            g.state = State.WAITING;
            String hub = getConfig().getString("hub-world", "lobby");
            for (UUID id : new ArrayList<>(g.players)) {
                Player p = Bukkit.getPlayer(id);
                // parti, ou passé ailleurs entre-temps : on ne l'emmène pas
                if (p == null || !p.getWorld().getName().equals(hub)) {
                    g.players.remove(id);
                    if (p != null) p.sendMessage(PREFIX + "§7Tu n'es plus au hub : inscription au cache-cache annulée.");
                    continue;
                }
                g.toWaiting(p);
                p.sendMessage(PREFIX + "§aLa partie " + g.index + " est prête !");
            }
            if (g.players.isEmpty()) destroyGame(g);
            else g.broadcast("§7Joueurs : §f" + g.players.size() + "/" + cfg("max-players", 20) + "§7.");
            reportStatus();
        });
    }

    /** Retire une copie (n ≥ 2) de la liste et supprime son monde au tick suivant. */
    private void destroyGame(Game g) {
        if (g.index <= 1) return;
        games.remove(g.index);
        for (Player p : online(g.spectators)) {
            showAll(p);
            sendToHub(p);
        }
        g.spectators.clear();
        World w = g.world;
        g.world = null;
        if (w == null) return;
        // la suppression des fichiers est asynchrone : le numéro redevient libre un peu plus tard
        int n = g.index;
        releasing.add(n);
        Bukkit.getScheduler().runTaskLater(this, () -> releasing.remove(n), 200L);
        Bukkit.getScheduler().runTask(this, () -> WorldClone.destroy(this, w, base, p -> {
            showAll(p);
            sendToHub(p);
        }));
    }

    /** (Re)fait le modèle depuis la partie 1 quand elle est propre (en attente) et qu'aucune copie n'est en cours. */
    private void maybeRemakeModel() {
        Game g1 = games.get(1);
        if (!modelPending || modelBusy || copying > 0 || g1 == null || g1.world == null) return;
        if (g1.state != State.WAITING && g1.state != State.STARTING) return;
        modelPending = false;
        World old = Bukkit.getWorld(modelName());
        model = null;
        if (old != null) {
            for (Player p : old.getPlayers()) sendToHub(p);
            if (!Bukkit.unloadWorld(old, false)) {
                getLogger().warning("Impossible de décharger l'ancien modèle " + modelName() + ", nouvel essai dans 1 s.");
                modelPending = true;
                return;
            }
        }
        modelBusy = true;
        // WorldClone.create efface l'ancien dossier puis copie le monde de la partie 1
        WorldClone.create(this, g1.world, modelName(), w -> {
            modelBusy = false;
            model = w;
            if (w == null) {
                getLogger().warning("Modèle " + modelName() + " impossible a créer : pas de partie supplémentaire pour l'instant.");
                return;
            }
            prepareWorld(w);
            removeDisplays(w);
            getLogger().info("Modèle " + modelName() + " prêt (parties en parallèle : " + maxInstances() + " max).");
        });
    }

    // ------------------------------------------------------------------ boucles

    private void second() {
        for (Game g : new ArrayList<>(games.values())) {
            if (games.get(g.index) != g) continue;
            g.second();
            // copie vide en attente : supprimée
            if (g.index >= 2 && g.players.isEmpty() && (g.state == State.WAITING || g.state == State.STARTING)) destroyGame(g);
            else if (g.index >= 2 && g.state == State.PREPARING && g.players.isEmpty() && !g.copyStarted) games.remove(g.index);
        }
        for (Game g : new ArrayList<>(games.values()))
            if (g.state == State.PREPARING && !g.copyStarted && model != null) startCopy(g);
        maybeRemakeModel();
        reportStatus();
    }

    private void tick() {
        ticks++;
        for (Game g : new ArrayList<>(games.values())) g.tick();
    }

    // ------------------------------------------------------------------ déguisements (communs)

    private void removeDisplays(World world) {
        if (world == null) return;
        for (Entity e : world.getEntities())
            if (e instanceof BlockDisplay && e.getScoreboardTags().contains(TAG)) e.remove();
    }

    private BlockData randomBlock() {
        List<Material> l = new ArrayList<>(blocks.keySet());
        return normalized(l.get(random.nextInt(l.size())).createBlockData());
    }

    private BlockData normalized(BlockData d) {
        if (d instanceof FaceAttachable fa) fa.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        return d;
    }

    private void openMenu(Player h) {
        Menu m = new Menu();
        m.items.addAll(blocks.keySet());
        int size = Math.min(54, Math.max(9, (m.items.size() + 8) / 9 * 9));
        m.inv = Bukkit.createInventory(m, size, Component.text("Choisis ton bloc", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < m.items.size() && i < size; i++) {
            Material mat = m.items.get(i);
            ItemStack icon = new ItemStack(mat.isItem() ? mat : Material.FLOWER_POT);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(blocks.get(mat), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.values());
            icon.setItemMeta(meta);
            m.inv.setItem(i, icon);
        }
        h.openInventory(m.inv);
    }

    /** Vrai si q est un cacheur d'une partie en cours (il doit rester caché aux autres). */
    private boolean hiddenHider(Player q) {
        Game g = gameOf(q);
        return g != null && (g.state == State.HIDING || g.state == State.SEEKING) && g.roles.get(q.getUniqueId()) == Role.HIDER;
    }

    /** Réaffiche p à tout le monde, et tout le monde à p sauf les cacheurs des parties en cours. */
    private void showAll(Player p) {
        for (Player q : Bukkit.getOnlinePlayers()) {
            if (q == p) continue;
            if (!hiddenHider(p)) q.showPlayer(this, p);
            if (!hiddenHider(q)) p.showPlayer(this, q);
        }
    }

    // ------------------------------------------------------------------ partie

    /** Une partie : son monde (partie 1 = monde d'origine, n ≥ 2 = copie) et tout son état. */
    private final class Game {
        final int index;
        World world;
        boolean copyStarted;

        final Set<UUID> players = new LinkedHashSet<>();
        final Map<UUID, Role> roles = new HashMap<>();
        final Map<UUID, Disguise> disguises = new HashMap<>();
        final Map<UUID, Integer> foundBy = new HashMap<>();
        final Map<UUID, Integer> taunts = new HashMap<>();
        final Map<UUID, Long> lastSwing = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Spectateurs (non inscrits) de la partie en cours. */
        final Set<UUID> spectators = new LinkedHashSet<>();

        State state = State.WAITING;
        int timer;
        int total;
        int hidersAtStart;

        Game(int index) {
            this.index = index;
        }

        Location point(String key) {
            return EemiHideSeek.this.point(world, key);
        }

        boolean running() {
            return state == State.HIDING || state == State.SEEKING || state == State.ENDING || state == State.BUILDING;
        }

        void shutdown() {
            for (Player p : online(disguises.keySet())) undisguise(p);
            disguises.clear();
            removeDisplays(world);
            for (Player p : online(players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                showAll(p);
                if (world != null && p.getWorld().equals(world)) sendToHub(p);
            }
            for (Player p : online(spectators)) {
                showAll(p);
                if (world != null && p.getWorld().equals(world)) sendToHub(p);
            }
        }

        // -------------------------------------------------------------- construction de la map (partie 1)

        void build(CommandSender who) {
            if (world == null) return;
            if (state != State.WAITING && state != State.STARTING) {
                if (who != null) who.sendMessage(PREFIX + "§cImpossible pendant une partie.");
                return;
            }
            state = State.BUILDING;
            long t0 = System.currentTimeMillis();
            List<Map.Entry<Long, BlockData>> todo = new ArrayList<>(new MapBuilder().plan().entrySet());
            getLogger().info("Construction de la map : " + todo.size() + " positions...");
            if (who != null) who.sendMessage(PREFIX + "§7Construction de la map (" + todo.size() + " positions)...");
            apply(todo, 0, who, t0);
        }

        private void apply(List<Map.Entry<Long, BlockData>> todo, int from, CommandSender who, long t0) {
            int end = Math.min(todo.size(), from + 20000);
            for (int i = from; i < end; i++) {
                long k = todo.get(i).getKey();
                Block b = world.getBlockAt(MapBuilder.kx(k), MapBuilder.ky(k), MapBuilder.kz(k));
                BlockData d = todo.get(i).getValue();
                if (!b.getBlockData().equals(d)) b.setBlockData(d, false);
            }
            if (end < todo.size()) {
                Bukkit.getScheduler().runTaskLater(EemiHideSeek.this, () -> apply(todo, end, who, t0), 1L);
                return;
            }
            File f = new File(getDataFolder(), "map.yml");
            YamlConfiguration st = new YamlConfiguration();
            st.set("version", MapBuilder.VERSION);
            try {
                st.save(f);
            } catch (IOException e) {
                getLogger().warning("map.yml : " + e.getMessage());
            }
            state = State.WAITING;
            String msg = "Map construite (" + todo.size() + " positions, " + (System.currentTimeMillis() - t0) + " ms).";
            getLogger().info(msg);
            if (who != null) who.sendMessage(PREFIX + "§a" + msg + " §7Le modèle des autres parties va être refait.");
            // les copies repartent de la nouvelle map
            modelPending = true;
            for (Player p : online(players)) toWaiting(p);
        }

        // -------------------------------------------------------------- boucles

        void second() {
            if (world == null) return;
            if (world.getTime() < 5000 || world.getTime() > 7000) world.setTime(6000);
            switch (state) {
                case WAITING -> {
                    if (inRoom().size() >= cfg("min-players", 3)) {
                        state = State.STARTING;
                        timer = cfg("countdown", 20);
                        broadcast("§eAssez de joueurs : la partie commence dans §6" + timer + " s§e.");
                    }
                }
                case STARTING -> {
                    if (inRoom().size() < cfg("min-players", 3)) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                    } else {
                        if (timer <= 5 && timer > 0)
                            for (Player p : inRoom()) {
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case HIDING -> {
                    for (Player s : online(role(Role.SEEKER)))
                        s.sendActionBar(Component.text("Les cacheurs se cachent... " + timer + " s", NamedTextColor.RED));
                    if (timer <= 0) release();
                    else timer--;
                }
                case SEEKING -> {
                    int reveal = cfg("final-reveal-time", 60);
                    if (timer == reveal) {
                        broadcastGame("§6Plus que " + reveal + " secondes ! §7Les cacheurs se font entendre...");
                        for (Player p : online(roles.keySet())) p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
                    }
                    if (timer <= reveal && timer > 0 && timer % Math.max(1, cfg("reveal-interval", 10)) == 0) revealHiders();
                    if (timer <= 0) {
                        endGame(true);
                    } else timer--;
                }
                case ENDING -> {
                    if (--timer <= 0) finishGame();
                }
                case BUILDING, PREPARING -> { }
            }
            if (games.get(index) != this) return;
            for (Player p : online(players)) {
                if (world == null || !p.getWorld().equals(world)) continue;
                updateSidebar(p);
                hideNames(p);
            }
        }

        /** Chaque tick : le déguisement suit le cacheur, immobilité, bloc solide ; renvois périodiques. */
        void tick() {
            if (world == null || (state != State.HIDING && state != State.SEEKING)) return;
            int solidTicks = (int) Math.round(cfgD("solid-delay", 3.5) * 20);
            for (Map.Entry<UUID, Disguise> en : new ArrayList<>(disguises.entrySet())) {
                Player h = Bukkit.getPlayer(en.getKey());
                Disguise d = en.getValue();
                if (h == null || !h.getWorld().equals(world)) continue;
                Location l = h.getLocation();
                if (d.solid) {
                    if (d.anchor == null || l.distanceSquared(d.anchor) > 0.0025) {
                        unsolidify(h, d);
                        d.anchor = l.clone();
                        d.still = 0;
                    }
                } else {
                    if (d.anchor == null || !d.anchor.getWorld().equals(l.getWorld()) || l.distanceSquared(d.anchor) > 0.0025) {
                        d.anchor = l.clone();
                        d.still = 0;
                        d.blocked = false;
                    } else d.still++;
                    follow(h, d);
                    if (d.still >= solidTicks && !d.blocked) solidify(h, d);
                }
                if (ticks % 5 == 0) actionBar(h, d, solidTicks);
            }
            if (ticks % 10 == 0) {
                resendFakeBlocks();
                syncDisplays();
            }
        }

        private void actionBar(Player h, Disguise d, int solidTicks) {
            Component c;
            if (d.solid) c = Component.text("SOLIDE", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                    .append(Component.text(" - ne bouge pas (" + blockName(d.data) + ")", NamedTextColor.GRAY));
            else if (d.blocked) c = Component.text("Impossible de devenir solide ici", NamedTextColor.RED);
            else if (d.still > 0) c = Component.text(String.format("Solide dans %.1f s", Math.max(0, solidTicks - d.still) / 20.0), NamedTextColor.YELLOW);
            else c = Component.text("En mouvement - " + blockName(d.data), NamedTextColor.GRAY);
            h.sendActionBar(c);
        }

        // -------------------------------------------------------------- déguisements

        private void follow(Player h, Disguise d) {
            Location l = h.getLocation();
            Location at = new Location(world, l.getX(), l.getY(), l.getZ(), 0f, 0f);
            if (d.display == null || !d.display.isValid()) {
                d.display = spawnDisplay(at, d.data);
                syncDisplay(h, d);
            } else d.display.teleport(at);
        }

        /** Display invisible par défaut : chaque spectateur autorisé le reçoit via syncDisplay (jamais les autres cacheurs). */
        private BlockDisplay spawnDisplay(Location at, BlockData data) {
            return world.spawn(at, BlockDisplay.class, e -> {
                e.setVisibleByDefault(false);
                e.setBlock(data);
                e.setTransformation(new Transformation(new Vector3f(-0.5f, 0f, -0.5f), new AxisAngle4f(), new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
                e.setTeleportDuration(2);
                e.setPersistent(false);
                e.addScoreboardTag(TAG);
            });
        }

        /**
         * Qui voit les déguisements des autres : tout le monde sauf les cacheurs (sinon un cacheur trouvé
         * saurait déjà où sont les autres en devenant chercheur).
         */
        private boolean seesHiders(Player v) {
            return roles.get(v.getUniqueId()) != Role.HIDER;
        }

        /**
         * Visibilité du BlockDisplay d'un cacheur : le cacheur voit toujours le sien ; les autres cacheurs jamais ;
         * chercheurs et spectateurs seulement quand il bouge (solide, ils ont le bloc fantôme à la place).
         * Seuls les joueurs du monde de CETTE partie sont concernés.
         */
        private void syncDisplay(Player h, Disguise d) {
            if (d.display == null || !d.display.isValid() || world == null) return;
            for (Player v : world.getPlayers()) {
                if (v == h || !d.solid && seesHiders(v)) v.showEntity(EemiHideSeek.this, d.display);
                else v.hideEntity(EemiHideSeek.this, d.display);
            }
        }

        private void syncDisplays() {
            for (Map.Entry<UUID, Disguise> en : disguises.entrySet()) {
                Player h = Bukkit.getPlayer(en.getKey());
                if (h != null) syncDisplay(h, en.getValue());
            }
        }

        /** Immobile assez longtemps : vrai bloc fantôme aligné sur la grille, envoyé aux chercheurs et spectateurs seulement. */
        private void solidify(Player h, Disguise d) {
            Location l = h.getLocation();
            int by = (int) Math.floor(l.getY() + 0.1);
            Block b = world.getBlockAt(l.getBlockX(), by, l.getBlockZ());
            boolean replaceable = b.isEmpty() || b.isReplaceable() || b.getType().name().endsWith("_CARPET");
            boolean taken = false;
            for (Disguise o : disguises.values()) if (o != d && o.solid && b.equals(o.block)) taken = true;
            if (!replaceable || taken || Math.abs(l.getY() - by) > 0.1) {
                d.blocked = true;
                return;
            }
            Location snap = new Location(world, b.getX() + 0.5, l.getY(), b.getZ() + 0.5, l.getYaw(), l.getPitch());
            h.teleport(snap);
            d.anchor = snap.clone();
            d.solid = true;
            d.block = b;
            // Le cacheur ne reçoit pas le bloc fantôme (il serait coincé dedans) : il garde le BlockDisplay, visible de lui seul.
            if (d.display == null || !d.display.isValid()) d.display = spawnDisplay(new Location(world, snap.getX(), snap.getY(), snap.getZ()), d.data);
            else d.display.teleport(new Location(world, snap.getX(), snap.getY(), snap.getZ()));
            syncDisplay(h, d);
            // les autres cacheurs ne reçoivent rien : pour eux la case reste telle qu'elle est
            for (Player v : world.getPlayers()) if (v != h && seesHiders(v)) v.sendBlockChange(b.getLocation(), d.data);
            h.playSound(h.getLocation(), Sound.BLOCK_STONE_PLACE, 0.6f, 1.4f);
        }

        /** Le cacheur bouge (ou est touché) : le bloc fantôme disparaît, le BlockDisplay revient. */
        private void unsolidify(Player h, Disguise d) {
            if (!d.solid) return;
            d.solid = false;
            if (d.block != null && world != null) {
                BlockData real = d.block.getBlockData();
                for (Player v : world.getPlayers()) v.sendBlockChange(d.block.getLocation(), real);
            }
            d.block = null;
            follow(h, d);
            syncDisplay(h, d);
        }

        /** Renvoi périodique des blocs fantômes aux chercheurs et spectateurs (jamais aux cacheurs). */
        private void resendFakeBlocks() {
            if (world == null) return;
            for (Map.Entry<UUID, Disguise> en : disguises.entrySet()) {
                Disguise d = en.getValue();
                if (!d.solid || d.block == null) continue;
                for (Player v : world.getPlayers())
                    if (!v.getUniqueId().equals(en.getKey()) && seesHiders(v)) v.sendBlockChange(d.block.getLocation(), d.data);
            }
        }

        private void disguise(Player h, BlockData data) {
            Disguise d = disguises.computeIfAbsent(h.getUniqueId(), k -> new Disguise());
            boolean wasSolid = d.solid;
            if (wasSolid) unsolidify(h, d);
            d.data = data;
            d.still = 0;
            d.blocked = false;
            d.anchor = null;
            if (d.display != null && d.display.isValid()) d.display.setBlock(data);
            else follow(h, d);
            h.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }

        /** Fin du déguisement : bloc réel renvoyé aux joueurs de la partie, display supprimé, invisibilité retirée, joueur réaffiché. */
        private void undisguise(Player h) {
            Disguise d = disguises.remove(h.getUniqueId());
            if (d != null) {
                if (d.solid && d.block != null && world != null) {
                    BlockData real = d.block.getBlockData();
                    for (Player v : world.getPlayers()) v.sendBlockChange(d.block.getLocation(), real);
                }
                if (d.display != null) d.display.remove();
            }
            // effets : seulement dans un monde du jeu ou au hub (ailleurs, ce sont ceux de ce monde, rechargés par MV-Inv)
            if (mayTouch(h)) h.removePotionEffect(PotionEffectType.INVISIBILITY);
            // h n'est plus cacheur : on peut le réafficher à tous
            for (Player v : Bukkit.getOnlinePlayers()) if (v != h) v.showPlayer(EemiHideSeek.this, h);
        }

        /** Changement de bloc demandé par le cacheur (menu ou bloc visé). */
        void changeBlock(Player h, BlockData data) {
            Disguise d = disguises.get(h.getUniqueId());
            if (d == null) return;
            long now = System.currentTimeMillis();
            long cd = state == State.SEEKING ? cfg("change-cooldown", 3) * 1000L : 500L;
            if (now - d.lastChange < cd) {
                h.sendMessage(PREFIX + "§cAttends encore " + String.format("%.1f", (cd - (now - d.lastChange)) / 1000.0) + " s avant de changer de bloc.");
                return;
            }
            d.lastChange = now;
            disguise(h, data);
            h.playSound(h.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
            h.sendMessage(PREFIX + "§7Tu es maintenant : §a" + blockName(data));
        }

        /** Tourne le déguisement : clic droit = quart de tour, clic gauche = retourner (axe, haut/bas). */
        void rotateBlock(Player h, boolean flip) {
            Disguise d = disguises.get(h.getUniqueId());
            if (d == null || d.data == null) return;
            BlockData n = d.data.clone();
            if (!flip) n.rotate(StructureRotation.CLOCKWISE_90);
            else if (n instanceof Slab sl && sl.getType() != Slab.Type.DOUBLE)
                sl.setType(sl.getType() == Slab.Type.TOP ? Slab.Type.BOTTOM : Slab.Type.TOP);
            else if (n instanceof Bisected bi && !(n instanceof org.bukkit.block.data.type.Door))
                bi.setHalf(bi.getHalf() == Bisected.Half.TOP ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            else if (n instanceof Orientable o) {
                List<org.bukkit.Axis> axes = new ArrayList<>(List.of(org.bukkit.Axis.Y, org.bukkit.Axis.X, org.bukkit.Axis.Z));
                axes.retainAll(o.getAxes());
                if (!axes.isEmpty()) o.setAxis(axes.get((axes.indexOf(o.getAxis()) + 1) % axes.size()));
            } else if (n instanceof Directional di) {
                List<BlockFace> faces = new ArrayList<>(List.of(BlockFace.NORTH, BlockFace.UP, BlockFace.DOWN));
                faces.retainAll(di.getFaces());
                BlockFace cur = di.getFacing();
                if (faces.size() > 1) di.setFacing(faces.get((faces.indexOf(cur) + 1) % faces.size()));
            }
            if (n.equals(d.data)) {
                h.sendActionBar(Component.text("Ce bloc ne peut pas " + (flip ? "se retourner" : "tourner"), NamedTextColor.RED));
                return;
            }
            d.data = n;
            if (d.display != null && d.display.isValid()) d.display.setBlock(n);
            if (d.solid && d.block != null)
                for (Player v : world.getPlayers()) if (v != h && seesHiders(v)) v.sendBlockChange(d.block.getLocation(), n);
            h.playSound(h.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        }

        void taunt(Player h) {
            Disguise d = disguises.get(h.getUniqueId());
            if (d == null) return;
            if (state != State.SEEKING) {
                h.sendMessage(PREFIX + "§7Tu pourras provoquer quand les chercheurs seront lâches.");
                return;
            }
            long now = System.currentTimeMillis();
            long cd = cfg("taunt-cooldown", 15) * 1000L;
            if (now - d.lastTaunt < cd) {
                h.sendMessage(PREFIX + "§cProvocation disponible dans " + (cd - (now - d.lastTaunt) + 999) / 1000 + " s.");
                return;
            }
            d.lastTaunt = now;
            Location at = (d.solid && d.block != null ? d.block.getLocation() : h.getLocation().getBlock().getLocation()).add(0.5, 0.6, 0.5);
            Sound[] sounds = {Sound.ENTITY_CAT_AMBIENT, Sound.ENTITY_VILLAGER_YES, Sound.ENTITY_CHICKEN_AMBIENT, Sound.ENTITY_GOAT_SCREAMING_AMBIENT, Sound.BLOCK_NOTE_BLOCK_BELL};
            world.playSound(at, sounds[random.nextInt(sounds.length)], 1.5f, 1f);
            world.spawnParticle(Particle.NOTE, at.clone().add(0, 0.8, 0), 6, 0.4, 0.3, 0.4, 1);
            world.spawnParticle(Particle.FIREWORK, at, 20, 0.3, 0.4, 0.3, 0.05);
            taunts.merge(h.getUniqueId(), 1, Integer::sum);
            int bonus = cfg("taunt-time-bonus", 5);
            if (timer > bonus + 5) timer -= bonus;
            broadcastGame("§dUn cacheur provoque les chercheurs ! §7(-" + bonus + " s)");
            h.sendMessage(PREFIX + "§d+1 point de provocation §7(total " + taunts.get(h.getUniqueId()) + ").");
        }

        /** Dernières secondes : chaque cacheur émet particules et son. */
        private void revealHiders() {
            for (Player h : online(role(Role.HIDER))) {
                Disguise d = disguises.get(h.getUniqueId());
                Location at = (d != null && d.solid && d.block != null ? d.block.getLocation() : h.getLocation().getBlock().getLocation()).add(0.5, 0.6, 0.5);
                world.spawnParticle(Particle.FIREWORK, at, 25, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.4, 0.4, 0.4, 0);
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, 1.6f);
            }
        }

        // -------------------------------------------------------------- coups des chercheurs

        /** Coup d'un chercheur : lancer de rayon contre les déguisements des cacheurs de CETTE partie. */
        void swing(Player s) {
            if (state != State.SEEKING || roles.get(s.getUniqueId()) != Role.SEEKER || !s.getWorld().equals(world)) return;
            long now = System.currentTimeMillis();
            Long last = lastSwing.get(s.getUniqueId());
            if (last != null && now - last < cfg("hit-cooldown-ms", 400)) return;
            double range = cfgD("hit-range", 4.0);
            Location eye = s.getEyeLocation();
            Vector start = eye.toVector(), dir = eye.getDirection();
            RayTraceResult wall = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
            double max = wall == null ? range : wall.getHitPosition().distance(start);
            Player best = null;
            double bestDist = max + 0.01;
            for (Player h : online(role(Role.HIDER))) {
                Disguise d = disguises.get(h.getUniqueId());
                if (d == null || !h.getWorld().equals(world)) continue;
                BoundingBox box;
                if (d.solid && d.block != null) box = BoundingBox.of(d.block);
                else {
                    Location l = h.getLocation();
                    box = new BoundingBox(l.getX() - 0.55, l.getY(), l.getZ() - 0.55, l.getX() + 0.55, l.getY() + 1.05, l.getZ() + 0.55);
                }
                RayTraceResult r = box.rayTrace(start, dir, range);
                if (r == null) continue;
                double dist = r.getHitPosition().distance(start);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = h;
                }
            }
            if (best == null) return;
            lastSwing.put(s.getUniqueId(), now);
            hit(s, best);
        }

        private void hit(Player seeker, Player h) {
            Disguise d = disguises.get(h.getUniqueId());
            if (d == null) return;
            Location at = (d.solid && d.block != null ? d.block.getLocation().add(0.5, 0.5, 0.5) : h.getLocation().add(0, 0.5, 0));
            if (d.solid) unsolidify(h, d);
            d.still = 0;
            d.anchor = null;
            world.spawnParticle(Particle.BLOCK, at, 25, 0.3, 0.3, 0.3, d.data);
            world.playSound(at, Sound.ENTITY_PLAYER_HURT, 1f, 1f);
            double hp = h.getHealth() - cfgD("hit-damage", 5.0);
            if (hp <= 0) {
                found(h, seeker);
                return;
            }
            h.setHealth(hp);
            h.playHurtAnimation(0f);
            seeker.sendActionBar(Component.text("Touché ! ", NamedTextColor.GOLD)
                    .append(Component.text(String.format("%.1f", hp / 2) + " cœurs restants", NamedTextColor.GRAY)));
        }

        /** Cacheur trouvé : il devient chercheur (ou spectateur). */
        private void found(Player h, Player seeker) {
            undisguise(h);
            foundBy.merge(seeker.getUniqueId(), 1, Integer::sum);
            eemi("reward " + seeker.getName() + " hideseek kill");
            world.strikeLightningEffect(h.getLocation());
            broadcastGame("§c" + h.getName() + " §7a été trouvé par §e" + seeker.getName() + "§7 !");
            if ("spectator".equalsIgnoreCase(getConfig().getString("found-becomes", "seeker"))) {
                roles.put(h.getUniqueId(), Role.SPECTATOR);
                if (mayTouch(h)) {
                    h.getInventory().clear();
                    h.setGameMode(GameMode.SPECTATOR);
                }
                h.showTitle(Title.title(Component.text("Trouvé !", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("Tu regardes la fin de la partie", NamedTextColor.GRAY)));
            } else {
                roles.put(h.getUniqueId(), Role.SEEKER);
                heal(h);
                equipSeeker(h);
                h.showTitle(Title.title(Component.text("Trouvé !", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("Tu deviens chercheur !", NamedTextColor.YELLOW)));
                h.sendMessage(PREFIX + "§eTu deviens chercheur : §ftrouve les autres cacheurs !");
            }
            refreshVisibility();
            reportStatus();
            checkEnd();
        }

        // -------------------------------------------------------------- déroulement

        List<Player> inRoom() {
            List<Player> l = new ArrayList<>();
            if (world == null) return l;
            for (Player p : online(players)) if (p.getWorld().equals(world)) l.add(p);
            return l;
        }

        Set<UUID> role(Role r) {
            Set<UUID> s = new LinkedHashSet<>();
            for (Map.Entry<UUID, Role> e : roles.entrySet()) if (e.getValue() == r) s.add(e.getKey());
            return s;
        }

        void startGame() {
            List<Player> list = inRoom();
            if (list.size() < 2) {
                state = State.WAITING;
                return;
            }
            Collections.shuffle(list, random);
            int seekers = Math.max(1, list.size() / Math.max(1, cfg("players-per-seeker", 5)));
            seekers = Math.min(seekers, list.size() - 1);
            roles.clear();
            disguises.clear();
            foundBy.clear();
            taunts.clear();
            lastSwing.clear();
            removeDisplays(world);
            world.setTime(6000);
            total = list.size();
            for (int i = 0; i < list.size(); i++) {
                Player p = list.get(i);
                resetPlayer(p, GameMode.ADVENTURE);
                p.setAllowFlight(true);
                if (i < seekers) {
                    roles.put(p.getUniqueId(), Role.SEEKER);
                    p.teleport(point("seeker-room"));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 6, false, false, false));
                    p.showTitle(Title.title(Component.text("CHERCHEUR", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                            Component.text("Attends " + cfg("hide-time", 30) + " s, puis cherche !", NamedTextColor.GRAY)));
                    p.sendMessage(PREFIX + "§cTu es chercheur : §7attends " + cfg("hide-time", 30) + " s, puis frappe les blocs suspects.");
                } else {
                    roles.put(p.getUniqueId(), Role.HIDER);
                    p.teleport(point("hider-spawn"));
                    disguise(p, randomBlock());
                    giveHiderItems(p);
                    p.showTitle(Title.title(Component.text("CACHEUR", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                            Component.text("Choisis ton bloc et cache-toi !", NamedTextColor.GRAY)));
                    Bukkit.getScheduler().runTaskLater(EemiHideSeek.this, () -> {
                        if (p.isOnline() && roles.get(p.getUniqueId()) == Role.HIDER) openMenu(p);
                    }, 10L);
                }
            }
            hidersAtStart = list.size() - seekers;
            state = State.HIDING;
            refreshVisibility();
            timer = cfg("hide-time", 30);
            reportStatus();
            broadcastGame("§eLa partie commence ! §7" + seekers + " chercheur(s), " + (list.size() - seekers)
                    + " cacheur(s). Les chercheurs sont lâches dans §f" + timer + " s§7.");
            broadcastGame("§7Cacheurs : §fclic droit avec l'améthyste §7sur un bloc pour le copier, dans le vide pour le menu. Bâton de blaze pour tourner le bloc. Immobile = bloc solide.");
        }

        /** Fin du temps de cachette : les chercheurs arrivent sur la place avec une épée. */
        private void release() {
            state = State.SEEKING;
            timer = cfg("game-time", 300);
            for (Player s : online(role(Role.SEEKER))) {
                s.removePotionEffect(PotionEffectType.BLINDNESS);
                s.removePotionEffect(PotionEffectType.SLOWNESS);
                s.teleport(point("seeker-spawn"));
                equipSeeker(s);
                s.showTitle(Title.title(Component.text("CHERCHEZ !", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("Frappe les blocs suspects", NamedTextColor.GRAY)));
                s.playSound(s.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);
            }
            broadcastGame("§cLes chercheurs sont lâches ! §7Temps : §f" + clock(timer) + "§7.");
        }

        /**
         * Personne ne voit le joueur d'un cacheur : chercheurs, spectateurs et autres joueurs du monde n'ont que son bloc,
         * les autres cacheurs n'ont rien du tout (ni joueur, ni display, ni bloc fantôme). Appelée à chaque changement
         * de rôle (début, cacheur trouvé, arrivée d'un spectateur) : un cacheur devenu chercheur reçoit alors les
         * displays et les blocs fantômes des cacheurs restants. Ne touche qu'aux joueurs du monde de cette partie.
         */
        void refreshVisibility() {
            if (world == null) return;
            Set<UUID> hiders = role(Role.HIDER);
            for (Player v : world.getPlayers())
                for (Player q : world.getPlayers()) {
                    if (v == q) continue;
                    if (hiders.contains(q.getUniqueId())) v.hidePlayer(EemiHideSeek.this, q);
                    else v.showPlayer(EemiHideSeek.this, q);
                }
            syncDisplays();
            resendFakeBlocks();
        }

        void checkEnd() {
            if (state != State.HIDING && state != State.SEEKING) return;
            int hiders = online(role(Role.HIDER)).size(), seekers = online(role(Role.SEEKER)).size();
            if (hiders == 0) endGame(false);
            else if (seekers == 0) endGame(true);
        }

        private void endGame(boolean hidersWin) {
            endGame(hidersWin, true);
        }

        /** Fin de partie : tous les déguisements sont retirés, titre des vainqueurs. rewards = false pour un arrêt admin. */
        void endGame(boolean hidersWin, boolean rewards) {
            if (state != State.HIDING && state != State.SEEKING) return;
            List<Player> survivors = online(role(Role.HIDER));
            if (rewards) {
                // gagnants : les cacheurs encore caches, ou tous les chercheurs (y compris les cacheurs trouves)
                Role winners = hidersWin ? Role.HIDER : Role.SEEKER;
                for (Map.Entry<UUID, Role> en : roles.entrySet()) {
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p == null) continue;
                    eemi("reward " + p.getName() + " hideseek play");
                    if (en.getValue() == winners) eemi("reward " + p.getName() + " hideseek win");
                }
            }
            state = State.ENDING;
            reportStatus();
            timer = cfg("end-time", 8);
            for (Player h : online(new LinkedHashSet<>(disguises.keySet()))) undisguise(h);
            disguises.clear();
            removeDisplays(world);
            for (Player p : online(roles.keySet())) {
                showAll(p);
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.SPEED);
            }
            StringBuilder names = new StringBuilder();
            for (Player h : survivors) names.append(names.length() == 0 ? "" : ", ").append(h.getName());
            // titres courts (sinon tronqués à l'écran) : les noms des survivants sont dans le chat
            Component title = hidersWin
                    ? Component.text("Les cacheurs", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                    : Component.text("Les chercheurs", NamedTextColor.RED).decorate(TextDecoration.BOLD);
            Component sub = Component.text("gagnent ! " + (hidersWin
                    ? (survivors.isEmpty() ? "Plus aucun chercheur" : survivors.size() + " cacheur(s) en vie")
                    : "Tous trouvés"), NamedTextColor.GRAY);
            for (Player p : online(roles.keySet())) {
                p.showTitle(Title.title(title, sub));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            broadcastGame(hidersWin ? "§aLes cacheurs gagnent !" + (survivors.isEmpty() ? "" : " §7Survivants : §f" + names)
                    : "§cLes chercheurs gagnent ! §7Tous les cacheurs ont été trouvés.");
            UUID best = null;
            int bestFound = 0;
            for (Map.Entry<UUID, Integer> e : foundBy.entrySet()) if (e.getValue() > bestFound) { bestFound = e.getValue(); best = e.getKey(); }
            if (best != null && Bukkit.getPlayer(best) != null)
                broadcastGame("§7Meilleur chercheur : §e" + Bukkit.getPlayer(best).getName() + " §7(" + bestFound + "trouvé(s))");
            UUID taunter = null;
            int bestTaunts = 0;
            for (Map.Entry<UUID, Integer> e : taunts.entrySet()) if (e.getValue() > bestTaunts) { bestTaunts = e.getValue(); taunter = e.getKey(); }
            if (taunter != null && Bukkit.getPlayer(taunter) != null)
                broadcastGame("§7Roi de la provocation : §d" + Bukkit.getPlayer(taunter).getName() + " §7(" + bestTaunts + " point(s))");
        }

        /**
         * Après l'écran de fin. Partie 1 : tout le monde en salle d'attente (comme avant). Copie : les inscrits vont
         * dans une autre partie en attente qui a la place (puis la copie est détruite) ; sinon la copie devient une
         * nouvelle partie en attente (détruite dès qu'elle est vide).
         */
        void finishGame() {
            roles.clear();
            disguises.clear();
            removeDisplays(world);
            state = State.WAITING;
            for (Player p : online(spectators)) {
                showAll(p);
                sendToHub(p);
                p.sendMessage(PREFIX + "§7Partie terminée, retour au hub.");
            }
            spectators.clear();
            if (index >= 2) {
                Game other = players.isEmpty() ? null : joinable(players.size(), this, false);
                if (other != null || players.isEmpty()) {
                    List<UUID> moved = new ArrayList<>(players);
                    players.clear();
                    for (UUID id : moved) boards.remove(id);
                    destroyGame(this);
                    if (other != null)
                        for (UUID id : moved) {
                            Player p = Bukkit.getPlayer(id);
                            if (p == null) continue;
                            other.players.add(id);
                            other.toWaiting(p);
                            p.sendMessage(PREFIX + "§7Tu rejoins la salle d'attente de la partie " + other.index + ".");
                        }
                    reportStatus();
                    return;
                }
            }
            for (Player p : online(players)) toWaiting(p);
            reportStatus();
        }

        void forceStop() {
            if (state == State.HIDING || state == State.SEEKING) endGame(false, false);
            if (state == State.ENDING) finishGame();
            if (state == State.STARTING) state = State.WAITING;
        }

        // -------------------------------------------------------------- inscription

        void toWaiting(Player p) {
            Location l = point("waiting-spawn");
            showAll(p);
            p.teleport(l);
            later(() -> {
                if (!p.isOnline() || !players.contains(p.getUniqueId()) || world == null || !mayTouch(p)) return;
                // téléporté d'abord, réinitialisé ensuite (jamais vider puis téléporter)
                if (!p.getWorld().equals(world)) p.teleport(l);
                if (!p.getWorld().equals(world)) return;
                resetPlayer(p, GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED), "quit"), "Quitter le cache-cache", NamedTextColor.RED));
                updateSidebar(p);
            });
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            if (state == State.PREPARING) {
                // encore au hub : rien à remettre en place
                if (!copyStarted && players.isEmpty()) games.remove(index);
                reportStatus();
                return;
            }
            if (disguises.containsKey(id)) undisguise(p);
            Role r = roles.remove(id);
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            showAll(p);
            // parti par changement de monde (teleport = false) : il est déjà dans le monde d'arrivée,
            // on ne touche plus à lui (MV-Inv a rechargé inventaire et effets de ce monde ; vol : EemiCore)
            if (teleport) sendToHub(p);
            if (r != null && r != Role.SPECTATOR) {
                broadcastGame("§c" + p.getName() + " §7a quitté la partie.");
                checkEnd();
            }
            if (index >= 2 && players.isEmpty() && (state == State.WAITING || state == State.STARTING) && games.get(index) == this)
                destroyGame(this);
            reportStatus();
        }

        /** Regarder la partie en cours en spectateur, au-dessus de la place du village. Les cacheurs lui restent invisibles. */
        void spectate(Player p) {
            Location view = point("seeker-spawn").add(0, 20, 18);
            view.setYaw(180f);
            view.setPitch(45f);
            spectators.add(p.getUniqueId());
            // téléporté d'abord (il peut venir du skyblock), puis vidé dans le monde de la partie
            p.teleport(view);
            if (mayTouch(p)) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
            }
            later(this::refreshVisibility);
            p.sendMessage(PREFIX + "§7Tu regardes la partie " + index + ", §f/lobby §7pour revenir.");
            p.sendMessage(PREFIX + "§cNe révèle pas les cachettes dans le chat !");
        }

        // -------------------------------------------------------------- scoreboard latéral et pseudos cachés

        private Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("hns", Criteria.DUMMY,
                        Component.text("CACHE", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                                .append(Component.text("-CACHE", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                Team t = b.registerNewTeam(NAMES_TEAM);
                t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        /** Pseudos cachés : tous les inscrits de la partie dans l'équipe sans pseudo, sur le scoreboard de chaque inscrit. */
        private void hideNames(Player viewer) {
            Team t = board(viewer).getTeam(NAMES_TEAM);
            if (t == null) return;
            for (Player q : online(players)) if (!t.hasEntry(q.getName())) t.addEntry(q.getName());
            for (String entry : new ArrayList<>(t.getEntries())) {
                Player q = Bukkit.getPlayerExact(entry);
                if (q == null || !players.contains(q.getUniqueId())) t.removeEntry(entry);
            }
        }

        private void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("hns");
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
            Role r = roles.get(p.getUniqueId());
            boolean playing = r != null && (state == State.HIDING || state == State.SEEKING || state == State.ENDING);
            if (!playing) {
                l.add(kv("Joueurs : ", inRoom().size() + "/" + cfg("max-players", 20), NamedTextColor.WHITE));
                l.add(kv("Minimum : ", String.valueOf(cfg("min-players", 3)), NamedTextColor.WHITE));
                l.add(kv("Map : ", "Village", NamedTextColor.GREEN));
                if (maxInstances() > 1) l.add(kv("Partie : ", "#" + index, NamedTextColor.WHITE));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(kv("Début dans ", timer + " s", NamedTextColor.GREEN));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else if (state == State.BUILDING) l.add(Component.text("Construction de la map...", NamedTextColor.GOLD));
                else l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
            } else {
                l.add(switch (r) {
                    case HIDER -> kv("Rôle : ", "Cacheur", NamedTextColor.GREEN);
                    case SEEKER -> kv("Rôle : ", "Chercheur", NamedTextColor.RED);
                    case SPECTATOR -> kv("Rôle : ", "Spectateur", NamedTextColor.DARK_GRAY);
                });
                l.add(Component.text("  "));
                l.add(kv("Cacheurs restants : ", online(role(Role.HIDER)).size() + "/" + hidersAtStart, NamedTextColor.GREEN));
                l.add(kv("Chercheurs : ", String.valueOf(online(role(Role.SEEKER)).size()), NamedTextColor.RED));
                if (state == State.HIDING) l.add(kv("Cachette : ", timer + " s", NamedTextColor.YELLOW));
                else if (state == State.SEEKING) l.add(kv("Temps : ", clock(timer), timer <= cfg("final-reveal-time", 60) ? NamedTextColor.GOLD : NamedTextColor.WHITE));
                else l.add(Component.text("Fin de la partie", NamedTextColor.GOLD));
                Disguise d = disguises.get(p.getUniqueId());
                if (d != null) {
                    l.add(kv("Bloc : ", blockName(d.data), NamedTextColor.AQUA));
                    if (d.solid) l.add(Component.text("Solide", NamedTextColor.GREEN));
                    l.add(kv("Provocations : ", String.valueOf(taunts.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.LIGHT_PURPLE));
                } else if (r == Role.SEEKER) l.add(kv("Trouvés : ", String.valueOf(foundBy.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.YELLOW));
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        // -------------------------------------------------------------- messages de la partie

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        void broadcastGame(String msg) {
            for (Player p : online(roles.keySet())) p.sendMessage(PREFIX + msg);
        }

        String stateName() {
            return switch (state) {
                case PREPARING -> "preparation";
                case WAITING -> "attente";
                case STARTING -> "décompte " + timer + " s";
                case HIDING -> "cachette " + timer + " s";
                case SEEKING -> "recherche " + clock(timer);
                case ENDING -> "fin";
                case BUILDING -> "construction";
            };
        }
    }

    // ------------------------------------------------------------------ inscription (choix de la partie)

    private void join(Player p) {
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (games.get(1) == null) { p.sendMessage(PREFIX + "§cLa map n'est pas chargée."); return; }
        Game sg = spectatorGameOf(p);
        if (sg != null) {
            sg.spectators.remove(p.getUniqueId());
            showAll(p);
        }
        Game g = joinable(1, null, true);
        boolean created = false;
        if (g == null) {
            g = newGame();
            created = g != null;
        }
        if (g == null) { p.sendMessage(PREFIX + "§cToutes les parties sont pleines, réessaie dans un instant."); return; }
        g.players.add(p.getUniqueId());
        if (g.state == State.PREPARING) {
            p.sendMessage(PREFIX + (created ? "§ePréparation d'une nouvelle partie... §7tu y seras téléporté."
                    : "§eUne nouvelle partie se prépare : §7tu y seras téléporté."));
            reportStatus();
            return;
        }
        g.toWaiting(p);
        g.broadcast("§a" + p.getName() + " §7a rejoint le cache-cache (§f" + g.inRoom().size() + "/" + cfg("max-players", 20) + "§7).");
        reportStatus();
    }

    /**
     * Le plugin ne touche au joueur (inventaire, armure, XP, vie, faim, effets, mode de jeu) que s'il est
     * dans un monde du jeu (partie, copies, modèle) ou au hub : ailleurs (skyblock...) c'est l'inventaire
     * de CE monde que Multiverse-Inventories sauvegarderait.
     */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        return isGameWorld(w) || w.getName().equals(getConfig().getString("hub-world", "lobby"));
    }

    /**
     * Renvoi au hub. Joueur dans un monde du jeu : réinitialisé dans ce monde puis téléporté (MV-Inv sauvegarde
     * l'inventaire du jeu et recharge celui du hub, objets du hub compris). Déjà au hub : réinitialisé sur place.
     * Ailleurs (skyblock...) : ni téléporté ni réinitialisé.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        p.setAllowFlight(false);
        World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    private void resetPlayer(Player p, GameMode mode) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        heal(p);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    private void heal(Player p) {
        if (!mayTouch(p)) return;
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5);
    }

    private void equipSeeker(Player s) {
        if (!mayTouch(s)) return;
        s.getInventory().clear();
        ItemStack sword = kit(new ItemStack(Material.IRON_SWORD), "sword");
        ItemMeta meta = sword.getItemMeta();
        meta.setUnbreakable(true);
        meta.displayName(Component.text("Épée du chercheur", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        sword.setItemMeta(meta);
        s.getInventory().setItem(0, sword);
        s.getInventory().setHeldItemSlot(0);
        // le chercheur court plus vite que les cacheurs (seeker-speed = niveau de Vitesse, 0 = aucun)
        int speed = cfg("seeker-speed", 2);
        if (speed > 0) s.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, speed - 1, false, false, true));
    }

    private void giveHiderItems(Player h) {
        if (!mayTouch(h)) return;
        h.getInventory().clear();
        h.getInventory().setItem(0, named(kit(new ItemStack(Material.AMETHYST_SHARD), "change"), "Changer de bloc", NamedTextColor.LIGHT_PURPLE,
                "Clic droit sur un bloc : le copier", "Clic droit dans le vide : menu"));
        h.getInventory().setItem(1, named(kit(new ItemStack(Material.FIREWORK_STAR), "taunt"), "Provoquer", NamedTextColor.GOLD,
                "Son + particules : +1 point", "et le temps restant baisse"));
        h.getInventory().setItem(2, named(kit(new ItemStack(Material.BLAZE_ROD), "rotate"), "Tourner le bloc", NamedTextColor.AQUA,
                "Clic droit : quart de tour", "Clic gauche : retourner (haut/bas, axe)"));
        h.getInventory().setHeldItemSlot(0);
    }

    private static String clock(int s) {
        s = Math.max(0, s);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static Component kv(String k, String v, NamedTextColor c) {
        return Component.text(k, NamedTextColor.GRAY).append(Component.text(v, c));
    }

    // ------------------------------------------------------------------ objets

    private ItemStack named(ItemStack it, String name, NamedTextColor color, String... lore) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack kit(ItemStack it, String type) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, type);
        it.setItemMeta(meta);
        return it;
    }

    private String kitType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ événements

    private boolean inWorld(Block b) {
        return isGameWorld(b.getWorld());
    }

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemihideseek.admin");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (inWorld(e.getBlock()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (inWorld(e.getBlock()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (inWorld(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (inWorld(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (inWorld(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (inWorld(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (inWorld(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isGameWorld(e.getLocation().getWorld())
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    /** Aucun dégât dans les mondes du jeu (les coups des chercheurs passent par le lancer de rayon). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && (gameOf(p) != null || isGameWorld(p.getWorld())))
            e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && (gameOf(p) != null || isGameWorld(p.getWorld())))
            e.setCancelled(true);
    }

    /** Coup d'un chercheur : lancer de rayon contre les déguisements de sa partie. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Game g = gameOf(e.getPlayer());
        if (g != null) g.swing(e.getPlayer());
    }

    /** Chercheurs immobiles pendant le temps de cachette (la tête peut tourner). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g == null || g.state != State.HIDING || g.roles.get(e.getPlayer().getUniqueId()) != Role.SEEKER) return;
        Location f = e.getFrom(), t = e.getTo();
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            Location back = f.clone();
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            e.setTo(back);
        }
    }

    /** Vol autorisé (évite le kick « flying » sur les blocs fantômes) mais jamais activé. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (!e.isFlying() || g == null || !g.roles.containsKey(p.getUniqueId())) return;
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) return;
        e.setCancelled(true);
        p.setFlying(false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        boolean here = isGameWorld(p.getWorld());
        Game g = gameOf(p);
        if (!here && g == null) return;
        if (here && !admin(p) && (e.getAction() == Action.PHYSICAL || e.getAction() == Action.RIGHT_CLICK_BLOCK)) e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        String type = kitType(e.getItem());
        if (type == null) return;
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean hider = g != null && g.roles.get(p.getUniqueId()) == Role.HIDER;
        switch (type) {
            case "quit" -> {
                if (!right) return;
                e.setCancelled(true);
                if (g != null) g.leave(p, true);
                else sendToHub(p);
                p.sendMessage(PREFIX + "§7Tu as quitté le cache-cache.");
            }
            case "change" -> {
                e.setCancelled(true);
                if (!hider) return;
                if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    Block b = e.getClickedBlock();
                    if (b == null) return;
                    if (!blocks.containsKey(b.getType()) && !getConfig().getBoolean("allow-any-target", false)) {
                        p.sendMessage(PREFIX + "§cCe bloc n'est pas autorisé. §7Clic droit dans le vide pour voir la liste.");
                        return;
                    }
                    g.changeBlock(p, b.getBlockData().clone());
                } else if (right) openMenu(p);
            }
            case "rotate" -> {
                e.setCancelled(true);
                if (e.getAction() != Action.PHYSICAL && hider) g.rotateBlock(p, !right);
            }
            case "taunt" -> {
                e.setCancelled(true);
                if (right && hider) g.taunt(p);
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (isGameWorld(e.getPlayer().getWorld()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Game g = gameOf(p);
        if (e.getInventory().getHolder() instanceof Menu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory()) return;
            int slot = e.getRawSlot();
            if (slot < 0 || slot >= m.items.size() || g == null || g.roles.get(p.getUniqueId()) != Role.HIDER) return;
            Material mat = m.items.get(slot);
            p.closeInventory();
            g.changeBlock(p, normalized(mat.createBlockData()));
            return;
        }
        if (g != null && (g.roles.containsKey(p.getUniqueId()) || g.world != null && p.getWorld().equals(g.world))) {
            if (!admin(p)) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (g != null && g.world != null && p.getWorld().equals(g.world) && !admin(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null && g.roles.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player sp = e.getPlayer();
        for (Game g : new ArrayList<>(games.values()))
            if (g.spectators.contains(sp.getUniqueId()) && (g.world == null || !sp.getWorld().equals(g.world))) {
                // spectateur parti (/lobby) : il redevient un joueur normal et revoit tout le monde
                g.spectators.remove(sp.getUniqueId());
                showAll(sp);
                // mode de jeu : seulement s'il arrive au hub (ailleurs, celui du monde, via Multiverse)
                if (sp.getGameMode() == GameMode.SPECTATOR && mayTouch(sp)) sp.setGameMode(GameMode.SURVIVAL);
            }
        Game g = gameOf(sp);
        if (g == null) return;
        if (g.state == State.PREPARING) {
            // il est parti du hub pendant la préparation : il ne sera pas emmené
            g.leave(sp, false);
            sp.sendMessage(PREFIX + "§7Inscription au cache-cache annulée.");
        } else if (g.world != null && e.getFrom().equals(g.world)) g.leave(sp, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game sg = spectatorGameOf(e.getPlayer());
        if (sg != null) {
            sg.spectators.remove(e.getPlayer().getUniqueId());
            showAll(e.getPlayer());
            sendToHub(e.getPlayer());
        }
        Game g = gameOf(e.getPlayer());
        if (g != null) g.leave(e.getPlayer(), g.state != State.PREPARING);
    }

    /** Quelqu'un qui se reconnecte dans un monde du jeu sans être inscrit retourne au hub. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        later(() -> {
            if (!p.isOnline()) return;
            if (isGameWorld(p.getWorld()) && gameOf(p) == null && !p.hasPermission("eemihideseek.admin")) sendToHub(p);
            // un joueur qui arrive ne doit pas voir les cacheurs
            for (Game g : games.values())
                if (g.state == State.HIDING || g.state == State.SEEKING) g.refreshVisibility();
        });
    }

    // ------------------------------------------------------------------ commandes

    /** Partie visée par une commande admin : numéro donné, sinon celle de l'admin, sinon la partie 1. */
    private Game target(CommandSender sender, String[] args) {
        if (args.length > 1) {
            try {
                return games.get(Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (sender instanceof Player p) {
            Game g = gameOf(p);
            if (g == null) g = spectatorGameOf(p);
            if (g != null) return g;
        }
        return games.get(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemihideseek.admin");
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
                if (p != null && spectatorGameOf(p) != null) { sendToHub(p); return true; }
                Game g = p == null ? null : gameOf(p);
                if (g == null) return true;
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté le cache-cache.");
            }
            case "start" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) { sender.sendMessage(PREFIX + "§cPartie introuvable (§f/hideseek parties§c)."); return true; }
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cLa partie " + g.index + " est déjà en cours."); return true; }
                if (g.inRoom().size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs en salle d'attente (partie " + g.index + ")."); return true; }
                g.startGame();
                sender.sendMessage(PREFIX + "§7Partie " + g.index + " lancée.");
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) { sender.sendMessage(PREFIX + "§cPartie introuvable (§f/hideseek parties§c)."); return true; }
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie " + g.index + " arrêtée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ") :");
                for (Game g : games.values())
                    sender.sendMessage("§7 #" + g.index + " §f" + (g.world == null ? WorldClone.name(base, g.index) + " (copie en cours)" : g.world.getName())
                            + " §7- " + g.stateName() + " - §f" + g.players.size() + "§7 inscrit(s), §f" + g.spectators.size() + "§7 spectateur(s)");
                sender.sendMessage("§7 Modèle : §f" + (model != null ? model.getName() : modelBusy || modelPending ? "en préparation" : "absent"));
            }
            case "build" -> {
                if (!admin) return true;
                Game g1 = games.get(1);
                if (g1 == null) return true;
                g1.build(sender);
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.state != State.WAITING && g.state != State.PREPARING) { sender.sendMessage(PREFIX + "§cAucune partie ne doit être en cours."); return true; }
                loadSettings();
                for (Game g : games.values()) if (g.world != null) prepareWorld(g.world);
                if (model != null) prepareWorld(model);
                sender.sendMessage(PREFIX + "§aConfig rechargée.");
            }
            default -> sender.sendMessage(PREFIX + "§7/hideseek <join|leave|spectate [joueur]>" + (admin ? " §8| admin : start [n], stop [n], parties, build, reload" : ""));
        }
        return true;
    }

    /** Spectateur : avec un pseudo, la partie de ce joueur ; sans, la partie en cours la plus remplie. */
    private void spectate(Player p, String name) {
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/hideseek leave §cd'abord."); return; }
        Game g = null;
        if (name != null) {
            Player t = Bukkit.getPlayerExact(name);
            if (t == null) { p.sendMessage(PREFIX + "§cJoueur introuvable."); return; }
            g = gameOf(t);
            if (g == null) g = spectatorGameOf(t);
            if (g == null || g.state != State.HIDING && g.state != State.SEEKING) { p.sendMessage(PREFIX + "§c" + t.getName() + " n'est dans aucune partie en cours."); return; }
        } else {
            for (Game c : games.values())
                if ((c.state == State.HIDING || c.state == State.SEEKING) && (g == null || c.roles.size() > g.roles.size())) g = c;
            if (g == null) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return; }
        }
        Game old = spectatorGameOf(p);
        if (old != null) old.spectators.remove(p.getUniqueId());
        g.spectate(p);
    }

    // ------------------------------------------------------------------ EemiCore

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /**
     * Statut agrégé pour EemiCore (envoyé s'il a changé, ou toutes les 30 s) : waiting si une partie attend ou
     * décompte, sinon playing si une partie joue ; joueurs en attente / en jeu additionnés ; max = max par partie
     * × nombre de parties possibles.
     */
    private void reportStatus() {
        boolean anyWaiting = false, anyPlaying = false;
        int waiting = 0, playing = 0;
        for (Game g : games.values()) {
            switch (g.state) {
                case PREPARING, WAITING, STARTING -> anyWaiting = true;
                case HIDING, SEEKING, ENDING -> anyPlaying = true;
                default -> { }
            }
            boolean inGame = g.state == State.HIDING || g.state == State.SEEKING || g.state == State.ENDING;
            for (Player p : online(g.players)) {
                Role r = g.roles.get(p.getUniqueId());
                if (inGame && r != null) {
                    if (r != Role.SPECTATOR) playing++;
                } else waiting++;
            }
        }
        String st = anyWaiting ? "waiting" : anyPlaying ? "playing" : "waiting";
        String status = "status hideseek " + st + " " + waiting + " " + playing + " " + cfg("max-players", 20) * maxInstances();
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
