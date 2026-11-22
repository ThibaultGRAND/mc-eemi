package fr.eemicraft.bowspleef;

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
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Bow Spleef d'EEMICRAFT (TNT Games d'Hypixel).
 * Chacun pour soi sur trois disques de TNT superposés (100, 90, 80 par défaut) au-dessus du vide, sous un
 * chapiteau de cirque construit par le plugin. Arc Infinité + Flamme : une flèche qui touche la TNT de l'arène
 * la fait disparaître avec ses voisines (break-radius / break-shape), sans explosion. Marcher dessus ne fait
 * rien. Aucun dégât entre joueurs : une flèche repousse légèrement. Tomber sous le dernier étage = éliminé.
 * Capacités : Triple tir (bâton de blaze, recharge 10 s) et 3 doubles sauts. Mort subite après 3 min (des blocs
 * de TNT disparaissent de plus en plus vite), égalité des survivants à 8 min.
 *
 * Plusieurs parties en même temps (max-instances, 3 par défaut) : partie 1 = monde « bowspleef »,
 * partie n = copie « bowspleef_n » du modèle « bowspleef_modele » (copie propre de la partie 1, refaite après
 * /bowspleef build). Une copie est détruite dès qu'elle est vide. La TNT est remise en place après chaque partie.
 */
