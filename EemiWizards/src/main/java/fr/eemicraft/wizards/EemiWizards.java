package fr.eemicraft.wizards;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
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
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
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
import java.util.function.Consumer;

/**
 * TNT Wizards d'EEMICRAFT, d'après les TNT Games d'Hypixel.
 * Rouge contre Bleu sur une île de sorciers construite par le plugin. Chaque joueur choisit une classe
 * (6 baguettes) : clic droit = sort principal (mana, 100 max, se régénère, affiché sur la barre d'XP),
 * clic gauche = sort secondaire (temps de recharge). 4 ou 5 points de capture selon la map : un point tenu rapporte des
 * points à son équipe chaque seconde, un kill aussi. Première équipe à 1000 points, sinon le plus de
 * points après 15 min (égalité possible). Mort = 5 s en spectateur puis retour à la base.
 *
 * Plusieurs parties en même temps (max-instances, 3 par défaut) : partie 1 = monde « tntwizards »,
 * partie n = copie « tntwizards_n » du modèle « tntwizards_modele » (copie propre de la partie 1, refaite
 * au démarrage et après /tntwizards build). Une copie est détruite dès qu'elle est vide.
 */
public final class EemiWizards extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§6TNT Wizards§8] §7";
    private static final String GAME_ID = "tntwizards";

    enum State { WAITING, STARTING, PLAYING, ENDING }

    /** Menu de choix de la classe. */
    private static final class ClassMenu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int[] MENU_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    // ------------------------------------------------------------------ état global (pas d'état de partie ici)

    private NamespacedKey kitKey, wandKey, menuKey, entityKey, versionKey;
    /** Map imposée par un admin pour la prochaine partie (null = au hasard). */
    private String forcedMap;
    private String base = "tntwizards";
    private int floorY, lobbyY;
    private final Map<Integer, Game> games = new TreeMap<>();
    private World model;
    private boolean modelBusy;
    private int modelGen;
    private long ticks;
    private String lastStatus;
    private long lastStatusAt;
    private final Random random = new Random();
    /** Classe choisie par joueur (gardée dans classes.yml). */
    private final Map<UUID, Wizard> classes = new HashMap<>();
    private File classesFile;
    /** Dégâts de sort en cours d'application (l'écouteur de dégâts les laisse passer). */
    private boolean spellDamage;
    private Player spellAttacker;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        wandKey = new NamespacedKey(this, "wand");
        menuKey = new NamespacedKey(this, "menu");
        entityKey = new NamespacedKey(this, "entity");
        versionKey = new NamespacedKey(this, "map_version");
        saveDefaultConfig();
        loadSettings();
        loadClasses();
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
            if (g1 == null || g1.world == null) return;
            Integer v = g1.world.getPersistentDataContainer().get(versionKey, PersistentDataType.INTEGER);
            if (v == null || v != Arena.VERSION) g1.build();
            else {
                g1.clearOurEntities();
                g1.resetMap();
                getLogger().info("Map TNT Wizards déjà construite (version " + v + ").");
            }
            makeModel();
        }, 20L);
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) {
            g.bolts.clear();
            g.hideAllBars();
            for (Player p : online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            }
            for (Player p : online(g.spectators)) if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            g.players.clear();
            g.spectators.clear();
            g.pending.clear();
            g.clearOurEntities();
        }
        saveClasses();
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "tntwizards");
        floorY = cfg("floor-y", 100);
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
        if (w == null) getLogger().severe("Impossible de charger ou créer le monde " + base + " : TNT Wizards désactivé.");
        return w;
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 141, Arena.LOBBY_Z + 0.5);
        }
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private double cfgd(String key, double def) {
        return getConfig().getDouble(key, def);
    }

    /** Valeur d'un sort : classes.<id>.<clé> du config, sinon le défaut du code. */
    private double spell(Wizard w, String key, double def) {
        return getConfig().getDouble("classes." + w.id + "." + key, def);
    }

    // ------------------------------------------------------------------ classes (fichier)

    private void loadClasses() {
        classesFile = new File(getDataFolder(), "classes.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(classesFile);
        for (String k : y.getKeys(false)) {
            try {
                Wizard w = Wizard.byId(y.getString(k));
                if (w != null) classes.put(UUID.fromString(k), w);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveClasses() {
        if (classesFile == null) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Wizard> e : classes.entrySet()) y.set(e.getKey().toString(), e.getValue().id);
        try {
            y.save(classesFile);
        } catch (IOException ex) {
            getLogger().warning("Sauvegarde de classes.yml impossible : " + ex.getMessage());
        }
    }

    private Wizard classOf(Player p) {
        Wizard w = classes.get(p.getUniqueId());
        if (w != null) return w;
        Wizard d = Wizard.byId(getConfig().getString("default-class", "kinetique"));
        return d != null ? d : Wizard.KINETIQUE;
    }

    private void setClass(Player p, Wizard w) {
        classes.put(p.getUniqueId(), w);
        saveClasses();
        p.sendMessage(PREFIX + "§7Classe choisie : " + legacy(w.color) + w.label + " §7(gardée pour les prochaines parties).");
        p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        Game g = gameOf(p);
        if (g != null && !g.running() && p.getWorld().equals(g.world)) {
            p.getInventory().setItem(0, selector(w));
            g.updateSidebar(p);
        } else if (g != null && g.state == State.PLAYING && g.isActive(p.getUniqueId()) && g.teams.containsKey(p.getUniqueId())
                && g.inBase(p.getLocation(), g.teams.get(p.getUniqueId()))) {
            // en partie, dans sa base : la nouvelle baguette tout de suite
            g.wizards.put(p.getUniqueId(), w);
            g.secondaryReady.remove(p.getUniqueId());
            g.auraUntil.remove(p.getUniqueId());
            g.giveKit(p);
            g.updateSidebar(p);
            p.sendMessage(PREFIX + "§7Nouvelle baguette en main.");
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
            g.makeArenas();
            g.prepareWorld();
            g.clearOurEntities();
            g.resetMap();
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
        g.bolts.clear();
        g.hideAllBars();
        g.clearOurEntities();
        for (Player p : online(g.players)) g.leave(p, true);
        for (Player p : online(g.spectators)) sendToHub(p);
        g.spectators.clear();
        if (g.world != null) WorldClone.destroy(this, g.world, base, this::sendToHub);
        getLogger().info("Partie " + g.index + " terminée : copie " + WorldClone.name(base, g.index) + " supprimée.");
        reportStatus();
    }

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
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, getConfig().getBoolean("natural-regen", false));
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

    /** Un point de capture d'une partie : contrôle de -100 (Rouge) à +100 (Bleu). */
    final class CapPoint {
        final Arena.PointDef def;
        double control;
        Side owner;
        int red, blue;
        TextDisplay label;

        /** Décalage en z de la map du point. */
        final int oz;

        CapPoint(Arena.PointDef def, int oz) {
            this.def = def;
            this.oz = oz;
        }

        int top() {
            return floorY - 1 + def.h();
        }

        Location center(World w) {
            return new Location(w, def.x() + 0.5, top() + 1, def.z() + oz + 0.5);
        }

        boolean contains(Location l) {
            double r = cfgd("capture-radius", 4.5);
            double dx = l.getX() - (def.x() + 0.5), dz = l.getZ() - (def.z() + oz + 0.5);
            return dx * dx + dz * dz <= r * r && l.getY() >= top() - 0.5 && l.getY() <= top() + 7;
        }

        Component status() {
            if (red > 0 && blue > 0 && red == blue) return Component.text("Conteste", NamedTextColor.YELLOW);
            int pct = (int) Math.round(Math.abs(control));
            if (owner != null && pct >= 100) return Component.text(owner.label, owner.color);
            if (pct == 0) return Component.text("Neutre", NamedTextColor.WHITE);
            Side s = control > 0 ? Side.BLEU : Side.ROUGE;
            return Component.text((owner == null ? s.label + " " : owner.label + " ") + pct + "%", (owner != null ? owner : s).color);
        }
    }

    /** Projectile magique simulé par le plugin (pas d'entité) : avance chaque tick, éclate à l'impact. */
    /** Nuage toxique posé au sol (Toxique). */
    static final class Zone {
        final UUID caster;
        final Side side;
        final Location center;
        final double radius;
        final long until;

        Zone(UUID caster, Side side, Location center, double radius, long until) {
            this.caster = caster;
            this.side = side;
            this.center = center;
            this.radius = radius;
            this.until = until;
        }
    }

    static final class Bolt {
        final UUID caster;
        final Side side;
        final Wizard type;
        final Location pos;
        final Vector dir;
        final double speed;
        double left;

        Bolt(UUID caster, Side side, Wizard type, Location pos, Vector dir, double speed, double range) {
            this.caster = caster;
            this.side = side;
            this.type = type;
            this.pos = pos;
            this.dir = dir;
            this.speed = speed;
            this.left = range;
        }
    }

    final class Game {
        final int index;
        World world;
        Arena arena;
        final List<Arena> arenas = new ArrayList<>();
        String lastMap;
        boolean preparing, destroyed;

        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> pending = new LinkedHashSet<>();
        final Set<UUID> spectators = new LinkedHashSet<>();
        /** Équipe de chaque joueur de la partie en cours (fixée au départ). */
        final Map<UUID, Side> teams = new HashMap<>();
        /** Classe de chaque joueur pour la partie en cours. */
        final Map<UUID, Wizard> wizards = new HashMap<>();
        /** Morts en attente de réapparition : ticks restants. */
        final Map<UUID, Integer> dead = new HashMap<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        final Map<UUID, Long> protectedUntil = new HashMap<>();
        final Map<UUID, Double> mana = new HashMap<>();
        final Map<UUID, Long> primaryReady = new HashMap<>();
        final Map<UUID, Long> secondaryReady = new HashMap<>();
        final Map<UUID, Long> auraUntil = new HashMap<>();
        final List<Zone> zones = new ArrayList<>();
        /** Dernier combat (coup donné ou reçu) de chaque joueur, pour la régénération hors combat. */
        final Map<UUID, Long> lastCombat = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        final Map<UUID, BossBar> bars = new HashMap<>();
        final List<CapPoint> points = new ArrayList<>();
        final List<Bolt> bolts = new ArrayList<>();
        final int[] score = new int[2];

        State state = State.WAITING;
        int timer, gameLeft, gateTicks, total;
        TextDisplay sign;

        Game(int index, World world) {
            this.index = index;
            this.world = world;
            if (world != null) makeArenas();
            else for (Arena.PointDef d : Arena.LAYOUTS.get(0).points()) points.add(new CapPoint(d, 0));
        }

        /** Une arène par map (toutes construites dans le monde, décalées en z) ; la première par défaut. */
        void makeArenas() {
            arenas.clear();
            for (Arena.Layout l : Arena.LAYOUTS) arenas.add(new Arena(world, floorY, lobbyY, getLogger(), l));
            useArena(arenas.get(0));
        }

        void useArena(Arena a) {
            arena = a;
            for (CapPoint cp : points) if (cp.label != null) cp.label.remove();
            points.clear();
            for (Arena.PointDef d : a.lay.points()) points.add(new CapPoint(d, a.lay.oz()));
        }

        /** Map de la prochaine partie : celle imposée par un admin, sinon au hasard (pas deux fois de suite). */
        void chooseMap() {
            List<Arena> pool = new ArrayList<>();
            List<String> allowed = getConfig().getStringList("maps");
            for (Arena a : arenas) if (allowed.isEmpty() || allowed.contains(a.lay.id())) pool.add(a);
            if (pool.isEmpty()) pool.addAll(arenas);
            Arena pick = null;
            if (forcedMap != null) for (Arena a : arenas) if (a.lay.id().equals(forcedMap)) pick = a;
            forcedMap = null;
            if (pick == null) {
                if (pool.size() > 1) pool.removeIf(a -> a.lay.id().equals(lastMap));
                pick = pool.get(random.nextInt(pool.size()));
            }
            lastMap = pick.lay.id();
            if (pick != arena) useArena(pick);
        }

        void prepareWorld() {
            prepareRules(world);
            world.setSpawnLocation(lobbySpawn());
        }

        // -------------------------------------------------------------- positions

        Location lobbySpawn() {
            Location l = new Location(world, 0.5, lobbyY + 1, Arena.LOBBY_Z + 0.5);
            l.setYaw(180);
            return l;
        }

        Location spectatorSpot() {
            Location l = new Location(world, 0.5, floorY + 20, arena.lay.oz() + arena.lay.wallZ() + 2.5);
            l.setYaw(180);
            l.setPitch(35);
            return l;
        }

        /** Place de départ / réapparition dans la cour de la base (cases |x| 33..35, z -3..3), tourné vers le centre. */
        Location spawnSpot(Side s) {
            int bx = s.sign * (arena.lay.baseMid() - 1 + random.nextInt(3));
            int bz = -3 + random.nextInt(7);
            Location l = new Location(world, bx + 0.5 + (random.nextDouble() - 0.5) * 0.6, floorY, arena.lay.oz() + bz + 0.5);
            l.setYaw(s == Side.ROUGE ? -90 : 90);
            return l;
        }

        /** Dans la cour de la base de s (cases |x| 31..37, |z| <= 6, mêmes cases des deux côtés). */
        boolean inBase(Location l, Side s) {
            Arena.Layout lay = arena.lay;
            int bx = l.getBlockX() * s.sign, bz = l.getBlockZ() - lay.oz();
            return bx >= lay.baseFront() + 1 && bx <= lay.baseBack() - 1 && Math.abs(bz) <= lay.baseHalfZ() - 1
                    && l.getY() < floorY + 6;
        }

        /** Zone interdite à l'équipe adverse : la cour et l'embrasure de la porte (jusqu'au-dessus des remparts). */
        boolean inBaseZone(Location l, Side s) {
            Arena.Layout lay = arena.lay;
            double bx = l.getX() * s.sign, bz = l.getZ() - lay.oz() - 0.5;
            return bx >= lay.baseFront() - 0.3 && bx <= lay.baseBack() + 1 && Math.abs(bz) <= lay.baseHalfZ() + 0.5
                    && l.getY() < floorY + 8;
        }

        /** Barrière des bases : un ennemi qui entre est renvoyé devant la porte (toutes les 2 ticks). */
        void guardBases() {
            if (!getConfig().getBoolean("base-barrier", true)) return;
            for (Player p : online(teams.keySet())) {
                Side s = teams.get(p.getUniqueId());
                if (s == null || !isActive(p.getUniqueId()) || !p.getWorld().equals(world)) continue;
                Side enemy = s.other();
                if (!inBaseZone(p.getLocation(), enemy)) continue;
                Location out = new Location(world, enemy.sign * (arena.lay.baseFront() - 1.5),
                        floorY, arena.lay.oz() + Math.max(-arena.lay.gateHalf(), Math.min(arena.lay.gateHalf(), p.getLocation().getBlockZ() - arena.lay.oz())) + 0.5,
                        p.getLocation().getYaw(), p.getLocation().getPitch());
                p.teleport(out);
                p.setVelocity(new Vector(-enemy.sign * 0.6, 0.25, 0));
                p.setFallDistance(0);
                p.sendActionBar(Component.text("Base ennemie : une barrière magique te repousse !", NamedTextColor.RED));
                p.playSound(p, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.4f);
                world.spawnParticle(Particle.WITCH, out.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0);
            }
        }

        // -------------------------------------------------------------- map

        /** Construit toute la map (partie 1 ou build admin). */
        void build() {
            clearOurEntities();
            long t0 = System.currentTimeMillis();
            int n = 0;
            for (int i = 0; i < arenas.size(); i++) n += arenas.get(i).build(i == 0);
            world.getPersistentDataContainer().set(versionKey, PersistentDataType.INTEGER, Arena.VERSION);
            world.setSpawnLocation(lobbySpawn());
            resetMap();
            getLogger().info("Maps TNT Wizards construites (partie " + index + ", " + world.getName() + ", " + arenas.size() + " maps) : " + n + " blocs en "
                    + (System.currentTimeMillis() - t0) + " ms, sol en y " + floorY + ", salle d'attente en y " + lobbyY + ".");
        }

        /** Remise à zéro : portes des bases fermées, points neutres. Aucun sort ne casse de bloc. */
        void resetMap() {
            if (world == null) return;
            bolts.clear();
            arena.gate(-1, true);
            arena.gate(1, true);
            for (CapPoint cp : points) {
                cp.control = 0;
                cp.owner = null;
                cp.red = cp.blue = 0;
                arena.paintPoint(cp.def, null);
                updateLabel(cp);
            }
            score[0] = score[1] = 0;
        }

        void clearOurEntities() {
            if (world == null) return;
            for (Entity e : world.getEntities()) if (isOurs(e)) e.remove();
            for (CapPoint cp : points) cp.label = null;
            sign = null;
        }

        void ensureLabels() {
            for (CapPoint cp : points) {
                if (cp.label != null && cp.label.isValid()) continue;
                Location l = cp.center(world).add(0, 3.6, 0);
                if (!l.isChunkLoaded()) continue;
                cp.label = world.spawn(l, TextDisplay.class, td -> {
                    tag(td);
                    td.setBillboard(Display.Billboard.CENTER);
                    td.setPersistent(false);
                    td.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                });
                updateLabel(cp);
            }
        }

        void updateLabel(CapPoint cp) {
            if (cp.label == null || !cp.label.isValid()) return;
            NamedTextColor c = cp.owner == null ? NamedTextColor.WHITE : cp.owner.color;
            cp.label.text(Component.text(cp.def.key() + " - " + cp.def.name(), c).decorate(TextDecoration.BOLD)
                    .append(Component.newline()).append(cp.status().decoration(TextDecoration.BOLD, false)));
        }

        void ensureSign() {
            if (world == null || (sign != null && sign.isValid())) return;
            Location l = new Location(world, 0.5, lobbyY + 3, Arena.LOBBY_Z - 5.5);
            if (!l.isChunkLoaded()) return;
            sign = world.spawn(l, TextDisplay.class, td -> {
                tag(td);
                td.text(Component.text("TNT WIZARDS", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
                        .append(Component.newline())
                        .append(Component.text("Rouge contre Bleu, à coups de sorts !", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Clic droit : sort principal (mana)", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Clic gauche : sort secondaire (recharge)", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Tiens les points de capture : " + cfg("points-to-win", 1000) + " points pour gagner.", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
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
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> {
                    if (gateTicks <= 0) {
                        gameLeft--;
                        scorePoints();
                        if (state != State.PLAYING) break;
                        auras();
                        zones();
                        baseDamage();
                        combatRegen();
                        if (state != State.PLAYING) break;
                        if (gameLeft == 300 || gameLeft == 60 || gameLeft == 30 || gameLeft == 10)
                            broadcast("§eFin de la partie dans §6" + clock(gameLeft) + "§e !");
                        if (gameLeft <= 0) timeUp();
                    }
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (destroyed) return;
            if (!players.isEmpty()) {
                ensureSign();
                ensureLabels();
            }
            for (CapPoint cp : points) updateLabel(cp);
            if (state == State.PLAYING) {
                colorNames();
                pointParticles();
            }
            for (Player p : online(players)) updateSidebar(p);
        }

        void safety() {
            for (Player p : world.getPlayers()) {
                if (p.getGameMode() == GameMode.CREATIVE) continue;
                UUID id = p.getUniqueId();
                double y = p.getLocation().getY();
                if (isActive(id)) {
                    if (y < floorY - 25) kill(p, killerOf(p));
                    continue;
                }
                boolean lost = p.getGameMode() == GameMode.SPECTATOR ? y < floorY - 45 : y < floorY - 25;
                boolean lobbyFall = !running() && players.contains(id) && y < lobbyY - 3;
                if (!lost && !lobbyFall) continue;
                p.setFallDistance(0);
                p.setVelocity(new Vector());
                if (players.contains(id) && !running()) p.teleport(lobbySpawn());
                else if (!players.contains(id) && p.getGameMode() != GameMode.SPECTATOR) p.teleport(lobbySpawn());
                else p.teleport(spectatorSpot());
            }
        }

        void gameTick() {
            // portes fermées les premières secondes
            if (gateTicks > 0) {
                gateTicks--;
                if (gateTicks % 20 == 0) {
                    int s = gateTicks / 20;
                    for (Player p : online(teams.keySet())) {
                        Component t = s > 0 ? Component.text(String.valueOf(s), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
                                : Component.text("GO !", NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
                        p.showTitle(Title.title(t, s > 0 ? Component.text("Les portes vont s'ouvrir...", NamedTextColor.GRAY) : Component.text("Capture les points !", NamedTextColor.YELLOW),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                        p.playSound(p, s > 0 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_ENDER_DRAGON_GROWL, s > 0 ? 1f : 0.5f, s > 0 ? 1f : 1.4f);
                    }
                    if (s == 0) {
                        arena.gate(-1, false);
                        arena.gate(1, false);
                        world.playSound(new Location(world, 0, floorY, 0), Sound.BLOCK_BEACON_ACTIVATE, 2f, 1f);
                    }
                }
                manaTick();
                return;
            }
            tickBolts();
            if (state != State.PLAYING) return;
            if (ticks % 2 == 0) guardBases();
            manaTick();
            // réapparitions
            for (Map.Entry<UUID, Integer> e : new ArrayList<>(dead.entrySet())) {
                if (!dead.containsKey(e.getKey())) continue;
                int left = e.getValue() - 1;
                Player p = Bukkit.getPlayer(e.getKey());
                if (left <= 0) {
                    dead.remove(e.getKey());
                    if (p != null) respawn(p);
                    if (state != State.PLAYING) return;
                } else {
                    dead.put(e.getKey(), left);
                    if (p != null && left % 20 == 0)
                        p.sendActionBar(Component.text("Réapparition dans " + (left / 20) + " s", NamedTextColor.GRAY));
                }
            }
            if (ticks % 10 == 0) capture(0.5);
            if (ticks % 5 == 0) for (Player p : online(teams.keySet())) if (isActive(p.getUniqueId())) actionBar(p);
        }

        // -------------------------------------------------------------- joueurs

        boolean isActive(UUID id) {
            return state == State.PLAYING && teams.containsKey(id) && !dead.containsKey(id) && players.contains(id);
        }

        boolean isEnemy(Side s, Player o) {
            Side os = teams.get(o.getUniqueId());
            return os != null && os != s && isActive(o.getUniqueId()) && o.getWorld().equals(world) && o.getGameMode() != GameMode.SPECTATOR;
        }

        boolean isProtected(Player p) {
            return protectedUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
        }

        List<Player> enemiesOf(Side s) {
            List<Player> l = new ArrayList<>();
            for (Player p : online(teams.keySet())) if (isEnemy(s, p)) l.add(p);
            return l;
        }

        List<Player> alliesOf(Side s) {
            List<Player> l = new ArrayList<>();
            for (Player p : online(teams.keySet()))
                if (teams.get(p.getUniqueId()) == s && isActive(p.getUniqueId()) && p.getWorld().equals(world)) l.add(p);
            return l;
        }

        Player killerOf(Player p) {
            Long t = lastHitTime.get(p.getUniqueId());
            if (t == null || System.currentTimeMillis() - t > cfg("kill-credit-time", 10) * 1000L) return null;
            Player k = Bukkit.getPlayer(lastHit.get(p.getUniqueId()));
            return k != null && teams.containsKey(k.getUniqueId()) && players.contains(k.getUniqueId()) ? k : null;
        }

        void markHit(Player victim, Player attacker) {
            markCombat(victim);
            if (attacker == null || attacker.equals(victim)) return;
            markCombat(attacker);
            lastHit.put(victim.getUniqueId(), attacker.getUniqueId());
            lastHitTime.put(victim.getUniqueId(), System.currentTimeMillis());
        }

        /**
         * Dégâts d'un sort (calculés par le plugin) : jamais sur un coéquipier, un joueur protégé ou mort.
         * Coup mortel = mort gérée par le plugin (pas d'écran de mort). Rend true si la cible a été touchée.
         */
        boolean hurt(Player victim, Player attacker, double amount) {
            UUID id = victim.getUniqueId();
            if (!isActive(id) || isProtected(victim) || amount <= 0 || !victim.getWorld().equals(world)) return false;
            if (attacker != null && teams.get(attacker.getUniqueId()) == teams.get(id)) return false;
            markHit(victim, attacker);
            if (amount >= victim.getHealth() - 0.01) {
                kill(victim, attacker != null ? attacker : killerOf(victim));
                return true;
            }
            victim.setNoDamageTicks(0);
            DamageSource.Builder b = DamageSource.builder(DamageType.MAGIC);
            if (attacker != null) b = b.withCausingEntity(attacker).withDirectEntity(attacker);
            spellDamage = true;
            spellAttacker = attacker;
            try {
                victim.damage(amount, b.build());
            } finally {
                spellDamage = false;
                spellAttacker = null;
            }
            return true;
        }

        /** Coup donné ou reçu : la régénération hors combat s'arrête et le délai repart. */
        void markCombat(Player p) {
            lastCombat.put(p.getUniqueId(), System.currentTimeMillis());
            PotionEffect r = p.getPotionEffect(PotionEffectType.REGENERATION);
            // seulement la nôtre (ambiante, courte) : la Régénération du soin de zone reste
            if (r != null && r.isAmbient() && r.getDuration() <= 40) p.removePotionEffect(PotionEffectType.REGENERATION);
        }

        /** Chaque seconde : Régénération aux joueurs sans combat depuis combat-regen-delay secondes. */
        void combatRegen() {
            double delay = cfgd("combat-regen-delay", 4);
            int level = cfg("combat-regen-level", 2);
            if (delay < 0 || level <= 0) return;
            long now = System.currentTimeMillis();
            for (Player p : online(teams.keySet())) {
                UUID id = p.getUniqueId();
                if (!isActive(id) || !p.getWorld().equals(world)) continue;
                if (now - lastCombat.getOrDefault(id, 0L) < delay * 1000) continue;
                AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
                if (p.getHealth() >= (hp == null ? 20 : hp.getValue())) continue;
                PotionEffect cur = p.getPotionEffect(PotionEffectType.REGENERATION);
                if (cur != null && !cur.isAmbient()) continue;
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, level - 1, true, false, true));
            }
        }

        void heal(Player p, double amount) {
            if (!p.getWorld().equals(world)) return;
            AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
            double max = hp == null ? 20 : hp.getValue();
            p.setHealth(Math.min(max, p.getHealth() + amount));
        }

        /** Mort : points et kill à l'équipe adverse, 5 s en spectateur sur place puis retour à la base. */
        void kill(Player victim, Player killer) {
            UUID id = victim.getUniqueId();
            if (!isActive(id)) return;
            Side vs = teams.get(id);
            lastHit.remove(id);
            lastHitTime.remove(id);
            auraUntil.remove(id);
            int delay = Math.max(1, cfg("respawn-delay", 5));
            dead.put(id, delay * 20 + 1);
            if (killer != null && !killer.equals(victim) && teams.get(killer.getUniqueId()) == vs.other()) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                score[vs.other().ordinal()] += cfg("kill-points", 5);
                eemi("reward " + killer.getName() + " " + GAME_ID + " kill");
                killer.playSound(killer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                broadcast(legacy(vs.color) + victim.getName() + " §7a été tué par " + legacy(vs.other().color) + killer.getName()
                        + " §8(+" + cfg("kill-points", 5) + " pts)");
            } else {
                broadcast(legacy(vs.color) + victim.getName() + " §7est mort.");
            }
            hideBar(victim);
            if (victim.getWorld().equals(world)) {
                victim.setFireTicks(0);
                for (PotionEffect e : victim.getActivePotionEffects()) victim.removePotionEffect(e.getType());
                AttributeInstance hp = victim.getAttribute(Attribute.MAX_HEALTH);
                victim.setHealth(hp == null ? 20 : hp.getValue());
                victim.setVelocity(new Vector());
                victim.setGameMode(GameMode.SPECTATOR);
                if (victim.getLocation().getY() < floorY - 20) victim.teleport(spectatorSpot());
                world.spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.03);
            }
            victim.showTitle(Title.title(Component.text("Mort", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text("Réapparition dans " + delay + " s", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(300))));
            victim.playSound(victim, Sound.ENTITY_PLAYER_HURT, 1f, 0.7f);
            checkScore();
        }

        void respawn(Player p) {
            UUID id = p.getUniqueId();
            Side s = teams.get(id);
            if (s == null || !players.contains(id) || !p.getWorld().equals(world)) return;
            p.teleport(spawnSpot(s));
            resetPlayer(p, GameMode.ADVENTURE);
            giveKit(p);
            mana.put(id, maxMana());
            protectedUntil.put(id, System.currentTimeMillis() + cfg("respawn-protection", 2) * 1000L);
            p.playSound(p, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.2f);
        }

        double maxMana() {
            return Math.max(1, cfgd("mana-max", 100));
        }

        void manaTick() {
            double max = maxMana(), regen = cfgd("mana-regen", 9) / 20.0;
            for (Player p : online(teams.keySet())) {
                UUID id = p.getUniqueId();
                if (!p.getWorld().equals(world) || dead.containsKey(id)) continue;
                double m = Math.min(max, mana.getOrDefault(id, max) + regen);
                mana.put(id, m);
                if (ticks % 2 == 0) {
                    p.setLevel((int) m);
                    p.setExp((float) Math.max(0, Math.min(0.999, m / max)));
                }
            }
        }

        // -------------------------------------------------------------- points de capture

        void capture(double frac) {
            double speed = cfgd("capture-speed", 10), extra = cfgd("capture-speed-extra", 5), decay = cfgd("capture-decay", 4);
            for (CapPoint cp : points) {
                int r = 0, b = 0;
                for (Player p : online(teams.keySet())) {
                    if (!isActive(p.getUniqueId()) || !p.getWorld().equals(world) || p.getGameMode() == GameMode.SPECTATOR) continue;
                    if (!cp.contains(p.getLocation())) continue;
                    if (teams.get(p.getUniqueId()) == Side.ROUGE) r++;
                    else b++;
                }
                cp.red = r;
                cp.blue = b;
                int diff = b - r;
                if (r > 0 || b > 0) {
                    if (diff != 0) cp.control += Math.signum(diff) * (speed + extra * (Math.abs(diff) - 1)) * frac;
                } else {
                    double target = cp.owner == null ? 0 : cp.owner.sign * 100;
                    double d = target - cp.control;
                    cp.control += Math.signum(d) * Math.min(Math.abs(d), decay * frac);
                }
                cp.control = Math.max(-100, Math.min(100, cp.control));
                if (cp.control >= 100 && cp.owner != Side.BLEU) captured(cp, Side.BLEU);
                else if (cp.control <= -100 && cp.owner != Side.ROUGE) captured(cp, Side.ROUGE);
                else if (cp.owner == Side.BLEU && cp.control <= 0) neutralized(cp);
                else if (cp.owner == Side.ROUGE && cp.control >= 0) neutralized(cp);
            }
            // barre de boss pour les joueurs sur un point
            for (Player p : online(teams.keySet())) {
                if (!isActive(p.getUniqueId()) || !p.getWorld().equals(world)) {
                    hideBar(p);
                    continue;
                }
                CapPoint in = null;
                for (CapPoint cp : points) if (cp.contains(p.getLocation())) in = cp;
                if (in == null) hideBar(p);
                else showBar(p, in);
            }
        }

        void captured(CapPoint cp, Side s) {
            cp.owner = s;
            cp.control = s.sign * 100;
            arena.paintPoint(cp.def, s);
            updateLabel(cp);
            broadcast(legacy(s.color) + "Les " + s.plural + " §7ont capturé §f" + cp.def.key() + " - " + cp.def.name() + "§7 !");
            for (Player p : online(teams.keySet()))
                p.playSound(p, teams.get(p.getUniqueId()) == s ? Sound.BLOCK_BEACON_ACTIVATE : Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
            world.spawnParticle(Particle.FIREWORK, cp.center(world).add(0, 1, 0), 40, 2, 1, 2, 0.05);
        }

        void neutralized(CapPoint cp) {
            Side lost = cp.owner;
            cp.owner = null;
            arena.paintPoint(cp.def, null);
            updateLabel(cp);
            if (lost != null)
                broadcast("§f" + cp.def.key() + " - " + cp.def.name() + " §7n'appartient plus aux " + legacy(lost.color) + lost.plural + "§7.");
            for (Player p : online(teams.keySet())) p.playSound(p, Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 0.8f);
        }

        /** Chaque seconde : chaque point tenu rapporte des points à son équipe. */
        void scorePoints() {
            int per = cfg("capture-points-per-second", 1);
            for (CapPoint cp : points) if (cp.owner != null) score[cp.owner.ordinal()] += per;
            checkScore();
        }

        void checkScore() {
            if (state != State.PLAYING) return;
            int win = cfg("points-to-win", 1000);
            boolean r = score[Side.ROUGE.ordinal()] >= win, b = score[Side.BLEU.ordinal()] >= win;
            if (r && b) endGame(score[0] == score[1] ? null : score[0] > score[1] ? Side.ROUGE : Side.BLEU, true);
            else if (r) endGame(Side.ROUGE, true);
            else if (b) endGame(Side.BLEU, true);
        }

        void timeUp() {
            int r = score[Side.ROUGE.ordinal()], b = score[Side.BLEU.ordinal()];
            broadcast("§eTemps écoulé !");
            endGame(r == b ? null : r > b ? Side.ROUGE : Side.BLEU, true);
        }

        /** Équipe vidée par les départs : l'autre gagne. */
        void checkTeams() {
            if (state != State.PLAYING) return;
            int r = 0, b = 0;
            for (UUID id : teams.keySet()) {
                if (!players.contains(id) || Bukkit.getPlayer(id) == null) continue;
                if (teams.get(id) == Side.ROUGE) r++;
                else b++;
            }
            if (r == 0 && b == 0) endGame(null, false);
            else if (total >= 2 && r == 0) endGame(Side.BLEU, true);
            else if (total >= 2 && b == 0) endGame(Side.ROUGE, true);
        }

        void pointParticles() {
            for (CapPoint cp : points) {
                Color c = cp.owner == null ? Color.WHITE : cp.owner.armor;
                Particle.DustOptions dust = new Particle.DustOptions(c, 1.4f);
                double y = cp.top() + 1.15;
                double r = cfgd("capture-radius", 4.5);
                for (int i = 0; i < 24; i++) {
                    double a = 2 * Math.PI * i / 24;
                    world.spawnParticle(Particle.DUST, cp.def.x() + 0.5 + Math.cos(a) * r, y, cp.def.z() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0, dust);
                }
            }
        }

        void showBar(Player p, CapPoint cp) {
            BossBar bar = bars.get(p.getUniqueId());
            float prog = (float) Math.max(0, Math.min(1, Math.abs(cp.control) / 100.0));
            BossBar.Color color = cp.control > 0.5 ? Side.BLEU.bar : cp.control < -0.5 ? Side.ROUGE.bar : BossBar.Color.WHITE;
            Component name = Component.text(cp.def.key() + " - " + cp.def.name() + " : ", NamedTextColor.WHITE).append(cp.status());
            if (bar == null) {
                bar = BossBar.bossBar(name, prog, color, BossBar.Overlay.NOTCHED_10);
                bars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else {
                bar.name(name);
                bar.progress(prog);
                bar.color(color);
            }
        }

        void hideBar(Player p) {
            BossBar bar = bars.remove(p.getUniqueId());
            if (bar != null) p.hideBossBar(bar);
        }

        void hideAllBars() {
            for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) p.hideBossBar(e.getValue());
            }
            bars.clear();
        }

        /** Un ennemi dans la cour d'une base perd de la vie chaque seconde (anti-campement). */
        void baseDamage() {
            double dmg = cfgd("base-damage", 2);
            if (dmg <= 0) return;
            for (Player p : online(teams.keySet())) {
                Side s = teams.get(p.getUniqueId());
                if (!isActive(p.getUniqueId()) || !p.getWorld().equals(world) || !inBase(p.getLocation(), s.other())) continue;
                p.sendActionBar(Component.text("Base ennemie : la magie te brûle !", NamedTextColor.RED));
                world.spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0);
                hurt(p, null, dmg);
                if (state != State.PLAYING) return;
            }
        }

        // -------------------------------------------------------------- sorts

        boolean canCast(Player p) {
            return state == State.PLAYING && gateTicks <= 0 && isActive(p.getUniqueId()) && p.getWorld().equals(world)
                    && p.getGameMode() != GameMode.SPECTATOR;
        }

        Wizard wizardOf(Player p) {
            Wizard w = wizards.get(p.getUniqueId());
            return w != null ? w : classOf(p);
        }

        void castPrimary(Player p) {
            if (!canCast(p)) return;
            UUID id = p.getUniqueId();
            long now = System.currentTimeMillis();
            if (now < primaryReady.getOrDefault(id, 0L)) return;
            Wizard w = wizardOf(p);
            double cost = spell(w, "cost", switch (w) {
                case KINETIQUE -> 30;
                case GLACE -> 40;
                case WITHER -> 20;
                case TEMPETE, TERRE -> 35;
                case ARCANE -> 15;
                default -> 25;
            });
            double m = mana.getOrDefault(id, maxMana());
            if (m < cost) {
                p.sendActionBar(Component.text("Pas assez de mana (" + (int) m + "/" + (int) cost + ")", NamedTextColor.RED));
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                primaryReady.put(id, now + 250);
                return;
            }
            mana.put(id, m - cost);
            primaryReady.put(id, now + (long) (cfgd("primary-delay", 0.5) * 1000));
            Side s = teams.get(id);
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            switch (w) {
                case KINETIQUE -> {
                    bolts.add(new Bolt(id, s, w, eye.clone().add(dir.clone().multiply(0.6)), dir, spell(w, "speed", 2.6), spell(w, "range", 40)));
                    world.playSound(eye, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.4f);
                }
                case FEU -> {
                    bolts.add(new Bolt(id, s, w, eye.clone().add(dir.clone().multiply(0.6)), dir, spell(w, "speed", 2.6), spell(w, "range", 35)));
                    world.playSound(eye, Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);
                }
                case WITHER -> {
                    bolts.add(new Bolt(id, s, w, eye.clone().add(dir.clone().multiply(0.6)), dir, spell(w, "speed", 3.0), spell(w, "range", 40)));
                    world.playSound(eye, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.1f);
                }
                case GLACE -> {
                    Player t = ray(p, s, spell(w, "range", 30), Particle.SNOWFLAKE, null);
                    world.playSound(eye, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
                    if (t != null && hurt(t, p, spell(w, "damage", 3)) && isActive(t.getUniqueId())) {
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (spell(w, "slow-time", 3) * 20),
                                Math.max(0, (int) spell(w, "slow-level", 2) - 1), false, true, true));
                        world.playSound(t.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
                        world.spawnParticle(Particle.SNOWFLAKE, t.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
                    }
                }
                case SANG -> {
                    Player t = ray(p, s, spell(w, "range", 25), Particle.DUST, new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.2f));
                    world.playSound(eye, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 0.8f);
                    if (t != null && hurt(t, p, spell(w, "damage", 4))) {
                        heal(p, spell(w, "lifesteal", 1.5));
                        world.spawnParticle(Particle.DAMAGE_INDICATOR, t.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.1);
                        world.spawnParticle(Particle.HEART, p.getLocation().add(0, 2.1, 0), 2, 0.3, 0.2, 0.3, 0);
                    }
                }
                case TEMPETE -> lightning(p, s, w);
                case TOXIQUE -> {
                    bolts.add(new Bolt(id, s, w, eye.clone().add(dir.clone().multiply(0.6)), dir, spell(w, "speed", 2.4), spell(w, "range", 35)));
                    world.playSound(eye, Sound.ENTITY_SPLASH_POTION_THROW, 1f, 0.8f);
                }
                case TERRE -> {
                    bolts.add(new Bolt(id, s, w, eye.clone().add(dir.clone().multiply(0.6)), dir, spell(w, "speed", 2.0), spell(w, "range", 30)));
                    world.playSound(eye, Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
                }
                case ARCANE -> {
                    bolts.add(new Bolt(id, s, w, eye.clone().add(dir.clone().multiply(0.6)), dir, spell(w, "speed", 3.4), spell(w, "range", 40)));
                    world.playSound(eye, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
                }
                case HYDRO -> {
                    Player t = ray(p, s, spell(w, "range", 25), Particle.SPLASH, null);
                    world.playSound(eye, Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.3f);
                    if (t != null && hurt(t, p, spell(w, "damage", 3)) && isActive(t.getUniqueId())) {
                        push(t, p.getLocation(), spell(w, "knockback", 0.9));
                        t.setFireTicks(0);
                        world.spawnParticle(Particle.SPLASH, t.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.1);
                    }
                }
            }
        }

        void castSecondary(Player p, boolean verbose) {
            if (!canCast(p)) return;
            UUID id = p.getUniqueId();
            long now = System.currentTimeMillis();
            long ready = secondaryReady.getOrDefault(id, 0L);
            if (now < ready) {
                if (verbose) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.5f);
                return;
            }
            Wizard w = wizardOf(p);
            double cd = spell(w, "cooldown", switch (w) {
                case KINETIQUE -> 7;
                case GLACE -> 12;
                case FEU -> 9;
                case WITHER -> 8;
                case SANG -> 15;
                case TEMPETE, HYDRO -> 10;
                case TOXIQUE, TERRE -> 12;
                case ARCANE -> 6;
            });
            secondaryReady.put(id, now + (long) (cd * 1000));
            Side s = teams.get(id);
            Location loc = p.getLocation();
            switch (w) {
                case KINETIQUE -> {
                    Vector v = p.getEyeLocation().getDirection().normalize().multiply(spell(w, "leap-power", 1.5));
                    v.setY(Math.max(0.45, Math.min(1.1, v.getY() + 0.4)));
                    p.setVelocity(v);
                    p.setFallDistance(0);
                    world.spawnParticle(Particle.GUST, loc.clone().add(0, 0.3, 0), 2, 0.2, 0.1, 0.2, 0);
                    world.playSound(loc, Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
                }
                case GLACE -> {
                    double r = spell(w, "wave-radius", 5);
                    for (double rr = 1; rr <= r; rr += 1)
                        for (int i = 0; i < 6 * rr; i++) {
                            double a = 2 * Math.PI * i / (6 * rr);
                            world.spawnParticle(Particle.SNOWFLAKE, loc.getX() + Math.cos(a) * rr, loc.getY() + 0.3, loc.getZ() + Math.sin(a) * rr, 1, 0, 0.1, 0, 0.01);
                        }
                    world.playSound(loc, Sound.BLOCK_POWDER_SNOW_BREAK, 1.2f, 0.7f);
                    world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
                    for (Player t : enemiesOf(s)) {
                        if (t.getLocation().distanceSquared(loc) > r * r) continue;
                        if (hurt(t, p, spell(w, "wave-damage", 2)) && isActive(t.getUniqueId()))
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (spell(w, "wave-slow-time", 3) * 20),
                                    Math.max(0, (int) spell(w, "wave-slow-level", 3) - 1), false, true, true));
                    }
                }
                case FEU -> {
                    Location eye = p.getEyeLocation();
                    Vector dir = eye.getDirection().normalize();
                    double range = spell(w, "jet-range", 7), angle = Math.toRadians(spell(w, "jet-angle", 30));
                    for (double d = 1; d <= range; d += 0.6) {
                        Location l = eye.clone().add(dir.clone().multiply(d));
                        double spread = Math.tan(angle) * d * 0.5;
                        world.spawnParticle(Particle.FLAME, l, 4, spread, spread * 0.6, spread, 0.02);
                    }
                    world.playSound(eye, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
                    world.playSound(eye, Sound.BLOCK_FIRE_AMBIENT, 1f, 1.2f);
                    for (Player t : enemiesOf(s)) {
                        Vector to = t.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
                        double dist = to.length();
                        if (dist > range || dist < 0.01 || to.angle(dir) > angle || !sight(eye, to, dist)) continue;
                        if (hurt(t, p, spell(w, "jet-damage", 3)) && isActive(t.getUniqueId()))
                            t.setFireTicks(Math.max(t.getFireTicks(), (int) (spell(w, "jet-burn-time", 4) * 20)));
                    }
                }
                case WITHER -> {
                    if (getConfig().getString("classes.wither.secondary", "volee").equalsIgnoreCase("aura")) {
                        auraUntil.put(id, now + (long) (spell(w, "aura-time", 5) * 1000));
                        world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.8f, 1.3f);
                        aura(p, w);
                    } else {
                        // volée de crânes en éventail (même crâne que le sort principal)
                        Location eye = p.getEyeLocation();
                        Vector dir = eye.getDirection().normalize();
                        int n = Math.max(1, (int) spell(w, "volley-count", 3));
                        double spread = spell(w, "volley-angle", 12);
                        for (int i = 0; i < n; i++) {
                            double deg = n == 1 ? 0 : -spread + 2 * spread * i / (n - 1);
                            Vector d = dir.clone().rotateAroundY(Math.toRadians(deg)).normalize();
                            bolts.add(new Bolt(id, s, w, eye.clone().add(d.clone().multiply(0.6)), d, spell(w, "speed", 3.0), spell(w, "range", 40)));
                        }
                        world.playSound(eye, Sound.ENTITY_WITHER_SHOOT, 1f, 0.8f);
                    }
                }
                case SANG -> {
                    double r = spell(w, "heal-radius", 6);
                    world.playSound(loc, Sound.ENTITY_WITCH_DRINK, 1f, 1.1f);
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
                    for (int i = 0; i < 32; i++) {
                        double a = 2 * Math.PI * i / 32;
                        world.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * r, loc.getY() + 0.3, loc.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(0xD01030), 1.5f));
                    }
                    for (Player a : alliesOf(s)) {
                        if (a.getLocation().distanceSquared(loc) > r * r) continue;
                        heal(a, spell(w, "heal-amount", 3));
                        int rt = (int) (spell(w, "regen-time", 0) * 20);
                        if (rt > 0) a.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, rt, 0, false, true, true));
                        world.spawnParticle(Particle.HEART, a.getLocation().add(0, 2.1, 0), 3, 0.4, 0.2, 0.4, 0);
                    }
                }
                case TEMPETE -> {
                    double r = spell(w, "gust-radius", 5), power = spell(w, "gust-power", 1.4);
                    world.spawnParticle(Particle.GUST, loc.clone().add(0, 1, 0), 3, 1, 0.3, 1, 0);
                    world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0), 40, r * 0.5, 0.3, r * 0.5, 0.1);
                    world.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.8f);
                    for (Player t : enemiesOf(s)) {
                        Vector away = t.getLocation().toVector().subtract(loc.toVector());
                        if (away.lengthSquared() > r * r) continue;
                        away.setY(0);
                        if (away.lengthSquared() < 0.01) away = p.getLocation().getDirection().setY(0);
                        Vector v = away.normalize().multiply(power).setY(0.55);
                        if (hurt(t, p, spell(w, "gust-damage", 2)) && isActive(t.getUniqueId())) t.setVelocity(v);
                    }
                }
                case TOXIQUE -> {
                    zones.add(new Zone(id, s, loc.clone(), spell(w, "cloud-radius", 4), now + (long) (spell(w, "cloud-time", 5) * 1000)));
                    world.playSound(loc, Sound.BLOCK_BREWING_STAND_BREW, 1.2f, 0.7f);
                    world.playSound(loc, Sound.ENTITY_PUFFER_FISH_BLOW_UP, 1f, 0.8f);
                }
                case HYDRO -> {
                    Location eye = p.getEyeLocation();
                    Vector dir = eye.getDirection().normalize();
                    double range = spell(w, "wave-range", 6), angle = Math.toRadians(spell(w, "wave-angle", 40));
                    for (double d = 1; d <= range; d += 0.5) {
                        Location l = eye.clone().add(dir.clone().multiply(d));
                        double spread = Math.tan(angle) * d * 0.5;
                        world.spawnParticle(Particle.SPLASH, l, 8, spread, spread * 0.5, spread, 0.05);
                        world.spawnParticle(Particle.BUBBLE_POP, l, 3, spread, spread * 0.5, spread, 0.02);
                    }
                    world.playSound(eye, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.8f);
                    for (Player t : enemiesOf(s)) {
                        Vector to = t.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
                        double dist = to.length();
                        if (dist > range || dist < 0.01 || to.angle(dir) > angle || !sight(eye, to, dist)) continue;
                        if (hurt(t, p, spell(w, "wave-damage", 2)) && isActive(t.getUniqueId())) push(t, loc, spell(w, "wave-power", 1.3));
                    }
                    double hr = spell(w, "heal-radius", 5);
                    for (Player a : alliesOf(s)) {
                        if (a.getLocation().distanceSquared(loc) > hr * hr) continue;
                        a.setFireTicks(0);
                        heal(a, spell(w, "heal-amount", 2));
                    }
                }
                case TERRE -> {
                    double r = spell(w, "quake-radius", 6);
                    world.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.2, 0), 120, r * 0.5, 0.1, r * 0.5, 0, Material.DIRT.createBlockData());
                    world.spawnParticle(Particle.EXPLOSION, loc, 2, 1, 0.2, 1, 0);
                    world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.6f);
                    world.playSound(loc, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
                    for (Player t : enemiesOf(s)) {
                        if (t.getLocation().distanceSquared(loc) > r * r || !((Entity) t).isOnGround()) continue;
                        if (hurt(t, p, spell(w, "quake-damage", 3)) && isActive(t.getUniqueId())) {
                            t.setVelocity(t.getVelocity().setY(spell(w, "quake-lift", 0.9)));
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (spell(w, "quake-slow-time", 2) * 20), 1, false, true, true));
                        }
                    }
                }
                case ARCANE -> {
                    Location eye = p.getEyeLocation();
                    Vector dir = eye.getDirection().normalize();
                    double dist = spell(w, "blink-distance", 8);
                    RayTraceResult hit = world.rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
                    if (hit != null) dist = Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 1);
                    Location to = loc.clone().add(dir.clone().multiply(dist));
                    // deux blocs libres à l'arrivée, sinon on recule
                    while (dist > 0.5 && (!to.getBlock().isPassable() || !to.clone().add(0, 1, 0).getBlock().isPassable())) {
                        dist -= 0.5;
                        to = loc.clone().add(dir.clone().multiply(dist));
                    }
                    if (dist <= 0.5) {
                        secondaryReady.put(id, now + 500);
                        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                        return;
                    }
                    world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.3);
                    to.setYaw(loc.getYaw());
                    to.setPitch(loc.getPitch());
                    p.teleport(to);
                    p.setFallDistance(0);
                    world.spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
                    world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
                }
            }
        }

        /** Nuages toxiques : une impulsion par seconde sur les ennemis dedans. */
        void zones() {
            long now = System.currentTimeMillis();
            for (Zone z : new ArrayList<>(zones)) {
                Player caster = Bukkit.getPlayer(z.caster);
                if (z.until < now || caster == null || !teams.containsKey(z.caster)) {
                    zones.remove(z);
                    continue;
                }
                Wizard w = Wizard.TOXIQUE;
                world.spawnParticle(Particle.DUST, z.center.clone().add(0, 0.6, 0), 60, z.radius * 0.5, 0.5, z.radius * 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(0x6CC24A), 1.6f));
                world.spawnParticle(Particle.ITEM_SLIME, z.center.clone().add(0, 0.4, 0), 10, z.radius * 0.4, 0.3, z.radius * 0.4, 0);
                for (Player t : enemiesOf(z.side)) {
                    if (t.getLocation().distanceSquared(z.center) > z.radius * z.radius) continue;
                    if (hurt(t, caster, spell(w, "cloud-damage", 1.5)) && isActive(t.getUniqueId()))
                        t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, true, true));
                    if (state != State.PLAYING) return;
                }
            }
        }

        /** Ligne de vue (aucun bloc plein entre l'œil et la cible). */
        boolean sight(Location eye, Vector to, double dist) {
            RayTraceResult r = world.rayTraceBlocks(eye, to.clone().normalize(), dist, FluidCollisionMode.NEVER, true);
            return r == null;
        }

        /** Rayon instantané : rend l'ennemi touché (ou null) et dessine la ligne. */
        Player ray(Player p, Side s, double range, Particle particle, Object data) {
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            RayTraceResult r = world.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.35,
                    ent -> ent instanceof Player o && isEnemy(s, o));
            Vector end = r == null ? eye.toVector().add(dir.clone().multiply(range)) : r.getHitPosition();
            line(eye.toVector(), end, particle, data);
            return r != null && r.getHitEntity() instanceof Player t ? t : null;
        }

        void line(Vector from, Vector to, Particle particle, Object data) {
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.01) return;
            d.normalize();
            for (double i = 0.8; i <= len; i += 0.35) {
                Vector v = from.clone().add(d.clone().multiply(i));
                if (data != null) world.spawnParticle(particle, v.getX(), v.getY(), v.getZ(), 1, 0, 0, 0, 0, data);
                else world.spawnParticle(particle, v.getX(), v.getY(), v.getZ(), 1, 0, 0, 0, 0);
            }
        }

        /** Tempête : la foudre (effet visuel) tombe sur la cible visée, dégâts calculés autour. */
        void lightning(Player p, Side s, Wizard w) {
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            double range = spell(w, "range", 35);
            RayTraceResult r = world.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.3,
                    ent -> ent instanceof Player o && isEnemy(s, o));
            Location target;
            if (r != null && r.getHitEntity() != null) target = r.getHitEntity().getLocation();
            else {
                Vector end = r == null ? eye.toVector().add(dir.clone().multiply(range)) : r.getHitPosition();
                target = end.toLocation(world);
                RayTraceResult down = world.rayTraceBlocks(target, new Vector(0, -1, 0), 30, FluidCollisionMode.NEVER, true);
                if (down != null) target = down.getHitPosition().toLocation(world);
            }
            world.strikeLightningEffect(target);
            for (double y = 0; y < 8; y += 0.5)
                world.spawnParticle(Particle.ELECTRIC_SPARK, target.getX(), target.getY() + y, target.getZ(), 3, 0.15, 0.1, 0.15, 0.02);
            area(p, s, target, spell(w, "radius", 2.5), spell(w, "damage", 6), null);
        }

        /** Dégâts de zone (moitié au bord du rayon) ; extra est appliqué aux cibles encore en vie. */
        void area(Player caster, Side s, Location c, double radius, double damage, Consumer<Player> extra) {
            for (Player t : enemiesOf(s)) {
                double d = t.getLocation().add(0, 0.9, 0).distance(c);
                if (d > radius) continue;
                double f = 1 - 0.5 * (d / radius);
                if (hurt(t, caster, damage * f) && extra != null && isActive(t.getUniqueId())) extra.accept(t);
            }
        }

        /** Aura de Wither : une impulsion par seconde tant qu'elle dure. */
        void auras() {
            long now = System.currentTimeMillis();
            // copie : une impulsion peut tuer quelqu'un (kill retire son aura) ou finir la partie
            for (Map.Entry<UUID, Long> e : new ArrayList<>(auraUntil.entrySet())) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (e.getValue() < now || p == null || !isActive(e.getKey())) {
                    auraUntil.remove(e.getKey());
                    continue;
                }
                aura(p, Wizard.WITHER);
                if (state != State.PLAYING) return;
            }
        }

        void aura(Player p, Wizard w) {
            double r = spell(w, "aura-radius", 4.5);
            Location loc = p.getLocation();
            for (int i = 0; i < 20; i++) {
                double a = 2 * Math.PI * i / 20;
                world.spawnParticle(Particle.LARGE_SMOKE, loc.getX() + Math.cos(a) * r, loc.getY() + 0.4, loc.getZ() + Math.sin(a) * r, 1, 0, 0.1, 0, 0.01);
            }
            world.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 8, 0.4, 0.5, 0.4, 0.02);
            Side s = teams.get(p.getUniqueId());
            if (s == null) return;
            for (Player t : enemiesOf(s)) {
                if (t.getLocation().distanceSquared(loc) > r * r) continue;
                if (hurt(t, p, spell(w, "aura-damage", 2)) && isActive(t.getUniqueId()))
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0, false, true, true));
            }
        }

        /** Projectiles magiques : avancent par petits pas, éclatent sur un bloc, un ennemi ou au bout de la portée. */
        void tickBolts() {
            // copie : un impact peut finir la partie (la liste est alors vidée)
            for (Bolt b : new ArrayList<>(bolts)) {
                if (!bolts.contains(b)) continue;
                int n = Math.max(1, (int) Math.ceil(b.speed / 0.35));
                double step = b.speed / n;
                boolean done = false;
                for (int i = 0; i < n && !done; i++) {
                    b.pos.add(b.dir.clone().multiply(step));
                    b.left -= step;
                    trail(b);
                    Block blk = world.getBlockAt(b.pos);
                    if (!blk.isPassable()) {
                        impact(b, b.pos.clone().subtract(b.dir.clone().multiply(step)));
                        done = true;
                    } else if (hitsEnemy(b)) {
                        impact(b, b.pos.clone());
                        done = true;
                    } else if (b.left <= 0 || b.pos.getY() < floorY - 30) {
                        impact(b, b.pos.clone());
                        done = true;
                    }
                }
                if (done) bolts.remove(b);
                if (state != State.PLAYING) {
                    bolts.clear();
                    return;
                }
            }
        }

        boolean hitsEnemy(Bolt b) {
            for (Player t : enemiesOf(b.side))
                if (t.getBoundingBox().expand(spell(b.type, "hitbox", b.type == Wizard.WITHER ? 0.6 : 0.35)).contains(b.pos.toVector())) return true;
            return false;
        }

        void trail(Bolt b) {
            switch (b.type) {
                case KINETIQUE -> {
                    world.spawnParticle(Particle.END_ROD, b.pos, 1, 0.02, 0.02, 0.02, 0);
                    world.spawnParticle(Particle.CRIT, b.pos, 2, 0.08, 0.08, 0.08, 0.02);
                }
                case FEU -> {
                    world.spawnParticle(Particle.FLAME, b.pos, 3, 0.08, 0.08, 0.08, 0.01);
                    world.spawnParticle(Particle.SMOKE, b.pos, 1, 0.05, 0.05, 0.05, 0);
                }
                case WITHER -> {
                    world.spawnParticle(Particle.LARGE_SMOKE, b.pos, 2, 0.1, 0.1, 0.1, 0);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, b.pos, 1, 0.05, 0.05, 0.05, 0);
                }
                case TOXIQUE -> world.spawnParticle(Particle.DUST, b.pos, 2, 0.06, 0.06, 0.06, 0, new Particle.DustOptions(Color.fromRGB(0x6CC24A), 1.2f));
                case TERRE -> world.spawnParticle(Particle.BLOCK, b.pos, 3, 0.1, 0.1, 0.1, 0, Material.COARSE_DIRT.createBlockData());
                case ARCANE -> {
                    world.spawnParticle(Particle.WITCH, b.pos, 1, 0.03, 0.03, 0.03, 0);
                    world.spawnParticle(Particle.DUST, b.pos, 1, 0.03, 0.03, 0.03, 0, new Particle.DustOptions(Color.fromRGB(0xC77DFF), 1f));
                }
                default -> world.spawnParticle(Particle.ENCHANT, b.pos, 2, 0.1, 0.1, 0.1, 0);
            }
        }

        void impact(Bolt b, Location c) {
            Player caster = Bukkit.getPlayer(b.caster);
            if (caster != null && !teams.containsKey(caster.getUniqueId())) caster = null;
            Wizard w = b.type;
            switch (w) {
                case KINETIQUE -> {
                    double r = spell(w, "radius", 3.5), kb = spell(w, "knockback", 0.9);
                    world.spawnParticle(Particle.EXPLOSION, c, 3, 0.6, 0.6, 0.6, 0);
                    world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
                    area(caster, b.side, c, r, spell(w, "damage", 7), t -> push(t, c, kb));
                    // le lanceur pris dans sa propre explosion est projeté (sans dégât) : saut à l'orbe
                    if (caster != null && isActive(caster.getUniqueId()) && getConfig().getBoolean("classes.kinetique.self-knockback", true)
                            && caster.getLocation().add(0, 0.9, 0).distance(c) <= r) push(caster, c, kb);
                }
                case FEU -> {
                    world.spawnParticle(Particle.FLAME, c, 40, 0.8, 0.8, 0.8, 0.05);
                    world.spawnParticle(Particle.LAVA, c, 6, 0.5, 0.5, 0.5, 0);
                    world.spawnParticle(Particle.EXPLOSION, c, 1, 0, 0, 0, 0);
                    world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.6f);
                    int burn = (int) (spell(w, "burn-time", 3) * 20);
                    area(caster, b.side, c, spell(w, "radius", 2.5), spell(w, "damage", 5), t -> t.setFireTicks(Math.max(t.getFireTicks(), burn)));
                }
                case WITHER -> {
                    world.spawnParticle(Particle.LARGE_SMOKE, c, 30, 0.7, 0.7, 0.7, 0.03);
                    world.spawnParticle(Particle.SOUL, c, 12, 0.6, 0.6, 0.6, 0.02);
                    world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.7f);
                    int wt = (int) (spell(w, "wither-time", 4) * 20);
                    int wl = Math.max(0, (int) spell(w, "wither-level", 1) - 1);
                    double steal = spell(w, "lifesteal", 1);
                    Player c2 = caster;
                    area(caster, b.side, c, spell(w, "radius", 2.5), spell(w, "damage", 4),
                            t -> {
                                t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, wt, wl, false, true, true));
                                if (c2 != null && steal > 0 && isActive(c2.getUniqueId())) heal(c2, steal);
                            });
                }
                case TOXIQUE -> {
                    world.spawnParticle(Particle.DUST, c, 40, 1.2, 0.6, 1.2, 0, new Particle.DustOptions(Color.fromRGB(0x6CC24A), 1.5f));
                    world.spawnParticle(Particle.ITEM_SLIME, c, 12, 0.6, 0.4, 0.6, 0.05);
                    world.playSound(c, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.8f);
                    int pt = (int) (spell(w, "poison-time", 3) * 20);
                    int pl = Math.max(0, (int) spell(w, "poison-level", 2) - 1);
                    area(caster, b.side, c, spell(w, "radius", 2.5), spell(w, "damage", 3),
                            t -> t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, pt, pl, false, true, true)));
                }
                case TERRE -> {
                    world.spawnParticle(Particle.BLOCK, c, 40, 0.6, 0.6, 0.6, 0, Material.STONE.createBlockData());
                    world.playSound(c, Sound.BLOCK_STONE_BREAK, 1.2f, 0.5f);
                    world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.6f);
                    int st = (int) (spell(w, "slow-time", 2) * 20);
                    area(caster, b.side, c, spell(w, "radius", 1.8), spell(w, "damage", 6),
                            t -> t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, st, 1, false, true, true)));
                }
                case ARCANE -> {
                    world.spawnParticle(Particle.WITCH, c, 15, 0.3, 0.3, 0.3, 0.05);
                    world.playSound(c, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.4f);
                    area(caster, b.side, c, spell(w, "radius", 1.2), spell(w, "damage", 3.5), null);
                }
                default -> {
                }
            }
        }

        void push(Player t, Location c, double power) {
            Vector v = t.getLocation().toVector().subtract(c.toVector());
            v.setY(0);
            if (v.lengthSquared() < 0.01) v = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            v.normalize().multiply(power).setY(0.45);
            t.setVelocity(t.getVelocity().add(v));
        }

        // -------------------------------------------------------------- kit

        void giveKit(Player p) {
            if (!p.getWorld().equals(world)) return;
            Side s = teams.get(p.getUniqueId());
            if (s == null) return;
            p.getInventory().clear();
            p.getInventory().setHelmet(armor(Material.LEATHER_HELMET, s));
            p.getInventory().setChestplate(armor(Material.LEATHER_CHESTPLATE, s));
            p.getInventory().setLeggings(armor(Material.LEATHER_LEGGINGS, s));
            p.getInventory().setBoots(armor(Material.LEATHER_BOOTS, s));
            p.getInventory().setItem(0, wand(wizardOf(p)));
            if (getConfig().getBoolean("class-change-in-base", true)) {
                ItemStack change = selector(wizardOf(p));
                ItemMeta cm = change.getItemMeta();
                cm.displayName(Component.text("Changer de classe", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(" (dans ta base)", NamedTextColor.GRAY)));
                change.setItemMeta(cm);
                change.setType(Material.BOOK);
                p.getInventory().setItem(8, change);
            }
            p.getInventory().setHeldItemSlot(0);
        }

        // -------------------------------------------------------------- partie

        boolean running() {
            return state == State.PLAYING || state == State.ENDING;
        }

        void startGame() {
            List<Player> list = online(players);
            list.removeIf(p -> !p.getWorld().equals(world));
            if (list.isEmpty() || world == null) { state = State.WAITING; return; }
            teams.clear();
            wizards.clear();
            dead.clear();
            kills.clear();
            lastHit.clear();
            lastCombat.clear();
            lastHitTime.clear();
            protectedUntil.clear();
            mana.clear();
            primaryReady.clear();
            secondaryReady.clear();
            auraUntil.clear();
            zones.clear();
            hideAllBars();
            chooseMap();
            resetMap();
            ensureLabels();
            // équipes équilibrées (au hasard, écart d'au plus un joueur)
            Collections.shuffle(list, random);
            Side first = random.nextBoolean() ? Side.ROUGE : Side.BLEU;
            for (int i = 0; i < list.size(); i++) teams.put(list.get(i).getUniqueId(), i % 2 == 0 ? first : first.other());
            state = State.PLAYING;
            total = list.size();
            gameLeft = Math.max(60, cfg("game-time", 900));
            gateTicks = Math.max(0, cfg("gate-time", 5)) * 20 + 1;
            world.setTime(cfg("time", 6000));
            for (Player p : list) {
                UUID id = p.getUniqueId();
                Side s = teams.get(id);
                wizards.put(id, classOf(p));
                kills.put(id, 0);
                mana.put(id, maxMana());
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(spawnSpot(s));
                giveKit(p);
                Wizard w = wizardOf(p);
                p.showTitle(Title.title(Component.text("TNT WIZARDS", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                        Component.text("Équipe " + s.fem(), s.color).append(Component.text(" - " + w.label, w.color)),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                p.sendMessage(PREFIX + "§7Tu es dans l'équipe " + legacy(s.color) + s.fem() + "§7, classe " + legacy(w.color) + w.label
                        + "§7. Clic droit : §f" + w.primary + "§7, clic gauche : §f" + w.secondary + "§7.");
            }
            colorNames();
            reportStatus();
            broadcast("§eMap : §d" + arena.lay.name() + " §7(" + points.size() + " points de capture).");
            broadcast("§eLa partie commence ! §7Tenez les points de capture : §6" + cfg("points-to-win", 1000)
                    + " points §7pour gagner (§f" + cfg("capture-points-per-second", 1) + "/s §7par point, §f" + cfg("kill-points", 5) + " §7par kill).");
        }

        void endGame(Side winner, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            bolts.clear();
            auraUntil.clear();
            zones.clear();
            hideAllBars();
            int r = score[Side.ROUGE.ordinal()], b = score[Side.BLEU.ordinal()];
            if (rewards) {
                for (Player p : online(teams.keySet())) {
                    if (!players.contains(p.getUniqueId())) continue;
                    eemi("reward " + p.getName() + " " + GAME_ID + " play");
                    if (winner != null && teams.get(p.getUniqueId()) == winner) eemi("reward " + p.getName() + " " + GAME_ID + " win");
                }
            }
            timer = cfg("end-time", 8);
            String sc = "Rouge " + r + " - " + b + " Bleu";
            for (Player p : online(players)) {
                Side s = teams.get(p.getUniqueId());
                Component title;
                if (winner == null) title = Component.text("Égalité !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
                else if (s == winner) title = Component.text("Victoire !", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
                else if (s != null) title = Component.text("Défaite", NamedTextColor.RED).decorate(TextDecoration.BOLD);
                else title = Component.text("Victoire " + winner.label, winner.color).decorate(TextDecoration.BOLD);
                p.showTitle(Title.title(title, Component.text(sc, NamedTextColor.GRAY)));
                p.playSound(p, s == winner && winner != null ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            if (winner == null) broadcast("§eÉgalité ! §7" + sc);
            else broadcast(legacy(winner.color) + "Les " + winner.plural + " gagnent la partie ! §7" + sc);
            List<UUID> byKills = new ArrayList<>(kills.keySet());
            byKills.sort((x, y) -> Integer.compare(kills.getOrDefault(y, 0), kills.getOrDefault(x, 0)));
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(3, byKills.size()); i++) {
                Player k = Bukkit.getPlayer(byKills.get(i));
                top.append(i == 0 ? "" : "§7, ").append("§f").append(k != null ? k.getName() : "?").append(" §8(").append(kills.getOrDefault(byKills.get(i), 0)).append(")");
            }
            if (!byKills.isEmpty()) broadcast("§7Kills : " + top);
            // les morts reviennent à la base pour la fin
            for (UUID id : new ArrayList<>(dead.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getWorld().equals(world) && teams.containsKey(id)) {
                    p.teleport(spawnSpot(teams.get(id)));
                    p.setGameMode(GameMode.ADVENTURE);
                }
            }
            dead.clear();
            if (winner != null)
                for (Player w : online(teams.keySet()))
                    if (teams.get(w.getUniqueId()) == winner)
                        for (int i = 0; i < 4; i++)
                            Bukkit.getScheduler().runTaskLater(EemiWizards.this, () -> {
                                if (w.isOnline() && state == State.ENDING && !destroyed && w.getWorld().equals(world)) firework(w.getLocation(), winner.armor);
                            }, i * 25L);
            reportStatus();
        }

        void backToLobby() {
            state = State.WAITING;
            hideAllBars();
            clearNames();
            teams.clear();
            wizards.clear();
            dead.clear();
            auraUntil.clear();
            zones.clear();
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
            resetMap();
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, false);
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
            broadcast("§a" + p.getName() + " §7a rejoint TNT Wizards §8(partie " + index + ") §7(§f" + players.size() + "/" + cfg("max-players", 16) + "§7).");
            reportStatus();
        }

        void toWaiting(Player p) {
            Location l = lobbySpawn();
            p.teleport(l);
            later(() -> {
                if (!p.isOnline() || !players.contains(p.getUniqueId()) || destroyed) return;
                if (!p.getWorld().equals(world)) return;
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(l);
                p.getInventory().setItem(0, selector(classOf(p)));
                p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter TNT Wizards"));
                p.getInventory().setHeldItemSlot(0);
                updateSidebar(p);
            });
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            boolean wasPlaying = state == State.PLAYING && teams.containsKey(id);
            Side s = teams.remove(id);
            if (wasPlaying) broadcast(legacy(s.color) + p.getName() + " §7a quitté la partie.");
            dead.remove(id);
            wizards.remove(id);
            mana.remove(id);
            auraUntil.remove(id);
            protectedUntil.remove(id);
            hideBar(p);
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // sans téléportation (changement de monde) : on ne touche plus au joueur (Multiverse-Inventories s'en occupe)
            if (teleport) sendToHub(p);
            if (state == State.PLAYING) {
                colorNames();
                checkTeams();
            }
            reportStatus();
        }

        // -------------------------------------------------------------- affichage

        void actionBar(Player p) {
            UUID id = p.getUniqueId();
            Wizard w = wizardOf(p);
            double m = mana.getOrDefault(id, maxMana());
            AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
            Component c = Component.text("❤ ", NamedTextColor.RED).append(Component.text((int) Math.ceil(p.getHealth()) + "/"
                    + (int) (maxHp == null ? 20 : maxHp.getValue()), NamedTextColor.WHITE)).append(Component.text("  |  ", NamedTextColor.GRAY))
                    .append(Component.text("Mana ", NamedTextColor.AQUA)).append(Component.text((int) m + "/" + (int) maxMana(), NamedTextColor.WHITE));
            long left = secondaryReady.getOrDefault(id, 0L) - System.currentTimeMillis();
            c = c.append(Component.text("  |  " + w.secondary + " : ", NamedTextColor.GRAY));
            c = c.append(left <= 0 ? Component.text("pret", NamedTextColor.GREEN) : Component.text((left / 1000 + 1) + " s", NamedTextColor.YELLOW));
            long aura = auraUntil.getOrDefault(id, 0L) - System.currentTimeMillis();
            if (aura > 0) c = c.append(Component.text("  |  Aura " + (aura / 1000 + 1) + " s", NamedTextColor.DARK_GRAY));
            if (isProtected(p)) c = c.append(Component.text("  |  Protégé", NamedTextColor.LIGHT_PURPLE));
            p.sendActionBar(c);
        }

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("tw", Criteria.DUMMY,
                        Component.text("TNT WIZARDS", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                // 1.4 : vie de chaque joueur sous son pseudo (critère HEALTH, tenu à jour par le serveur)
                if (getConfig().getBoolean("show-health", true)) {
                    Objective hp = b.registerNewObjective("vie", Criteria.HEALTH, Component.text("❤", NamedTextColor.RED));
                    hp.setDisplaySlot(DisplaySlot.BELOW_NAME);
                }
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("tw");
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
            UUID id = p.getUniqueId();
            boolean playing = running() && teams.containsKey(id);
            if (!playing) {
                if (games.size() > 1 || index > 1)
                    l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("n°" + index, NamedTextColor.WHITE)));
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + cfg("max-players", 16), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                Wizard w = classOf(p);
                l.add(Component.text("Classe : ", NamedTextColor.GRAY).append(Component.text(w.label, w.color)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.LIGHT_PURPLE)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
            } else {
                int win = cfg("points-to-win", 1000);
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(gameLeft), NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                l.add(Component.text("Rouge : ", NamedTextColor.RED).append(Component.text(score[Side.ROUGE.ordinal()] + "/" + win, NamedTextColor.WHITE)));
                l.add(Component.text("Bleu : ", NamedTextColor.BLUE).append(Component.text(score[Side.BLEU.ordinal()] + "/" + win, NamedTextColor.WHITE)));
                l.add(Component.text("   "));
                for (CapPoint cp : points)
                    l.add(Component.text("■ ", cp.owner == null ? NamedTextColor.WHITE : cp.owner.color)
                            .append(Component.text(cp.def.key() + " " + cp.def.name() + " ", NamedTextColor.GRAY))
                            .append(cp.status()));
                l.add(Component.text("    "));
                Side s = teams.get(id);
                Wizard w = wizardOf(p);
                l.add(Component.text("Équipe : ", NamedTextColor.GRAY).append(Component.text(s.fem(), s.color))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY)).append(Component.text(w.label, w.color)));
                l.add(Component.text("Tes kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(id, 0)), NamedTextColor.GREEN)));
            }
            l.add(Component.text("     "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        /** Pseudos colorés « [Rouge] » / « [Bleu] » sur le scoreboard de chaque joueur (lu par %eemi_team%). */
        void colorNames() {
            for (Player viewer : online(players)) {
                Scoreboard b = board(viewer);
                for (Side s : Side.values()) {
                    Team t = b.getTeam("tw_" + s.key());
                    if (t == null) {
                        t = b.registerNewTeam("tw_" + s.key());
                        t.color(s.color);
                        t.prefix(Component.text("[" + s.label + "] ", s.color));
                        t.setAllowFriendlyFire(false);
                    }
                    // seulement les changements (pas de retrait/ajout de tout le monde chaque seconde)
                    Set<String> want = new java.util.HashSet<>();
                    for (Map.Entry<UUID, Side> e : teams.entrySet()) {
                        Player p = Bukkit.getPlayer(e.getKey());
                        if (p != null && e.getValue() == s && players.contains(e.getKey())) want.add(p.getName());
                    }
                    for (String e : new ArrayList<>(t.getEntries())) if (!want.contains(e)) t.removeEntry(e);
                    for (String n : want) if (!t.hasEntry(n)) t.addEntry(n);
                }
            }
        }

        void clearNames() {
            for (Scoreboard b : boards.values())
                for (Side s : Side.values()) {
                    Team t = b.getTeam("tw_" + s.key());
                    if (t != null) t.unregister();
                }
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
                case PLAYING -> "en jeu (" + score[0] + " - " + score[1] + ")";
                case ENDING -> "fin";
            };
        }
    }

    // ------------------------------------------------------------------ objets

    private ItemStack armor(Material m, Side s) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(s.armor);
        meta.setUnbreakable(true);
        meta.displayName(Component.text("Robe " + s.fem(), s.color).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private List<Component> wandLore(Wizard w) {
        List<Component> lore = new ArrayList<>();
        int cost = (int) spell(w, "cost", switch (w) {
            case KINETIQUE -> 30;
            case GLACE -> 40;
            case WITHER -> 20;
            case TEMPETE, TERRE -> 35;
            case ARCANE -> 15;
            default -> 25;
        });
        int cd = (int) spell(w, "cooldown", switch (w) {
            case KINETIQUE -> 7;
            case GLACE -> 12;
            case FEU -> 9;
            case WITHER -> 8;
            case SANG -> 15;
            case TEMPETE, HYDRO -> 10;
            case TOXIQUE, TERRE -> 12;
            case ARCANE -> 6;
        });
        lore.add(Component.text("Clic droit : ", NamedTextColor.GRAY).append(Component.text(w.primary, NamedTextColor.WHITE))
                .append(Component.text(" (" + cost + " mana)", NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  " + w.primaryDesc, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Clic gauche : ", NamedTextColor.GRAY).append(Component.text(w.secondary, NamedTextColor.WHITE))
                .append(Component.text(" (recharge " + cd + " s)", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  " + w.secondaryDesc, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    private ItemStack wand(Wizard w) {
        ItemStack it = new ItemStack(w.wand);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Baguette " + w.label, w.color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(wandLore(w));
        m.setUnbreakable(true);
        m.setEnchantmentGlintOverride(true);
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, w.id);
        it.setItemMeta(m);
        return it;
    }

    /** Objet de la salle d'attente : baguette de la classe actuelle, clic = menu des classes. */
    private ItemStack selector(Wizard w) {
        ItemStack it = new ItemStack(w.wand);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Classe : " + w.label, w.color).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" (clic pour changer)", NamedTextColor.GRAY)));
        m.lore(wandLore(w));
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(menuKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private void openMenu(Player p) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            ClassMenu holder = new ClassMenu();
            Inventory inv = Bukkit.createInventory(holder, 36, Component.text("Choisis ta classe", NamedTextColor.DARK_PURPLE));
            holder.inv = inv;
            Wizard cur = classOf(p);
            ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
            ItemMeta pm = pane.getItemMeta();
            pm.displayName(Component.text(" "));
            pane.setItemMeta(pm);
            for (int i = 0; i < 36; i++) inv.setItem(i, pane);
            Wizard[] all = Wizard.values();
            for (int i = 0; i < all.length && i < MENU_SLOTS.length; i++) {
                Wizard w = all[i];
                ItemStack it = new ItemStack(w.wand);
                ItemMeta m = it.getItemMeta();
                m.displayName(Component.text(w.label, w.color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>(wandLore(w));
                lore.add(Component.empty());
                lore.add(w == cur ? Component.text("Classe choisie", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                        : Component.text("Clic pour choisir", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                m.lore(lore);
                m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
                if (w == cur) m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
                inv.setItem(MENU_SLOTS[i], it);
            }
            p.openInventory(inv);
        }, 2L);
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

    private boolean has(ItemStack it, NamespacedKey key) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(key);
    }

    private <T extends Entity> T tag(T e) {
        e.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
        return e;
    }

    private boolean isOurs(Entity e) {
        return e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE);
    }

    private void firework(Location l, Color c) {
        l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class, f -> {
            tag(f);
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(c, Color.PURPLE).withFade(Color.WHITE).flicker(true).build());
            m.setPower(1);
            f.setFireworkMeta(m);
        });
    }

    private static String legacy(NamedTextColor c) {
        if (c == NamedTextColor.RED) return "§c";
        if (c == NamedTextColor.BLUE) return "§9";
        if (c == NamedTextColor.AQUA) return "§b";
        if (c == NamedTextColor.GOLD) return "§6";
        if (c == NamedTextColor.DARK_GRAY) return "§8";
        if (c == NamedTextColor.DARK_RED) return "§4";
        if (c == NamedTextColor.YELLOW) return "§e";
        return "§f";
    }

    // ------------------------------------------------------------------ inscription

    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (pendingOf(p) != null) { p.sendMessage(PREFIX + "§7Ta partie est en préparation, patiente un instant."); return; }
        if (games.isEmpty()) { p.sendMessage(PREFIX + "§cLe monde de TNT Wizards n'est pas chargé."); return; }
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

    /** Renvoi au hub (seulement depuis un monde du jeu ou le hub : jamais ailleurs, ex. skyblock). */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    /** On ne touche au joueur que dans un monde du jeu ou au hub (Multiverse-Inventories). */
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
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(0);
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
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemiwizards.admin");
    }

    private Player attackerOf(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Player pl) return pl;
        if (d instanceof Projectile proj && proj.getShooter() instanceof Player pl) return pl;
        return null;
    }

    /**
     * Dégâts dans les mondes du jeu : seulement en partie, entre joueurs actifs. Sorts (dégâts calculés
     * par le plugin), mêlée faible entre ennemis, brûlure et Wither (crédités au dernier lanceur) ; tout
     * le reste est annulé. Coup mortel annulé = mort gérée par le plugin.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !isGameWorld(victim.getWorld())) return;
        Game g = gameAt(victim.getWorld());
        UUID id = victim.getUniqueId();
        if (g == null || !g.isActive(id) || g.gateTicks > 0 || g.isProtected(victim)) {
            e.setCancelled(true);
            return;
        }
        if (spellDamage) {
            e.setCancelled(false);
            if (e.getFinalDamage() >= victim.getHealth()) {
                e.setCancelled(true);
                g.kill(victim, spellAttacker != null ? spellAttacker : g.killerOf(victim));
            }
            return;
        }
        EntityDamageEvent.DamageCause cause = e.getCause();
        Player killer;
        if (e instanceof EntityDamageByEntityEvent by && (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            Player attacker = attackerOf(by);
            if (attacker == null || by.getDamager() != attacker || !g.isActive(attacker.getUniqueId())
                    || g.teams.get(attacker.getUniqueId()) == g.teams.get(id) || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                e.setCancelled(true);
                return;
            }
            e.setCancelled(false);
            e.setDamage(cfgd("melee-damage", 2));
            g.markHit(victim, attacker);
            killer = attacker;
            // clic gauche sur un ennemi avec la baguette : le sort secondaire part aussi (s'il est prêt)
            if (has(attacker.getInventory().getItemInMainHand(), wandKey)) g.castSecondary(attacker, false);
        } else if (cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.WITHER || cause == EntityDamageEvent.DamageCause.POISON) {
            killer = g.killerOf(victim);
            g.markCombat(victim);
        } else {
            e.setCancelled(true);
            return;
        }
        if (!e.isCancelled() && e.getFinalDamage() >= victim.getHealth()) {
            e.setCancelled(true);
            g.kill(victim, killer);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && (isGameWorld(p.getWorld()) || gameOf(p) != null)) e.setCancelled(true);
    }

    /** Les flammes d'un sort ne s'éteignent pas d'elles-mêmes hors partie : on coupe aussi le feu des non-joueurs. */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (!isGameWorld(e.getEntity().getWorld())) return;
        if (!(e.getEntity() instanceof Player p)) { e.setCancelled(true); return; }
        Game g = gameAt(p.getWorld());
        if (g == null || !g.isActive(p.getUniqueId())) e.setCancelled(true);
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

    /** Nos entités (étiquettes des points, panneau, feux d'artifice) apparaissent même si WorldGuard les bloque. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (!e.isCancelled() || !isGameWorld(e.getLocation().getWorld())) return;
        // nos entités, et l'éclair visuel (sans dégât ni feu) de la classe Tempête
        if (isOurs(e.getEntity()) || (e.getEntity() instanceof org.bukkit.entity.LightningStrike ls && ls.isEffect())) e.setCancelled(false);
    }

    /** Pas de projectiles (arcs, perles...) dans les mondes du jeu : les sorts sont simulés par le plugin. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (isOurs(e.getEntity())) return;
        if (isGameWorld(e.getEntity().getWorld()) && !(e.getEntity().getShooter() instanceof Player p && admin(p))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // le décor (balises, tables d'enchantement...) ne s'ouvre pas
        if (isGameWorld(p.getWorld()) && !admin(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
        }
        Game g = gameOf(p);
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean left = e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK;
        if (it.getType() == Material.RED_BED && has(it, kitKey)) {
            e.setCancelled(true);
            if (!right) return;
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté TNT Wizards.");
            return;
        }
        if (has(it, menuKey)) {
            e.setCancelled(true);
            if (!right && !left) return;
            if (g.running()) {
                Side side = g.teams.get(p.getUniqueId());
                if (side == null || !g.isActive(p.getUniqueId()) || !g.inBase(p.getLocation(), side)) {
                    p.sendMessage(PREFIX + "§cTu ne peux changer de classe que dans ta base.");
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                    return;
                }
            }
            openMenu(p);
            return;
        }
        if (has(it, wandKey)) {
            e.setCancelled(true);
            if (right) g.castPrimary(p);
            else if (left) g.castSecondary(p, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ClassMenu) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
            Wizard[] all = Wizard.values();
            for (int i = 0; i < MENU_SLOTS.length && i < all.length; i++)
                if (MENU_SLOTS[i] == e.getRawSlot()) {
                    setClass(p, all[i]);
                    Bukkit.getScheduler().runTask(this, () -> p.closeInventory());
                    return;
                }
            return;
        }
        if (e.getWhoClicked() instanceof Player p && gameOf(p) != null && !admin(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ClassMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (gameOf(e.getPlayer()) != null || has(e.getItemDrop().getItemStack(), kitKey)) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    /** Sécurité (/kill...) : pas de drops, compte comme une mort normale. */
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
        if (g.isActive(p.getUniqueId())) {
            e.setRespawnLocation(p.getLocation().getWorld().equals(g.world) ? p.getLocation() : g.spectatorSpot());
            later(() -> {
                if (p.isOnline() && p.getWorld().equals(g.world)) g.kill(p, g.killerOf(p));
            });
            return;
        }
        if (g.running()) {
            e.setRespawnLocation(g.spectatorSpot());
            later(() -> { if (p.isOnline() && p.getWorld().equals(g.world) && !g.teams.containsKey(p.getUniqueId())) p.setGameMode(GameMode.SPECTATOR); });
            return;
        }
        e.setRespawnLocation(g.lobbySpawn());
        later(() -> g.toWaiting(p));
    }

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
        boolean admin = sender.hasPermission("eemiwizards.admin");
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
            case "classe", "class", "kit" -> {
                if (p == null) return true;
                if (args.length > 1) {
                    Wizard w = Wizard.byId(args[1]);
                    if (w == null) {
                        StringBuilder sb = new StringBuilder();
                        for (Wizard x : Wizard.values()) sb.append(sb.isEmpty() ? "" : ", ").append(x.id);
                        p.sendMessage(PREFIX + "§cClasse inconnue. §7" + sb);
                        return true;
                    }
                    setClass(p, w);
                    if (gameOf(p) != null && gameOf(p).running()) p.sendMessage(PREFIX + "§7Elle servira à la prochaine partie.");
                } else openMenu(p);
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
                p.sendMessage(PREFIX + "§7Tu as quitté TNT Wizards.");
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
                            + (g.spectators.isEmpty() ? "" : " §8| §7spectateurs §f" + g.spectators.size()));
                sender.sendMessage("§8- §7modèle §f" + modelName() + " §7" + (model != null ? "pret" : modelBusy ? "en préparation" : "absent"));
            }
            case "map" -> {
                if (!admin) return true;
                if (args.length < 2 || Arena.layout(args[1]) == null) {
                    StringBuilder sb = new StringBuilder();
                    for (Arena.Layout l : Arena.LAYOUTS) sb.append(sb.isEmpty() ? "" : ", ").append(l.id()).append(" (").append(l.name()).append(")");
                    sender.sendMessage(PREFIX + "§7Maps : §f" + sb + "§7. §f/tntwizards map <id> §7impose la prochaine partie.");
                    return true;
                }
                forcedMap = Arena.layout(args[1]).id();
                sender.sendMessage(PREFIX + "§7Prochaine partie sur §d" + Arena.layout(args[1]).name() + "§7.");
            }
            case "build" -> {
                if (!admin) return true;
                if (g1 == null || g1.world == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g1.running()) { sender.sendMessage(PREFIX + "§cImpossible : la partie 1 est en cours."); return true; }
                loadSettings();
                g1.build();
                for (Player pl : online(g1.players)) g1.toWaiting(pl);
                makeModel();
                sender.sendMessage(PREFIX + "§aMap de la partie 1 reconstruite §7(modèle refait).");
            }
            case "reload" -> {
                if (!admin) return true;
                String oldBase = base;
                int oldF = floorY, oldL = lobbyY;
                loadSettings();
                if (!base.equals(oldBase) || floorY != oldF || lobbyY != oldL) {
                    base = oldBase;
                    floorY = oldF;
                    lobbyY = oldL;
                    sender.sendMessage(PREFIX + "§cLe monde et les hauteurs ne changent qu'au redémarrage.");
                }
                for (Game g : games.values()) if (g.world != null) prepareRules(g.world);
                sender.sendMessage(PREFIX + "§aConfig rechargée §7(valeurs des sorts et règles prises tout de suite).");
            }
            default -> sender.sendMessage(PREFIX + "§7/tntwizards <join|leave|classe [id]|spectate [joueur]>"
                    + (admin ? " §8| admin : start [n], stop [n], parties, build, reload" : ""));
        }
        return true;
    }

    private void spectate(Player p, String targetName) {
        if (gameOf(p) != null || pendingOf(p) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/tntwizards leave §cd'abord."); return; }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : gameOf(t);
            if (g == null) { p.sendMessage(PREFIX + "§cCe joueur n'est dans aucune partie de TNT Wizards."); return; }
            if (!g.running()) { p.sendMessage(PREFIX + "§cSa partie n'a pas encore commencé."); return; }
        } else {
            int best = -1;
            for (Game c : games.values())
                if (c.state == State.PLAYING && c.world != null && c.teams.size() > best) { best = c.teams.size(); g = c; }
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

    /** Statut agrégé de toutes les parties (waiting si une partie attend, sinon playing si une joue). */
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
                if (!g.running() || !g.teams.containsKey(p.getUniqueId())) waiting++;
                else playing++;
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
