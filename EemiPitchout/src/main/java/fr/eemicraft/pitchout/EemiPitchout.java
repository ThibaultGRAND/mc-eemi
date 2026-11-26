package fr.eemicraft.pitchout;

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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
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
 * Pitchout d'EEMICRAFT, façon Funcraft / Epicube / Hypixel.
 * Chacun pour soi sur l'une des trois arènes de la map « PitchOut 1.20-1.21 by Missid » (désert, jungle,
 * glace), tirée au sort après chaque partie. Aucun dégât : les coups
 * ne font que reculer (bâton Recul V, arc Frappe II, perle de l'Ender). Tomber = -1 vie et retour sur
 * la plateforme ; à 0 vie, éliminé. Le dernier frappeur (10 s) est crédité du kill. Des bonus
 * flottent sur la plateforme toutes les 20 s. Dernier en vie gagnant, sinon le plus de vies à la fin
 * du temps. Le plugin crée son monde vide lui-même et y pose l'arène (démarrage, fin de partie, /pitchout build|map).
 *
 * 2.1 : plusieurs parties en même temps (max-instances, 3 par défaut). Partie 1 = monde « pitchout »,
 * partie n = copie « pitchout_n » du modèle « pitchout_modele » (copie propre de la partie 1, refaite
 * après build / map). Une copie garde l'arène du modèle pour sa première partie, puis tire la sienne
 * au hasard après chaque partie comme la partie 1 ; elle est détruite dès qu'elle est vide.
 */
public final class EemiPitchout extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§cPitchout§8] §7";

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
    private static final String GAME_ID = "pitchout";
    private static final String HEART = "❤";

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Bonus { SPEED, JUMP, SUPER_STICK, PEARL, LIFE }

    /** Un bonus posé sur la plateforme : objet qui tourne + nom au-dessus. */
    private static final class BonusDrop {
        final Bonus type;
        final Location base;
        final ItemDisplay item;
        final TextDisplay label;
        int age;

        BonusDrop(Bonus type, Location base, ItemDisplay item, TextDisplay label) {
            this.type = type;
            this.base = base;
            this.item = item;
            this.label = label;
        }
    }

    // ------------------------------------------------------------------ arènes (2.0 : maps « PitchOut 1.20-1.21 by Missid »)

    /**
     * Arène reprise de la map fournie par Thibault : blocs dans arenas/<id>.pom du jar (gzip : « POM1 »,
     * largeur x, hauteur y, longueur z, taille de la palette, palette d'états de bloc, puis un index par
     * case, 0xFFFF = air ; ordre y/z/x). origin = coin de la boîte d'origine, (cx, cz) = centre de la
     * plateforme, radius = rayon de jeu (cases de réapparition). Le y 0 d'origine est posé en floor-y.
     */
    private record Arena(String name, int ox, int oy, int oz, int cx, int cz, int radius) {}

    private static final Map<String, Arena> ARENAS = new LinkedHashMap<>();
    static {
        // colisée de grès : plateforme de glace, fosse de lave tout autour
        ARENAS.put("desert", new Arena("Désert", -72, -8, -52, -35, -15, 27));
        // île de terracotta et anneau sous une couronne de mangrove
        ARENAS.put("jungle", new Arena("Jungle", 21, -8, -235, 42, -214, 22));
        // disque et anneaux de glace séparés par le vide
        ARENAS.put("glace", new Arena("Glace", 100, -2, -86, 125, -61, 26));
    }

    /** #86 : centre surélevé interdit (arbre de la Jungle, butte du Glace) : rayon et hauteur au-dessus du sol. */
    private int[] middleZone(String arenaId) {
        int defR = 0, defH = 0;
        if ("jungle".equals(arenaId)) { defR = 9; defH = 2; }
        else if ("glace".equals(arenaId)) { defR = 5; defH = 3; }
        return new int[] {getConfig().getInt("middle-forbidden." + arenaId + ".radius", defR),
                getConfig().getInt("middle-forbidden." + arenaId + ".min-height", defH)};
    }

    private boolean inMiddle(Game g, Location l) {
        if (g == null || l == null) return false;
        int[] z = middleZone(g.arenaId);
        if (z[0] <= 0) return false;
        double dx = l.getX(), dz = l.getZ();
        return dx * dx + dz * dz < (double) z[0] * z[0] && l.getY() >= floorY + z[1];
    }

    private static Arena arenaOf(String id) {
        Arena a = id == null ? null : ARENAS.get(id);
        return a != null ? a : ARENAS.values().iterator().next();
    }

    // ------------------------------------------------------------------ état global (pas d'état de partie ici)

    private NamespacedKey kitKey, entityKey;
    /** Nom du monde de la partie 1 (config « world »). */
    private String base = "pitchout";
    private int floorY, lobbyY, voidDepth;
    /** Parties par numéro (1 = monde d'origine, n ≥ 2 = copies). */
    private final Map<Integer, Game> games = new TreeMap<>();
    /** Modèle propre (copie de la partie 1) d'où partent les copies ; personne n'y va. */
    private World model;
    private String modelArenaId;
    /** 2.2.3 : derniere arene JOUEE (toutes parties), gardee dans data.yml (last-map) : jamais deux fois de suite, meme apres un redemarrage. */
    private String lastArena;
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
        saveDefaultConfig();
        loadSettings();
        lastArena = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile()).getString("last-map");
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
            g.clearBonuses();
            for (Player p : online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            }
            for (Player p : online(g.spectators)) if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            g.players.clear();
            g.spectators.clear();
            g.pending.clear();
            if (g.sign != null) g.sign.remove();
        }
        // les copies restées sur le disque sont supprimées au prochain démarrage (WorldClone.cleanup)
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "pitchout");
        floorY = cfg("floor-y", 100);
        lobbyY = cfg("lobby-y", 140);
        voidDepth = Math.max(3, cfg("void-depth", 15));
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
        if (w == null) getLogger().severe("Impossible de charger ou créer le monde " + base + " : Pitchout désactivé.");
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
        String arena = g1.arenaId;
        WorldClone.create(this, g1.world, modelName(), w -> {
            if (gen != modelGen) return; // un modèle plus récent est en route
            modelBusy = false;
            if (w == null) {
                getLogger().warning("Modèle " + modelName() + " impossible a créer : une seule partie possible.");
                return;
            }
            model = w;
            modelArenaId = arena;
            prepareRules(w);
            getLogger().info("Modèle " + modelName() + " prêt (arène " + arenaOf(arena).name() + ").");
        });
    }

    private java.io.File dataFile() {
        return new java.io.File(getDataFolder(), "data.yml");
    }

    /** 2.2.3 : note l'arene qui vient d'etre jouee (exclue du prochain tirage) et l'ecrit dans data.yml. */
    private void markPlayed(Game g) {
        g.lastPlayed = g.arenaId;
        lastArena = g.arenaId;
        java.io.File f = dataFile();
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        y.set("last-map", lastArena);
        try {
            y.save(f);
        } catch (java.io.IOException ex) {
            getLogger().warning("data.yml non ecrit : " + ex.getMessage());
        }
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
        String arena = modelArenaId;
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
            g.arenaId = arena;
            g.prepareWorld();
            g.applyArena();
            g.computeSpots();
            // 2.2.3 : tirage a la creation de la partie (premier joueur), avant d'y faire entrer quiconque
            String pick = g.pickArena();
            if (!pick.equals(g.arenaId)) g.build(pick);
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
        g.clearBonuses();
        if (g.sign != null) g.sign.remove();
        for (Player p : online(g.players)) g.leave(p, true);
        for (Player p : online(g.spectators)) sendToHub(p);
        g.spectators.clear();
        if (g.world != null) WorldClone.destroy(this, g.world, base, this::sendToHub);
        getLogger().info("Partie " + g.index + " terminée : copie " + WorldClone.name(base, g.index) + " supprimée.");
        reportStatus();
    }

    /** Règles d'un monde du jeu (partie ou modèle) : jour fixe, pas de mobs, pas de dégâts de chute, rien ne bouge. */
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
        String arenaId = "desert";
        /** 2.2.3 : derniere arene jouee par cette partie (null = celle de data.yml). */
        String lastPlayed;
        /** Arène imposée par un admin pour la prochaine construction (partie 1). */
        String nextArena;
        int radius, voidY;
        /** Cases de réapparition : sol plein de la plateforme principale, deux blocs libres au-dessus. */
        final List<Location> spots = new ArrayList<>();

        final Set<UUID> players = new LinkedHashSet<>();
        /** Joueurs qui attendent au hub que la copie soit prête. */
        final Set<UUID> pending = new LinkedHashSet<>();
        final Set<UUID> inGame = new LinkedHashSet<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> spectators = new LinkedHashSet<>();
        final Map<UUID, Integer> lives = new HashMap<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        final Map<UUID, Long> protectedUntil = new HashMap<>();
        final Map<UUID, Long> superUntil = new HashMap<>();
        final Map<UUID, Integer> pearlTimer = new HashMap<>();
        /** Ordre d'élimination (le dernier éliminé en dernier). */
        final List<UUID> outOrder = new ArrayList<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        final List<BonusDrop> bonuses = new ArrayList<>();

        State state = State.WAITING;
        int timer;
        int freezeTicks;
        int gameLeft;
        int bonusTimer;
        int total;
        TextDisplay sign;

        Game(int index, World world) {
            this.index = index;
            this.world = world;
            applyArena();
        }

        void applyArena() {
            radius = arenaOf(arenaId).radius();
            voidY = floorY - voidDepth;
        }

        Arena arena() {
            return arenaOf(arenaId);
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
            Location l = new Location(world, 0.5, floorY + 16, radius + 10.5);
            l.setYaw(180);
            l.setPitch(35);
            return l;
        }

        /** Regard tourné vers le centre de la plateforme. */
        Location facingCenter(Location l) {
            Location c = l.clone();
            Vector dir = new Vector(0.5 - l.getX(), 0, 0.5 - l.getZ());
            if (dir.lengthSquared() > 0.01) c.setDirection(dir);
            c.setPitch(0);
            return c;
        }

        /** Case de réapparition la plus éloignée des autres joueurs, parmi quelques tirages. */
        Location respawnSpot(Player self) {
            if (spots.isEmpty()) return facingCenter(new Location(world, 0.5, floorY + 2, 0.5));
            Location best = null;
            double bestD = -1;
            for (int i = 0; i < 12; i++) {
                Location c = spots.get(random.nextInt(spots.size()));
                double d = Double.MAX_VALUE;
                for (Player o : online(alive)) {
                    if (o.equals(self) || !o.getWorld().equals(world)) continue;
                    d = Math.min(d, o.getLocation().distanceSquared(c));
                }
                if (d > bestD) { bestD = d; best = c; }
            }
            return facingCenter(best);
        }

        /** Case de départ : au plus près d'un point du cercle (rayon 60 % de la plateforme). */
        Location startSpot(int i, int n) {
            double a = 2 * Math.PI * i / Math.max(1, n);
            double r = radius * 0.6;
            double tx = 0.5 + Math.cos(a) * r, tz = 0.5 + Math.sin(a) * r;
            Location best = null;
            double bd = Double.MAX_VALUE;
            for (Location s : spots) {
                double d = Math.hypot(s.getX() - tx, s.getZ() - tz);
                if (d < bd) { bd = d; best = s; }
            }
            if (best == null) best = new Location(world, tx, floorY + 2, tz);
            return facingCenter(best);
        }

        // -------------------------------------------------------------- construction de l'arène

        void set(int x, int y, int z, Material m) {
            world.getBlockAt(x, y, z).setType(m, false);
        }

        /**
         * Arène de la prochaine construction : celle imposée, sinon au hasard uniforme parmi « arenas », sans la
         * dernière arène JOUÉE (celle de la partie, sinon celle de data.yml), sauf s'il n'y en a qu'une (2.2.3).
         */
        String pickArena() {
            if (nextArena != null) {
                String a = nextArena;
                nextArena = null;
                return a;
            }
            List<String> pool = new ArrayList<>();
            for (String id : getConfig().getStringList("arenas")) {
                String k = id.toLowerCase();
                if (ARENAS.containsKey(k) && !pool.contains(k)) pool.add(k);
            }
            if (pool.isEmpty()) pool.addAll(ARENAS.keySet());
            String last = lastPlayed != null ? lastPlayed : lastArena;
            if (last != null && pool.size() > 1) pool.remove(last);
            return pool.get(random.nextInt(pool.size()));
        }

        /**
         * Construit tout : vide le volume, pose l'arène tirée au sort (centrée en 0, 0, son y 0 en floor-y),
         * puis la salle d'attente vitrée en lobby-y. Tomber 3 blocs sous le sol (fosse de lave, vide entre les
         * anneaux, vide autour de l'île) = -1 vie : void-y vaut floor-y - void-depth pour toutes les arènes (2.2 : 15 au lieu
         * de 3, pour avoir le temps de lancer la perle) ; la lave compte aussi.
         */
        void build() {
            build(pickArena());
        }

        void build(String id) {
            int F = floorY, L = lobbyY;
            arenaId = id;
            Arena a = arena();
            applyArena();
            int ext = 45;
            for (int cx = (-ext) >> 4; cx <= ext >> 4; cx++)
                for (int cz = (-ext) >> 4; cz <= ext >> 4; cz++) world.getChunkAt(cx, cz).load(true);

            clearBonuses();
            // 1) on vide le volume de l'arène et de la salle d'attente
            for (int x = -ext; x <= ext; x++)
                for (int z = -ext; z <= ext; z++)
                    for (int y = F - Math.max(18, voidDepth + 3); y <= F + 30; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
            for (int x = -9; x <= 9; x++)
                for (int z = -9; z <= 9; z++)
                    for (int y = L - 1; y <= L + 7; y++) set(x, y, z, Material.AIR);

            // 2) l'arène
            try {
                pasteArena(a);
            } catch (java.io.IOException | RuntimeException ex) {
                getLogger().log(java.util.logging.Level.SEVERE, "Arène " + arenaId + " impossible a poser", ex);
            }

            // 3) salle d'attente : vitre au centre, damier, murs de verre bleu, plafond de barrières
            for (int x = -7; x <= 7; x++)
                for (int z = -7; z <= 7; z++) {
                    int d = Math.max(Math.abs(x), Math.abs(z));
                    if (d <= 2) set(x, L, z, Material.GLASS);
                    else if (d == 3) set(x, L, z, Material.SEA_LANTERN);
                    else if (d < 7) set(x, L, z, Math.floorMod(x + z, 2) == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_BLUE_CONCRETE);
                    else {
                        set(x, L, z, Material.SMOOTH_QUARTZ);
                        for (int y = L + 1; y <= L + 4; y++) set(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                    }
                    set(x, L + 5, z, Material.BARRIER);
                }

            computeSpots();
            world.setSpawnLocation(lobbySpawn());
            ensureSign();
            getLogger().info("Arène Pitchout construite (partie " + index + ", " + world.getName() + ") : " + a.name() + " (" + arenaId + "), "
                    + spots.size() + " cases de réapparition, sol en y " + F + ", salle d'attente en y " + L + ".");
        }

        /** Lit l'arène dans le jar et la pose, centrée en 0, 0, son y 0 en floor-y. */
        void pasteArena(Arena a) throws java.io.IOException {
            java.io.InputStream res = getResource("arenas/" + arenaId + ".pom");
            if (res == null) throw new java.io.IOException("arenas/" + arenaId + ".pom absent du jar");
            try (java.io.DataInputStream in = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(res))) {
                byte[] magic = in.readNBytes(4);
                if (!new String(magic, java.nio.charset.StandardCharsets.US_ASCII).equals("POM1")) throw new java.io.IOException("format inconnu");
                int arenaW = in.readShort();
                int arenaH = in.readShort();
                int arenaL = in.readShort();
                int n = in.readShort();
                org.bukkit.block.data.BlockData[] pal = new org.bukkit.block.data.BlockData[n];
                for (int i = 0; i < n; i++) {
                    String st = new String(in.readNBytes(in.readUnsignedShort()), java.nio.charset.StandardCharsets.UTF_8);
                    try {
                        pal[i] = Bukkit.createBlockData(st);
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("Bloc invalide ignore : " + st);
                    }
                }
                int placed = 0;
                for (int y = 0; y < arenaH; y++)
                    for (int z = 0; z < arenaL; z++)
                        for (int x = 0; x < arenaW; x++) {
                            int idx = in.readUnsignedShort();
                            if (idx == 0xFFFF || idx >= n || pal[idx] == null) continue;
                            world.getBlockAt(a.ox() + x - a.cx(), floorY + a.oy() + y, a.oz() + z - a.cz()).setBlockData(pal[idx], false);
                            placed++;
                        }
                getLogger().info("Arène " + a.name() + " posée : " + placed + " blocs.");
            }
        }

        /**
         * Cases de réapparition : dans le rayon de jeu, sol plein entre floor-y - 1 et floor-y + 2 (pas de
         * lave, de barrière ni de bloc de lumière), trois blocs libres au-dessus, cases voisines pleines
         * (pas au bord du vide).
         */
        void computeSpots() {
            spots.clear();
            int F = floorY, R = radius;
            for (int x = -R; x <= R; x++)
                for (int z = -R; z <= R; z++) {
                    if (Math.hypot(x, z) > R) continue;
                    for (int y = F + 2; y >= F - 1; y--) {
                        Block b = world.getBlockAt(x, y, z);
                        if (!solidFloor(b)) continue;
                        boolean free = b.getRelative(0, 1, 0).getType().isAir() && b.getRelative(0, 2, 0).getType().isAir()
                                && b.getRelative(0, 3, 0).getType().isAir();
                        boolean inner = solidFloor(b.getRelative(1, 0, 0)) && solidFloor(b.getRelative(-1, 0, 0))
                                && solidFloor(b.getRelative(0, 0, 1)) && solidFloor(b.getRelative(0, 0, -1));
                        Location spot = new Location(world, x + 0.5, y + 1, z + 0.5);
                        if (free && inner && !inMiddle(this, spot)) spots.add(spot);
                        break;
                    }
                }
        }

        /** Panneau flottant de la salle d'attente (non sauvegardé : recréé s'il a disparu). */
        void ensureSign() {
            if (world == null || (sign != null && sign.isValid())) return;
            Location l = new Location(world, 0.5, lobbyY + 2.8, -5.5);
            if (!l.isChunkLoaded()) return;
            sign = world.spawn(l, TextDisplay.class, td -> {
                tag(td);
                td.text(Component.text("PITCHOUT", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                        .append(Component.newline())
                        .append(Component.text("Pousse les autres dans le vide !", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text(cfg("lives", 5) + " vies, aucun dégât, chacun pour soi.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Ramasse les bonus qui flottent sur l'arène.", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
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
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
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
                        pearls();
                        superSticks();
                        bonusSecond();
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

        /** Toutes les 5 ticks : un joueur du monde qui tombe hors partie revient à sa place. */
        void safety() {
            for (Player p : world.getPlayers()) {
                if (alive.contains(p.getUniqueId()) && state == State.PLAYING) continue;
                if (p.getGameMode() == GameMode.CREATIVE) continue;
                double y = p.getLocation().getY();
                boolean lost = p.getGameMode() == GameMode.SPECTATOR ? y < floorY - 40 : y < voidY;
                boolean lobbyFall = !running() && players.contains(p.getUniqueId()) && y < lobbyY - 3 && y > floorY + 20;
                if (!lost && !lobbyFall) continue;
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                if (!players.contains(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) p.teleport(lobbySpawn());
                else if (players.contains(p.getUniqueId()) && !running()) p.teleport(lobbySpawn());
                else if (state == State.ENDING && inGame.contains(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) p.teleport(respawnSpot(p));
                else p.teleport(spectatorSpot());
            }
        }

        /** La fosse de lave du désert compte comme le vide (la zone de mort est plus bas que la fosse). */
        boolean inLava(Player p) {
            Location l = p.getLocation();
            return l.getBlock().getType() == Material.LAVA || l.clone().add(0, 0.5, 0).getBlock().getType() == Material.LAVA;
        }

        void gameTick() {
            if (freezeTicks > 0) {
                freezeTicks--;
                if (freezeTicks % 20 == 0) {
                    int s = freezeTicks / 20;
                    for (Player p : online(inGame)) {
                        Component t = s > 0 ? Component.text(String.valueOf(s), NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                                : Component.text("GO !", NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
                        p.showTitle(Title.title(t, s > 0 ? Component.text("Prepare-toi...", NamedTextColor.GRAY) : Component.text("Pousse-les dans le vide !", NamedTextColor.YELLOW),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                        p.playSound(p, s > 0 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_ENDER_DRAGON_GROWL, s > 0 ? 1f : 0.5f, s > 0 ? 1f : 1.4f);
                    }
                }
                return;
            }
            // chute dans le vide
            for (Player p : online(alive))
                if (p.getWorld().equals(world) && (p.getLocation().getY() < voidY || inLava(p))) {
                    loseLife(p);
                    if (state != State.PLAYING) return;
                }
            bonusTick();
            // petite aura des joueurs protégés
            if (ticks % 4 == 0)
                for (Player p : online(alive))
                    if (isProtected(p)) world.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
        }

        // -------------------------------------------------------------- vies

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

        /** Tombé dans le vide : -1 vie, kill au dernier frappeur, réapparition ou élimination. */
        void loseLife(Player p) {
            UUID id = p.getUniqueId();
            if (!alive.contains(id)) return;
            Player killer = killerOf(p);
            lastHit.remove(id);
            lastHitTime.remove(id);
            removePearlsOf(p);
            int left = Math.max(0, livesOf(id) - 1);
            lives.put(id, left);
            if (killer != null && !killer.equals(p)) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " " + GAME_ID + " kill");
                killer.playSound(killer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                broadcast("§c" + p.getName() + " §7a été poussé dans le vide par §a" + killer.getName()
                        + " §8(" + (left > 0 ? left + " vie" + (left > 1 ? "s" : "") : "elimine") + ")");
            } else {
                broadcast("§c" + p.getName() + " §7est tombé dans le vide §8(" + (left > 0 ? left + " vie" + (left > 1 ? "s" : "") : "elimine") + ")");
            }
            if (left <= 0) {
                eliminate(p);
                return;
            }
            // réapparition sur la plateforme, protégé quelques secondes (seulement s'il est encore dans le monde de la partie)
            if (!p.getWorld().equals(world)) return;
            p.setFallDistance(0);
            p.setVelocity(new Vector());
            p.teleport(respawnSpot(p));
            p.setFireTicks(0);
            p.setArrowsInBody(0);
            superUntil.remove(id);
            giveKit(p);
            protectedUntil.put(id, System.currentTimeMillis() + cfg("respawn-protection", 3) * 1000L);
            p.showTitle(Title.title(Component.text("-1 vie", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    hearts(left, cfg("lives", 5)), Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            p.playSound(p, Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
            reportStatus();
        }

        void eliminate(Player p) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            outOrder.add(id);
            superUntil.remove(id);
            protectedUntil.remove(id);
            if (p.getWorld().equals(world)) {
                for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
                p.getInventory().clear();
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(spectatorSpot());
            }
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

        /** Fin du temps : le ou les joueurs avec le plus de vies gagnent. */
        void timeUp() {
            int best = 0;
            for (UUID id : alive) best = Math.max(best, livesOf(id));
            List<UUID> w = new ArrayList<>();
            for (UUID id : alive) if (livesOf(id) == best) w.add(id);
            broadcast("§eTemps écoulé ! " + (w.size() > 1 ? "Égalité entre les joueurs à " : "Victoire au joueur à ") + "§6" + best + " vie" + (best > 1 ? "s" : "") + "§e.");
            endGame(w);
        }

        void removePearlsOf(Player p) {
            if (world == null) return;
            for (EnderPearl e : world.getEntitiesByClass(EnderPearl.class))
                if (p.equals(e.getShooter())) e.remove();
        }

        // -------------------------------------------------------------- kit

        void giveKit(Player p) {
            if (!p.getWorld().equals(world)) return;
            p.getInventory().clear();
            boolean sup = superUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
            p.getInventory().setItem(0, stick(sup ? cfg("super-stick-knockback", 6) : cfg("stick-knockback", 3)));
            p.getInventory().setItem(1, bow());
            int pm = Math.max(0, cfg("pearl-max", 1));
            if (pm > 0) p.getInventory().setItem(2, pearl(pm));
            p.getInventory().setItem(9, kit(new ItemStack(Material.ARROW)));
            p.getInventory().setHeldItemSlot(0);
            pearlTimer.put(p.getUniqueId(), 0);
        }

        /** Une perle rendue toutes les pearl-cooldown secondes tant qu'on en a moins de pearl-max. */
        void pearls() {
            int max = cfg("pearl-max", 1), cd = Math.max(1, cfg("pearl-cooldown", 15));
            for (Player p : online(alive)) {
                UUID id = p.getUniqueId();
                if (pearlCount(p) >= max) { pearlTimer.put(id, 0); continue; }
                int t = pearlTimer.getOrDefault(id, 0) + 1;
                if (t >= cd) {
                    addPearl(p);
                    p.playSound(p, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
                    t = 0;
                }
                pearlTimer.put(id, t);
            }
        }

        /** Fin du bâton surpuissant : retour au bâton normal. */
        void superSticks() {
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<UUID, Long>> itr = superUntil.entrySet().iterator(); itr.hasNext(); ) {
                Map.Entry<UUID, Long> e = itr.next();
                if (e.getValue() > now) continue;
                itr.remove();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null || !alive.contains(e.getKey())) continue;
                replaceStick(p, cfg("stick-knockback", 3));
                p.sendMessage(PREFIX + "§7Ton bâton redevient normal.");
            }
        }

        // -------------------------------------------------------------- bonus

        /** Chaque seconde : vieillissement des bonus et apparition d'un nouveau toutes les bonus-interval secondes. */
        void bonusSecond() {
            int life = cfg("bonus-lifetime", 30);
            for (Iterator<BonusDrop> itr = bonuses.iterator(); itr.hasNext(); ) {
                BonusDrop b = itr.next();
                if (++b.age >= life || !b.item.isValid()) {
                    removeDrop(b);
                    itr.remove();
                }
            }
            if (++bonusTimer >= Math.max(3, cfg("bonus-interval", 20))) {
                bonusTimer = 0;
                if (bonuses.size() < cfg("bonus-max", 3)) spawnBonus();
            }
        }

        void spawnBonus() {
            if (spots.isEmpty()) return;
            Location base = null;
            for (int i = 0; i < 10 && base == null; i++) {
                Location c = spots.get(random.nextInt(spots.size()));
                boolean free = true;
                for (BonusDrop b : bonuses) if (b.base.distanceSquared(c) < 25) free = false;
                if (free) base = c.clone();
            }
            if (base == null) return;
            Bonus type = randomBonus();
            Location itemLoc = base.clone().add(0, 0.8, 0);
            ItemDisplay item = world.spawn(itemLoc, ItemDisplay.class, d -> {
                tag(d);
                d.setItemStack(new ItemStack(bonusIcon(type)));
                d.setBillboard(Display.Billboard.FIXED);
                d.setTeleportDuration(2);
                d.setGlowing(true);
                d.setPersistent(false);
            });
            TextDisplay label = world.spawn(base.clone().add(0, 1.9, 0), TextDisplay.class, d -> {
                tag(d);
                d.text(Component.text(bonusName(type), bonusColor(type)).decorate(TextDecoration.BOLD));
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
            });
            bonuses.add(new BonusDrop(type, base, item, label));
            world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
            world.spawnParticle(Particle.FIREWORK, itemLoc, 20, 0.3, 0.3, 0.3, 0.05);
            broadcast("§7Un bonus §f" + bonusName(type) + " §7est apparu sur l'arène !");
        }

        /** Chaque tick : l'objet tourne et flotte ; un joueur en vie qui le touche le ramasse. */
        void bonusTick() {
            for (Iterator<BonusDrop> itr = bonuses.iterator(); itr.hasNext(); ) {
                BonusDrop b = itr.next();
                if (ticks % 2 == 0 && b.item.isValid()) {
                    Location l = b.base.clone().add(0, 0.8 + Math.sin(ticks / 8.0) * 0.15, 0);
                    l.setYaw((ticks * 6) % 360 - 180);
                    b.item.teleport(l);
                }
                if (ticks % 10 == 0) world.spawnParticle(Particle.END_ROD, b.base.clone().add(0, 0.8, 0), 1, 0.25, 0.25, 0.25, 0.01);
                for (Player p : online(alive)) {
                    Location pl = p.getLocation();
                    if (!pl.getWorld().equals(world)) continue;
                    if (Math.abs(pl.getX() - b.base.getX()) < 1.1 && Math.abs(pl.getZ() - b.base.getZ()) < 1.1
                            && pl.getY() > b.base.getY() - 1.2 && pl.getY() < b.base.getY() + 1.8) {
                        removeDrop(b);
                        itr.remove();
                        giveBonus(p, b.type);
                        break;
                    }
                }
            }
        }

        void giveBonus(Player p, Bonus b) {
            if (!p.getWorld().equals(world)) return;
            int dur = cfg("bonus-effect-time", 10) * 20;
            switch (b) {
                case SPEED -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 1, false, true, true));
                case JUMP -> p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur, 1, false, true, true));
                case SUPER_STICK -> {
                    superUntil.put(p.getUniqueId(), System.currentTimeMillis() + dur * 50L);
                    replaceStick(p, cfg("super-stick-knockback", 6));
                }
                case PEARL -> addPearl(p);
                case LIFE -> {
                    int max = Math.max(cfg("lives", 5), cfg("max-lives", 7));
                    int v = livesOf(p.getUniqueId());
                    if (v < max) lives.put(p.getUniqueId(), v + 1);
                }
            }
            p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            p.showTitle(Title.title(Component.empty(), Component.text(bonusName(b), bonusColor(b)).decorate(TextDecoration.BOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            world.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.05);
            broadcast("§b" + p.getName() + " §7a ramassé le bonus §f" + bonusName(b) + "§7.");
        }

        void clearBonuses() {
            for (BonusDrop b : bonuses) removeDrop(b);
            bonuses.clear();
            // restes d'une session précédente (non sauvegardés, mais au cas où)
            if (world != null)
                for (Entity e : world.getEntities())
                    if ((e instanceof ItemDisplay || e instanceof TextDisplay) && isOurs(e) && e != sign) e.remove();
        }

        // -------------------------------------------------------------- partie

        boolean running() {
            return state == State.PLAYING || state == State.ENDING;
        }

        void startGame() {
            List<Player> list = online(players);
            if (list.isEmpty() || world == null) { state = State.WAITING; return; }
            if (spots.isEmpty()) computeSpots();
            alive.clear();
            inGame.clear();
            lives.clear();
            kills.clear();
            lastHit.clear();
            lastHitTime.clear();
            protectedUntil.clear();
            superUntil.clear();
            pearlTimer.clear();
            outOrder.clear();
            clearBonuses();
            markPlayed(this);
            state = State.PLAYING;
            total = list.size();
            gameLeft = Math.max(30, cfg("game-time", 600));
            freezeTicks = Math.max(0, cfg("freeze-time", 3)) * 20 + 1;
            bonusTimer = 0;
            world.setTime(cfg("time", 6000));
            int n = list.size(), i = 0;
            int startLives = Math.max(1, cfg("lives", 5));
            for (Player p : list) {
                UUID id = p.getUniqueId();
                alive.add(id);
                inGame.add(id);
                lives.put(id, startLives);
                kills.put(id, 0);
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(startSpot(i++, n));
                giveKit(p);
                p.showTitle(Title.title(Component.text("PITCHOUT", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                        Component.text("Pousse les autres dans le vide !", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
            reportStatus();
            broadcast("§7Arène : §e" + arena().name());
            broadcast("§eLa partie commence avec §6" + total + " §ejoueurs ! §7" + startLives + " vies chacun, le dernier en vie gagne.");
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
            clearBonuses();
            superUntil.clear();
            List<Player> win = online(new LinkedHashSet<>(winners));
            StringBuilder names = new StringBuilder();
            for (Player w : win) names.append(names.isEmpty() ? "" : ", ").append(w.getName());
            Component title = win.isEmpty()
                    ? Component.text("Fin de la partie", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    : Component.text(win.size() == 1 ? names.toString() : "Égalité !", win.size() == 1 ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD); // titre court : pseudo seul
            for (Player p : online(players)) {
                boolean won = win.contains(p);
                p.showTitle(Title.title(title, Component.text(win.size() > 1 ? tieSubtitle(win) : won ? "Tu as gagné le Pitchout !" : win.size() == 1 ? "gagne le Pitchout !" : "", NamedTextColor.YELLOW)));
                p.playSound(p, won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            if (win.isEmpty()) broadcast("§7La partie se termine sans vainqueur.");
            else broadcast("§6" + names + " §eremporte" + (win.size() > 1 ? "nt" : "") + " le Pitchout !");
            // classement des kills
            List<UUID> byKills = new ArrayList<>(inGame);
            byKills.sort((a, b) -> Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0)));
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(3, byKills.size()); i++) {
                Player k = Bukkit.getPlayer(byKills.get(i));
                String nm = k != null ? k.getName() : "?";
                top.append(i == 0 ? "" : "§7, ").append("§f").append(nm).append(" §8(").append(kills.getOrDefault(byKills.get(i), 0)).append(")");
            }
            if (!byKills.isEmpty()) broadcast("§7Kills : " + top);
            for (Player w : win) {
                if (w.getGameMode() == GameMode.SPECTATOR && w.getWorld().equals(world)) {
                    w.setGameMode(GameMode.ADVENTURE);
                    w.teleport(respawnSpot(w));
                }
                for (int i = 0; i < 6; i++)
                    Bukkit.getScheduler().runTaskLater(EemiPitchout.this, () -> {
                        if (w.isOnline() && state == State.ENDING && !destroyed && w.getWorld().equals(world)) firework(w.getLocation());
                    }, i * 20L);
            }
            alive.clear();
            reportStatus();
        }

        /**
         * Après la fin : les inscrits reviennent dans la salle d'attente, les spectateurs au hub, puis
         * l'arène suivante est tirée au sort. Pareil pour une copie (elle redevient une partie en
         * attente) ; une copie vide est détruite par la boucle (pas de rebuild inutile).
         */
        void backToLobby() {
            state = State.WAITING;
            alive.clear();
            inGame.clear();
            lives.clear();
            superUntil.clear();
            protectedUntil.clear();
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
            // arène suivante (tirée au sort) pendant que les joueurs sont dans la salle d'attente
            Bukkit.getScheduler().runTaskLater(EemiPitchout.this, () -> {
                if (world != null && !destroyed && state == State.WAITING) build();
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
            broadcast("§a" + p.getName() + " §7a rejoint le Pitchout §8(partie " + index + ") §7(§f" + players.size() + "/" + cfg("max-players", 16) + "§7).");
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
                p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter Pitchout"));
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
            superUntil.remove(id);
            protectedUntil.remove(id);
            pearlTimer.remove(id);
            boards.remove(id);
            removePearlsOf(p);
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
            if (!alive.contains(id)) {
                p.sendActionBar(Component.text("Spectateur - " + alive.size() + " joueur" + (alive.size() > 1 ? "s" : "") + " en vie", NamedTextColor.GRAY));
                return;
            }
            Component c = Component.text("Vies : ", NamedTextColor.GRAY).append(hearts(livesOf(id), cfg("lives", 5)));
            int max = cfg("pearl-max", 1);
            if (max > 0) {
                int pc = pearlCount(p);
                c = c.append(Component.text("  |  Perle : ", NamedTextColor.GRAY));
                if (pc >= max) c = c.append(Component.text("prete", NamedTextColor.GREEN));
                else c = c.append(Component.text((Math.max(1, cfg("pearl-cooldown", 15)) - pearlTimer.getOrDefault(id, 0)) + " s", NamedTextColor.YELLOW));
            }
            long sup = superUntil.getOrDefault(id, 0L) - System.currentTimeMillis();
            if (sup > 0) c = c.append(Component.text("  |  Bâton surpuissant " + (sup / 1000 + 1) + " s", NamedTextColor.GOLD));
            p.sendActionBar(c);
        }

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("po", Criteria.DUMMY,
                        Component.text("PITCHOUT", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("po");
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
                l.add(Component.text("Vies : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("lives", 5)), NamedTextColor.RED)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.AQUA)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else {
                    l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
                    l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
                }
            } else {
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(gameLeft), NamedTextColor.WHITE)));
                l.add(Component.text("En vie : ", NamedTextColor.GRAY).append(Component.text(alive.size() + "/" + total, NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                List<UUID> order = new ArrayList<>(inGame);
                order.sort((a, b) -> {
                    int c = Integer.compare(livesOf(b), livesOf(a));
                    return c != 0 ? c : Integer.compare(kills.getOrDefault(b, 0), kills.getOrDefault(a, 0));
                });
                int shown = 0;
                for (UUID id : order) {
                    if (shown >= 8) break;
                    Player o = Bukkit.getPlayer(id);
                    String name = o != null ? o.getName() : "?";
                    boolean me = id.equals(p.getUniqueId());
                    int lv = livesOf(id);
                    Component line = Component.text(name + " ", me ? NamedTextColor.YELLOW : lv > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY);
                    if (lv > 0) line = line.append(Component.text(HEART.repeat(Math.min(lv, 7)), NamedTextColor.RED));
                    else line = line.append(Component.text("✖", NamedTextColor.DARK_RED));
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

    private ItemStack stick(int level) {
        ItemStack it = new ItemStack(Material.STICK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(level > cfg("stick-knockback", 3) ? "Bâton surpuissant" : "Bâton de recul",
                level > cfg("stick-knockback", 3) ? NamedTextColor.GOLD : NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.addEnchant(Enchantment.KNOCKBACK, level, true);
        m.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack bow() {
        ItemStack it = new ItemStack(Material.BOW);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Arc de recul", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.addEnchant(Enchantment.PUNCH, cfg("bow-punch", 2), true);
        m.addEnchant(Enchantment.INFINITY, 1, true);
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack pearl(int n) {
        ItemStack it = kit(new ItemStack(Material.ENDER_PEARL, Math.max(1, n)));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Perle de l'Ender", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(m);
        return it;
    }

    private int pearlCount(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.getType() == Material.ENDER_PEARL) n += it.getAmount();
        return n;
    }

    private void addPearl(Player p) {
        if (!mayTouch(p)) return;
        ItemStack slot = p.getInventory().getItem(2);
        if (slot != null && slot.getType() == Material.ENDER_PEARL) slot.setAmount(slot.getAmount() + 1);
        else if (slot == null || slot.getType() == Material.AIR) p.getInventory().setItem(2, pearl(1));
        else p.getInventory().addItem(pearl(1));
    }

    private void replaceStick(Player p, int level) {
        if (!mayTouch(p)) return;
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++)
            if (c[i] != null && c[i].getType() == Material.STICK && isKit(c[i])) {
                p.getInventory().setItem(i, stick(level));
                return;
            }
        p.getInventory().addItem(stick(level));
    }

    private Bonus randomBonus() {
        Map<Bonus, Integer> w = new LinkedHashMap<>();
        ConfigurationSection sec = getConfig().getConfigurationSection("bonus-weights");
        int sum = 0;
        for (Bonus b : Bonus.values()) {
            int def = switch (b) {
                case SPEED, JUMP -> 30;
                case SUPER_STICK -> 20;
                case PEARL -> 15;
                case LIFE -> 5;
            };
            int v = Math.max(0, sec == null ? def : sec.getInt(b.name(), def));
            w.put(b, v);
            sum += v;
        }
        if (sum <= 0) return Bonus.SPEED;
        int k = random.nextInt(sum);
        for (Map.Entry<Bonus, Integer> e : w.entrySet()) {
            k -= e.getValue();
            if (k < 0) return e.getKey();
        }
        return Bonus.SPEED;
    }

    private static Material bonusIcon(Bonus b) {
        return switch (b) {
            case SPEED -> Material.SUGAR;
            case JUMP -> Material.RABBIT_FOOT;
            case SUPER_STICK -> Material.BLAZE_ROD;
            case PEARL -> Material.ENDER_PEARL;
            case LIFE -> Material.GOLDEN_APPLE;
        };
    }

    private static String bonusName(Bonus b) {
        return switch (b) {
            case SPEED -> "Vitesse";
            case JUMP -> "Super saut";
            case SUPER_STICK -> "Bâton surpuissant";
            case PEARL -> "Perle supplémentaire";
            case LIFE -> "+1 vie";
        };
    }

    private static NamedTextColor bonusColor(Bonus b) {
        return switch (b) {
            case SPEED -> NamedTextColor.AQUA;
            case JUMP -> NamedTextColor.GREEN;
            case SUPER_STICK -> NamedTextColor.GOLD;
            case PEARL -> NamedTextColor.DARK_AQUA;
            case LIFE -> NamedTextColor.RED;
        };
    }

    private <T extends Entity> T tag(T e) {
        e.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
        return e;
    }

    private boolean isOurs(Entity e) {
        return e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE);
    }

    private static void removeDrop(BonusDrop b) {
        if (b.item.isValid()) b.item.remove();
        if (b.label.isValid()) b.label.remove();
    }

    private static boolean solidFloor(Block b) {
        Material m = b.getType();
        return m.isSolid() && m != Material.BARRIER && m != Material.LIGHT && m != Material.LAVA;
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class, f -> {
            tag(f);
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                    .withColor(Color.AQUA, Color.WHITE).withFade(Color.BLUE).flicker(true).build());
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
     * /pitchout join : la partie en attente (ou en décompte, ou en préparation) qui a de la place, la plus
     * remplie d'abord ; sinon une nouvelle copie si on est sous max-instances ; sinon « pleines ».
     * Les membres d'un groupe rejoignent l'un après l'autre : la plus remplie est celle du premier.
     */
    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (pendingOf(p) != null) { p.sendMessage(PREFIX + "§7Ta partie est en préparation, patiente un instant."); return; }
        if (games.isEmpty()) { p.sendMessage(PREFIX + "§cLe monde de Pitchout n'est pas chargé."); return; }
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
        if (target.index == 1 && !target.preparing && target.state == State.WAITING && !modelBusy
                && target.players.isEmpty() && target.pending.isEmpty()) {
            // 2.2.3 : premier joueur dans la partie 1 vide en attente = nouveau tirage, arène posée AVANT d'y entrer
            String pick = target.pickArena();
            if (!pick.equals(target.arenaId)) target.build(pick);
        }
        if (target.preparing) {
            target.pending.add(id);
            p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
            reportStatus();
            return;
        }
        target.addPlayer(p);
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

    // ------------------------------------------------------------------ affichage (commun)

    /** « ❤❤❤ » rouges pour les vies restantes, gris pour les vies perdues (sur la base de départ). */
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
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemipitchout.admin");
    }

    private Player attackerOf(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Player pl) return pl;
        if (d instanceof Projectile proj && proj.getShooter() instanceof Player pl) return pl;
        return null;
    }

    /**
     * Aucun dégât : un coup (bâton, poing, flèche) d'un joueur en vie sur un joueur en vie de la même partie
     * garde son recul mais à 0 dégât ; tout le reste (chute, perle, vide...) est annulé. Joueur protégé après
     * une réapparition ou pendant le départ : coup annulé (donc sans recul).
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
                && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK || e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE)) {
            Player attacker = attackerOf(by);
            if (attacker == null || g.isProtected(victim) || (attacker != victim && !g.alive.contains(attacker.getUniqueId()))) {
                e.setCancelled(true);
                return;
            }
            if (by.getDamager() instanceof EnderPearl) {
                e.setCancelled(true);
                return;
            }
            if (attacker != victim) {
                g.lastHit.put(id, attacker.getUniqueId());
                g.lastHitTime.put(id, System.currentTimeMillis());
            }
            e.setCancelled(false);
            e.setDamage(0);
            // au cas où un modificateur laisserait passer quelque chose
            Bukkit.getScheduler().runTask(this, () -> {
                if (!victim.isOnline() || victim.isDead()) return;
                AttributeInstance hp = victim.getAttribute(Attribute.MAX_HEALTH);
                victim.setHealth(hp == null ? 20 : hp.getValue());
            });
            return;
        }
        e.setCancelled(true);
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

    /** Pas de mobs naturels ni d'endermites des perles. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (!isGameWorld(e.getLocation().getWorld())) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.NATURAL || r == CreatureSpawnEvent.SpawnReason.ENDER_PEARL) e.setCancelled(true);
    }

    /** Nos propres entités (bonus, panneau, feux d'artifice) apparaissent même si un autre plugin les bloque. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (e.isCancelled() && isOurs(e.getEntity())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // le décor (lanternes, fleurs...) ne se règle pas ; les objets du kit restent utilisables
        if (isGameWorld(p.getWorld()) && !admin(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
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
            p.sendMessage(PREFIX + "§7Tu as quitté le Pitchout.");
        }
    }

    /** Pas de tir ni de perle pendant le départ ou hors partie. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p) || !isGameWorld(p.getWorld()) || admin(p)) return;
        Game g = gameAt(p.getWorld());
        if (g == null || g.state != State.PLAYING || g.freezeTicks > 0 || !g.alive.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isGameWorld(p.getWorld())) return;
        if (e.getProjectile() instanceof AbstractArrow a) a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    /** Les flèches disparaissent à l'impact. */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (isGameWorld(e.getEntity().getWorld()) && e.getEntity() instanceof AbstractArrow a)
            Bukkit.getScheduler().runTask(this, a::remove);
    }

    /** Une perle d'un joueur éliminé ou sorti ne le téléporte plus. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL || !isGameWorld(e.getPlayer().getWorld())) return;
        Game g = gameAt(e.getPlayer().getWorld());
        if (g == null || !g.alive.contains(e.getPlayer().getUniqueId()) || g.state != State.PLAYING) { e.setCancelled(true); return; }
        if (inMiddle(g, e.getTo())) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            p.getInventory().addItem(pearl(1));
            p.sendActionBar(Component.text("Le centre de l'arène est interdit.", NamedTextColor.RED));
        }
    }

    /** Départ : immobiles pendant le décompte (on peut tourner la tête). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null && g.state == State.PLAYING && g.alive.contains(e.getPlayer().getUniqueId())
                && e.getPlayer().getWorld().equals(g.world) && inMiddle(g, e.getTo())) {
            // repoussé vers l'extérieur (et vers le bas) : on ne campe plus en haut du centre
            Player p = e.getPlayer();
            Location t = e.getTo();
            double len = Math.max(0.01, Math.hypot(t.getX(), t.getZ()));
            double ox = len < 0.3 ? 1 : t.getX() / len, oz = len < 0.3 ? 0 : t.getZ() / len;
            p.setVelocity(new org.bukkit.util.Vector(ox * 0.9, Math.min(p.getVelocity().getY(), 0.2), oz * 0.9));
            if (ticks % 10 == 0) p.sendActionBar(Component.text("Le centre de l'arène est interdit.", NamedTextColor.RED));
            if (inMiddle(g, e.getFrom())) return;
            Location back = e.getFrom().clone();
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            e.setTo(back);
            return;
        }
        if (g == null || g.state != State.PLAYING || g.freezeTicks <= 0 || !g.alive.contains(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (f.getX() != t.getX() || f.getZ() != t.getZ() || t.getY() > f.getY()) {
            Location back = f.clone();
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            e.setTo(back);
        }
    }

    /** Sécurité (/kill...) : pas de drops, compte comme une chute. */
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
            e.setRespawnLocation(g.respawnSpot(p));
            later(() -> {
                if (!p.isOnline() || !g.alive.contains(p.getUniqueId()) || !p.getWorld().equals(g.world)) return;
                p.setGameMode(GameMode.ADVENTURE);
                // la mort compte comme une chute (vie perdue, kill au dernier frappeur)
                g.loseLife(p);
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

    /** Quitter le monde de SA partie = quitter la partie (aller d'un monde de jeu à un autre aussi). */
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
        boolean admin = sender.hasPermission("eemipitchout.admin");
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
                p.sendMessage(PREFIX + "§7Tu as quitté le Pitchout.");
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
                            + " §8| §7arène §f" + g.arena().name() + " §8| §7joueurs §f" + g.players.size()
                            + (g.pending.isEmpty() ? "" : " §8(+" + g.pending.size() + " en route)")
                            + (g.running() ? " §8| §7en vie §f" + g.alive.size() : "")
                            + (g.spectators.isEmpty() ? "" : " §8| §7spectateurs §f" + g.spectators.size()));
                sender.sendMessage("§8- §7modèle §f" + modelName() + " §7" + (model != null ? "prêt (" + arenaOf(modelArenaId).name() + ")" : modelBusy ? "en préparation" : "absent"));
            }
            case "map", "arene" -> {
                if (!admin) return true;
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + "§7Arène de la partie 1 : §e" + (g1 == null ? "?" : g1.arena().name()) + " §7- arènes : §f" + String.join(", ", ARENAS.keySet()));
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!ARENAS.containsKey(id)) { sender.sendMessage(PREFIX + "§cArène inconnue. §7" + String.join(", ", ARENAS.keySet())); return true; }
                if (g1 == null || g1.world == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g1.running()) { sender.sendMessage(PREFIX + "§cImpossible : la partie 1 est en cours."); return true; }
                g1.nextArena = id;
                g1.build();
                for (Player pl : online(g1.players)) g1.toWaiting(pl);
                makeModel();
                sender.sendMessage(PREFIX + "§aArène de la partie 1 construite : §e" + ARENAS.get(id).name() + " §7(modèle refait)");
            }
            case "build" -> {
                if (!admin) return true;
                if (g1 == null || g1.world == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g1.running()) { sender.sendMessage(PREFIX + "§cImpossible : la partie 1 est en cours."); return true; }
                loadSettings();
                g1.build();
                for (Player pl : online(g1.players)) g1.toWaiting(pl);
                makeModel();
                sender.sendMessage(PREFIX + "§aArène de la partie 1 reconstruite §7(modèle refait).");
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
                for (Game g : games.values()) {
                    if (g.world == null) continue;
                    prepareRules(g.world);
                    if (!g.running()) g.applyArena();
                }
                sender.sendMessage(PREFIX + "§aConfig rechargée. §7(/pitchout build si le rayon ou la hauteur a changé)");
            }
            default -> sender.sendMessage(PREFIX + "§7/pitchout <join|leave|spectate [joueur]>" + (admin ? " §8| admin : start [n], stop [n], parties, map, build, reload" : ""));
        }
        return true;
    }

    /** Regarder une partie en cours en spectateur : celle d'un joueur, sinon la plus remplie. */
    private void spectate(Player p, String targetName) {
        if (gameOf(p) != null || pendingOf(p) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/pitchout leave §cd'abord."); return; }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : gameOf(t);
            if (g == null) { p.sendMessage(PREFIX + "§cCe joueur n'est dans aucune partie de Pitchout."); return; }
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
