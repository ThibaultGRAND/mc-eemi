package fr.eemicraft.jumpleague;

import fr.eemicraft.jumpleague.CourseGen.Box;
import fr.eemicraft.jumpleague.CourseGen.Course;
import fr.eemicraft.jumpleague.CourseGen.Place;
import fr.eemicraft.jumpleague.CourseGen.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * JumpLeague d'EEMICRAFT, façon GommeHD.
 * Phase 1 (parcours) : chaque joueur a son couloir ; 10 modules générés au hasard (3 faciles, 3 normaux,
 * 3 difficiles, 1 ultra), identiques pour tous, chacun fini par un checkpoint et doté d'un coffre de
 * l'Ender (équipement meilleur dans les modules durs). Kit de départ : cuir complet + épée en bois.
 * Tomber = retour au dernier checkpoint. Finir = bottes en diamant ; sans jamais tomber = casque en
 * diamant en plus. Phase 2 (deathmatch) dès la fin du temps ou dès le premier arrivé : arène commune,
 * on garde son équipement, 3 vies, le dernier en vie gagne. À 60 s de la fin, un phare s'allume sur l'un
 * des 3 points de l'arène ; à la fin, le joueur dans son cercle qui a le plus de kills gagne.
 *
 * Le plugin crée son monde vide et y construit tout : 12 couloirs (x -110..110, départ en z 0, vers +z),
 * l'arène (centre 0, -100) et la salle d'attente (y 170). Plusieurs parties en même temps comme les autres
 * jeux (max-instances, copies « jumpleague_n » du modèle « jumpleague_modele »). Nouveau parcours après
 * chaque partie ; sa graine est gardée dans le monde pour pouvoir l'effacer au redémarrage.
 */
