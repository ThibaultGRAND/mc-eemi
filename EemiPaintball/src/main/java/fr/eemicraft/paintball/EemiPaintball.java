package fr.eemicraft.paintball;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TextDisplay;
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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Paintball : Rouge contre Bleu, match a mort par equipe sur un terrain de speedball.
 * Une bille adverse elimine, reapparition a la base apres quelques secondes.
 */
public final class EemiPaintball extends JavaPlugin implements Listener {
    static final String P = "&e&l[Paintball] &r";
    /** Version de la construction : la map est refaite si elle change. */
    /** 2 (1.3.0) : terrain de speedball refait (relief, couverts, haie, cabanes d'equipe). */
    /** 3 (1.6.1) : trappes, nervures, lanternes, drapeau et arbre corriges sur les petites maps. */
    static final int BUILD_VERSION = 4; // 1.8.0 : vitres dans le mur (Facade)
    static final String TAG = "eemipaintball";
    static final Material MARKER_BASE = Material.IRON_HORSE_ARMOR;
    static final Material GRENADE_BASE = Material.FIREWORK_STAR;

    enum State { WAITING, COUNTDOWN, PLAYING, ENDING }

    static final String[] TEAM_NAME = {"Rouge", "Bleu"};
    static final String[] TEAM_CODE = {"&c", "&9"};
    static final String[] TEAM_ID = {"rouge", "bleu"};
    static final Color[] TEAM_COLOR = {Color.fromRGB(0xE0, 0x1E, 0x1E), Color.fromRGB(0x1E, 0x5A, 0xE6)};
    static final Material[] TEAM_WOOL = {Material.RED_WOOL, Material.BLUE_WOOL};
    static final Material[] TEAM_CONCRETE = {Material.RED_CONCRETE, Material.BLUE_CONCRETE};

    static final class PB {
        final UUID id;
        String name;
        int team;
        int kills;
        int deaths;
        int streak;
        int ammo;
        int grenades;
        boolean reloading;
        boolean dead;
        boolean queued;
        long nextShot;
        long protectedUntil;
        long nextGrenade;
        /** Numero de vie : une tache d'une vie precedente (recharge, tir en attente) ne fait rien. */
        int life;
        int lastClickTick = -1;
        /** Present depuis le debut du match (sinon arrive en cours : timeLeft au moment de l'arrivee). */
        boolean fromStart;
        int joinedLeft;
        Scoreboard board;

        PB(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    World world;
    State state = State.WAITING;
    final Map<UUID, PB> players = new LinkedHashMap<>();
    final List<UUID> spectators = new ArrayList<>();
    /** Joueurs qui ont bouge la tete pendant la partie : seuls eux restent en file a la fin (anti-AFK). */
    final java.util.Set<UUID> active = new java.util.HashSet<>();
    final int[] score = new int[2];
    /** Numero de partie : une tache planifiee d'une autre partie ne fait rien. */
    int gen;
    int countdown;
    int timeLeft;
    int matchTime;
    boolean twoTeams;
    BossBar bar;
    Scoreboard specBoard;
    /** Blocs peints : position -> bloc d'origine (remis en fin de partie). */
    final Map<Long, BlockData> painted = new HashMap<>();
    boolean dirty;
    /** true pendant que le plugin fait apparaitre ses entites (WorldGuard les bloque sinon). */
    boolean spawning;
    TextDisplay rules;
    /** Version du contenu des grandes maps : une map est recollee si sa version change. */
    static final int BIG_VERSION = 1;
    /**
     * Grandes maps, jouees a partir de big-map.min-players joueurs (une tiree au hasard, pas deux fois de suite).
     * Chacune a sa zone dans le monde du paintball, loin du terrain de speedball (x 0) et des autres.
     */
    final List<BigMap> bigs = List.of(
        new BigMap("foret", "Stade de la foret", "base rouge au nord, bleue au sud, stade au centre",
            1000, 40, -151, 5, 108.5, 50, 151.5, 0f, 180f),
        new BigMap("fort", "Fort de Battlebound", "ile fortifiee, base rouge au nord, bleue au sud",
            2000, 30, -112, 30, 82.5, 95, 112.5, 0f, 180f));
    /** Petite map posee au centre (speedball, chantier, western, tournoi ; voir SmallMaps), notee dans data.yml. */
    String smallMap = "speedball";
    /** Derniere petite map jouee (jamais deux fois de suite). */
    String lastSmall;
    /** Vrai apres le tirage du demarrage : la premiere partie se joue sur la map posee (celle vue depuis la salle d'attente). */
    boolean keepPosed;
    /** Map de la partie en cours (null = petite map, voir smallMap). */
    BigMap cur;
    String lastBig;
    /** La partie en cours se joue sur une grande map. */
    boolean onBig;
    /** Nuke en cours (Esteban, 17/11) : plus aucune elimination ne compte, la partie est gagnee par forcedWinner. */
    boolean nuking;
    /** Equipe gagnante imposee par une nuke (-1 = au score). */
    int forcedWinner = -1;
    String nuker;
    final Random rnd = new Random();
    NamespacedKey kItem;
    NamespacedKey kShooter;
    NamespacedKey kTeam;
    NamespacedKey kUid;
    private int buildVersion;

    static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        s.sendMessage(c(P + m));
    }

    static String legacy(String s) {
        return s.replace('&', '§');
    }