public final class EemiBowSpleef extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§6Bow Spleef§8] §7";

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
    private static final String GAME_ID = "bowspleef";
    /** À incrémenter quand le décor change : le monde est alors reconstruit au démarrage. */
    private static final int BUILD_VERSION = 1;

    enum State { WAITING, STARTING, PLAYING, ENDING }

    /** Bloc de TNT condamné par la mort subite : il fume puis disparaît au tick « due ». */
    private record Doomed(int x, int y, int z, long due) {}

    // ------------------------------------------------------------------ état global (pas d'état de partie ici)

    private NamespacedKey kitKey, entityKey, buildKey;
    private String base = "bowspleef";
    private int radius, floorY, floors, floorGap, lobbyY;
    private final Map<Integer, Game> games = new TreeMap<>();
    private World model;
    private boolean modelBusy;
    private int modelGen;
    private long ticks;
    private String lastStatus;
    private long lastStatusAt;
    private final Random random = new Random();
    private final Map<String, BlockData> dataCache = new HashMap<>();
    private final Set<String> badData = new HashSet<>();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        entityKey = new NamespacedKey(this, "entity");
        buildKey = new NamespacedKey(this, "build");
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
                g1.ensureBuilt(false);
                makeModel();
            }
        }, 20L);
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
            // la TNT de la partie 1 est remise tout de suite (les copies sont supprimées au prochain démarrage)
            if (g.index == 1 && g.world != null) g.resetFloors();
            g.players.clear();
            g.spectators.clear();
            g.pending.clear();
            if (g.sign != null) g.sign.remove();
        }
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "bowspleef");
        radius = Math.max(6, cfg("radius", 18));
        floorY = cfg("floor-y", 100);
        floors = Math.max(1, Math.min(6, cfg("floors", 3)));
        floorGap = Math.max(4, cfg("floor-gap", 10));
        lobbyY = cfg("lobby-y", 140);
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private String modelName() {
        return base + "_modele";
    }

    private World setupWorld() {
        World w = Bukkit.getWorld(base);
        if (w == null) {
            w = new WorldCreator(base).generator(new VoidGenerator()).createWorld();
            if (w != null) getLogger().info("Monde " + base + " cree (vide).");
        }
        if (w == null) getLogger().severe("Impossible de charger ou créer le monde " + base + " : Bow Spleef désactivé.");
        return w;
    }

    /** Générateur vide : le monde n'a que ce que le plugin construit. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 141, 4.5);
        }
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private double cfgD(String key, double def) {
        return getConfig().getDouble(key, def);
    }

    // ------------------------------------------------------------------ géométrie (commune à toutes les parties)

    /** Hauteur de l'étage i (0 = haut). */
    private int floorAt(int i) {
        return floorY - i * floorGap;
    }

    private int bottomFloor() {
        return floorAt(floors - 1);
    }

    /** Sous cette hauteur : éliminé. */
    private int deathY() {
        return bottomFloor() - Math.max(1, cfg("death-margin", 5));
    }

    private boolean isFloorLevel(int y) {
        for (int i = 0; i < floors; i++) if (floorAt(i) == y) return true;
        return false;
    }

    private boolean inDisc(int x, int z, double r) {
        return x * x + z * z <= (r + 0.5) * (r + 0.5);
    }

    /** Case de TNT de l'arène (étage + disque). */
    private boolean isArenaCell(int x, int y, int z) {
        return isFloorLevel(y) && inDisc(x, z, radius);
    }

    /** Signature de la construction : si elle change, le monde est reconstruit. */
    private String buildSignature() {
        return BUILD_VERSION + ":" + radius + ":" + floorY + ":" + floors + ":" + floorGap + ":" + lobbyY;
    }

    /** État de bloc lu dans une chaîne ; invalide = averti une fois et ignoré (jamais d'arrêt de la construction). */
    private BlockData data(String s) {
        if (badData.contains(s)) return null;
        BlockData d = dataCache.get(s);
        if (d != null) return d;
        try {
            d = Bukkit.createBlockData(s);
            dataCache.put(s, d);
            return d;
        } catch (IllegalArgumentException ex) {
            badData.add(s);
            getLogger().warning("Bloc invalide ignore : " + s);
            return null;
        }
    }

    // ------------------------------------------------------------------ modèle et copies

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
        WorldClone.create(this, g1.world, modelName(), w -> {
            if (gen != modelGen) return;
            modelBusy = false;
            if (w == null) {
                getLogger().warning("Modèle " + modelName() + " impossible a créer : une seule partie possible.");
                return;
            }
            model = w;
            prepareRules(w);
            getLogger().info("Modèle " + modelName() + " prêt.");
        });
    }

    private Game newCopy() {
        if (model == null || modelBusy) return null;
        int idx = -1;
        for (int n = 2; n <= maxInstances(); n++)
            if (!games.containsKey(n)) { idx = n; break; }
        if (idx < 0) return null;
        Game g = new Game(idx, null);
        g.preparing = true;
        games.put(idx, g);
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
            g.prepareWorld();
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

    /** Règles d'un monde du jeu (partie ou modèle). */
    private void prepareRules(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
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

    private boolean isGameWorld(World w) {
        return w != null && (WorldClone.index(base, w.getName()) > 0 || w.getName().equals(modelName()));
    }

    private Game gameAt(World w) {
        if (w == null) return null;
        Game g = games.get(WorldClone.index(base, w.getName()));
        return g != null && w.equals(g.world) ? g : null;
    }

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

        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> pending = new LinkedHashSet<>();
        final Set<UUID> inGame = new LinkedHashSet<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> spectators = new LinkedHashSet<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        final Map<UUID, Integer> jumps = new HashMap<>();
        /** Double saut utilisé : il faut toucher le sol avant le suivant. */
        final Set<UUID> needLand = new HashSet<>();
        /** Triple tir armé (prochain tir) et fin de recharge (ms). */
        final Set<UUID> tripleArmed = new HashSet<>();
        final Map<UUID, Long> tripleReady = new HashMap<>();
        final List<UUID> outOrder = new ArrayList<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        final List<Doomed> doomed = new ArrayList<>();

        State state = State.WAITING;
        int timer;
        int freezeTicks;
        int gameLeft;
        int total;
        boolean suddenDeath;
        int brokenCount;
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
            Location l = new Location(world, 0.5, lobbyY + 1, 4.5);
            l.setYaw(180);
            return l;
        }

        Location spectatorSpot() {
            Location l = new Location(world, 0.5, floorY + 10, radius + 4.5);
            l.setYaw(180);
            l.setPitch(40);
            return l;
        }

        /** Départ i sur n : cercle de rayon 60 % sur l'étage du haut, regard vers le centre. */
        Location startSpot(int i, int n) {
            double a = 2 * Math.PI * i / Math.max(1, n);
            double r = radius * 0.6;
            Location l = new Location(world, Math.floor(Math.cos(a) * r) + 0.5, floorY + 1, Math.floor(Math.sin(a) * r) + 0.5);
            Vector dir = new Vector(0.5 - l.getX(), 0, 0.5 - l.getZ());
            if (dir.lengthSquared() > 0.01) l.setDirection(dir);
            l.setPitch(0);
            return l;
        }

        // -------------------------------------------------------------- construction

        void set(int x, int y, int z, Material m) {
            world.getBlockAt(x, y, z).setType(m, false);
        }

        void set(int x, int y, int z, String state) {
            BlockData d = data(state);
            if (d != null) world.getBlockAt(x, y, z).setBlockData(d, false);
        }

        void loadChunks(int ext) {
            for (int cx = (-ext) >> 4; cx <= ext >> 4; cx++)
                for (int cz = (-ext) >> 4; cz <= ext >> 4; cz++) world.getChunkAt(cx, cz).load(true);
        }

        /** Construit tout si la signature a changé (ou si force), sinon remet seulement la TNT. */
        void ensureBuilt(boolean force) {
            PersistentDataContainer pdc = world.getPersistentDataContainer();
            String sig = pdc.get(buildKey, PersistentDataType.STRING);
            if (force || !buildSignature().equals(sig)) {
                build();
                pdc.set(buildKey, PersistentDataType.STRING, buildSignature());
            } else {
                resetFloors();
                ensureSign();
                getLogger().info("Arène Bow Spleef déjà construite (partie " + index + ", " + world.getName() + ") : TNT remise en place.");
            }
            world.setSpawnLocation(lobbySpawn());
        }

        /**
         * Chapiteau de cirque : piste de sciure au fond (sous la limite d'élimination), gradins colorés,
         * mur rayé rouge et blanc, toit conique rayé, lanternes suspendues, trapèze, projecteurs ; au milieu,
         * trois disques de TNT. Salle d'attente au-dessus du toit.
         */
        void build() {
            long t0 = System.currentTimeMillis();
            int R = radius, F = floorY, Fb = bottomFloor(), L = lobbyY;
            int ground = Fb - 22;
            int tentR = R + 18;
            int standsIn = R + 9;
            int wallTop = F + 16;
            int apex = Math.min(wallTop + 16, L - 6);
            int ext = tentR + 2;
            loadChunks(ext);

            // 1) on vide le volume
            for (int x = -ext; x <= ext; x++)
                for (int z = -ext; z <= ext; z++)
                    for (int y = ground - 3; y <= apex + 6; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
            for (int x = -10; x <= 10; x++)
                for (int z = -10; z <= 10; z++)
                    for (int y = L - 1; y <= L + 7; y++) set(x, y, z, Material.AIR);

            for (int x = -ext; x <= ext; x++)
                for (int z = -ext; z <= ext; z++) {
                    double d = Math.hypot(x, z);
                    if (d >= tentR + 1) continue;
                    double deg = Math.toDegrees(Math.atan2(z, x)) + 180;

                    // 2) sol du chapiteau : piste de sciure, bordure rayée, plancher
                    if (d <= R + 2) set(x, ground, z, Material.SAND);
                    else if (d <= R + 3.5) {
                        Material c = ((int) (deg / 6)) % 2 == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE;
                        set(x, ground, z, c);
                        set(x, ground + 1, z, c);
                    } else set(x, ground, z, Material.DARK_OAK_PLANKS);
                    // piédestal au centre de la piste
                    if (d <= 3) {
                        set(x, ground + 1, z, Material.RED_CONCRETE);
                        set(x, ground + 2, z, Material.WHITE_CONCRETE);
                        set(x, ground + 3, z, d <= 1.5 ? Material.GOLD_BLOCK : Material.YELLOW_CONCRETE);
                    }

                    // 3) gradins : 9 marches de 3 blocs, secteurs rouge / jaune / bleu, allées en quartz
                    if (d >= standsIn && d < tentR) {
                        int s = (int) (d - standsIn);
                        int top = ground + 2 + s * 3;
                        for (int y = ground + 1; y < top; y++) set(x, y, z, Material.SPRUCE_PLANKS);
                        boolean aisle = deg % 30 < 2.5;
                        Material seat = aisle ? Material.SMOOTH_QUARTZ : switch (((int) (deg / 30)) % 3) {
                            case 0 -> Material.RED_CONCRETE;
                            case 1 -> Material.YELLOW_CONCRETE;
                            default -> Material.BLUE_CONCRETE;
                        };
                        set(x, top, z, seat);
                    }

                    // 4) mur rayé (bandes de 10°), projecteurs tous les 30°
                    if (d >= tentR && d < tentR + 1) {
                        Material stripe = ((int) (deg / 10)) % 2 == 0 ? Material.RED_WOOL : Material.WHITE_WOOL;
                        for (int y = ground; y <= wallTop; y++) set(x, y, z, stripe);
                        set(x, ground, z, Material.DARK_OAK_PLANKS);
                        if (deg % 30 < 2) {
                            set(x, F - 4, z, Material.SEA_LANTERN);
                            set(x, F + 8, z, Material.SEA_LANTERN);
                        }
                    }

                    // 5) toit conique rayé (épaisseur 2), couronnes de lanternes de mer
                    int ry = wallTop + (int) Math.round((tentR - Math.min(d, tentR)) * (apex - wallTop) / (double) tentR);
                    Material roof = ((int) (deg / 15)) % 2 == 0 ? Material.RED_WOOL : Material.WHITE_WOOL;
                    boolean lightRing = (Math.abs(d - 12) < 0.7 || Math.abs(d - 26) < 0.7) && deg % 30 < 4;
                    set(x, ry, z, lightRing ? Material.SEA_LANTERN : roof);
                    set(x, ry + 1, z, roof);
                }

            // mât et fanion au sommet du toit
            int poleTop = Math.min(apex + 5, L - 2);
            for (int y = apex + 2; y <= poleTop; y++) set(0, y, 0, "minecraft:oak_fence");
            set(0, poleTop, 0, Material.GOLD_BLOCK);
            set(1, poleTop - 1, 0, Material.RED_WOOL);
            set(2, poleTop - 1, 0, Material.RED_WOOL);
            set(1, poleTop - 2, 0, Material.YELLOW_WOOL);

            // 6) lanternes suspendues tous les 30°, à R + 6 du centre (hors de portée de l'étage du haut)
            int ld = R + 6;
            for (int a = 0; a < 360; a += 30) {
                int x = (int) Math.round(Math.cos(Math.toRadians(a)) * ld);
                int z = (int) Math.round(Math.sin(Math.toRadians(a)) * ld);
                int ry = roofY(x, z, tentR, wallTop, apex);
                for (int y = ry - 1; y >= ry - 4; y--) set(x, y, z, "minecraft:iron_chain[axis=y]");
                set(x, ry - 5, z, "minecraft:lantern[hanging=true]");
            }

            // 7) trapèze au-dessus du centre, 12 blocs au-dessus de l'étage du haut
            int bar = F + 12;
            for (int sx : new int[]{-2, 2}) {
                int ry = roofY(sx, 0, tentR, wallTop, apex);
                for (int y = ry - 1; y > bar; y--) set(sx, y, 0, "minecraft:iron_chain[axis=y]");
            }
            for (int x = -2; x <= 2; x++) set(x, bar, 0, "minecraft:stripped_birch_log[axis=x]");

            // 8) lumière invisible (blocs de lumière, sans collision : les flèches et les joueurs passent)
            for (int x = -tentR + 4; x <= tentR - 4; x += 8)
                for (int z = -tentR + 4; z <= tentR - 4; z += 8) {
                    if (Math.hypot(x, z) > tentR - 3) continue;
                    for (int y : new int[]{F + 6, F - floorGap / 2, Fb - 8, ground + 6})
                        if (!isFloorLevel(y) && world.getBlockAt(x, y, z).getType().isAir()) set(x, y, z, "minecraft:light[level=15]");
                }

            // 9) les disques de TNT
            resetFloors();

            // 10) salle d'attente : damier rouge et blanc, centre doré, vitres jaunes, plafond de barrières
            for (int x = -9; x <= 9; x++)
                for (int z = -9; z <= 9; z++) {
                    double d = Math.hypot(x, z);
                    if (d > 9) continue;
                    if (d <= 1.5) set(x, L, z, Material.GOLD_BLOCK);
                    else if (d <= 2.5) set(x, L, z, Material.YELLOW_CONCRETE);
                    else if (d < 8) set(x, L, z, Math.floorMod(x + z, 2) == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE);
                    else {
                        set(x, L, z, Material.SMOOTH_QUARTZ);
                        for (int y = L + 1; y <= L + 4; y++) set(x, y, z, Material.YELLOW_STAINED_GLASS);
                    }
                    set(x, L + 5, z, Material.BARRIER);
                }
            for (int[] c : new int[][]{{5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) {
                set(c[0], L + 1, c[1], "minecraft:oak_fence");
                set(c[0], L + 2, c[1], "minecraft:lantern[hanging=false]");
            }

            world.setSpawnLocation(lobbySpawn());
            ensureSign();
            getLogger().info("Arène Bow Spleef construite (partie " + index + ", " + world.getName() + ") : chapiteau de rayon " + tentR
                    + ", " + floors + " étage(s) de TNT de rayon " + R + " (y " + F + " a " + Fb + "), élimination sous y " + deathY()
                    + ", salle d'attente en y " + L + " (" + (System.currentTimeMillis() - t0) + " ms).");
        }

        int roofY(int x, int z, int tentR, int wallTop, int apex) {
            double d = Math.min(Math.hypot(x, z), tentR);
            return wallTop + (int) Math.round((tentR - d) * (apex - wallTop) / (double) tentR);
        }

        /** Remet toute la TNT de l'arène (fin de partie, démarrage). */
        void resetFloors() {
            if (world == null) return;
            loadChunks(radius + 1);
            doomed.clear();
            int n = 0;
            for (int i = 0; i < floors; i++) {
                int y = floorAt(i);
                for (int x = -radius - 1; x <= radius + 1; x++)
                    for (int z = -radius - 1; z <= radius + 1; z++) {
                        if (!inDisc(x, z, radius)) continue;
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType() != Material.TNT) {
                            b.setType(Material.TNT, false);
                            n++;
                        }
                    }
            }
            removeArrows();
            if (n > 0) getLogger().info("Bow Spleef partie " + index + " : " + n + " blocs de TNT remis.");
        }

        void removeArrows() {
            if (world == null) return;
            for (Entity e : world.getEntities()) if (e instanceof AbstractArrow) e.remove();
        }

        void ensureSign() {
            if (world == null || (sign != null && sign.isValid())) return;
            Location l = new Location(world, 0.5, lobbyY + 2.8, -6.5);
            if (!l.isChunkLoaded()) return;
            sign = world.spawn(l, TextDisplay.class, td -> {
                tag(td);
                td.text(Component.text("BOW SPLEEF", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                        .append(Component.newline())
                        .append(Component.text("Tire sur la TNT sous les pieds des autres !", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Aucun dégât, " + floors + " étages, dernier debout gagne.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Triple tir et " + cfg("double-jumps", 3) + " doubles sauts.", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(false);
            });
        }

        // -------------------------------------------------------------- destruction de la TNT

        /** Une flèche du tireur a touché la case (x, y, z) : la TNT autour disparaît (même étage). */
        void breakAt(Block hit, Player shooter) {
            int r = Math.max(0, cfg("break-radius", 1));
            String shape = getConfig().getString("break-shape", "carre").toLowerCase(Locale.ROOT);
            int y = hit.getY();
            List<Block> gone = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    boolean in = switch (shape) {
                        case "croix" -> Math.abs(dx) + Math.abs(dz) <= r;
                        case "rond" -> dx * dx + dz * dz <= r * r + r;
                        default -> true;
                    };
                    if (!in) continue;
                    int x = hit.getX() + dx, z = hit.getZ() + dz;
                    if (!isArenaCell(x, y, z)) continue;
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.TNT) continue;
                    gone.add(b);
                }
            if (gone.isEmpty()) return;
            for (Block b : gone) removeCell(b, true);
            world.playSound(hit.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.8f);
            world.playSound(hit.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f);
            // kill crédité au tireur pour les joueurs qui se tenaient sur ces blocs (meilleur effort)
            if (shooter == null) return;
            for (Player v : online(alive)) {
                if (v.equals(shooter) || !v.getWorld().equals(world)) continue;
                Location l = v.getLocation();
                if (l.getY() < y + 0.5 || l.getY() > y + 3.5) continue;
                for (Block b : gone)
                    if (Math.abs(l.getX() - (b.getX() + 0.5)) < 0.85 && Math.abs(l.getZ() - (b.getZ() + 0.5)) < 0.85) {
                        lastHit.put(v.getUniqueId(), shooter.getUniqueId());
                        lastHitTime.put(v.getUniqueId(), System.currentTimeMillis());
                        break;
                    }
            }
        }

        void removeCell(Block b, boolean loud) {
            b.setType(Material.AIR, false);
            brokenCount++;
            Location c = b.getLocation().add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.FLAME, c, loud ? 6 : 3, 0.3, 0.3, 0.3, 0.02);
            world.spawnParticle(Particle.SMOKE, c, loud ? 4 : 2, 0.3, 0.3, 0.3, 0.01);
            if (loud && random.nextInt(3) == 0) world.spawnParticle(Particle.EXPLOSION, c, 1);
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
                    if (players.size() >= min) {
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
                        if (players.size() >= cfg("max-players", 16)) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0)
                            for (Player p : online(players)) {
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> {
                    if (freezeTicks <= 0) {
                        gameLeft--;
                        int elapsed = Math.max(30, cfg("game-time", 480)) - gameLeft;
                        int sd = cfg("sudden-death-time", 180);
                        if (!suddenDeath && elapsed >= sd) {
                            suddenDeath = true;
                            for (Player p : online(players)) {
                                p.showTitle(Title.title(Component.text("Mort subite !", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                                        Component.text("La TNT disparaît toute seule", NamedTextColor.GOLD)));
                                p.playSound(p, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
                            }
                            broadcast("§c§lMort subite ! §7Des blocs de TNT disparaissent au hasard, de plus en plus vite.");
                        } else if (!suddenDeath && (sd - elapsed == 30 || sd - elapsed == 10)) {
                            broadcast("§eMort subite dans §6" + (sd - elapsed) + " s§e !");
                        }
                        if (suddenDeath) suddenDeathSecond(elapsed - sd);
                        if (gameLeft == 60 || gameLeft == 30 || gameLeft == 10)
                            broadcast("§eFin de la partie dans §6" + gameLeft + " s§e !");
                        if (gameLeft <= 0) timeUp();
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

        /** Mort subite : k blocs de TNT au hasard condamnés (ils fument puis disparaissent). */
        void suddenDeathSecond(int since) {
            int k = cfg("sudden-death-base", 2) + (since / Math.max(1, cfg("sudden-death-step", 15))) * cfg("sudden-death-increase", 1);
            k = Math.max(1, Math.min(k, cfg("sudden-death-max", 30)));
            long due = ticks + Math.max(0, cfg("sudden-death-warning", 1)) * 20L;
            Set<Long> already = new HashSet<>();
            for (Doomed d : doomed) already.add(key(d.x(), d.y(), d.z()));
            int added = 0;
            for (int tries = 0; tries < k * 10 && added < k; tries++) {
                int y = floorAt(random.nextInt(floors));
                int x = random.nextInt(2 * radius + 1) - radius;
                int z = random.nextInt(2 * radius + 1) - radius;
                if (!inDisc(x, z, radius) || world.getBlockAt(x, y, z).getType() != Material.TNT) continue;
                if (!already.add(key(x, y, z))) continue;
                doomed.add(new Doomed(x, y, z, due));
                added++;
            }
        }

        long key(int x, int y, int z) {
            return ((long) (x + 512) << 20) | ((long) (z + 512) << 8) | (y & 0xFF);
        }

        /** Toutes les 5 ticks : un joueur du monde qui tombe hors partie revient à sa place. */
        void safety() {
            for (Player p : world.getPlayers()) {
                if (alive.contains(p.getUniqueId()) && state == State.PLAYING) continue;
                if (p.getGameMode() == GameMode.CREATIVE) continue;
                double y = p.getLocation().getY();
                boolean lost = p.getGameMode() == GameMode.SPECTATOR ? y < bottomFloor() - 40 : y < deathY();
                boolean lobbyFall = !running() && players.contains(p.getUniqueId()) && y < lobbyY - 3 && y > floorY + 20;
                if (!lost && !lobbyFall) continue;
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                if (!players.contains(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) p.teleport(lobbySpawn());
                else if (players.contains(p.getUniqueId()) && !running()) p.teleport(lobbySpawn());
                else p.teleport(spectatorSpot());
            }
        }

        void gameTick() {
            if (freezeTicks > 0) {
                freezeTicks--;
                if (freezeTicks % 20 == 0) {
                    int s = freezeTicks / 20;
                    for (Player p : online(inGame)) {
                        Component t = s > 0 ? Component.text(String.valueOf(s), NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                                : Component.text("GO !", NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
                        p.showTitle(Title.title(t, s > 0 ? Component.text("Prépare ton arc...", NamedTextColor.GRAY) : Component.text("Tire sur la TNT !", NamedTextColor.YELLOW),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                        p.playSound(p, s > 0 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                    }
                }
                return;
            }
            double bound = radius + Math.max(2, cfg("boundary-margin", 6));
            for (Player p : online(alive)) {
                if (!p.getWorld().equals(world)) continue;
                Location l = p.getLocation();
                // chute sous le dernier étage
                if (l.getY() < deathY()) {
                    eliminate(p);
                    if (state != State.PLAYING) return;
                    continue;
                }
                // trop loin du centre : repoussé vers l'arène (le décor n'est pas un refuge)
                double h = Math.hypot(l.getX(), l.getZ());
                if (h > bound && ticks % 5 == 0) {
                    Vector back = new Vector(-l.getX(), 0, -l.getZ()).normalize().multiply(0.8).setY(0.25);
                    p.setVelocity(back);
                    p.sendActionBar(Component.text("Reste sous le chapiteau !", NamedTextColor.RED));
                }
                updateFlight(p);
            }
            // TNT condamnée par la mort subite : fumée puis disparition
            if (!doomed.isEmpty()) {
                for (Iterator<Doomed> it = doomed.iterator(); it.hasNext(); ) {
                    Doomed d = it.next();
                    Block b = world.getBlockAt(d.x(), d.y(), d.z());
                    if (b.getType() != Material.TNT) { it.remove(); continue; }
                    if (ticks >= d.due()) {
                        removeCell(b, false);
                        if (random.nextInt(4) == 0) world.playSound(b.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.5f);
                        it.remove();
                    } else if (ticks % 4 == 0) {
                        world.spawnParticle(Particle.LARGE_SMOKE, b.getLocation().add(0.5, 1.05, 0.5), 1, 0.2, 0.05, 0.2, 0.005);
                    }
                }
            }
        }

        /** Double saut possible : sauts restants, sol touché depuis le dernier, en aventure. */
        void updateFlight(Player p) {
            UUID id = p.getUniqueId();
            if (p.getGameMode() != GameMode.ADVENTURE) return;
            if (needLand.contains(id) && p.isOnGround()) needLand.remove(id);
            boolean allow = jumps.getOrDefault(id, 0) > 0 && !needLand.contains(id);
            if (p.getAllowFlight() != allow) p.setAllowFlight(allow);
            if (!allow && p.isFlying()) p.setFlying(false);
        }

        void doubleJump(Player p) {
            UUID id = p.getUniqueId();
            int left = jumps.getOrDefault(id, 0);
            if (left <= 0 || needLand.contains(id)) return;
            left--;
            jumps.put(id, left);
            needLand.add(id);
            p.setFlying(false);
            p.setAllowFlight(false);
            Vector v = p.getLocation().getDirection().setY(0);
            if (v.lengthSquared() < 1e-4) v = new Vector();
            else v.normalize().multiply(cfgD("double-jump-power", 0.8));
            v.setY(cfgD("double-jump-height", 0.8));
            p.setVelocity(v);
            p.setFallDistance(0);
            p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.3f);
            world.spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.05, 0.3, 0.02);
            if (mayTouch(p)) {
                if (left > 0) p.getInventory().setItem(2, feather(left));
                else p.getInventory().setItem(2, null);
            }
        }

        // -------------------------------------------------------------- éliminations

        Player killerOf(Player p) {
            Long t = lastHitTime.get(p.getUniqueId());
            if (t == null || System.currentTimeMillis() - t > cfg("kill-credit-time", 8) * 1000L) return null;
            Player k = Bukkit.getPlayer(lastHit.get(p.getUniqueId()));
            return k != null && inGame.contains(k.getUniqueId()) ? k : null;
        }

        /** Tombé sous le dernier étage : éliminé, kill au dernier tireur (8 s). */
        void eliminate(Player p) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            outOrder.add(id);
            Player killer = killerOf(p);
            lastHit.remove(id);
            lastHitTime.remove(id);
            tripleArmed.remove(id);
            needLand.remove(id);
            if (killer != null && !killer.equals(p)) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " " + GAME_ID + " kill");
                killer.playSound(killer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                broadcast("§c" + p.getName() + " §7est tombé à cause de §a" + killer.getName() + " §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            } else {
                broadcast("§c" + p.getName() + " §7est tombé dans le vide §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            }
            if (p.getWorld().equals(world)) {
                for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
                p.getInventory().clear();
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                p.setAllowFlight(false);
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(spectatorSpot());
            }
            p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text("Tu es tombé de l'arène", NamedTextColor.GRAY)));
            p.playSound(p, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
            checkEnd();
            reportStatus();
        }

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

        /** Fin du temps : égalité entre tous les survivants. */
        void timeUp() {
            List<UUID> w = new ArrayList<>(alive);
            broadcast("§eTemps écoulé ! " + (w.size() > 1 ? "Égalité entre les " + w.size() + " survivants." : "Victoire du dernier survivant."));
            endGame(w);
        }

        // -------------------------------------------------------------- kit

        void giveKit(Player p) {
            if (!p.getWorld().equals(world)) return;
            p.getInventory().clear();
            p.getInventory().setItem(0, bow());
            p.getInventory().setItem(1, tripleItem());
            int j = jumps.getOrDefault(p.getUniqueId(), 0);
            if (j > 0) p.getInventory().setItem(2, feather(j));
            p.getInventory().setItem(9, kit(new ItemStack(Material.ARROW)));
            p.getInventory().setHeldItemSlot(0);
        }

        void activateTriple(Player p) {
            UUID id = p.getUniqueId();
            if (!alive.contains(id) || state != State.PLAYING || freezeTicks > 0) return;
            if (tripleArmed.contains(id)) { p.sendActionBar(Component.text("Triple tir déjà prêt : tire !", NamedTextColor.GOLD)); return; }
            long left = tripleReady.getOrDefault(id, 0L) - System.currentTimeMillis();
            if (left > 0) {
                p.sendActionBar(Component.text("Triple tir dans " + (left / 1000 + 1) + " s", NamedTextColor.RED));
                return;
            }
            int cd = Math.max(1, cfg("triple-cooldown", 10));
            tripleArmed.add(id);
            tripleReady.put(id, System.currentTimeMillis() + cd * 1000L);
            p.setCooldown(Material.BLAZE_ROD, cd * 20);
            p.playSound(p, Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.2f);
            p.sendActionBar(Component.text("Triple tir prêt : ton prochain tir part en 3 flèches !", NamedTextColor.GOLD));
        }

        // -------------------------------------------------------------- partie

        boolean running() {
            return state == State.PLAYING || state == State.ENDING;
        }

        void startGame() {
            List<Player> list = online(players);
            if (list.isEmpty() || world == null) { state = State.WAITING; return; }
            resetFloors();
            alive.clear();
            inGame.clear();
            kills.clear();
            lastHit.clear();
            lastHitTime.clear();
            jumps.clear();
            needLand.clear();
            tripleArmed.clear();
            tripleReady.clear();
            outOrder.clear();
            state = State.PLAYING;
            total = list.size();
            gameLeft = Math.max(30, cfg("game-time", 480));
            freezeTicks = Math.max(0, cfg("freeze-time", 3)) * 20 + 1;
            suddenDeath = false;
            brokenCount = 0;
            world.setTime(cfg("time", 6000));
            int n = list.size(), i = 0;
            int dj = Math.max(0, cfg("double-jumps", 3));
            for (Player p : list) {
                UUID id = p.getUniqueId();
                if (!p.getWorld().equals(world)) continue;
                alive.add(id);
                inGame.add(id);
                kills.put(id, 0);
                jumps.put(id, dj);
                resetPlayer(p, GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.teleport(startSpot(i++, n));
                giveKit(p);
                p.showTitle(Title.title(Component.text("BOW SPLEEF", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                        Component.text("Tire sur la TNT sous leurs pieds !", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
            total = inGame.size();
            reportStatus();
            broadcast("§eLa partie commence avec §6" + total + " §ejoueurs ! §7Une flèche détruit la TNT, le dernier debout gagne.");
            broadcast("§7Bâton de blaze = §6triple tir §7(" + cfg("triple-cooldown", 10) + " s), saut x2 = §bdouble saut §7(" + dj + " par partie). §cMort subite à "
                    + clock(cfg("sudden-death-time", 180)) + "§7.");
        }

        void endGame(List<UUID> winners) {
            endGame(winners, true);
        }

        void endGame(List<UUID> winners, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            doomed.clear();
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
                    ? Component.text("Fin de partie", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    : Component.text(win.size() == 1 ? names.toString() : "Égalité !", win.size() == 1 ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
            for (Player p : online(players)) {
                boolean won = win.contains(p);
                p.showTitle(Title.title(title, Component.text(win.size() > 1 ? tieSubtitle(win) : won ? "Tu as gagné le Bow Spleef !" : win.size() == 1 ? "gagne le Bow Spleef !" : "", NamedTextColor.YELLOW)));
                p.playSound(p, won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                if (p.getWorld().equals(world)) p.setAllowFlight(false);
            }
            if (win.isEmpty()) broadcast("§7La partie se termine sans vainqueur.");
            else broadcast("§6" + names + " §eremporte" + (win.size() > 1 ? "nt" : "") + " le Bow Spleef !");
            List<UUID> byKills = new ArrayList<>(inGame);
            byKills.sort((a, b) -> Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0)));
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(3, byKills.size()); i++) {
                Player k = Bukkit.getPlayer(byKills.get(i));
                top.append(i == 0 ? "" : "§7, ").append("§f").append(k != null ? k.getName() : "?").append(" §8(").append(kills.getOrDefault(byKills.get(i), 0)).append(")");
            }
            if (!byKills.isEmpty()) broadcast("§7Kills : " + top + " §8| §7TNT détruite : §f" + brokenCount);
            for (Player w : win) {
                for (int i = 0; i < 6; i++)
                    Bukkit.getScheduler().runTaskLater(EemiBowSpleef.this, () -> {
                        if (w.isOnline() && state == State.ENDING && !destroyed && w.getWorld().equals(world)) firework(w.getLocation());
                    }, i * 20L);
            }
            alive.clear();
            tripleArmed.clear();
            reportStatus();
        }

        /** Après la fin : inscrits en salle d'attente, spectateurs au hub, TNT remise ; copie vide détruite. */
        void backToLobby() {
            state = State.WAITING;
            alive.clear();
            inGame.clear();
            jumps.clear();
            needLand.clear();
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
            Bukkit.getScheduler().runTaskLater(EemiBowSpleef.this, () -> {
                if (world != null && !destroyed && state == State.WAITING) resetFloors();
            }, 20L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(List.of(), false);
            if (state == State.ENDING) backToLobby();
            if (state == State.STARTING) state = State.WAITING;
        }

        // -------------------------------------------------------------- inscription

        void addPlayer(Player p) {
            UUID id = p.getUniqueId();
            Game sg = spectatorGame(p);
            if (sg != null) sg.spectators.remove(id);
            players.add(id);
            toWaiting(p);
            broadcast("§a" + p.getName() + " §7a rejoint le Bow Spleef §8(partie " + index + ") §7(§f" + players.size() + "/" + cfg("max-players", 16) + "§7).");
            reportStatus();
        }

        /** Téléporte d'abord dans la salle d'attente, puis (3 ticks après, si arrivé) vide l'inventaire. */
        void toWaiting(Player p) {
            Location l = lobbySpawn();
            p.teleport(l);
            later(() -> {
                if (!p.isOnline() || !players.contains(p.getUniqueId()) || destroyed) return;
                if (!p.getWorld().equals(world)) return;
                resetPlayer(p, GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.teleport(l);
                p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter le Bow Spleef"));
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
            jumps.remove(id);
            needLand.remove(id);
            tripleArmed.remove(id);
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // sans téléportation (changement de monde) : on ne touche plus au joueur (Multiverse-Inventories s'en occupe)
            if (teleport) sendToHub(p);
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
                p.sendActionBar(Component.text("Spectateur - " + alive.size() + " joueur" + (alive.size() > 1 ? "s" : "") + " en jeu", NamedTextColor.GRAY));
                return;
            }
            int j = jumps.getOrDefault(id, 0), max = Math.max(0, cfg("double-jumps", 3));
            Component c = Component.text("Doubles sauts : ", NamedTextColor.GRAY)
                    .append(Component.text("●".repeat(j), NamedTextColor.AQUA))
                    .append(Component.text("●".repeat(Math.max(0, max - j)), NamedTextColor.DARK_GRAY))
                    .append(Component.text("  |  Triple tir : ", NamedTextColor.GRAY));
            long left = tripleReady.getOrDefault(id, 0L) - System.currentTimeMillis();
            if (tripleArmed.contains(id)) c = c.append(Component.text("ARME", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            else if (left > 0) c = c.append(Component.text((left / 1000 + 1) + " s", NamedTextColor.YELLOW));
            else c = c.append(Component.text("pret", NamedTextColor.GREEN));
            p.sendActionBar(c);
        }

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("bs", Criteria.DUMMY,
                        Component.text("BOW SPLEEF", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("bs");
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
            boolean playing = inGame.contains(p.getUniqueId()) && running();
            if (!playing) {
                l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("n°" + index, NamedTextColor.WHITE)));
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + cfg("max-players", 16), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("Étages : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(floors), NamedTextColor.GOLD)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GOLD)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else {
                    l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
                    l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
                }
            } else {
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(gameLeft), NamedTextColor.WHITE)));
                int elapsed = Math.max(30, cfg("game-time", 480)) - gameLeft;
                int sd = cfg("sudden-death-time", 180);
                if (suddenDeath) l.add(Component.text("MORT SUBITE", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                else l.add(Component.text("Mort subite : ", NamedTextColor.GRAY).append(Component.text(clock(sd - elapsed), NamedTextColor.RED)));
                l.add(Component.text("En jeu : ", NamedTextColor.GRAY).append(Component.text(alive.size() + "/" + total, NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                List<UUID> order = new ArrayList<>(inGame);
                order.sort((a, b) -> {
                    int c = Boolean.compare(alive.contains(b), alive.contains(a));
                    return c != 0 ? c : Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0));
                });
                int shown = 0;
                for (UUID id : order) {
                    if (shown >= 7) break;
                    Player o = Bukkit.getPlayer(id);
                    String name = o != null ? o.getName() : "?";
                    boolean me = id.equals(p.getUniqueId());
                    boolean in = alive.contains(id);
                    Component line = Component.text(name + " ", me ? NamedTextColor.YELLOW : in ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY)
                            .append(in ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✖", NamedTextColor.DARK_RED));
                    l.add(line);
                    shown++;
                }
                l.add(Component.text("   "));
                l.add(Component.text("Tes kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.GREEN)));
            }
            l.add(Component.text("    "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
            for (Player p : online(spectators)) p.sendMessage(PREFIX + msg);
        }

        String stateLabel() {
            if (preparing) return "preparation";
            return switch (state) {
                case WAITING -> "attente";
                case STARTING -> "décompte " + timer + " s";
                case PLAYING -> "en jeu" + (suddenDeath ? " (mort subite)" : "");
                case ENDING -> "fin";
            };
        }
    }

    // ------------------------------------------------------------------ objets

    private ItemStack bow() {
        ItemStack it = new ItemStack(Material.BOW);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Arc de feu", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.addEnchant(Enchantment.INFINITY, 1, true);
        m.addEnchant(Enchantment.FLAME, 1, true);
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack tripleItem() {
        ItemStack it = kit(new ItemStack(Material.BLAZE_ROD));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Triple tir", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Clic droit : ton prochain tir", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("part en 3 flèches en éventail.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack feather(int n) {
        ItemStack it = kit(new ItemStack(Material.FEATHER, Math.max(1, Math.min(64, n))));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Double saut (" + n + ")", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Appuie deux fois sur saut.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        it.setItemMeta(m);
        return it;
    }

    private <T extends Entity> T tag(T e) {
        e.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
        return e;
    }

    private boolean isOurs(Entity e) {
        return e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE);
    }

    private void firework(Location l) {
        l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class, f -> {
            tag(f);
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                    .withColor(Color.RED, Color.YELLOW).withFade(Color.ORANGE).flicker(true).build());
            m.setPower(1);
            f.setFireworkMeta(m);
        });
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

    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (pendingOf(p) != null) { p.sendMessage(PREFIX + "§7Ta partie est en préparation, patiente un instant."); return; }
        if (games.isEmpty()) { p.sendMessage(PREFIX + "§cLe monde du Bow Spleef n'est pas chargé."); return; }
        int max = cfg("max-players", 16);
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

    /** Renvoi au hub : seulement depuis un monde du jeu ou le hub (jamais depuis le skyblock...). */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        p.setAllowFlight(false);
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    /** On ne touche au joueur que dans un monde du jeu (partie, copie, modèle) ou au hub. */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        if (isGameWorld(w)) return true;
        World hub = hubWorld();
        return hub != null && w.equals(hub);
    }

    private World hubWorld() {
        return Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
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
        p.setArrowsInBody(0);
    }

    private static String clock(int s) {
        s = Math.max(0, s);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    // ------------------------------------------------------------------ événements

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemibowspleef.admin");
    }

    /** Aucun dégât dans les mondes du jeu (coups, flèches, chute, feu...) : le recul des flèches est fait à la main. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player victim && isGameWorld(victim.getWorld())) {
            e.setCancelled(true);
            victim.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent e) {
        if (e.getEntity() instanceof Player && isGameWorld(e.getEntity().getWorld())) e.setCancelled(true);
    }

    /**
     * Impact d'une flèche : jamais l'effet vanilla (TNT allumée par la Flamme, dégâts). Sur la TNT de l'arène,
     * le bloc et ses voisins disparaissent ; sur un joueur, petit recul sans dégât. La flèche disparaît.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow arrow) || !isGameWorld(arrow.getWorld())) return;
        e.setCancelled(true);
        arrow.remove();
        Game g = gameAt(arrow.getWorld());
        if (g == null || g.state != State.PLAYING || g.freezeTicks > 0) return;
        Player shooter = arrow.getShooter() instanceof Player sp ? sp : null;
        if (shooter == null || !g.alive.contains(shooter.getUniqueId())) return;
        Block hit = e.getHitBlock();
        if (hit != null) {
            if (hit.getType() == Material.TNT && isArenaCell(hit.getX(), hit.getY(), hit.getZ())) g.breakAt(hit, shooter);
            return;
        }
        if (e.getHitEntity() instanceof Player victim && !victim.equals(shooter) && g.alive.contains(victim.getUniqueId())) {
            Vector v = arrow.getVelocity().setY(0);
            if (v.lengthSquared() < 1e-4) v = victim.getLocation().toVector().subtract(shooter.getLocation().toVector()).setY(0);
            if (v.lengthSquared() < 1e-4) v = new Vector(1, 0, 0);
            v.normalize().multiply(cfgD("arrow-knockback", 0.6)).setY(cfgD("arrow-knockback-y", 0.3));
            victim.setVelocity(victim.getVelocity().multiply(0.5).add(v));
            victim.playSound(victim, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1f);
            shooter.playSound(shooter, Sound.ENTITY_ARROW_HIT_PLAYER, 0.6f, 1.4f);
            g.lastHit.put(victim.getUniqueId(), shooter.getUniqueId());
            g.lastHitTime.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** Jamais de TNT allumée, de feu ni d'explosion dans les mondes du jeu. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrime(TNTPrimeEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIgnite(BlockIgniteEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(BlockBurnEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (isGameWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
            e.blockList().clear();
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && (isGameWorld(p.getWorld()) || gameOf(p) != null)) e.setCancelled(true);
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
        if (!isGameWorld(e.getLocation().getWorld())) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.NATURAL || r == CreatureSpawnEvent.SpawnReason.ENDER_PEARL) e.setCancelled(true);
    }

    /** Nos entités (flèches du triple tir, panneau, feux d'artifice) apparaissent même si WorldGuard les bloque. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (e.isCancelled() && isOurs(e.getEntity()) && isGameWorld(e.getLocation().getWorld())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // le décor ne se règle pas ; l'arc et le triple tir restent utilisables
        if (isGameWorld(p.getWorld()) && !admin(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
        }
        Game g = gameOf(p);
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !isKit(it)) return;
        if (it.getType() == Material.RED_BED) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté le Bow Spleef.");
        } else if (it.getType() == Material.BLAZE_ROD) {
            e.setCancelled(true);
            g.activateTriple(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p) || !isGameWorld(p.getWorld()) || admin(p)) return;
        Game g = gameAt(p.getWorld());
        if (g == null || g.state != State.PLAYING || g.freezeTicks > 0 || !g.alive.contains(p.getUniqueId())) e.setCancelled(true);
    }

    /** Tir : flèche non ramassable ; triple tir armé = deux flèches de plus en éventail. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isGameWorld(p.getWorld())) return;
        if (!(e.getProjectile() instanceof AbstractArrow main)) return;
        main.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        Game g = gameAt(p.getWorld());
        if (g == null || g.state != State.PLAYING || !g.tripleArmed.remove(p.getUniqueId())) return;
        Vector v = main.getVelocity();
        double angle = Math.toRadians(cfgD("triple-angle", 12));
        for (double a : new double[]{-angle, angle}) {
            Vector dv = v.clone().rotateAroundY(a);
            p.getWorld().spawn(main.getLocation(), Arrow.class, ar -> {
                tag(ar);
                ar.setShooter(p);
                ar.setVelocity(dv);
                ar.setFireTicks(2000);
                ar.setCritical(main.isCritical());
                ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            });
        }
        p.playSound(p, Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.8f);
    }

    /** Double saut : appuyer deux fois sur saut (le vol est autorisé seulement pour ça, jamais pour voler). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!isGameWorld(p.getWorld()) || !e.isFlying()) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        e.setCancelled(true);
        Game g = gameOf(p);
        if (g != null && g.state == State.PLAYING && g.freezeTicks <= 0 && g.alive.contains(p.getUniqueId()) && p.getWorld().equals(g.world)) {
            g.doubleJump(p);
        } else {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

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

    /** Sécurité (/kill...) : pas de drops ; en partie, compte comme une chute. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Game g = gameOf(p);
        if (g == null || g.world == null || !p.getWorld().equals(g.world)) return;
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
        if (g.running()) {
            e.setRespawnLocation(g.spectatorSpot());
            later(() -> {
                if (!p.isOnline() || !p.getWorld().equals(g.world)) return;
                if (g.alive.contains(p.getUniqueId()) && g.state == State.PLAYING) g.eliminate(p);
                else p.setGameMode(GameMode.SPECTATOR);
            });
            return;
        }
        e.setRespawnLocation(g.lobbySpawn());
        later(() -> g.toWaiting(p));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (gameOf(e.getPlayer()) != null || isKit(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && gameOf(p) != null && !admin(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    /** Quitter le monde de SA partie = quitter la partie (sans toucher au joueur). */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player sp = e.getPlayer();
        Game sg = spectatorGame(sp);
        if (sg != null && !sp.getWorld().equals(sg.world)) {
            sg.spectators.remove(sp.getUniqueId());
            if (sp.getGameMode() == GameMode.SPECTATOR && mayTouch(sp)) sp.setGameMode(GameMode.SURVIVAL);
        }
        Game g = gameOf(sp);
        if (g != null && g.world != null && e.getFrom().equals(g.world) && !sp.getWorld().equals(g.world)) g.leave(sp, false);
        // le double saut de DeluxeHub laisse allowFlight : on le retire en arrivant dans un monde du jeu
        if (isGameWorld(sp.getWorld()) && sp.getGameMode() != GameMode.CREATIVE && sp.getGameMode() != GameMode.SPECTATOR) {
            sp.setFlying(false);
            sp.setAllowFlight(false);
        }
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
        boolean admin = sender.hasPermission("eemibowspleef.admin");
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
                p.sendMessage(PREFIX + "§7Tu as quitté le Bow Spleef.");
            }
            case "start" -> {
                if (!admin) return true;
                Game g = targetGame(sender, args);
                if (g == null || g.preparing) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                if (g.running()) { sender.sendMessage(PREFIX + "§cUne partie est déjà en cours."); return true; }
                if (g.players.isEmpty()) { sender.sendMessage(PREFIX + "§cPersonne n'est inscrit."); return true; }
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
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ") :");
                for (Game g : games.values())
                    sender.sendMessage("§8- §e#" + g.index + " §f" + WorldClone.name(base, g.index) + " §7" + g.stateLabel()
                            + " §8| §7joueurs §f" + g.players.size()
                            + (g.pending.isEmpty() ? "" : " §8(+" + g.pending.size() + " en route)")
                            + (g.running() ? " §8| §7en jeu §f" + g.alive.size() : "")
                            + (g.spectators.isEmpty() ? "" : " §8| §7spectateurs §f" + g.spectators.size()));
                sender.sendMessage("§8- §7modèle §f" + modelName() + " §7" + (model != null ? "pret" : modelBusy ? "en préparation" : "absent"));
            }
            case "build" -> {
                if (!admin) return true;
                if (g1 == null || g1.world == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g1.running()) { sender.sendMessage(PREFIX + "§cImpossible : la partie 1 est en cours."); return true; }
                String oldBase = base;
                loadSettings();
                base = oldBase;
                g1.ensureBuilt(true);
                for (Player pl : online(g1.players)) g1.toWaiting(pl);
                makeModel();
                sender.sendMessage(PREFIX + "§aArène de la partie 1 reconstruite §7(modèle refait).");
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.running()) { sender.sendMessage(PREFIX + "§cImpossible : la partie " + g.index + " est en cours."); return true; }
                String oldBase = base;
                String oldSig = buildSignature();
                loadSettings();
                if (!base.equals(oldBase)) {
                    base = oldBase;
                    sender.sendMessage(PREFIX + "§cLe monde du jeu ne change qu'au redémarrage.");
                }
                for (Game g : games.values()) if (g.world != null) prepareRules(g.world);
                sender.sendMessage(PREFIX + "§aConfig rechargée." + (oldSig.equals(buildSignature()) ? "" : " §e/bowspleef build §7pour appliquer la nouvelle arène."));
            }
            default -> sender.sendMessage(PREFIX + "§7/bowspleef <join|leave|spectate [joueur]>" + (admin ? " §8| admin : start [n], stop [n], parties, build, reload" : ""));
        }
        return true;
    }

    private void spectate(Player p, String targetName) {
        if (gameOf(p) != null || pendingOf(p) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/bowspleef leave §cd'abord."); return; }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : gameOf(t);
            if (g == null) { p.sendMessage(PREFIX + "§cCe joueur n'est dans aucune partie de Bow Spleef."); return; }
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
        // téléporter d'abord (Multiverse-Inventories sauvegarde l'inventaire du monde quitté), puis vider
        p.teleport(g.spectatorSpot());
        if (!p.getWorld().equals(g.world)) {
            g.spectators.remove(p.getUniqueId());
            return;
        }
        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        p.sendMessage(PREFIX + "§7Tu regardes la partie " + g.index + ", §f/lobby §7pour revenir.");
    }

    // ------------------------------------------------------------------ EemiCore

    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /** Statut agrégé de toutes les parties pour EemiCore (envoyé s'il a changé, ou toutes les 30 s). */
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
        String status = "status " + GAME_ID + " " + st + " " + waiting + " " + playing + " " + cfg("max-players", 16) * maxInstances();
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