public final class EemiJumpLeague extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§cJumpLeague§8] §7";

    /** Sous-titre d'égalité : les pseudos s'ils tiennent (2 ou 3 joueurs), sinon le nombre de gagnants. */
    static String tieSubtitle(List<Player> win) {
        if (win.size() <= 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < win.size(); i++)
                sb.append(i == 0 ? "" : i == win.size() - 1 ? " et " : ", ").append(win.get(i).getName());
            if (sb.length() <= 34) return sb.toString();
        }
        return win.size() + " gagnants";
    }
    private static final String GAME_ID = "jumpleague";
    private static final String HEART = "❤";
    private static final int MODULES = CourseGen.MODULES;

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Phase { JUMP, DEATHMATCH }

    // ------------------------------------------------------------------ géométrie du monde

    /** 12 couloirs espacés de 20 blocs : centres x -110, -90, ..., 110. */
    private static final int LANES = 12;
    /** Ordre d'attribution des couloirs : ceux du milieu d'abord (les joueurs se voient). */
    private static final int[] LANE_ORDER = {5, 6, 4, 7, 3, 8, 2, 9, 1, 10, 0, 11};
    private static final DyeColor[] LANE_COLORS = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME,
            DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA,
            DyeColor.PINK, DyeColor.WHITE};
    /** Arène du deathmatch : centre et rayon. */
    private static final int AX = 0, AZ = -100, AR = 30;
    /** Les 3 points du phare (relatifs au centre de l'arène, à 17 blocs, à 120° les uns des autres). */
    private static final int[][] BEACONS = {{0, 17}, {-15, -8}, {15, -8}};
    /** Centre de la salle d'attente (en x 0). */
    private static final int LZ = -30;

    private static int laneX(int lane) {
        return (2 * lane - (LANES - 1)) * 10;
    }

    // ------------------------------------------------------------------ état global (pas d'état de partie ici)

    private NamespacedKey kitKey, entityKey, seedKey, genKey;
    /** Nom du monde de la partie 1 (config « world »). */
    private String base = "jumpleague";
    private int floorY, lobbyY;
    /** Parties par numéro (1 = monde d'origine, n ≥ 2 = copies). */
    private final Map<Integer, Game> games = new TreeMap<>();
    /** Modèle propre (copie de la partie 1) d'où partent les copies ; personne n'y va. */
    private World model;
    private Course modelCourse;
    private boolean modelBusy;
    private int modelGen;
    private long ticks;
    private String lastStatus;
    private long lastStatusAt;
    private final Random random = new Random();
    private final Map<String, BlockData> states = new HashMap<>();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        entityKey = new NamespacedKey(this, "entity");
        seedKey = new NamespacedKey(this, "course_seed");
        genKey = new NamespacedKey(this, "course_gen");
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        World w = setupWorld();
        if (w != null) {
            WorldClone.cleanup(this, w, base, maxInstances());
            Game g1 = new Game(1, w);
            games.put(1, g1);
            g1.prepareWorld();
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Game g1 = games.get(1);
            if (g1 != null && g1.world != null) {
                g1.restoreCourse();
                g1.buildStatic();
                g1.build();
                makeModel();
            }
        }, 20L);
        // statut initial pour EemiCore (une fois tous les plugins activés)
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) {
            for (Player p : online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            }
            for (Player p : online(g.spectators)) if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            g.players.clear();
            g.spectators.clear();
            g.pending.clear();
            if (g.sign != null) g.sign.remove();
            if (g.world != null && g.beaconOn) g.resetBeacon();
        }
        // les copies restées sur le disque sont supprimées au prochain démarrage (WorldClone.cleanup)
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "jumpleague");
        floorY = cfg("floor-y", 100);
        lobbyY = cfg("lobby-y", 170);
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private int maxPlayers() {
        return Math.max(1, Math.min(LANES, cfg("max-players", 12)));
    }

    private String modelName() {
        return base + "_modele";
    }

    /** Charge ou crée le monde vide de la partie 1. */
    private World setupWorld() {
        World w = Bukkit.getWorld(base);
        if (w == null) {
            w = new WorldCreator(base).generator(new VoidGenerator()).createWorld();
            if (w != null) getLogger().info("Monde " + base + " cree (vide).");
        }
        if (w == null) getLogger().severe("Impossible de charger ou créer le monde " + base + " : JumpLeague désactivé.");
        return w;
    }

    /** Générateur vide : le monde n'a que ce que le plugin construit. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 171, LZ + 4.5);
        }
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private BlockData state(String s) {
        return states.computeIfAbsent(s, Bukkit::createBlockData);
    }

    // ------------------------------------------------------------------ modèle et copies

    /**
     * (Re)fait le modèle depuis la partie 1 (qui doit être propre : juste construite). L'ancien modèle est
     * déchargé sans sauvegarde ; WorldClone.create supprime son dossier avant d'y recopier la partie 1.
     * Jamais pendant la préparation d'une copie (elle lit le dossier du modèle) : on réessaie 2 s après.
     */
    private void makeModel() {
        Game g1 = games.get(1);
        if (g1 == null || g1.world == null) return;
        for (Game g : games.values())
            if (g.preparing) {
                Bukkit.getScheduler().runTaskLater(this, this::makeModel, 40L);
                return;
            }
        int gen = ++modelGen;
        modelBusy = true;
        model = null;
        World old = Bukkit.getWorld(modelName());
        if (old != null) {
            for (Player p : old.getPlayers()) sendToHub(p);
            if (!Bukkit.unloadWorld(old, false)) getLogger().warning("Impossible de décharger l'ancien modèle " + modelName() + ".");
        }
        Course course = g1.course;
        WorldClone.create(this, g1.world, modelName(), w -> {
            if (gen != modelGen) return; // un modèle plus récent est en route
            modelBusy = false;
            if (w == null) {
                getLogger().warning("Modèle " + modelName() + " impossible a créer : une seule partie possible.");
                return;
            }
            model = w;
            modelCourse = course;
            prepareRules(w);
            getLogger().info("Modèle " + modelName() + " prêt.");
        });
    }

    /** Nouvelle partie dans une copie du modèle (numéro libre le plus bas), ou null si impossible. */
    private Game newCopy() {
        if (model == null || modelBusy || modelCourse == null) return null;
        int idx = -1;
        for (int n = 2; n <= maxInstances(); n++)
            if (!games.containsKey(n)) { idx = n; break; }
        if (idx < 0) return null;
        Game g = new Game(idx, null);
        g.preparing = true;
        games.put(idx, g);
        Course course = modelCourse;
        WorldClone.create(this, model, WorldClone.name(base, idx), w -> {
            if (g.destroyed) {
                if (w != null) WorldClone.destroy(this, w, base, this::sendToHub);
                return;
            }
            if (w == null) {
                games.remove(g.index);
                g.destroyed = true;
                for (Player p : online(g.pending)) p.sendMessage(PREFIX + "§cImpossible de préparer une nouvelle partie, réessaie plus tard.");
                g.pending.clear();
                reportStatus();
                return;
            }
            g.world = w;
            g.course = course;
            g.prepareWorld();
            g.computeSpawns();
            g.preparing = false;
            World hub = hubWorld();
            for (Player p : online(g.pending)) {
                if (hub != null && !p.getWorld().equals(hub)) {
                    p.sendMessage(PREFIX + "§7Tu as quitté le hub : la nouvelle partie ne t'attend pas.");
                    continue;
                }
                g.addPlayer(p);
            }
            g.pending.clear();
            reportStatus();
        });
        return g;
    }

    /** Détruit une copie vide (jamais la partie 1). */
    private void destroyGame(Game g) {
        if (g.index <= 1 || g.destroyed) return;
        g.destroyed = true;
        games.remove(g.index);
        if (g.sign != null) g.sign.remove();
        for (Player p : online(g.players)) g.leave(p, true);
        for (Player p : online(g.spectators)) sendToHub(p);
        g.spectators.clear();
        if (g.world != null) WorldClone.destroy(this, g.world, base, this::sendToHub);
        getLogger().info("Partie " + g.index + " terminée : copie " + WorldClone.name(base, g.index) + " supprimée.");
        reportStatus();
    }

    /** Règles d'un monde du jeu (partie ou modèle) : jour fixe, pas de mobs, on garde son inventaire. */
    private void prepareRules(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setTime(cfg("time", 6000));
        world.setStorm(false);
        world.setThundering(false);
    }

    // ------------------------------------------------------------------ recherche des parties

    /** Monde d'une partie ou modèle : toutes les protections s'y appliquent. */
    private boolean isGameWorld(World w) {
        return w != null && (WorldClone.index(base, w.getName()) > 0 || w.getName().equals(modelName()));
    }

    /** Partie dont c'est le monde, ou null. */
    private Game gameAt(World w) {
        if (w == null) return null;
        Game g = games.get(WorldClone.index(base, w.getName()));
        return g != null && w.equals(g.world) ? g : null;
    }

    /** Partie où le joueur est inscrit (hors attente de copie). */
    private Game gameOf(Player p) {
        for (Game g : games.values()) if (g.players.contains(p.getUniqueId())) return g;
        return null;
    }

    private Game pendingOf(Player p) {
        for (Game g : games.values()) if (g.pending.contains(p.getUniqueId())) return g;
        return null;
    }

    private Game spectatorGame(Player p) {
        for (Game g : games.values()) if (g.spectators.contains(p.getUniqueId())) return g;
        return null;
    }

    /** Partie précise (« start 2 »), sinon celle de l'admin, sinon la partie 1. */
    private Game targetGame(CommandSender sender, String[] args) {
        if (args.length > 1) {
            try {
                return games.get(Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (sender instanceof Player p) {
            Game g = gameOf(p);
            if (g == null) g = spectatorGame(p);
            if (g != null) return g;
        }
        return games.get(1);
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        ticks++;
        for (Game g : new ArrayList<>(games.values())) {
            if (g.world == null || g.destroyed) continue;
            g.tick();
        }
        if (ticks % 20 == 0) {
            // copie vide en attente : détruite (la prochaine repartira du modèle)
            for (Game g : new ArrayList<>(games.values()))
                if (g.index > 1 && !g.preparing && !g.destroyed && g.state == State.WAITING
                        && g.players.isEmpty() && g.pending.isEmpty() && g.spectators.isEmpty())
                    destroyGame(g);
            reportStatus();
        }
    }

    // ------------------------------------------------------------------ une partie

    final class Game {
        final int index;
        World world;
        boolean preparing, destroyed;
        /** Parcours posé dans le monde (null tant qu'il n'est pas construit). */
        Course course;
        /** Points d'apparition du deathmatch (anneau autour du centre de l'arène). */
        final List<Location> spawns = new ArrayList<>();

        final Set<UUID> players = new LinkedHashSet<>();
        /** Joueurs qui attendent au hub que la copie soit prête. */
        final Set<UUID> pending = new LinkedHashSet<>();
        final Set<UUID> inGame = new LinkedHashSet<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> spectators = new LinkedHashSet<>();
        final Map<UUID, Integer> lane = new HashMap<>();
        /** Dernier checkpoint atteint (0 = départ, 10 = arrivée). */
        final Map<UUID, Integer> checkpoint = new HashMap<>();
        final Map<UUID, Integer> falls = new HashMap<>();
        /** Ordre d'arrivée du parcours. */
        final List<UUID> finished = new ArrayList<>();
        /** Coffres ouverts : joueur → module → contenu (on peut y revenir). */
        final Map<UUID, Map<Integer, Inventory>> chests = new HashMap<>();
        final Map<UUID, Integer> lives = new HashMap<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        final Map<UUID, Long> protectedUntil = new HashMap<>();
        final Map<UUID, Long> backCooldown = new HashMap<>();
        /** Ordre d'élimination (le dernier éliminé en dernier). */
        final List<UUID> outOrder = new ArrayList<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();

        State state = State.WAITING;
        Phase phase = Phase.JUMP;
        int timer;
        int freezeTicks;
        int phaseLeft;
        /** Secondes avant le deathmatch après le premier arrivé (-1 = personne n'est arrivé). */
        int graceLeft = -1;
        int total;
        int beaconIdx = -1;
        boolean beaconOn;
        TextDisplay sign;

        Game(int index, World world) {
            this.index = index;
            this.world = world;
        }

        void prepareWorld() {
            prepareRules(world);
            world.setSpawnLocation(lobbySpawn());
        }

        // -------------------------------------------------------------- positions

        Location lobbySpawn() {
            Location l = new Location(world, 0.5, lobbyY + 1, LZ + 4.5);
            l.setYaw(180);
            return l;
        }

        Location spectatorSpot() {
            Location l;
            if (phase == Phase.DEATHMATCH && running()) {
                l = new Location(world, AX + 0.5, floorY + 22, AZ + AR + 6.5);
                l.setYaw(180);
            } else {
                l = new Location(world, 0.5, floorY + 30, -14.5);
                l.setYaw(0);
            }
            l.setPitch(35);
            return l;
        }

        /** Sur le checkpoint k du couloir du joueur, tourné vers la suite du parcours (+z). */
        Location checkpointLoc(UUID id, int k) {
            Box b = course.checkpoints.get(Math.max(0, Math.min(MODULES, k)));
            int ln = lane.getOrDefault(id, 0);
            Location l = new Location(world, laneX(ln) + b.cx(), b.base() + 1, b.cz());
            l.setYaw(0);
            l.setPitch(0);
            return l;
        }

        Location beaconLoc(int i) {
            return new Location(world, AX + BEACONS[i][0] + 0.5, floorY + 1, AZ + BEACONS[i][1] + 0.5);
        }

        /** Point du deathmatch le plus éloigné des autres joueurs en vie, parmi quelques tirages. */
        Location respawnSpot(Player self) {
            if (spawns.isEmpty()) return new Location(world, AX + 0.5, floorY + 3, AZ + 0.5);
            Location best = null;
            double bestD = -1;
            for (int i = 0; i < 12; i++) {
                Location c = spawns.get(random.nextInt(spawns.size()));
                double d = Double.MAX_VALUE;
                for (Player o : online(alive)) {
                    if (o.equals(self) || !o.getWorld().equals(world)) continue;
                    d = Math.min(d, o.getLocation().distanceSquared(c));
                }
                if (d > bestD) { bestD = d; best = c; }
            }
            return best.clone();
        }

        // -------------------------------------------------------------- construction

        void set(int x, int y, int z, Material m) {
            world.getBlockAt(x, y, z).setType(m, false);
        }

        /** Parcours sauvé dans le monde (graine) : on le régénère pour pouvoir l'effacer. */
        void restoreCourse() {
            PersistentDataContainer pdc = world.getPersistentDataContainer();
            Long seed = pdc.get(seedKey, PersistentDataType.LONG);
            Integer gen = pdc.get(genKey, PersistentDataType.INTEGER);
            if (seed != null && gen != null && gen == CourseGen.VERSION) course = CourseGen.generate(seed);
        }

        void loadChunks(int x0, int x1, int z0, int z1) {
            for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
                for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) world.getChunkAt(cx, cz).load(true);
        }

        /**
         * Nouveau parcours : on efface l'ancien (connu par sa graine, sinon on vide la zone des couloirs),
         * on pose le nouveau dans les 12 couloirs (béton de la couleur du couloir pour départ et checkpoints),
         * et on garde sa graine dans le monde.
         */
        void build() {
            long seed = random.nextLong();
            Course nc = CourseGen.generate(seed);
            int minX = laneX(0) - 8, maxX = laneX(LANES - 1) + 8;
            if (course != null) {
                loadChunks(minX, maxX, course.minZ, course.maxZ);
                for (int ln = 0; ln < LANES; ln++)
                    for (Place p : course.places) set(laneX(ln) + p.x(), p.y(), p.z(), Material.AIR);
            } else {
                clearLanes(minX, maxX);
            }
            loadChunks(minX, maxX, nc.minZ, nc.maxZ);
            for (int ln = 0; ln < LANES; ln++) {
                BlockData lc = Material.valueOf(LANE_COLORS[ln].name() + "_CONCRETE").createBlockData();
                for (Place p : nc.places) {
                    BlockData d;
                    try {
                        d = p.state() == null ? lc : state(p.state());
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("Bloc invalide ignore : " + p.state());
                        continue;
                    }
                    world.getBlockAt(laneX(ln) + p.x(), p.y(), p.z()).setBlockData(d, false);
                }
            }
            course = nc;
            world.getPersistentDataContainer().set(seedKey, PersistentDataType.LONG, seed);
            world.getPersistentDataContainer().set(genKey, PersistentDataType.INTEGER, CourseGen.VERSION);
            world.setSpawnLocation(lobbySpawn());
            getLogger().info("Parcours JumpLeague pose (partie " + index + ", " + world.getName() + ") : " + nc.jumps + " sauts, "
                    + nc.ladders + " échelles, " + nc.chests.size() + " coffres, z 0 a " + nc.maxZ + ", y " + nc.minY + " a " + nc.maxY
                    + ", " + nc.places.size() + " blocs par couloir.");
        }

        /** Sans graine connue (premier démarrage, algorithme changé) : tout ce qui traîne dans les couloirs part. */
        void clearLanes(int minX, int maxX) {
            int removed = 0;
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
                for (int cz = -1; cz <= 460 >> 4; cz++) {
                    var chunk = world.getChunkAt(cx, cz);
                    ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++)
                            for (int y = 80; y <= 150; y++) {
                                if (snap.getBlockType(x, y, z).isAir()) continue;
                                chunk.getBlock(x, y, z).setType(Material.AIR, false);
                                removed++;
                            }
                }
            if (removed > 0) getLogger().info("Ancien parcours efface : " + removed + " blocs.");
        }

        /** Arène du deathmatch et salle d'attente (démarrage, /jumpleague build). */
        void buildStatic() {
            buildArena();
            buildLobby();
            computeSpawns();
            ensureSign();
        }

        /**
         * Arène : île ronde flottante (rayon 30) en herbe, bordée d'un muret de 2 blocs, colline centrale en
         * briques de pierre avec 4 colonnes, 6 piliers de couverture, buissons, et les 3 socles de fer du phare.
         */
        void buildArena() {
            int F = floorY, ext = AR + 6;
            loadChunks(AX - ext, AX + ext, AZ - ext, AZ + ext);
            for (int x = -ext; x <= ext; x++)
                for (int z = -ext; z <= ext; z++)
                    for (int y = F - 14; y <= F + 16; y++) {
                        Block b = world.getBlockAt(AX + x, y, AZ + z);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
            Random rr = new Random(1234);
            for (int x = -AR; x <= AR; x++)
                for (int z = -AR; z <= AR; z++) {
                    double d = Math.hypot(x, z);
                    if (d > AR + 0.5) continue;
                    int depth = (int) (2 + (AR - d) * 0.3) + rr.nextInt(2);
                    for (int y = F - depth; y < F; y++)
                        set(AX + x, y, AZ + z, y >= F - 2 ? Material.DIRT : Material.STONE);
                    int n = rr.nextInt(100);
                    set(AX + x, F, AZ + z, n < 8 ? Material.MOSS_BLOCK : n < 13 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
                    if (d > AR - 1) {
                        set(AX + x, F + 1, AZ + z, Material.MOSSY_COBBLESTONE);
                        set(AX + x, F + 2, AZ + z, Material.MOSSY_COBBLESTONE_WALL);
                    }
                }
            // colline centrale : deux terrasses de briques de pierre
            for (int x = -6; x <= 6; x++)
                for (int z = -6; z <= 6; z++) {
                    double d = Math.hypot(x, z);
                    int lvl = d <= 3.5 ? 2 : d <= 6.2 ? 1 : 0;
                    for (int y = 1; y <= lvl; y++)
                        set(AX + x, F + y, AZ + z, rr.nextInt(5) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                }
            for (int[] c : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}})
                for (int y = F + 2; y <= F + 4; y++) set(AX + c[0], y, AZ + c[1], y == F + 4 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
            // piliers de couverture (2x2, 4 de haut) à 23 blocs du centre
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(i * 60);
                int px = (int) Math.round(Math.cos(a) * 23), pz = (int) Math.round(Math.sin(a) * 23);
                for (int dx = 0; dx <= 1; dx++)
                    for (int dz = 0; dz <= 1; dz++)
                        for (int y = F + 1; y <= F + 4; y++)
                            set(AX + px + dx, y, AZ + pz + dz, y == F + 4 ? Material.STONE_BRICK_SLAB
                                    : rr.nextInt(3) == 0 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS);
            }
            // buissons (feuilles persistantes), loin des points du phare et de la colline
            BlockData leaves = state("minecraft:oak_leaves[persistent=true]");
            int bushes = 0;
            for (int tries = 0; tries < 200 && bushes < 10; tries++) {
                double a = rr.nextDouble() * Math.PI * 2, r = 10 + rr.nextDouble() * 10;
                int bx = (int) Math.round(Math.cos(a) * r), bz = (int) Math.round(Math.sin(a) * r);
                boolean far = true;
                for (int[] bc : BEACONS) if (Math.hypot(bx - bc[0], bz - bc[1]) < 8) far = false;
                if (!far) continue;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        for (int y = F + 1; y <= F + 2; y++)
                            if (y == F + 1 || (dx == 0 || dz == 0)) world.getBlockAt(AX + bx + dx, y, AZ + bz + dz).setBlockData(leaves, false);
                bushes++;
            }
            // socles du phare : fer 3x3 dans le sol, phare posé seulement à l'allumage
            for (int[] bc : BEACONS)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        set(AX + bc[0] + dx, F, AZ + bc[1] + dz, Material.IRON_BLOCK);
                        for (int y = F + 1; y <= F + 3; y++) set(AX + bc[0] + dx, y, AZ + bc[1] + dz, Material.AIR);
                    }
            beaconOn = false;
            beaconIdx = -1;
            getLogger().info("Arène du deathmatch construite (partie " + index + ") : centre " + AX + " " + F + " " + AZ + ", rayon " + AR + ".");
        }

        /** Salle d'attente vitrée (comme les autres jeux), en vert. */
        void buildLobby() {
            int L = lobbyY;
            loadChunks(-9, 9, LZ - 9, LZ + 9);
            for (int x = -9; x <= 9; x++)
                for (int z = -9; z <= 9; z++)
                    for (int y = L - 1; y <= L + 7; y++) set(x, y, LZ + z, Material.AIR);
            for (int x = -7; x <= 7; x++)
                for (int z = -7; z <= 7; z++) {
                    int d = Math.max(Math.abs(x), Math.abs(z));
                    if (d <= 2) set(x, L, LZ + z, Material.GLASS);
                    else if (d == 3) set(x, L, LZ + z, Material.SEA_LANTERN);
                    else if (d < 7) set(x, L, LZ + z, Math.floorMod(x + z, 2) == 0 ? Material.WHITE_CONCRETE : Material.LIME_CONCRETE);
                    else {
                        set(x, L, LZ + z, Material.SMOOTH_QUARTZ);
                        for (int y = L + 1; y <= L + 4; y++) set(x, y, LZ + z, Material.LIME_STAINED_GLASS);
                    }
                    set(x, L + 5, LZ + z, Material.BARRIER);
                }
        }

        /** Points d'apparition du deathmatch : 12 sur un anneau de 26 blocs, sur le sol réel. */
        void computeSpawns() {
            spawns.clear();
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(15 + i * 30);
                int x = AX + (int) Math.round(Math.cos(a) * 26), z = AZ + (int) Math.round(Math.sin(a) * 26);
                int y = floorY;
                for (int yy = floorY + 6; yy >= floorY - 2; yy--)
                    if (world.getBlockAt(x, yy, z).getType().isSolid()) { y = yy; break; }
                Location l = new Location(world, x + 0.5, y + 1, z + 0.5);
                l.setDirection(new Vector(AX - x, 0, AZ - z));
                l.setPitch(0);
                spawns.add(l);
            }
        }

        /** Panneau flottant de la salle d'attente (non sauvegardé : recréé s'il a disparu). */
        void ensureSign() {
            if (world == null || (sign != null && sign.isValid())) return;
            Location l = new Location(world, 0.5, lobbyY + 2.8, LZ - 5.5);
            if (!l.isChunkLoaded()) return;
            sign = world.spawn(l, TextDisplay.class, td -> {
                tag(td);
                td.text(Component.text("JUMPLEAGUE", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .append(Component.newline())
                        .append(Component.text("10 modules de parcours, un coffre par module.", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Puis deathmatch : " + cfg("lives", 3) + " vies, le dernier en vie gagne.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Fini sans tomber = casque en diamant !", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false)));
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(false);
            });
        }

        // -------------------------------------------------------------- boucle

        void tick() {
            if (state == State.PLAYING) gameTick();
            if (destroyed) return;
            if (ticks % 20 == 0) second();
            if (destroyed) return;
            if (ticks % 5 == 0) safety();
        }

        void second() {
            if (world.getTime() != cfg("time", 6000)) world.setTime(cfg("time", 6000));
            int min = Math.max(1, cfg("min-players", 2));
            switch (state) {
                case WAITING -> {
                    if (players.size() >= min && course != null) {
                        state = State.STARTING;
                        timer = cfg("countdown", 20);
                        broadcast("§eAssez de joueurs : la partie commence dans §6" + timer + " s§e !");
                    }
                }
                case STARTING -> {
                    if (players.size() < min) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                    } else {
                        if (players.size() >= maxPlayers()) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0)
                            for (Player p : online(players)) {
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> {
                    if (freezeTicks <= 0) {
                        phaseLeft--;
                        if (phase == Phase.JUMP) {
                            if (phaseLeft == 300 || phaseLeft == 120 || phaseLeft == 60 || phaseLeft == 30 || phaseLeft == 10)
                                broadcast("§eDeathmatch dans §6" + clock(phaseLeft) + "§e !");
                            if (graceLeft > 0) {
                                graceLeft--;
                                if (graceLeft > 0 && graceLeft <= 5) broadcast("§eDeathmatch dans §6" + graceLeft + " s§e !");
                            }
                            if (phaseLeft <= 0 || graceLeft == 0) startDeathmatch();
                        } else {
                            if (phaseLeft == cfg("beacon-time", 60)) activateBeacon();
                            if (phaseLeft == 30 || phaseLeft == 10) broadcast("§eFin de la partie dans §6" + phaseLeft + " s§e !");
                            if (phaseLeft <= 0) timeUp();
                        }
                    }
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (!players.isEmpty()) ensureSign();
            for (Player p : online(players)) updateSidebar(p);
            if (state == State.PLAYING) for (Player p : online(inGame)) actionBar(p);
        }

        /** Toutes les 5 ticks : un joueur du monde qui tombe hors partie revient à sa place. */
        void safety() {
            for (Player p : world.getPlayers()) {
                if (alive.contains(p.getUniqueId()) && state == State.PLAYING) continue;
                if (p.getGameMode() == GameMode.CREATIVE) continue;
                double y = p.getLocation().getY();
                boolean lost = y < floorY - 40;
                boolean lobbyFall = !running() && players.contains(p.getUniqueId()) && y < lobbyY - 3;
                if (!lost && !lobbyFall) continue;
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                if (players.contains(p.getUniqueId()) && !running()) p.teleport(lobbySpawn());
                else if (p.getGameMode() == GameMode.SPECTATOR || running()) p.teleport(spectatorSpot());
                else p.teleport(lobbySpawn());
            }
        }

        void gameTick() {
            if (freezeTicks > 0) {
                freezeTicks--;
                if (freezeTicks % 20 == 0) {
                    int s = freezeTicks / 20;
                    boolean dm = phase == Phase.DEATHMATCH;
                    for (Player p : online(inGame)) {
                        Component t = s > 0 ? Component.text(String.valueOf(s), dm ? NamedTextColor.RED : NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                                : Component.text(dm ? "COMBAT !" : "GO !", dm ? NamedTextColor.RED : NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
                        Component sub = s > 0 ? Component.text(dm ? "Deathmatch" : "Prepare-toi...", NamedTextColor.GRAY)
                                : Component.text(dm ? cfg("lives", 3) + " vies, le dernier gagne" : "Saute jusqu'à l'arrivée !", NamedTextColor.YELLOW);
                        p.showTitle(Title.title(t, sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                        p.playSound(p, s > 0 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_ENDER_DRAGON_GROWL, s > 0 ? 1f : 0.5f, s > 0 ? 1f : 1.4f);
                    }
                }
                return;
            }
            if (phase == Phase.JUMP) {
                if (ticks % 2 == 0) jumpTick();
            } else {
                for (Player p : online(alive))
                    if (p.getWorld().equals(world) && p.getLocation().getY() < floorY - 8) {
                        die(p, killerOf(p));
                        if (state != State.PLAYING) return;
                    }
                if (beaconOn && ticks % 10 == 0) beaconParticles();
            }
        }

        /** Checkpoints atteints et chutes, pour chaque joueur sur son couloir. */
        void jumpTick() {
            for (Player p : online(alive)) {
                if (state != State.PLAYING || phase != Phase.JUMP) return;
                if (!p.getWorld().equals(world)) continue;
                UUID id = p.getUniqueId();
                Location l = p.getLocation();
                double rx = l.getX() - laneX(lane.getOrDefault(id, 0));
                int cp = checkpoint.getOrDefault(id, 0);
                for (int k = cp + 1; k <= MODULES; k++)
                    if (course.checkpoints.get(k).contains(rx, l.getY(), l.getZ())) {
                        reach(p, k);
                        cp = k;
                    }
                if (state != State.PLAYING || phase != Phase.JUMP) return;
                double fy = cp < MODULES ? course.fallY[cp] : course.finishFallY;
                if (l.getY() < fy) fall(p, false);
            }
        }

        // -------------------------------------------------------------- parcours

        void reach(Player p, int k) {
            UUID id = p.getUniqueId();
            checkpoint.put(id, k);
            if (k >= MODULES) {
                finish(p);
                return;
            }
            Tier next = CourseGen.tierOf(k);
            p.showTitle(Title.title(Component.text("Checkpoint " + k + "/" + MODULES, NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    Component.text("Module suivant : " + next.label, tierColor(next)),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 12, 0.4, 0.5, 0.4, 0.05);
        }

        /** Arrivée : bottes en diamant, casque en diamant si aucune chute ; le premier lance le deathmatch. */
        void finish(Player p) {
            UUID id = p.getUniqueId();
            if (finished.contains(id)) return;
            finished.add(id);
            int f = falls.getOrDefault(id, 0);
            equip(p, EquipmentSlot.FEET, new ItemStack(Material.DIAMOND_BOOTS));
            if (f == 0) equip(p, EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));
            p.showTitle(Title.title(Component.text("Arrivée !", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                    Component.text(f == 0 ? "Sans tomber : bottes + casque diamant" : "Bottes en diamant gagnées", NamedTextColor.AQUA),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            firework(p.getLocation());
            broadcast("§6" + p.getName() + " §ea fini le parcours §7(" + finished.size() + (finished.size() == 1 ? "er" : "e")
                    + ", " + f + " chute" + (f > 1 ? "s" : "") + ")" + (f == 0 ? " §bsans tomber !" : "§e."));
            if (finished.size() == 1) {
                int grace = Math.max(0, cfg("finish-grace", 0));
                if (grace == 0) Bukkit.getScheduler().runTask(EemiJumpLeague.this, () -> {
                    if (state == State.PLAYING && phase == Phase.JUMP && !destroyed) startDeathmatch();
                });
                else {
                    graceLeft = grace;
                    broadcast("§eDeathmatch dans §6" + grace + " s §e: dépêche-toi !");
                }
            }
        }

        /** Chute (ou retour volontaire) : retour au dernier checkpoint, compte comme une chute. */
        void fall(Player p, boolean voluntary) {
            UUID id = p.getUniqueId();
            int f = falls.merge(id, 1, Integer::sum);
            p.setFallDistance(0);
            p.setVelocity(new Vector());
            p.teleport(checkpointLoc(id, checkpoint.getOrDefault(id, 0)));
            p.setFireTicks(0);
            p.playSound(p, voluntary ? Sound.ENTITY_ENDERMAN_TELEPORT : Sound.ENTITY_PLAYER_HURT, 0.8f, 1f);
            p.sendActionBar(Component.text((voluntary ? "Retour au checkpoint" : "Chute !") + " (" + f + " au total)", NamedTextColor.RED));
        }

        /** Coffre de l'Ender du module m : contenu tiré à la première ouverture, propre à chaque joueur. */
        void openChest(Player p, int m) {
            Map<Integer, Inventory> mine = chests.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            Inventory inv = mine.get(m);
            Tier t = CourseGen.tierOf(m);
            if (inv == null) {
                inv = Bukkit.createInventory(null, 27, Component.text("Coffre - module " + (m + 1) + " (" + t.label + ")"));
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < 27; i++) slots.add(i);
                Collections.shuffle(slots, random);
                List<ItemStack> items = loot(t);
                for (int i = 0; i < items.size(); i++) inv.setItem(slots.get(i), items.get(i));
                mine.put(m, inv);
            }
            p.openInventory(inv);
            p.playSound(p, Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1f);
        }

        // -------------------------------------------------------------- deathmatch

        int livesOf(UUID id) {
            return lives.getOrDefault(id, 0);
        }

        boolean isProtected(Player p) {
            return protectedUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
        }

        Player killerOf(Player p) {
            Long t = lastHitTime.get(p.getUniqueId());
            if (t == null || System.currentTimeMillis() - t > cfg("kill-credit-time", 10) * 1000L) return null;
            Player k = Bukkit.getPlayer(lastHit.get(p.getUniqueId()));
            return k != null && inGame.contains(k.getUniqueId()) ? k : null;
        }

        void startDeathmatch() {
            if (state != State.PLAYING || phase != Phase.JUMP) return;
            phase = Phase.DEATHMATCH;
            phaseLeft = Math.max(cfg("beacon-time", 60) + 10, cfg("deathmatch-time", 300));
            freezeTicks = Math.max(0, cfg("deathmatch-freeze", 5)) * 20 + 1;
            graceLeft = -1;
            List<Player> list = online(alive);
            Collections.shuffle(list, random);
            List<Location> order = new ArrayList<>(spawns);
            int startLives = Math.max(1, cfg("lives", 3));
            for (int i = 0; i < list.size(); i++) {
                Player p = list.get(i);
                UUID id = p.getUniqueId();
                lives.put(id, startLives);
                p.closeInventory();
                removeKitItems(p);
                heal(p);
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                Location at = order.isEmpty() ? respawnSpot(p)
                        : order.get((int) Math.round((double) i * order.size() / list.size()) % order.size()).clone();
                p.teleport(at);
            }
            broadcast("§c§lDeathmatch ! §7" + startLives + " vies chacun, le dernier en vie gagne. §8(" + clock(phaseLeft) + ")");
            if (finished.isEmpty()) broadcast("§7Personne n'a fini le parcours.");
            for (Player p : online(spectators)) p.teleport(spectatorSpot());
            reportStatus();
        }

        /** Mort au deathmatch : -1 vie, kill au tueur, réapparition protégée ou élimination. */
        void die(Player p, Player killer) {
            UUID id = p.getUniqueId();
            if (!alive.contains(id) || phase != Phase.DEATHMATCH) return;
            lastHit.remove(id);
            lastHitTime.remove(id);
            int left = Math.max(0, livesOf(id) - 1);
            lives.put(id, left);
            String rest = left > 0 ? left + " vie" + (left > 1 ? "s" : "") : "elimine";
            if (killer != null && !killer.equals(p) && inGame.contains(killer.getUniqueId())) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " " + GAME_ID + " kill");
                killer.playSound(killer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                broadcast("§c" + p.getName() + " §7a été tué par §a" + killer.getName() + " §8(" + rest + ")");
            } else {
                broadcast("§c" + p.getName() + " §7est mort §8(" + rest + ")");
            }
            world.strikeLightningEffect(p.getLocation());
            if (left <= 0) {
                eliminate(p);
                return;
            }
            heal(p);
            p.setFallDistance(0);
            p.setVelocity(new Vector());
            p.teleport(respawnSpot(p));
            protectedUntil.put(id, System.currentTimeMillis() + cfg("respawn-protection", 3) * 1000L);
            p.showTitle(Title.title(Component.text("-1 vie", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    hearts(left, cfg("lives", 3)), Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            p.playSound(p, Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
            reportStatus();
        }

        void eliminate(Player p) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            outOrder.add(id);
            protectedUntil.remove(id);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            p.getInventory().clear();
            heal(p);
            p.setFallDistance(0);
            p.setVelocity(new Vector());
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(spectatorSpot());
            p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text("Tu n'as plus de vie", NamedTextColor.GRAY)));
            p.playSound(p, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
            broadcast("§4" + p.getName() + " §cest éliminé ! §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            checkEnd();
            reportStatus();
        }

        /** Dernier en vie (ou plus personne) : fin de partie. */
        void checkEnd() {
            if (state != State.PLAYING) return;
            if (alive.isEmpty()) {
                List<UUID> w = new ArrayList<>();
                if (!outOrder.isEmpty()) w.add(outOrder.get(outOrder.size() - 1));
                endGame(total >= 2 ? w : List.of());
            } else if (alive.size() == 1 && total >= 2) {
                endGame(new ArrayList<>(alive));
            }
        }

        /** À beacon-time de la fin : le phare s'allume sur l'un des 3 points. */
        void activateBeacon() {
            if (beaconOn) return;
            beaconIdx = random.nextInt(BEACONS.length);
            Location b = beaconLoc(beaconIdx);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) set(b.getBlockX() + dx, floorY, b.getBlockZ() + dz, Material.IRON_BLOCK);
            set(b.getBlockX(), floorY + 1, b.getBlockZ(), Material.BEACON);
            beaconOn = true;
            world.playSound(b, Sound.BLOCK_BEACON_ACTIVATE, 3f, 1f);
            for (Player p : online(inGame)) {
                p.showTitle(Title.title(Component.text("Le phare brille !", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                        Component.text("Sois dans le cercle à la fin", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                p.playSound(p, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
            }
            broadcast("§6Le phare s'est allumé ! §eÀ la fin du temps, le joueur dans son cercle qui a le plus de kills gagne.");
        }

        void beaconParticles() {
            Location c = beaconLoc(beaconIdx);
            double r = cfg("beacon-radius", 6);
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.6f);
            int n = (int) Math.max(24, r * 10);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                world.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, floorY + 1.2, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, dust);
            }
        }

        boolean inCircle(Player p) {
            if (!beaconOn || !p.getWorld().equals(world)) return false;
            Location c = beaconLoc(beaconIdx), l = p.getLocation();
            return Math.hypot(l.getX() - c.getX(), l.getZ() - c.getZ()) <= cfg("beacon-radius", 6) && Math.abs(l.getY() - c.getY()) <= 5;
        }

        void resetBeacon() {
            for (int[] bc : BEACONS) set(AX + bc[0], floorY + 1, AZ + bc[1], Material.AIR);
            beaconOn = false;
            beaconIdx = -1;
        }

        /** Fin du temps : dans le cercle du phare, le plus de kills gagne (égalité possible) ; personne = pas de vainqueur. */
        void timeUp() {
            if (!beaconOn) activateBeacon();
            List<Player> in = new ArrayList<>();
            for (Player p : online(alive)) if (inCircle(p)) in.add(p);
            if (in.isEmpty()) {
                broadcast("§eTemps écoulé ! §7Personne dans le cercle du phare.");
                endGame(List.of());
                return;
            }
            int best = -1;
            for (Player p : in) best = Math.max(best, kills.getOrDefault(p.getUniqueId(), 0));
            List<UUID> w = new ArrayList<>();
            for (Player p : in) if (kills.getOrDefault(p.getUniqueId(), 0) == best) w.add(p.getUniqueId());
            broadcast("§eTemps écoulé ! " + (w.size() > 1 ? "Égalité dans le cercle à " : "Victoire dans le cercle avec ") + "§6" + best + " kill" + (best > 1 ? "s" : "") + "§e.");
            endGame(w);
        }

        // -------------------------------------------------------------- partie

        boolean running() {
            return state == State.PLAYING || state == State.ENDING;
        }

        void startGame() {
            List<Player> list = online(players);
            if (list.isEmpty() || world == null || course == null) { state = State.WAITING; return; }
            alive.clear();
            inGame.clear();
            lane.clear();
            checkpoint.clear();
            falls.clear();
            finished.clear();
            chests.clear();
            lives.clear();
            kills.clear();
            lastHit.clear();
            lastHitTime.clear();
            protectedUntil.clear();
            backCooldown.clear();
            outOrder.clear();
            if (beaconOn) resetBeacon();
            state = State.PLAYING;
            phase = Phase.JUMP;
            graceLeft = -1;
            total = list.size();
            phaseLeft = Math.max(30, cfg("jump-time", 660));
            freezeTicks = Math.max(0, cfg("freeze-time", 3)) * 20 + 1;
            world.setTime(cfg("time", 6000));
            for (int i = 0; i < list.size(); i++) {
                Player p = list.get(i);
                UUID id = p.getUniqueId();
                int ln = LANE_ORDER[i % LANES];
                alive.add(id);
                inGame.add(id);
                lane.put(id, ln);
                checkpoint.put(id, 0);
                falls.put(id, 0);
                kills.put(id, 0);
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(checkpointLoc(id, 0));
                giveKit(p, ln);
                p.showTitle(Title.title(Component.text("JUMPLEAGUE", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                        Component.text("10 modules, puis le deathmatch", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
            reportStatus();
            broadcast("§eLa partie commence avec §6" + total + " §ejoueurs ! §7Parcours de " + MODULES + " modules ("
                    + clock(phaseLeft) + "), un coffre de l'Ender par module, puis deathmatch.");
            broadcast("§7Fini = §bbottes en diamant§7, fini sans tomber = §bcasque en diamant §7en plus.");
        }

        /** Kit de départ : cuir complet à la couleur du couloir, épée en bois, objet de retour au checkpoint. */
        void giveKit(Player p, int ln) {
            PlayerInventory inv = p.getInventory();
            inv.clear();
            Color col = LANE_COLORS[ln].getColor();
            inv.setHelmet(leather(Material.LEATHER_HELMET, col));
            inv.setChestplate(leather(Material.LEATHER_CHESTPLATE, col));
            inv.setLeggings(leather(Material.LEATHER_LEGGINGS, col));
            inv.setBoots(leather(Material.LEATHER_BOOTS, col));
            inv.setItem(0, kit(new ItemStack(Material.WOODEN_SWORD))); // marquée kit : ne se jette pas, ne se déplace pas
            ItemStack back = kit(new ItemStack(Material.SLIME_BALL));
            ItemMeta m = back.getItemMeta();
            m.displayName(Component.text("Retour au checkpoint", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text("Compte comme une chute", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            back.setItemMeta(m);
            inv.setItem(8, back);
            inv.setHeldItemSlot(0);
        }

        void endGame(List<UUID> winners) {
            endGame(winners, true);
        }

        /** rewards = false pour un arrêt admin (pas de récompenses EemiCore). */
        void endGame(List<UUID> winners, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            if (rewards) {
                for (Player p : online(inGame)) {
                    eemi("reward " + p.getName() + " " + GAME_ID + " play");
                    if (winners.contains(p.getUniqueId())) eemi("reward " + p.getName() + " " + GAME_ID + " win");
                }
            }
            timer = cfg("end-time", 8);
            List<Player> win = online(new LinkedHashSet<>(winners));
            StringBuilder names = new StringBuilder();
            for (Player w : win) names.append(names.isEmpty() ? "" : ", ").append(w.getName());
            Component title = win.isEmpty()
                    ? Component.text("Fin de la partie", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    : Component.text(win.size() == 1 ? names.toString() : "Égalité !", win.size() == 1 ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD); // titre court : pseudo seul
            for (Player p : online(players)) {
                p.closeInventory();
                boolean won = win.contains(p);
                p.showTitle(Title.title(title, Component.text(win.size() > 1 ? tieSubtitle(win) : won ? "Tu as gagné la JumpLeague !" : win.size() == 1 ? "gagne la JumpLeague !" : "", NamedTextColor.YELLOW)));
                p.playSound(p, won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            if (win.isEmpty()) broadcast("§7La partie se termine sans vainqueur.");
            else broadcast("§6" + names + " §eremporte" + (win.size() > 1 ? "nt" : "") + " la JumpLeague !");
            List<UUID> byKills = new ArrayList<>(inGame);
            byKills.sort((a, b) -> Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0)));
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(3, byKills.size()); i++) {
                Player k = Bukkit.getPlayer(byKills.get(i));
                top.append(i == 0 ? "" : "§7, ").append("§f").append(k != null ? k.getName() : "?").append(" §8(").append(kills.getOrDefault(byKills.get(i), 0)).append(")");
            }
            if (!byKills.isEmpty() && phase == Phase.DEATHMATCH) broadcast("§7Kills : " + top);
            for (Player w : win) {
                for (int i = 0; i < 6; i++)
                    Bukkit.getScheduler().runTaskLater(EemiJumpLeague.this, () -> {
                        if (w.isOnline() && state == State.ENDING && !destroyed && w.getWorld().equals(world)) firework(w.getLocation());
                    }, i * 20L);
            }
            alive.clear();
            reportStatus();
        }

        /**
         * Après la fin : les inscrits reviennent dans la salle d'attente, les spectateurs au hub, puis un
         * nouveau parcours est posé. Une copie vide est détruite (la prochaine repartira du modèle).
         */
        void backToLobby() {
            state = State.WAITING;
            phase = Phase.JUMP;
            alive.clear();
            inGame.clear();
            lives.clear();
            protectedUntil.clear();
            chests.clear();
            for (Player p : online(players)) toWaiting(p);
            for (Player p : online(spectators)) {
                sendToHub(p);
                p.sendMessage(PREFIX + "§7Partie terminée, retour au hub.");
            }
            spectators.clear();
            reportStatus();
            if (index > 1 && players.isEmpty()) {
                destroyGame(this);
                return;
            }
            Bukkit.getScheduler().runTaskLater(EemiJumpLeague.this, () -> {
                if (world == null || destroyed || state != State.WAITING) return;
                resetBeacon();
                // flèches et objets restés sur l'arène
                for (Entity e : world.getEntities())
                    if ((e instanceof Item || e instanceof AbstractArrow) && e.getLocation().distanceSquared(new Location(world, AX, floorY, AZ)) < 60 * 60) e.remove();
                build();
            }, 20L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(List.of(), false);
            if (state == State.ENDING) backToLobby();
            if (state == State.STARTING) state = State.WAITING;
        }

        // -------------------------------------------------------------- inscription

        /** Inscription dans cette partie (en attente ou en décompte). */
        void addPlayer(Player p) {
            UUID id = p.getUniqueId();
            Game sg = spectatorGame(p);
            if (sg != null) sg.spectators.remove(id);
            players.add(id);
            toWaiting(p);
            broadcast("§a" + p.getName() + " §7a rejoint la JumpLeague §8(partie " + index + ") §7(§f" + players.size() + "/" + maxPlayers() + "§7).");
            reportStatus();
        }

        void toWaiting(Player p) {
            Location l = lobbySpawn();
            p.teleport(l);
            later(() -> {
                if (!p.isOnline() || !players.contains(p.getUniqueId()) || destroyed) return;
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(l);
                p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter la JumpLeague"));
                updateSidebar(p);
            });
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            if (alive.remove(id) && state == State.PLAYING) {
                outOrder.add(id);
                broadcast("§c" + p.getName() + " §7a quitté la partie. §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            }
            inGame.remove(id);
            protectedUntil.remove(id);
            chests.remove(id);
            boards.remove(id);
            p.closeInventory();
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) sendToHub(p);
            else {
                p.getInventory().clear();
                for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            }
            if (state == State.PLAYING) {
                if (inGame.isEmpty()) endGame(List.of(), false);
                else checkEnd();
            }
            reportStatus();
        }

        // -------------------------------------------------------------- affichage

        void actionBar(Player p) {
            UUID id = p.getUniqueId();
            if (!alive.contains(id)) {
                p.sendActionBar(Component.text("Spectateur - " + alive.size() + " joueur" + (alive.size() > 1 ? "s" : "") + " en vie", NamedTextColor.GRAY));
                return;
            }
            if (phase == Phase.JUMP) {
                int cp = checkpoint.getOrDefault(id, 0);
                Component c = cp >= MODULES
                        ? Component.text("Parcours fini ! Attends le deathmatch.", NamedTextColor.GOLD)
                        : Component.text("Module " + (cp + 1) + "/" + MODULES + " ", NamedTextColor.WHITE)
                          .append(Component.text(CourseGen.tierOf(cp).label, tierColor(CourseGen.tierOf(cp))));
                c = c.append(Component.text("  |  Chutes : " + falls.getOrDefault(id, 0), NamedTextColor.GRAY));
                if (graceLeft > 0) c = c.append(Component.text("  |  Deathmatch dans " + graceLeft + " s", NamedTextColor.RED));
                p.sendActionBar(c);
                return;
            }
            Component c = Component.text("Vies : ", NamedTextColor.GRAY).append(hearts(livesOf(id), cfg("lives", 3)))
                    .append(Component.text("  |  Kills : " + kills.getOrDefault(id, 0), NamedTextColor.GREEN));
            if (beaconOn) {
                Location b = beaconLoc(beaconIdx);
                double d = Math.hypot(p.getLocation().getX() - b.getX(), p.getLocation().getZ() - b.getZ());
                c = c.append(inCircle(p) ? Component.text("  |  Dans le cercle !", NamedTextColor.GOLD)
                        : Component.text("  |  Phare : " + (int) d + " m", NamedTextColor.YELLOW));
            }
            p.sendActionBar(c);
        }

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("jl", Criteria.DUMMY,
                        Component.text("JUMPLEAGUE", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("jl");
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
            UUID me = p.getUniqueId();
            boolean playing = inGame.contains(me) && running();
            if (!playing) {
                l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("n°" + index, NamedTextColor.WHITE)));
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + maxPlayers(), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("Modules : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(MODULES), NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GREEN)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else {
                    l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
                    l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
                }
            } else if (phase == Phase.JUMP) {
                int cp = checkpoint.getOrDefault(me, 0);
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(phaseLeft), NamedTextColor.WHITE)));
                l.add(Component.text("Module : ", NamedTextColor.GRAY).append(Component.text(Math.min(cp + 1, MODULES) + "/" + MODULES, NamedTextColor.WHITE)));
                Tier t = CourseGen.tierOf(Math.min(cp, MODULES - 1));
                l.add(Component.text("Difficulté : ", NamedTextColor.GRAY).append(Component.text(cp >= MODULES ? "Fini" : t.label, cp >= MODULES ? NamedTextColor.GOLD : tierColor(t))));
                l.add(Component.text("Chutes : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(falls.getOrDefault(me, 0)), NamedTextColor.RED)));
                l.add(Component.text("  "));
                List<UUID> order = new ArrayList<>(inGame);
                order.sort((a, b) -> Integer.compare(checkpoint.getOrDefault(b, 0), checkpoint.getOrDefault(a, 0)));
                int shown = 0;
                for (UUID id : order) {
                    if (shown++ >= 7) break;
                    Player o = Bukkit.getPlayer(id);
                    int c = checkpoint.getOrDefault(id, 0);
                    Component line = Component.text((o != null ? o.getName() : "?") + " ", id.equals(me) ? NamedTextColor.YELLOW : NamedTextColor.WHITE);
                    line = line.append(c >= MODULES ? Component.text("✔", NamedTextColor.GOLD) : Component.text(c + "/" + MODULES, NamedTextColor.GRAY));
                    l.add(line);
                }
            } else {
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(phaseLeft), NamedTextColor.WHITE)));
                l.add(Component.text("En vie : ", NamedTextColor.GRAY).append(Component.text(alive.size() + "/" + total, NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                List<UUID> order = new ArrayList<>(inGame);
                order.sort((a, b) -> {
                    int c = Integer.compare(livesOf(b), livesOf(a));
                    return c != 0 ? c : Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0));
                });
                int shown = 0;
                for (UUID id : order) {
                    if (shown++ >= 7) break;
                    Player o = Bukkit.getPlayer(id);
                    int lv = livesOf(id);
                    Component line = Component.text((o != null ? o.getName() : "?") + " ", id.equals(me) ? NamedTextColor.YELLOW : lv > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY);
                    line = line.append(lv > 0 ? Component.text(HEART.repeat(Math.min(lv, 5)), NamedTextColor.RED) : Component.text("✖", NamedTextColor.DARK_RED));
                    l.add(line);
                }
                l.add(Component.text("   "));
                l.add(Component.text("Tes kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(me, 0)), NamedTextColor.GREEN)));
            }
            l.add(Component.text("    "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        /** Message aux seuls joueurs et spectateurs de cette partie. */
        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
            for (Player p : online(spectators)) p.sendMessage(PREFIX + msg);
        }

        String stateLabel() {
            if (preparing) return "preparation";
            return switch (state) {
                case WAITING -> "attente";
                case STARTING -> "décompte " + timer + " s";
                case PLAYING -> phase == Phase.JUMP ? "parcours " + clock(phaseLeft) : "deathmatch " + clock(phaseLeft);
                case ENDING -> "fin";
            };
        }
    }

    // ------------------------------------------------------------------ butin des coffres

    /** 2 à 5 objets selon la difficulté (jamais de bottes ni de casque en diamant : ce sont les récompenses). */
    private List<ItemStack> loot(Tier t) {
        List<Supplier<ItemStack>> pool = new ArrayList<>();
        int n;
        switch (t) {
            case EASY -> {
                n = 2 + random.nextInt(2);
                pool.add(() -> new ItemStack(Material.CHAINMAIL_HELMET));
                pool.add(() -> new ItemStack(Material.CHAINMAIL_LEGGINGS));
                pool.add(() -> new ItemStack(Material.CHAINMAIL_BOOTS));
                pool.add(() -> new ItemStack(Material.GOLDEN_CHESTPLATE));
                pool.add(() -> new ItemStack(Material.STONE_SWORD));
                pool.add(() -> new ItemStack(Material.BREAD, 4));
                pool.add(() -> new ItemStack(Material.COOKED_PORKCHOP, 3));
                pool.add(() -> new ItemStack(Material.ARROW, 6));
                pool.add(() -> new ItemStack(Material.FISHING_ROD));
                pool.add(() -> new ItemStack(Material.APPLE, 3));
            }
            case NORMAL -> {
                n = 2 + random.nextInt(3);
                pool.add(() -> new ItemStack(Material.IRON_HELMET));
                pool.add(() -> new ItemStack(Material.IRON_LEGGINGS));
                pool.add(() -> new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                pool.add(() -> new ItemStack(Material.IRON_BOOTS));
                pool.add(() -> new ItemStack(Material.STONE_SWORD));
                pool.add(() -> new ItemStack(Material.BOW));
                pool.add(() -> new ItemStack(Material.ARROW, 10));
                pool.add(() -> new ItemStack(Material.GOLDEN_APPLE));
                pool.add(() -> new ItemStack(Material.COOKED_BEEF, 4));
                pool.add(() -> new ItemStack(Material.FISHING_ROD));
            }
            case HARD -> {
                n = 3 + random.nextInt(2);
                pool.add(() -> new ItemStack(Material.IRON_CHESTPLATE));
                pool.add(() -> new ItemStack(Material.IRON_LEGGINGS));
                pool.add(() -> new ItemStack(Material.IRON_SWORD));
                pool.add(() -> ench(new ItemStack(Material.BOW), Enchantment.POWER, 1));
                pool.add(() -> new ItemStack(Material.ARROW, 16));
                pool.add(() -> new ItemStack(Material.GOLDEN_APPLE, 2));
                pool.add(() -> new ItemStack(Material.COOKED_BEEF, 6));
                pool.add(() -> ench(new ItemStack(Material.IRON_HELMET), Enchantment.PROTECTION, 1));
                pool.add(() -> ench(new ItemStack(Material.IRON_BOOTS), Enchantment.PROTECTION, 1));
            }
            default -> {
                n = 4 + random.nextInt(2);
                pool.add(() -> new ItemStack(Material.DIAMOND_CHESTPLATE));
                pool.add(() -> new ItemStack(Material.DIAMOND_LEGGINGS));
                pool.add(() -> new ItemStack(Material.DIAMOND_SWORD));
                pool.add(() -> ench(new ItemStack(Material.IRON_SWORD), Enchantment.SHARPNESS, 2));
                pool.add(() -> ench(new ItemStack(Material.BOW), Enchantment.POWER, 2));
                pool.add(() -> new ItemStack(Material.ARROW, 24));
                pool.add(() -> new ItemStack(Material.GOLDEN_APPLE, 3));
                pool.add(() -> ench(new ItemStack(Material.IRON_CHESTPLATE), Enchantment.PROTECTION, 2));
            }
        }
        Collections.shuffle(pool, random);
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, pool.size()); i++) out.add(pool.get(i).get());
        return out;
    }

    private static ItemStack ench(ItemStack it, Enchantment e, int level) {
        ItemMeta m = it.getItemMeta();
        m.addEnchant(e, level, true);
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ objets (communs)

    private static ItemStack leather(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(c);
        it.setItemMeta(meta);
        return it;
    }

    /** Équipe la pièce ; l'ancienne va dans l'inventaire (ou tombe s'il est plein). */
    private static void equip(Player p, EquipmentSlot slot, ItemStack it) {
        PlayerInventory inv = p.getInventory();
        ItemStack old = inv.getItem(slot);
        inv.setItem(slot, it);
        if (old != null && !old.getType().isAir())
            for (ItemStack left : inv.addItem(old).values()) p.getWorld().dropItem(p.getLocation(), left);
    }

    private void removeKitItems(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (isKit(inv.getItem(i))) inv.setItem(i, null);
    }

    private static NamedTextColor tierColor(Tier t) {
        return switch (t) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.AQUA;
            case HARD -> NamedTextColor.GOLD;
            case ULTRA -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private <T extends Entity> T tag(T e) {
        e.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
        return e;
    }

    private boolean isOurs(Entity e) {
        return e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE);
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l, Firework.class, f -> {
            tag(f);
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.LIME, Color.YELLOW).withFade(Color.WHITE).trail(true).build());
            m.setPower(1);
            f.setFireworkMeta(m);
        });
        fw.setSilent(false);
    }

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

    // ------------------------------------------------------------------ inscription (choix de la partie)

    /**
     * /jumpleague join : la partie en attente (ou en décompte, ou en préparation) qui a de la place, la plus
     * remplie d'abord ; sinon une nouvelle copie si on est sous max-instances ; sinon « pleines ».
     */
    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (pendingOf(p) != null) { p.sendMessage(PREFIX + "§7Ta partie est en préparation, patiente un instant."); return; }
        if (games.isEmpty()) { p.sendMessage(PREFIX + "§cLe monde de la JumpLeague n'est pas chargé."); return; }
        int max = maxPlayers();
        Game target = null;
        int best = -1;
        for (Game g : games.values()) {
            if (g.destroyed || g.running()) continue;
            if (!g.preparing && g.world == null) continue;
            int n = g.players.size() + g.pending.size();
            if (n >= max) continue;
            if (n > best) { best = n; target = g; }
        }
        if (target == null) {
            if (games.size() >= maxInstances()) { p.sendMessage(PREFIX + "§cToutes les parties sont pleines."); return; }
            target = newCopy();
            if (target == null) { p.sendMessage(PREFIX + "§cUne partie est en préparation, réessaie dans quelques secondes."); return; }
        }
        if (target.preparing) {
            target.pending.add(id);
            p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
            reportStatus();
            return;
        }
        target.addPlayer(p);
    }

    private void sendToHub(Player p) {
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    private World hubWorld() {
        return Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
    }

    private static void heal(Player p) {
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setFireTicks(0);
        p.setArrowsInBody(0);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
    }

    private void resetPlayer(Player p, GameMode mode) {
        p.getInventory().clear();
        p.setGameMode(mode);
        heal(p);
        p.setLevel(0);
        p.setExp(0);
        p.setFallDistance(0);
    }

    // ------------------------------------------------------------------ affichage (commun)

    /** « ❤❤❤ » rouges pour les vies restantes, gris pour les vies perdues. */
    private Component hearts(int left, int baseLives) {
        Component c = Component.text(HEART.repeat(Math.max(0, left)), NamedTextColor.RED);
        if (baseLives > left) c = c.append(Component.text(HEART.repeat(baseLives - left), NamedTextColor.DARK_GRAY));
        return c;
    }

    private static String clock(int s) {
        s = Math.max(0, s);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    // ------------------------------------------------------------------ événements

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemijumpleague.admin");
    }

    private Player attackerOf(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Player pl) return pl;
        if (d instanceof Projectile proj && proj.getShooter() instanceof Player pl) return pl;
        return null;
    }

    /**
     * Aucun dégât pendant le parcours, l'attente et les départs. Au deathmatch : combat normal entre joueurs
     * en vie de la partie (réapparition protégée 3 s) ; un coup mortel est annulé et compte comme une mort
     * (pas d'écran de mort, on garde son équipement).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !isGameWorld(victim.getWorld())) return;
        Game g = gameAt(victim.getWorld());
        UUID id = victim.getUniqueId();
        if (g == null || g.state != State.PLAYING || g.phase != Phase.DEATHMATCH || !g.alive.contains(id)
                || g.freezeTicks > 0 || g.isProtected(victim)) {
            e.setCancelled(true);
            return;
        }
        if (e.isCancelled()) return;
        Player attacker = null;
        if (e instanceof EntityDamageByEntityEvent by) {
            attacker = attackerOf(by);
            if (attacker != null && attacker != victim) {
                if (!g.alive.contains(attacker.getUniqueId())) {
                    e.setCancelled(true);
                    return;
                }
                g.protectedUntil.remove(attacker.getUniqueId());
                g.lastHit.put(id, attacker.getUniqueId());
                g.lastHitTime.put(id, System.currentTimeMillis());
            }
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getFinalDamage() >= victim.getHealth()) {
            e.setCancelled(true);
            Player killer = attacker != null && attacker != victim ? attacker : g.killerOf(victim);
            g.die(victim, killer);
        }
    }

    /** Faim seulement au deathmatch. */
    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = gameOf(p);
        if (g == null && !isGameWorld(p.getWorld())) return;
        if (g == null || g.state != State.PLAYING || g.phase != Phase.DEATHMATCH) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (isGameWorld(e.getBlock().getWorld()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (isGameWorld(e.getBlock().getWorld()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (isGameWorld(e.getLocation().getWorld()) && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    /** Nos propres entités (panneau, feux d'artifice) apparaissent même si un autre plugin les bloque. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (e.isCancelled() && isOurs(e.getEntity())) e.setCancelled(false);
    }

    /**
     * Coffres de l'Ender du parcours : un inventaire propre au joueur et au module (le vrai coffre de l'Ender
     * ne s'ouvre jamais). Le décor ne se règle pas ; les objets en main restent utilisables (manger, arc...).
     * Objet « Retour au checkpoint » et lit « Quitter ».
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isGameWorld(p.getWorld())) return;
        Game g = gameOf(p);
        Block b = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b != null && !admin(p)) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
            if (b.getType() == Material.ENDER_CHEST) {
                e.setCancelled(true);
                if (e.getHand() != EquipmentSlot.HAND || g == null || g.course == null || g.state != State.PLAYING
                        || g.phase != Phase.JUMP || g.freezeTicks > 0 || !g.alive.contains(p.getUniqueId())) return;
                int rx = b.getX() - laneX(g.lane.getOrDefault(p.getUniqueId(), 0));
                Integer m = g.course.chests.get(CourseGen.key(rx, b.getY(), b.getZ()));
                if (m != null) g.openChest(p, m);
                return;
            }
        }
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !isKit(it)) return;
        e.setCancelled(true);
        if (it.getType() == Material.RED_BED) {
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté la JumpLeague.");
        } else if (it.getType() == Material.SLIME_BALL && g.state == State.PLAYING && g.phase == Phase.JUMP
                && g.freezeTicks <= 0 && g.alive.contains(p.getUniqueId())) {
            long now = System.currentTimeMillis();
            if (g.backCooldown.getOrDefault(p.getUniqueId(), 0L) > now) return;
            g.backCooldown.put(p.getUniqueId(), now + 1000);
            g.fall(p, true);
        }
    }

    /** Pas de tir pendant le parcours (on ne vise pas les autres couloirs) ni pendant les départs. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p) || !isGameWorld(p.getWorld()) || admin(p)) return;
        Game g = gameAt(p.getWorld());
        if (g == null || g.state != State.PLAYING || g.phase != Phase.DEATHMATCH || g.freezeTicks > 0 || !g.alive.contains(p.getUniqueId()))
            e.setCancelled(true);
    }

    /** Canne à pêche : pas d'accroche de joueur pendant le parcours. */
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null && g.phase == Phase.JUMP && e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) e.setCancelled(true);
    }

    /** Les flèches des joueurs restent ramassables au deathmatch ; elles disparaissent au bout de 30 s. */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (isGameWorld(e.getEntity().getWorld()) && e.getEntity() instanceof AbstractArrow a)
            Bukkit.getScheduler().runTaskLater(this, () -> { if (a.isValid()) a.remove(); }, 600L);
    }

    /** Départs (parcours et deathmatch) : immobiles pendant le décompte (on peut tourner la tête). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g == null || g.state != State.PLAYING || g.freezeTicks <= 0 || !g.alive.contains(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (f.getX() != t.getX() || f.getZ() != t.getZ() || t.getY() > f.getY()) {
            Location back = f.clone();
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            e.setTo(back);
        }
    }

    /** Sécurité (/kill...) : pas de drops, compte comme une mort (deathmatch) ou une chute (parcours). */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Game g = gameOf(p);
        if (g == null || g.world == null || !p.getWorld().equals(g.world)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && p.isDead()) p.spigot().respawn(); }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (g == null || g.world == null) return;
        UUID id = p.getUniqueId();
        if (g.alive.contains(id) && g.state == State.PLAYING) {
            if (g.phase == Phase.JUMP) {
                e.setRespawnLocation(g.checkpointLoc(id, g.checkpoint.getOrDefault(id, 0)));
                return;
            }
            e.setRespawnLocation(g.respawnSpot(p));
            later(() -> {
                if (!p.isOnline() || !g.alive.contains(id)) return;
                p.setGameMode(GameMode.ADVENTURE);
                g.die(p, g.killerOf(p));
            });
            return;
        }
        if (g.running()) {
            e.setRespawnLocation(g.spectatorSpot());
            later(() -> { if (p.isOnline()) p.setGameMode(GameMode.SPECTATOR); });
            return;
        }
        e.setRespawnLocation(g.lobbySpawn());
        later(() -> g.toWaiting(p));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (gameOf(e.getPlayer()) != null || isKit(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    /** On range son inventaire et on s'équipe librement ; seuls les objets du kit (retour, quitter) restent en place. */
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || admin(p)) return;
        Game g = gameOf(p);
        if (g == null) return;
        if (g.state != State.PLAYING) {
            e.setCancelled(true);
            return;
        }
        if (isKit(e.getCurrentItem()) || isKit(e.getCursor())
                || (e.getClick() == ClickType.NUMBER_KEY && isKit(p.getInventory().getItem(e.getHotbarButton()))))
            e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null && (g.state != State.PLAYING || isKit(e.getMainHandItem()) || isKit(e.getOffHandItem()))) e.setCancelled(true);
    }

    /** Quitter le monde de SA partie = quitter la partie. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player sp = e.getPlayer();
        Game sg = spectatorGame(sp);
        if (sg != null && !sp.getWorld().equals(sg.world)) {
            // spectateur parti (/lobby) : il redevient un joueur normal
            sg.spectators.remove(sp.getUniqueId());
            if (sp.getGameMode() == GameMode.SPECTATOR) sp.setGameMode(GameMode.SURVIVAL);
        }
        Game g = gameOf(sp);
        if (g != null && g.world != null && e.getFrom().equals(g.world) && !sp.getWorld().equals(g.world)) g.leave(sp, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game sg = spectatorGame(p);
        if (sg != null) {
            sg.spectators.remove(p.getUniqueId());
            sendToHub(p);
        }
        Game pg = pendingOf(p);
        if (pg != null) pg.pending.remove(p.getUniqueId());
        Game g = gameOf(p);
        if (g != null) g.leave(p, true);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemijumpleague.admin");
        Game g1 = games.get(1);
        switch (sub) {
            case "join" -> {
                Player t = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (t == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(t);
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "leave" -> {
                if (p == null) return true;
                Game sg = spectatorGame(p);
                if (sg != null) { sendToHub(p); return true; }
                Game pg = pendingOf(p);
                if (pg != null) {
                    pg.pending.remove(p.getUniqueId());
                    p.sendMessage(PREFIX + "§7Tu ne rejoindras pas la partie en préparation.");
                    reportStatus();
                    return true;
                }
                Game g = gameOf(p);
                if (g == null) return true;
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté la JumpLeague.");
            }
            case "start" -> {
                if (!admin) return true;
                Game g = targetGame(sender, args);
                if (g == null || g.preparing) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                if (g.running()) { sender.sendMessage(PREFIX + "§cUne partie est déjà en cours."); return true; }
                if (g.players.isEmpty()) { sender.sendMessage(PREFIX + "§cPersonne n'est inscrit."); return true; }
                if (g.course == null) { sender.sendMessage(PREFIX + "§cLe parcours n'est pas encore construit."); return true; }
                g.startGame();
                sender.sendMessage(PREFIX + "§aPartie " + g.index + " lancée.");
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = targetGame(sender, args);
                if (g == null || g.preparing) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie " + g.index + " arrêtée.");
            }
            case "skip" -> {
                if (!admin) return true;
                Game g = targetGame(sender, args);
                if (g == null || g.state != State.PLAYING) { sender.sendMessage(PREFIX + "§cAucune partie en cours."); return true; }
                if (g.phase == Phase.JUMP) {
                    g.freezeTicks = 0;
                    g.startDeathmatch();
                    sender.sendMessage(PREFIX + "§7Partie " + g.index + " : deathmatch lancé.");
                } else {
                    g.phaseLeft = Math.min(g.phaseLeft, cfg("beacon-time", 60) + 1);
                    sender.sendMessage(PREFIX + "§7Partie " + g.index + " : le phare s'allume dans une seconde.");
                }
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ") :");
                for (Game g : games.values())
                    sender.sendMessage("§8- §e#" + g.index + " §f" + WorldClone.name(base, g.index) + " §7" + g.stateLabel()
                            + " §8| §7joueurs §f" + g.players.size()
                            + (g.pending.isEmpty() ? "" : " §8(+" + g.pending.size() + " en route)")
                            + (g.running() ? " §8| §7en vie §f" + g.alive.size() : "")
                            + (g.spectators.isEmpty() ? "" : " §8| §7spectateurs §f" + g.spectators.size()));
                sender.sendMessage("§8- §7modèle §f" + modelName() + " §7" + (model != null ? "pret" : modelBusy ? "en préparation" : "absent"));
            }
            case "build" -> {
                if (!admin) return true;
                if (g1 == null || g1.world == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g1.running()) { sender.sendMessage(PREFIX + "§cImpossible : la partie 1 est en cours."); return true; }
                loadSettings();
                g1.buildStatic();
                g1.build();
                for (Player pl : online(g1.players)) g1.toWaiting(pl);
                makeModel();
                sender.sendMessage(PREFIX + "§aArène et nouveau parcours de la partie 1 construits §7(modèle refait).");
            }
            case "reload" -> {
                if (!admin) return true;
                if (g1 != null && g1.running()) { sender.sendMessage(PREFIX + "§cImpossible : la partie 1 est en cours."); return true; }
                String oldBase = base;
                loadSettings();
                if (!base.equals(oldBase)) {
                    base = oldBase;
                    sender.sendMessage(PREFIX + "§cLe monde du jeu ne change qu'au redémarrage.");
                }
                for (Game g : games.values()) if (g.world != null) prepareRules(g.world);
                sender.sendMessage(PREFIX + "§aConfig rechargée. §7(/jumpleague build si floor-y ou lobby-y a changé)");
            }
            default -> sender.sendMessage(PREFIX + "§7/jumpleague <join|leave|spectate [joueur]>" + (admin ? " §8| admin : start [n], stop [n], skip [n], parties, build, reload" : ""));
        }
        return true;
    }

    /** Regarder une partie en cours en spectateur : celle d'un joueur, sinon la plus remplie. */
    private void spectate(Player p, String targetName) {
        if (gameOf(p) != null || pendingOf(p) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/jumpleague leave §cd'abord."); return; }
        Game g = null;
        Player target = null;
        if (targetName != null) {
            target = Bukkit.getPlayerExact(targetName);
            g = target == null ? null : gameOf(target);
            if (g == null) { p.sendMessage(PREFIX + "§cCe joueur n'est dans aucune partie de JumpLeague."); return; }
            if (g.state != State.PLAYING) { p.sendMessage(PREFIX + "§cSa partie n'a pas encore commencé."); return; }
        } else {
            int best = -1;
            for (Game c : games.values())
                if (c.state == State.PLAYING && c.world != null && c.inGame.size() > best) { best = c.inGame.size(); g = c; }
        }
        if (g == null || g.world == null) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return; }
        Game old = spectatorGame(p);
        if (old != null && old != g) old.spectators.remove(p.getUniqueId());
        g.spectators.add(p.getUniqueId());
        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        // pendant le parcours, au-dessus du joueur suivi (les couloirs sont loin les uns des autres)
        if (target != null && g.phase == Phase.JUMP && target.getWorld().equals(g.world)) p.teleport(target.getLocation().add(0, 4, -4));
        else p.teleport(g.spectatorSpot());
        p.sendMessage(PREFIX + "§7Tu regardes la partie " + g.index + ", §f/lobby §7pour revenir.");
    }

    // ------------------------------------------------------------------ EemiCore

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /**
     * Statut agrégé de toutes les parties pour EemiCore : waiting si une partie attend ou décompte (ou se
     * prépare), sinon playing si une partie joue, sinon waiting. Envoyé s'il a changé (ou toutes les 30 s).
     */
    private void reportStatus() {
        int waiting = 0, playing = 0;
        boolean anyWait = false, anyPlay = false;
        for (Game g : games.values()) {
            if (g.destroyed) continue;
            if (g.preparing) {
                anyWait = true;
                waiting += g.pending.size();
                continue;
            }
            if (g.state == State.WAITING || g.state == State.STARTING) anyWait = true;
            else anyPlay = true;
            for (Player p : online(g.players)) {
                UUID id = p.getUniqueId();
                if (!g.running() || !g.inGame.contains(id)) waiting++;
                else if (g.state == State.ENDING || g.alive.contains(id)) playing++;
            }
        }
        String st = anyWait ? "waiting" : anyPlay ? "playing" : "waiting";
        String status = "status " + GAME_ID + " " + st + " " + waiting + " " + playing + " " + maxPlayers() * maxInstances();
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
