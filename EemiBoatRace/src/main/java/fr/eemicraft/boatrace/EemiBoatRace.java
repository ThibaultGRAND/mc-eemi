package fr.eemicraft.boatrace;

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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
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

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 * Course de bateaux sur glace d'EEMICRAFT (façon Mario Kart / courses de bateau des serveurs).
 * Circuits importés (Cosmic Cove, The Adventure, Mario Kart Stadium : mondes importés tels quels) ou
 * circuit procédural construit par le plugin dans son monde vide (glace bleue, murets, tribunes, arche
 * de départ, passerelle, sapins). Le tracé est défini dans {@link Track} : il sert à poser la piste et à
 * suivre la progression (points de contrôle dans l'ordre, pas de raccourci). Tours selon le circuit ;
 * quand le premier finit, les autres ont 30 s ; classement et record par circuit.
 *
 * Multi-partie (2.2) : chaque partie a son circuit. Une partie utilise le monde d'origine d'un circuit
 * s'il est libre, sinon une copie « <monde>_n » faite depuis le modèle propre « <monde>_modele ».
 * Rotation : à la fin d'une course, la partie passe sur un autre circuit dont le monde d'origine est
 * libre (joueurs emmenés dans la nouvelle salle d'attente).
 */
public final class EemiBoatRace extends JavaPlugin implements Listener {

    private static final String PREFIX = "§b[§fCourse de bateaux§b] §r";
    private static final String GAME = "boatrace";

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Phase { NONE, GRID, RACE }

    /** Un coureur d'une partie. */
    private static final class Racer {
        final UUID id;
        final String name;
        EntityType boatType;
        UUID boat;
        int slot;
        /** Échantillon du tracé le plus proche, position s (0..longueur) et distance « déroulée » depuis la ligne. */
        int idx;
        double s, dist;
        /** Nombre de points de contrôle passés (la ligne compte comme le dernier de chaque tour). */
        int passed;
        long lapStart, bestLap = -1, finish = -1;
        int place;
        boolean respawning;
        int offBoatTicks;
        double stuckRef;
        int stuckSec;
        long lastRespawn;

        Racer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // ------------------------------------------------------------------ réglages communs

    private NamespacedKey kitKey, boatKey;
    /** Réglages du circuit procédural (construction) ; les parties lisent ceux de leur circuit. */
    private Track track;
    private int floorY, lobbyY, width, laps, checkpoints;
    private double half, wallOut;
    /** Feux de départ sous l'arche (coordonnées, circuit procédural seulement). */
    private final List<int[]> lightPos = new ArrayList<>();
    /** Monde en cours de construction (build). */
    private World bw;

    /**
     * Circuit importé (2.0) : monde importé tel quel (mv import), ligne médiane dans tracks/<id>.txt du
     * jar (un point « x z » ou « x z sol posé » par ligne, dans le sens de la course, calculée hors ligne
     * depuis la glace de la piste), ligne d'arrivée au point (fx, fz). floor = niveau du sol (y où repose
     * le bateau) pour les lignes sans hauteur. laps / checkpoints : 0 = réglage du config. gridCols :
     * colonnes de la grille de départ.
     */
    private record Circuit(String name, String world, int floor, int lobbyY, double fx, double fz,
                           int laps, int checkpoints, int gridCols) {}

    private static final Map<String, Circuit> CIRCUITS = new LinkedHashMap<>();
    static {
        // Cosmic Cove (Ice Boat Track) : boucle de ~1 500 blocs en glace bleue en y 102, ligne d'arrivée
        // en zigzag de boue en x 121-122 sur la ligne droite (z -35 a -15), départ vers +x
        CIRCUITS.put("cosmic", new Circuit("Cosmic Cove", "boat_cosmic", 103, 220, 121.5, -25.0, 0, 0, 2));
        // The Adventure (MuscplaysYT, CurseForge 1514002) : grande boucle de ~3 400 blocs, un seul tour (mode
        // « 1 round » de la map) ; départ dans la voie de départ (x -260), ligne en z -36, vers le nord ;
        // 2 colonnes de bulles, deux ponts, des slimes, un labyrinthe ; la croix se contourne des deux côtés
        CIRCUITS.put("adventure", new Circuit("The Adventure", "boat_adventure", -59, 220, -259.5, -35.5, 1, 40, 1));
        // MarioKart Stadium (Silinox_, CurseForge 1331248 « boat ice race ») : boucle de ~590 blocs sur deux
        // étages (colonne de bulles puis saut au bout du pont), ligne en damier en z 520, départ vers le sud
        CIRCUITS.put("stadium", new Circuit("Mario Kart Stadium", "boat_stadium", -60, 220, -319.5, 520.0, 0, 0, 2));
    }

    /** Un circuit chargé : son tracé, son monde d'origine et son modèle propre pour les copies. */
    private final class Course {
        final String id, name, worldName;
        final boolean imported;
        final Track track;
        final int lobbyY, gridCols, lapsOwn, cpOwn;
        World model;

        Course(String id, String name, String worldName, boolean imported, Track track, int lobbyY, int gridCols, int lapsOwn, int cpOwn) {
            this.id = id;
            this.name = name;
            this.worldName = worldName;
            this.imported = imported;
            this.track = track;
            this.lobbyY = lobbyY;
            this.gridCols = gridCols;
            this.lapsOwn = lapsOwn;
            this.cpOwn = cpOwn;
        }

        int laps() {
            return lapsOwn > 0 ? lapsOwn : EemiBoatRace.this.laps;
        }

        int checkpoints() {
            return cpOwn > 0 ? cpOwn : EemiBoatRace.this.checkpoints;
        }

        String modelName() {
            return worldName + "_modele";
        }

        /** Les records dépendent du circuit (procédural : une clé par échelle et largeur). */
        String recordKey() {
            if (imported) return "circuit-" + id;
            return "circuit-" + Math.round(cfgD("track-scale", 1.4) * 100) + "-" + width;
        }

        World base() {
            return Bukkit.getWorld(worldName);
        }

        /** Monde de la partie, d'une copie en préparation ou modèle de ce circuit. */
        boolean owns(String w) {
            return w.equals(worldName) || w.equals(modelName()) || WorldClone.index(worldName, w) > 0;
        }
    }

    /** Circuits utilisables (monde chargé), dans l'ordre du config ; le premier est celui de la partie 1. */
    private final Map<String, Course> courses = new LinkedHashMap<>();

    /** Joueurs autorisés à descendre de leur bateau (replacement, téléportation par une autre commande). */
    private final Set<UUID> exitAllowed = new HashSet<>();
    private String lastStatus;
    private long lastStatusAt;
    private long ticks;
    private YamlConfiguration records;
    private final Random random = new Random();

    // ------------------------------------------------------------------ parties

    private final Map<Integer, Game> games = new TreeMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private final Map<UUID, Game> spectating = new HashMap<>();
    /** Copies en préparation : numéro → joueurs qui l'attendent (au hub). */
    private final Map<Integer, List<UUID>> creating = new LinkedHashMap<>();
    /** Circuit de chaque copie en préparation. */
    private final Map<Integer, Course> creatingCourse = new HashMap<>();
    private final Map<UUID, Integer> pendingOf = new HashMap<>();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        boatKey = new NamespacedKey(this, "boat");
        saveDefaultConfig();
        loadSettings();
        loadCourses();
        loadRecords();
        getServer().getPluginManager().registerEvents(this, this);
        if (!courses.isEmpty()) {
            Course c = courses.values().iterator().next();
            games.put(1, new Game(1, c.base(), c, false));
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            long delay = 20L;
            for (Course c : courses.values()) {
                World w = c.base();
                if (w == null) continue;
                build(w, c);
                WorldClone.cleanup(this, w, c.worldName, maxInstances());
                Bukkit.getScheduler().runTaskLater(this, () -> makeModel(c), delay);
                delay += 40L;
            }
        }, 20L);
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) {
            for (Player p : online(g.players)) exitAllowed.add(p.getUniqueId());
            g.removeBoats();
            g.clearCages();
            for (Player p : online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (p.getWorld().equals(g.world)) sendToHub(p);
            }
            if (g.sign != null) g.sign.remove();
            if (g.copy) {
                for (Player p : g.world.getPlayers()) sendToHub(p);
                // fichiers supprimés au prochain démarrage (WorldClone.cleanup)
                Bukkit.unloadWorld(g.world, false);
            }
        }
        games.clear();
        byPlayer.clear();
        spectating.clear();
    }

    private int maxInstances() {
        return Math.max(1, getConfig().getInt("max-instances", 3));
    }

    /** (Re)crée le modèle propre d'un circuit depuis son monde d'origine (après la salle d'attente). */
    private void makeModel(Course c) {
        World base = c.base();
        if (base == null) return;
        World old = Bukkit.getWorld(c.modelName());
        c.model = null;
        if (old != null) {
            for (Player p : old.getPlayers()) sendToHub(p);
            Bukkit.unloadWorld(old, false);
        }
        WorldClone.create(this, base, c.modelName(), w -> {
            c.model = w;
            if (w == null) getLogger().warning("Modele " + c.modelName() + " non cree : pas de copie de " + c.name + ".");
            else getLogger().info("Modele " + c.modelName() + " pret (" + c.name + ").");
        });
    }

    private void loadSettings() {
        reloadConfig();
        floorY = cfg("floor-y", 100);
        lobbyY = cfg("lobby-y", 140);
        width = Math.max(4, cfg("track-width", 7));
        half = width / 2.0;
        wallOut = half + 1.5;
        laps = Math.max(1, cfg("laps", 3));
        checkpoints = Math.max(4, cfg("checkpoints", 16));
        // le tracé procédural ne change qu'au redémarrage (les parties gardent le leur)
        if (track == null) {
            track = new Track(Math.max(0.6, cfgD("track-scale", 1.4)), 1.0);
            track.setFloor(floorY + 1);
        }
    }

    /**
     * Circuits (au démarrage seulement) : liste « circuits » du config, sinon tous les circuits importés en
     * commençant par « circuit » ; circuit: procedural = le seul circuit construit par le plugin.
     * Un circuit dont le monde n'est pas chargé est ignoré (avertissement).
     */
    private void loadCourses() {
        courses.clear();
        String first = getConfig().getString("circuit", "cosmic").toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();
        if (first.equals("procedural")) ids.add("procedural");
        else if (getConfig().isList("circuits")) {
            if (CIRCUITS.containsKey(first)) ids.add(first);
            for (String s : getConfig().getStringList("circuits")) ids.add(s.toLowerCase(Locale.ROOT).trim());
        } else {
            if (CIRCUITS.containsKey(first)) ids.add(first);
            ids.addAll(CIRCUITS.keySet());
        }
        for (String id : ids) {
            if (courses.containsKey(id)) continue;
            if (id.equals("procedural")) {
                String name = getConfig().getString("world", "boatrace");
                World w = Bukkit.getWorld(name);
                if (w == null) {
                    w = new WorldCreator(name).generator(new VoidGenerator()).createWorld();
                    if (w != null) getLogger().info("Monde " + name + " cree (vide).");
                }
                if (w == null) { getLogger().severe("Impossible de charger ou creer le monde " + name + "."); continue; }
                prepareWorld(w, lobbyY);
                courses.put(id, new Course(id, "Circuit d'EEMICRAFT", name, false, track, lobbyY, 2, 0, 0));
                continue;
            }
            Circuit c = CIRCUITS.get(id);
            if (c == null) { getLogger().warning("Circuit inconnu ignore : " + id); continue; }
            World w = Bukkit.getWorld(c.world());
            if (w == null) {
                getLogger().severe("Monde " + c.world() + " introuvable (a importer avec /mv import " + c.world() + " normal) : circuit " + c.name() + " ignore.");
                continue;
            }
            Track t;
            try {
                t = new Track(loadLine(id, c.floor()), 1, c.fx(), c.fz(), 1.0);
            } catch (IllegalStateException e) {
                getLogger().severe(e.getMessage());
                continue;
            }
            prepareWorld(w, c.lobbyY());
            courses.put(id, new Course(id, c.name(), c.world(), true, t, c.lobbyY(), Math.max(1, c.gridCols()), c.laps(), c.checkpoints()));
        }
        if (courses.isEmpty()) getLogger().severe("Aucun circuit utilisable : course de bateaux desactivee.");
        else getLogger().info("Circuits : " + String.join(", ", courses.keySet()) + ".");
    }

    /** Ligne médiane d'un circuit importé (tracks/<id>.txt du jar) ; floor = sol des lignes sans hauteur. */
    private double[][] loadLine(String id, int floor) {
        List<double[]> pts = new ArrayList<>();
        try (java.io.InputStream in = getResource("tracks/" + id + ".txt")) {
            if (in == null) throw new IOException("tracks/" + id + ".txt absent du jar");
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.trim().split("\\s+");
                if (p.length >= 4) pts.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])});
                else if (p.length >= 2) pts.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), floor, 1});
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("Ligne du circuit " + id + " illisible : " + e.getMessage(), e);
        }
        return pts.toArray(new double[0][]);
    }

    private void loadRecords() {
        File f = new File(getDataFolder(), "records.yml");
        records = YamlConfiguration.loadConfiguration(f);
    }

    private void saveRecords() {
        try {
            records.save(new File(getDataFolder(), "records.yml"));
        } catch (IOException e) {
            getLogger().warning("Impossible d'enregistrer records.yml : " + e.getMessage());
        }
    }

    private void prepareWorld(World world, int lobbyY) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.PVP, false);
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
        world.setSpawnLocation(lobbySpawn(world, lobbyY));
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 141, 0.5);
        }
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private double cfgD(String key, double def) {
        return getConfig().getDouble(key, def);
    }

    private int maxPlayers() {
        return Math.max(1, Math.min(cfg("max-players", 12), 24));
    }

    // ------------------------------------------------------------------ positions

    private Location lobbySpawn(World world, int lobbyY) {
        Location l = new Location(world, 0.5, lobbyY + 1, 0.5);
        l.setYaw(180);
        return l;
    }

    /** Point de la ligne médiane à la position s (+ décalage latéral), au niveau du sol de la piste, tourné dans le sens de la course. */
    private Location trackLocation(World world, Track t, double s, double lateral) {
        double[] a = t.at(s);
        Location l = new Location(world, a[0] - a[3] * lateral, t.yAt(s), a[1] + a[2] * lateral);
        l.setYaw(Track.yaw(a[2], a[3]));
        l.setPitch(0);
        return l;
    }

    // ------------------------------------------------------------------ construction du circuit (partie 1)

    private void set(int x, int y, int z, Material m) {
        bw.getBlockAt(x, y, z).setType(m, false);
    }

    private void setData(int x, int y, int z, String data) {
        try {
            bw.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(data), false);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Bloc invalide ignore : " + data);
        }
    }

    /**
     * Construit tout : sol de neige, piste de glace bleue (distance à la ligne médiane <= largeur/2),
     * murets en glace compacte avec bordure rouge/blanche, ligne d'arrivée en damier, arche de départ
     * avec feux, tribunes de la ligne droite, drapeaux, passerelle sur la ligne droite du dos,
     * sapins, salle d'attente en lobby-y.
     */
    private void build(World world, Course co) {
        bw = world;
        Game g1 = gameOfWorld(world);
        if (co.imported) {
            // circuit importé : rien à construire sauf la salle d'attente (au-dessus de l'origine du monde)
            for (int cx = -1; cx <= 0; cx++)
                for (int cz = -1; cz <= 0; cz++) world.getChunkAt(cx, cz).load(true);
            if (g1 != null) g1.removeBoats();
            if (g1 != null) g1.cages.clear();
            buildLobby(co.lobbyY);
            world.setSpawnLocation(lobbySpawn(world, co.lobbyY));
            if (g1 != null) g1.ensureSign();
            getLogger().info(String.format(Locale.ROOT, "Circuit %s pret : %.0f blocs par tour, %d tour(s), sol de y %.0f a %.0f, salle d'attente en y %d.",
                    co.name, co.track.length, co.laps(), co.track.minY(), maxY(co.track), co.lobbyY));
            return;
        }
        long t0 = System.currentTimeMillis();
        int ops = 0;
        int F = floorY;
        Track.Grid g = track.grid(cfg("ground-margin", 14), wallOut + 10);
        int x0 = g.minX, z0 = g.minZ, x1 = g.minX + g.w - 1, z1 = g.minZ + g.h - 1;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) world.getChunkAt(cx, cz).load(true);
        for (int cx = -1; cx <= 0; cx++)
            for (int cz = -1; cz <= 0; cz++) world.getChunkAt(cx, cz).load(true);
        if (g1 != null) {
            g1.removeBoats();
            g1.cages.clear();
        }
        lightPos.clear();

        // 1) on vide le volume
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = F - 2; y <= F + 14; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) { b.setType(Material.AIR, false); ops++; }
                }
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++)
                for (int y = lobbyY - 1; y <= lobbyY + 7; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) { b.setType(Material.AIR, false); ops++; }
                }

        // 2) sol, piste, murets, ligne
        boolean[] used = new boolean[g.w * g.h];
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                float d = g.dist(x, z);
                double s = g.pos(x, z);
                boolean line = Math.abs(track.wrap(s)) < 1.0;
                set(x, F - 1, z, d <= wallOut ? Material.PACKED_ICE : Material.STONE);
                if (d <= half) {
                    set(x, F, z, line && ((x + z) & 1) == 0 ? Material.PACKED_ICE : Material.BLUE_ICE);
                    used[(x - x0) + (z - z0) * g.w] = true;
                } else if (d <= wallOut) {
                    set(x, F, z, Material.PACKED_ICE);
                    Material curb;
                    if (line) curb = ((x + z) & 1) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE;
                    else curb = ((int) Math.floor(s / 2) & 1) == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE;
                    set(x, F + 1, z, curb);
                    used[(x - x0) + (z - z0) * g.w] = true;
                } else {
                    set(x, F, z, Material.SNOW_BLOCK);
                }
                ops += 2;
            }

        // 3) tribunes de la ligne droite des stands (dehors : z <= -7, dedans : z >= 8), x -30..30
        int sx0 = -cfg("stands-half-length", 30), sx1 = cfg("stands-half-length", 30);
        for (int x = sx0; x <= sx1; x++) {
            // dehors, 6 rangées qui montent vers le sud (-z), spectateurs tournés vers la piste
            for (int k = 0; k < 6; k++) {
                int z = -7 - k, h = k + 1;
                for (int y = F + 1; y < F + h; y++) set(x, y, z, Material.SPRUCE_PLANKS);
                setData(x, F + h, z, "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]");
                mark(used, g, x, z);
            }
            for (int y = F + 1; y <= F + 9; y++) set(x, y, -13, Math.floorMod(x, 6) == 0 ? Material.SPRUCE_LOG : Material.SPRUCE_PLANKS);
            mark(used, g, x, -13);
            for (int z = -13; z <= -9; z++) set(x, F + 10, z, Math.floorMod(x, 4) < 2 ? Material.RED_WOOL : Material.WHITE_WOOL);
            if (Math.floorMod(x, 6) == 0) {
                setData(x, F + 11, -13, "minecraft:spruce_fence");
                set(x, F + 12, -13, wool(x / 6));
            }
            // dedans, 3 rangées tournées vers le nord (vers la piste)
            for (int k = 0; k < 3; k++) {
                int z = 8 + k, h = k + 1;
                for (int y = F + 1; y < F + h; y++) set(x, y, z, Material.SPRUCE_PLANKS);
                setData(x, F + h, z, "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight]");
                mark(used, g, x, z);
            }
            for (int y = F + 1; y <= F + 5; y++) set(x, y, 11, Math.floorMod(x, 6) == 0 ? Material.SPRUCE_LOG : Material.SPRUCE_PLANKS);
            mark(used, g, x, 11);
            if (Math.floorMod(x, 6) == 3) setData(x, F + 6, 11, "minecraft:lantern[hanging=false]");
        }
        // escaliers d'accès aux bouts des tribunes : rien, ce sont des gradins décoratifs

        // 4) arche de départ (sur la ligne) avec feux de départ
        double[] a = track.at(0);
        double nx = -a[3], nz = a[2];
        double span = half + 2.5;
        for (double l = -span; l <= span + 0.01; l += 0.5) {
            int bx = (int) Math.floor(a[0] + nx * l), bz = (int) Math.floor(a[1] + nz * l);
            boolean pillar = Math.abs(l) >= span - 0.01;
            if (pillar) {
                for (int y = F + 1; y <= F + 8; y++) setData(bx, y, bz, "minecraft:quartz_pillar[axis=y]");
                set(bx, F + 9, bz, Material.CHISELED_QUARTZ_BLOCK);
            }
            for (int y = F + 7; y <= F + 8; y++)
                if (!pillar) set(bx, y, bz, ((bx + bz + y) & 1) == 0 ? Material.BLACK_WOOL : Material.WHITE_WOOL);
            mark(used, g, bx, bz);
        }
        for (int l = -1; l <= 1; l++) {
            int bx = (int) Math.floor(a[0] + nx * l), bz = (int) Math.floor(a[1] + nz * l);
            world.getBlockAt(bx, F + 6, bz).setType(Material.BLACK_CONCRETE, false);
            lightPos.add(new int[]{bx, F + 6, bz});
        }

        // 5) drapeaux le long des murets (un tous les flag-every blocs, côtés alternés)
        int every = Math.max(4, cfg("flag-every", 12));
        int c = 0;
        for (int i = every / 2; i < track.n; i += every) {
            double s = i * track.step;
            if (Math.abs(track.wrap(s)) < 8) continue;
            double[] p = track.at(s);
            double side = (c % 2 == 0 ? 1 : -1) * (half + 3);
            int bx = (int) Math.floor(p[0] - p[3] * side), bz = (int) Math.floor(p[1] + p[2] * side);
            c++;
            if (g.dist(bx, bz) <= wallOut + 0.5 || isUsed(used, g, bx, bz)) continue;
            for (int y = F + 1; y <= F + 3; y++) setData(bx, y, bz, "minecraft:spruce_fence");
            Material w = wool(c);
            set(bx, F + 4, bz, w);
            set(bx, F + 5, bz, w);
            int fx = bx + (int) Math.round(p[2]), fz = bz + (int) Math.round(p[3]);
            if (g.dist(fx, fz) > wallOut + 0.5 && !isUsed(used, g, fx, fz)) {
                set(fx, F + 4, fz, w);
                set(fx, F + 5, fz, w);
                mark(used, g, fx, fz);
            }
            mark(used, g, bx, bz);
        }

        // 6) passerelle au-dessus de la ligne droite du dos
        double sc = cfgD("track-scale", 1.4);
        double bridgeS = track.nearestGlobal(-20 * sc + 0.5, 28 * sc + 0.5) * track.step;
        double[] b = track.at(bridgeS);
        double bnx = -b[3], bnz = b[2];
        double bspan = half + 4;
        for (double l = -bspan; l <= bspan + 0.01; l += 0.5)
            for (int along = -1; along <= 1; along++) {
                int bx = (int) Math.floor(b[0] + bnx * l + b[2] * along), bz = (int) Math.floor(b[1] + bnz * l + b[3] * along);
                set(bx, F + 6, bz, Material.SPRUCE_PLANKS);
                mark(used, g, bx, bz);
            }
        for (double l = -bspan; l <= bspan + 0.01; l += 0.5)
            for (int along = -2; along <= 2; along += 4) {
                int bx = (int) Math.floor(b[0] + bnx * l + b[2] * along), bz = (int) Math.floor(b[1] + bnz * l + b[3] * along);
                setData(bx, F + 6, bz, "minecraft:spruce_slab[type=bottom]");
                setData(bx, F + 7, bz, "minecraft:spruce_fence");
            }
        for (int sgn = -1; sgn <= 1; sgn += 2)
            for (int along = -1; along <= 1; along += 2) {
                int bx = (int) Math.floor(b[0] + bnx * sgn * bspan + b[2] * along), bz = (int) Math.floor(b[1] + bnz * sgn * bspan + b[3] * along);
                for (int y = F + 1; y <= F + 5; y++) set(bx, y, bz, Material.STONE_BRICKS);
                setData(bx, F + 8, bz, "minecraft:lantern[hanging=false]");
                mark(used, g, bx, bz);
            }

        // 7) sapins sur la neige, loin de la piste
        Random r = new Random(1234);
        double chance = cfgD("tree-chance", 0.006);
        for (int x = x0 + 2; x <= x1 - 2; x++)
            for (int z = z0 + 2; z <= z1 - 2; z++) {
                if (r.nextDouble() >= chance) continue;
                if (g.dist(x, z) < half + 9) continue;
                boolean free = true;
                for (int dx = -2; dx <= 2 && free; dx++)
                    for (int dz = -2; dz <= 2 && free; dz++) if (isUsed(used, g, x + dx, z + dz)) free = false;
                if (!free) continue;
                if (x >= sx0 - 3 && x <= sx1 + 3 && z >= -16 && z <= 14) continue;
                tree(x, z, 5 + r.nextInt(3));
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) mark(used, g, x + dx, z + dz);
            }

        // 8) salle d'attente (au-dessus de la ligne de départ)
        buildLobby(lobbyY);
        world.setSpawnLocation(lobbySpawn(world, lobbyY));
        if (g1 != null) {
            g1.ensureSign();
            g1.removeBoats();
        }
        getLogger().info(String.format(Locale.ROOT, "Circuit construit : %.0f blocs par tour, piste de %d de large, %d x %d blocs (%d a %d, %d a %d), %d operations en %d ms.",
                track.length, width, g.w, g.h, x0, x1, z0, z1, ops, System.currentTimeMillis() - t0));
    }

    private void mark(boolean[] used, Track.Grid g, int x, int z) {
        if (g.in(x, z)) used[(x - g.minX) + (z - g.minZ) * g.w] = true;
    }

    private boolean isUsed(boolean[] used, Track.Grid g, int x, int z) {
        return !g.in(x, z) || used[(x - g.minX) + (z - g.minZ) * g.w];
    }

    private static final Material[] WOOLS = {Material.RED_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.MAGENTA_WOOL, Material.ORANGE_WOOL, Material.BLUE_WOOL, Material.PINK_WOOL};

    private static Material wool(int i) {
        return WOOLS[Math.floorMod(i, WOOLS.length)];
    }

    /** Sapin : tronc d'épicéa, feuillage en cône (feuilles persistantes). */
    private void tree(int x, int z, int height) {
        int F = floorY;
        for (int y = F + 1; y <= F + height; y++) set(x, y, z, Material.SPRUCE_LOG);
        String leaves = "minecraft:spruce_leaves[persistent=true]";
        for (int y = F + 2; y <= F + height + 1; y++) {
            int fromTop = F + height + 1 - y;
            int rad = fromTop == 0 ? 0 : (fromTop % 2 == 1 ? 1 : 2);
            if (y == F + 2) rad = 2;
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx == 0 && dz == 0 && y <= F + height) continue;
                    if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (rad == 1 && Math.abs(dx) == 1 && Math.abs(dz) == 1) continue;
                    if (bw.getBlockAt(x + dx, y, z + dz).getType().isAir()) setData(x + dx, y, z + dz, leaves);
                }
        }
        set(x, F + height + 2, z, Material.SNOW_BLOCK);
    }

    private static double maxY(Track t) {
        double m = -Double.MAX_VALUE;
        for (double v : t.y) m = Math.max(m, v);
        return m;
    }

    /** Salle d'attente 16 x 16 en lobby-y : sol en damier, vitre au centre (vue sur la ligne), murs de verre. */
    private void buildLobby(int L) {
        for (int x = -8; x <= 7; x++)
            for (int z = -8; z <= 7; z++) {
                int d = Math.max(x < 0 ? -x - 1 : x, z < 0 ? -z - 1 : z);
                if (d <= 2) set(x, L, z, Material.GLASS);
                else if (d <= 6) set(x, L, z, ((x + z) & 1) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE);
                else {
                    set(x, L, z, Material.PACKED_ICE);
                    set(x, L + 1, z, Material.BLUE_ICE);
                    for (int y = L + 2; y <= L + 4; y++) set(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                    set(x, L + 5, z, Material.PACKED_ICE);
                }
                set(x, L + 6, z, d <= 6 && Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? Material.SEA_LANTERN : Material.PACKED_ICE);
            }
    }

    // ------------------------------------------------------------------ bateaux (communs)

    private boolean isOurBoat(Entity e) {
        return e instanceof Boat && e.getPersistentDataContainer().has(boatKey, PersistentDataType.STRING);
    }

    private Entity boatOf(Racer r) {
        if (r.boat == null) return null;
        Entity e = Bukkit.getEntity(r.boat);
        return e != null && e.isValid() ? e : null;
    }

    private void removeBoat(Racer r, Player p) {
        exitAllowed.add(r.id);
        if (p != null && p.isInsideVehicle()) p.leaveVehicle();
        Entity e = boatOf(r);
        if (e != null) {
            e.eject();
            e.remove();
        }
        r.boat = null;
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        ticks++;
        for (Game g : new ArrayList<>(games.values())) {
            if (g.destroyed) continue;
            g.tick();
            if (ticks % 20 == 0 && !g.destroyed) g.second();
            if (ticks % 5 == 0 && !g.destroyed) g.safety();
        }
        if (ticks % 20 == 0) reportStatus();
    }

    // ------------------------------------------------------------------ une partie

    private final class Game {
        final int n;
        /** Monde de la partie (change à la rotation) ; copy = copie à supprimer quand elle ne sert plus. */
        World world;
        boolean copy;
        /** Circuit de la partie et ses réglages (floorY = sol le plus bas du tracé). */
        Course c;
        Track track;
        int floorY, lobbyY, laps, checkpoints;
        final Set<UUID> players = new LinkedHashSet<>();
        final Map<UUID, Racer> racers = new LinkedHashMap<>();
        final Set<UUID> spectators = new LinkedHashSet<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Barrières des cases de départ (retirées au feu vert). */
        final List<Block> cages = new ArrayList<>();
        State state = State.WAITING;
        Phase phase = Phase.NONE;
        int timer, phaseTicks, grace = -1, finished;
        long raceStart;
        TextDisplay sign;
        boolean destroyed;

        Game(int n, World world, Course c, boolean copy) {
            this.n = n;
            this.world = world;
            this.copy = copy;
            setCourse(c);
        }

        void setCourse(Course c) {
            this.c = c;
            track = c.track;
            floorY = (int) Math.floor(track.minY());
            lobbyY = c.lobbyY;
            laps = c.laps();
            checkpoints = c.checkpoints();
        }

        Location lobbySpawn() {
            return EemiBoatRace.this.lobbySpawn(world, lobbyY);
        }

        Location spectatorSpot() {
            if (c.imported) {
                Location l = trackLocation(0, 0).add(0, 15, 0);
                l.setPitch(35);
                return l;
            }
            Location l = new Location(world, 0.5, floorY + 15, -24.5);
            l.setYaw(0);
            l.setPitch(35);
            return l;
        }

        Location trackLocation(double s, double lateral) {
            return EemiBoatRace.this.trackLocation(world, track, s, lateral);
        }

        /** Case de départ k : rangées de 3 blocs derrière la ligne (1 ou 2 colonnes) ; centre sur un coin de bloc (cage 2 x 2). */
        Location slotLocation(int k) {
            int cols = c.gridCols >= 2 ? 2 : 1;
            int row = k / cols;
            double lateral = cols == 1 ? 0 : (k % 2 == 0 ? -2.5 : 2.5);
            Location l = trackLocation(-(cfgD("grid-first", 4.5) + 3 * row), lateral);
            l.setX(Math.round(l.getX()));
            l.setZ(Math.round(l.getZ()));
            return l;
        }

        boolean running() {
            return state == State.PLAYING || state == State.ENDING;
        }

        void broadcast(String msg) {
            Set<UUID> all = new LinkedHashSet<>(players);
            all.addAll(spectators);
            for (Player p : online(all)) p.sendMessage(PREFIX + msg);
        }

        /** Panneau flottant de la salle d'attente (non sauvegardé, recréé s'il a disparu). */
        void ensureSign() {
            if (sign != null && sign.isValid()) return;
            Location l = new Location(world, 0.5, lobbyY + 2.8, -6.0);
            if (!l.isChunkLoaded()) return;
            sign = world.spawn(l, TextDisplay.class, td -> {
                td.text(Component.text("COURSE DE BATEAUX", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                        .append(Component.newline())
                        .append(Component.text(c.name, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.text(laps + (laps > 1 ? " tours" : " tour") + " sur la glace, le premier gagne !", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Passe tous les points de controle : pas de raccourci.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Coince ? Touche F ou objet « Revenir ».", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(false);
            });
        }

        // ---------------------------------------------------------- bateaux

        Entity spawnBoat(Location l, Racer r) {
            Entity e = world.spawnEntity(l, r.boatType, CreatureSpawnEvent.SpawnReason.CUSTOM, en -> {
                en.getPersistentDataContainer().set(boatKey, PersistentDataType.STRING, r.id.toString());
                en.setPersistent(false);
                en.setInvulnerable(true);
            });
            if (e == null || !e.isValid()) {
                getLogger().warning("Le bateau de " + r.name + " n'a pas pu apparaitre en " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                        + " (" + world.getName() + ").");
                return null;
            }
            r.boat = e.getUniqueId();
            return e;
        }

        /** Supprime tous les bateaux du jeu de ce monde (chunks chargés) ; les bateaux ne sont jamais sauvegardés. */
        void removeBoats() {
            for (Entity e : world.getEntities())
                if (isOurBoat(e)) {
                    for (Entity pass : e.getPassengers()) exitAllowed.add(pass.getUniqueId());
                    e.eject();
                    e.remove();
                }
            for (Racer r : racers.values()) r.boat = null;
        }

        /** Met le joueur dans un nouveau bateau au point l (deux ticks plus tard, le temps de la téléportation). */
        void boardAt(Player p, Racer r, Location l) {
            removeBoat(r, p);
            r.respawning = true;
            p.setFallDistance(0);
            p.teleport(l);
            Bukkit.getScheduler().runTaskLater(EemiBoatRace.this, () -> {
                if (destroyed || !p.isOnline() || racers.get(r.id) != r || state != State.PLAYING || r.finish >= 0) {
                    r.respawning = false;
                    return;
                }
                p.teleport(l);
                Entity boat = spawnBoat(l, r);
                if (boat != null) boat.addPassenger(p);
                exitAllowed.remove(r.id);
                r.respawning = false;
                r.offBoatTicks = 0;
            }, 2L);
        }

        // ---------------------------------------------------------- boucles

        void tick() {
            if (state == State.PLAYING) {
                if (phase == Phase.GRID) gridTick();
                else if (phase == Phase.RACE) raceTick();
            }
        }

        void second() {
            if (world.getTime() != cfg("time", 6000)) world.setTime(cfg("time", 6000));
            int min = Math.max(1, cfg("min-players", 2));
            switch (state) {
                case WAITING -> {
                    if (players.size() >= min) {
                        state = State.STARTING;
                        timer = cfg("countdown", 20);
                        broadcast("§eAssez de joueurs : depart dans §6" + timer + " s§e !");
                    }
                }
                case STARTING -> {
                    if (players.size() < min) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, depart annule.");
                    } else {
                        if (players.size() >= maxPlayers()) timer = Math.min(timer, cfg("full-countdown", 10));
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
                    if (phase == Phase.RACE) raceSecond();
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (destroyed) return;
            if (!players.isEmpty()) ensureSign();
            for (Player p : online(players)) updateSidebar(p);
        }

        /** Un joueur du monde qui tombe hors partie revient à la salle d'attente (ou au point spectateur). */
        void safety() {
            for (Player p : world.getPlayers()) {
                if (p.getLocation().getY() > floorY - 20) continue;
                if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
                if (racing(p.getUniqueId())) continue;
                p.setFallDistance(0);
                p.teleport(running() && players.contains(p.getUniqueId()) ? spectatorSpot() : lobbySpawn());
            }
        }

        // ---------------------------------------------------------- départ

        void startGame() {
            List<Player> list = online(players);
            if (list.isEmpty()) { state = State.WAITING; return; }
            Collections.shuffle(list, random);
            setCourse(c); // laps / checkpoints du config relus (reload)
            racers.clear();
            finished = 0;
            grace = -1;
            state = State.PLAYING;
            phase = Phase.GRID;
            phaseTicks = Math.max(3, cfg("start-lights", 5)) * 20;
            removeBoats();
            clearCages();
            List<EntityType> types = boatTypes();
            int k = 0;
            for (Player p : list) {
                Racer r = new Racer(p.getUniqueId(), p.getName());
                r.slot = k;
                r.boatType = types.get(k % types.size());
                racers.put(r.id, r);
                Location l = slotLocation(k);
                placeCage(l);
                if (mayTouch(p)) {
                    resetPlayer(p, GameMode.ADVENTURE);
                    p.getInventory().setItem(8, kit(named(new ItemStack(Material.RECOVERY_COMPASS), "Revenir au dernier point", NamedTextColor.YELLOW)));
                    p.getInventory().setHeldItemSlot(0);
                }
                boardAt(p, r, l);
                p.showTitle(Title.title(Component.text("A VOS MARQUES", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                        Component.text(laps + (laps > 1 ? " tours" : " tour") + " - attends le feu vert !", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(400))));
                k++;
            }
            setLights(Material.BLACK_CONCRETE);
            broadcast("§eDepart sur §b" + c.name + " §eavec §6" + racers.size() + " §epilote(s), §6" + laps + " §etour(s) ! §7Touche §fF §7(ou l'objet Revenir) si tu es coince.");
            reportStatus();
        }

        /**
         * Cage de barrières (anneau 4 x 4 autour de 2 x 2 cases, 2 de haut) autour d'une case de départ, seulement sur l'air.
         * Posée au-dessus du VRAI sol de la case : sur Cosmic Cove la grille n'est pas au niveau du tracé, et une cage
         * trop haute passait au-dessus des bateaux (2.1.1 : on pouvait partir avant le GO, 29/10/2026).
         */
        void placeCage(Location l) {
            int bx = l.getBlockX(), bz = l.getBlockZ();
            int base = (int) Math.floor(l.getY());
            int y = base;
            for (int sy = base + 2; sy >= base - 5; sy--) {
                if (world.getBlockAt(bx, sy, bz).getType().isSolid() || world.getBlockAt(bx - 1, sy, bz - 1).getType().isSolid()) {
                    y = sy + 1;
                    break;
                }
            }
            for (int x = bx - 2; x <= bx + 1; x++)
                for (int z = bz - 2; z <= bz + 1; z++) {
                    boolean border = x == bx - 2 || x == bx + 1 || z == bz - 2 || z == bz + 1;
                    if (!border) continue;
                    for (int dy = 0; dy <= 1; dy++) {
                        Block b = world.getBlockAt(x, y + dy, z);
                        if (b.getType().isAir()) {
                            b.setType(Material.BARRIER, false);
                            cages.add(b);
                        }
                    }
                }
        }

        /**
         * Pendant les feux, un bateau qui s'éloigne de sa case est ramené dessus (le bateau est piloté par le client :
         * la cage seule ne suffit pas si elle laisse un passage).
         */
        void holdBoats() {
            for (Racer r : racers.values()) {
                if (r.respawning || r.boat == null) continue;
                Entity boat = Bukkit.getEntity(r.boat);
                if (boat == null || !boat.isValid()) continue;
                Location slot = slotLocation(r.slot);
                Location at = boat.getLocation();
                double dx = at.getX() - slot.getX(), dz = at.getZ() - slot.getZ();
                if (dx * dx + dz * dz <= 0.25 && boat.getVelocity().lengthSquared() < 0.01) continue;
                boat.setVelocity(new org.bukkit.util.Vector());
                if (dx * dx + dz * dz > 0.25) {
                    Location back = slot.clone();
                    back.setY(at.getY());
                    back.setYaw(at.getYaw());
                    back.setPitch(0);
                    boat.teleport(back, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                }
            }
        }

        void clearCages() {
            for (Block b : cages) if (b.getType() == Material.BARRIER) b.setType(Material.AIR, false);
            cages.clear();
        }

        void setLights(Material m) {
            if (c.imported) return; // feux de l'arche : circuit procédural seulement
            for (int[] p : lightPos) world.getBlockAt(p[0], p[1], p[2]).setType(m, false);
        }

        /** Feux de départ : rouge, rouge, orange... vert. Les bateaux sont dans leurs cages. */
        void gridTick() {
            phaseTicks--;
            holdBoats();
            // un joueur qui n'est pas (encore) dans son bateau y est remis
            if (phaseTicks % 20 == 0)
                for (Racer r : racers.values()) {
                    Player p = Bukkit.getPlayer(r.id);
                    if (p == null || r.respawning) continue;
                    Entity v = p.getVehicle();
                    if (v == null || !v.getUniqueId().equals(r.boat)) boardAt(p, r, slotLocation(r.slot));
                }
            if (phaseTicks > 0 && phaseTicks % 20 == 0) {
                int sec = phaseTicks / 20;
                if (sec <= 3) {
                    setLights(sec == 1 ? Material.ORANGE_CONCRETE : Material.RED_CONCRETE);
                    for (Player p : online(racers.keySet())) {
                        p.showTitle(Title.title(Component.text(String.valueOf(sec), sec == 1 ? NamedTextColor.GOLD : NamedTextColor.RED).decorate(TextDecoration.BOLD),
                                Component.text("Prepare-toi...", NamedTextColor.GRAY), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                    }
                }
            }
            if (phaseTicks <= 0) go();
        }

        void go() {
            phase = Phase.RACE;
            clearCages();
            setLights(Material.LIME_CONCRETE);
            raceStart = System.currentTimeMillis();
            for (Racer r : racers.values()) {
                Player p = Bukkit.getPlayer(r.id);
                Location l = p != null && p.getVehicle() != null ? p.getVehicle().getLocation() : slotLocation(r.slot);
                r.idx = track.nearestGlobal(l.getX(), l.getZ());
                r.s = track.position(r.idx, l.getX(), l.getZ());
                r.dist = track.wrap(r.s);
                r.passed = 0;
                r.lapStart = raceStart;
                r.stuckRef = r.dist;
                r.stuckSec = 0;
                if (p != null) {
                    p.showTitle(Title.title(Component.text("GO !", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                            Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(300))));
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    p.playSound(p, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                }
            }
        }

        // ---------------------------------------------------------- course

        boolean racing(UUID id) {
            Racer r = racers.get(id);
            return r != null && state == State.PLAYING && r.finish < 0;
        }

        double cpPos(int g) {
            return g * track.length / checkpoints;
        }

        void raceTick() {
            for (Racer r : new ArrayList<>(racers.values())) {
                if (r.finish >= 0 || r.respawning) continue;
                Player p = Bukkit.getPlayer(r.id);
                if (p == null) continue;
                Entity v = p.getVehicle();
                if (v == null || !v.getUniqueId().equals(r.boat)) {
                    if (++r.offBoatTicks > 10) respawn(p, r, "Tu es sorti du bateau");
                    continue;
                }
                r.offBoatTicks = 0;
                Location l = v.getLocation();
                // chute : plus bas que le sol le plus bas des environs (les sauts et ascenseurs descendent à leur pied)
                if (l.getY() < track.minY(r.idx, 15) - cfg("fall-depth", 3)) { respawn(p, r, "Hors piste"); continue; }
                int ni = track.nearest(l.getX(), l.getZ(), r.idx, 12, 40);
                double lim = half + cfgD("off-track-margin", 2.5);
                // circuit importé : largeur variable (dégagements), seuls la chute et le blocage replacent
                if (!c.imported && track.distSq(ni, l.getX(), l.getZ()) > lim * lim) { respawn(p, r, "Hors piste"); continue; }
                double ns = track.position(ni, l.getX(), l.getZ());
                r.dist += track.wrap(ns - r.s);
                r.s = ns;
                r.idx = ni;
                // points de contrôle dans l'ordre (la distance ne peut avancer qu'en roulant sur la piste)
                int total = laps * checkpoints;
                while (r.passed < total && r.dist >= cpPos(r.passed + 1)) {
                    r.passed++;
                    if (r.passed % checkpoints == 0) lapDone(p, r);
                    if (r.finish >= 0) break;
                }
                if (r.finish >= 0) continue;
                if (ticks % 5 == 0) {
                    int lap = Math.min(laps, r.passed / checkpoints + 1);
                    p.sendActionBar(Component.text("Tour " + lap + "/" + laps + " - ", NamedTextColor.AQUA)
                            .append(Component.text(placeOf(r) + ordinal(placeOf(r)), NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                            .append(Component.text("  " + time(System.currentTimeMillis() - raceStart), NamedTextColor.WHITE)));
                }
            }
        }

        /** Une fois par seconde : coureurs coincés, fin de course. */
        void raceSecond() {
            double minMove = cfgD("stuck-min-distance", 3.0);
            int stuckSecs = cfg("stuck-seconds", 10);
            for (Racer r : new ArrayList<>(racers.values())) {
                if (r.finish >= 0 || r.respawning) continue;
                Player p = Bukkit.getPlayer(r.id);
                if (p == null) continue;
                if (r.dist - r.stuckRef >= minMove) {
                    r.stuckRef = r.dist;
                    r.stuckSec = 0;
                } else if (stuckSecs > 0 && track.solid[r.idx] && ++r.stuckSec >= stuckSecs) {
                    // (pas dans un saut, un ascenseur à bulles ou une zone libre : la progression y est irrégulière)
                    respawn(p, r, "Bloque");
                }
            }
            long elapsed = System.currentTimeMillis() - raceStart;
            boolean allDone = true;
            for (Racer r : racers.values()) if (r.finish < 0) { allDone = false; break; }
            if (racers.isEmpty() || allDone) { endGame(true); return; }
            if (grace > 0) {
                grace--;
                if (grace <= 10 && grace > 0)
                    for (Player p : online(racers.keySet())) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                if (grace == 0) {
                    broadcast("§cTemps ecoule !");
                    endGame(true);
                    return;
                }
            }
            if (elapsed >= cfg("max-time", 600) * 1000L) {
                broadcast("§cTemps maximal de la course atteint.");
                endGame(true);
            }
        }

        /** Replace le coureur au dernier point de contrôle passé, dans un bateau neuf, dans le sens de la course. */
        void respawn(Player p, Racer r, String reason) {
            if (r.respawning || r.finish >= 0 || phase != Phase.RACE) return;
            // jamais dans un saut, un ascenseur ou une zone libre : on recule jusqu'à la glace
            double d = track.solidBack(r.passed > 0 ? cpPos(r.passed) + 1.5 : -3);
            r.dist = d;
            r.s = track.mod(d);
            r.idx = track.index(d);
            r.stuckRef = d;
            r.stuckSec = 0;
            r.lastRespawn = System.currentTimeMillis();
            Location l = trackLocation(d, 0);
            l.setY(track.yAt(d) + 0.2);
            boardAt(p, r, l);
            p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
            p.sendActionBar(Component.text(reason + " : retour au dernier point de controle.", NamedTextColor.YELLOW));
        }

        void askRespawn(Player p) {
            Racer r = racers.get(p.getUniqueId());
            if (r == null || !racing(r.id)) return;
            if (phase != Phase.RACE) { p.sendActionBar(Component.text("Attends le feu vert !", NamedTextColor.RED)); return; }
            long cd = (long) (cfgD("respawn-cooldown", 2.0) * 1000);
            if (System.currentTimeMillis() - r.lastRespawn < cd) return;
            respawn(p, r, "Demande");
        }

        void lapDone(Player p, Racer r) {
            long now = System.currentTimeMillis();
            long lap = now - r.lapStart;
            r.lapStart = now;
            int lapNo = r.passed / checkpoints;
            boolean best = r.bestLap < 0 || lap < r.bestLap;
            if (best) r.bestLap = lap;
            checkLapRecord(r, lap);
            if (lapNo >= laps) {
                finish(p, r, now - raceStart);
                return;
            }
            p.showTitle(Title.title(Component.empty(),
                    Component.text("Tour " + (lapNo + 1) + "/" + laps, NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                            .append(Component.text("  (" + time(lap) + ")", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(300))));
            p.playSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
            if (lapNo == laps - 1) {
                p.sendMessage(PREFIX + "§6Dernier tour !");
                p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            }
        }

        void finish(Player p, Racer r, long time) {
            r.finish = time;
            r.place = ++finished;
            removeBoat(r, p);
            if (p.getWorld().equals(world)) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(spectatorSpot());
            }
            p.showTitle(Title.title(Component.text(r.place + ordinal(r.place) + " !", r.place == 1 ? NamedTextColor.GOLD : NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                    Component.text("Temps : " + time(time), NamedTextColor.WHITE)));
            p.playSound(p, r.place == 1 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            broadcast("§a" + r.name + " §7termine §6" + r.place + ordinal(r.place) + " §7en §f" + time(time) + "§7.");
            if (r.place == 1) {
                firework(trackLocation(0, 0).add(0, 3, 0));
                int g = cfg("finish-grace", 30);
                boolean others = false;
                for (Racer o : racers.values()) if (o.finish < 0) { others = true; break; }
                if (others) {
                    grace = g;
                    broadcast("§eLes autres pilotes ont §6" + g + " s §epour terminer !");
                }
            }
            checkRaceRecord(r, time);
        }

        void checkLapRecord(Racer r, long lap) {
            String key = c.recordKey() + ".lap";
            long old = records.getLong(key + ".time", -1);
            if (old >= 0 && lap >= old) return;
            records.set(key + ".time", lap);
            records.set(key + ".player", r.name);
            records.set(key + ".date", LocalDate.now().toString());
            saveRecords();
            broadcast("§d§lRecord du tour ! §r§d" + r.name + " §7en §f" + time(lap) + (old >= 0 ? " §8(ancien " + time(old) + ")" : ""));
        }

        void checkRaceRecord(Racer r, long time) {
            String key = c.recordKey() + ".race-" + laps;
            long old = records.getLong(key + ".time", -1);
            if (old >= 0 && time >= old) return;
            records.set(key + ".time", time);
            records.set(key + ".player", r.name);
            records.set(key + ".date", LocalDate.now().toString());
            saveRecords();
            Component t = Component.text("Nouveau record !", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD);
            for (Player p : online(players))
                p.showTitle(Title.title(t, Component.text(r.name + " - " + time(time), NamedTextColor.WHITE)));
            broadcast("§d§lNouveau record du circuit ! §r§d" + r.name + " §7en §f" + time(time) + (old >= 0 ? " §8(ancien " + time(old) + ")" : ""));
        }

        /** Classement : arrivés par temps, puis les autres par distance parcourue. */
        List<Racer> ranking() {
            List<Racer> list = new ArrayList<>(racers.values());
            list.sort((a, b) -> {
                if (a.finish >= 0 && b.finish >= 0) return Long.compare(a.finish, b.finish);
                if (a.finish >= 0) return -1;
                if (b.finish >= 0) return 1;
                return Double.compare(b.dist, a.dist);
            });
            return list;
        }

        int placeOf(Racer r) {
            return ranking().indexOf(r) + 1;
        }

        // ---------------------------------------------------------- fin

        void endGame(boolean rewards) {
            if (state != State.PLAYING) return;
            List<Racer> rank = ranking();
            state = State.ENDING;
            phase = Phase.NONE;
            grace = -1;
            timer = cfg("end-time", 10);
            for (Racer r : racers.values()) exitAllowed.add(r.id);
            removeBoats();
            clearCages();
            setLights(Material.BLACK_CONCRETE);
            if (rewards) {
                for (Racer r : rank) {
                    Player p = Bukkit.getPlayer(r.id);
                    if (p == null) continue;
                    eemi("reward " + p.getName() + " " + GAME + " play");
                    if (r.finish >= 0 && r.place == 1) eemi("reward " + p.getName() + " " + GAME + " win");
                }
            }
            List<String> lines = new ArrayList<>();
            lines.add("§b§m          §r §fClassement final §b§m          ");
            int i = 1;
            for (Racer r : rank) {
                String col = i == 1 ? "§6" : i == 2 ? "§f" : i == 3 ? "§c" : "§7";
                if (r.finish >= 0)
                    lines.add(col + i + ". " + r.name + " §8- §f" + time(r.finish) + (r.bestLap >= 0 ? " §8(meilleur tour " + time(r.bestLap) + ")" : ""));
                else
                    lines.add(col + i + ". " + r.name + " §8- §7non classe (tour " + Math.min(laps, r.passed / checkpoints + 1) + "/" + laps + ")");
                i++;
            }
            Racer winner = rank.isEmpty() || rank.get(0).finish < 0 ? null : rank.get(0);
            Component title = winner == null
                    ? Component.text("Course terminee", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    : Component.text(winner.name, NamedTextColor.GOLD).decorate(TextDecoration.BOLD); // pseudo seul : titre court
            Set<UUID> audience = new LinkedHashSet<>(players);
            audience.addAll(spectators);
            for (Player p : online(audience)) {
                for (String l : lines) p.sendMessage(l);
                Racer me = racers.get(p.getUniqueId());
                boolean won = winner != null && me == winner;
                Component sub = me == null ? (winner == null ? Component.empty() : Component.text("gagne la course !", NamedTextColor.YELLOW))
                        : Component.text(won ? "Tu as gagne la course !" : "Tu termines " + (rank.indexOf(me) + 1) + ordinal(rank.indexOf(me) + 1), NamedTextColor.YELLOW);
                p.showTitle(Title.title(title, sub));
                if (racers.containsKey(p.getUniqueId()) && p.getWorld().equals(world)) {
                    p.getInventory().clear();
                    p.setGameMode(GameMode.SPECTATOR);
                    p.teleport(spectatorSpot());
                }
            }
            if (winner != null)
                for (int k = 0; k < 5; k++)
                    Bukkit.getScheduler().runTaskLater(EemiBoatRace.this, () -> {
                        if (!destroyed && state == State.ENDING) firework(trackLocation(0, random.nextInt(7) - 3).add(0, 2, 0));
                    }, k * 20L);
            reportStatus();
        }

        /**
         * Retour à la salle d'attente de cette partie, sur un autre circuit si la rotation est active et qu'un
         * monde d'origine est libre ; une copie restée vide est ensuite détruite.
         */
        void backToLobby() {
            state = State.WAITING;
            phase = Phase.NONE;
            for (UUID id : racers.keySet()) exitAllowed.remove(id);
            racers.clear();
            for (Player p : online(spectators)) {
                spectating.remove(p.getUniqueId(), this);
                sendToHub(p);
                p.sendMessage(PREFIX + "§7Course terminee, retour au hub.");
            }
            for (UUID id : spectators) spectating.remove(id, this);
            spectators.clear();
            Course next = getConfig().getBoolean("rotation", true) && !players.isEmpty() ? freeCourse(c) : null;
            if (next != null) switchCourse(next);
            else for (Player p : online(players)) toWaiting(p);
            reportStatus();
            checkEmpty();
        }

        /** Passe la partie sur le monde d'origine d'un autre circuit (libre) ; l'ancienne copie est supprimée. */
        void switchCourse(Course nc) {
            World w = nc.base();
            if (w == null) {
                for (Player p : online(players)) toWaiting(p);
                return;
            }
            World old = world;
            Course oc = c;
            boolean wasCopy = copy;
            removeBoats();
            clearCages();
            if (sign != null) { sign.remove(); sign = null; }
            world = w;
            copy = false;
            setCourse(nc);
            prepareWorld(w, lobbyY);
            for (Chunk0 c0 : Chunk0.ALL) w.getChunkAt(c0.x, c0.z).load(true);
            // le monde change avant la téléportation : onWorldChange ne prend pas le départ pour un abandon
            for (Player p : online(players)) toWaiting(p);
            broadcast("§7Prochaine course : §b" + nc.name + " §7(" + laps + (laps > 1 ? " tours" : " tour") + ").");
            getLogger().info("Partie " + n + " : circuit " + oc.name + " -> " + nc.name + ".");
            if (wasCopy)
                Bukkit.getScheduler().runTaskLater(EemiBoatRace.this,
                        () -> WorldClone.destroy(EemiBoatRace.this, old, oc.worldName, EemiBoatRace.this::sendToHub), 20L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(false);
            if (state == State.ENDING) backToLobby();
            if (state == State.STARTING) state = State.WAITING;
        }

        /** Une partie 2+ vide et en attente ne sert plus : retirée (une copie est supprimée, la prochaine repartira du modèle). */
        void checkEmpty() {
            if (n == 1 || destroyed) return;
            Bukkit.getScheduler().runTask(EemiBoatRace.this, () -> {
                if (!destroyed && (state == State.WAITING || state == State.STARTING) && players.isEmpty()) destroyGame(this);
            });
        }

        // ---------------------------------------------------------- inscription

        void add(Player p) {
            UUID id = p.getUniqueId();
            Game sg = spectating.remove(id);
            if (sg != null) sg.spectators.remove(id);
            players.add(id);
            byPlayer.put(id, this);
            toWaiting(p);
            broadcast("§a" + p.getName() + " §7a rejoint la course (§f" + players.size() + "/" + maxPlayers() + "§7).");
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
                p.getInventory().setItem(8, kit(named(new ItemStack(Material.RED_BED), "Quitter la course", NamedTextColor.RED)));
                updateSidebar(p);
            });
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            byPlayer.remove(id, this);
            Racer r = racers.remove(id);
            if (r != null) {
                removeBoat(r, p);
                if (state == State.PLAYING && r.finish < 0) broadcast("§c" + p.getName() + " §7a abandonne.");
            }
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // sans téléportation (le joueur a changé de monde) : on ne touche plus au joueur, Multiverse-Inventories
            // a déjà sauvegardé l'inventaire du jeu et chargé celui du monde d'arrivée
            if (teleport) sendToHub(p);
            exitAllowed.remove(id);
            if (state == State.PLAYING && racers.isEmpty()) endGame(false);
            reportStatus();
            checkEmpty();
        }

        void addSpectator(Player p) {
            spectators.add(p.getUniqueId());
            spectating.put(p.getUniqueId(), this);
            // téléporter d'abord (Multiverse-Inventories sauvegarde l'inventaire du monde quitté), puis vider
            p.teleport(spectatorSpot());
            if (!p.getWorld().equals(world)) {
                spectators.remove(p.getUniqueId());
                spectating.remove(p.getUniqueId(), this);
                return;
            }
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage(PREFIX + "§7Tu regardes la course " + n + ", §f/lobby §7pour revenir.");
        }

        int waitingCount() {
            int w = 0;
            for (UUID id : players) {
                Racer r = racers.get(id);
                if (!running() || r == null) w++;
            }
            return w;
        }

        int playingCount() {
            int c = 0;
            for (UUID id : players) {
                Racer r = racers.get(id);
                if (running() && r != null && (state == State.ENDING || r.finish < 0)) c++;
            }
            return c;
        }

        // ---------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("br", Criteria.DUMMY,
                        Component.text("COURSE ", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                                .append(Component.text("DE BATEAUX", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("br");
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
            Racer me = racers.get(p.getUniqueId());
            if (me == null || state != State.PLAYING) {
                l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("#" + n, NamedTextColor.WHITE)));
                l.add(Component.text("Circuit : ", NamedTextColor.GRAY).append(Component.text(c.name, NamedTextColor.AQUA)));
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + maxPlayers(), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("Tours : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(laps), NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Depart dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.AQUA)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else if (state == State.ENDING) l.add(Component.text("Fin de course", NamedTextColor.GOLD));
                else {
                    l.add(Component.text("Course en cours...", NamedTextColor.YELLOW));
                    l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
                }
            } else {
                List<Racer> rank = ranking();
                int lap = Math.min(laps, me.passed / checkpoints + 1);
                int pos = rank.indexOf(me) + 1;
                long now = System.currentTimeMillis();
                long el = phase == Phase.RACE ? now - raceStart : 0;
                l.add(Component.text("Tour : ", NamedTextColor.GRAY).append(Component.text(me.finish >= 0 ? "arrive" : lap + "/" + laps, NamedTextColor.WHITE)));
                l.add(Component.text("Position : ", NamedTextColor.GRAY).append(Component.text(pos + ordinal(pos) + "/" + rank.size(), NamedTextColor.GOLD)));
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(time(me.finish >= 0 ? me.finish : el), NamedTextColor.WHITE)));
                l.add(Component.text("Meilleur tour : ", NamedTextColor.GRAY).append(Component.text(me.bestLap >= 0 ? time(me.bestLap) : "-", NamedTextColor.AQUA)));
                if (grace > 0) l.add(Component.text("Fin dans ", NamedTextColor.GRAY).append(Component.text(grace + " s", NamedTextColor.RED)));
                l.add(Component.text("  "));
                int shown = 0;
                for (Racer r : rank) {
                    if (shown >= 6) break;
                    shown++;
                    String info = r.finish >= 0 ? time(r.finish) : "T" + Math.min(laps, r.passed / checkpoints + 1);
                    NamedTextColor c = r == me ? NamedTextColor.YELLOW : NamedTextColor.WHITE;
                    l.add(Component.text(shown + ". ", NamedTextColor.GRAY).append(Component.text(r.name, c))
                            .append(Component.text(" " + info, NamedTextColor.DARK_GRAY)));
                }
            }
            l.add(Component.text("   "));
            l.add(recordLine(this));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }
    }

    // ------------------------------------------------------------------ parties : recherche, création, destruction

    private Game gameOfWorld(World w) {
        if (w == null) return null;
        for (Game g : games.values()) if (!g.destroyed && g.world.equals(w)) return g;
        return null;
    }

    /** Monde protégé : monde d'une partie, monde d'origine d'un circuit, copie en cours de chargement ou modèle. */
    private boolean isGameWorld(World w) {
        if (w == null) return false;
        if (gameOfWorld(w) != null) return true;
        for (Course c : courses.values()) if (c.owns(w.getName())) return true;
        return false;
    }

    /** Un circuit au hasard dont le monde d'origine est chargé et sans partie (sauf « except »), ou null. */
    private Course freeCourse(Course except) {
        List<Course> list = new ArrayList<>();
        for (Course c : courses.values()) {
            if (c == except) continue;
            World w = c.base();
            if (w == null || gameOfWorld(w) != null) continue;
            list.add(c);
        }
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    private boolean racing(UUID id) {
        Game g = byPlayer.get(id);
        return g != null && g.racing(id);
    }

    /** /boatrace join : course en attente la plus remplie, sinon une nouvelle copie, sinon refus. */
    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (byPlayer.containsKey(id) || pendingOf.containsKey(id)) { p.sendMessage(PREFIX + "§7Tu es deja inscrit."); return; }
        if (games.get(1) == null) { p.sendMessage(PREFIX + "§cLe monde de la course n'est pas charge."); return; }
        int max = maxPlayers();
        Game best = null;
        for (Game g : games.values()) {
            if (g.destroyed || (g.state != State.WAITING && g.state != State.STARTING) || g.players.size() >= max) continue;
            if (best == null || g.players.size() > best.players.size()) best = g;
        }
        if (best != null) {
            best.add(p);
            return;
        }
        // une copie en préparation qui a encore de la place (groupes : on reste ensemble)
        for (Map.Entry<Integer, List<UUID>> c : creating.entrySet()) {
            if (c.getValue().size() >= max) continue;
            c.getValue().add(id);
            pendingOf.put(id, c.getKey());
            p.sendMessage(PREFIX + "§7Preparation d'une nouvelle partie...");
            reportStatus();
            return;
        }
        if (games.size() + creating.size() >= maxInstances()) { p.sendMessage(PREFIX + "§cToutes les parties sont pleines."); return; }
        int n = 2;
        while (games.containsKey(n) || creating.containsKey(n)) n++;
        // un circuit dont le monde d'origine est libre : nouvelle partie tout de suite, sans copie
        Course free = freeCourse(null);
        if (free != null) {
            Game g = new Game(n, free.base(), free, false);
            games.put(n, g);
            prepareWorld(g.world, g.lobbyY);
            for (Chunk0 c0 : Chunk0.ALL) g.world.getChunkAt(c0.x, c0.z).load(true);
            g.add(p);
            reportStatus();
            return;
        }
        List<Course> ready = new ArrayList<>();
        for (Course c : courses.values()) if (c.model != null) ready.add(c);
        if (ready.isEmpty()) { p.sendMessage(PREFIX + "§cLes parties ne sont pas encore pretes, reessaie dans un instant."); return; }
        Course cc = ready.get(random.nextInt(ready.size()));
        List<UUID> list = new ArrayList<>();
        list.add(id);
        creating.put(n, list);
        creatingCourse.put(n, cc);
        pendingOf.put(id, n);
        p.sendMessage(PREFIX + "§7Preparation d'une nouvelle partie...");
        reportStatus();
        int num = n;
        WorldClone.create(this, cc.model, WorldClone.name(cc.worldName, num), w -> {
            creatingCourse.remove(num);
            List<UUID> waiting = creating.remove(num);
            if (waiting == null) waiting = List.of();
            if (w == null || !isEnabled()) {
                for (UUID u : waiting) {
                    if (!pendingOf.remove(u, num)) continue;
                    Player pl = Bukkit.getPlayer(u);
                    if (pl != null) pl.sendMessage(PREFIX + "§cImpossible de preparer une nouvelle partie.");
                }
                reportStatus();
                return;
            }
            Game g = new Game(num, w, cc, true);
            games.put(num, g);
            prepareWorld(w, g.lobbyY);
            for (Chunk0 c0 : Chunk0.ALL) w.getChunkAt(c0.x, c0.z).load(true);
            for (UUID u : waiting) {
                if (!pendingOf.remove(u, num)) continue;
                Player pl = Bukkit.getPlayer(u);
                if (pl != null && g.players.size() < max) g.add(pl);
            }
            if (g.players.isEmpty()) destroyGame(g);
            reportStatus();
        });
    }

    /** Chunks de la salle d'attente (autour de 0,0). */
    private record Chunk0(int x, int z) {
        static final List<Chunk0> ALL = List.of(new Chunk0(-1, -1), new Chunk0(-1, 0), new Chunk0(0, -1), new Chunk0(0, 0));
    }

    private void destroyGame(Game g) {
        if (g.n == 1 || g.destroyed) return;
        for (UUID id : g.players) exitAllowed.add(id);
        g.removeBoats();
        g.clearCages();
        if (g.sign != null) g.sign.remove();
        g.destroyed = true;
        games.remove(g.n);
        for (Player p : online(g.spectators)) {
            spectating.remove(p.getUniqueId(), g);
            sendToHub(p);
        }
        for (UUID id : g.spectators) spectating.remove(id, g);
        g.spectators.clear();
        for (Player p : online(g.players)) {
            byPlayer.remove(p.getUniqueId(), g);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            sendToHub(p);
        }
        for (UUID id : g.players) byPlayer.remove(id, g);
        g.players.clear();
        g.racers.clear();
        if (g.copy) {
            WorldClone.destroy(this, g.world, g.c.worldName, this::sendToHub);
            getLogger().info("Partie " + g.n + " terminee, copie " + g.world.getName() + " supprimee.");
        } else getLogger().info("Partie " + g.n + " terminee (" + g.c.name + ").");
        reportStatus();
    }

    /** Retire un joueur d'une copie en préparation ; true s'il y était. */
    private boolean cancelPending(UUID id) {
        Integer n = pendingOf.remove(id);
        if (n == null) return false;
        List<UUID> l = creating.get(n);
        if (l != null) l.remove(id);
        reportStatus();
        return true;
    }

    // ------------------------------------------------------------------ joueurs

    private List<EntityType> boatTypes() {
        List<EntityType> out = new ArrayList<>();
        for (String s : getConfig().getStringList("boat-types")) {
            try {
                EntityType t = EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT));
                if (t.getEntityClass() != null && Boat.class.isAssignableFrom(t.getEntityClass())) out.add(t);
                else getLogger().warning("boat-types : " + s + " n'est pas un bateau.");
            } catch (IllegalArgumentException e) {
                getLogger().warning("boat-types : type inconnu " + s);
            }
        }
        if (out.isEmpty()) out.add(EntityType.OAK_BOAT);
        return out;
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l, Firework.class);
        FireworkMeta m = fw.getFireworkMeta();
        m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.AQUA, Color.WHITE).withFade(Color.BLUE).flicker(true).build());
        m.setPower(1);
        fw.setFireworkMeta(m);
    }

    private void sendToHub(Player p) {
        Game sg = spectating.remove(p.getUniqueId());
        if (sg != null) sg.spectators.remove(p.getUniqueId());
        // ailleurs que dans un monde du jeu ou au hub (skyblock...) : ni téléportation ni remise à zéro.
        // Dans un monde du jeu, la remise à zéro touche l'inventaire de CE monde (Multiverse-Inventories
        // charge ensuite celui du hub).
        if (!mayTouch(p)) return;
        exitAllowed.add(p.getUniqueId());
        if (p.isInsideVehicle()) p.leaveVehicle();
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    private World hubWorld() {
        return Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
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

    /** /boatrace spectate [joueur] : la course de ce joueur, sinon la course en cours la plus remplie. */
    private void spectate(Player p, String targetName) {
        if (byPlayer.containsKey(p.getUniqueId()) || pendingOf.containsKey(p.getUniqueId())) {
            p.sendMessage(PREFIX + "§cTu es inscrit : §f/boatrace leave §cd'abord.");
            return;
        }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            if (t == null) { p.sendMessage(PREFIX + "§cJoueur introuvable."); return; }
            g = byPlayer.get(t.getUniqueId());
            if (g == null || g.state != State.PLAYING) { p.sendMessage(PREFIX + "§c" + t.getName() + " n'est pas dans une course en cours."); return; }
        } else {
            for (Game c : games.values())
                if (!c.destroyed && c.state == State.PLAYING && (g == null || c.racers.size() > g.racers.size())) g = c;
            if (g == null) { p.sendMessage(PREFIX + "§cAucune course en cours."); return; }
        }
        Game old = spectating.remove(p.getUniqueId());
        if (old != null) old.spectators.remove(p.getUniqueId());
        g.addSpectator(p);
    }

    private Component recordLine(Game g) {
        String key = g.c.recordKey() + ".race-" + g.laps;
        long t = records.getLong(key + ".time", -1);
        if (t < 0) return Component.text("Record : ", NamedTextColor.GRAY).append(Component.text("aucun", NamedTextColor.DARK_GRAY));
        return Component.text("Record : ", NamedTextColor.GRAY).append(Component.text(time(t), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" " + records.getString(key + ".player", "?"), NamedTextColor.WHITE));
    }

    // ------------------------------------------------------------------ objets et textes

    private ItemStack named(ItemStack it, String name, NamedTextColor color) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
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

    private static String ordinal(int n) {
        return n == 1 ? "er" : "e";
    }

    /** Temps m:ss.mmm */
    private static String time(long ms) {
        if (ms < 0) ms = 0;
        return String.format(Locale.ROOT, "%d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000);
    }

    // ------------------------------------------------------------------ événements

    private boolean inGameWorld(Player p) {
        return isGameWorld(p.getWorld());
    }

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemiboatrace.admin");
    }

    /** WorldGuard peut bloquer l'apparition d'entités par plugin : on rétablit nos seuls bateaux. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (isOurBoat(e.getEntity())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleCreate(VehicleCreateEvent e) {
        if (isOurBoat(e.getVehicle())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDamage(VehicleDamageEvent e) {
        if (isOurBoat(e.getVehicle())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        if (isOurBoat(e.getVehicle())) e.setCancelled(true);
    }

    /** Seul le propriétaire monte dans son bateau. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (!isOurBoat(e.getVehicle())) return;
        String owner = e.getVehicle().getPersistentDataContainer().get(boatKey, PersistentDataType.STRING);
        if (owner == null || !owner.equals(e.getEntered().getUniqueId().toString())) e.setCancelled(true);
    }

    /** On ne descend pas de son bateau pendant la course (sauf replacement / téléportation ailleurs). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleExit(VehicleExitEvent e) {
        UUID id = e.getExited().getUniqueId();
        if (racing(id) && !exitAllowed.contains(id) && e.isCancellable()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDismount(EntityDismountEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (racing(id) && !exitAllowed.contains(id) && e.isCancellable()) e.setCancelled(true);
    }

    /**
     * Une commande tapée pendant la course (/lobby, /hub, /mv tp...) peut téléporter le joueur avant que
     * l'événement de téléportation arrive (selon l'ordre de Paper, non vérifié) : la descente est autorisée
     * pour ce tick, sinon l'annulation de la descente pourrait bloquer la téléportation.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Game g = byPlayer.get(id);
        if (g == null || !g.racers.containsKey(id)) return;
        String cmd = e.getMessage().toLowerCase();
        if (cmd.startsWith("/boatrace") || cmd.startsWith("/course") || cmd.startsWith("/br ") || cmd.equals("/br")) return;
        exitAllowed.add(id);
        Bukkit.getScheduler().runTask(this, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !g.destroyed && p.getWorld().equals(g.world) && g.racers.containsKey(id)) exitAllowed.remove(id);
        });
    }

    /** Téléporté hors du monde de sa partie (/lobby, /tp...) : on le laisse descendre du bateau. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Game g = byPlayer.get(e.getPlayer().getUniqueId());
        if (g != null && g.racers.containsKey(e.getPlayer().getUniqueId()) && e.getTo().getWorld() != null && !e.getTo().getWorld().equals(g.world))
            exitAllowed.add(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && (inGameWorld(p) || byPlayer.containsKey(p.getUniqueId()))) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && (inGameWorld(p) || byPlayer.containsKey(p.getUniqueId()))) e.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (inGameWorld(p) && !admin(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK) e.setCancelled(true);
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        if (it == null || !isKit(it)) return;
        e.setCancelled(true);
        if (it.getType() == Material.RED_BED
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitte la course.");
        } else if (it.getType() == Material.RECOVERY_COMPASS && e.getAction() != Action.PHYSICAL) {
            g.askRespawn(p);
        }
    }

    /** Touche F (échanger les mains) = revenir au dernier point. */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = byPlayer.get(e.getPlayer().getUniqueId());
        if (g == null) return;
        e.setCancelled(true);
        g.askRespawn(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (byPlayer.containsKey(e.getPlayer().getUniqueId()) || isKit(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && byPlayer.containsKey(p.getUniqueId()) && !admin(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player sp = e.getPlayer();
        Game sg = spectating.get(sp.getUniqueId());
        if (sg != null && !sp.getWorld().equals(sg.world)) {
            spectating.remove(sp.getUniqueId());
            sg.spectators.remove(sp.getUniqueId());
            if (sp.getGameMode() == GameMode.SPECTATOR && mayTouch(sp)) sp.setGameMode(GameMode.SURVIVAL);
        }
        Game g = byPlayer.get(sp.getUniqueId());
        if (g != null && e.getFrom().equals(g.world) && !sp.getWorld().equals(g.world)) g.leave(sp, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        exitAllowed.add(p.getUniqueId());
        cancelPending(p.getUniqueId());
        if (spectating.containsKey(p.getUniqueId())) sendToHub(p);
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null) g.leave(p, true);
        exitAllowed.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ commandes

    /** Partie visée par une commande admin : numéro donné, sinon celle de l'admin, sinon la partie 1. */
    private Game target(CommandSender sender, String[] args) {
        if (args.length > 1) {
            try {
                Game g = games.get(Integer.parseInt(args[1]));
                if (g == null) sender.sendMessage(PREFIX + "§cPartie " + args[1] + " inexistante.");
                return g;
            } catch (NumberFormatException ignored) {
                sender.sendMessage(PREFIX + "§cNumero de partie invalide.");
                return null;
            }
        }
        if (sender instanceof Player p) {
            Game g = byPlayer.get(p.getUniqueId());
            if (g == null) g = spectating.get(p.getUniqueId());
            if (g == null) g = gameOfWorld(p.getWorld());
            if (g != null) return g;
        }
        Game g = games.get(1);
        if (g == null) sender.sendMessage(PREFIX + "§cMonde introuvable.");
        return g;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemiboatrace.admin");
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
                if (spectating.containsKey(p.getUniqueId())) { sendToHub(p); return true; }
                if (cancelPending(p.getUniqueId())) { p.sendMessage(PREFIX + "§7Tu as quitte la course."); return true; }
                Game g = byPlayer.get(p.getUniqueId());
                if (g == null) return true;
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitte la course.");
            }
            case "record", "records" -> {
                for (Course c : courses.values()) {
                    String key = c.recordKey();
                    int l = c.laps();
                    long race = records.getLong(key + ".race-" + l + ".time", -1), lap = records.getLong(key + ".lap.time", -1);
                    sender.sendMessage(PREFIX + "§b" + c.name + " §7- " + l + (l > 1 ? " tours : " : " tour : ")
                            + (race < 0 ? "§8aucun" : "§d" + time(race) + " §f" + records.getString(key + ".race-" + l + ".player"))
                            + " §7- tour : " + (lap < 0 ? "§8aucun" : "§d" + time(lap) + " §f" + records.getString(key + ".lap.player")));
                }
            }
            case "start" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) return true;
                if (g.running()) { sender.sendMessage(PREFIX + "§cLa course " + g.n + " est deja en cours."); return true; }
                if (g.players.isEmpty()) { sender.sendMessage(PREFIX + "§cPersonne n'est inscrit (partie " + g.n + ")."); return true; }
                g.startGame();
                sender.sendMessage(PREFIX + "§aCourse " + g.n + " lancee.");
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) return true;
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Course " + g.n + " arretee.");
            }
            case "build" -> {
                if (!admin) return true;
                int done = 0;
                for (Course c : courses.values()) {
                    World w = c.base();
                    if (w == null) continue;
                    Game g = gameOfWorld(w);
                    if (g != null && g.running()) { sender.sendMessage(PREFIX + "§c" + c.name + " : course en cours, ignore."); continue; }
                    build(w, c);
                    if (g != null) for (Player pl : online(g.players)) g.toWaiting(pl);
                    makeModel(c);
                    done++;
                }
                sender.sendMessage(PREFIX + "§a" + done + " circuit(s) prepare(s), modeles refaits.");
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.running()) { sender.sendMessage(PREFIX + "§cImpossible pendant une course."); return true; }
                loadSettings();
                loadRecords();
                for (Game g : games.values()) {
                    prepareWorld(g.world, g.lobbyY);
                    g.setCourse(g.c);
                    if (g.sign != null) { g.sign.remove(); g.sign = null; }
                }
                sender.sendMessage(PREFIX + "§aConfig rechargee. §7(liste des circuits et circuit procedural : au redemarrage)");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                StringBuilder models = new StringBuilder();
                for (Course c : courses.values()) models.append(" §f").append(c.id).append(c.model != null ? " §apret" : " §cabsent");
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ", modeles" + models + "§7) :");
                for (Game g : games.values())
                    sender.sendMessage("§7 #" + g.n + " §f" + g.world.getName() + " §7(" + g.c.name + ") - " + g.state.name().toLowerCase(Locale.ROOT)
                            + " - §f" + g.players.size() + " §7inscrits, §f" + g.playingCount() + " §7en course, §f"
                            + g.spectators.size() + " §7spectateurs");
                for (Map.Entry<Integer, List<UUID>> c : creating.entrySet())
                    sender.sendMessage("§7 #" + c.getKey() + " §f" + WorldClone.name(creatingCourse.get(c.getKey()).worldName, c.getKey()) + " §7- preparation - §f"
                            + c.getValue().size() + " §7en attente");
            }
            default -> sender.sendMessage(PREFIX + "§7/boatrace <join|leave|spectate [joueur]|record>"
                    + (admin ? " §8| admin : start [n], stop [n], build, reload, parties" : ""));
        }
        return true;
    }

    // ------------------------------------------------------------------ EemiCore

    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /** Statut agrégé de toutes les parties pour EemiCore : envoyé s'il a changé (ou toutes les 30 s). */
    private void reportStatus() {
        boolean anyWaiting = games.isEmpty(), anyPlaying = false;
        int waiting = 0, playing = 0;
        for (Game g : games.values()) {
            if (g.destroyed) continue;
            if (g.state == State.WAITING || g.state == State.STARTING) anyWaiting = true;
            else anyPlaying = true;
            waiting += g.waitingCount();
            playing += g.playingCount();
        }
        for (List<UUID> l : creating.values()) {
            waiting += l.size();
            anyWaiting = true;
        }
        String st = anyWaiting ? "waiting" : anyPlaying ? "playing" : "waiting";
        String status = "status " + GAME + " " + st + " " + waiting + " " + playing + " " + (maxPlayers() * maxInstances());
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

    private List<Player> online(Collection<UUID> ids) {
        List<Player> list = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) list.add(p);
        }
        return list;
    }
}
