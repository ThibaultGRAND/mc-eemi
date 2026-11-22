package fr.eemicraft.pvprun;

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
import org.bukkit.block.data.type.Leaves;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * PVP Run d'EEMICRAFT (TNT Games d'Hypixel) : un TNT Run avec PvP, chacun pour soi.
 * Trois étages (étoile de sable, fleur de poudre de béton, spirale de gravier) posés sur de la TNT
 * au-dessus du vide : le bloc sous les pieds d'un joueur (et la TNT dessous) disparaît block-delay ticks
 * après son passage. Épée en pierre incassable (le combat 1.8 sans recharge vient d'OldCombatMechanics),
 * pas de régénération naturelle, un kill rend 3 cœurs, pas de dégâts de chute. Coup mortel ou chute sous
 * void-y = éliminé (spectateur), chute créditée au dernier frappeur (10 s). 5 doubles sauts par partie.
 * Dernier debout gagnant, sinon le plus de PV au bout de 10 min.
 *
 * Plusieurs parties en même temps (max-instances, 3 par défaut) : partie 1 = monde « pvprun », partie n =
 * copie « pvprun_n » du modèle « pvprun_modele » (copie propre de la partie 1). Une copie est détruite dès
 * qu'elle est vide.
 */
public final class EemiPvpRun extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§6PVP Run§8] §7";

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
    private static final String GAME_ID = "pvprun";
    private static final String HEART = "❤";
    /** Version de la construction (décor + étages) : la changer force la reconstruction au démarrage. */
    private static final int BUILD_VERSION = 1;
    /** Demi-largeur de la zone des étages (les motifs ne dépassent pas 20 blocs du centre). */
    private static final int FLOOR_R = 21;

    enum State { WAITING, STARTING, PLAYING, ENDING }

    /** Case d'un bloc à faire disparaître. */
    private record Pos(int x, int y, int z) {}

    // ------------------------------------------------------------------ état global (pas d'état de partie ici)

    private NamespacedKey kitKey, entityKey, buildKey;
    /** Nom du monde de la partie 1 (config « world »). */
    private String base = "pvprun";
    private int floorY, floorGap, floorCount, lobbyY, voidY;
    /** Parties par numéro (1 = monde d'origine, n ≥ 2 = copies). */
    private final Map<Integer, Game> games = new TreeMap<>();
    /** Modèle propre (copie de la partie 1) d'où partent les copies ; personne n'y va. */
    private World model;
    private boolean modelBusy;
    private int modelGen;
    private long ticks;
    private String lastStatus;
    private long lastStatusAt;
    private final Random random = new Random();

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
                if (!buildSignature().equals(g1.world.getPersistentDataContainer().get(buildKey, PersistentDataType.STRING))) g1.build();
                else g1.resetFloors(true);
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
            // la partie 1 garde un sol complet sur le disque
            if (g.index == 1 && g.world != null && g.running()) g.resetFloors(false);
        }
        // les copies restées sur le disque sont supprimées au prochain démarrage (WorldClone.cleanup)
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "pvprun");
        floorY = cfg("floor-y", 100);
        floorGap = Math.max(4, cfg("floor-gap", 10));
        floorCount = Math.max(1, Math.min(6, cfg("floors", 3)));
        lobbyY = cfg("lobby-y", 140);
        voidY = cfg("void-y", floorY - 6);
    }

    /** Signature de la construction : version du code + réglages qui changent l'arène. */
    private String buildSignature() {
        return BUILD_VERSION + ":" + floorY + ":" + floorGap + ":" + floorCount + ":" + lobbyY;
    }

    private int topY() {
        return floorY + (floorCount - 1) * floorGap;
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
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
        if (w == null) getLogger().severe("Impossible de charger ou créer le monde " + base + " : PVP Run désactivé.");
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

    // ------------------------------------------------------------------ modèle et copies

    /**
     * (Re)fait le modèle depuis la partie 1 (qui doit être propre : juste construite ou remise à zéro).
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
        WorldClone.create(this, g1.world, modelName(), w -> {
            if (gen != modelGen) return; // un modèle plus récent est en route
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

    /** Nouvelle partie dans une copie du modèle (numéro libre le plus bas), ou null si impossible. */
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
            g.resetFloors(false);
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

    /** Règles d'un monde du jeu (partie ou modèle) : jour fixe, pas de mobs, pas de dégâts de chute, pas de regen. */
    private void prepareRules(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
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

    // ------------------------------------------------------------------ motifs des étages

    /**
     * Bloc du sol de l'étage level (0 = le plus bas) en (x, z), centre en 0, 0 ; null = vide.
     * Étage du haut : étoile à 8 branches en sable / sable rouge. Étage du milieu : fleur à 4 pétales en
     * poudre de béton (vert, bleu, rose, jaune) bordée de gravier. Étage du bas : disque ondulé percé au
     * centre, spirale de gravier / poudre cyan / poudre gris clair. Au-delà de 3 étages, les motifs se répètent.
     */
    private Material floorAt(int level, int x, int z) {
        int pattern = Math.floorMod(floorCount - 1 - level, 3);
        double d = Math.hypot(x, z);
        double a = Math.atan2(z, x);
        switch (pattern) {
            case 0 -> { // étoile
                double lim = 14.5 + 2.5 * Math.cos(8 * a);
                if (d > lim) return null;
                if (d < 3.5) return Material.RED_SAND;
                if (d < 4.5 || d > lim - 1.2) return Material.ORANGE_CONCRETE_POWDER;
                int sector = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * 16);
                return Math.floorMod(sector, 2) == 0 ? Material.SAND : Material.RED_SAND;
            }
            case 1 -> { // fleur
                double lim = 10 + 8 * Math.abs(Math.cos(2 * a));
                if (d > lim) return null;
                if (d < 4.5) return Material.WHITE_CONCRETE_POWDER;
                if (d > lim - 1.2) return Material.GRAVEL;
                int petal = Math.floorMod((int) Math.round(a / (Math.PI / 2)), 4);
                // nervure du pétale
                boolean rib = (petal % 2 == 0) ? Math.abs(z) < 1 : Math.abs(x) < 1;
                if (rib) return Material.WHITE_CONCRETE_POWDER;
                return switch (petal) {
                    case 0 -> Material.LIME_CONCRETE_POWDER;
                    case 1 -> Material.LIGHT_BLUE_CONCRETE_POWDER;
                    case 2 -> Material.PINK_CONCRETE_POWDER;
                    default -> Material.YELLOW_CONCRETE_POWDER;
                };
            }
            default -> { // spirale
                double lim = 18.5 + 1.0 * Math.cos(6 * a);
                if (d > lim || d < 2.5) return null;
                if (d > lim - 1) return Material.GRAY_CONCRETE_POWDER;
                double t = a / (2 * Math.PI) * 3 + d / 7;
                return switch (Math.floorMod((int) Math.floor(t * 2), 3)) {
                    case 0 -> Material.GRAVEL;
                    case 1 -> Material.CYAN_CONCRETE_POWDER;
                    default -> Material.LIGHT_GRAY_CONCRETE_POWDER;
                };
            }
        }
    }

    /** Hauteur du sol de l'étage level. */
    private int levelY(int level) {
        return floorY + level * floorGap;
    }

    /** Numéro d'étage dont y est le sol, ou -1. */
    private int levelOf(int y) {
        int dy = y - floorY;
        if (dy < 0 || dy % floorGap != 0) return -1;
        int l = dy / floorGap;
        return l < floorCount ? l : -1;
    }

    // ------------------------------------------------------------------ une partie

    final class Game {
        final int index;
        World world;
        boolean preparing, destroyed;

        final Set<UUID> players = new LinkedHashSet<>();
        /** Joueurs qui attendent au hub que la copie soit prête. */
        final Set<UUID> pending = new LinkedHashSet<>();
        final Set<UUID> inGame = new LinkedHashSet<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> spectators = new LinkedHashSet<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        final Map<UUID, Integer> jumpsLeft = new HashMap<>();
        final Map<UUID, Long> jumpReady = new HashMap<>();
        /** Ordre d'élimination (le dernier éliminé en dernier). */
        final List<UUID> outOrder = new ArrayList<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Blocs qui vont disparaître → tick de disparition. */
        final Map<Pos, Long> fading = new LinkedHashMap<>();
        /** Cases de départ : sol de l'étage du haut, voisines pleines. */
        final List<Location> spots = new ArrayList<>();

        State state = State.WAITING;
        int timer;
        int freezeTicks;
        int gameLeft;
        int total;
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
            Location l = new Location(world, 0.5, topY() + 12, FLOOR_R + 8.5);
            l.setYaw(180);
            l.setPitch(40);
            return l;
        }

        Location facingCenter(Location l) {
            Location c = l.clone();
            Vector dir = new Vector(0.5 - l.getX(), 0, 0.5 - l.getZ());
            if (dir.lengthSquared() > 0.01) c.setDirection(dir);
            c.setPitch(0);
            return c;
        }

        /** Case de départ : au plus près d'un point du cercle de rayon 9 sur l'étage du haut. */
        Location startSpot(int i, int n) {
            if (spots.isEmpty()) computeSpots();
            double ang = 2 * Math.PI * i / Math.max(1, n);
            double tx = 0.5 + Math.cos(ang) * 9, tz = 0.5 + Math.sin(ang) * 9;
            Location best = null;
            double bd = Double.MAX_VALUE;
            for (Location s : spots) {
                double d = Math.hypot(s.getX() - tx, s.getZ() - tz);
                if (d < bd) { bd = d; best = s; }
            }
            if (best == null) best = new Location(world, tx, topY() + 1, tz);
            return facingCenter(best);
        }

        void computeSpots() {
            spots.clear();
            int top = floorCount - 1;
            for (int x = -FLOOR_R; x <= FLOOR_R; x++)
                for (int z = -FLOOR_R; z <= FLOOR_R; z++)
                    if (floorAt(top, x, z) != null && floorAt(top, x + 1, z) != null && floorAt(top, x - 1, z) != null
                            && floorAt(top, x, z + 1) != null && floorAt(top, x, z - 1) != null)
                        spots.add(new Location(world, x + 0.5, topY() + 1, z + 0.5));
        }

        // -------------------------------------------------------------- construction

        void set(int x, int y, int z, Material m) {
            world.getBlockAt(x, y, z).setType(m, false);
        }

        /** Pose un état de bloc écrit en texte ; invalide = ignoré avec un avertissement. */
        void setState(int x, int y, int z, String state) {
            BlockData bd;
            try {
                bd = Bukkit.createBlockData(state);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Bloc invalide ignore : " + state);
                return;
            }
            world.getBlockAt(x, y, z).setBlockData(bd, false);
        }

        /**
         * Construit tout : vide le volume, pose le décor (piliers rouges et blancs façon bâton de TNT, anneau
         * rayé sous l'arène, îles flottantes), la salle d'attente et les étages. Version gardée dans le PDC du monde.
         */
        void build() {
            int ext = 48;
            for (int cx = (-ext) >> 4; cx <= ext >> 4; cx++)
                for (int cz = (-ext) >> 4; cz <= ext >> 4; cz++) world.getChunkAt(cx, cz).load(true);
            int yMin = Math.min(floorY, voidY) - 22, yMax = Math.max(topY() + 16, lobbyY + 8);
            for (int x = -ext; x <= ext; x++)
                for (int z = -ext; z <= ext; z++)
                    for (int y = yMin; y <= yMax; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
            try {
                buildDecor();
            } catch (RuntimeException ex) {
                getLogger().log(java.util.logging.Level.SEVERE, "Décor du PVP Run impossible a poser", ex);
            }
            buildLobby();
            resetFloors(false);
            world.setSpawnLocation(lobbySpawn());
            world.getPersistentDataContainer().set(buildKey, PersistentDataType.STRING, buildSignature());
            ensureSign();
            getLogger().info("Arène PVP Run construite (partie " + index + ", " + world.getName() + ") : " + floorCount
                    + " étages en y " + floorY + ".." + topY() + ", élimination sous y " + voidY + ", salle d'attente en y " + lobbyY + ".");
        }

        void buildDecor() {
            int bottom = floorY, top = topY();
            // 1) huit piliers rayés rouge/blanc (bâtons de TNT géants), coiffés de TNT et d'une lanterne
            int pyMin = bottom - 12, pyMax = top + 8;
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4 + Math.PI / 8;
                int cx = (int) Math.round(Math.cos(ang) * 32), cz = (int) Math.round(Math.sin(ang) * 32);
                for (int y = pyMin; y <= pyMax; y++) {
                    Material band = Math.floorMod((y - pyMin) / 3, 2) == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE;
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++) {
                            boolean corner = dx != 0 && dz != 0;
                            set(cx + dx, y, cz + dz, corner ? Material.POLISHED_BLACKSTONE : band);
                        }
                    // incrustations lumineuses sur les faces, tous les 6 blocs
                    if ((y - pyMin) % 6 == 4) {
                        set(cx + 1, y, cz, Material.SHROOMLIGHT);
                        set(cx - 1, y, cz, Material.SHROOMLIGHT);
                        set(cx, y, cz + 1, Material.SHROOMLIGHT);
                        set(cx, y, cz - 1, Material.SHROOMLIGHT);
                    }
                }
                // pointe sous le pilier
                set(cx, pyMin - 1, cz, Material.POLISHED_BLACKSTONE);
                set(cx, pyMin - 2, cz, Material.POLISHED_BLACKSTONE_WALL);
                setState(cx, pyMin - 3, cz, "minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]");
                // chapeau
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) set(cx + dx, pyMax + 1, cz + dz, Material.POLISHED_BLACKSTONE_BRICKS);
                set(cx, pyMax + 2, cz, Material.TNT);
                setState(cx, pyMax + 3, cz, "minecraft:soul_lantern[hanging=false]");
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    setState(cx + o[0], pyMax + 2, cz + o[1], "minecraft:polished_blackstone_brick_slab[type=bottom]");
            }

            // 2) anneau rayé rouge/blanc sous l'arène (hors de la trajectoire des chutes)
            int ry = bottom - 8;
            for (int x = -37; x <= 37; x++)
                for (int z = -37; z <= 37; z++) {
                    double d = Math.hypot(x, z);
                    if (d < 34 || d > 36.5) continue;
                    double a = Math.atan2(z, x);
                    int seg = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * 48);
                    set(x, ry, z, Math.floorMod(seg, 2) == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE);
                    if (d > 35.8) setState(x, ry + 1, z, "minecraft:polished_blackstone_brick_wall");
                }

            // 3) quatre îles flottantes avec un arbre et un tas de TNT
            for (int k = 0; k < 4; k++) {
                double ang = k * Math.PI / 2;
                int cx = (int) Math.round(Math.cos(ang) * 42), cz = (int) Math.round(Math.sin(ang) * 42);
                int iy = bottom + floorGap / 2 + (k % 2) * 4 + 2;
                buildIsland(cx, iy, cz, k);
            }
        }

        void buildIsland(int cx, int iy, int cz, int k) {
            Random r = new Random(1234L + k);
            for (int dx = -5; dx <= 5; dx++)
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > 4.6) continue;
                    set(cx + dx, iy, cz + dz, Material.GRASS_BLOCK);
                    int depth = (int) Math.round((4.6 - d) * 1.3) + 1;
                    for (int y = 1; y <= depth; y++) set(cx + dx, iy - y, cz + dz, y <= 2 ? Material.DIRT : Material.STONE);
                    if (d < 4 && r.nextInt(6) == 0 && (Math.abs(dx) > 1 || Math.abs(dz) > 1))
                        set(cx + dx, iy + 1, cz + dz, r.nextBoolean() ? Material.POPPY : Material.DANDELION);
                    else if (d < 4 && r.nextInt(4) == 0 && (Math.abs(dx) > 1 || Math.abs(dz) > 1))
                        set(cx + dx, iy + 1, cz + dz, Material.SHORT_GRASS);
                }
            // arbre
            BlockData leaves = Material.OAK_LEAVES.createBlockData();
            if (leaves instanceof Leaves lv) lv.setPersistent(true);
            for (int y = 1; y <= 5; y++) set(cx, iy + y, cz, Material.OAK_LOG);
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int y = 4; y <= 7; y++) {
                        int rad = y >= 6 ? 1 : 2;
                        if (Math.abs(dx) > rad || Math.abs(dz) > rad) continue;
                        if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                        if (dx == 0 && dz == 0 && y <= 5) continue;
                        Block b = world.getBlockAt(cx + dx, iy + y, cz + dz);
                        if (b.getType().isAir()) b.setBlockData(leaves, false);
                    }
            // petit tas de TNT au bord de l'île
            int tx = cx + 2, tz = cz - 2;
            set(tx, iy + 1, tz, Material.TNT);
            set(tx + 1, iy + 1, tz, Material.TNT);
            set(tx, iy + 1, tz - 1, Material.TNT);
            set(tx, iy + 2, tz, Material.TNT);
            set(tx - 1, iy + 1, tz, Material.HAY_BLOCK);
        }

        /** Salle d'attente : vitre au centre (on voit l'arène dessous), damier rouge/blanc, verre rouge, plafond de barrières. */
        void buildLobby() {
            int L = lobbyY;
            for (int x = -9; x <= 9; x++)
                for (int z = -9; z <= 9; z++)
                    for (int y = L - 1; y <= L + 7; y++) set(x, y, z, Material.AIR);
            for (int x = -7; x <= 7; x++)
                for (int z = -7; z <= 7; z++) {
                    int d = Math.max(Math.abs(x), Math.abs(z));
                    if (d <= 2) set(x, L, z, Material.GLASS);
                    else if (d == 3) set(x, L, z, Material.SEA_LANTERN);
                    else if (d < 7) set(x, L, z, Math.floorMod(x + z, 2) == 0 ? Material.WHITE_CONCRETE : Material.RED_CONCRETE);
                    else {
                        set(x, L, z, Material.SMOOTH_QUARTZ);
                        for (int y = L + 1; y <= L + 4; y++) set(x, y, z, Material.RED_STAINED_GLASS);
                    }
                    set(x, L + 5, z, Material.BARRIER);
                }
            // quatre TNT décoratives dans les coins
            for (int[] c : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) set(c[0], L + 1, c[1], Material.TNT);
        }

        /**
         * Remet tous les étages : bloc qui tombe du motif + TNT dessous, air ailleurs (efface aussi les restes).
         * Les blocs sont posés sans mise à jour physique : ils ne tombent pas.
         */
        void resetFloors(boolean log) {
            fading.clear();
            for (int x = -FLOOR_R; x <= FLOOR_R; x++)
                for (int z = -FLOOR_R; z <= FLOOR_R; z++)
                    for (int level = 0; level < floorCount; level++) {
                        int y = levelY(level);
                        Material m = floorAt(level, x, z);
                        Block top = world.getBlockAt(x, y, z), under = world.getBlockAt(x, y - 1, z);
                        if (m == null) {
                            if (!top.getType().isAir()) top.setType(Material.AIR, false);
                            if (!under.getType().isAir()) under.setType(Material.AIR, false);
                        } else {
                            if (top.getType() != m) top.setType(m, false);
                            if (under.getType() != Material.TNT) under.setType(Material.TNT, false);
                        }
                    }
            for (Entity e : world.getEntitiesByClass(FallingBlock.class)) e.remove();
            computeSpots();
            if (log) getLogger().info("Étages du PVP Run remis a neuf (partie " + index + ").");
        }

        /** Panneau flottant de la salle d'attente (non sauvegardé : recréé s'il a disparu). */
        void ensureSign() {
            if (world == null || (sign != null && sign.isValid())) return;
            Location l = new Location(world, 0.5, lobbyY + 2.8, -5.5);
            if (!l.isChunkLoaded()) return;
            sign = world.spawn(l, TextDisplay.class, td -> {
                tag(td);
                td.text(Component.text("PVP RUN", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .append(Component.newline())
                        .append(Component.text("Le sol disparaît sous tes pieds !", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Épée en pierre, " + cfg("double-jumps", 5) + " doubles sauts, pas de regen.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Un kill rend " + fmtHearts(cfg("kill-heal", 6)) + ". Dernier debout gagne.", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
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
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.RED).decorate(TextDecoration.BOLD),
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
        }

        /** Toutes les 5 ticks : un joueur du monde qui tombe hors partie revient à sa place. */
        void safety() {
            for (Player p : world.getPlayers()) {
                if (alive.contains(p.getUniqueId()) && state == State.PLAYING) continue;
                if (p.getGameMode() == GameMode.CREATIVE) continue;
                double y = p.getLocation().getY();
                boolean lost = p.getGameMode() == GameMode.SPECTATOR ? y < voidY - 40 : y < voidY;
                boolean lobbyFall = !running() && players.contains(p.getUniqueId()) && y < lobbyY - 3 && y > topY() + 3;
                if (!lost && !lobbyFall) continue;
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                if (players.contains(p.getUniqueId()) && !running()) p.teleport(lobbySpawn());
                else if (!players.contains(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) p.teleport(lobbySpawn());
                else p.teleport(spectatorSpot());
            }
        }

        void gameTick() {
            if (freezeTicks > 0) {
                freezeTicks--;
                if (freezeTicks % 20 == 0) {
                    int s = freezeTicks / 20;
                    for (Player p : online(inGame)) {
                        Component t = s > 0 ? Component.text(String.valueOf(s), NamedTextColor.RED).decorate(TextDecoration.BOLD)
                                : Component.text("GO !", NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
                        p.showTitle(Title.title(t, s > 0 ? Component.text("Prepare-toi...", NamedTextColor.GRAY) : Component.text("Cours et frappe !", NamedTextColor.YELLOW),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                        p.playSound(p, s > 0 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_TNT_PRIMED, 1f, s > 0 ? 1f : 1.2f);
                    }
                }
                return;
            }
            long delay = Math.max(1, cfg("block-delay", 10));
            long now = System.currentTimeMillis();
            int maxJumps = cfg("double-jumps", 5);
            for (Player p : online(alive)) {
                if (!p.getWorld().equals(world)) continue;
                // chute sous le dernier étage
                if (p.getLocation().getY() < voidY) {
                    eliminate(p, killerOf(p), true);
                    if (state != State.PLAYING) return;
                    continue;
                }
                markUnder(p, delay);
                // double saut : vol autorisé seulement quand un saut est disponible (PlayerToggleFlightEvent le consomme)
                UUID id = p.getUniqueId();
                boolean can = maxJumps > 0 && jumpsLeft.getOrDefault(id, 0) > 0 && jumpReady.getOrDefault(id, 0L) <= now
                        && (p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SURVIVAL);
                if (p.getAllowFlight() != can) p.setAllowFlight(can);
                if (!can && p.isFlying()) p.setFlying(false);
                if (ticks % 4 == 0) actionBar(p);
            }
            // disparition des blocs dont le délai est écoulé
            for (Iterator<Map.Entry<Pos, Long>> itr = fading.entrySet().iterator(); itr.hasNext(); ) {
                Map.Entry<Pos, Long> e = itr.next();
                if (e.getValue() > ticks) continue;
                itr.remove();
                Pos ps = e.getKey();
                Block b = world.getBlockAt(ps.x(), ps.y(), ps.z());
                if (b.getType().hasGravity()) b.setType(Material.AIR, false);
                Block under = b.getRelative(0, -1, 0);
                if (under.getType() == Material.TNT) under.setType(Material.AIR, false);
            }
        }

        /**
         * Comme TNT Run : chaque bloc de sol touché par la boîte de collision du joueur (ses 4 coins) est
         * programmé pour disparaître. On regarde sous les pieds et jusqu'à 1,5 bloc plus bas (sauter sur
         * place ne sauve pas le bloc).
         */
        void markUnder(Player p, long delay) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) return;
            BoundingBox bb = p.getBoundingBox();
            double feet = bb.getMinY();
            double[] xs = {bb.getMinX() + 0.001, bb.getMaxX() - 0.001};
            double[] zs = {bb.getMinZ() + 0.001, bb.getMaxZ() - 0.001};
            int[] ys = {(int) Math.floor(feet - 0.01), (int) Math.floor(feet - 1.01), (int) Math.floor(feet - 1.51)};
            for (int y : ys) {
                if (levelOf(y) < 0) continue;
                for (double x : xs)
                    for (double z : zs) {
                        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
                        Pos ps = new Pos(bx, y, bz);
                        if (fading.containsKey(ps)) continue;
                        if (!world.getBlockAt(bx, y, bz).getType().hasGravity()) continue;
                        fading.put(ps, ticks + delay);
                    }
            }
        }

        // -------------------------------------------------------------- combat et éliminations

        Player killerOf(Player p) {
            Long t = lastHitTime.get(p.getUniqueId());
            if (t == null || System.currentTimeMillis() - t > cfg("kill-credit-time", 10) * 1000L) return null;
            Player k = Bukkit.getPlayer(lastHit.get(p.getUniqueId()));
            return k != null && alive.contains(k.getUniqueId()) ? k : null;
        }

        /** Éliminé (coup mortel annulé ou chute) : spectateur sur place (ou au-dessus de l'arène après une chute). */
        void eliminate(Player p, Player killer, boolean fell) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            outOrder.add(id);
            lastHit.remove(id);
            lastHitTime.remove(id);
            if (killer != null && !killer.equals(p)) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " " + GAME_ID + " kill");
                killer.playSound(killer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                // un kill rend des cœurs au tueur
                int heal = Math.max(0, cfg("kill-heal", 6));
                if (heal > 0 && killer.getWorld().equals(world) && !killer.isDead()) {
                    AttributeInstance hp = killer.getAttribute(Attribute.MAX_HEALTH);
                    double max = hp == null ? 20 : hp.getValue();
                    killer.setHealth(Math.min(max, killer.getHealth() + heal));
                    killer.sendActionBar(Component.text("+" + fmtHearts(heal), NamedTextColor.RED));
                    world.spawnParticle(Particle.HEART, killer.getLocation().add(0, 2, 0), 4, 0.4, 0.2, 0.4, 0);
                }
                broadcast("§c" + p.getName() + " §7" + (fell ? "est tombé, poussé par" : "a été tué par") + " §a" + killer.getName()
                        + " §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            } else {
                broadcast("§c" + p.getName() + " §7" + (fell ? "est tombé dans le vide" : "est mort") + " §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            }
            if (p.getWorld().equals(world)) {
                for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
                p.getInventory().clear();
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                p.setAllowFlight(false);
                AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(hp == null ? 20 : hp.getValue());
                p.setGameMode(GameMode.SPECTATOR);
                if (fell) p.teleport(spectatorSpot());
            }
            String by = killer != null && !killer.equals(p) ? (fell ? "Poussé par " : "Tué par ") + killer.getName() : (fell ? "Tu es tombé" : "Tu es mort");
            p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text(by, NamedTextColor.GRAY)));
            p.playSound(p, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
            checkEnd();
            reportStatus();
        }

        /** Dernier debout (ou plus personne) : fin de partie. */
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

        int hpOf(UUID id) {
            Player p = Bukkit.getPlayer(id);
            return p == null ? 0 : (int) Math.ceil(p.getHealth());
        }

        /** Fin du temps : le ou les joueurs avec le plus de PV gagnent. */
        void timeUp() {
            int best = 0;
            for (UUID id : alive) best = Math.max(best, hpOf(id));
            List<UUID> w = new ArrayList<>();
            for (UUID id : alive) if (hpOf(id) == best) w.add(id);
            broadcast("§eTemps écoulé ! " + (w.size() > 1 ? "Égalité entre les joueurs à " : "Victoire au joueur à ") + "§6" + best + " PV§e.");
            endGame(w);
        }

        // -------------------------------------------------------------- kit

        void giveKit(Player p) {
            if (!p.getWorld().equals(world)) return;
            p.getInventory().clear();
            p.getInventory().setItem(0, sword());
            p.getInventory().setHeldItemSlot(0);
        }

        // -------------------------------------------------------------- partie

        boolean running() {
            return state == State.PLAYING || state == State.ENDING;
        }

        void startGame() {
            List<Player> list = online(players);
            if (list.isEmpty() || world == null) { state = State.WAITING; return; }
            resetFloors(false);
            alive.clear();
            inGame.clear();
            kills.clear();
            lastHit.clear();
            lastHitTime.clear();
            jumpsLeft.clear();
            jumpReady.clear();
            outOrder.clear();
            state = State.PLAYING;
            total = list.size();
            gameLeft = Math.max(30, cfg("game-time", 600));
            freezeTicks = Math.max(0, cfg("freeze-time", 3)) * 20 + 1;
            world.setTime(cfg("time", 6000));
            int n = list.size(), i = 0;
            for (Player p : list) {
                UUID id = p.getUniqueId();
                if (!p.getWorld().equals(world)) continue;
                alive.add(id);
                inGame.add(id);
                kills.put(id, 0);
                jumpsLeft.put(id, Math.max(0, cfg("double-jumps", 5)));
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(startSpot(i++, n));
                giveKit(p);
                p.showTitle(Title.title(Component.text("PVP RUN", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("Le sol disparaît, frappe les autres !", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
            total = inGame.size();
            reportStatus();
            broadcast("§eLa partie commence avec §6" + total + " §ejoueurs ! §7Le sol disparaît derrière toi, pas de régénération naturelle, "
                    + "un kill rend " + fmtHearts(cfg("kill-heal", 6)) + ", " + cfg("double-jumps", 5) + " doubles sauts. Dernier debout gagnant.");
        }

        void endGame(List<UUID> winners) {
            endGame(winners, true);
        }

        /** rewards = false pour un arrêt admin (pas de récompenses EemiCore). */
        void endGame(List<UUID> winners, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            fading.clear();
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
                p.showTitle(Title.title(title, Component.text(win.size() > 1 ? tieSubtitle(win) : won ? "Tu as gagné le PVP Run !" : win.size() == 1 ? "gagne le PVP Run !" : "", NamedTextColor.YELLOW)));
                p.playSound(p, won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                if (p.getWorld().equals(world)) p.setAllowFlight(false);
            }
            if (win.isEmpty()) broadcast("§7La partie se termine sans vainqueur.");
            else broadcast("§6" + names + " §eremporte" + (win.size() > 1 ? "nt" : "") + " le PVP Run !");
            List<UUID> byKills = new ArrayList<>(inGame);
            byKills.sort((a, b) -> Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0)));
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(3, byKills.size()); i++) {
                Player k = Bukkit.getPlayer(byKills.get(i));
                String nm = k != null ? k.getName() : "?";
                top.append(i == 0 ? "" : "§7, ").append("§f").append(nm).append(" §8(").append(kills.getOrDefault(byKills.get(i), 0)).append(")");
            }
            if (!byKills.isEmpty()) broadcast("§7Kills : " + top);
            for (Player w : win)
                for (int i = 0; i < 6; i++)
                    Bukkit.getScheduler().runTaskLater(EemiPvpRun.this, () -> {
                        if (w.isOnline() && state == State.ENDING && !destroyed && w.getWorld().equals(world)) firework(w.getLocation());
                    }, i * 20L);
            alive.clear();
            reportStatus();
        }

        /**
         * Après la fin : les inscrits reviennent dans la salle d'attente, les spectateurs au hub, puis les
         * étages sont remis à neuf. Une copie vide est détruite.
         */
        void backToLobby() {
            state = State.WAITING;
            alive.clear();
            inGame.clear();
            jumpsLeft.clear();
            jumpReady.clear();
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
            resetFloors(false);
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
            broadcast("§a" + p.getName() + " §7a rejoint le PVP Run §8(partie " + index + ") §7(§f" + players.size() + "/" + cfg("max-players", 16) + "§7).");
            reportStatus();
        }

        void toWaiting(Player p) {
            Location l = lobbySpawn();
            p.teleport(l);
            later(() -> {
                if (!p.isOnline() || !players.contains(p.getUniqueId()) || destroyed) return;
                // téléportation refusée ou joueur reparti entre-temps : on ne touche à rien hors du monde de la partie
                if (!p.getWorld().equals(world)) return;
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(l);
                p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter le PVP Run"));
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
            jumpsLeft.remove(id);
            jumpReady.remove(id);
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // sans téléportation (le joueur a changé de monde) : on ne touche plus au joueur, Multiverse-Inventories
            // a déjà sauvegardé l'inventaire du jeu et chargé celui du monde d'arrivée
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
            int max = cfg("double-jumps", 5);
            int left = jumpsLeft.getOrDefault(id, 0);
            Component c = Component.text("PV : ", NamedTextColor.GRAY).append(Component.text(fmtHp(p.getHealth()), NamedTextColor.RED));
            if (max > 0) {
                c = c.append(Component.text("  |  Double saut : ", NamedTextColor.GRAY))
                        .append(Component.text(left + "/" + max, NamedTextColor.WHITE));
                long wait = jumpReady.getOrDefault(id, 0L) - System.currentTimeMillis();
                if (left <= 0) c = c.append(Component.text(" (épuisé)", NamedTextColor.DARK_GRAY));
                else if (wait > 0) c = c.append(Component.text(" (" + (wait / 1000 + 1) + " s)", NamedTextColor.YELLOW));
                else c = c.append(Component.text(" (prêt)", NamedTextColor.GREEN));
            }
            p.sendActionBar(c);
        }

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("pr", Criteria.DUMMY,
                        Component.text("PVP RUN", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("pr");
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
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + cfg("max-players", 16), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("Doubles sauts : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("double-jumps", 5)), NamedTextColor.AQUA)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.RED)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else {
                    l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
                    l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
                }
            } else {
                int max = cfg("double-jumps", 5);
                boolean isAlive = alive.contains(me);
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(gameLeft), NamedTextColor.WHITE)));
                l.add(Component.text("En vie : ", NamedTextColor.GRAY).append(Component.text(alive.size() + "/" + total, NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                if (isAlive) {
                    l.add(Component.text("Tes PV : ", NamedTextColor.GRAY).append(Component.text(fmtHp(p.getHealth()) + " " + HEART, NamedTextColor.RED)));
                    l.add(Component.text("Doubles sauts : ", NamedTextColor.GRAY).append(Component.text(jumpsLeft.getOrDefault(me, 0) + "/" + max, NamedTextColor.AQUA)));
                } else l.add(Component.text("Éliminé", NamedTextColor.DARK_GRAY));
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
                case PLAYING -> "en jeu";
                case ENDING -> "fin";
            };
        }
    }

    // ------------------------------------------------------------------ objets (communs)

    private ItemStack sword() {
        ItemStack it = new ItemStack(Material.STONE_SWORD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Épée du PVP Run", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
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
        Firework fw = l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class, f -> {
            tag(f);
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                    .withColor(Color.RED, Color.WHITE).withFade(Color.ORANGE).flicker(true).build());
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
     * /pvprun join : la partie en attente (ou en décompte, ou en préparation) qui a de la place, la plus
     * remplie d'abord ; sinon une nouvelle copie si on est sous max-instances ; sinon « pleines ».
     */
    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (pendingOf(p) != null) { p.sendMessage(PREFIX + "§7Ta partie est en préparation, patiente un instant."); return; }
        if (games.isEmpty()) { p.sendMessage(PREFIX + "§cLe monde du PVP Run n'est pas chargé."); return; }
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

    /** Renvoi au hub : seulement si le joueur est dans un monde du jeu ou déjà au hub (jamais ailleurs). */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    /**
     * Le plugin ne touche à l'inventaire, à la vie, aux effets ou au mode de jeu d'un joueur que s'il est
     * dans un monde du jeu (partie, copie, modèle) ou au hub : jamais dans un autre monde (skyblock...).
     */
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
        p.setFlying(false);
        p.setAllowFlight(false);
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

    // ------------------------------------------------------------------ affichage (commun)

    /** PV avec une décimale si besoin (« 13 » ou « 13,5 »). */
    private static String fmtHp(double hp) {
        double r = Math.round(hp * 2) / 2.0;
        return r == Math.floor(r) ? String.valueOf((int) r) : String.format(Locale.FRANCE, "%.1f", r);
    }

    /** « 3 coeurs » pour 6 PV. */
    private static String fmtHearts(int hp) {
        double h = hp / 2.0;
        String n = h == Math.floor(h) ? String.valueOf((int) h) : String.format(Locale.FRANCE, "%.1f", h);
        return n + " cœur" + (h > 1 ? "s" : "");
    }

    private static String clock(int s) {
        s = Math.max(0, s);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    // ------------------------------------------------------------------ événements

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemipvprun.admin");
    }

    private Player attackerOf(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Player pl) return pl;
        if (d instanceof Projectile proj && proj.getShooter() instanceof Player pl) return pl;
        return null;
    }

    /**
     * Dégâts : seuls les coups d'un joueur en vie sur un joueur en vie de la même partie passent (après le
     * départ). Priorité HIGHEST : OldCombatMechanics a déjà calculé les dégâts. Coup mortel = annulé,
     * la victime est éliminée et le tueur récupère kill-heal PV. Tout le reste (chute, etc.) est annulé.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !isGameWorld(victim.getWorld())) return;
        Game g = gameAt(victim.getWorld());
        UUID id = victim.getUniqueId();
        if (g == null || g.state != State.PLAYING || !g.alive.contains(id) || g.freezeTicks > 0) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent by
                && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE)) {
            Player attacker = attackerOf(by);
            if (attacker == null || attacker == victim || !g.alive.contains(attacker.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            e.setCancelled(false);
            g.lastHit.put(id, attacker.getUniqueId());
            g.lastHitTime.put(id, System.currentTimeMillis());
            if (e.getFinalDamage() >= victim.getHealth()) {
                // coup mortel : pas d'écran de mort, spectateur sur place
                e.setCancelled(true);
                victim.playSound(victim, Sound.ENTITY_PLAYER_HURT, 1f, 1f);
                g.eliminate(victim, attacker, false);
            }
            return;
        }
        e.setCancelled(true);
    }

    /** Pas de régénération naturelle (faim pleine) dans les mondes du jeu. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isGameWorld(p.getWorld())) return;
        EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
        if (r == EntityRegainHealthEvent.RegainReason.SATIATED || r == EntityRegainHealthEvent.RegainReason.REGEN) e.setCancelled(true);
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

    /** Le sable et le gravier des étages ne tombent jamais tout seuls. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType().hasGravity() && isGameWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock && isGameWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
            e.getEntity().remove();
        }
    }

    /** La TNT des étages et du décor ne s'allume jamais ; aucune explosion ne casse de bloc. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrime(TNTPrimeEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (isGameWorld(e.getEntity().getWorld())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (!isGameWorld(e.getLocation().getWorld())) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND) e.setCancelled(true);
    }

    /** Nos propres entités (panneau, feux d'artifice) apparaissent même si un autre plugin (WorldGuard) les bloque. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (e.isCancelled() && isOurs(e.getEntity()) && isGameWorld(e.getLocation().getWorld())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // le décor (TNT, fleurs...) ne se règle pas ; l'épée reste utilisable
        if (isGameWorld(p.getWorld()) && !admin(p) && (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL)) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
        }
        Game g = gameOf(p);
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté le PVP Run.");
        }
    }

    /**
     * Double saut : le vol n'est autorisé (par la boucle) que quand un saut est disponible ; appuyer deux
     * fois sur saut le consomme et propulse le joueur vers l'avant et le haut. Ailleurs dans nos mondes,
     * pas de vol (sauf créatif / spectateur).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!e.isFlying() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        Game g = gameOf(p);
        boolean here = isGameWorld(p.getWorld());
        if (g == null || !p.getWorld().equals(g.world)) {
            if (here) {
                e.setCancelled(true);
                p.setFlying(false);
                p.setAllowFlight(false);
            }
            return;
        }
        e.setCancelled(true);
        p.setFlying(false);
        p.setAllowFlight(false);
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        if (g.state != State.PLAYING || g.freezeTicks > 0 || !g.alive.contains(id)) return;
        int left = g.jumpsLeft.getOrDefault(id, 0);
        if (left <= 0 || g.jumpReady.getOrDefault(id, 0L) > now) return;
        g.jumpsLeft.put(id, left - 1);
        g.jumpReady.put(id, now + Math.max(0, cfg("double-jump-cooldown", 4)) * 1000L);
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 0.0001) dir.normalize().multiply(cfgD("double-jump-power", 1.1));
        dir.setY(cfgD("double-jump-height", 0.85));
        p.setVelocity(dir);
        p.setFallDistance(0);
        p.playSound(p, Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.3f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.05, 0.3, 0.02);
        g.updateSidebar(p);
    }

    /** Départ : immobiles pendant le décompte (on peut tourner la tête). */
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

    /** Sécurité (/kill...) : pas de drops ; la mort compte comme une élimination. */
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
        if (g.alive.contains(p.getUniqueId()) && g.state == State.PLAYING) {
            e.setRespawnLocation(g.spectatorSpot());
            Player killer = g.killerOf(p);
            later(() -> {
                if (!p.isOnline() || !g.alive.contains(p.getUniqueId()) || !p.getWorld().equals(g.world)) return;
                g.eliminate(p, killer, true);
            });
            return;
        }
        if (g.running()) {
            e.setRespawnLocation(g.spectatorSpot());
            later(() -> { if (p.isOnline() && p.getWorld().equals(g.world)) p.setGameMode(GameMode.SPECTATOR); });
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

    /** Quitter le monde de SA partie = quitter la partie. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player sp = e.getPlayer();
        Game sg = spectatorGame(sp);
        if (sg != null && !sp.getWorld().equals(sg.world)) {
            // spectateur parti (/lobby) : il redevient un joueur normal
            sg.spectators.remove(sp.getUniqueId());
            if (sp.getGameMode() == GameMode.SPECTATOR && mayTouch(sp)) sp.setGameMode(GameMode.SURVIVAL);
        }
        Game g = gameOf(sp);
        if (g != null && g.world != null && e.getFrom().equals(g.world) && !sp.getWorld().equals(g.world)) g.leave(sp, false);
        // arrivée dans un monde du jeu : pas de vol hérité du double saut de DeluxeHub
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
        boolean admin = sender.hasPermission("eemipvprun.admin");
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
                p.sendMessage(PREFIX + "§7Tu as quitté le PVP Run.");
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
                            + (g.running() ? " §8| §7en vie §f" + g.alive.size() : "")
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
                g1.build();
                for (Player pl : online(g1.players)) g1.toWaiting(pl);
                makeModel();
                sender.sendMessage(PREFIX + "§aArène de la partie 1 reconstruite §7(modèle refait).");
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.running()) { sender.sendMessage(PREFIX + "§cImpossible : une partie est en cours."); return true; }
                String oldBase = base;
                String oldSig = buildSignature();
                loadSettings();
                if (!base.equals(oldBase)) {
                    base = oldBase;
                    sender.sendMessage(PREFIX + "§cLe monde du jeu ne change qu'au redémarrage.");
                }
                for (Game g : games.values()) if (g.world != null) prepareRules(g.world);
                sender.sendMessage(PREFIX + "§aConfig rechargée." + (oldSig.equals(buildSignature()) ? "" : " §e/pvprun build §7pour appliquer les nouveaux étages."));
            }
            default -> sender.sendMessage(PREFIX + "§7/pvprun <join|leave|spectate [joueur]>" + (admin ? " §8| admin : start [n], stop [n], parties, build, reload" : ""));
        }
        return true;
    }

    /** Regarder une partie en cours en spectateur : celle d'un joueur, sinon la plus remplie. */
    private void spectate(Player p, String targetName) {
        if (gameOf(p) != null || pendingOf(p) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/pvprun leave §cd'abord."); return; }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : gameOf(t);
            if (g == null) { p.sendMessage(PREFIX + "§cCe joueur n'est dans aucune partie de PVP Run."); return; }
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