    void tell(String m) {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg(p, m);
            }
        }
    }

    List<UUID> all() {
        List<UUID> l = new ArrayList<>(players.keySet());
        l.addAll(spectators);
        return l;
    }

    /** Tache planifiee liee a la partie en cours. */
    void later(long ticks, Runnable r) {
        int g = gen;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (g == gen) {
                r.run();
            }
        }, ticks);
    }

    int cfg(String k, int def) {
        return getConfig().getInt(k, def);
    }

    double cfgD(String k, double def) {
        return getConfig().getDouble(k, def);
    }

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kItem = new NamespacedKey(this, "item");
        kShooter = new NamespacedKey(this, "shooter");
        kTeam = new NamespacedKey(this, "team");
        kUid = new NamespacedKey(this, "uid");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));
        buildVersion = data.getInt("build-version", 0);
        boolean wasDirty = data.getBoolean("dirty", false);
        // avant la 1.4.0 seul le speedball existait : cle absente = speedball pose
        String sm = data.getString("small-map", "speedball");
        smallMap = SmallMaps.get(sm) == null ? "speedball" : SmallMaps.get(sm).id();
        if (SmallMaps.get(sm) == null) {
            buildVersion = 0;
        }
        // la map posee compte comme la derniere jouee : la premiere partie apres un redemarrage en change
        lastSmall = smallMap;
        for (BigMap b : bigs) {
            b.version = data.getInt("bigs." + b.id + ".version", 0);
            b.dirty = data.getBoolean("bigs." + b.id + ".dirty", false);
        }
        // 1.1.0 : une seule grande map (foret) notee big-version / big-dirty
        if (!data.contains("bigs.foret")) {
            bigs.get(0).version = data.getInt("big-version", 0);
            bigs.get(0).dirty = data.getBoolean("big-dirty", false);
        }
        createWorld(wasDirty);
        if (world != null && getConfig().getBoolean("big-map.enabled", true)) {
            List<BigMap> todo = new ArrayList<>();
            for (BigMap b : bigs) {
                if (!b.load(this)) {
                    continue;
                }
                if (b.version != BIG_VERSION || b.dirty) {
                    // tant que le collage n'est pas fini, dirty reste vrai : un arret en plein collage le relance au demarrage suivant
                    b.dirty = true;
                    todo.add(b);
                } else {
                    b.ready = true;
                    b.freeData();
                    getLogger().info("Grande map " + b.name + " prete : " + b.spawns.get(0).size() + " points d'apparition par equipe.");
                }
            }
            saveData();
            pasteNext(todo);
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::fastTick, 5L, 4L);
        sendStatus();
    }

    @Override
    public void onDisable() {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                resetPlayer(p);
                if (inWorld(p)) {
                    p.teleport(hub());
                }
            }
        }
        players.clear();
        spectators.clear();
        restorePaint();
        removeEntities();
        // collage interrompu par l'arret : on ne laisse aucun chunk force (la map sera recollee au demarrage)
        for (BigMap bm : bigs) {
            bm.force(world, false);
        }
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

    private void createWorld(boolean wasDirty) {
        String name = getConfig().getString("world", "paintball");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
            getLogger().info("Monde " + name + " charge.");
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
        world.setGameRule(GameRules.FIRE_DAMAGE, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setSpawnLocation(waitSpawn());
        for (int cx = -3; cx <= 2; cx++) {
            for (int cz = -3; cz <= 2; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
            }
        }
        removeEntities();
        if (buildVersion != BUILD_VERSION || wasDirty) {
            if (wasDirty) {
                getLogger().info("Peinture d'une partie interrompue trouvee : la map est reconstruite.");
            }
            build();
        }
        // demarrage : une autre petite map est tiree au hasard et posee tout de suite (comme le correctif de Mario
        // Barque 2.3.2) ; la map d'avant le redemarrage reste exclue du tirage du premier lancement (lastSmall)
        List<String> ids = smallIds();
        String before = smallMap;
        ids.remove(before);
        if (getConfig().getBoolean("random-start-map", true) && !ids.isEmpty()) {
            String pick = ids.get(rnd.nextInt(ids.size()));
            getLogger().info("Demarrage : petite map tiree au hasard = " + pick + " (avant le redemarrage : " + before + ").");
            buildSmall(pick, true);
            keepPosed = smallMap.equals(pick);
        }
        lastSmall = before;
        spawnRules();
    }

    /** Reconstruit la petite map posee et la salle d'attente (demarrage, /pb build). */
    void build() {
        buildSmall(smallMap, true);
    }

    /**
     * Pose la petite map {@code id} au centre (et la salle d'attente si {@code waiting}). La peinture en cours doit
     * avoir ete remise avant (restorePaint) : la map est entierement reposee.
     */
    void buildSmall(String id, boolean waiting) {
        long t0 = System.currentTimeMillis();
        painted.clear();
        MapBuilder b = new MapBuilder(this, world);
        try {
            b.build(id, waiting);
        } catch (Throwable e) {
            // Throwable : une erreur d'initialisation de classe (ExceptionInInitializerError, NoClassDefFoundError)
            // n'est pas une RuntimeException et faisait avorter le lancement de la partie sans rien dire
            SmallMaps.Info info = SmallMaps.get(id);
            String name = info == null ? id : info.name();
            getLogger().log(java.util.logging.Level.SEVERE, "Construction de la petite map " + id + " (" + name
                + ") impossible" + (id.equals("speedball") ? "." : " : repli sur le terrain de speedball."), e);
            if (!players.isEmpty() || !spectators.isEmpty()) {
                tell("&cLa map &f" + name + " &cn'a pas pu etre construite"
                    + (id.equals("speedball") ? "." : " : on joue sur le terrain de speedball.")
                    + " &7(erreur signalee dans la console)");
            }
            if (!id.equals("speedball")) {
                buildSmall("speedball", waiting);
            }
            return;
        }
        smallMap = id;
        buildVersion = BUILD_VERSION;
        dirty = false;
        saveData();
        getLogger().info("Map " + SmallMaps.get(id).name() + " construite : " + b.ops + " blocs en "
            + (System.currentTimeMillis() - t0) + " ms.");
    }

    /** Petites maps jouables : liste small-maps du config (vide ou absente = toutes). */
    List<String> smallIds() {
        List<String> ids = new ArrayList<>();
        for (String s : getConfig().getStringList("small-maps")) {
            SmallMaps.Info i = SmallMaps.get(s);
            if (i != null && !ids.contains(i.id())) {
                ids.add(i.id());
            }
        }
        return ids.isEmpty() ? new ArrayList<>(SmallMaps.ids()) : ids;
    }

    private void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("build-version", buildVersion);
        y.set("small-map", smallMap);
        for (BigMap b : bigs) {
            y.set("bigs." + b.id + ".version", b.version);
            y.set("bigs." + b.id + ".dirty", b.dirty);
        }
        y.set("dirty", dirty);
        try {
            getDataFolder().mkdirs();
            y.save(new File(getDataFolder(), "data.yml"));
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    /** Colle les grandes maps une par une (une seule a la fois pour limiter le lag). */
    void pasteNext(List<BigMap> todo) {
        if (todo.isEmpty() || world == null) {
            return;
        }
        BigMap b = todo.remove(0);
        getLogger().info("Collage de la grande map " + b.name + " en cours (quelques secondes)...");
        b.force(world, true);
        b.paste(this, world, ok -> {
            if (ok) {
                b.version = BIG_VERSION;
                b.dirty = false;
                b.ready = true;
                saveData();
            }
            if (!(state == State.PLAYING && cur == b)) {
                b.force(world, false);
            }
            b.freeData();
            pasteNext(todo);
        });
    }

    BigMap bigAt(int x, int y, int z) {
        for (BigMap b : bigs) {
            if (b.inside(x, y, z)) {
                return b;
            }
        }
        return null;
    }

    Location waitSpawn() {
        return new Location(world, 0.5, MapBuilder.WY + 1, 0.5, 0f, 0f);
    }

    Location standSpawn() {
        if (onBig && cur != null && (state == State.PLAYING || state == State.ENDING)) {
            return cur.stand(world);
        }
        return new Location(world, 0.5, MapBuilder.FY + 6.5, 29.5, 180f, 20f);
    }

    Location baseSpawn(int team) {
        if (onBig && cur != null) {
            // Rouge au nord regarde vers le sud (+z), Bleu au sud vers le nord
            return cur.spawn(world, team, rnd);
        }
        double x = team == 0 ? -28.5 : 29.5;
        double z = rnd.nextInt(11) - 5 + 0.5;
        return new Location(world, x, MapBuilder.FY + 1, z, team == 0 ? -90f : 90f, 0f);
    }

    Location hub() {
        ConfigurationSection h = getConfig().getConfigurationSection("hub");
        World w = h == null ? null : Bukkit.getWorld(h.getString("world", "lobby"));
        if (w == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(w, h.getDouble("x", 722.5), h.getDouble("y", 51), h.getDouble("z", 650.5),
            (float) h.getDouble("yaw", 90), (float) h.getDouble("pitch", 0));
    }

    boolean inWorld(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    // ================================================================ entites

    boolean ours(Entity e) {
        return e.getScoreboardTags().contains(TAG);
    }

    void removeEntities() {
        if (world == null) {
            return;
        }
        for (Entity e : world.getEntities()) {
            if (ours(e)) {
                e.remove();
            }
        }
        rules = null;
    }

    /** Panneau des regles dans la salle d'attente. */
    void spawnRules() {
        if (world == null || (rules != null && rules.isValid())) {
            return;
        }
        Location l = new Location(world, 0.5, MapBuilder.WY + 2.6, -6.2);
        spawning = true;
        try {
            rules = world.spawn(l, TextDisplay.class, t -> {
                t.text(c("&e&lPAINTBALL\n&cRouge &7contre &9Bleu\n\n&fClic droit &7: tirer une bille\n&fF &7: recharger\n"
                    + "&fGrenade &7: clic droit, explose en 1,5 s\n&7Une bille adverse = elimine\n&7" + cfg("score-limit", 30)
                    + " eliminations ou " + cfg("match-time", 360) / 60 + " min"));
                t.setBillboard(Display.Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
                t.setShadowed(true);
                t.setPersistent(false);
                t.addScoreboardTag(TAG);
            });
        } finally {
            spawning = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world)) {
            return;
        }
        if (spawning) {
            // WorldGuard bloque les apparitions par plugin : on retablit les notres
            e.setCancelled(false);
        } else if (e instanceof ProjectileLaunchEvent pe && !(pe.getEntity().getShooter() instanceof Player p && builder(p))) {
            // aucun autre projectile que les billes
            e.setCancelled(true);
        }
    }

    // ================================================================ objets

    ItemStack tagged(Material m, int amount, String kind, String name, String model, List<String> lore) {
        ItemStack it = new ItemStack(m, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore != null) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(c(s));
            }
            meta.lore(l);
        }
        meta.setItemModel(new NamespacedKey("eemicraft", model));
        meta.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, kind);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    int magazine() {
        return Math.max(1, Math.min(99, cfg("magazine", 20)));
    }

    ItemStack marker(int team, int ammo) {
        ItemStack it = tagged(MARKER_BASE, 1, "marker", TEAM_CODE[team] + "&lMarqueur de paintball",
            "paintball/marqueur_" + TEAM_ID[team], List.of("&7Clic droit : tirer une bille", "&7F : recharger", "&8Quantite = billes restantes"));
        ItemMeta m = it.getItemMeta();
        m.setMaxStackSize(magazine());
        it.setItemMeta(m);
        it.setAmount(Math.max(1, Math.min(magazine(), ammo)));
        return it;
    }

    ItemStack grenade(int team, int n) {
        ItemStack it = tagged(GRENADE_BASE, 1, "grenade", TEAM_CODE[team] + "&lGrenade de peinture",
            "paintball/grenade_" + TEAM_ID[team], List.of("&7Clic droit : lancer", "&7Explose apres 1,5 s, rayon " + cfg("grenade-radius", 3)));
        ItemMeta m = it.getItemMeta();
        m.setMaxStackSize(16);
        it.setItemMeta(m);
        it.setAmount(Math.max(1, Math.min(16, n)));
        return it;
    }

    ItemStack ball(int team) {
        return tagged(team == 0 ? Material.RED_DYE : Material.BLUE_DYE, 1, "ball", TEAM_CODE[team] + "Bille de peinture",
            "paintball/bille_" + TEAM_ID[team], null);
    }

    ItemStack armor(Material m, int team, String name) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(TEAM_COLOR[team]);
        meta.displayName(c(TEAM_CODE[team] + name));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
        meta.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, "armor");
        it.setItemMeta(meta);
        return it;
    }

    String kind(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(kItem, PersistentDataType.STRING);
    }

    void giveKit(Player p, PB pb) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(0, marker(pb.team, pb.ammo));
        if (pb.grenades > 0) {
            inv.setItem(1, grenade(pb.team, pb.grenades));
        }
        inv.setHelmet(armor(Material.LEATHER_HELMET, pb.team, "Masque " + TEAM_NAME[pb.team]));
        inv.setChestplate(armor(Material.LEATHER_CHESTPLATE, pb.team, "Maillot " + TEAM_NAME[pb.team]));
        inv.setHeldItemSlot(0);
    }

    void updateMarker(Player p, PB pb) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            if ("marker".equals(kind(inv.getItem(i)))) {
                inv.getItem(i).setAmount(Math.max(1, Math.min(magazine(), pb.ammo)));
                return;
            }
        }
    }

    void updateGrenades(Player p, PB pb) {
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if ("grenade".equals(kind(inv.getItem(i)))) {
                slot = i;
                break;
            }
        }
        if (pb.grenades <= 0) {
            if (slot >= 0) {
                inv.setItem(slot, null);
            }
        } else if (slot >= 0) {
            inv.getItem(slot).setAmount(Math.min(16, pb.grenades));
        } else {
            inv.setItem(1, grenade(pb.team, pb.grenades));
        }
    }

    /** Retire tout ce que le plugin a donne (kit, armure), sans toucher au reste. */
    void stripKit(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (kind(inv.getItem(i)) != null) {
                inv.setItem(i, null);
            }
        }
        if (kind(inv.getHelmet()) != null) {
            inv.setHelmet(null);
        }
        if (kind(inv.getChestplate()) != null) {
            inv.setChestplate(null);
        }
    }

    // ================================================================ deroulement

    /** Tableau de la salle d'attente : une equipe sans pseudo au-dessus des tetes. */
    Scoreboard waitBoard;
    final java.util.Set<UUID> boardWarned = new java.util.HashSet<>();

    /**
     * Pseudos caches partout dans le monde du paintball (carte #51) : salle d'attente comprise, et le tableau du jeu
     * est remis toutes les secondes si un autre plugin l'a remplace (sinon les pseudos reapparaissent).
     */
    void enforceHiddenNames() {
        if (waitBoard == null) {
            waitBoard = Bukkit.getScoreboardManager().getNewScoreboard();
            Team t = waitBoard.registerNewTeam("pb_attente");
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        Team wt = waitBoard.getTeam("pb_attente");
        boolean game = state == State.PLAYING || state == State.ENDING;
        for (PB pb : players.values()) {
            Player p = Bukkit.getPlayer(pb.id);
            if (p == null || !inWorld(p)) {
                continue;
            }
            Scoreboard want = game && pb.board != null ? pb.board : waitBoard;
            if (p.getScoreboard() != want) {
                if (game && boardWarned.add(pb.id)) {
                    getLogger().info("Tableau de " + p.getName() + " remplace par un autre plugin : remis (pseudos caches).");
                }
                p.setScoreboard(want);
            }
            if (want == waitBoard) {
                if (wt != null && !wt.hasEntry(pb.name)) {
                    wt.addEntry(pb.name);
                }
            } else {
                syncTeams(pb.board);
            }
        }
        if (game && specBoard != null) {
            syncTeams(specBoard);
            for (UUID id : spectators) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && inWorld(p) && p.getScoreboard() != specBoard) {
                    p.setScoreboard(specBoard);
                }
            }
        }
    }

    private void tick() {
        if (world == null) {
            return;
        }
        enforceHiddenNames();
        int min = Math.max(1, cfg("min-players", 2));
        int max = cfg("max-players", 16);
        switch (state) {
            case WAITING -> {
                if (players.size() >= min) {
                    state = State.COUNTDOWN;
                    countdown = cfg("countdown", 20);
                    tell("&aLa partie commence dans &e" + countdown + " s&a.");
                    sendStatus();
                }
                spawnRules();
            }
            case COUNTDOWN -> {
                if (players.size() < min) {
                    state = State.WAITING;
                    tell("&cPas assez de joueurs, decompte annule.");
                    sendStatus();
                    return;
                }
                int full = cfg("full-countdown", 5);
                if (players.size() >= max && countdown > full) {
                    countdown = full;
                    tell("&aPartie pleine : depart dans &e" + countdown + " s&a.");
                }
                countdown--;
                if (countdown <= 0) {
                    start();
                } else if (countdown <= 5 || countdown % 10 == 0) {
                    for (UUID id : players.keySet()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            p.showTitle(Title.title(c("&e" + countdown), c("&cRouge &7contre &9Bleu"),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                        }
                    }
                }
            }
            case PLAYING -> {
                timeLeft--;
                if (bar != null) {
                    bar.progress(Math.max(0f, Math.min(1f, timeLeft / (float) Math.max(1, matchTime))));
                    bar.name(c("&fTemps &e" + clock(timeLeft) + "  &cRouge " + score[0] + " &7- &9" + score[1] + " Bleu"));
                }
                if (timeLeft == 60 || timeLeft == 30 || timeLeft == 10) {
                    tell("&eEncore &f" + timeLeft + " s &e!");
                }
                updateBoards();
                if (timeLeft <= 0) {
                    end();
                }
            }
            default -> {
            }
        }
        // billes perdues
        int life = cfg("ball-life", 60);
        for (Snowball s : world.getEntitiesByClass(Snowball.class)) {
            if (ours(s) && s.getTicksLived() > life) {
                s.remove();
            }
        }
    }

    /** Barre d'action (billes, recharge, protection). */
    private void fastTick() {
        if (state != State.PLAYING) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PB pb : players.values()) {
            Player p = Bukkit.getPlayer(pb.id);
            if (p == null || pb.dead) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            if (pb.reloading) {
                sb.append("&eRecharge...");
            } else {
                sb.append("&fBilles &e").append(pb.ammo).append("&7/").append(magazine());
            }
            sb.append("  &8|  &fGrenades &e").append(pb.grenades);
            if (now < pb.protectedUntil) {
                sb.append("  &8|  &bProtege");
                world.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
            }
            p.sendActionBar(c(sb.toString()));
        }
    }

    static String clock(int s) {
        s = Math.max(0, s);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    void start() {
        gen++;
        state = State.PLAYING;
        nuking = false;
        forcedWinner = -1;
        nuker = null;
        active.clear();
        // le lit « Retour au lobby » ne sert plus pendant la partie (le kit arrive 2 ticks plus tard)
        for (PB pb0 : players.values()) {
            Player p0 = Bukkit.getPlayer(pb0.id);
            if (p0 != null && inWorld(p0) && "leave".equals(kind(p0.getInventory().getItem(8)))) {
                p0.getInventory().setItem(8, null);
            }
        }
        score[0] = 0;
        score[1] = 0;
        restorePaint();
        removeBallsAndGrenades();
        matchTime = Math.max(30, cfg("match-time", 360));
        timeLeft = matchTime;
        // equipes equilibrees
        List<PB> list = new ArrayList<>(players.values());
        Collections.shuffle(list, rnd);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).team = i % 2;
        }
        twoTeams = count(0) > 0 && count(1) > 0;
        // grandes maps seulement a partir de big-map.min-players joueurs (4 contre 4 par defaut), tirees au hasard
        cur = null;
        if (getConfig().getBoolean("big-map.enabled", true) && players.size() >= Math.max(2, cfg("big-map.min-players", 8))) {
            List<BigMap> ok = new ArrayList<>();
            for (BigMap b : bigs) {
                if (b.ready && !b.pasting) {
                    ok.add(b);
                }
            }
            if (ok.size() > 1) {
                ok.removeIf(b -> b.id.equals(lastBig));
            }
            if (!ok.isEmpty()) {
                cur = ok.get(rnd.nextInt(ok.size()));
                lastBig = cur.id;
            }
        }
        onBig = cur != null;
        if (onBig) {
            cur.force(world, true);
        } else {
            // petite map tiree au hasard (jamais deux fois de suite), reconstruite si ce n'est pas celle qui est posee
            List<String> ids = smallIds();
            if (ids.size() > 1) {
                ids.remove(lastSmall);
            }
            String pick = keepPosed && ids.contains(smallMap) ? smallMap : ids.get(rnd.nextInt(ids.size()));
            keepPosed = false;
            lastSmall = pick;
            if (!pick.equals(smallMap)) {
                buildSmall(pick, false);
            }
        }
        bar = BossBar.bossBar(c("&fTemps &e" + clock(timeLeft)), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        specBoard = Bukkit.getScoreboardManager().getNewScoreboard();
        setupTeams(specBoard);
        for (PB pb : players.values()) {
            pb.kills = 0;
            pb.deaths = 0;
            pb.streak = 0;
            pb.fromStart = true;
            pb.board = Bukkit.getScoreboardManager().getNewScoreboard();
            setupTeams(pb.board);
        }
        for (UUID id : spectators) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(specBoard);
                p.showBossBar(bar);
                if (onBig) {
                    p.teleport(standSpawn());
                }
            }
        }
        for (PB pb : players.values()) {
            Player p = Bukkit.getPlayer(pb.id);
            if (p == null) {
                continue;
            }
            p.setScoreboard(pb.board);
            p.showBossBar(bar);
            spawnPlayer(p, pb, true);
            p.showTitle(Title.title(c(TEAM_CODE[pb.team] + "&lEQUIPE " + TEAM_NAME[pb.team].toUpperCase()),
                c("&7" + cfg("score-limit", 30) + " eliminations pour gagner"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
        tell("&e&lC'est parti ! &cRouge &7(" + count(0) + ") &7contre &9Bleu &7(" + count(1) + ")");
        tell("&7Map : &f" + (onBig ? cur.name + " &7(grande map, " + cur.desc + ")" : SmallMaps.get(smallMap).name()));
        tell("&7Clic droit : tirer. &fF &7: recharger. Une bille adverse elimine. Grenade : clic droit.");
        updateBoards();
        sendStatus();
    }

    int count(int team) {
        int n = 0;
        for (PB pb : players.values()) {
            if (pb.team == team) {
                n++;
            }
        }
        return n;
    }

    void setupTeams(Scoreboard sb) {
        for (int t = 0; t < 2; t++) {
            Team team = sb.registerNewTeam("pb_" + TEAM_ID[t]);
            team.prefix(c(TEAM_CODE[t] + "[" + TEAM_NAME[t] + "] "));
            team.color(t == 0 ? net.kyori.adventure.text.format.NamedTextColor.RED : net.kyori.adventure.text.format.NamedTextColor.BLUE);
            team.setAllowFriendlyFire(false);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            // carte #51 : plus aucun pseudo au-dessus des tetes (on ne repere pas les autres a travers les murs)
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        syncTeams(sb);
    }

    void syncTeams(Scoreboard sb) {
        if (sb == null) {
            return;
        }
        for (PB pb : players.values()) {
            Team t = sb.getTeam("pb_" + TEAM_ID[pb.team]);
            if (t != null && !t.hasEntry(pb.name)) {
                t.addEntry(pb.name);
            }
        }
    }

    /** Apparition a la base avec le kit (debut de partie ou reapparition). */
    void spawnPlayer(Player p, PB pb, boolean first) {
        pb.dead = false;
        pb.life++;
        pb.reloading = false;
        pb.queued = false;
        pb.ammo = magazine();
        pb.grenades = Math.max(0, cfg("grenades-per-life", 1));
        pb.protectedUntil = System.currentTimeMillis() + Math.max(0, cfg("spawn-protection", 3)) * 1000L;
        p.teleport(baseSpawn(pb.team));
        p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setCooldown(MARKER_BASE, 0);
        if (first) {
            // deja dans le monde (salle d'attente), mais on garde le delai de 2 ticks des changements de monde
            int life = pb.life;
            later(2L, () -> {
                if (p.isOnline() && pb.life == life && players.get(pb.id) == pb) {
                    giveKit(p, pb);
                }
            });
        } else {
            giveKit(p, pb);
        }
    }

    // ================================================================ tir

    void tryFire(Player p, PB pb) {
        if (state != State.PLAYING || pb.dead) {
            return;
        }
        if (pb.reloading) {
            return;
        }
        if (pb.ammo <= 0) {
            startReload(p, pb);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < pb.nextShot) {
            // clic arrive trop tot (clic maintenu = tous les 4 ticks) : un tir en attente, a la fin du delai
            if (!pb.queued) {
                pb.queued = true;
                int life = pb.life;
                long ticks = Math.max(1, Math.round((pb.nextShot - now) / 50.0));
                later(ticks, () -> {
                    pb.queued = false;
                    if (pb.life == life && p.isOnline() && "marker".equals(kind(p.getInventory().getItemInMainHand()))) {
                        fire(p, pb);
                    }
                });
            }
            return;
        }
        fire(p, pb);
    }

    void fire(Player p, PB pb) {
        if (state != State.PLAYING || pb.dead || pb.reloading || pb.ammo <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now + 10 < pb.nextShot) {
            return;
        }
        pb.nextShot = now + Math.max(50, cfg("fire-delay-ms", 250));
        pb.ammo--;
        pb.protectedUntil = 0;
        double spread = cfgD("spread", 0.03);
        Vector dir = p.getEyeLocation().getDirection().normalize();
        dir.add(new Vector(rnd.nextGaussian() * spread, rnd.nextGaussian() * spread, rnd.nextGaussian() * spread));
        Vector vel = dir.normalize().multiply(cfgD("ball-speed", 2.6));
        int team = pb.team;
        spawning = true;
        try {
            p.launchProjectile(Snowball.class, vel, s -> {
                s.setItem(ball(team));
                s.setPersistent(false);
                s.addScoreboardTag(TAG);
                PersistentDataContainer pdc = s.getPersistentDataContainer();
                pdc.set(kShooter, PersistentDataType.STRING, pb.id.toString());
                pdc.set(kTeam, PersistentDataType.INTEGER, team);
            });
        } finally {
            spawning = false;
        }
        world.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 0.9f, 1.9f);
        world.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f);
        updateMarker(p, pb);
        if (pb.ammo <= 0) {
            startReload(p, pb);
        }
    }

    void startReload(Player p, PB pb) {
        if (pb.reloading || pb.dead || pb.ammo >= magazine() || state != State.PLAYING) {
            return;
        }
        pb.reloading = true;
        int ticks = (int) Math.max(5, Math.round(cfgD("reload-time", 2.0) * 20));
        p.setCooldown(MARKER_BASE, ticks);
        p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.8f, 1.3f);
        int life = pb.life;
        later(ticks, () -> {
            if (pb.life != life || !pb.reloading) {
                return;
            }
            pb.reloading = false;
            pb.ammo = magazine();
            if (p.isOnline()) {
                updateMarker(p, pb);
                p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8f, 1.4f);
            }
        });
    }

    void throwGrenade(Player p, PB pb) {
        if (state != State.PLAYING || pb.dead || pb.grenades <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < pb.nextGrenade) {
            return;
        }
        pb.nextGrenade = now + 800;
        pb.grenades--;
        pb.protectedUntil = 0;
        updateGrenades(p, pb);
        ItemStack stack = grenade(pb.team, 1);
        ItemMeta m = stack.getItemMeta();
        // objet unique : deux grenades au sol ne fusionnent pas
        m.getPersistentDataContainer().set(kUid, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(m);
        Location from = p.getEyeLocation().subtract(0, 0.2, 0);
        Vector v = from.getDirection().normalize().multiply(cfgD("grenade-throw", 0.95)).add(new Vector(0, 0.18, 0));
        int team = pb.team;
        Item item;
        spawning = true;
        try {
            item = world.dropItem(from, stack, i -> {
                i.setCanPlayerPickup(false);
                i.setCanMobPickup(false);
                i.setPickupDelay(32767);
                i.setPersistent(false);
                i.setInvulnerable(true);
                i.setGlowing(true);
                i.addScoreboardTag(TAG);
                i.setVelocity(v);
            });
        } finally {
            spawning = false;
        }
        item.setVelocity(v);
        world.playSound(from, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.7f);
        world.playSound(from, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1.6f);
        int fuse = Math.max(5, cfg("grenade-fuse", 30));
        Particle.DustOptions dust = new Particle.DustOptions(TEAM_COLOR[team], 1.0f);
        for (int t = 2; t < fuse; t += 2) {
            later(t, () -> {
                if (item.isValid()) {
                    world.spawnParticle(Particle.DUST, item.getLocation().add(0, 0.2, 0), 3, 0.05, 0.05, 0.05, 0, dust);
                }
            });
        }
        later(fuse, () -> {
            if (item.isValid()) {
                Location at = item.getLocation().add(0, 0.3, 0);
                item.remove();
                explode(at, team, pb.id);
            }
        });
    }

    void explode(Location at, int team, UUID thrower) {
        double r = Math.max(1, cfg("grenade-radius", 3));
        Particle.DustOptions big = new Particle.DustOptions(TEAM_COLOR[team], 2.2f);
        world.spawnParticle(Particle.DUST, at, 160, r * 0.45, r * 0.35, r * 0.45, 0, big);
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.playSound(at, Sound.ENTITY_SPLASH_POTION_BREAK, 1.4f, 0.8f);
        world.playSound(at, Sound.ENTITY_SLIME_SQUISH_SMALL, 1.4f, 0.6f);
        // peinture
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > r + 0.3) {
                        continue;
                    }
                    if (rnd.nextDouble() < 1.0 - d / (r + 1.5) * 0.6) {
                        Block b = at.getBlock().getRelative(dx, dy, dz);
                        if (exposed(b)) {
                            paint(b, team);
                        }
                    }
                }
            }
        }
        // ennemis dans le rayon, sans mur entre eux
        PB killer = players.get(thrower);
        for (PB pv : new ArrayList<>(players.values())) {
            if (pv.dead || pv.team == team) {
                continue;
            }
            Player v = Bukkit.getPlayer(pv.id);
            if (v == null || !inWorld(v) || v.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location body = v.getLocation().add(0, 1.0, 0);
            double dist = body.distance(at);
            if (dist > r + 0.5) {
                continue;
            }
            if (dist > 0.3) {
                Vector dir = body.toVector().subtract(at.toVector()).normalize();
                RayTraceResult hit = world.rayTraceBlocks(at, dir, dist, FluidCollisionMode.NEVER, true);
                if (hit != null && hit.getHitBlock() != null) {
                    continue;
                }
            }
            if (System.currentTimeMillis() < pv.protectedUntil) {
                continue;
            }
            eliminate(v, pv, killer, team, "grenade");
        }
    }

    // ================================================================ peinture

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    boolean exposed(Block b) {
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (!b.getRelative(f).getType().isOccluding()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Peint un bloc aux couleurs de l'equipe : vraie modification, notee avec son etat d'origine et remise en fin de partie.
     * Seuls le sol, les lignes et les gonflables sont peints (jamais filets, barrieres, bases, gradins).
     */
    void paint(Block b, int team) {
        if (!b.getWorld().equals(world)) {
            return;
        }
        int x = b.getX();
        int y = b.getY();
        int z = b.getZ();
        BigMap zone = bigAt(x, y, z);
        boolean bigZone = zone != null;
        // petites maps : la ligne du filet (|x| = NX, |z| = NZ : poteaux, panneaux du filet) n'est jamais peinte
        if (!bigZone &&(Math.abs(x) >= MapBuilder.NX || Math.abs(z) >= MapBuilder.NZ || y < MapBuilder.FY || y > MapBuilder.FY + 12)) {
            return;
        }
        long k = key(x, y, z);
        BlockData orig = painted.get(k);
        Material base = orig == null ? b.getType() : orig.getMaterial();
        if (orig == null && !(bigZone ? BigMap.PAINTABLE.contains(base) : MapBuilder.paintable(smallMap, base))) {
            return;
        }
        Material target = base.name().endsWith("_WOOL") ? TEAM_WOOL[team] : TEAM_CONCRETE[team];
        if (b.getType() == target) {
            return;
        }
        if (orig == null) {
            painted.put(k, b.getBlockData());
            // marqueurs separes : un crash ne fait refaire que la map qui a ete peinte
            if (bigZone) {
                if (!zone.dirty) {
                    zone.dirty = true;
                    saveData();
                }
            } else if (!dirty) {
                dirty = true;
                saveData();
            }
        }
        b.setType(target, false);
    }

    void splat(Block hit, BlockFace face, int team) {
        paint(hit, team);
        double chance = cfgD("splat-chance", 0.45);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if ((a == 0 && b == 0) || rnd.nextDouble() >= chance) {
                    continue;
                }
                Block n = switch (face == null ? BlockFace.UP : face) {
                    case UP, DOWN -> hit.getRelative(a, 0, b);
                    case NORTH, SOUTH -> hit.getRelative(a, b, 0);
                    default -> hit.getRelative(0, a, b);
                };
                if (exposed(n)) {
                    paint(n, team);
                }
            }
        }
    }

    void restorePaint() {
        if (world == null) {
            painted.clear();
            return;
        }
        for (Map.Entry<Long, BlockData> e : painted.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 38);
            int z = (int) (k << 26 >> 38);
            int y = (int) (k & 0xFFF);
            if (y >= 2048) {
                y -= 4096;
            }
            world.getBlockAt(x, y, z).setBlockData(e.getValue(), false);
        }
        painted.clear();
        boolean save = dirty;
        dirty = false;
        for (BigMap bm : bigs) {
            // une map en cours de collage garde son marqueur (le collage n'est pas fini)
            if (!bm.pasting) {
                // une map dont le collage a echoue (pas ready) garde son marqueur : elle sera recollee au demarrage
                if (bm.dirty && bm.ready) {
                    bm.dirty = false;
                    save = true;
                }
                bm.force(world, false);
            }
        }
        if (save) {
            saveData();
        }
    }

    void removeBallsAndGrenades() {
        if (world == null) {
            return;
        }
        for (Entity e : world.getEntities()) {
            if (ours(e) && (e instanceof Snowball || e instanceof Item || e instanceof org.bukkit.entity.BlockDisplay)) {
                e.remove();
            }
        }
    }

    // ================================================================ eliminations

    void eliminate(Player v, PB pv, PB killer, int byTeam, String how) {
        if (state != State.PLAYING || pv.dead || nuking) {
            return;
        }
        pv.dead = true;
        pv.life++;
        pv.deaths++;
        pv.streak = 0;
        pv.reloading = false;
        if (byTeam != pv.team) {
            score[byTeam]++;
        }
        String kname = killer == null ? TEAM_CODE[byTeam] + "?" : TEAM_CODE[killer.team] + killer.name;
        tell(TEAM_CODE[pv.team] + pv.name + " &7elimine par " + kname + (how.equals("grenade") ? " &8(grenade)" : ""));
        Particle.DustOptions dust = new Particle.DustOptions(TEAM_COLOR[byTeam], 1.8f);
        world.spawnParticle(Particle.DUST, v.getLocation().add(0, 1, 0), 70, 0.4, 0.7, 0.4, 0, dust);
        world.spawnParticle(Particle.ITEM_SLIME, v.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
        world.playSound(v.getLocation(), Sound.ENTITY_SLIME_SQUISH_SMALL, 1f, 0.8f);
        v.getInventory().clear();
        v.setGameMode(GameMode.SPECTATOR);
        v.showTitle(Title.title(c("&c&lELIMINE !"), c("&7par " + kname),
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2200), Duration.ofMillis(400))));
        if (killer != null) {
            killer.kills++;
            killer.streak++;
            // pas la recompense "kill" d'EemiCore (3 pieces + 10 xp, trop en paintball) :
            // seulement kill-coins pieces par elimination (0 = rien)
            int killCoins = Math.max(0, cfg("kill-coins", 1));
            if (killCoins > 0) eemi("eemi give " + killer.name + " coins " + killCoins);
            Player kp = Bukkit.getPlayer(killer.id);
            if (kp != null) {
                kp.playSound(kp.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.2f);
            }
            int streak = Math.max(2, cfg("streak", 3));
            if (killer.streak % streak == 0 && !killer.dead) {
                killer.grenades++;
                tell("&6&lSERIE ! " + TEAM_CODE[killer.team] + killer.name + " &6enchaine &f" + killer.streak
                    + " &6eliminations sans mourir &7(+1 grenade)");
                if (kp != null) {
                    updateGrenades(kp, killer);
                    kp.playSound(kp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
                }
            }
            // NUKE (Esteban, 17/11) : nuke-streak eliminations sans mourir = la map est repeinte et la partie gagnee
            int nukeAt = cfg("nuke-streak", 10);
            if (nukeAt > 0 && !killer.dead && killer.team != pv.team) {
                int left = nukeAt - killer.streak;
                if (left <= 0 && kp != null) {
                    nuke(kp, killer);
                    return;
                }
                if (left > 0 && left <= 3 && kp != null) {
                    msg(kp, "&c&lNUKE &7dans &f" + left + " &7élimination" + (left > 1 ? "s" : "") + " sans mourir !");
                    kp.playSound(kp.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f + 0.4f * (3 - left));
                }
            }
        }
        UUID id = pv.id;
        int life = pv.life;
        later(Math.max(1, cfg("respawn-delay", 3)) * 20L, () -> {
            PB cur = players.get(id);
            Player p = Bukkit.getPlayer(id);
            if (state == State.PLAYING && cur == pv && pv.life == life && p != null && inWorld(p)) {
                spawnPlayer(p, pv, false);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        });
        updateBoards();
        if (score[byTeam] >= Math.max(1, cfg("score-limit", 30))) {
            end();
        }
    }

    // ================================================================ nuke

    /**
     * NUKE (Esteban, 17/11/2026) : une bombe tombe du ciel au centre de la map, explose, une onde de peinture
     * recouvre toute la map de la couleur du tireur, puis la partie se termine sur la victoire de son equipe.
     * Pendant l'animation (~5 s) plus aucune elimination ne compte. La peinture passe par paint() : seuls les blocs
     * peignables changent, et tout est remis en fin de partie comme d'habitude.
     */
    void nuke(Player kp, PB killer) {
        nuking = true;
        forcedWinner = killer.team;
        nuker = killer.name;
        int team = killer.team;
        String who = TEAM_CODE[team] + killer.name;
        tell("&4&l☢ NUKE ! " + who + " &7enchaîne &f" + killer.streak + " &7éliminations sans mourir et lance une nuke !");
        Bukkit.broadcast(c(P + who + " &7a lancé une &4&lNUKE &7au Paintball !"));
        int coins = Math.max(0, cfg("nuke-coins", 20));
        if (coins > 0) eemi("eemi give " + killer.name + " coins " + coins);
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(c("&4&l☢ NUKE ☢"), c(who + " &7a lancé une nuke"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2600), Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.6f);
        }
        // zone a repeindre : grande map en cours, ou petite map au centre
        final int x1, x2, z1, z2, yBot, yTop;
        if (onBig && cur != null) {
            x1 = cur.ox; x2 = cur.ox + cur.w - 1; z1 = cur.oz; z2 = cur.oz + cur.l - 1; yBot = cur.oy; yTop = cur.oy + cur.h - 1;
        } else {
            x1 = -MapBuilder.NX + 1; x2 = MapBuilder.NX - 1; z1 = -MapBuilder.NZ + 1; z2 = MapBuilder.NZ - 1;
            yBot = MapBuilder.FY; yTop = MapBuilder.FY + 12;
        }
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        int ground = Math.min(yTop, Math.max(yBot, world.getHighestBlockYAt(cx, cz)));
        // la bombe : un bloc de TNT geant qui tombe en 50 ticks
        final org.bukkit.entity.BlockDisplay[] bomb = new org.bukkit.entity.BlockDisplay[1];
        Location top = new Location(world, cx + 0.5, ground + 60, cz + 0.5);
        spawning = true;
        try {
            bomb[0] = world.spawn(top, org.bukkit.entity.BlockDisplay.class, d -> {
                d.setBlock(Material.TNT.createBlockData());
                d.setPersistent(false);
                d.setTeleportDuration(2);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-2f, 0f, -2f),
                    new org.joml.AxisAngle4f(), new org.joml.Vector3f(4f, 6f, 4f), new org.joml.AxisAngle4f()));
                d.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, "nuke");
                d.addScoreboardTag(TAG); // retiree par removeBallsAndGrenades() si la partie s'arrete pendant la chute
            });
        } finally {
            spawning = false;
        }
        final int fall = 50;
        for (int t = 1; t <= fall; t++) {
            final int tt = t;
            later(t, () -> {
                if (bomb[0] == null || !bomb[0].isValid()) return;
                double y = ground + 60 - 60.0 * (tt * tt) / (double) (fall * fall);
                bomb[0].teleport(new Location(world, cx + 0.5, y, cz + 0.5));
                if (tt % 5 == 0) world.spawnParticle(Particle.SMOKE, bomb[0].getLocation().add(0, 6, 0), 20, 1, 0.5, 1, 0.02);
                if (tt % 10 == 0) world.playSound(bomb[0].getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 4f, 0.5f + tt / 100f);
            });
        }
        later(fall + 1, () -> {
            if (bomb[0] != null) bomb[0].remove();
            Location c0 = new Location(world, cx + 0.5, ground + 1, cz + 0.5);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, c0, 6, 3, 2, 3, 0);
            world.spawnParticle(Particle.DUST, c0, 600, 12, 6, 12, 0, new Particle.DustOptions(TEAM_COLOR[team], 3f));
            for (UUID id : all()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);
            }
            // onde de peinture : un anneau par tick, du centre vers les bords
            int maxR = (int) Math.ceil(Math.hypot(Math.max(cx - x1, x2 - cx), Math.max(cz - z1, z2 - cz))) + 1;
            int waves = Math.max(10, Math.min(40, maxR / 3));
            for (int w = 0; w < waves; w++) {
                final double rIn = maxR * w / (double) waves, rOut = maxR * (w + 1) / (double) waves;
                later(1 + w, () -> {
                    for (int x = x1; x <= x2; x++)
                        for (int z = z1; z <= z2; z++) {
                            double d = Math.hypot(x - cx, z - cz);
                            if (d < rIn || d >= rOut) continue;
                            paintColumn(x, z, yBot, yTop, team);
                        }
                    world.playSound(new Location(world, cx, ground, cz), Sound.ENTITY_SLIME_SQUISH, 3f, 0.6f);
                });
            }
            later(waves + 20, this::end);
        });
    }

    /** Repeint le haut d'une colonne : les 3 premiers blocs pleins exposes en partant du haut de la zone. */
    void paintColumn(int x, int z, int yBot, int yTop, int team) {
        int from = Math.min(yTop, world.getHighestBlockYAt(x, z));
        int done = 0;
        for (int y = from; y >= yBot && done < 3; y--) {
            Block b = world.getBlockAt(x, y, z);
            if (b.getType().isAir() || !b.getType().isSolid()) continue;
            if (exposed(b)) {
                paint(b, team);
                done++;
            } else {
                break;
            }
        }
    }

    // ================================================================ fin

    void end() {
        if (state != State.PLAYING) {
            return;
        }
        state = State.ENDING;
        int winner = forcedWinner >= 0 ? forcedWinner : score[0] > score[1] ? 0 : score[1] > score[0] ? 1 : -1;
        if (forcedWinner >= 0) {
            tell(TEAM_CODE[winner] + "&lVictoire de l'équipe " + TEAM_NAME[winner] + " par NUKE !");
        } else if (winner < 0) {
            tell("&e&lEGALITE ! &c" + score[0] + " &7- &9" + score[1]);
        } else {
            tell(TEAM_CODE[winner] + "&lVictoire de l'equipe " + TEAM_NAME[winner] + " ! &c" + score[0] + " &7- &9" + score[1]);
            Bukkit.broadcast(c(P + TEAM_CODE[winner] + "L'equipe " + TEAM_NAME[winner] + " &7gagne une partie de Paintball (&c" + score[0] + " &7- &9" + score[1] + "&7) !"));
        }
        // meilleur tireur
        PB best = null;
        for (PB pb : players.values()) {
            if (best == null || pb.kills > best.kills) {
                best = pb;
            }
        }
        if (best != null && best.kills > 0) {
            tell("&7Meilleur tireur : " + TEAM_CODE[best.team] + best.name + " &7(&f" + best.kills + " &7eliminations, &f" + best.deaths + " &7morts)");
        }
        int minPlay = Math.max(0, cfg("reward-min-seconds", 60));
        for (PB pb : players.values()) {
            // arrive en cours de match juste avant la fin : pas de recompense (sinon on rejoint a 5 s de la fin pour gagner)
            if (!pb.fromStart && pb.joinedLeft - timeLeft < minPlay) {
                continue;
            }
            eemi("eemi reward " + pb.name + " " + coreId() + " play");
            if (pb.team == winner) {
                eemi("eemi reward " + pb.name + " " + coreId() + " win");
            }
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            PB pb = players.get(id);
            boolean won = pb != null && pb.team == winner;
            String title = winner < 0 ? "&e&lEGALITE" : won ? "&6&lVICTOIRE !" : pb == null ? TEAM_CODE[winner] + "&l" + TEAM_NAME[winner].toUpperCase() + " GAGNE" : "&c&lDEFAITE";
            p.showTitle(Title.title(c(title), c("&cRouge " + score[0] + " &7- &9" + score[1] + " Bleu"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(800))));
            if (won) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            if (pb != null) {
                p.getInventory().clear();
            }
        }
        removeBallsAndGrenades();
        updateBoards();
        sendStatus();
        later(140L, () -> finish(true));
    }

    /**
     * Fin, peinture effacee. Fin normale ({@code natural}) : les joueurs de la partie encore la restent inscrits et
     * retournent dans la salle d'attente (file de la partie suivante, map tiree au lancement comme avant) ; les
     * simples spectateurs rentrent au hub. Arret admin : tout le monde au hub, comme avant.
     */
    void finish(boolean natural) {
        gen++;
        boardWarned.clear();
        List<PB> stay = new ArrayList<>();
        if (natural) {
            int max = cfg("max-players", 16);
            for (PB pb : players.values()) {
                Player p = Bukkit.getPlayer(pb.id);
                if (p != null && inWorld(p) && stay.size() < max) {
                    // anti-AFK retire (Esteban, 17/11) : tous les joueurs de la partie restent en file
                    stay.add(pb);
                }
            }
        }
        active.clear();
        List<UUID> everyone = all();
        // on vide d'abord : le changement de monde qui suit ne repasse pas par leave()
        players.clear();
        spectators.clear();
        for (UUID id : everyone) {
            boolean kept = false;
            for (PB pb : stay) {
                kept |= pb.id.equals(id);
            }
            if (kept) {
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                resetPlayer(p);
                if (inWorld(p)) {
                    p.teleport(hub());
                }
            }
        }
        // de nouveau inscrits (fiche neuve : les taches de l'ancienne partie ne la reconnaissent pas)
        for (PB old : stay) {
            Player p = Bukkit.getPlayer(old.id);
            if (p == null) {
                continue;
            }
            PB pb = new PB(old.id, p.getName());
            players.put(pb.id, pb);
            backToQueue(p);
        }
        bar = null;
        specBoard = null;
        // salle d'attente hors du terrain : la peinture est remise une fois les joueurs a l'abri
        restorePaint();
        removeBallsAndGrenades();
        state = State.WAITING;
        enforceHiddenNames();
        sendStatus();
    }

    /** Remet un joueur de la partie finie dans la file : salle d'attente, etat d'attente, objet « Retour au lobby ». */
    void backToQueue(Player p) {
        // teleporter AVANT de toucher a l'inventaire
        p.teleport(waitSpawn());
        resetPlayer(p);
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        org.bukkit.attribute.AttributeInstance hp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20.0 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFallDistance(0);
        p.getInventory().setItem(8, leaveItem());
        msg(p, "&7Tu restes dans la file du &ePaintball&7. Clique sur le lit (ou &f/pb leave&7) pour revenir au lobby.");
    }

    /** Objet de la file d'attente (case 8) : clic droit = quitter la file et revenir au hub. */
    ItemStack leaveItem() {
        ItemStack it = new ItemStack(Material.RED_BED);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c("&cRetour au lobby &7(clic droit)"));
        meta.lore(List.of(c("&7Quitte la file du paintball")));
        meta.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, "leave");
        it.setItemMeta(meta);
        return it;
    }

    /** Remet un joueur comme au lobby (kit retire, survie, sans effets, scoreboard principal). */
    void resetPlayer(Player p) {
        stripKit(p);
        if (bar != null) {
            p.hideBossBar(bar);
        }
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.setGlowing(false);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.setCooldown(MARKER_BASE, 0);
        p.setFireTicks(0);
        p.setGameMode(GameMode.SURVIVAL);
    }

    // ================================================================ tableau

    void updateBoards() {
        if (state != State.PLAYING && state != State.ENDING) {
            return;
        }
        for (PB pb : players.values()) {
            if (pb.board != null) {
                syncTeams(pb.board);
                fillBoard(pb.board, pb);
            }
        }
        if (specBoard != null) {
            syncTeams(specBoard);
            fillBoard(specBoard, null);
        }
    }

    void fillBoard(Scoreboard sb, PB pb) {
        Objective o = sb.getObjective("pb");
        if (o != null) {
            o.unregister();
        }
        o = sb.registerNewObjective("pb", Criteria.DUMMY, c("&e&lPAINTBALL"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        int line = 15;
        o.getScore(legacy("&7Temps : &f" + clock(timeLeft))).setScore(line--);
        o.getScore(legacy(" ")).setScore(line--);
        o.getScore(legacy("&cRouge : &f" + score[0])).setScore(line--);
        o.getScore(legacy("&9Bleu : &f" + score[1])).setScore(line--);
        o.getScore(legacy("&8Objectif : " + cfg("score-limit", 30))).setScore(line--);
        if (pb != null) {
            o.getScore(legacy("  ")).setScore(line--);
            o.getScore(legacy("&fEquipe : " + TEAM_CODE[pb.team] + TEAM_NAME[pb.team])).setScore(line--);
            o.getScore(legacy("&fEliminations : &a" + pb.kills)).setScore(line--);
            o.getScore(legacy("&fMorts : &c" + pb.deaths)).setScore(line--);
            o.getScore(legacy("&fSerie : &e" + pb.streak)).setScore(line--);
        }
        o.getScore(legacy("   ")).setScore(line--);
        o.getScore(legacy("&7eemicraft.mine.fun")).setScore(line);
    }

    // ================================================================ entree / sortie

    String coreId() {
        return getConfig().getString("eemicore-id", "paintball");
    }

    void eemi(String cmd) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    void sendStatus() {
        String st = switch (state) {
            case WAITING, COUNTDOWN -> "waiting";
            case PLAYING -> "playing";
            case ENDING -> "ending";
        };
        boolean inGame = state == State.PLAYING || state == State.ENDING;
        eemi("eemi status " + coreId() + " " + st + " " + (inGame ? 0 : players.size()) + " " + (inGame ? players.size() : 0) + " " + cfg("max-players", 16));
    }

    /** On n'entre que depuis le lobby (ou le monde du jeu). */
    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!inWorld(p) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemipaintball.admin")) {
            msg(p, "&cReviens au lobby (&f/lobby&c) pour rejoindre.");
            return false;
        }
        return true;
    }

    void join(Player p) {
        if (world == null) {
            msg(p, "&cLe jeu est indisponible.");
            return;
        }
        if (!canEnter(p)) {
            return;
        }
        UUID id = p.getUniqueId();
        if (players.containsKey(id)) {
            msg(p, "&7Tu es deja dans la partie. &f/pb leave &7pour sortir.");
            return;
        }
        int max = cfg("max-players", 16);
        if (players.size() >= max) {
            msg(p, "&cLa partie est pleine.");
            return;
        }
        if (state == State.ENDING) {
            msg(p, "&7La partie se termine : reessaie dans quelques secondes.");
            return;
        }
        boolean wasSpec = spectators.remove(id);
        PB pb = new PB(id, p.getName());
        int c0 = count(0);
        int c1 = count(1);
        players.put(id, pb);
        if (state == State.PLAYING) {
            // arrivee en cours de match : dans l'equipe la moins nombreuse (a egalite, celle qui perd)
            pb.team = c0 < c1 ? 0 : c1 < c0 ? 1 : (score[0] <= score[1] ? 0 : 1);
            twoTeams = twoTeams || (count(0) > 0 && count(1) > 0);
            pb.fromStart = false;
            pb.joinedLeft = timeLeft;
            pb.board = Bukkit.getScoreboardManager().getNewScoreboard();
            setupTeams(pb.board);
            updateBoards();
            pb.ammo = magazine();
            p.teleport(baseSpawn(pb.team));
            p.setScoreboard(pb.board);
            int g = gen;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (g == gen && p.isOnline() && inWorld(p) && players.get(id) == pb && state == State.PLAYING) {
                    p.getInventory().clear();
                    p.setScoreboard(pb.board);
                    if (bar != null) {
                        p.showBossBar(bar);
                    }
                    spawnPlayer(p, pb, false);
                }
            }, 2L);
            tell(TEAM_CODE[pb.team] + p.getName() + " &7rejoint l'equipe " + TEAM_CODE[pb.team] + TEAM_NAME[pb.team] + "&7.");
            sendStatus();
            return;
        }
        if (wasSpec) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        p.teleport(waitSpawn());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inWorld(p) && players.get(id) == pb && state != State.PLAYING) {
                p.getInventory().clear();
                p.setGameMode(GameMode.ADVENTURE);
                p.setFoodLevel(20);
                p.setSaturation(20f);
                p.setAllowFlight(false);
                p.setGlowing(false);
                p.getInventory().setItem(8, leaveItem());
            }
        }, 2L);
        tell("&f" + p.getName() + " &7rejoint le paintball (&f" + players.size() + "&7/&f" + max + "&7)");
        int min = cfg("min-players", 2);
        if (players.size() < min) {
            msg(p, "&7En attente de &f" + (min - players.size()) + " &7joueur(s)...");
        }
        sendStatus();
    }

    void spectate(Player p) {
        UUID id = p.getUniqueId();
        if (!spectators.contains(id)) {
            spectators.add(id);
        }
        p.teleport(standSpawn());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inWorld(p) && spectators.contains(id)) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                if (specBoard != null) {
                    p.setScoreboard(specBoard);
                }
                if (bar != null) {
                    p.showBossBar(bar);
                }
            }
        }, 2L);
        msg(p, "&7Tu regardes le paintball. &f/pb leave &7pour sortir.");
    }

    /** Sortie de la partie (commande, deconnexion, changement de monde). */
    void leave(Player p, boolean teleport) {
        UUID id = p.getUniqueId();
        boolean was = spectators.remove(id);
        PB pb = players.remove(id);
        if (pb != null && waitBoard != null) {
            Team wt = waitBoard.getTeam("pb_attente");
            if (wt != null) {
                wt.removeEntry(pb.name);
            }
        }
        if (pb != null && specBoard != null) {
            for (Team t : specBoard.getTeams()) {
                t.removeEntry(pb.name);
            }
        }
        if (pb != null) {
            was = true;
            pb.life++;
            tell(TEAM_CODE[pb.team] + pb.name + " &7quitte la partie.");
            if (state == State.PLAYING) {
                for (PB o : players.values()) {
                    if (o.board != null) {
                        Team t = o.board.getTeam("pb_" + TEAM_ID[pb.team]);
                        if (t != null) {
                            t.removeEntry(pb.name);
                        }
                    }
                }
                if (twoTeams && (count(0) == 0 || count(1) == 0)) {
                    tell("&7Une equipe est vide : fin du match.");
                    later(1L, this::end);
                }
                updateBoards();
            }
        }
        if (was) {
            resetPlayer(p);
            if (teleport && inWorld(p)) {
                p.teleport(hub());
            }
            sendStatus();
        }
    }

    // ================================================================ evenements

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (state == State.PLAYING && inWorld(p) && players.containsKey(p.getUniqueId())
                && (e.getFrom().getYaw() != e.getTo().getYaw() || e.getFrom().getPitch() != e.getTo().getPitch())) {
            // a tourne la tete de lui-meme pendant la partie : pas un compte AFK (garde en file a la fin)
            active.add(p.getUniqueId());
        }
        if (!inWorld(p) || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // la grande map est plus basse (sol vers y 57) : son propre seuil de chute
        double fallY = MapBuilder.FY - 5;
        for (BigMap bm : bigs) {
            if (bm.near(e.getTo().getX(), e.getTo().getZ())) {
                fallY = bm.fallY();
                break;
            }
        }
        if (e.getTo().getY() < fallY) {
            PB pb = players.get(p.getUniqueId());
            if (pb != null && state == State.PLAYING) {
                p.teleport(baseSpawn(pb.team));
            } else if (pb != null && state == State.ENDING && onBig) {
                p.teleport(standSpawn());
            } else if (!builder(p)) {
                p.teleport(waitSpawn());
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (world != null && e.getFrom().equals(world)) {
            leave(e.getPlayer(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (inWorld(p)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && inWorld(p) && !players.containsKey(p.getUniqueId())) {
                    resetPlayer(p);
                    p.teleport(hub());
                }
            }, 10L);
        }
    }

    private boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (world == null || !p.getWorld().equals(world) || builder(p)) {
            return;
        }
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_BLOCK || a == Action.PHYSICAL) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // objets du lobby et objets du kit : jamais leur usage vanilla
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        use(p, e.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (world == null || !p.getWorld().equals(world) || builder(p)) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) {
            use(p, p.getInventory().getItemInMainHand());
        }
    }

    void use(Player p, ItemStack it) {
        PB pb = players.get(p.getUniqueId());
        if (pb == null) {
            return;
        }
        // un clic droit sur un bloc arrive deux fois dans le meme tick (bloc puis air) : un seul tir
        int tick = Bukkit.getCurrentTick();
        if (pb.lastClickTick == tick) {
            return;
        }
        pb.lastClickTick = tick;
        String k = kind(it);
        if ("leave".equals(k)) {
            leave(p, true);
            msg(p, "&7Tu as quitté la file du paintball.");
            return;
        }
        if ("marker".equals(k)) {
            tryFire(p, pb);
        } else if ("grenade".equals(k)) {
            throwGrenade(p, pb);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (world == null || !p.getWorld().equals(world) || builder(p)) {
            return;
        }
        e.setCancelled(true);
        PB pb = players.get(p.getUniqueId());
        if (pb != null && state == State.PLAYING && !pb.dead && !pb.reloading && pb.ammo < magazine()) {
            startReload(p, pb);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && inWorld(p) && !builder(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!ours(pr)) {
            return;
        }
        PersistentDataContainer pdc = pr.getPersistentDataContainer();
        Integer team = pdc.get(kTeam, PersistentDataType.INTEGER);
        String sh = pdc.get(kShooter, PersistentDataType.STRING);
        if (team == null) {
            return;
        }
        PB shooter = null;
        try {
            shooter = sh == null ? null : players.get(UUID.fromString(sh));
        } catch (IllegalArgumentException ignored) {
            // shooter invalide
        }
        Particle.DustOptions dust = new Particle.DustOptions(TEAM_COLOR[team], 1.3f);
        if (e.getHitEntity() != null) {
            if (!(e.getHitEntity() instanceof Player v)) {
                e.setCancelled(true);
                return;
            }
            PB pv = players.get(v.getUniqueId());
            if (pv == null || pv.dead || v.getGameMode() == GameMode.SPECTATOR || pv.team == team) {
                // pas de tir ami : la bille traverse
                e.setCancelled(true);
                return;
            }
            world.spawnParticle(Particle.DUST, pr.getLocation(), 25, 0.25, 0.25, 0.25, 0, dust);
            world.playSound(pr.getLocation(), Sound.ENTITY_SLIME_SQUISH_SMALL, 1f, 1.4f);
            if (state != State.PLAYING) {
                return;
            }
            if (System.currentTimeMillis() < pv.protectedUntil) {
                Player sp = shooter == null ? null : Bukkit.getPlayer(shooter.id);
                if (sp != null) {
                    sp.sendActionBar(c("&b" + pv.name + " est protege"));
                }
                // invincible : la bille ne le touche pas du tout (pas de recul)
                e.setCancelled(true);
                return;
            }
            eliminate(v, pv, shooter, team, "bille");
            return;
        }
        Block b = e.getHitBlock();
        if (b != null) {
            Location at = pr.getLocation();
            world.spawnParticle(Particle.DUST, at, 12, 0.15, 0.15, 0.15, 0, dust);
            world.playSound(at, Sound.ENTITY_SLIME_SQUISH_SMALL, 0.6f, 1.6f);
            if (state == State.PLAYING) {
                splat(b, e.getHitBlockFace(), team);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world) && e.isGliding()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (world != null && e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    // ================================================================ commandes

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "join" : a[0].toLowerCase();
        boolean admin = s.hasPermission("eemipaintball.admin");
        switch (sub) {
            case "join", "rejoindre" -> {
                if (s instanceof Player p) {
                    join(p);
                }
            }
            case "leave", "quitter" -> {
                if (s instanceof Player p) {
                    if (players.containsKey(p.getUniqueId()) || spectators.contains(p.getUniqueId())) {
                        leave(p, true);
                    } else {
                        msg(p, "&7Tu n'es pas dans la partie.");
                    }
                }
            }
            case "spectate", "regarder" -> {
                if (s instanceof Player p) {
                    if (players.containsKey(p.getUniqueId())) {
                        msg(p, "&7Tu es deja inscrit.");
                    } else if (world != null && canEnter(p)) {
                        spectate(p);
                    }
                }
            }
            case "start" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else if (state == State.PLAYING || state == State.ENDING) {
                    msg(s, "&cUne partie est deja en cours.");
                } else if (players.isEmpty()) {
                    msg(s, "&cPersonne n'est inscrit.");
                } else {
                    start();
                }
            }
            case "stop" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else {
                    if (state == State.PLAYING || state == State.ENDING) {
                        tell("&cPartie arretee par un admin.");
                    }
                    // aucune recompense sur un arret admin, et tout le monde au hub (comportement d'avant)
                    state = State.ENDING;
                    finish(false);
                    msg(s, "&7Partie arretee, attente remise a zero.");
                }
            }
            case "build" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else if (state == State.PLAYING || state == State.ENDING) {
                    msg(s, "&cImpossible pendant une partie.");
                } else if (world != null) {
                    removeEntities();
                    build();
                    spawnRules();
                    msg(s, "&aTerrain reconstruit.");
                }
            }
            case "reload" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else {
                    reloadConfig();
                    if (rules != null) {
                        rules.remove();
                        rules = null;
                    }
                    spawnRules();
                    msg(s, "&aConfig rechargee.");
                }
            }
            case "map", "maps" -> {
                // admin : se teleporter sur une map pour la visiter (sans argument = une map au hasard)
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else if (!(s instanceof Player p)) {
                    msg(s, "&7Petites maps : " + String.join(", ", SmallMaps.ids()) + " (posee : " + smallMap + ") ; grandes maps : "
                        + String.join(", ", bigs.stream().map(b -> b.id + (b.ready ? "" : " (pas prete)")).toList()));
                } else if (world == null) {
                    msg(p, "&cLe monde du paintball n'est pas charge.");
                } else if (players.containsKey(p.getUniqueId()) || spectators.contains(p.getUniqueId())) {
                    msg(p, "&cQuitte d'abord la partie (&f/pb leave&c).");
                } else if (state == State.PLAYING || state == State.ENDING) {
                    msg(p, "&cUne partie est en cours : &f/pb spectate &cpour la regarder.");
                } else if (!inWorld(p) && !getConfig().getStringList("join-from").isEmpty()
                    && !getConfig().getStringList("join-from").contains(p.getWorld().getName())) {
                    msg(p, "&cReviens au lobby (&f/lobby&c) avant de visiter les maps.");
                } else {
                    List<String> ids = new ArrayList<>(SmallMaps.ids());
                    for (BigMap b : bigs) {
                        if (b.ready) {
                            ids.add(b.id);
                        }
                    }
                    String id = a.length > 1 ? a[1].toLowerCase() : ids.get(rnd.nextInt(ids.size()));
                    Location to = null;
                    String name = null;
                    SmallMaps.Info small = SmallMaps.get(id);
                    if (small != null) {
                        // petite map : posee a la place de l'actuelle si besoin (aucune partie en cours ici)
                        if (!small.id().equals(smallMap)) {
                            restorePaint();
                            buildSmall(small.id(), false);
                        }
                        double x = rnd.nextBoolean() ? -28.5 : 29.5;
                        to = new Location(world, x, MapBuilder.FY + 1, 0.5, x < 0 ? -90f : 90f, 0f);
                        name = small.name();
                    } else {
                        for (BigMap b : bigs) {
                            if (b.id.equals(id) && b.ready) {
                                to = b.spawn(world, rnd.nextInt(2), rnd);
                                name = b.name;
                            }
                        }
                    }
                    if (to == null) {
                        msg(p, "&cMap inconnue ou pas prete. &7Maps : &f" + String.join(", ", ids));
                    } else {
                        p.leaveVehicle();
                        p.setFallDistance(0);
                        if (!p.teleport(to)) {
                            msg(p, "&cTeleportation impossible.");
                            return true;
                        }
                        msg(p, "&7Tu es sur &f" + name + "&7. Maps : &f" + String.join(", ", ids) + " &7(/pb map <nom>, sans nom = au hasard).");
                        msg(p, "&7En partie, la map reste tiree au hasard : grandes maps des " + Math.max(2, cfg("big-map.min-players", 8))
                            + " joueurs, sinon une petite map (&f" + String.join(", ", smallIds()) + "&7), jamais deux fois de suite.");
                    }
                }
            }
            case "info" -> msg(s, "&7Etat &f" + state + "&7, inscrits &f" + players.size() + " &7(&cR " + count(0) + "&7/&9B " + count(1)
                + "&7), score &c" + score[0] + "&7-&9" + score[1] + "&7, temps &f" + clock(timeLeft) + "&7, blocs peints &f" + painted.size()
                + "&7, spectateurs &f" + spectators.size());
            default -> {
                msg(s, "&f/pb join &7- rejoindre | &f/pb leave &7- quitter | &f/pb spectate &7- regarder");
                if (admin) {
                    msg(s, "&c/pb start | stop | build | reload | info | map [nom]");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>(List.of("join", "leave", "spectate"));
        if (s.hasPermission("eemipaintball.admin")) {
            out.addAll(List.of("start", "stop", "build", "reload", "info", "map"));
            if (a.length == 2 && a[0].equalsIgnoreCase("map")) {
                List<String> m = new ArrayList<>(SmallMaps.ids());
                for (BigMap b : bigs) {
                    m.add(b.id);
                }
                m.removeIf(x -> !x.startsWith(a[1].toLowerCase()));
                return m;
            }
        }
        if (a.length == 1) {
            out.removeIf(x -> !x.startsWith(a[0].toLowerCase()));
            return out;
        }
        return List.of();
    }

}
