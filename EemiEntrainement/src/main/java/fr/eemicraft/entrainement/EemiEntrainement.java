package fr.eemicraft.entrainement;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

/**
 * Entrainement solo : bridge (d'une ile a l'autre, blocs infinis, chrono et records)
 * et knockback (un entraineur te pousse dans le vide, rattrape-toi avec des blocs).
 * Une piste par joueur dans le monde vide « entrainement », creee a l'entree, effacee a la sortie.
 */
public final class EemiEntrainement extends JavaPlugin implements Listener {
    static final String P = "&6&l[Entrainement] &r";
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    static final String[] STYLES = {"droit", "diagonale", "montee"};
    static final String[] STYLE_NAMES = {"Droit", "Diagonale", "Montee"};
    static final String[] DIFFS = {"facile", "moyen", "difficile"};
    static final String[] DIFF_NAMES = {"&aFacile", "&eMoyen", "&cDifficile"};
    static final String[] TYPES = {"WOOL", "TERRACOTTA", "CONCRETE"};
    static final String[] TYPE_NAMES = {"Laine", "Terre cuite", "Beton"};
    static final String[] COLORS = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
        "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"};
    static final String[] COLOR_NAMES = {"blanc", "orange", "magenta", "bleu clair", "jaune", "vert clair", "rose", "gris",
        "gris clair", "cyan", "violet", "bleu", "marron", "vert", "rouge", "noir"};

    /** Objets du MLG : seau d'eau, toile d'araignee, echelle (comme le MLG de Lunar / Hypixel). */
    static final String[] MLG_ITEMS = {"eau", "toile", "echelle"};
    static final String[] MLG_NAMES = {"Seau d'eau", "Toile d'araignee", "Echelle"};
    static final Material[] MLG_MATS = {Material.WATER_BUCKET, Material.COBWEB, Material.LADDER};

    enum Mode { BRIDGE, KNOCKBACK, MLG, PEARL }

    enum Kb { WAIT, AIR, RESULT }

    /** Une piste (emplacement dans le monde). */
    static final class Lane {
        final int slot;
        Islands.Layout layout;
        final Set<Long> built = new HashSet<>();
        final Set<Long> placed = new HashSet<>();
        int x1, y1, z1, x2, y2, z2;
        Mannequin npc;
        final Set<Long> tickets = new HashSet<>();

        Lane(int slot) {
            this.slot = slot;
        }

        boolean inBox(int x, int y, int z) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }

    /** Session d'entrainement d'un joueur. */
    static final class Session {
        final UUID id;
        String name;
        Mode mode;
        Lane lane;
        long enterMs;
        int dist;
        String style;
        String diff;
        // bridge
        long startMs;
        int resetIn = -1;
        boolean attemptCounted;
        // knockback
        Kb kb = Kb.WAIT;
        int wait;
        int warnAt = -1;
        int air;
        int stable;
        int onPlatform;
        int doubleIn = -1;
        int resultIn;
        int streak;
        int ok;
        int fail;
        long lastClick;
        Vector lastPos;
        // mlg
        int height;
        String mlgItem;
        boolean falling;
        boolean saved;
        int savedTicks;
        /** Plus haut point des pieds depuis le debut de la chute, y du tick precedent, descente du tick precedent. */
        double peakY;
        double lastY = Double.NaN;
        final double[] drops = new double[4];
        int dropIdx;
        // perle
        boolean pearlThrown;
        boolean pearlLanded;
        /** Difficulte du mode perle (1.2.0), distincte de celle du knockback. */
        String pearlDiff = "facile";
        /** Ticks passes au sol hors de la cible (difficile : ponton seulement). */
        int offTarget;

        Session(UUID id) {
            this.id = id;
        }
    }

    /** Donnees d'un joueur (records, reglages). */
    static final class PData {
        final YamlConfiguration y;
        boolean broken;
        boolean dirty;

        PData(YamlConfiguration y) {
            this.y = y;
        }
    }

    /** Menus. */
    static final class Menu implements InventoryHolder {
        final String type;
        Inventory inv;

        Menu(String type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Classement : cle (bridge.droit.32 / kb.facile) -> joueur -> (nom, valeur). */
    final Map<String, Map<UUID, Object[]>> board = new HashMap<>();
    final Map<UUID, Session> sessions = new HashMap<>();
    final Map<UUID, PData> pdata = new HashMap<>();
    final Map<Integer, Lane> lanes = new HashMap<>();
    /** Derniere recompense par joueur, aussi en memoire (un fichier illisible n'est jamais reecrit). */
    final Map<UUID, Long> lastReward = new HashMap<>();
    World world;
    Noise noise;
    NamespacedKey itemKey;
    NamespacedKey npcKey;
    private final Map<String, BlockData> cache = new HashMap<>();
    int invalidStates;
    long tickCount;

    static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        s.sendMessage(c(P + m));
    }

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "item");
        npcKey = new NamespacedKey(this, "npc");
        noise = new Noise(getConfig().getLong("seed", 20261116L));
        createWorld();
        cleanupAfterCrash();
        verifyStates();
        loadBoard();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::flushAll, 1200L, 1200L);
        sendStatus();
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                // pas de /lobby pendant l'arret (les autres plugins s'arretent aussi) : teleportation directe
                leave(p, false);
                if (ours(p.getWorld())) {
                    p.teleport(hub());
                }
            } else {
                Session s = sessions.remove(id);
                if (s != null) {
                    freeLane(s.lane);
                }
            }
        }
        for (Lane l : new ArrayList<>(lanes.values())) {
            freeLane(l);
        }
        flushAll();
    }

    static final class Empty extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }

    private void createWorld() {
        String name = getConfig().getString("world", "entrainement");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
            if (world != null) {
                getLogger().info("Monde " + name + " charge.");
            }
        }
        if (world == null) {
            getLogger().severe("Impossible de creer le monde " + name);
            return;
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setSpawnLocation(new Location(world, 0.5, baseY() + 1, 0.5));
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
    }

    int baseY() {
        return Math.max(40, Math.min(250, getConfig().getInt("base-y", 100)));
    }

    int spacing() {
        return Math.max(160, getConfig().getInt("lane-spacing", 200));
    }

    boolean ours(World w) {
        return world != null && world.equals(w);
    }

    // ================================================================ pistes : pose / effacement

    BlockData data(String s) {
        return cache.computeIfAbsent(s, k -> {
            try {
                return Bukkit.createBlockData(k);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bloc invalide ignore : " + k);
                invalidStates++;
                return Bukkit.createBlockData(Material.AIR);
            }
        });
    }

    /** Dessine une piste de chaque sorte en memoire et verifie tous les etats de bloc. */
    void verifyStates() {
        Set<String> states = new HashSet<>();
        for (String st : STYLES) {
            states.addAll(Islands.bridge(noise, 0, baseY(), 100, st, incline()).rec.blocks.values());
        }
        states.addAll(Islands.knockback(noise, 0, baseY()).rec.blocks.values());
        for (int h : heights()) {
            states.addAll(Islands.mlg(noise, 0, baseY(), h).rec.blocks.values());
        }
        invalidStates = 0;
        for (String s : states) {
            data(s);
        }
        getLogger().info("Etats de blocs des pistes verifies : " + states.size() + ", invalides : " + invalidStates + ".");
    }

    double incline() {
        return Math.max(0, Math.min(1, getConfig().getDouble("bridge.incline", 0.25)));
    }

    int freeSlot() {
        for (int i = 0; ; i++) {
            if (!lanes.containsKey(i)) {
                return i;
            }
        }
    }

    /** Pose la piste de la session (dans son emplacement, en effacant l'ancienne piste s'il y en a une). */
    Lane buildLane(Session s, int slot) {
        Lane old = lanes.get(slot);
        if (old != null) {
            freeLane(old);
        }
        Lane l = new Lane(slot);
        int ox = slot * spacing();
        int y = baseY();
        l.layout = switch (s.mode) {
            case BRIDGE -> Islands.bridge(noise, ox, y, s.dist, s.style, incline());
            case MLG -> Islands.mlg(noise, ox, y, s.height);
            default -> Islands.knockback(noise, ox, y);
        };
        Islands.Rec r = l.layout.rec;
        int margin = switch (s.mode) {
            case BRIDGE -> 6;
            case MLG -> 8;
            case PEARL -> 32;
            default -> 24;
        };
        l.x1 = r.minX - margin;
        l.x2 = r.maxX + margin;
        l.z1 = r.minZ - margin;
        l.z2 = r.maxZ + switch (s.mode) {
            case BRIDGE, MLG -> margin;
            case PEARL -> 70;
            default -> 40;
        };
        int fall = fall(s);
        // perle : sous fall-limit, une perle encore en vol garde sa chance ; y1 est la limite absolue (tickKb)
        l.y1 = Math.max(world.getMinHeight() + 1, l.layout.lowTop - fall - 2 - (s.mode == Mode.PEARL ? 48 : 0));
        l.y2 = Math.min(world.getMaxHeight() - 1, l.layout.highTop + 14);
        for (int cx = l.x1 >> 4; cx <= l.x2 >> 4; cx++) {
            for (int cz = l.z1 >> 4; cz <= l.z2 >> 4; cz++) {
                world.addPluginChunkTicket(cx, cz, this);
                l.tickets.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
            }
        }
        for (Map.Entry<Long, String> e : r.blocks.entrySet()) {
            long k = e.getKey();
            world.getBlockAt(Islands.kx(k), Islands.ky(k), Islands.kz(k)).setBlockData(data(e.getValue()), false);
            l.built.add(k);
        }
        lanes.put(slot, l);
        saveLanes();
        return l;
    }

    /** Efface tout ce que la piste a pose (ile, decor, blocs du joueur) et le PNJ. */
    void freeLane(Lane l) {
        if (l == null) {
            return;
        }
        removeNpc(l);
        clearPlaced(l);
        for (long k : l.built) {
            Block b = world.getBlockAt(Islands.kx(k), Islands.ky(k), Islands.kz(k));
            if (!b.getType().isAir()) {
                b.setType(Material.AIR, false);
            }
        }
        l.built.clear();
        for (long t : l.tickets) {
            world.removePluginChunkTicket((int) (t >> 32), (int) t, this);
        }
        l.tickets.clear();
        if (lanes.get(l.slot) == l) {
            lanes.remove(l.slot);
        }
        saveLanes();
    }

    void clearPlaced(Lane l) {
        for (long k : l.placed) {
            Block b = world.getBlockAt(Islands.kx(k), Islands.ky(k), Islands.kz(k));
            if (!b.getType().isAir()) {
                b.setType(Material.AIR, false);
            }
        }
        l.placed.clear();
    }

    /** Pistes en cours, notees pour tout effacer au demarrage apres un arret brutal. */
    void saveLanes() {
        YamlConfiguration y = new YamlConfiguration();
        for (Lane l : lanes.values()) {
            y.set("lanes." + l.slot, List.of(l.x1, l.y1, l.z1, l.x2, l.y2, l.z2));
        }
        writeAtomic(y, new File(getDataFolder(), "pistes.yml"));
    }

    void cleanupAfterCrash() {
        if (world == null) {
            return;
        }
        File f = new File(getDataFolder(), "pistes.yml");
        if (!f.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection cs = y.getConfigurationSection("lanes");
        if (cs == null) {
            return;
        }
        int n = 0;
        long t0 = System.currentTimeMillis();
        for (String k : cs.getKeys(false)) {
            List<Integer> b = cs.getIntegerList(k);
            if (b.size() != 6) {
                continue;
            }
            int y1 = Math.max(world.getMinHeight(), b.get(1));
            int y2 = Math.min(world.getMaxHeight() - 1, b.get(4));
            for (int x = b.get(0); x <= b.get(3); x++) {
                for (int z = b.get(2); z <= b.get(5); z++) {
                    for (int yy = y1; yy <= y2; yy++) {
                        Block bl = world.getBlockAt(x, yy, z);
                        if (!bl.getType().isAir()) {
                            bl.setType(Material.AIR, false);
                            n++;
                        }
                    }
                }
            }
        }
        saveLanes();
        getLogger().info("Pistes laissees par un arret brutal effacees : " + cs.getKeys(false).size() + " piste(s), " + n + " blocs, " + (System.currentTimeMillis() - t0) + " ms.");
    }

    // ================================================================ PNJ

    void spawnNpc(Session s, Player owner) {
        Lane l = s.lane;
        removeNpc(l);
        Islands.Layout lay = l.layout;
        Location loc = new Location(world, lay.npcX, lay.npcY, lay.npcZ, lay.npcYaw, 0f);
        String skin = getConfig().getString("knockback.npc-skin", "");
        String name = getConfig().getString("knockback.npc-name", "&cEntraineur");
        try {
            l.npc = world.spawn(loc, Mannequin.class, m -> {
                m.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
                m.addScoreboardTag("eemientrainement");
                m.setPersistent(false);
                m.setInvulnerable(true);
                m.setGravity(false);
                m.setCollidable(false);
                m.setImmovable(true);
                m.setSilent(true);
                m.setVisibleByDefault(false);
                m.customName(c(name));
                m.setCustomNameVisible(true);
                m.setDescription(c(s.mode == Mode.PEARL ? "&5Perle " + DIFF_NAMES[diffIndex(s.pearlDiff)] : DIFF_NAMES[diffIndex(s.diff)]));
                if (skin != null && !skin.isBlank()) {
                    m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile().name(skin).build());
                }
                m.getEquipment().setItemInMainHand(new ItemStack(Material.STICK));
            });
            owner.showEntity(this, l.npc);
        } catch (RuntimeException e) {
            getLogger().warning("PNJ d'entrainement impossible a faire apparaitre : " + e.getMessage());
            l.npc = null;
        }
    }

    void removeNpc(Lane l) {
        if (l != null && l.npc != null) {
            l.npc.remove();
            l.npc = null;
        }
    }

    // ================================================================ entree / sortie

    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!ours(p.getWorld()) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemientrainement.admin")) {
            msg(p, "&cReviens au lobby (&f/lobby&c) pour t'entrainer.");
            return false;
        }
        return true;
    }

    /** Lance (ou relance avec d'autres reglages) un entrainement. */
    void start(Player p, Mode mode) {
        if (world == null) {
            msg(p, "&cL'entrainement est indisponible.");
            return;
        }
        Session s = sessions.get(p.getUniqueId());
        boolean fresh = s == null;
        if (fresh) {
            if (!canEnter(p)) {
                return;
            }
            if (p.isInsideVehicle()) {
                msg(p, "&cDescends de ta monture d'abord.");
                return;
            }
            if (!ours(p.getWorld()) && sessions.size() >= getConfig().getInt("max-players", 30)) {
                msg(p, "&cToutes les pistes sont prises, reessaie dans un instant.");
                return;
            }
            s = new Session(p.getUniqueId());
            s.enterMs = System.currentTimeMillis();
        } else if (s.mode != Mode.BRIDGE && s.kb == Kb.AIR) {
            countFail(s);
        }
        // une serie ne passe jamais d'une difficulte (ou d'une relance) a l'autre
        s.streak = 0;
        s.ok = 0;
        s.fail = 0;
        PData d = pd(p.getUniqueId());
        s.name = p.getName();
        s.mode = mode;
        s.dist = dist(d.y.getInt("prefs.distance", 32));
        s.style = style(d.y.getString("prefs.style", "droit"));
        s.diff = diff(d.y.getString("prefs.difficulty", "facile"));
        s.pearlDiff = diff(d.y.getString("prefs.pearl-difficulty", "facile"));
        s.height = height(d.y.getInt("prefs.mlg-height", 20));
        s.mlgItem = mlgItem(d.y.getString("prefs.mlg-item", "eau"));
        int slot = fresh ? freeSlot() : s.lane.slot;
        if (!fresh) {
            freeLane(s.lane);
        }
        s.lane = buildLane(s, slot);
        resetState(s);
        Location spawn = spawnOf(s);
        if (fresh) {
            if (!p.teleport(spawn) || !ours(p.getWorld())) {
                freeLane(s.lane);
                msg(p, "&cImpossible de t'emmener sur ta piste.");
                return;
            }
            sessions.put(p.getUniqueId(), s);
            applyVisibility(p);
            sendStatus();
            if (d.broken) {
                msg(p, "&cTon fichier de records est illisible : tes records ne seront pas enregistres (previens un admin).");
            }
        } else {
            p.teleport(spawn);
            sessions.put(p.getUniqueId(), s);
        }
        p.setFallDistance(0);
        p.setVelocity(new Vector());
        final Session fs = s;
        UUID id = p.getUniqueId();
        // Multiverse-Inventories enregistre l'inventaire au changement de monde : equipement apres la teleportation
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player q = Bukkit.getPlayer(id);
            if (q != null && sessions.get(id) == fs && ours(q.getWorld())) {
                prepare(q, fs);
            }
        }, fresh ? 3L : 1L);
        if (mode == Mode.KNOCKBACK) {
            spawnNpc(s, p);
            msg(p, "&fKnockback " + DIFF_NAMES[diffIndex(s.diff)] + "&7 : l'entraineur va te pousser dans le vide. Pose des blocs pour te rattraper et tiens &f1 s &7dessus.");
        } else if (mode == Mode.PEARL) {
            spawnNpc(s, p);
            boolean ponton = pearlPontonOnly(s);
            msg(p, "&5Perle " + DIFF_NAMES[diffIndex(s.pearlDiff)] + "&7 : l'entraîneur t'éjecte loin dans le vide. Lance ta &fperle de l'Ender &7vers "
                    + (ponton ? "&fle bout du ponton &7(l'île ne compte pas)" : "l'île (ou le ponton)") + " et tiens &f1 s &7dessus.");
            int best = d.y.getInt(statKey(s) + ".best-streak", 0);
            if (best > 0) {
                msg(p, "&7Ta meilleure série ici : &a" + best);
            }
        } else if (mode == Mode.MLG) {
            msg(p, "&bMLG &7" + s.height + " blocs, " + MLG_NAMES[mlgIndex(s.mlgItem)].toLowerCase()
                    + "&7 : saute du bord rouge de la tour et utilise ton objet juste avant de toucher le sol.");
            int best = d.y.getInt(statKey(s) + ".best-streak", 0);
            if (best > 0) {
                msg(p, "&7Ta meilleure serie ici : &a" + best);
            }
        } else {
            msg(p, "&fBridge &7" + s.dist + " blocs, " + STYLE_NAMES[styleIndex(s.style)].toLowerCase() + "&7 : le chrono part au premier bloc pose et s'arrete sur l'ile d'arrivee.");
            long best = d.y.getLong(bridgeKey(s) + ".best", -1);
            if (best > 0) {
                msg(p, "&7Ton record ici : &a" + time(best));
            }
        }
    }

    void prepare(Player p, Session s) {
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        if (!p.hasMetadata("vanished")) {
            for (PotionEffect e : p.getActivePotionEffects()) {
                p.removePotionEffect(e.getType());
            }
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            p.setHealth(hp.getValue());
        }
        p.setFoodLevel(20);
        p.setSaturation(20f);
        giveKit(p, s);
    }

    Location spawnOf(Session s) {
        Islands.Layout l = s.lane.layout;
        return new Location(world, l.spawnX, l.spawnY, l.spawnZ, l.spawnYaw, s.mode == Mode.BRIDGE ? 30f : s.mode == Mode.MLG ? 45f : 10f);
    }

    void resetState(Session s) {
        s.startMs = 0;
        s.resetIn = -1;
        s.attemptCounted = false;
        s.kb = Kb.WAIT;
        s.air = 0;
        s.stable = 0;
        s.onPlatform = 0;
        s.doubleIn = -1;
        s.lastPos = null;
        s.falling = false;
        s.saved = false;
        s.savedTicks = 0;
        s.peakY = 0;
        s.lastY = Double.NaN;
        java.util.Arrays.fill(s.drops, 0);
        s.pearlThrown = false;
        s.pearlLanded = false;
        s.offTarget = 0;
        newWait(s);
    }

    void newWait(Session s) {
        ConfigurationSection d = s.mode == Mode.PEARL ? pearlSection(s.pearlDiff) : diffSection(s.diff);
        int a = Math.max(10, d.getInt("delay-min", 40));
        int b = Math.max(a, d.getInt("delay-max", 40));
        s.wait = a + (b > a ? ThreadLocalRandom.current().nextInt(b - a + 1) : 0);
        s.warnAt = d.getBoolean("warn", false) ? 20 : -1;
    }

    /** Retour au depart : blocs effaces, joueur replace. */
    void restart(Player p, Session s) {
        removePearls(p);
        clearPlaced(s.lane);
        resetState(s);
        p.teleport(spawnOf(s));
        p.setFallDistance(0);
        p.setVelocity(new Vector());
        refill(p, s);
        giveModeItems(p, s);
    }

    /** Une perle encore en vol teleporterait le joueur apres la remise en place. */
    void removePearls(Player p) {
        for (org.bukkit.entity.EnderPearl ep : world.getEntitiesByClass(org.bukkit.entity.EnderPearl.class)) {
            if (p.equals(ep.getShooter())) {
                ep.remove();
            }
        }
    }

    void leave(Player p, boolean teleport) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) {
            return;
        }
        if (world != null) {
            removePearls(p);
        }
        freeLane(s.lane);
        maybeReward(p, s);
        flush(p.getUniqueId());
        for (Session o : sessions.values()) {
            Player q = Bukkit.getPlayer(o.id);
            if (q != null) {
                q.showPlayer(this, p);
                p.showPlayer(this, q);
            }
        }
        if (ours(p.getWorld())) {
            p.getInventory().clear();
            p.setFallDistance(0);
            p.setVelocity(new Vector());
            if (teleport) {
                toLobby(p);
            }
        } else {
            stripKit(p);
        }
        sendStatus();
    }

    void toLobby(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        String cmd = getConfig().getString("lobby-command", "lobby");
        boolean ok = cmd != null && !cmd.isBlank() && p.performCommand(cmd);
        if (!ok) {
            p.teleport(hub());
        } else {
            // /lobby peut teleporter avec un delai : repli seulement si le joueur est toujours ici
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && ours(p.getWorld()) && !sessions.containsKey(p.getUniqueId())) {
                    p.teleport(hub());
                }
            }, 10L);
        }
    }

    /** Point de repli : spawn DeluxeHub du lobby (config hub), sinon spawn du monde principal. */
    Location hub() {
        ConfigurationSection h = getConfig().getConfigurationSection("hub");
        if (h != null) {
            World w = Bukkit.getWorld(h.getString("world", "lobby"));
            if (w != null) {
                return new Location(w, h.getDouble("x", 722.5), h.getDouble("y", 51), h.getDouble("z", 650.5),
                        (float) h.getDouble("yaw", 90), (float) h.getDouble("pitch", 0));
            }
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    void applyVisibility(Player p) {
        if (!getConfig().getBoolean("hide-players", true)) {
            return;
        }
        for (Session o : sessions.values()) {
            Player q = Bukkit.getPlayer(o.id);
            if (q != null && q != p) {
                p.hidePlayer(this, q);
                q.hidePlayer(this, p);
            }
        }
    }

    /** Recompense EemiCore « play » : une fois par session assez longue, au plus toutes les N minutes. */
    void maybeReward(Player p, Session s) {
        int min = getConfig().getInt("reward.min-seconds", 120);
        if (min < 0 || System.currentTimeMillis() - s.enterMs < min * 1000L) {
            return;
        }
        PData d = pd(s.id);
        long last = Math.max(d.y.getLong("last-reward", 0), lastReward.getOrDefault(s.id, 0L));
        long cd = getConfig().getLong("reward.cooldown-minutes", 30) * 60000L;
        if (System.currentTimeMillis() - last < cd) {
            return;
        }
        lastReward.put(s.id, System.currentTimeMillis());
        d.y.set("last-reward", System.currentTimeMillis());
        d.dirty = true;
        eemi("eemi reward " + p.getName() + " " + coreId() + " play");
    }

    String coreId() {
        return getConfig().getString("eemicore-id", "entrainement");
    }

    void eemi(String cmd) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    void sendStatus() {
        eemi("eemi status " + coreId() + " playing 0 " + sessions.size() + " " + getConfig().getInt("max-players", 30));
    }

    // ================================================================ objets

    ItemStack item(Material m, int amount, String name, String tag, String... lore) {
        ItemStack it = new ItemStack(m, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(c(s));
            }
            meta.lore(l);
        }
        if (tag != null) {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tag);
        }
        it.setItemMeta(meta);
        return it;
    }

    String tag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    Material blockOf(UUID id) {
        PData d = pd(id);
        int t = Math.floorMod(d.y.getInt("prefs.block-type", 0), TYPES.length);
        int col = Math.floorMod(d.y.getInt("prefs.block-color", 14), COLORS.length);
        Material m = Material.matchMaterial(COLORS[col] + "_" + TYPES[t]);
        return m == null || !m.isBlock() ? Material.WHITE_WOOL : m;
    }

    void giveKit(Player p, Session s) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        refill(p, s);
        giveModeItems(p, s);
        inv.setItem(6, item(Material.RED_DYE, 1, "&cRecommencer &7(clic droit)", "restart", "&7Retour au depart, blocs effaces"));
        inv.setItem(7, item(Material.COMPARATOR, 1, "&eReglages &7(clic droit)", "settings", "&7Changer d'entrainement ou de reglages"));
        inv.setItem(8, item(Material.RED_BED, 1, "&cQuitter &7(clic droit)", "leave"));
        inv.setHeldItemSlot(0);
    }

    /** Objet unique du MLG (case 0) ou perle de l'Ender (case 0), redonne a chaque essai. */
    void giveModeItems(Player p, Session s) {
        PlayerInventory inv = p.getInventory();
        if (s.mode == Mode.MLG) {
            for (int i = 0; i < inv.getSize(); i++) {
                if ("mlg".equals(tag(inv.getItem(i)))) {
                    inv.setItem(i, null);
                }
            }
            int k = mlgIndex(s.mlgItem);
            inv.setItem(0, item(MLG_MATS[k], 1, "&b" + MLG_NAMES[k], "mlg", "&7Utilise-le juste avant de toucher le sol"));
            inv.setHeldItemSlot(0);
        } else if (s.mode == Mode.PEARL) {
            for (int i = 0; i < inv.getSize(); i++) {
                if ("pearl".equals(tag(inv.getItem(i)))) {
                    inv.setItem(i, null);
                }
            }
            inv.setItem(0, item(Material.ENDER_PEARL, 1, "&5Perle de l'Ender", "pearl", pearlPontonOnly(s)
                    ? "&7Lance-la vers le ponton quand tu es éjecté" : "&7Lance-la vers l'île quand tu es éjecté"));
            inv.setHeldItemSlot(0);
        }
    }

    void refill(Player p, Session s) {
        if (s.mode == Mode.MLG || s.mode == Mode.PEARL) {
            return;
        }
        Material m = blockOf(s.id);
        String name = "&fBlocs infinis";
        for (int slot = 0; slot <= 1; slot++) {
            ItemStack cur = p.getInventory().getItem(slot);
            // on ne remet des blocs que dans une case vide ou deja occupee par nos blocs (pas de source infinie ailleurs)
            if (cur == null || cur.getType().isAir() || "blocks".equals(tag(cur))) {
                p.getInventory().setItem(slot, item(m, 64, name, "blocks"));
            }
        }
    }

    void stripKit(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (tag(inv.getItem(i)) != null) {
                inv.setItem(i, null);
            }
        }
        if (tag(p.getItemOnCursor()) != null) {
            p.setItemOnCursor(null);
        }
    }

    // ================================================================ boucle

    private void tick() {
        tickCount++;
        for (Session s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.id);
            if (p == null || !ours(p.getWorld()) || s.lane == null) {
                continue;
            }
            if (p.getAllowFlight() && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR && !p.hasMetadata("vanished")) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
            switch (s.mode) {
                case BRIDGE -> tickBridge(p, s);
                case MLG -> tickMlg(p, s);
                default -> tickKb(p, s);
            }
        }
    }

    // ---------------------------------------------------------------- bridge

    void tickBridge(Player p, Session s) {
        Islands.Layout l = s.lane.layout;
        Location loc = p.getLocation();
        if (s.resetIn >= 0) {
            if (--s.resetIn <= 0) {
                restart(p, s);
            }
            return;
        }
        if (loc.getY() < l.lowTop - bridgeFall() || !s.lane.inBox(loc.getBlockX(), Math.max(s.lane.y1, loc.getBlockY()), loc.getBlockZ())) {
            if (s.startMs > 0) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            }
            restart(p, s);
            return;
        }
        @SuppressWarnings("deprecation")
        boolean ground = p.isOnGround();
        if (s.startMs > 0 && ground) {
            double dx = loc.getX() - (l.finishX + 0.5);
            double dz = loc.getZ() - (l.finishZ + 0.5);
            double fy = loc.getY() - l.finishY;
            if (dx * dx + dz * dz <= l.finishR * l.finishR && fy >= 0.9 && fy <= 2.6 && under(p, s.lane) == 1) {
                finishBridge(p, s);
                return;
            }
        }
        if (tickCount % 2 == 0) {
            PData d = pd(s.id);
            long best = d.y.getLong(bridgeKey(s) + ".best", -1);
            String t = s.startMs > 0 ? "&e" + time(System.currentTimeMillis() - s.startMs) : "&7Pose un bloc pour lancer le chrono";
            p.sendActionBar(c(t + " &8| &7Record : " + (best > 0 ? "&a" + time(best) : "&8-")));
        }
    }

    void finishBridge(Player p, Session s) {
        long ms = System.currentTimeMillis() - s.startMs;
        s.startMs = 0;
        s.resetIn = Math.max(10, getConfig().getInt("bridge.reset-ticks", 50));
        PData d = pd(s.id);
        String k = bridgeKey(s);
        long best = d.y.getLong(k + ".best", -1);
        d.y.set(k + ".runs", d.y.getInt(k + ".runs", 0) + 1);
        d.y.set("name", p.getName());
        boolean record = best <= 0 || ms < best;
        if (record) {
            d.y.set(k + ".best", ms);
            boardPut(k, s.id, p.getName(), ms);
        }
        d.dirty = true;
        String sub = s.dist + " blocs, " + STYLE_NAMES[styleIndex(s.style)].toLowerCase();
        if (record) {
            p.showTitle(Title.title(c("&a&l" + time(ms)), c("&6Nouveau record ! &7(" + sub + ")"), Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1600), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            msg(p, "&6Nouveau record &7(" + sub + ") : &a" + time(ms) + (best > 0 ? " &8(avant " + time(best) + ")" : ""));
        } else {
            p.showTitle(Title.title(c("&e" + time(ms)), c("&7Record : " + time(best)), Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1600), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        }
        world.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0);
    }

    String bridgeKey(Session s) {
        return "bridge." + s.style + "." + s.dist;
    }

    // ---------------------------------------------------------------- knockback

    void tickKb(Player p, Session s) {
        Lane lane = s.lane;
        Islands.Layout l = lane.layout;
        Location loc = p.getLocation();
        if (tickCount % 20 == 0 && (lane.npc == null || !lane.npc.isValid())) {
            spawnNpc(s, p);
        }
        if (lane.npc != null && lane.npc.isValid() && tickCount % 3 == 0) {
            Vector v = loc.toVector().subtract(lane.npc.getLocation().toVector());
            float yaw = (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
            lane.npc.setRotation(yaw, 0f);
        }
        if (tickCount % 4 == 0) {
            PData d = pd(s.id);
            int best = d.y.getInt(statKey(s) + ".best-streak", 0);
            p.sendActionBar(c(label(s) + " &8| &aRéussites " + s.ok + " &8| &cÉchecs " + s.fail
                    + " &8| &fSérie " + s.streak + " &7(record " + best + ")"));
        }
        // perle : une perle lancee encore en vol garde sa chance hors de la boite en x/z, pas sous la limite absolue
        boolean pearlFlying = s.mode == Mode.PEARL && s.kb == Kb.AIR && s.pearlThrown && !s.pearlLanded
                && loc.getY() >= lane.y1 && hasLivePearl(p);
        if (s.kb == Kb.AIR && loc.getY() < lane.y1) {
            kbFail(p, s);
            return;
        }
        if (!pearlFlying && !lane.inBox(loc.getBlockX(), Math.max(lane.y1, loc.getBlockY()), loc.getBlockZ())) {
            if (s.kb == Kb.AIR) {
                kbFail(p, s);
            } else {
                restart(p, s);
            }
            return;
        }
        switch (s.kb) {
            case WAIT -> {
                Location sp = spawnOf(s);
                double hd = Math.hypot(loc.getX() - sp.getX(), loc.getZ() - sp.getZ());
                // il faut rester entre l'entraineur et le vide (pas sur ni derriere lui)
                double ahead = (loc.getX() - l.npcX) * (sp.getX() - l.npcX) + (loc.getZ() - l.npcZ) * (sp.getZ() - l.npcZ);
                double span = Math.hypot(sp.getX() - l.npcX, sp.getZ() - l.npcZ);
                if (loc.getY() < l.lowTop - 1 || hd > 2.2 || ahead < span * 1.0) {
                    // le joueur s'eloigne de sa place (ou saute de lui-meme) : on le remet, sans compter
                    p.teleport(new Location(world, sp.getX(), sp.getY(), sp.getZ(), loc.getYaw(), loc.getPitch()));
                    p.setVelocity(new Vector());
                    p.sendActionBar(c("&eReste au bout du ponton !"));
                    return;
                }
                s.wait--;
                if (s.wait == s.warnAt) {
                    p.showTitle(Title.title(c("&c!"), c("&7L'entraineur arrive..."), Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                }
                if (s.wait <= 0) {
                    hit(p, s, false);
                }
            }
            case AIR -> {
                s.air++;
                if (s.doubleIn > 0 && --s.doubleIn == 0) {
                    if (s.mode == Mode.PEARL && s.pearlLanded) {
                        s.doubleIn = -1; // deja revenu par la perle : pas de 2e coup
                    } else {
                        hit(p, s, true);
                    }
                }
                s.lastPos = loc.toVector();
                if (loc.getY() < l.lowTop - fall(s)) {
                    // perle : la limite est le moment ou il faut avoir lance ; une perle en vol garde sa chance
                    if (!pearlFlying) {
                        kbFail(p, s);
                        return;
                    }
                }
                @SuppressWarnings("deprecation")
                boolean ground = p.isOnGround();
                if (s.mode == Mode.PEARL && ground && s.air > 3) {
                    // perle : on doit revenir sur l'ile ou le ponton grace a la perle (difficile : ponton seulement)
                    if (under(p, lane) == 1) {
                        if (s.pearlLanded) {
                            if (pearlPontonOnly(s) && !onPonton(loc, l)) {
                                s.stable = 0;
                                if (++s.offTarget >= 10) {
                                    pearlMiss(p, s);
                                    return;
                                }
                            } else if (++s.stable >= Math.max(5, getConfig().getInt("knockback.stable-ticks", 20))) {
                                kbSuccess(p, s);
                                return;
                            }
                        } else if (++s.onPlatform >= 10) {
                            p.sendActionBar(c("&7Pas éjecté, on recommence."));
                            s.kb = Kb.RESULT;
                            s.resultIn = 20;
                        }
                    } else {
                        s.stable = 0;
                    }
                } else if (s.mode == Mode.PEARL) {
                    s.stable = 0;
                    s.onPlatform = 0;
                } else if (ground && s.air > 3) {
                    int under = under(p, lane);
                    if (under == 2) {
                        s.onPlatform = 0;
                        if (++s.stable >= Math.max(5, getConfig().getInt("knockback.stable-ticks", 20)) && s.doubleIn < 0) {
                            kbSuccess(p, s);
                            return;
                        }
                    } else if (under == 1) {
                        s.stable = 0;
                        if (++s.onPlatform >= 10 && s.doubleIn < 0) {
                            // revenu sur le ponton sans tomber : coup sans effet, on rejoue sans compter
                            p.sendActionBar(c("&7Pas éjecté, on recommence."));
                            s.kb = Kb.RESULT;
                            s.resultIn = 20;
                        }
                    }
                } else {
                    s.stable = 0;
                    s.onPlatform = 0;
                }
                if (s.air > 20 * 12 && s.kb == Kb.AIR) {
                    if (s.mode == Mode.PEARL) {
                        countFail(s); // 12 s sans revenir : la perle a rate
                    }
                    s.kb = Kb.RESULT;
                    s.resultIn = 10;
                }
            }
            case RESULT -> {
                if (--s.resultIn <= 0) {
                    restart(p, s);
                }
            }
        }
    }

    /** 0 = rien sous les pieds, 1 = ponton / ile, 2 = un bloc pose par le joueur. */
    int under(Player p, Lane lane) {
        Location loc = p.getLocation();
        int by = (int) Math.floor(loc.getY() - 0.2);
        // d'abord le bloc sous le centre, puis les 4 coins de la hitbox (un poil a l'interieur)
        long center = Islands.key(loc.getBlockX(), by, loc.getBlockZ());
        if (lane.built.contains(center) && world.getBlockAt(loc.getBlockX(), by, loc.getBlockZ()).getType().isSolid()) {
            return 1;
        }
        boolean placed = lane.placed.contains(center);
        boolean platform = false;
        for (double ox = -0.29; ox <= 0.3; ox += 0.58) {
            for (double oz = -0.29; oz <= 0.3; oz += 0.58) {
                int x = (int) Math.floor(loc.getX() + ox);
                int z = (int) Math.floor(loc.getZ() + oz);
                long k = Islands.key(x, by, z);
                if (lane.placed.contains(k)) {
                    placed = true;
                } else if (lane.built.contains(k) && world.getBlockAt(x, by, z).getType().isSolid()) {
                    platform = true;
                }
            }
        }
        return placed ? 2 : platform ? 1 : 0;
    }

    void hit(Player p, Session s, boolean second) {
        ConfigurationSection d = s.mode == Mode.PEARL ? pearlSection(s.pearlDiff) : diffSection(s.diff);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Lane lane = s.lane;
        Vector dir;
        if (lane.npc != null && lane.npc.isValid()) {
            dir = p.getLocation().toVector().subtract(lane.npc.getLocation().toVector()).setY(0);
            lane.npc.swingMainHand();
        } else {
            dir = new Vector(0, 0, 1);
        }
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        double spread = Math.toRadians(second ? d.getDouble("double-spread", 70) : d.getDouble("spread", 0));
        if (spread > 0) {
            dir.rotateAroundY((r.nextDouble() * 2 - 1) * spread);
        }
        double hMin = second ? d.getDouble("double-horizontal", 0.35) : d.getDouble("horizontal-min", 0.42);
        double hMax = second ? hMin : Math.max(hMin, d.getDouble("horizontal-max", hMin));
        double h = hMin + (hMax > hMin ? r.nextDouble() * (hMax - hMin) : 0);
        double v;
        if (second) {
            v = d.getDouble("double-vertical", 0.3);
        } else {
            // vertical-min / vertical-max (perle 1.2.0) ; sinon une seule valeur « vertical » (knockback)
            double vMin = d.getDouble("vertical-min", d.getDouble("vertical", 0.36));
            double vMax = Math.max(vMin, d.getDouble("vertical-max", vMin));
            v = vMin + (vMax > vMin ? r.nextDouble() * (vMax - vMin) : 0);
        }
        Vector vel = dir.multiply(h).setY(v);
        if (second && s.lastPos != null) {
            // la vitesse serveur d'un joueur ne contient pas son elan horizontal : on le mesure d'un tick a l'autre
            Vector moved = p.getLocation().toVector().subtract(s.lastPos).setY(0);
            if (s.mode == Mode.PEARL) {
                // ejecte en perle, l'elan depasse 1 bloc par tick : on le garde (au-dela de 3, c'est une teleportation)
                if (moved.lengthSquared() < 9.0) {
                    vel.add(moved.multiply(0.9));
                }
            } else if (moved.lengthSquared() < 1.0) {
                vel.add(moved.multiply(0.6));
            }
        }
        p.setVelocity(vel);
        p.playHurtAnimation(0f);
        p.playSound(p.getLocation(), second ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1f);
        if (!second) {
            s.kb = Kb.AIR;
            s.air = 0;
            s.stable = 0;
            s.onPlatform = 0;
            double chance = d.getDouble("double-chance", 0);
            if (chance > 0 && r.nextDouble() < chance) {
                int a = Math.max(2, d.getInt("double-delay-min", 8));
                int b = Math.max(a, d.getInt("double-delay-max", 12));
                s.doubleIn = a + r.nextInt(b - a + 1);
            } else {
                s.doubleIn = -1;
            }
        } else {
            s.doubleIn = -1;
        }
    }

    void kbSuccess(Player p, Session s) {
        s.kb = Kb.RESULT;
        s.resultIn = 25;
        s.ok++;
        s.streak++;
        PData d = pd(s.id);
        String k = statKey(s);
        d.y.set(k + ".success", d.y.getInt(k + ".success", 0) + 1);
        d.y.set("name", p.getName());
        int best = d.y.getInt(k + ".best-streak", 0);
        boolean record = s.streak > best;
        if (record) {
            d.y.set(k + ".best-streak", s.streak);
            boardPut(k, s.id, p.getName(), s.streak);
        }
        d.dirty = true;
        String head = s.mode == Mode.MLG ? "&b&lMLG REUSSI !" : s.mode == Mode.PEARL ? "&5&lSAUVÉ !" : "&a&lRATTRAPE !";
        p.showTitle(Title.title(c(head), c(record ? "&6Record de série : " + s.streak : "&7Série : " + s.streak), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
        p.playSound(p.getLocation(), record ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
    }

    void kbFail(Player p, Session s) {
        countFail(s);
        String head = s.mode == Mode.MLG ? "&c&lECRASE" : "&c&lTOMBE";
        String sub = switch (s.mode) {
            case MLG -> "&7Utilise ton objet juste avant le sol !";
            case PEARL -> pearlPontonOnly(s) ? "&7Lance ta perle vers le ponton !" : "&7Lance ta perle vers l'île !";
            default -> "&7Pose un bloc sous tes pieds !";
        };
        p.showTitle(Title.title(c(head), c(sub), Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200))));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        restart(p, s);
    }

    /** Perle difficile : revenu par la perle, mais sur l'ile et pas sur les planches du ponton. */
    void pearlMiss(Player p, Session s) {
        countFail(s);
        p.showTitle(Title.title(c("&c&lÀ CÔTÉ"), c("&7En difficile, seul le ponton compte"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        restart(p, s);
    }

    /** Difficulte de perle ou seul le ponton compte (cle « target: ponton »). */
    boolean pearlPontonOnly(Session s) {
        return s.mode == Mode.PEARL && "ponton".equalsIgnoreCase(pearlSection(s.pearlDiff).getString("target", "ile"));
    }

    /** Pieds sur les planches du ponton (centre du joueur, petite marge pour la hitbox). */
    static boolean onPonton(Location loc, Islands.Layout l) {
        double m = 0.3;
        return loc.getX() >= l.pontonX1 - m && loc.getX() < l.pontonX2 + 1 + m
                && loc.getZ() >= l.pontonZ1 && loc.getZ() < l.pontonZ2 + 1 + m;
    }

    /** Echec compte (chute, ou relance / « Recommencer » en plein vol : on n'efface pas une chute). */
    void countFail(Session s) {
        s.fail++;
        s.streak = 0;
        PData d = pd(s.id);
        String k = statKey(s);
        d.y.set(k + ".fails", d.y.getInt(k + ".fails", 0) + 1);
        d.dirty = true;
    }

    int bridgeFall() {
        return Math.max(3, getConfig().getInt("bridge.fall-depth", 8));
    }

    int kbFall() {
        return Math.max(4, getConfig().getInt("knockback.fall-limit", 12));
    }

    /** Profondeur de chute (sous le dessus le plus bas) qui compte comme un echec. */
    int fall(Session s) {
        return switch (s.mode) {
            case BRIDGE -> bridgeFall();
            case MLG -> 4;
            case PEARL -> Math.max(8, pearlSection(s.pearlDiff).getInt("fall-limit", 30));
            default -> kbFall();
        };
    }

    /** Cle des statistiques (series, reussites, echecs) du mode en cours. */
    static String statKey(Session s) {
        return switch (s.mode) {
            case MLG -> "mlg." + s.mlgItem + "." + s.height;
            case PEARL -> "pearl." + s.pearlDiff;
            default -> "kb." + s.diff;
        };
    }

    static String label(Session s) {
        return switch (s.mode) {
            case MLG -> "&bMLG " + s.height + " &7" + MLG_NAMES[mlgIndex(s.mlgItem)];
            case PEARL -> "&5Perle " + DIFF_NAMES[diffIndex(s.pearlDiff)];
            default -> DIFF_NAMES[diffIndex(s.diff)];
        };
    }

    /** Cles d'une difficulte de perle (toutes ont un defaut dans pearlDefaults). */
    static final String[] PEARL_KEYS = {"horizontal-min", "horizontal-max", "vertical-min", "vertical-max", "spread",
        "delay-min", "delay-max", "warn", "fall-limit", "double-chance", "double-delay-min", "double-delay-max",
        "double-horizontal", "double-vertical", "double-spread", "target"};

    /** Valeurs par defaut d'une difficulte de perle (facile = le mode perle de la 1.1.x). */
    static YamlConfiguration pearlDefaults(String diff) {
        YamlConfiguration y = new YamlConfiguration();
        switch (diff) {
            case "moyen" -> {
                y.set("horizontal-min", 1.45);
                y.set("horizontal-max", 1.8);
                y.set("vertical-min", 0.3);
                y.set("vertical-max", 0.55);
                y.set("spread", 50);
                y.set("delay-min", 20);
                y.set("delay-max", 70);
                y.set("warn", false);
                y.set("fall-limit", 22);
                y.set("double-chance", 0.0);
                y.set("target", "ile");
            }
            case "difficile" -> {
                y.set("horizontal-min", 1.7);
                y.set("horizontal-max", 2.1);
                y.set("vertical-min", 0.12);
                y.set("vertical-max", 0.45);
                y.set("spread", 80);
                y.set("delay-min", 12);
                y.set("delay-max", 60);
                y.set("warn", false);
                y.set("fall-limit", 16);
                y.set("double-chance", 0.4);
                y.set("double-delay-min", 6);
                y.set("double-delay-max", 14);
                y.set("double-horizontal", 0.6);
                y.set("double-vertical", 0.2);
                y.set("double-spread", 90);
                y.set("target", "ponton");
            }
            default -> {
                y.set("horizontal-min", 1.1);
                y.set("horizontal-max", 1.45);
                y.set("vertical-min", 0.55);
                y.set("vertical-max", 0.55);
                y.set("spread", 25);
                y.set("delay-min", 30);
                y.set("delay-max", 60);
                y.set("warn", true);
                y.set("fall-limit", 30);
                y.set("double-chance", 0.0);
                y.set("target", "ile");
            }
        }
        // jamais lus sans 2e coup, mais toujours presents (hit() a des defauts de knockback sinon)
        if (!y.isSet("double-delay-min")) {
            y.set("double-delay-min", 8);
            y.set("double-delay-max", 12);
            y.set("double-horizontal", 0.5);
            y.set("double-vertical", 0.25);
            y.set("double-spread", 60);
        }
        return y;
    }

    /**
     * Reglages du coup de l'entraineur en mode perle pour une difficulte : defauts du code, puis (facile seulement)
     * les anciennes cles pearl.* de la 1.1.x, puis pearl.difficulties.&lt;difficulte&gt;.* du config.
     */
    ConfigurationSection pearlSection(String diff) {
        String df = diff(diff);
        return pearlCache.computeIfAbsent(df, this::buildPearlSection);
    }

    /** Reglages de perle deja fusionnes, vides au rechargement du config. */
    final Map<String, ConfigurationSection> pearlCache = new HashMap<>();

    ConfigurationSection buildPearlSection(String df) {
        YamlConfiguration y = pearlDefaults(df);
        ConfigurationSection legacy = getConfig().getConfigurationSection("pearl");
        if (df.equals("facile") && legacy != null) {
            for (String k : PEARL_KEYS) {
                if (legacy.isSet(k) && !legacy.isConfigurationSection(k)) {
                    y.set(k, legacy.get(k));
                }
            }
            if (legacy.isSet("vertical") && !legacy.isConfigurationSection("vertical")) {
                y.set("vertical-min", legacy.getDouble("vertical"));
                y.set("vertical-max", legacy.getDouble("vertical"));
            }
        }
        ConfigurationSection cs = getConfig().getConfigurationSection("pearl.difficulties." + df);
        if (cs != null) {
            for (String k : PEARL_KEYS) {
                if (cs.isSet(k)) {
                    y.set(k, cs.get(k));
                }
            }
            if (cs.isSet("vertical")) {
                if (!cs.isSet("vertical-min")) {
                    y.set("vertical-min", cs.getDouble("vertical"));
                }
                if (!cs.isSet("vertical-max")) {
                    y.set("vertical-max", cs.getDouble("vertical"));
                }
            }
        }
        return y;
    }

    List<Integer> heights() {
        List<Integer> l = new ArrayList<>();
        for (int h : getConfig().getIntegerList("mlg.heights")) {
            if (h >= 10 && h <= 80 && !l.contains(h)) { // sous 10, une echelle posee depuis le sommet atteint le pied
                l.add(h);
            }
        }
        if (l.isEmpty()) {
            l.addAll(List.of(10, 20, 30, 50));
        }
        return l.subList(0, Math.min(4, l.size()));
    }

    int height(int h) {
        List<Integer> l = heights();
        return l.contains(h) ? h : l.get(Math.min(1, l.size() - 1));
    }

    static String mlgItem(String s) {
        return List.of(MLG_ITEMS).contains(s) ? s : "eau";
    }

    static int mlgIndex(String s) {
        return Math.max(0, List.of(MLG_ITEMS).indexOf(s));
    }

    // ---------------------------------------------------------------- MLG

    static boolean cushion(Material m) {
        return m == Material.WATER || m == Material.COBWEB || m == Material.LADDER;
    }

    /**
     * MLG : le joueur saute du sommet de la tour. Reussi s'il passe par de l'eau, une toile ou une echelle
     * avant de toucher l'ile (comme en jeu : ces blocs annulent la chute) ; echec s'il touche le sol sans.
     */
    void tickMlg(Player p, Session s) {
        Islands.Layout l = s.lane.layout;
        Location loc = p.getLocation();
        if (s.kb == Kb.RESULT) {
            if (--s.resultIn <= 0) {
                restart(p, s);
            }
            return;
        }
        if (loc.getY() < l.lowTop - fall(s) || !s.lane.inBox(loc.getBlockX(), Math.max(s.lane.y1, loc.getBlockY()), loc.getBlockZ())) {
            if (s.falling) {
                kbFail(p, s);
            } else {
                restart(p, s);
            }
            return;
        }
        @SuppressWarnings("deprecation")
        boolean ground = p.isOnGround();
        // vitesse de chute mesuree d'un tick a l'autre (la vitesse serveur d'un joueur n'est pas fiable)
        double y = loc.getY();
        double drop = Double.isNaN(s.lastY) ? 0 : s.lastY - y;
        s.lastY = y;
        s.drops[s.dropIdx++ & 3] = drop;
        // vitesse recente : max des 4 derniers ticks (un tick sans paquet de position ne fausse pas la mesure)
        double recent = 0;
        for (double d : s.drops) {
            recent = Math.max(recent, d);
        }
        if (!s.falling) {
            if (loc.getY() < l.highTop + 0.5) {
                s.falling = true;
                s.air = 0;
                s.kb = Kb.AIR;
                s.peakY = Math.max(y, l.highTop + 1);
            } else {
                if (tickCount % 4 == 0) {
                    int best = pd(s.id).y.getInt(statKey(s) + ".best-streak", 0);
                    p.sendActionBar(c(label(s) + " &8| &7Saute du bord rouge &8| &fSerie " + s.streak + " &7(record " + best + ")"));
                }
                return;
            }
        }
        s.air++;
        s.peakY = Math.max(s.peakY, y);
        Material feet = loc.getBlock().getType();
        Material legs = loc.clone().add(0, 0.6, 0).getBlock().getType();
        if (!s.saved && (cushion(feet) || cushion(legs))) {
            // l'amorti ne compte qu'au pied de la tour, apres une vraie chute (17/11/2026 : de l'eau posee en
            // haut de la tour suffisait a reussir)
            double surface = l.lowTop + 1;
            double maxAbove = Math.max(0.5, getConfig().getDouble("mlg.max-cushion-height", 3.0));
            if (y - surface > maxAbove) {
                mlgTooHigh(p, s);
                return;
            }
            // la chute minimale est garantie par la regle ci-dessus (on part du sommet, a h >= 10 blocs du sol)
            boolean ladderOnly = feet != Material.WATER && feet != Material.COBWEB && legs != Material.WATER && legs != Material.COBWEB;
            double minSpeed = getConfig().getDouble("mlg.ladder-min-speed", 0.5);
            if (ladderOnly && feet != Material.LADDER) {
                // echelle touchee seulement par les jambes : on attend que les pieds y soient
            } else if (ladderOnly && recent < minSpeed) {
                // echelle prise sans tomber (descendue a la main) : ne compte pas
                debugOnce(p, "MLG : echelle prise trop lentement (" + String.format(java.util.Locale.ROOT, "%.2f", recent) + " b/t)");
            } else {
                s.saved = true;
            }
        }
        if (s.saved) {
            // dans l'eau ou la toile on ne touche pas le sol : reussi quand la chute est arretee
            if (ground || ++s.savedTicks >= 8) {
                kbSuccess(p, s);
            }
            return;
        }
        if (ground && s.air > 2) {
            if (l.highTop - loc.getY() > 3.5) {
                kbFail(p, s);
            } else {
                // retombe sur la tour (pas saute) : on rejoue sans compter
                s.falling = false;
                s.kb = Kb.WAIT;
            }
        }
    }

    /** MLG : amorti touche trop haut sur la tour (eau, toile ou echelle posee plus haut que le pied). */
    void mlgTooHigh(Player p, Session s) {
        countFail(s);
        p.showTitle(Title.title(c("&c&lTROP HAUT !"), c("&7L'amorti doit être au pied de la tour"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        restart(p, s);
    }

    ConfigurationSection diffSection(String diff) {
        ConfigurationSection cs = getConfig().getConfigurationSection("knockback.difficulties." + diff);
        if (cs != null) {
            return cs;
        }
        YamlConfiguration y = new YamlConfiguration();
        switch (diff) {
            case "moyen" -> {
                y.set("horizontal-min", 0.55);
                y.set("horizontal-max", 0.7);
                y.set("vertical", 0.38);
                y.set("delay-min", 25);
                y.set("delay-max", 70);
                y.set("spread", 15);
            }
            case "difficile" -> {
                y.set("horizontal-min", 0.7);
                y.set("horizontal-max", 0.85);
                y.set("vertical", 0.4);
                y.set("delay-min", 15);
                y.set("delay-max", 60);
                y.set("spread", 55);
                y.set("double-chance", 0.5);
            }
            default -> {
                y.set("horizontal-min", 0.42);
                y.set("horizontal-max", 0.42);
                y.set("vertical", 0.36);
                y.set("delay-min", 40);
                y.set("delay-max", 40);
                y.set("warn", true);
            }
        }
        return y;
    }

    // ================================================================ reglages

    int dist(int d) {
        List<Integer> l = distances();
        return l.contains(d) ? d : l.get(Math.min(1, l.size() - 1));
    }

    List<Integer> distances() {
        List<Integer> l = new ArrayList<>();
        for (int d : getConfig().getIntegerList("bridge.distances")) {
            if (d >= 4 && d <= 120 && !l.contains(d)) {
                l.add(d);
            }
        }
        if (l.isEmpty()) {
            l.addAll(List.of(16, 32, 50, 100));
        }
        return l.subList(0, Math.min(4, l.size()));
    }

    static String style(String s) {
        return List.of(STYLES).contains(s) ? s : "droit";
    }

    static int styleIndex(String s) {
        int i = List.of(STYLES).indexOf(s);
        return Math.max(0, i);
    }

    static String diff(String s) {
        return List.of(DIFFS).contains(s) ? s : "facile";
    }

    static int diffIndex(String s) {
        return Math.max(0, List.of(DIFFS).indexOf(s));
    }

    static String time(long ms) {
        return String.format(java.util.Locale.ROOT, "%.3f s", ms / 1000.0);
    }

    // ================================================================ menus

    void openLater(Player p, String type) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) {
                open(p, type);
            }
        }, 2L);
    }

    void open(Player p, String type) {
        PData d = pd(p.getUniqueId());
        Menu m = new Menu(type);
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, 1, " ", null);
        switch (type) {
            case "bridge" -> {
                m.inv = Bukkit.createInventory(m, 45, c("&8Entrainement - Bridge"));
                fill(m.inv, pane);
                int cur = dist(d.y.getInt("prefs.distance", 32));
                List<Integer> ds = distances();
                int[] slots = {10, 12, 14, 16};
                for (int i = 0; i < ds.size(); i++) {
                    boolean sel = ds.get(i) == cur;
                    m.inv.setItem(slots[i], item(sel ? Material.LIME_CONCRETE : Material.WHITE_CONCRETE, Math.min(64, ds.get(i)),
                            (sel ? "&a" : "&f") + ds.get(i) + " blocs", "dist:" + ds.get(i), "&7Longueur du vide", sel ? "&aChoisi" : "&eClic pour choisir"));
                }
                String st = style(d.y.getString("prefs.style", "droit"));
                Material[] icons = {Material.OAK_PLANKS, Material.OAK_STAIRS, Material.LADDER};
                String[] lores = {"&7Tout droit vers l'ile", "&7L'ile est en biais (45 degres)", "&7L'ile d'arrivee est plus haute"};
                int[] ss = {20, 22, 24};
                for (int i = 0; i < STYLES.length; i++) {
                    boolean sel = STYLES[i].equals(st);
                    m.inv.setItem(ss[i], item(icons[i], 1, (sel ? "&a" : "&f") + STYLE_NAMES[i], "style:" + STYLES[i], lores[i], sel ? "&aChoisi" : "&eClic pour choisir"));
                }
                addBlockButtons(m.inv, p, 29, 33);
                m.inv.setItem(31, item(Material.CLOCK, 1, "&6Records", "records", "&7Tes meilleurs temps"));
                m.inv.setItem(36, item(Material.ARROW, 1, "&7Retour", "back"));
                m.inv.setItem(40, item(Material.LIME_WOOL, 1, "&a&lLANCER", "go-bridge", "&7" + cur + " blocs, " + STYLE_NAMES[styleIndex(st)].toLowerCase()));
            }
            case "kb" -> {
                m.inv = Bukkit.createInventory(m, 36, c("&8Entrainement - Knockback"));
                fill(m.inv, pane);
                String cur = diff(d.y.getString("prefs.difficulty", "facile"));
                Material[] icons = {Material.WOODEN_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD};
                String[][] lores = {
                    {"&7Un coup annonce, recul faible", "&7Ideal pour apprendre"},
                    {"&7Recul plus fort, coup sans prevenir", "&7Direction un peu variable"},
                    {"&7Recul fort, directions au hasard", "&7Parfois un 2e coup en l'air"}};
                int[] ss = {11, 13, 15};
                for (int i = 0; i < DIFFS.length; i++) {
                    boolean sel = DIFFS[i].equals(cur);
                    int best = d.y.getInt("kb." + DIFFS[i] + ".best-streak", 0);
                    m.inv.setItem(ss[i], item(icons[i], 1, DIFF_NAMES[i] + (sel ? " &a(choisi)" : ""), "go-kb:" + DIFFS[i],
                            lores[i][0], lores[i][1], "&7Meilleure serie : &f" + best, "&eClic pour lancer"));
                }
                addBlockButtons(m.inv, p, 20, 24);
                m.inv.setItem(27, item(Material.ARROW, 1, "&7Retour", "back"));
            }
            case "pearl" -> {
                m.inv = Bukkit.createInventory(m, 36, c("&8Entrainement - Perle"));
                fill(m.inv, pane);
                String cur = diff(d.y.getString("prefs.pearl-difficulty", "facile"));
                Material[] icons = {Material.ENDER_PEARL, Material.ENDER_EYE, Material.END_CRYSTAL};
                String[][] lores = {
                    {"&7Éjection annoncée, toujours pareille", "&7Reviens sur l'île ou le ponton"},
                    {"&7Plus loin, sans prévenir", "&7Direction et hauteur variables"},
                    {"&7Très loin, presque de côté, parfois bas", "&7Parfois un 2e coup en l'air",
                        "&cSeul le ponton compte"}};
                int[] ss = {11, 13, 15};
                for (int i = 0; i < DIFFS.length; i++) {
                    boolean sel = DIFFS[i].equals(cur);
                    int best = d.y.getInt("pearl." + DIFFS[i] + ".best-streak", 0);
                    List<String> lore = new ArrayList<>(List.of(lores[i]));
                    lore.add("&7Meilleure série : &f" + best);
                    lore.add("&eClic pour lancer");
                    m.inv.setItem(ss[i], item(icons[i], 1, DIFF_NAMES[i] + (sel ? " &a(choisi)" : ""), "go-pearl:" + DIFFS[i],
                            lore.toArray(new String[0])));
                }
                m.inv.setItem(27, item(Material.ARROW, 1, "&7Retour", "back"));
                m.inv.setItem(31, item(Material.CLOCK, 1, "&6Mes records", "records"));
            }
            case "records" -> {
                m.inv = Bukkit.createInventory(m, 45, c("&8Entrainement - Records"));
                fill(m.inv, pane);
                int slot = 10;
                for (String st : STYLES) {
                    List<String> lore = new ArrayList<>();
                    for (int ds : distances()) {
                        long b = d.y.getLong("bridge." + st + "." + ds + ".best", -1);
                        lore.add("&7" + ds + " blocs : " + (b > 0 ? "&a" + time(b) : "&8-"));
                    }
                    m.inv.setItem(slot, item(Material.CLOCK, 1, "&6Bridge " + STYLE_NAMES[styleIndex(st)].toLowerCase(), null, lore.toArray(new String[0])));
                    slot += 2;
                }
                slot = 19;
                for (int i = 0; i < DIFFS.length; i++) {
                    String k = "kb." + DIFFS[i];
                    m.inv.setItem(slot, item(Material.STICK, 1, "&6Knockback " + DIFF_NAMES[i], null,
                            "&7Meilleure serie : &f" + d.y.getInt(k + ".best-streak", 0),
                            "&7Reussites : &a" + d.y.getInt(k + ".success", 0), "&7Echecs : &c" + d.y.getInt(k + ".fails", 0)));
                    slot += 2;
                }
                List<String> pl = new ArrayList<>();
                for (int i = 0; i < DIFFS.length; i++) {
                    String k = "pearl." + DIFFS[i];
                    pl.add(DIFF_NAMES[i] + " &7: série &f" + d.y.getInt(k + ".best-streak", 0)
                            + " &8(&a" + d.y.getInt(k + ".success", 0) + "&8/&c" + d.y.getInt(k + ".fails", 0) + "&8)");
                }
                m.inv.setItem(16, item(Material.ENDER_PEARL, 1, "&5Perle", null, pl.toArray(new String[0])));
                slot = 28;
                for (int i = 0; i < MLG_ITEMS.length; i++) {
                    List<String> lore = new ArrayList<>();
                    for (int h : heights()) {
                        lore.add("&7" + h + " blocs : serie &f" + d.y.getInt("mlg." + MLG_ITEMS[i] + "." + h + ".best-streak", 0));
                    }
                    m.inv.setItem(slot, item(MLG_MATS[i], 1, "&bMLG " + MLG_NAMES[i].toLowerCase(), null, lore.toArray(new String[0])));
                    slot += 2;
                }
                m.inv.setItem(40, item(Material.ARROW, 1, "&7Retour", "back"));
            }
            case "mlg" -> {
                m.inv = Bukkit.createInventory(m, 36, c("&8Entrainement - MLG"));
                fill(m.inv, pane);
                int cur = height(d.y.getInt("prefs.mlg-height", 20));
                String it = mlgItem(d.y.getString("prefs.mlg-item", "eau"));
                List<Integer> hs = heights();
                int[] slots = {10, 12, 14, 16};
                for (int i = 0; i < hs.size(); i++) {
                    boolean sel = hs.get(i) == cur;
                    int best = d.y.getInt("mlg." + it + "." + hs.get(i) + ".best-streak", 0);
                    m.inv.setItem(slots[i], item(sel ? Material.LIME_CONCRETE : Material.WHITE_CONCRETE, Math.min(64, hs.get(i)),
                            (sel ? "&a" : "&f") + hs.get(i) + " blocs", "mlgh:" + hs.get(i), "&7Hauteur de la tour",
                            "&7Meilleure serie (" + MLG_NAMES[mlgIndex(it)].toLowerCase() + ") : &f" + best, sel ? "&aChoisi" : "&eClic pour choisir"));
                }
                String[] lores = {"&7Pose l'eau sur le sol juste avant l'impact", "&7Pose la toile sur le sol sous toi", "&7Accroche l'echelle a la tour en tombant"};
                int[] is = {20, 22, 24};
                for (int i = 0; i < MLG_ITEMS.length; i++) {
                    boolean sel = MLG_ITEMS[i].equals(it);
                    m.inv.setItem(is[i], item(MLG_MATS[i], 1, (sel ? "&a" : "&f") + MLG_NAMES[i], "mlgi:" + MLG_ITEMS[i], lores[i], sel ? "&aChoisi" : "&eClic pour choisir"));
                }
                m.inv.setItem(27, item(Material.ARROW, 1, "&7Retour", "back"));
                m.inv.setItem(31, item(Material.LIME_WOOL, 1, "&a&lLANCER", "go-mlg", "&7" + cur + " blocs, " + MLG_NAMES[mlgIndex(it)].toLowerCase()));
            }
            default -> {
                m.inv = Bukkit.createInventory(m, 27, c("&8Entrainement"));
                fill(m.inv, pane);
                m.inv.setItem(10, item(Material.WHITE_WOOL, 1, "&f&lBRIDGE", "open:bridge", "&7Traverse le vide d'une ile a l'autre", "&7Blocs infinis, chrono et records", "&eClic pour choisir"));
                m.inv.setItem(12, item(Material.STICK, 1, "&c&lKNOCKBACK", "open:kb", "&7Un entraineur te pousse dans le vide", "&7Rattrape-toi en posant des blocs", "&eClic pour choisir"));
                m.inv.setItem(14, item(Material.WATER_BUCKET, 1, "&b&lMLG", "open:mlg", "&7Saute d'une tour et sauve-toi", "&7avec un seau d'eau, une toile ou une echelle", "&eClic pour choisir"));
                m.inv.setItem(16, item(Material.ENDER_PEARL, 1, "&5&lPERLE", "open:pearl", "&7Tu es éjecté loin dans le vide :", "&7lance ta perle pour revenir",
                        "&7Facile, moyen ou difficile", "&eClic pour choisir"));
                m.inv.setItem(21, item(Material.CLOCK, 1, "&6Mes records", "records"));
                if (sessions.containsKey(p.getUniqueId())) {
                    m.inv.setItem(23, item(Material.RED_BED, 1, "&cQuitter l'entrainement", "leave"));
                } else {
                    m.inv.setItem(23, item(Material.BARRIER, 1, "&cFermer", "close"));
                }
            }
        }
        p.openInventory(m.inv);
    }

    void addBlockButtons(Inventory inv, Player p, int typeSlot, int colorSlot) {
        PData d = pd(p.getUniqueId());
        int t = Math.floorMod(d.y.getInt("prefs.block-type", 0), TYPES.length);
        int col = Math.floorMod(d.y.getInt("prefs.block-color", 14), COLORS.length);
        Material m = blockOf(p.getUniqueId());
        inv.setItem(typeSlot, item(m, 1, "&fBloc : &e" + TYPE_NAMES[t], "type", "&7Laine, terre cuite ou beton", "&eClic pour changer"));
        inv.setItem(colorSlot, item(m, 1, "&fCouleur : &e" + COLOR_NAMES[col], "color", "&eClic gauche : suivante", "&eClic droit : precedente"));
    }

    static void fill(Inventory inv, ItemStack it) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, it);
        }
    }

    void onMenuClick(Player p, Menu m, ItemStack it, boolean right) {
        String t = tag(it);
        if (t == null) {
            return;
        }
        PData d = pd(p.getUniqueId());
        Session s = sessions.get(p.getUniqueId());
        if (t.startsWith("dist:")) {
            d.y.set("prefs.distance", Integer.parseInt(t.substring(5)));
            d.dirty = true;
            open(p, m.type);
        } else if (t.startsWith("style:")) {
            d.y.set("prefs.style", t.substring(6));
            d.dirty = true;
            open(p, m.type);
        } else if (t.equals("type") || t.equals("color")) {
            String key = t.equals("type") ? "prefs.block-type" : "prefs.block-color";
            int n = t.equals("type") ? TYPES.length : COLORS.length;
            int def = t.equals("type") ? 0 : 14;
            d.y.set(key, Math.floorMod(d.y.getInt(key, def) + (right ? -1 : 1), n));
            d.dirty = true;
            if (s != null && ours(p.getWorld())) {
                refill(p, s);
            }
            open(p, m.type);
        } else if (t.startsWith("mlgh:")) {
            d.y.set("prefs.mlg-height", Integer.parseInt(t.substring(5)));
            d.dirty = true;
            open(p, m.type);
        } else if (t.startsWith("mlgi:")) {
            d.y.set("prefs.mlg-item", t.substring(5));
            d.dirty = true;
            open(p, m.type);
        } else if (t.equals("go-mlg")) {
            p.closeInventory();
            start(p, Mode.MLG);
        } else if (t.startsWith("go-pearl")) {
            if (t.startsWith("go-pearl:")) {
                d.y.set("prefs.pearl-difficulty", diff(t.substring(9)));
                d.dirty = true;
            }
            p.closeInventory();
            start(p, Mode.PEARL);
        } else if (t.equals("go-bridge")) {
            p.closeInventory();
            start(p, Mode.BRIDGE);
        } else if (t.startsWith("go-kb:")) {
            d.y.set("prefs.difficulty", t.substring(6));
            d.dirty = true;
            p.closeInventory();
            start(p, Mode.KNOCKBACK);
        } else if (t.startsWith("open:")) {
            open(p, t.substring(5));
        } else if (t.equals("records")) {
            open(p, "records");
        } else if (t.equals("back")) {
            open(p, "main");
        } else if (t.equals("leave")) {
            p.closeInventory();
            leave(p, true);
        } else if (t.equals("close")) {
            p.closeInventory();
        }
    }

    // ================================================================ donnees joueur

    File file(UUID id) {
        return new File(new File(getDataFolder(), "players"), id + ".yml");
    }

    PData pd(UUID id) {
        return pdata.computeIfAbsent(id, k -> {
            File f = file(k);
            YamlConfiguration y = new YamlConfiguration();
            PData d = new PData(y);
            if (f.exists()) {
                try {
                    y.load(f);
                } catch (IOException | InvalidConfigurationException e) {
                    d.broken = true;
                    getLogger().warning("Fichier illisible, jamais reecrit : " + f.getName() + " (" + e.getMessage() + ")");
                }
            }
            if (!d.broken && migratePearl(y)) {
                d.dirty = true;
            }
            return d;
        });
    }

    void flush(UUID id) {
        PData d = pdata.get(id);
        if (d == null) {
            return;
        }
        if (d.dirty && !d.broken) {
            writeAtomic(d.y, file(id));
        }
        d.dirty = false;
        if (Bukkit.getPlayer(id) == null || !sessions.containsKey(id)) {
            pdata.remove(id);
        }
    }

    void flushAll() {
        for (UUID id : new ArrayList<>(pdata.keySet())) {
            flush(id);
        }
        if (isEnabled()) {
            sendStatus(); // le statut EemiCore expire au bout de 10 min
        }
    }

    void writeAtomic(YamlConfiguration y, File f) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            f.getParentFile().mkdirs();
            y.save(tmp);
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            getLogger().warning("Sauvegarde de " + f.getName() + " : " + e.getMessage());
        }
    }

    void loadBoard() {
        board.clear();
        File[] fs = new File(getDataFolder(), "players").listFiles((dir, n) -> n.endsWith(".yml"));
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            UUID id;
            try {
                id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
            } catch (IllegalArgumentException e) {
                continue;
            }
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.load(f);
            } catch (IOException | InvalidConfigurationException e) {
                continue;
            }
            String name = y.getString("name", "?");
            ConfigurationSection b = y.getConfigurationSection("bridge");
            if (b != null) {
                for (String st : b.getKeys(false)) {
                    ConfigurationSection sd = b.getConfigurationSection(st);
                    if (sd == null) {
                        continue;
                    }
                    for (String ds : sd.getKeys(false)) {
                        long best = sd.getLong(ds + ".best", -1);
                        if (best > 0) {
                            boardPut("bridge." + st + "." + ds, id, name, best);
                        }
                    }
                }
            }
            for (String df : DIFFS) {
                int bs = y.getInt("kb." + df + ".best-streak", 0);
                if (bs > 0) {
                    boardPut("kb." + df, id, name, bs);
                }
            }
            migratePearl(y); // en memoire seulement : le fichier est reecrit a la prochaine session du joueur
            for (String df : DIFFS) {
                int pbs = y.getInt("pearl." + df + ".best-streak", 0);
                if (pbs > 0) {
                    boardPut("pearl." + df, id, name, pbs);
                }
            }
            ConfigurationSection mg = y.getConfigurationSection("mlg");
            if (mg != null) {
                for (String itm : mg.getKeys(false)) {
                    ConfigurationSection si = mg.getConfigurationSection(itm);
                    if (si == null) {
                        continue;
                    }
                    for (String h : si.getKeys(false)) {
                        int bs = si.getInt(h + ".best-streak", 0);
                        if (bs > 0) {
                            boardPut("mlg." + itm + "." + h, id, name, bs);
                        }
                    }
                }
            }
        }
    }

    /**
     * 1.2.0 : les stats de perle de la 1.1.x (pearl.best-streak / success / fails, une seule difficulte)
     * deviennent celles du facile (pearl.facile.*), qui reprend exactement les reglages d'avant.
     */
    static boolean migratePearl(YamlConfiguration y) {
        boolean changed = false;
        for (String k : new String[]{"best-streak", "success", "fails"}) {
            String old = "pearl." + k;
            if (y.isSet(old) && !y.isConfigurationSection(old)) {
                int v = y.getInt(old, 0);
                String nk = "pearl.facile." + k;
                int cur = y.getInt(nk, 0);
                // series : la meilleure des deux ; compteurs : la somme
                y.set(nk, k.equals("best-streak") ? Math.max(cur, v) : cur + v);
                y.set(old, null);
                changed = true;
            }
        }
        return changed;
    }

    void boardPut(String key, UUID id, String name, long value) {
        board.computeIfAbsent(key, k -> new HashMap<>()).put(id, new Object[]{name, value});
    }

    void showTop(CommandSender s, String key, boolean lowerIsBetter, String title) {
        List<Object[]> l = new ArrayList<>(board.getOrDefault(key, Map.of()).values());
        l.sort((a, b) -> lowerIsBetter ? Long.compare((Long) a[1], (Long) b[1]) : Long.compare((Long) b[1], (Long) a[1]));
        msg(s, "&6Classement &f" + title);
        if (l.isEmpty()) {
            s.sendMessage(c("&8  Personne pour l'instant."));
        }
        for (int i = 0; i < Math.min(10, l.size()); i++) {
            Object[] e = l.get(i);
            long v = (Long) e[1];
            s.sendMessage(c("&e  " + (i + 1) + ". &f" + e[0] + " &7- " + (lowerIsBetter ? "&a" + time(v) : "&a" + v + " a la suite")));
        }
    }

    // ================================================================ evenements

    boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE && !sessions.containsKey(p.getUniqueId());
    }

    /** Pose validee par onPlace (pour la reprise si un autre plugin l'annule apres nous). */
    private BlockPlaceEvent approvedPlace;
    private org.bukkit.block.data.BlockData approvedData;
    private boolean placeCancelledEarly;
    private final Set<UUID> placeWarned = new HashSet<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlaceEarly(BlockPlaceEvent e) {
        placeCancelledEarly = e.isCancelled();
    }

    /**
     * Un autre plugin a annule une pose que nous avions validee (17/11/2026 : les blocs disparaissaient
     * des la pose). On pose le bloc nous-memes au tick suivant et on le signale une fois par joueur.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceMonitor(BlockPlaceEvent e) {
        if (e != approvedPlace) {
            return;
        }
        approvedPlace = null;
        if (!e.isCancelled() && e.canBuild()) {
            return;
        }
        Player p = e.getPlayer();
        Block b = e.getBlock();
        org.bukkit.block.data.BlockData data = approvedData;
        if (placeWarned.add(p.getUniqueId())) {
            getLogger().warning("Pose de " + p.getName() + " refusee par un autre plugin (annulee=" + e.isCancelled()
                + ", canBuild=" + e.canBuild() + ", "
                + (placeCancelledEarly ? "deja annulee avant nous" : "pas annulee avant nous")
                + ") : bloc repose par EemiEntrainement.");
        }
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Session s = sessions.get(id);
            Player q = Bukkit.getPlayer(id);
            if (s == null || s.lane == null || q == null || !b.getType().isAir()) {
                return;
            }
            b.setBlockData(data, false);
            s.lane.placed.add(Islands.key(b.getX(), b.getY(), b.getZ()));
            refill(q, s);
        });
    }

    /** Joueurs pour qui un BlockPlaceEvent est bien arrive jusqu'a nous (diagnostic). */
    private final Set<UUID> placeEventSeen = new HashSet<>();
    private final Set<String> debugDone = new HashSet<>();

    /** Une ligne de diagnostic par joueur et par raison, dans la console. */
    void debugOnce(Player p, String what) {
        if (getConfig().getBoolean("debug-place", true) && debugDone.add(p.getUniqueId() + what)) {
            getLogger().info("[pose] " + p.getName() + " : " + what);
        }
    }

    /** Raison pour laquelle le plugin refuse une pose en (b), ou null si elle est permise. */
    String refusal(Session s, Block b, String it, Material replaced) {
        if (s == null || s.lane == null) {
            return "pas de session";
        }
        boolean item = "blocks".equals(it) && (s.mode == Mode.BRIDGE || s.mode == Mode.KNOCKBACK)
                || "mlg".equals(it) && s.mode == Mode.MLG;
        if (!item) {
            return "objet " + it + " en mode " + s.mode;
        }
        if (!s.lane.inBox(b.getX(), b.getY(), b.getZ())) {
            return "hors de la piste (" + b.getX() + " " + b.getY() + " " + b.getZ() + ")";
        }
        if (!replaced.isAir()) {
            return "case occupee par " + replaced;
        }
        if (s.lane.built.contains(Islands.key(b.getX(), b.getY(), b.getZ()))) {
            return "case de l'ile";
        }
        return null;
    }

    /**
     * Repli si la pose n'a pas eu lieu alors que le clic droit visait une case libre de la piste avec nos blocs
     * (un autre plugin peut refuser le clic droit lui-meme : aucun BlockPlaceEvent n'est alors envoye).
     * On regarde au tick suivant si le bloc est la ; sinon on le pose nous-memes, avec les memes regles.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractMonitor(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && e.getHand() != null
                && ours(p.getWorld()) && !builder(p)) {
            pearlFallback(p, e);
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null
                || !ours(p.getWorld()) || builder(p)) {
            return;
        }
        Session s = sessions.get(p.getUniqueId());
        ItemStack hand = p.getInventory().getItemInMainHand();
        String it = tag(hand);
        if (s == null || !("blocks".equals(it) || "mlg".equals(it)) || hand.getType() == Material.WATER_BUCKET || hand.getType() == Material.BUCKET) {
            return;
        }
        Block target = e.getClickedBlock().getRelative(e.getBlockFace());
        Material mat = hand.getType();
        BlockFace face = e.getBlockFace();
        String state = "useItemInHand=" + e.useItemInHand() + ", useInteractedBlock=" + e.useInteractedBlock() + ", annule=" + e.isCancelled();
        placeEventSeen.remove(p.getUniqueId());
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Player q = Bukkit.getPlayer(id);
            Session cur = sessions.get(id);
            if (q == null || cur != s || cur.lane == null || !ours(q.getWorld())) {
                return;
            }
            long k = Islands.key(target.getX(), target.getY(), target.getZ());
            if (!target.getType().isAir() || cur.lane.placed.contains(k)) {
                return; // la pose normale a marche
            }
            boolean seen = placeEventSeen.contains(id);
            if (seen) {
                return; // notre onPlace a vu la pose et l'a refusee (raison deja notee)
            }
            debugOnce(q, "clic droit sans pose (" + state + ", aucun BlockPlaceEvent recu) : bloc pose par EemiEntrainement");
            if (refusal(cur, target, it, target.getType()) != null) {
                return;
            }
            if (cur.mode == Mode.KNOCKBACK && cur.kb != Kb.AIR || cur.mode == Mode.BRIDGE && cur.resetIn >= 0) {
                return;
            }
            // pas dans le corps d'un joueur
            org.bukkit.util.BoundingBox box = org.bukkit.util.BoundingBox.of(target);
            for (Player o : target.getWorld().getPlayers()) {
                if (o.getGameMode() != GameMode.SPECTATOR && o.getBoundingBox().overlaps(box)) {
                    return;
                }
            }
            if (mat == Material.LADDER) {
                if (face == BlockFace.UP || face == BlockFace.DOWN) {
                    return;
                }
                org.bukkit.block.data.type.Ladder lad = (org.bukkit.block.data.type.Ladder) Material.LADDER.createBlockData();
                lad.setFacing(face);
                target.setBlockData(lad, false);
            } else {
                target.setType(mat, false);
            }
            cur.lane.placed.add(k);
            q.playSound(target.getLocation(), target.getBlockData().getSoundGroup().getPlaceSound(), 1f, 0.8f);
            if (cur.mode == Mode.MLG) {
                ItemStack h = q.getInventory().getItemInMainHand();
                if ("mlg".equals(tag(h))) {
                    h.setAmount(h.getAmount() - 1);
                }
            } else {
                if (cur.mode == Mode.BRIDGE && cur.startMs == 0) {
                    cur.startMs = System.currentTimeMillis();
                }
                refill(q, cur);
            }
        });
    }

    /** Joueur dont EemiEntrainement lance lui-meme la perle (onLaunch la laisse partir). */
    private UUID launchingPearl;

    boolean hasLivePearl(Player p) {
        for (org.bukkit.entity.EnderPearl ep : world.getEntitiesByClass(org.bukkit.entity.EnderPearl.class)) {
            if (p.equals(ep.getShooter()) && ep.isValid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Perle (17/11/2026 : impossible de la lancer) : si au tick suivant le clic droit la perle est toujours
     * dans la main et qu'aucune perle du joueur n'est en vol, le jeu ne l'a pas lancee (clic refuse ou lancer
     * annule par un autre plugin) : on la lance nous-memes, avec les memes regles (apres l'ejection, une par essai).
     */
    void pearlFallback(Player p, PlayerInteractEvent e) {
        Session s = sessions.get(p.getUniqueId());
        EquipmentSlot hand = e.getHand();
        if (s == null || s.mode != Mode.PEARL || s.kb != Kb.AIR || s.pearlLanded || hand == null
                || !"pearl".equals(tag(p.getInventory().getItem(hand)))) {
            return;
        }
        // useInteractedBlock=DENY (donc isCancelled) vient de notre onInteract : seul useItemInHand renseigne
        String state = "useItemInHand=" + e.useItemInHand() + ", " + e.getAction();
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Player q = Bukkit.getPlayer(id);
            Session cur = sessions.get(id);
            if (q == null || cur != s || cur.lane == null || !ours(q.getWorld()) || cur.mode != Mode.PEARL
                    || cur.kb != Kb.AIR || cur.pearlLanded) {
                return;
            }
            PlayerInventory inv = q.getInventory();
            ItemStack it = inv.getItem(hand);
            if (it == null || it.getType() != Material.ENDER_PEARL || !"pearl".equals(tag(it)) || hasLivePearl(q)) {
                return; // le lancer normal a marche (la perle est partie)
            }
            org.bukkit.entity.EnderPearl ep;
            launchingPearl = id;
            try {
                ep = q.launchProjectile(org.bukkit.entity.EnderPearl.class);
            } catch (RuntimeException ex) {
                getLogger().warning("Perle de " + q.getName() + " impossible a lancer : " + ex.getMessage());
                return;
            } finally {
                launchingPearl = null;
            }
            if (ep == null || !ep.isValid()) {
                debugOnce(q, "perle lancee par EemiEntrainement mais refusee par un autre plugin");
                return;
            }
            debugOnce(q, "perle non lancee par le jeu (" + state + ") : lancee par EemiEntrainement");
            cur.pearlThrown = true;
            inv.setItem(hand, it.getAmount() > 1 ? it.asQuantity(it.getAmount() - 1) : null);
            q.setCooldown(Material.ENDER_PEARL, 20);
            q.swingHand(hand);
            world.playSound(q.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, org.bukkit.SoundCategory.NEUTRAL, 0.5f,
                    0.4f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f));
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!ours(b.getWorld()) || builder(p)) {
            return;
        }
        Session s = sessions.get(p.getUniqueId());
        String it = tag(e.getItemInHand());
        if (s != null) {
            placeEventSeen.add(p.getUniqueId());
        }
        String why = refusal(s, b, it, e.getBlockReplacedState().getType());
        if (why != null) {
            e.setCancelled(true);
            debugOnce(p, "pose refusee par EemiEntrainement : " + why);
            return;
        }
        if (s.mode == Mode.MLG) {
            // toile ou echelle : une seule par essai (pas de recharge), effacee a la remise en place
            e.setCancelled(false);
            e.setBuild(true);
            approvedPlace = e;
            approvedData = e.getBlockPlaced().getBlockData().clone();
            s.lane.placed.add(Islands.key(b.getX(), b.getY(), b.getZ()));
            return;
        }
        if (s.mode == Mode.KNOCKBACK && s.kb != Kb.AIR) {
            e.setCancelled(true);
            p.sendActionBar(c("&eAttends le coup pour poser des blocs !"));
            return;
        }
        if (s.mode == Mode.BRIDGE && s.resetIn >= 0) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(false);
        e.setBuild(true);
        approvedPlace = e;
        approvedData = e.getBlockPlaced().getBlockData().clone();
        s.lane.placed.add(Islands.key(b.getX(), b.getY(), b.getZ()));
        if (s.mode == Mode.BRIDGE && s.startMs == 0) {
            s.startMs = System.currentTimeMillis();
            PData d = pd(s.id);
            String k = bridgeKey(s);
            d.y.set(k + ".attempts", d.y.getInt(k + ".attempts", 0) + 1);
            d.dirty = true;
        }
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Player q = Bukkit.getPlayer(id);
            Session cur = sessions.get(id);
            if (q != null && cur == s && ours(q.getWorld())) {
                refill(q, s);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!ours(b.getWorld()) || builder(p)) {
            return;
        }
        Session s = sessions.get(p.getUniqueId());
        long k = Islands.key(b.getX(), b.getY(), b.getZ());
        if (s == null || s.lane == null || !s.lane.placed.contains(k)) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(false);
        e.setDropItems(false);
        e.setExpToDrop(0);
        s.lane.placed.remove(k);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld()) || builder(p)) {
            return;
        }
        Action a = e.getAction();
        String t = tag(e.getItem());
        if (a == Action.PHYSICAL) {
            e.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (a == Action.RIGHT_CLICK_BLOCK) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
        if ("pearl".equals(t)) {
            Session ps = sessions.get(p.getUniqueId());
            // perle « deja lancee » seulement si elle vole encore (un lancer annule par un autre plugin la laisse en main)
            if (ps == null || ps.mode != Mode.PEARL || ps.kb != Kb.AIR || ps.pearlThrown && hasLivePearl(p)) {
                e.setUseItemInHand(Event.Result.DENY);
                p.sendActionBar(c("&eAttends d'être éjecté pour lancer ta perle !"));
                return;
            }
        }
        if ("blocks".equals(t) || "mlg".equals(t) || "pearl".equals(t)) {
            // ne jamais refuser le bloc clique sans autoriser l'objet : sinon rien n'est pose (ni seau, ni perle)
            e.setUseItemInHand(Event.Result.ALLOW);
            return;
        }
        e.setUseItemInHand(Event.Result.DENY);
        if (t == null || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - s.lastClick < 300) {
            return;
        }
        s.lastClick = now;
        switch (t) {
            case "restart" -> {
                if (s.mode != Mode.BRIDGE && s.kb == Kb.AIR) {
                    kbFail(p, s);
                } else {
                    restart(p, s);
                }
            }
            case "settings" -> openLater(p, switch (s.mode) {
                case BRIDGE -> "bridge";
                case MLG -> "mlg";
                case PEARL -> "pearl";
                default -> "kb";
            });
            case "leave" -> leave(p, true);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getView().getTopInventory().getHolder() instanceof Menu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory())) {
                ItemStack it = e.getCurrentItem();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (p.isOnline()) {
                        onMenuClick(p, m, it, e.isRightClick());
                    }
                });
            }
            return;
        }
        if (sessions.containsKey(p.getUniqueId()) && ours(p.getWorld()) && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu
                || (e.getWhoClicked() instanceof Player p && sessions.containsKey(p.getUniqueId()) && ours(p.getWorld()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (ours(e.getPlayer().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (ours(e.getPlayer().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!ours(e.getEntity().getWorld())) {
            return;
        }
        e.setCancelled(true);
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Session s = sessions.get(p.getUniqueId());
            if (s != null && s.lane != null) {
                if (s.mode != Mode.BRIDGE && s.mode != Mode.MLG && s.kb == Kb.AIR) {
                    kbFail(p, s); // une chute dans le vide en plein essai compte comme un echec
                } else {
                    restart(p, s);
                }
            } else {
                toLobby(p);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (ours(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (ours(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    /** HIGHEST et sans ignoreCancelled : un lancer de perle permis passe meme si un autre plugin l'a annule avant. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!ours(e.getEntity().getWorld()) || e.getEntity().getShooter() instanceof Player b && builder(b)) {
            return;
        }
        if (e.getEntity() instanceof org.bukkit.entity.EnderPearl && e.getEntity().getShooter() instanceof Player p) {
            if (p.getUniqueId().equals(launchingPearl)) {
                e.setCancelled(false);
                return;
            }
            Session s = sessions.get(p.getUniqueId());
            if (s != null && s.mode == Mode.PEARL && s.kb == Kb.AIR && !s.pearlLanded && (!s.pearlThrown || !hasLivePearl(p))) {
                s.pearlThrown = true;
                e.setCancelled(false);
                return;
            }
        }
        e.setCancelled(true);
    }

    /** Perle : la teleportation compte seulement pendant un essai (apres l'ejection), meme annulee par un autre plugin. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPearlTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (e.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL || e.getTo() == null
                || !ours(e.getTo().getWorld()) || !ours(e.getFrom().getWorld())) {
            return;
        }
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || s.mode != Mode.PEARL || s.kb != Kb.AIR) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(false);
    }

    /** La perle n'est « arrivee » que si personne n'a reannule la teleportation apres nous. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlTeleportDone(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (e.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL || e.getTo() == null
                || !ours(e.getTo().getWorld()) || !ours(e.getFrom().getWorld())) {
            return;
        }
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null && s.mode == Mode.PEARL && s.kb == Kb.AIR) {
            s.pearlLanded = true;
            s.stable = 0;
            s.offTarget = 0;
            s.doubleIn = -1;
        }
    }

    /** Evenement Paper du lancer par le joueur (avant ProjectileLaunchEvent) : meme regle, meme si annule avant nous. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLaunch(com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld()) || builder(p) || !(e.getProjectile() instanceof org.bukkit.entity.EnderPearl)) {
            return;
        }
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.mode == Mode.PEARL && s.kb == Kb.AIR && !s.pearlLanded && !hasLivePearl(p)) {
            e.setCancelled(false);
        } else {
            e.setCancelled(true);
        }
    }

    /** MLG : seau d'eau vide en plein essai (le seau vide est garde pour reprendre l'eau). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld()) || builder(p)) {
            return;
        }
        Session s = sessions.get(p.getUniqueId());
        Block b = e.getBlock();
        if (s == null || s.mode != Mode.MLG || s.lane == null || !"mlg".equals(tag(p.getInventory().getItem(e.getHand())))
                || !s.lane.inBox(b.getX(), b.getY(), b.getZ()) || s.lane.built.contains(Islands.key(b.getX(), b.getY(), b.getZ()))) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(false);
        s.lane.placed.add(Islands.key(b.getX(), b.getY(), b.getZ()));
        e.setItemStack(item(Material.BUCKET, 1, "&bSeau", "mlg", "&7Clic droit sur l'eau pour la reprendre"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld()) || builder(p)) {
            return;
        }
        Session s = sessions.get(p.getUniqueId());
        Block b = e.getBlockClicked();
        long k = Islands.key(b.getX(), b.getY(), b.getZ());
        if (s == null || s.lane == null || !s.lane.placed.contains(k)) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(false);
        s.lane.placed.remove(k);
        e.setItemStack(item(Material.WATER_BUCKET, 1, "&b" + MLG_NAMES[0], "mlg", "&7Utilise-le juste avant de toucher le sol"));
    }

    /** Pas d'eau qui coule dans le monde d'entrainement (la source suffit pour le MLG, tout est efface ensuite). */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(org.bukkit.event.block.BlockFromToEvent e) {
        if (ours(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (ours(e.getEntity().getWorld()) && e.isGliding()) {
            e.setCancelled(true);
        }
    }

    /** WorldGuard peut bloquer les apparitions des plugins : on retablit celle de nos PNJ. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)) {
            e.setCancelled(false);
        } else if (ours(e.getEntity().getWorld()) && e.getEntity() instanceof org.bukkit.entity.Endermite) {
            // les perles du mode perle ne font jamais apparaitre d'endermite
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (ours(e.getFrom())) {
            leave(e.getPlayer(), false);
            showAll(e.getPlayer());
        }
        stripKit(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer(), false);
        flush(e.getPlayer().getUniqueId());
        pdata.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        stripKit(p);
        if (ours(p.getWorld())) {
            // reconnecte la ou etait sa piste (peut-etre celle d'un autre maintenant) : cache, puis renvoye au lobby
            for (Session o : sessions.values()) {
                Player q = Bukkit.getPlayer(o.id);
                if (q != null && q != p) {
                    q.hidePlayer(this, p);
                    p.hidePlayer(this, q);
                }
            }
            p.setVelocity(new Vector());
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && ours(p.getWorld()) && !sessions.containsKey(p.getUniqueId())) {
                    stripKit(p);
                    toLobby(p);
                }
            }, 1L);
        }
    }

    /** Re-affiche un joueur a tout le monde (apres une reconnexion cachee). */
    void showAll(Player p) {
        for (Player q : Bukkit.getOnlinePlayers()) {
            if (q != p) {
                q.showPlayer(this, p);
                p.showPlayer(this, q);
            }
        }
    }

    /** Le kit ne doit jamais etre enregistre par Multiverse-Inventories dans un autre groupe. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (ours(e.getFrom().getWorld()) && e.getTo() != null && !ours(e.getTo().getWorld())) {
            stripKit(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld()) || !sessions.containsKey(p.getUniqueId()) || p.hasPermission("eemientrainement.admin")) {
            return;
        }
        String c = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        int colon = c.indexOf(':');
        if (colon >= 0) {
            c = c.substring(colon + 1);
        }
        List<String> ok = getConfig().getStringList("allowed-commands");
        if (ok.isEmpty()) {
            ok = List.of("entrainement", "training", "practice", "lobby", "hub", "spawn", "l", "msg", "tell", "w", "r",
                    "party", "p", "pc", "groupe", "profil", "musique", "music", "spectate", "spec", "regarder");
        }
        if (!ok.contains(c)) {
            e.setCancelled(true);
            msg(p, "&cCommande indisponible pendant l'entrainement (&f/lobby &cpour sortir).");
        }
    }

    // ================================================================ commandes

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase();
        boolean admin = s.hasPermission("eemientrainement.admin");
        switch (sub) {
            case "menu", "join", "rejoindre" -> {
                if (s instanceof Player p) {
                    if (sessions.containsKey(p.getUniqueId()) || canEnter(p)) {
                        openLater(p, "main");
                    }
                }
            }
            case "bridge" -> {
                if (s instanceof Player p) {
                    PData d = pd(p.getUniqueId());
                    if (a.length >= 2) {
                        try {
                            d.y.set("prefs.distance", dist(Integer.parseInt(a[1])));
                        } catch (NumberFormatException ex) {
                            msg(p, "&cDistance inconnue.");
                            return true;
                        }
                    }
                    if (a.length >= 3) {
                        d.y.set("prefs.style", style(a[2].toLowerCase()));
                    }
                    d.dirty = true;
                    start(p, Mode.BRIDGE);
                }
            }
            case "knockback", "kb", "clutch" -> {
                if (s instanceof Player p) {
                    PData d = pd(p.getUniqueId());
                    if (a.length >= 2) {
                        d.y.set("prefs.difficulty", diff(a[1].toLowerCase()));
                        d.dirty = true;
                    }
                    start(p, Mode.KNOCKBACK);
                }
            }
            case "mlg" -> {
                if (s instanceof Player p) {
                    PData d = pd(p.getUniqueId());
                    if (a.length >= 2) {
                        d.y.set("prefs.mlg-height", height(parse(a[1], 20)));
                    }
                    if (a.length >= 3) {
                        d.y.set("prefs.mlg-item", mlgItem(a[2].toLowerCase()));
                    }
                    d.dirty = true;
                    start(p, Mode.MLG);
                }
            }
            case "perle", "pearl" -> {
                if (s instanceof Player p) {
                    if (a.length >= 2) {
                        PData d = pd(p.getUniqueId());
                        d.y.set("prefs.pearl-difficulty", diff(a[1].toLowerCase()));
                        d.dirty = true;
                    }
                    start(p, Mode.PEARL);
                }
            }
            case "leave", "quitter" -> {
                if (s instanceof Player p) {
                    if (sessions.containsKey(p.getUniqueId())) {
                        leave(p, true);
                    } else {
                        msg(p, "&7Tu n'es pas en entrainement.");
                    }
                }
            }
            case "top", "classement" -> {
                if (a.length >= 2 && (a[1].equalsIgnoreCase("kb") || a[1].equalsIgnoreCase("knockback"))) {
                    String df = diff(a.length >= 3 ? a[2].toLowerCase() : "facile");
                    showTop(s, "kb." + df, false, "Knockback " + df);
                } else if (a.length >= 2 && (a[1].equalsIgnoreCase("perle") || a[1].equalsIgnoreCase("pearl"))) {
                    String df = diff(a.length >= 3 ? a[2].toLowerCase() : "facile");
                    showTop(s, "pearl." + df, false, "Perle " + df);
                } else if (a.length >= 2 && a[1].equalsIgnoreCase("mlg")) {
                    String itm = mlgItem(a.length >= 3 ? a[2].toLowerCase() : "eau");
                    int h = height(a.length >= 4 ? parse(a[3], 20) : 20);
                    showTop(s, "mlg." + itm + "." + h, false, "MLG " + itm + " " + h + " blocs");
                } else {
                    // /entrainement top [bridge] [distance] [style]
                    int i = a.length >= 2 && a[1].equalsIgnoreCase("bridge") ? 2 : 1;
                    int ds = dist(a.length > i ? parse(a[i], 32) : 32);
                    String st = style(a.length > i + 1 ? a[i + 1].toLowerCase() : "droit");
                    showTop(s, "bridge." + st + "." + ds, true, "Bridge " + ds + " blocs " + st);
                }
            }
            case "reload" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else {
                    reloadConfig();
                    cache.clear();
                    pearlCache.clear();
                    verifyStates();
                    msg(s, "&aConfig rechargee (les nouvelles pistes la prennent). Etats invalides : " + invalidStates + ".");
                }
            }
            case "info" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else {
                    msg(s, "&7Sessions &f" + sessions.size() + "&7, pistes &f" + lanes.size());
                    for (Session x : sessions.values()) {
                        s.sendMessage(c("&8  " + x.name + " &7" + x.mode + " piste " + (x.lane == null ? "?" : x.lane.slot)
                                + (x.mode == Mode.BRIDGE ? " " + x.dist + " " + x.style : x.mode == Mode.PEARL ? " " + x.pearlDiff : " " + x.diff)));
                    }
                }
            }
            default -> {
                msg(s, "&f/entrainement &7- menu | &f/entrainement bridge [distance] [droit|diagonale|montee]");
                msg(s, "&f/entrainement knockback [facile|moyen|difficile] &7| &f/entrainement mlg [hauteur] [eau|toile|echelle] &7| &f/entrainement perle [facile|moyen|difficile]");
                msg(s, "&f/entrainement top [bridge <distance> <style> | kb <difficulte> | mlg <objet> <hauteur> | perle <difficulte>] &7| &f/entrainement leave");
                if (admin) {
                    msg(s, "&c/entrainement reload | info");
                }
            }
        }
        return true;
    }

    static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("menu", "bridge", "knockback", "mlg", "perle", "top", "leave"));
            if (s.hasPermission("eemientrainement.admin")) {
                out.addAll(List.of("reload", "info"));
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("bridge")) {
            for (int d : distances()) {
                out.add(String.valueOf(d));
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("bridge")) {
            out.addAll(List.of(STYLES));
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("knockback") || a[0].equalsIgnoreCase("kb")
                || a[0].equalsIgnoreCase("perle") || a[0].equalsIgnoreCase("pearl"))) {
            out.addAll(List.of(DIFFS));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("top")) {
            out.addAll(List.of("bridge", "kb", "mlg", "perle"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("top")) {
            if (a[1].equalsIgnoreCase("kb") || a[1].equalsIgnoreCase("perle") || a[1].equalsIgnoreCase("pearl")) {
                out.addAll(List.of(DIFFS));
            } else if (a[1].equalsIgnoreCase("mlg")) {
                out.addAll(List.of(MLG_ITEMS));
            } else {
                for (int d : distances()) {
                    out.add(String.valueOf(d));
                }
            }
        } else if (a.length == 4 && a[0].equalsIgnoreCase("top")) {
            if (a[1].equalsIgnoreCase("mlg")) {
                for (int h : heights()) {
                    out.add(String.valueOf(h));
                }
            } else if (!a[1].equalsIgnoreCase("kb") && !a[1].equalsIgnoreCase("perle") && !a[1].equalsIgnoreCase("pearl")) {
                out.addAll(List.of(STYLES));
            }
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.startsWith(last));
        return out;
    }
}
