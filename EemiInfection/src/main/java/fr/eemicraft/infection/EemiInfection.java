package fr.eemicraft.infection;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.generator.ChunkGenerator;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Infection : survivants contre zombies dans une ville abandonnee de nuit.
 * Les zombies de depart attendent dans la crypte pendant la fuite, puis sont laches.
 * 1.4 : deux coups de zombie infectent (le premier blesse) ; les survivants ont des armes a feu (kits) et des grenades ;
 * un zombie a des PV, a 0 il retourne a la crypte. Survivre au temps = victoire des survivants.
 */
public final class EemiInfection extends JavaPlugin implements Listener {
    static final String P = "&2&l[Infection] &r";
    /** 4 (1.6.0) : volees d'escalier interieures refaites (plus de marche contre le mur), couvercles de bennes fermes. */
    static final int BUILD_VERSION = 5; // 1.8.0 : vitres dans le mur (Facade), tete libre devant les escaliers
    static final String TAG = "eemiinfection";
    static final int G = City.G;

    enum State { WAITING, COUNTDOWN, FLEE, PLAYING, ENDING }

    static final class Gamer {
        final UUID id;
        String name;
        boolean zombie;
        boolean alpha;
        boolean inCrypt;
        boolean frozen;
        int infections;
        int hits;
        long protectedUntil;
        /** Pouvoir de ce zombie (plume = bond, laisse = langue, boule de slime = crachat acide, baton de blaze = charge), null = classique. */
        Material power;
        /** Zombie qui choisit son pouvoir dans le menu (place reservee) ; choix au hasard a choiceDeadline (ms). */
        boolean choosingPower;
        long choiceDeadline;
        /** Charge en cours (ticks de l'arsenal) et sa direction. */
        long chargeUntil;
        Vector chargeDir;
        /** Survivant etourdi par une charge jusqu'a (ticks de l'arsenal) : pas de tir. */
        long stunUntil;
        /** Dernier son de blessure joue sur ce zombie (ticks de l'arsenal), pour ne pas saturer en rafale. */
        long lastHurtSound;
        long lastKnock;
        /** Zombie : points de vie. */
        double hp;
        /** Survivant : coups de zombie encaisses (soignes apres un moment sans etre touche). */
        int wounds;
        long lastWound;
        /** Armes (survivant). */
        String kit;
        final Map<String, Integer> mag = new HashMap<>();
        final Map<String, Integer> reserve = new HashMap<>();
        String reloading;
        long reloadDone;
        final Map<String, Long> next = new HashMap<>();
        long holdUntil;
        int grenades;
        int grenadeTimer;
        int ammoTimer;
        String hitInfo;
        long hitInfoUntil;
        /** 1.6.1 : coeurs du zombie mis a jour au plus une fois par zombie-hearts-interval ticks (ticks de l'arsenal). */
        long heartsNext;
        boolean heartsDirty;

        Gamer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    World world;
    State state = State.WAITING;
    final Map<UUID, Gamer> players = new LinkedHashMap<>();
    final List<UUID> spectators = new ArrayList<>();
    /** Joueurs partis en pleine partie : gardes pour le resume, plus pilotes. */
    final Set<UUID> gone = new HashSet<>();
    /** 1.7.0 : spectateurs venus par /infection join pendant une partie (« tu joueras a la suivante ») : mis dans la file a la fin. */
    final Set<UUID> wantsNext = new HashSet<>();
    final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** Numero de partie : une tache planifiee d'une autre partie ne fait rien. */
    int gen;
    int countdown;
    int fleeLeft;
    int timeLeft;
    boolean lastMinute;
    boolean lastAnnounced;
    boolean testGame;
    BossBar bar;
    BukkitTask ticker;
    /** Cases de dispersion (x, y, z), calculees apres la construction. */
    final List<int[]> spots = new ArrayList<>();
    final Random rnd = new Random();
    boolean spawning;
    NamespacedKey kitKey;
    Arsenal arsenal;
    BukkitTask fastTask;
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

    void titleAll(String t, String sub, int stay) {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                title(p, t, sub, stay);
            }
        }
    }

    static void title(Player p, String t, String sub, int staySec) {
        p.showTitle(Title.title(c(t), c(sub), Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(staySec), Duration.ofMillis(400))));
    }

    void soundAll(Sound s, float vol, float pitch) {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.playSound(p.getLocation(), s, vol, pitch);
            }
        }
    }

    /** Joueurs encore dans la partie + spectateurs. */
    List<UUID> all() {
        List<UUID> l = new ArrayList<>(players.keySet());
        l.removeAll(gone);
        l.addAll(spectators);
        return l;
    }

    /** Joueur actif de la partie (inscrit, pas parti, connecte). */
    Gamer active(Player p) {
        if (p == null || gone.contains(p.getUniqueId())) {
            return null;
        }
        return players.get(p.getUniqueId());
    }

    int survivors() {
        int n = 0;
        for (Gamer g : players.values()) {
            if (!gone.contains(g.id) && !g.zombie) {
                n++;
            }
        }
        return n;
    }

    int zombies() {
        int n = 0;
        for (Gamer g : players.values()) {
            if (!gone.contains(g.id) && g.zombie) {
                n++;
            }
        }
        return n;
    }

    void later(long ticks, Runnable r) {
        int g = gen;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (g == gen) {
                r.run();
            }
        }, ticks);
    }

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kitKey = new NamespacedKey(this, "kit");
        arsenal = new Arsenal(this);
        File f = new File(getDataFolder(), "data.yml");
        buildVersion = YamlConfiguration.loadConfiguration(f).getInt("build-version", 0);
        createWorld();
        getServer().getPluginManager().registerEvents(this, this);
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        fastTask = Bukkit.getScheduler().runTaskTimer(this, this::fastTick, 1L, 1L);
        sendStatus();
    }

    /** Chaque tick : armes, grenades, flaques d'acide, charges, coeurs des zombies en attente. */
    void fastTick() {
        if (world == null) {
            return;
        }
        arsenal.tick();
        if (state == State.PLAYING) {
            chargeTick();
        }
        heartsTick();
    }

    @Override
    public void onDisable() {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && inWorld(p)) {
                toHub(p);
            } else if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        players.clear();
        spectators.clear();
        if (bar != null) {
            bar.removeAll();
        }
        if (arsenal != null) {
            arsenal.clearGame();
        }
        removeEntities();
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
        String name = getConfig().getString("world", "infection");
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
        world.setStorm(false);
        world.setThundering(false);
        world.setPVP(true);
        applyAmbience();
        world.setSpawnLocation(loc(City.WAIT));
        for (int cx = -City.EXT >> 4; cx <= City.EXT >> 4; cx++) {
            for (int cz = -City.EXT >> 4; cz <= (City.HZ2 + 16) >> 4; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
            }
        }
        removeEntities();
        if (buildVersion != BUILD_VERSION) {
            build();
        } else {
            scanSpots();
        }
        spawnRules();
    }

    void applyAmbience() {
        if (world == null) {
            return;
        }
        // minuit fixe
        world.setTime(18000);
        int vd = getConfig().getInt("fog-view-distance", 4);
        if (vd > 0) {
            try {
                world.setViewDistance(Math.max(2, Math.min(32, vd)));
                world.setSendViewDistance(Math.max(2, Math.min(32, vd)));
            } catch (RuntimeException e) {
                getLogger().warning("Distance de vue non reglee : " + e.getMessage());
            }
        }
    }

    void build() {
        long t0 = System.currentTimeMillis();
        fr.eemicraft.infection.build.bukkit.BukkitSink sink = new fr.eemicraft.infection.build.bukkit.BukkitSink(world, getLogger());
        City city = new City(sink, getLogger());
        city.build();
        buildVersion = BUILD_VERSION;
        YamlConfiguration y = new YamlConfiguration();
        y.set("build-version", buildVersion);
        try {
            getDataFolder().mkdirs();
            y.save(new File(getDataFolder(), "data.yml"));
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
        scanSpots();
        getLogger().info("Map construite : " + city.ops + " blocs en " + (System.currentTimeMillis() - t0) + " ms, " + spots.size() + " cases de dispersion"
                + (sink.invalidStates().isEmpty() ? "." : ", etats refuses : " + sink.invalidStates()));
    }

    /** Cases ou l'on peut poser un survivant : sol plein, 2 cases d'air, dans la ville. */
    void scanSpots() {
        spots.clear();
        int lim = City.WALL - 2;
        // accessibilite a pied depuis les rues : on ne garde que des cases d'ou l'on peut sortir
        // (pieces fermees, combles, creux de facade ou conteneurs = joueurs bloques au spawn, vu le 16/11/2026)
        Reach reach = new Reach((x, y, z) -> kindOf(world.getBlockAt(x, y, z).getType()), lim, G, G + 26);
        List<int[]> seeds = new ArrayList<>();
        for (int a = -lim; a <= lim; a++) {
            for (int b = -3; b <= 3; b++) {
                seeds.add(new int[]{a, G + 1, b});
                seeds.add(new int[]{b, G + 1, a});
            }
        }
        seeds.add(new int[]{(int) Math.floor(City.EXIT[0]), (int) Math.floor(City.EXIT[1]), (int) Math.floor(City.EXIT[2])});
        int[][] seedArr = seeds.toArray(new int[0][]);
        int reached = reach.run(seedArr);
        // 1.9.0 (carte #136) : parcours inverse = on doit pouvoir RESSORTIR de la case (pas seulement y tomber)
        reach.runBack(seedArr);
        int refused = 0;
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                for (int y = G + 1; y <= G + 22; y++) {
                    Block feet = world.getBlockAt(x, y, z);
                    Material ft = feet.getType();
                    if (!(ft.isAir() || Tag.WOOL_CARPETS.isTagged(ft) || ft == Material.SHORT_GRASS || ft == Material.DEAD_BUSH)) {
                        continue;
                    }
                    if (!world.getBlockAt(x, y + 1, z).getType().isAir()) {
                        continue;
                    }
                    Block below = world.getBlockAt(x, y - 1, z);
                    Material bt = below.getType();
                    if (!bt.isSolid() || bt == Material.BARRIER || Tag.LEAVES.isTagged(bt) || Tag.FENCES.isTagged(bt) || Tag.WALLS.isTagged(bt)
                            || Tag.FENCE_GATES.isTagged(bt) || bt == Material.IRON_BARS || bt.name().endsWith("PANE") || bt.name().contains("CAMPFIRE")) {
                        continue;
                    }
                    BoundingBox bb = below.getBoundingBox();
                    if (bb.getWidthX() < 0.9 || bb.getWidthZ() < 0.9 || bb.getHeight() < 0.5) {
                        continue;
                    }
                    // reliee a la rue dans les deux sens, 3 cases libres en hauteur, 4 voisines praticables (Reach.roomy)
                    if (!reach.roomy(x, y, z)) {
                        refused++;
                        continue;
                    }
                    spots.add(new int[]{x, y, z});
                }
            }
        }
        getLogger().info("Accessibilite : " + reached + " positions atteintes a pied, " + spots.size() + " cases de dispersion gardees, "
                + refused + " refusees (enfermees, sans sortie a pied ou trop a l'etroit).");
        if (spots.isEmpty()) {
            getLogger().warning("Aucune case de dispersion trouvee : /infection build");
        }
    }

    static boolean openable(Material t) {
        return Tag.WOODEN_DOORS.isTagged(t) || Tag.FENCE_GATES.isTagged(t) || Tag.WOODEN_TRAPDOORS.isTagged(t);
    }

    /** Pour Reach : peut-on y avoir le corps, peut-on y grimper. */
    static int kindOf(Material t) {
        if (t == Material.LADDER || t == Material.VINE || t == Material.SCAFFOLDING || t == Material.TWISTING_VINES
                || t == Material.TWISTING_VINES_PLANT || t == Material.WEEPING_VINES || t == Material.WEEPING_VINES_PLANT
                || t == Material.CAVE_VINES || t == Material.CAVE_VINES_PLANT) {
            return Reach.PASS | Reach.CLIMB;
        }
        // portes, portillons et trappes : seulement ceux qu'on peut ouvrir (openable, voir onInteract)
        if (t.isAir() || !t.isSolid() || Tag.WOOL_CARPETS.isTagged(t) || t == Material.MOSS_CARPET || t == Material.SNOW
                || openable(t)) {
            return Reach.PASS;
        }
        // clotures et murets : collision de 1,5, on ne tient pas debout dessus (1.9.0)
        if (Tag.FENCES.isTagged(t) || Tag.WALLS.isTagged(t)) {
            return Reach.TALL;
        }
        return 0;
    }

    Location loc(double[] p) {
        return new Location(world, p[0], p[1], p[2], (float) p[3], p.length > 4 ? (float) p[4] : 0f);
    }

    Location waitSpawn() {
        return loc(City.WAIT);
    }

    Location cryptSpawn() {
        return loc(City.CRYPT);
    }

    Location exitSpawn() {
        Location l = loc(City.EXIT);
        l.setYaw(0f);
        return l;
    }

    /**
     * Apparition d'un zombie (sortie de crypte, retour apres une fleche, chute) : une case au hasard de la ville,
     * au sol, loin de tous les survivants, pour qu'on ne puisse plus camper la sortie du cimetiere.
     */
    Location zombieSpawn() {
        if (!getConfig().getBoolean("zombie-random-spawn", true) || spots.isEmpty()) {
            return exitSpawn();
        }
        double minD = getConfig().getDouble("zombie-spawn-distance", 20);
        List<Location> surv = new ArrayList<>();
        for (Gamer g : players.values()) {
            if (gone.contains(g.id) || g.zombie) {
                continue;
            }
            Player p = Bukkit.getPlayer(g.id);
            if (p != null && inWorld(p)) {
                surv.add(p.getLocation());
            }
        }
        for (int pass = 0; pass < 3; pass++) {
            double need = pass == 0 ? minD : pass == 1 ? minD / 2 : 6;
            for (int tries = 0; tries < 300; tries++) {
                int[] s = spots.get(rnd.nextInt(spots.size()));
                if (pass < 2 && s[1] > G + 11) {
                    continue;
                }
                Location l = new Location(world, s[0] + 0.5, s[1], s[2] + 0.5, rnd.nextFloat() * 360f - 180f, 0f);
                boolean ok = true;
                for (Location o : surv) {
                    if (o.distanceSquared(l) < need * need) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return l;
                }
            }
        }
        return exitSpawn();
    }

    Location spectSpawn() {
        Location l = loc(City.SPECT);
        l.setPitch(70f);
        return l;
    }

    boolean inWorld(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    // ================================================================ entites (panneau des regles)

    void removeEntities() {
        if (world == null) {
            return;
        }
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains(TAG)) {
                e.remove();
            }
        }
    }

    void spawnRules() {
        if (world == null) {
            return;
        }
        String txt = "&2&lINFECTION\n&7Survivants contre zombies\n\n"
                + "&fChoisis ton &earme &f(étoile) : &6Assaut&f, &cPompe&f,\n&bSniper &fou &eMitraillette&f, + pistolet et grenades.\n"
                + "&fLes &2zombies &fsortent de la crypte après &e" + getConfig().getInt("flee-time", 30) + " s&f.\n"
                + "&f1er coup de zombie = &eblessé&f, 2e = &cinfecté&f.\n"
                + "&fUn zombie a &c" + num(zombieMaxHp()) + " PV &f: à 0, retour à la crypte.\n"
                + "&fClic droit = tirer, &eF &f= recharger. L'épée repousse.\n"
                + "&fLes zombies choisissent un pouvoir (1 de chaque) :\n&aBond&f, &dLangue&f, &2Crachat acide&f ou &6Charge&f.\n"
                + "&dLangue &f: tire dessus pour la couper !\n\n"
                + "&aTenez &e" + fmt(getConfig().getInt("game-time", 480)) + " &aensemble pour gagner !";
        spawning = true;
        try {
            world.spawn(new Location(world, 0.5, G + 4.3, City.HZ1 + 2.05, 0f, 0f), TextDisplay.class, t -> {
                t.text(c(txt));
                t.setBillboard(Display.Billboard.VERTICAL);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                t.setShadowed(true);
                t.setPersistent(false);
                t.addScoreboardTag(TAG);
            });
        } finally {
            spawning = false;
        }
    }

    /** WorldGuard bloque les apparitions par plugin : on retablit les notres. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent e) {
        if (spawning && world != null && e.getEntity().getWorld().equals(world)) {
            e.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLightning(LightningStrikeEvent e) {
        if (spawning && world != null && e.getWorld().equals(world)) {
            e.setCancelled(false);
        }
    }

    // ================================================================ objets

    ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(c(s));
            }
            meta.lore(l);
        }
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, "kit");
        it.setItemMeta(meta);
        return it;
    }

    // ================================================================ textures du pack (1.5.0)

    /** Textures du pack EEMICRAFT (armes 3D, tenues, pouvoirs). Relu a chaque usage : /infection reload suffit. */
    boolean textures() {
        return getConfig().getBoolean("textures", false);
    }

    /** Pose le modele eemicraft:infection/<model> si les textures sont activees (sinon objet vanilla inchange). */
    ItemStack skin(ItemStack it, String model) {
        if (it != null && textures()) {
            it.setData(DataComponentTypes.ITEM_MODEL, Key.key("eemicraft", "infection/" + model));
        }
        return it;
    }

    /**
     * Piece de tenue : modele d'icone + equipement eemicraft:<asset> (texture portee sur le joueur).
     * noArmor : aucun point d'armure (objet qui n'en donnait pas avant, pour ne rien changer au jeu).
     */
    ItemStack outfit(ItemStack it, EquipmentSlot slot, String asset, String model, boolean noArmor) {
        it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(Key.key("eemicraft", asset)).build());
        if (noArmor) {
            it.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        }
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("eemicraft", "infection/" + model));
        return it;
    }

    boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.STRING);
    }

    boolean isLeaveItem(ItemStack it) {
        return it != null && it.hasItemMeta() && "leave".equals(it.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING));
    }

    ItemStack leaveItem() {
        ItemStack it = item(Material.RED_BED, "&cQuitter &7(clic droit)");
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, "leave");
        it.setItemMeta(m);
        return it;
    }

    static String num(double d) {
        return Arsenal.num(d);
    }

    double zombieMaxHp() {
        return Math.max(1, getConfig().getDouble("zombie-health", 20));
    }

    int survivorHits() {
        return Math.max(1, getConfig().getInt("survivor-hits", 2));
    }

    static double maxHealth(Player p) {
        org.bukkit.attribute.AttributeInstance a = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return a == null ? 20 : Math.max(1, a.getValue());
    }

    /** Coeurs affiches : PV du zombie, ou blessure du survivant (la vraie vie ne sert pas dans ce jeu). */
    void showHearts(Player p, Gamer g) {
        try {
            double max = maxHealth(p);
            double f;
            if (g == null) {
                f = 1;
            } else if (g.zombie) {
                // 1.6.1 : les coeurs suivent les PV du plugin (proportion). Le client 26.3 joue l'inclinaison de degat
                // a chaque baisse de vie recue (LocalPlayer.hurtTo) : les baisses passent par queueHearts (une par
                // zombie-hearts-interval ticks). Jamais de vraie mort : minimum un demi-coeur, le plugin gere la crypte.
                f = getConfig().getBoolean("zombie-hearts", true) ? Math.max(0, g.hp) / zombieMaxHp() : 1;
            } else {
                f = 1.0 - g.wounds / (double) survivorHits();
            }
            p.setHealth(Math.max(1, Math.min(max, Math.ceil(max * f))));
        } catch (RuntimeException ignored) {
            // joueur mort ou en train de partir
        }
    }

    /** Vie remise au maximum (retour au hub, salle d'attente). */
    static void fullHealth(Player p) {
        try {
            if (!p.isDead()) {
                p.setHealth(maxHealth(p));
            }
        } catch (RuntimeException ignored) {
            // rien
        }
    }

    /** Retire les objets du jeu (marques) d'un inventaire, sans toucher au reste. */
    void removeKit(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isKit(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (isKit(armor[i])) {
                armor[i] = null;
            }
        }
        inv.setArmorContents(armor);
        if (isKit(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
        }
    }

    void clearEffects(Player p) {
        for (PotionEffect pe : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(pe.getType());
        }
        // recharges des pouvoirs : la laisse sert aussi d'objet « Groupe » au lobby
        for (Material m : new Material[]{LEAP, TONGUE, ACID, CHARGE, Material.FIRE_CHARGE, Material.IRON_HORSE_ARMOR, Material.GOLDEN_HORSE_ARMOR,
                Material.DIAMOND_HORSE_ARMOR, Material.LEATHER_HORSE_ARMOR, Material.COPPER_HORSE_ARMOR}) {
            if (p.hasCooldown(m)) {
                p.setCooldown(m, 0);
            }
        }
    }

    void giveSurvivorKit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        clearEffects(p);
        ItemStack sword = item(Material.WOODEN_SWORD, "&aÉpée en bois", "&7Repousse les zombies");
        ItemMeta sm = sword.getItemMeta();
        sm.addEnchant(Enchantment.KNOCKBACK, 1, true);
        sword.setItemMeta(sm);
        inv.setItem(0, skin(sword, "machette"));
        if (textures()) {
            // gilet tactique : seulement visuel (aucun point d'armure, le jeu compte les coups lui-meme)
            inv.setChestplate(outfit(item(Material.LEATHER_CHESTPLATE, "&2Gilet tactique", "&7Tenue des survivants"),
                    EquipmentSlot.CHEST, "infection_survivant", "gilet", true));
        }
        Gamer g = players.get(p.getUniqueId());
        if (g != null) {
            arsenal.giveWeapons(p, g);
        }
        if (state == State.FLEE) {
            inv.setItem(7, arsenal.kitMenuItem());
        }
        inv.setHeldItemSlot(1);
        showHearts(p, g);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
    }

    ItemStack leather(Material m) {
        ItemStack it = item(m, "&2Peau de zombie");
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(Color.fromRGB(0x3E7A2A));
        it.setItemMeta(lm);
        return it;
    }

    void giveZombieKit(Player p, boolean alpha) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        if (textures()) {
            // tenue complete du pack ; la tete passe en casque de cuir sans armure (la tete de zombie n'en donnait pas)
            inv.setHelmet(outfit(item(Material.LEATHER_HELMET, "&2Tête de zombie"), EquipmentSlot.HEAD, "infection_zombie", "zombie_tete", true));
            inv.setChestplate(outfit(leather(Material.LEATHER_CHESTPLATE), EquipmentSlot.CHEST, "infection_zombie", "zombie_haut", false));
            inv.setLeggings(outfit(leather(Material.LEATHER_LEGGINGS), EquipmentSlot.LEGS, "infection_zombie", "zombie_bas", false));
            inv.setBoots(outfit(leather(Material.LEATHER_BOOTS), EquipmentSlot.FEET, "infection_zombie", "zombie_pieds", false));
        } else {
            inv.setHelmet(item(Material.ZOMBIE_HEAD, "&2Tête de zombie"));
            inv.setChestplate(leather(Material.LEATHER_CHESTPLATE));
            inv.setLeggings(leather(Material.LEATHER_LEGGINGS));
            inv.setBoots(leather(Material.LEATHER_BOOTS));
        }
        if (alpha) {
            inv.setItem(0, skin(item(Material.WOODEN_SWORD, "&2&lZombie alpha", "&7Cosmétique : frappe pour infecter"), "griffes"));
        }
        Gamer gz = players.get(p.getUniqueId());
        Material power = gz == null || !abilities() ? null : gz.power;
        if (gz != null) {
            gz.hp = zombieMaxHp();
            gz.wounds = 0;
            gz.reloading = null;
            gz.holdUntil = 0;
            gz.heartsDirty = false;
            gz.chargeUntil = 0;
        }
        if (power != null) {
            inv.setItem(1, powerItem(power));
            msg(p, "&7Ton pouvoir de zombie : " + powerName(power) + " &7(objet dans ta barre)");
        } else if (gz != null && gz.choosingPower && abilities()) {
            inv.setItem(1, powerMenuItem());
            UUID pid = p.getUniqueId();
            later(3L, () -> {
                Player q = Bukkit.getPlayer(pid);
                Gamer gq = players.get(pid);
                if (q != null && gq != null && gq.choosingPower && !gone.contains(pid) && inWorld(q)) {
                    openPowerMenu(q);
                }
            });
            msg(p, "&dChoisis ton pouvoir de zombie &7(" + chooseSeconds() + " s, sinon au hasard). &8L'étoile (case 2) rouvre le menu.");
        } else if (abilities()) {
            msg(p, "&7Tu es un zombie classique : les pouvoirs sont déjà pris.");
        }
        inv.setHeldItemSlot(0);
        clearEffects(p);
        zombieEffects(p);
        // 1.6.0 : tous les zombies brillent (contour rouge, couleur de l'equipe des zombies), alpha ou infecte
        p.setGlowing(zombieGlow());
        showHearts(p, gz);
        p.setFoodLevel(20);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
    }

    // ================================================================ pouvoirs des zombies (facon Left 4 Dead)

    static final Material LEAP = Material.FEATHER;
    static final Material TONGUE = Material.LEAD;
    static final Material ACID = Material.SLIME_BALL;
    /** Charge (1.6.0, remplace l'Escalade) : le zombie fonce tout droit et etourdit le premier survivant touche. */
    static final Material CHARGE = Material.BLAZE_ROD;
    /** Les pouvoirs, un seul exemplaire de chacun par partie. */
    static final List<Material> POWERS = List.of(LEAP, TONGUE, ACID, CHARGE);
    static final String POWER_MENU_TAG = "powermenu";

    /** Menu de choix du pouvoir d'un zombie. */
    static final class PowerMenu implements org.bukkit.inventory.InventoryHolder {
        org.bukkit.inventory.Inventory inv;

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return inv;
        }
    }

    boolean abilities() {
        return getConfig().getBoolean("abilities.enabled", true);
    }

    boolean zombieGlow() {
        return getConfig().getBoolean("zombie-glow", true);
    }

    int chooseSeconds() {
        return Math.max(3, getConfig().getInt("abilities.choose-seconds", 10));
    }

    /** Nombre de zombies a pouvoir par partie (au plus un par pouvoir). */
    int maxPowered() {
        return Math.max(0, Math.min(POWERS.size(), getConfig().getInt("abilities.max-powered", POWERS.size())));
    }

    static String powerName(Material m) {
        if (m == LEAP) {
            return "&a&lBond";
        }
        if (m == TONGUE) {
            return "&d&lLangue";
        }
        if (m == ACID) {
            return "&2&lCrachat acide";
        }
        if (m == CHARGE) {
            return "&6&lCharge";
        }
        return "&7Zombie classique";
    }

    String[] powerLore(Material m) {
        if (m == LEAP) {
            return new String[]{"&7Saute loin devant toi", "&8Recharge : " + getConfig().getInt("abilities.leap-cooldown", 8) + " s"};
        }
        if (m == TONGUE) {
            return new String[]{"&7Vise un survivant (portée " + num(tongueRange()) + ") : ta langue",
                    "&7le tire d'un coup vers toi et le ralentit.", "&7Les murs bloquent la langue.",
                    "&8Recharge : " + tongueCooldown() + " s &7(raté : " + num(tongueMissCooldown()) + " s)"};
        }
        if (m == ACID) {
            return new String[]{"&7Lance une boule d'acide : flaque de rayon " + num(getConfig().getDouble("abilities.acid-radius", 3)),
                    "&7pendant " + getConfig().getInt("abilities.acid-duration", 6) + " s. Les survivants dedans sont ralentis,",
                    "&7ne peuvent plus tirer et sont repoussés dehors.", "&7Parfait pour déloger un toit ou un coin !",
                    "&8Recharge : " + getConfig().getInt("abilities.acid-cooldown", 25) + " s"};
        }
        return new String[]{"&7Fonce tout droit sur ~" + num(getConfig().getDouble("abilities.charge-distance", 10)) + " blocs.",
                "&7Le premier survivant touché est projeté et étourdi",
                "&7" + num(getConfig().getDouble("abilities.charge-stun-seconds", 1.5)) + " s (très lent, ne peut plus tirer).",
                "&7Un mur t'arrête net. Idéal pour casser un groupe !",
                "&8Recharge : " + getConfig().getInt("abilities.charge-cooldown", 20) + " s"};
    }

    ItemStack powerItem(Material m) {
        String model = m == LEAP ? "bond" : m == TONGUE ? "langue" : m == ACID ? "acide" : null;
        ItemStack it = item(m, powerName(m) + " &7(clic droit)", powerLore(m));
        // la Charge n'a pas de texture dans le pack : objet vanilla (baton de blaze) meme avec textures: true
        return model == null ? it : skin(it, model);
    }

    ItemStack powerMenuItem() {
        ItemStack it = item(Material.NETHER_STAR, "&d&lChoisir mon pouvoir &7(clic droit)", "&7Bond, Langue, Crachat acide ou Charge",
                "&8Un seul exemplaire de chaque pouvoir par partie");
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, POWER_MENU_TAG);
        it.setItemMeta(m);
        return it;
    }

    /** Pouvoir deja tenu par un zombie encore en jeu -> son pseudo. */
    Map<Material, String> takenPowers() {
        Map<Material, String> m = new HashMap<>();
        for (Gamer g : players.values()) {
            if (g.zombie && g.power != null && !gone.contains(g.id)) {
                m.put(g.power, g.name);
            }
        }
        return m;
    }

    List<Material> freePowers() {
        Map<Material, String> taken = takenPowers();
        List<Material> l = new ArrayList<>();
        for (Material m : POWERS) {
            if (!taken.containsKey(m)) {
                l.add(m);
            }
        }
        return l;
    }

    int pendingChoices() {
        int n = 0;
        for (Gamer g : players.values()) {
            if (g.zombie && g.choosingPower && !gone.contains(g.id)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Nouveau zombie (alpha de depart, infecte, nouvel alpha) : s'il reste un pouvoir libre, une place lui est reservee
     * et il choisit dans le menu (au hasard apres choose-seconds). Sinon zombie classique.
     */
    void offerPower(Gamer g) {
        g.choosingPower = false;
        if (g.power != null || !abilities()) {
            return;
        }
        int used = takenPowers().size() + pendingChoices();
        if (used < maxPowered() && freePowers().size() > pendingChoices()) {
            g.choosingPower = true;
            g.choiceDeadline = System.currentTimeMillis() + 1000L * chooseSeconds();
        }
    }

    void openPowerMenu(Player p) {
        Gamer g = active(p);
        if (g == null || !g.zombie || !g.choosingPower) {
            return;
        }
        PowerMenu h = new PowerMenu();
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(h, 27, c("&8Choisis ton pouvoir de zombie"));
        h.inv = inv;
        Map<Material, String> taken = takenPowers();
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < POWERS.size(); i++) {
            Material m = POWERS.get(i);
            ItemStack it;
            if (taken.containsKey(m)) {
                it = item(Material.GRAY_DYE, "&8" + legacyStrip(powerName(m)) + " &7(déjà pris)", "&8Pris par " + taken.get(m));
            } else {
                List<String> lore = new ArrayList<>(List.of(powerLore(m)));
                lore.add("");
                lore.add("&eClique pour choisir");
                it = powerItem(m);
                ItemMeta meta = it.getItemMeta();
                List<Component> l = new ArrayList<>();
                for (String s : lore) {
                    l.add(c(s));
                }
                meta.lore(l);
                it.setItemMeta(meta);
            }
            ItemMeta meta = it.getItemMeta();
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, "power:" + m.name());
            it.setItemMeta(meta);
            inv.setItem(slots[i], it);
        }
        long left = Math.max(0, (g.choiceDeadline - System.currentTimeMillis() + 999) / 1000);
        inv.setItem(22, item(Material.CLOCK, "&7Choix au hasard dans &f" + left + " s", "&8Ferme le menu : l'étoile le rouvre"));
        p.openInventory(inv);
    }

    static String legacyStrip(String s) {
        return s.replaceAll("&[0-9a-fk-or]", "");
    }

    /** Clic dans le menu des pouvoirs (tick suivant le clic). */
    void powerMenuClick(Player p, ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return;
        }
        String t = it.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        if (t == null || !t.startsWith("power:")) {
            return;
        }
        Material m = Material.matchMaterial(t.substring(6));
        Gamer g = active(p);
        if (g == null || !g.zombie || !g.choosingPower || m == null || !POWERS.contains(m)) {
            // double clic ou choix deja fait (au hasard) : on ferme
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof PowerMenu) {
                p.closeInventory();
            }
            return;
        }
        if (takenPowers().containsKey(m)) {
            msg(p, "&c" + legacyStrip(powerName(m)) + " vient d'être pris : choisis-en un autre.");
            openPowerMenu(p);
            return;
        }
        grantPower(p, g, m, false);
    }

    /** Donne le pouvoir choisi (ou tire au sort), remplace l'etoile, rafraichit les menus ouverts des autres. */
    void grantPower(Player p, Gamer g, Material m, boolean random) {
        g.power = m;
        g.choosingPower = false;
        if (p != null) {
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it != null && it.hasItemMeta() && POWER_MENU_TAG.equals(it.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING))) {
                    inv.setItem(i, null);
                }
            }
            inv.setItem(1, powerItem(m));
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof PowerMenu) {
                p.closeInventory();
            }
            msg(p, (random ? "&7Pouvoir tiré au hasard : " : "&7Ton pouvoir de zombie : ") + powerName(m) + " &7(case 2, clic droit)");
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.6f, 1.6f);
        }
        for (Gamer o : players.values()) {
            Player op = Bukkit.getPlayer(o.id);
            if (op != null && o != g && o.choosingPower && op.getOpenInventory().getTopInventory().getHolder() instanceof PowerMenu) {
                openPowerMenu(op);
            }
        }
    }

    /** Chaque seconde : choix non fait a temps = pouvoir libre au hasard. */
    void powerDeadlines() {
        long now = System.currentTimeMillis();
        for (Gamer g : players.values()) {
            if (!g.choosingPower || gone.contains(g.id) || !g.zombie || now < g.choiceDeadline) {
                continue;
            }
            List<Material> free = freePowers();
            Player p = Bukkit.getPlayer(g.id);
            if (free.isEmpty()) {
                g.choosingPower = false;
                if (p != null) {
                    removePowerMenuItem(p);
                    msg(p, "&7Plus de pouvoir libre : tu es un zombie classique.");
                }
                continue;
            }
            grantPower(p, g, free.get(rnd.nextInt(free.size())), true);
        }
    }

    void removePowerMenuItem(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && POWER_MENU_TAG.equals(it.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING))) {
                inv.setItem(i, null);
            }
        }
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof PowerMenu) {
            p.closeInventory();
        }
    }
    /** Zombie qui peut agir : partie lancee, hors crypte, pas fige. */
    Gamer huntingZombie(Player p) {
        Gamer g = active(p);
        if (g == null || !g.zombie || g.inCrypt || g.frozen || state != State.PLAYING || p.getGameMode() == GameMode.SPECTATOR) {
            return null;
        }
        return g;
    }

    boolean survivor(Entity e) {
        if (!(e instanceof Player t)) {
            return false;
        }
        Gamer g = active(t);
        return g != null && !g.zombie && t.getGameMode() != GameMode.SPECTATOR;
    }

    /** Clic droit avec un pouvoir. Renvoie vrai si l'objet etait un pouvoir. */
    boolean useAbility(Player p, ItemStack it) {
        if (it == null || !isKit(it)) {
            return false;
        }
        Material m = it.getType();
        if (!POWERS.contains(m)) {
            return false;
        }
        Gamer g = huntingZombie(p);
        if (g == null || !abilities() || g.power != m) {
            return true;
        }
        if (p.hasCooldown(m)) {
            p.sendActionBar(c("&7Pas encore prêt : &f" + (p.getCooldown(m) + 19) / 20 + " s"));
            return true;
        }
        if (m == LEAP) {
            Vector v = p.getEyeLocation().getDirection().multiply(getConfig().getDouble("abilities.leap-power", 1.35));
            v.setY(Math.max(getConfig().getDouble("abilities.leap-up", 0.75), v.getY() + 0.35));
            p.setVelocity(v);
            world.playSound(p.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1f, 0.7f);
            world.spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.1, 0.3, 0.02);
            p.setCooldown(m, 20 * getConfig().getInt("abilities.leap-cooldown", 8));
        } else if (m == TONGUE) {
            tongue(p, m);
        } else if (m == ACID) {
            arsenal.spit(p);
            p.setCooldown(m, 20 * getConfig().getInt("abilities.acid-cooldown", 25));
        } else if (m == CHARGE) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1.0E-4) {
                return true;
            }
            g.chargeDir = dir.normalize();
            g.chargeUntil = arsenal.ticks + chargeTicks();
            p.setCooldown(m, 20 * getConfig().getInt("abilities.charge-cooldown", 20));
            world.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.3f);
            world.spawnParticle(Particle.CLOUD, p.getLocation(), 14, 0.3, 0.1, 0.3, 0.03);
        }
        return true;
    }

    double tongueRange() {
        return Math.max(2, getConfig().getDouble("abilities.tongue-range", 24));
    }

    int tongueCooldown() {
        return Math.max(1, getConfig().getInt("abilities.tongue-cooldown", 18));
    }

    double tongueMissCooldown() {
        return Math.max(0.5, getConfig().getDouble("abilities.tongue-miss-cooldown", 2));
    }

    /**
     * Langue (comportement d'origine 1.1.0/1.2.0, remis en 1.6.1) : rayTrace sur le survivant vise (les blocs coupent
     * la ligne de vue), il est tire d'un coup vers le zombie et ralenti. Pas de prise maintenue, rien a casser.
     */
    void tongue(Player p, Material m) {
        double range = tongueRange();
        Location eye = p.getEyeLocation();
        org.bukkit.util.RayTraceResult r = world.rayTrace(eye, eye.getDirection(), range, org.bukkit.FluidCollisionMode.NEVER, true, 0.4,
                e -> e != p && survivor(e));
        Player t = r == null || !(r.getHitEntity() instanceof Player h) ? null : h;
        if (t != null) {
            // le rayon des joueurs est elargi : on refuse une cible dont les yeux sont caches derriere un bloc (coin de mur)
            Vector toEye = t.getEyeLocation().toVector().subtract(eye.toVector());
            double d = toEye.length();
            if (d > 0.3 && world.rayTraceBlocks(eye, toEye.normalize(), d, org.bukkit.FluidCollisionMode.NEVER, true) != null) {
                t = null;
            }
        }
        if (t == null) {
            p.sendActionBar(c("&7Ta langue ne touche personne."));
            world.playSound(p.getLocation(), Sound.ENTITY_FROG_TONGUE, 1f, 0.6f);
            p.setCooldown(m, (int) Math.round(20 * tongueMissCooldown()));
            return;
        }
        Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
        double dist = pull.length();
        if (dist > 1.6) {
            // vitesse horizontale proportionnelle a la distance (la friction de l'air la ramene a ~0 en ~1 s), plafonnee
            double speed = Math.min(getConfig().getDouble("abilities.tongue-max-speed", 2.6),
                    Math.max(0.25, dist * getConfig().getDouble("abilities.tongue-pull", 0.12)));
            Vector v = pull.clone().setY(0);
            if (v.lengthSquared() > 1.0E-4) {
                v.normalize().multiply(speed);
            }
            double up = getConfig().getDouble("abilities.tongue-up", 0.42) + Math.max(0, Math.min(0.5, pull.getY() * 0.08));
            v.setY(up);
            t.setVelocity(v);
        }
        t.setFallDistance(0f);
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                (int) Math.round(20 * getConfig().getDouble("abilities.tongue-slow-seconds", 1.5)), 1, true, false, true));
        p.setCooldown(m, 20 * tongueCooldown());
        world.playSound(p.getLocation(), Sound.ENTITY_FROG_TONGUE, 1.2f, 0.5f);
        world.playSound(t.getLocation(), Sound.ENTITY_FROG_EAT, 1f, 0.7f);
        // trait rose le long de la langue
        Location te = t.getEyeLocation();
        Vector line = te.toVector().subtract(eye.toVector());
        int n = Math.min(24, Math.max(2, (int) (line.length() * 1.5)));
        Vector step = line.clone().multiply(1.0 / n);
        Location a = eye.clone().add(0, -0.3, 0);
        Particle.DustOptions pink = new Particle.DustOptions(Color.fromRGB(0xB0306A), 1.1f);
        for (int i = 0; i < n; i++) {
            world.spawnParticle(Particle.DUST, a.add(step), 1, 0, 0, 0, 0, pink);
        }
        Gamer gtv = active(t);
        if (gtv != null) {
            gtv.hitInfo = "&d&lLANGUE ! &7" + p.getName() + " t'a attrapé";
            gtv.hitInfoUntil = arsenal.ticks + 30;
        }
        p.sendActionBar(c("&dTu attrapes " + t.getName() + " !"));
    }

    /** Intervalle minimal entre deux baisses de coeurs d'un zombie (ticks). */
    int heartsInterval() {
        return Math.max(1, getConfig().getInt("zombie-hearts-interval", 10));
    }

    /**
     * Coeurs d'un zombie apres une perte de PV : mis a jour tout de suite si la derniere mise a jour date d'au moins
     * zombie-hearts-interval ticks, sinon a la fin de l'intervalle (une rafale ne fait qu'une secousse par intervalle).
     */
    void queueHearts(Player p, Gamer g) {
        if (arsenal.ticks >= g.heartsNext) {
            g.heartsDirty = false;
            g.heartsNext = arsenal.ticks + heartsInterval();
            showHearts(p, g);
        } else {
            g.heartsDirty = true;
        }
    }

    /** Chaque tick : coeurs en attente. */
    void heartsTick() {
        for (Gamer g : players.values()) {
            if (!g.heartsDirty || arsenal.ticks < g.heartsNext) {
                continue;
            }
            g.heartsDirty = false;
            Player p = Bukkit.getPlayer(g.id);
            if (p == null || gone.contains(g.id) || !inWorld(p)) {
                continue;
            }
            g.heartsNext = arsenal.ticks + heartsInterval();
            showHearts(p, g);
        }
    }

    /** Degats d'arme ou de grenade sur un zombie. Renvoie vrai s'il est renvoye a la crypte. */
    boolean damageZombie(Player z, Player by, double dmg, boolean head, Vector kb) {
        Gamer gz = active(z);
        if (gz == null || !gz.zombie || gz.inCrypt || state != State.PLAYING || dmg <= 0) {
            return false;
        }
        Gamer gb = by == null ? null : active(by);
        if (System.currentTimeMillis() < gz.protectedUntil) {
            if (gb != null) {
                gb.hitInfo = "&7" + gz.name + " est protégé";
                gb.hitInfoUntil = arsenal.ticks + 15;
            }
            return false;
        }
        gz.hp -= dmg;
        if (gz.hp <= 0) {
            gz.hp = 0;
            if (gb != null) {
                gb.hitInfo = "&a✔ " + gz.name + " renvoyé à la crypte";
                gb.hitInfoUntil = arsenal.ticks + 30;
            }
            sendToCrypt(z, by, true);
            return true;
        }
        // 1.6.0 : une balle (kb == null) ne fait ni recul ni animation de degat : avant, chaque balle de rafale
        // remplacait la vitesse du zombie (setVelocity), qui restait fige sur place. Recul reserve aux grenades.
        if (kb != null) {
            z.playHurtAnimation(0f);
            z.setVelocity(z.getVelocity().add(kb));
        } else {
            world.spawnParticle(Particle.BLOCK, z.getLocation().add(0, 1.1, 0), 4, 0.2, 0.3, 0.2, 0, Material.REDSTONE_BLOCK.createBlockData());
        }
        if (arsenal.ticks - gz.lastHurtSound >= 4) {
            gz.lastHurtSound = arsenal.ticks;
            world.playSound(z.getLocation(), Sound.ENTITY_ZOMBIE_HURT, 0.8f, 1.1f);
        }
        queueHearts(z, gz);
        refreshHealthTag(gz);
        if (by != null) {
            by.playSound(by.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.5f, head ? 1.8f : 1.2f);
        }
        if (gb != null) {
            gb.hitInfo = (head ? "&6&lTÊTE ! " : "&f") + gz.name + " &c❤ " + (int) Math.ceil(gz.hp) + "&7/" + num(zombieMaxHp());
            gb.hitInfoUntil = arsenal.ticks + 25;
        }
        return false;
    }

    /** Ligne sous le pseudo de tout le monde : PV des zombies, etat des survivants. */
    void refreshHealthTag(Gamer g) {
        for (Scoreboard b : boards.values()) {
            Objective o = b.getObjective("pv");
            if (o == null) {
                continue;
            }
            org.bukkit.scoreboard.Score sc = o.getScore(g.name);
            if (gone.contains(g.id)) {
                b.resetScores(g.name);
                continue;
            }
            if (g.zombie) {
                int hp = (int) Math.ceil(g.hp);
                sc.setScore(hp);
                sc.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.fixed(c(g.inCrypt ? "&8(crypte)" : "&c" + hp + "/" + num(zombieMaxHp()) + " ❤")));
            } else {
                int left = Math.max(0, survivorHits() - g.wounds);
                sc.setScore(left);
                sc.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.fixed(c(g.wounds > 0 ? "&e&lBLESSÉ" : "&a" + "❤".repeat(Math.max(1, left)))));
            }
        }
    }

    /** Chaque tick : charges en cours (vitesse imposee, arret contre un mur, premier survivant touche). */
    void chargeTick() {
        long now = arsenal.ticks;
        double speed = getConfig().getDouble("abilities.charge-distance", 10) / Math.max(4, chargeTicks());
        for (Gamer g : players.values()) {
            if (g.chargeUntil <= now || g.chargeDir == null) {
                continue;
            }
            Player z = Bukkit.getPlayer(g.id);
            if (z == null || huntingZombie(z) == null) {
                g.chargeUntil = 0;
                continue;
            }
            Location l = z.getLocation();
            Vector ahead = g.chargeDir.clone().multiply(0.6);
            if (blocked(z, ahead)) {
                g.chargeUntil = 0;
                z.setVelocity(new Vector(0, Math.min(0, z.getVelocity().getY()), 0));
                z.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 2, true, false, false));
                world.playSound(l, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.7f);
                world.spawnParticle(Particle.BLOCK, l.clone().add(ahead).add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0, Material.COBBLESTONE.createBlockData());
                z.sendActionBar(c("&6Charge arrêtée par un mur !"));
                continue;
            }
            double vy = z.getVelocity().getY();
            z.setVelocity(new Vector(g.chargeDir.getX() * speed, Math.min(vy, 0.2), g.chargeDir.getZ() * speed));
            z.setFallDistance(0f);
            if (now % 2 == 0) {
                world.spawnParticle(Particle.CLOUD, l.clone().add(0, 0.2, 0), 3, 0.2, 0.05, 0.2, 0.01);
            }
            BoundingBox zb = z.getBoundingBox().expand(0.55, 0.2, 0.55);
            for (Gamer o : players.values()) {
                if (o.zombie || gone.contains(o.id)) {
                    continue;
                }
                Player t = Bukkit.getPlayer(o.id);
                if (t == null || !inWorld(t) || t.getGameMode() == GameMode.SPECTATOR || !zb.overlaps(t.getBoundingBox())) {
                    continue;
                }
                // pas a travers une vitre, des barreaux ou une barriere
                Vector line = t.getEyeLocation().toVector().subtract(z.getEyeLocation().toVector());
                double dist = line.length();
                if (dist > 0.05 && world.rayTraceBlocks(z.getEyeLocation(), line.normalize(), dist, org.bukkit.FluidCollisionMode.NEVER, true) != null) {
                    continue;
                }
                chargeHit(z, g, t, o);
                break;
            }
        }
    }

    int chargeTicks() {
        return (int) Math.max(4, Math.round(getConfig().getDouble("abilities.charge-seconds", 0.8) * 20));
    }

    void chargeHit(Player z, Gamer gz, Player t, Gamer gt) {
        gz.chargeUntil = 0;
        z.setVelocity(new Vector(0, Math.min(0, z.getVelocity().getY()), 0));
        Vector kb = gz.chargeDir.clone().multiply(getConfig().getDouble("abilities.charge-knockback", 1.4));
        kb.setY(getConfig().getDouble("abilities.charge-knockback-y", 0.5));
        t.setVelocity(kb);
        int stun = (int) Math.max(5, Math.round(getConfig().getDouble("abilities.charge-stun-seconds", 1.5) * 20));
        gt.stunUntil = arsenal.ticks + stun;
        gt.holdUntil = 0;
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stun, 4, true, false, false));
        t.playHurtAnimation(0f);
        gt.hitInfo = "&6&lÉTOURDI ! &7(charge de " + gz.name + ")";
        gt.hitInfoUntil = arsenal.ticks + stun;
        world.playSound(t.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.2f, 0.9f);
        world.spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
        z.sendActionBar(c("&6Tu as percuté &a" + t.getName() + " &6!"));
    }

    /**
     * La charge est arretee si la boite du zombie, avancee de {@code ahead} et remontee de 0,6 (montee automatique d'une
     * dalle ou d'une marche), touche la forme de collision reelle d'un bloc : dalles, marches, tapis et portes ouvertes ne
     * l'arretent plus, un mur ou une porte fermee oui.
     */
    boolean blocked(Player z, Vector ahead) {
        BoundingBox probe = z.getBoundingBox().shift(ahead.getX(), 0.6, ahead.getZ()).expand(-0.05, -0.05, -0.05);
        for (int x = (int) Math.floor(probe.getMinX()); x <= (int) Math.floor(probe.getMaxX()); x++) {
            for (int y = (int) Math.floor(probe.getMinY()); y <= (int) Math.floor(probe.getMaxY()); y++) {
                for (int zz = (int) Math.floor(probe.getMinZ()); zz <= (int) Math.floor(probe.getMaxZ()); zz++) {
                    Block b = world.getBlockAt(x, y, zz);
                    if (b.getType().isAir()) {
                        continue;
                    }
                    BoundingBox local = probe.clone().shift(-x, -y, -zz);
                    if (b.getCollisionShape().overlaps(local)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    void zombieEffects(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    // ================================================================ deroulement

    private void tick() {
        if (world == null) {
            return;
        }
        int min = Math.max(1, getConfig().getInt("min-players", 3));
        int max = getConfig().getInt("max-players", 20);
        switch (state) {
            case WAITING -> {
                if (players.size() >= min) {
                    state = State.COUNTDOWN;
                    countdown = getConfig().getInt("countdown", 20);
                    tell("&aLa partie commence dans &e" + countdown + " s&a.");
                    sendStatus();
                }
            }
            case COUNTDOWN -> {
                if (players.size() < min) {
                    state = State.WAITING;
                    tell("&cPas assez de joueurs, décompte annulé.");
                    sendStatus();
                    return;
                }
                if (players.size() >= max && countdown > 5) {
                    countdown = 5;
                    tell("&aPartie pleine : départ dans &e5 s&a.");
                }
                countdown--;
                if (countdown <= 0) {
                    start();
                } else if (countdown <= 5 || countdown % 10 == 0) {
                    for (UUID id : players.keySet()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            p.showTitle(Title.title(c("&2" + countdown), c("&7Prépare-toi à courir"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                        }
                    }
                }
            }
            case FLEE -> {
                fleeLeft--;
                if (fleeLeft <= 5 && fleeLeft > 0) {
                    for (UUID id : all()) {
                        Player p = Bukkit.getPlayer(id);
                        Gamer g = players.get(id);
                        if (p != null) {
                            if (g != null && g.zombie) {
                                p.showTitle(Title.title(c("&2" + fleeLeft), c("&7Tu sors bientôt de la crypte"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                            }
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.6f);
                        }
                    }
                }
                gameTick();
                if (fleeLeft <= 0) {
                    release();
                }
            }
            case PLAYING -> {
                timeLeft--;
                int glow = getConfig().getInt("glow-last-seconds", 90);
                if (!lastMinute && timeLeft <= glow && timeLeft > 0) {
                    lastMinute = true;
                    tell(glowAlways() ? "&c&lDernière ligne droite ! &7Tenez bon." : "&c&lDernière ligne droite ! &7Les survivants brillent maintenant.");
                    titleAll("&cDERNIÈRE MINUTE", glowAlways() ? "&7Tenez bon !" : "&7Les survivants sont visibles", 2);
                    soundAll(Sound.BLOCK_BELL_USE, 1f, 0.7f);
                }
                gameTick();
                if (state == State.PLAYING && timeLeft <= 0) {
                    end(survivors() > 0);
                }
            }
            default -> {
            }
        }
    }

    static String cd(Player p, Material m, String name) {
        return p.hasCooldown(m) ? name + " &7" + (p.getCooldown(m) + 19) / 20 + "s" : name + " &a✔";
    }

    /** Contour des survivants : toujours (defaut) ou seulement pendant les dernieres secondes. */
    boolean glowAlways() {
        return !"last-seconds".equalsIgnoreCase(getConfig().getString("survivor-glow", "always"));
    }

    /** Chaque seconde pendant la fuite et la partie. */
    void gameTick() {
        int grenMax = Math.max(0, getConfig().getInt("grenades.max", 2));
        int grenRegen = Math.max(1, getConfig().getInt("grenades.regen-seconds", 60));
        int ammoRegen = getConfig().getInt("weapons.ammo-regen-seconds", 30);
        long healMs = 1000L * Math.max(1, getConfig().getInt("wound-heal-seconds", 25));
        long now = System.currentTimeMillis();
        int ash = getConfig().getInt("ash-particles", 18);
        for (Gamer g : players.values()) {
            if (gone.contains(g.id)) {
                continue;
            }
            Player p = Bukkit.getPlayer(g.id);
            if (p == null) {
                continue;
            }
            if (g.zombie) {
                if (!p.hasPotionEffect(PotionEffectType.SPEED)) {
                    zombieEffects(p);
                }
                // 1.6.0 : meme regle pour tous les zombies (de depart ou infectes) ; couleur du contour = equipe (rouge)
                if (p.isGlowing() != zombieGlow()) {
                    p.setGlowing(zombieGlow());
                }
                if (!g.heartsDirty) {
                    // simple remise a niveau (pas de baisse en attente) : ne consomme pas le creneau de queueHearts
                    showHearts(p, g);
                }
                String hp = "&c❤ " + (int) Math.ceil(g.hp) + "&7/" + num(zombieMaxHp());
                if (g.frozen) {
                    p.sendActionBar(c("&2Dans la crypte... &7sortie dans &f" + fleeLeft + " s"));
                } else if (g.inCrypt) {
                    p.sendActionBar(c("&2Retour à la crypte..."));
                } else if (g.choosingPower) {
                    long left = Math.max(0, (g.choiceDeadline - System.currentTimeMillis() + 999) / 1000);
                    p.sendActionBar(c(hp + " &8| &d&lChoisis ton pouvoir &7(étoile, case 2) : &f" + left + " s"));
                } else if (abilities() && state == State.PLAYING && g.power != null) {
                    p.sendActionBar(c(hp + " &8| " + (g.chargeUntil > arsenal.ticks ? "&6&lCHARGE !" : cd(p, g.power, powerName(g.power)))));
                } else if (state == State.PLAYING) {
                    p.sendActionBar(c(hp));
                }
            } else {
                p.setFoodLevel(20);
                boolean wantGlow = glowAlways() || lastMinute;
                if (p.isGlowing() != wantGlow) {
                    p.setGlowing(wantGlow);
                }
                if (g.wounds > 0 && now - g.lastWound >= healMs) {
                    g.wounds = 0;
                    msg(p, "&aTa blessure est soignée.");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
                }
                showHearts(p, g);
                // grenades : +1 toutes les N secondes, jusqu'au maximum
                if (g.grenades < grenMax) {
                    if (++g.grenadeTimer >= grenRegen) {
                        g.grenadeTimer = 0;
                        g.grenades++;
                        arsenal.syncGrenades(p, g);
                        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.6f, 1.5f);
                    }
                } else {
                    g.grenadeTimer = 0;
                }
                // munitions : un chargeur de reserve rendu a l'arme principale toutes les N secondes (sans depasser la reserve de depart)
                if (ammoRegen > 0 && g.kit != null && state == State.PLAYING) {
                    Arsenal.Gun prim = arsenal.gun(g.kit);
                    int res = g.reserve.getOrDefault(prim.id(), prim.reserve());
                    if (res >= 0 && res < prim.reserve()) {
                        if (++g.ammoTimer >= ammoRegen) {
                            g.ammoTimer = 0;
                            g.reserve.put(prim.id(), Math.min(prim.reserve(), res + prim.mag()));
                        }
                    } else {
                        g.ammoTimer = 0;
                    }
                }
            }
        }
        if (ash > 0) {
            for (UUID id : all()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && inWorld(p)) {
                    p.spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 3, 0), ash, 8, 4, 8, 0);
                    p.spawnParticle(Particle.ASH, p.getLocation().add(0, 2, 0), ash / 2, 6, 3, 6, 0);
                }
            }
        }
        powerDeadlines();
        checkState();
        updateBar();
        updateBoards();
        sendStatus();
    }

    void start() {
        gen++;
        gone.clear();
        state = State.FLEE;
        lastMinute = false;
        lastAnnounced = false;
        int min = Math.max(1, getConfig().getInt("min-players", 3));
        testGame = players.size() < min;
        fleeLeft = Math.max(3, getConfig().getInt("flee-time", 30));
        timeLeft = Math.max(30, getConfig().getInt("game-time", 480));
        arsenal.clearGame();
        world.setTime(18000);
        List<UUID> ids = new ArrayList<>(players.keySet());
        Collections.shuffle(ids);
        int ratio = Math.max(2, getConfig().getInt("zombie-ratio", 6));
        int nz = ids.size() >= 2 ? Math.max(1, ids.size() / ratio) : 0;
        // 2 zombies de depart a partir de N joueurs
        if (ids.size() >= getConfig().getInt("two-zombies-from", 8)) {
            nz = Math.max(nz, 2);
        }

        for (int i = 0; i < ids.size(); i++) {
            Gamer g = players.get(ids.get(i));
            g.zombie = i < nz;
            g.alpha = g.zombie;
            g.frozen = g.zombie;
            g.inCrypt = g.zombie;
            g.infections = 0;
            g.hits = 0;
            g.protectedUntil = 0;
            g.power = null;
            g.choosingPower = false;
            g.chargeUntil = 0;
            g.stunUntil = 0;
            g.hp = zombieMaxHp();
            g.wounds = 0;
            g.lastWound = 0;
            g.reloading = null;
            g.next.clear();
            g.holdUntil = 0;
            g.grenades = Math.max(0, Math.min(getConfig().getInt("grenades.max", 2), getConfig().getInt("grenades.start", 1)));
            g.grenadeTimer = 0;
            g.ammoTimer = 0;
            g.hitInfo = null;
            g.heartsDirty = false;
            g.heartsNext = 0;
        }
        // pouvoirs : chaque zombie de depart choisit le sien dans un menu (places reservees dans l'ordre)
        for (UUID id : ids) {
            Gamer g = players.get(id);
            if (g.zombie) {
                offerPower(g);
            }
        }
        bar = Bukkit.createBossBar(legacy("&cFuite"), BarColor.RED, BarStyle.SOLID);
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                bar.addPlayer(p);
                p.setScoreboard(boardOf(id));
            }
        }
        List<Location> placed = new ArrayList<>();
        for (UUID id : ids) {
            Gamer g = players.get(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            if (g.zombie) {
                p.teleport(cryptSpawn());
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (fleeLeft + 2) * 20, 0, true, false, false));
                title(p, "&2&lZOMBIE ALPHA", "&7Tu sors de la crypte dans " + fleeLeft + " s", 3);
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 0.6f);
            } else {
                Location l = disperse(placed);
                placed.add(l);
                p.teleport(l);
                title(p, "&a&lFUIS !", "&7Zombies dans " + fleeLeft + " s - choisis ton arme", 3);
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.7f, 1.2f);
            }
            later(2L, () -> {
                Player q = Bukkit.getPlayer(id);
                Gamer gg = players.get(id);
                if (q == null || gg == null || gone.contains(id)) {
                    return;
                }
                if (gg.zombie) {
                    giveZombieKit(q, gg.alpha);
                    q.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (fleeLeft + 2) * 20, 0, true, false, false));
                } else {
                    giveSurvivorKit(q);
                }
            });
        }
        StringBuilder names = new StringBuilder();
        for (Gamer g : players.values()) {
            if (g.zombie) {
                names.append(names.length() > 0 ? "&7, " : "").append("&2").append(g.name);
            }
        }
        tell("&a&lC'est parti ! &7Cachez-vous dans la ville, les zombies sortent dans &e" + fleeLeft + " s&7.");
        tell("&7Survivants : l'&eétoile &7(case 8) change d'arme jusqu'à la fin de la fuite. &fClic droit &7= tirer, &fF &7= recharger.");
        if (names.length() > 0) {
            tell("&7Zombie(s) alpha : " + names);
        } else {
            tell("&8Partie de test : aucun zombie (pas assez de joueurs).");
        }
        if (testGame) {
            tell("&8Partie de test (moins de " + min + " joueurs) : pas de récompenses.");
        }
        updateBar();
        updateBoards();
        sendStatus();
    }

    /** Choisit une case de dispersion loin des autres survivants et de la sortie de la crypte. */
    Location disperse(List<Location> placed) {
        if (spots.isEmpty()) {
            return waitSpawn();
        }
        double minD = getConfig().getDouble("spread-distance", 10);
        Location exit = exitSpawn();
        for (int pass = 0; pass < 3; pass++) {
            for (int tries = 0; tries < 300; tries++) {
                int[] s = spots.get(rnd.nextInt(spots.size()));
                if (pass < 2 && s[1] > G + 11) {
                    continue;
                }
                Location l = new Location(world, s[0] + 0.5, s[1], s[2] + 0.5, rnd.nextFloat() * 360f - 180f, 0f);
                if (pass == 0 && l.distance(exit) < 16) {
                    continue;
                }
                boolean ok = true;
                for (Location o : placed) {
                    if (o.distanceSquared(l) < (pass == 0 ? minD * minD : minD * minD / 4)) {
                        ok = false;
                        break;
                    }
                }
                if (ok || pass == 2) {
                    return l;
                }
            }
        }
        int[] s = spots.get(rnd.nextInt(spots.size()));
        return new Location(world, s[0] + 0.5, s[1], s[2] + 0.5);
    }

    void release() {
        if (state != State.FLEE) {
            return;
        }
        state = State.PLAYING;
        for (Gamer g : players.values()) {
            if (gone.contains(g.id) || !g.zombie) {
                continue;
            }
            g.frozen = false;
            g.inCrypt = false;
            Player p = Bukkit.getPlayer(g.id);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                g.protectedUntil = System.currentTimeMillis() + 1000L * getConfig().getInt("respawn-protection", 2);
                p.teleport(zombieSpawn());
                title(p, "&2&lCHASSE !", "&7Frappe les survivants pour les infecter", 2);
            }
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            Gamer g = players.get(id);
            if (p != null && (g == null || !g.zombie)) {
                title(p, "&2&lILS ARRIVENT !", "&7Les zombies surgissent dans la ville", 2);
                if (g != null && !gone.contains(id) && inWorld(p)) {
                    arsenal.removeKitMenuItem(p);
                }
            }
        }
        soundAll(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.4f);
        tell("&2&lLes zombies sont lâchés ! &7Tenez &e" + fmt(timeLeft) + "&7.");
        updateBar();
        sendStatus();
    }

    /** Fin de partie possible, nouveau zombie si tous les zombies sont partis. */
    void checkState() {
        if (state != State.FLEE && state != State.PLAYING) {
            return;
        }
        int s = survivors();
        int z = zombies();
        if (s == 0 && z == 0) {
            end(false);
            return;
        }
        if (s == 0) {
            end(false);
            return;
        }
        if (z == 0 && s >= 2 && !testGame) {
            // tous les zombies sont partis : un survivant au hasard devient zombie alpha
            List<Gamer> l = new ArrayList<>();
            for (Gamer g : players.values()) {
                if (!gone.contains(g.id) && !g.zombie) {
                    l.add(g);
                }
            }
            Gamer g = l.get(rnd.nextInt(l.size()));
            g.zombie = true;
            g.alpha = true;
            g.wounds = 0;
            offerPower(g);
            Player p = Bukkit.getPlayer(g.id);
            tell("&7Plus aucun zombie : &2" + g.name + " &7devient le zombie alpha !");
            if (p != null) {
                p.setGlowing(false);
                giveZombieKit(p, true);
                sendToCrypt(p, null, false);
            }
            updateBoards();
            return;
        }
        if (z == 0 && s == 1 && !testGame) {
            // tous les zombies sont partis et il ne reste qu'un survivant : inutile d'attendre la fin du temps
            end(true);
            return;
        }
        if (s == 1 && !lastAnnounced && players.size() - gone.size() >= 2) {
            lastAnnounced = true;
            for (Gamer g : players.values()) {
                if (!gone.contains(g.id) && !g.zombie) {
                    tell("&e&lDernier survivant : &a" + g.name + " &e!");
                    titleAll("&eDERNIER EN VIE", "&a" + g.name, 2);
                }
            }
        }
    }

    void infect(Player victim, Player by) {
        Gamer gv = players.get(victim.getUniqueId());
        Gamer gb = players.get(by.getUniqueId());
        if (gv == null || gb == null || gv.zombie) {
            return;
        }
        gv.zombie = true;
        gv.alpha = false;
        gv.wounds = 0;
        gv.stunUntil = 0;
        offerPower(gv);
        gv.protectedUntil = System.currentTimeMillis() + 1000L * getConfig().getInt("respawn-protection", 2);
        gb.infections++;
        if (!testGame) {
            eemi("eemi reward " + gb.name + " " + coreId() + " kill");
        }
        Location l = victim.getLocation();
        spawning = true;
        try {
            world.strikeLightningEffect(l);
        } finally {
            spawning = false;
        }
        world.playSound(l, Sound.ENTITY_ZOMBIE_INFECT, 1.5f, 0.8f);
        world.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
        victim.setGlowing(false);
        giveZombieKit(victim, false);
        title(victim, "&2&lINFECTÉ !", "&7Infecte les survivants à ton tour", 2);
        title(by, "&2+1 infection", "&7" + gv.name, 1);
        by.playSound(by.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        tell("&c" + gv.name + " &7a été infecté par &2" + gb.name + " &8(" + survivors() + " survivant" + (survivors() > 1 ? "s" : "") + ")");
        updateBoards();
        checkState();
        sendStatus();
    }

    /** Zombie a 0 PV (ou nouveau zombie alpha) : passage par la crypte, PV remis au maximum. */
    void sendToCrypt(Player z, Player by, boolean killed) {
        Gamer gz = players.get(z.getUniqueId());
        if (gz == null) {
            return;
        }
        gz.heartsDirty = false;
        gz.chargeUntil = 0;
        gz.inCrypt = true;
        gz.hp = zombieMaxHp();
        world.spawnParticle(Particle.SMOKE, z.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
        z.teleport(cryptSpawn());
        showHearts(z, gz);
        refreshHealthTag(gz);
        int delay = Math.max(1, getConfig().getInt("respawn-delay", 3));
        if (killed) {
            Gamer gb = by == null ? null : players.get(by.getUniqueId());
            if (gb != null) {
                gb.hits++;
            }
            title(z, "&cABATTU !", "&7Retour à la crypte (" + delay + " s)", 1);
            if (by != null) {
                by.playSound(by.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            }
            tell("&a" + (gb != null ? gb.name : by != null ? by.getName() : "Une grenade") + " &7renvoie &2" + gz.name + " &7à la crypte.");
        }
        z.playSound(z.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 0.5f);
        UUID id = z.getUniqueId();
        later(delay * 20L, () -> {
            Gamer g = players.get(id);
            Player p = Bukkit.getPlayer(id);
            if (g == null || p == null || gone.contains(id) || !g.zombie || !inWorld(p)) {
                return;
            }
            if (state == State.FLEE) {
                // sortira avec les autres a la fin de la fuite
                g.frozen = true;
                return;
            }
            g.inCrypt = false;
            g.hp = zombieMaxHp();
            g.protectedUntil = System.currentTimeMillis() + 1000L * getConfig().getInt("respawn-protection", 2);
            p.teleport(zombieSpawn());
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 0.8f);
            refreshHealthTag(g);
        });
    }

    /** Coup de zombie sur un survivant : le premier blesse, le N-ieme infecte. */
    void zombieHit(Player victim, Player attacker, Gamer gv) {
        long now = System.currentTimeMillis();
        if (now - gv.lastWound < getConfig().getLong("hit-grace-ms", 800)) {
            return;
        }
        gv.wounds++;
        gv.lastWound = now;
        if (gv.wounds >= survivorHits()) {
            infect(victim, attacker);
            return;
        }
        int left = survivorHits() - gv.wounds;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Math.max(1, getConfig().getInt("wound-slow-ticks", 30)), 1, true, false, false));
        victim.playHurtAnimation(0f);
        world.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1.2, 0), 8, 0.3, 0.4, 0.3, 0.1);
        title(victim, "", "&c&lBLESSÉ ! &7Encore " + left + " coup" + (left > 1 ? "s" : "") + " et tu es infecté", 2);
        msg(victim, "&cTu es blessé par &2" + attacker.getName() + "&c. &7Soigné après &f" + getConfig().getInt("wound-heal-seconds", 25) + " s &7sans être touché.");
        attacker.sendActionBar(c("&eTu as blessé &a" + victim.getName() + " &7(encore " + left + ")"));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.6f, 1.4f);
        showHearts(victim, gv);
        refreshHealthTag(gv);
    }

    void knockback(Player attacker, Player victim) {
        Gamer gv = players.get(victim.getUniqueId());
        long now = System.currentTimeMillis();
        if (gv == null || now - gv.lastKnock < getConfig().getLong("sword-cooldown-ms", 450)) {
            return;
        }
        gv.lastKnock = now;
        Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = attacker.getLocation().getDirection().setY(0);
        }
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize().multiply(getConfig().getDouble("sword-knockback", 1.1));
        dir.setY(getConfig().getDouble("sword-knockback-y", 0.38));
        victim.setVelocity(dir);
        victim.playHurtAnimation(0f);
        world.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
        double sd = getConfig().getDouble("sword-damage", 2);
        if (sd > 0) {
            damageZombie(victim, attacker, sd, false, null);
        }
    }

    void end(boolean survivorsWin) {
        if (state != State.FLEE && state != State.PLAYING) {
            return;
        }
        state = State.ENDING;
        List<Gamer> winners = new ArrayList<>();
        for (Gamer g : players.values()) {
            if (gone.contains(g.id)) {
                continue;
            }
            if (survivorsWin ? !g.zombie : g.zombie) {
                winners.add(g);
            }
        }
        if (!testGame) {
            for (Gamer g : players.values()) {
                if (!gone.contains(g.id)) {
                    eemi("eemi reward " + g.name + " " + coreId() + " play");
                }
            }
            for (Gamer g : winners) {
                eemi("eemi reward " + g.name + " " + coreId() + " win");
            }
        }
        if (survivorsWin) {
            StringBuilder n = new StringBuilder();
            for (Gamer g : winners) {
                n.append(n.length() > 0 ? "&7, " : "").append("&a").append(g.name);
            }
            tell("&a&lLes survivants ont tenu ! &7Victoire de " + n + "&7.");
            Bukkit.broadcast(c(P + "&7Les survivants (" + n + "&7) gagnent une partie d'Infection !"));
        } else {
            tell("&2&lTout le monde est infecté ! &7Victoire des zombies.");
            Bukkit.broadcast(c(P + "&7Les zombies gagnent une partie d'Infection !"));
        }
        // meilleurs infecteurs
        List<Gamer> top = new ArrayList<>(players.values());
        top.sort((a, b) -> b.infections - a.infections);
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            if (top.get(i).infections > 0) {
                t.append("\n &e").append(i + 1).append(". &f").append(top.get(i).name).append(" &7- &c").append(top.get(i).infections).append(" infection(s)");
            }
        }
        if (t.length() > 0) {
            tell("&7Meilleurs infecteurs :" + t);
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Gamer g = players.get(id);
            boolean won = g != null && winners.contains(g);
            title(p, won ? "&6&lVICTOIRE !" : g == null ? "&7Fin de partie" : "&c&lDÉFAITE", survivorsWin ? "&aLes survivants ont tenu" : "&2Les zombies ont tout infecté", 4);
            if (won) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.3);
            }
        }
        updateBar();
        sendStatus();
        // 1.7.0 : fin normale -> retour dans la salle d'attente, dans la file de la partie suivante
        // (un /infection stop garde le retour au hub : finish())
        later(160L, this::finishToWaiting);
    }

    /**
     * 1.7.0 (demande d'Esteban) : fin normale. Les joueurs de la partie encore la (pas ceux partis) et les spectateurs
     * venus par /infection join pendant la partie restent inscrits : salle d'attente, etat d'attente (inventaire vide
     * + choix d'arme + lit « Quitter » en case 8). Le decompte repart tout seul des qu'il y a assez de joueurs.
     * Les simples spectateurs (/infection spectate) repartent au hub comme avant.
     */
    void finishToWaiting() {
        gen++;
        arsenal.clearGame();
        Map<UUID, Gamer> old = new LinkedHashMap<>(players);
        List<UUID> keep = new ArrayList<>();
        for (Gamer g : old.values()) {
            if (!gone.contains(g.id)) {
                keep.add(g.id);
            }
        }
        for (UUID s : spectators) {
            if (wantsNext.contains(s) && !keep.contains(s)) {
                keep.add(s);
            }
        }
        List<UUID> others = new ArrayList<>(spectators);
        others.removeAll(keep);
        // on vide d'abord : le changement de monde des autres ne repasse pas par leave()
        players.clear();
        spectators.clear();
        gone.clear();
        wantsNext.clear();
        boards.clear();
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        state = State.WAITING;
        for (UUID id : others) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            if (inWorld(p)) {
                toHub(p);
            } else {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                p.setGlowing(false);
                removeKit(p);
            }
        }
        int max = getConfig().getInt("max-players", 20);
        for (UUID id : keep) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            if (!inWorld(p) || players.size() >= max) {
                if (inWorld(p)) {
                    toHub(p);
                } else {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    p.setGlowing(false);
                    removeKit(p);
                }
                continue;
            }
            Gamer ng = new Gamer(id, p.getName());
            Gamer was = old.get(id);
            if (was != null) {
                ng.kit = was.kit;     // l'arme choisie reste la meme pour la partie suivante
            }
            players.put(id, ng);
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Arsenal.KitMenu
                    || p.getOpenInventory().getTopInventory().getHolder() instanceof PowerMenu) {
                p.closeInventory();
            }
            // teleporter d'abord : l'inventaire est vide par prepareWaiting() 2 ticks plus tard, dans la salle d'attente
            if (p.getGameMode() == GameMode.SPECTATOR) {
                p.setSpectatorTarget(null);
            }
            p.teleport(waitSpawn());
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.setGlowing(false);
            p.setFireTicks(0);
            p.setFallDistance(0);
            clearEffects(p);
            fullHealth(p);
            prepareWaiting(p);
            msg(p, "&7Tu restes dans la file de l'&2Infection&7. Clique sur le lit (ou &f/infection leave&7) pour revenir au lobby.");
        }
        sendStatus();
    }

    /** Fin : tout le monde au hub. */
    void finish() {
        gen++;
        arsenal.clearGame();
        List<UUID> everyone = all();
        // on vide d'abord : le changement de monde qui suit ne repasse pas par leave()
        players.clear();
        spectators.clear();
        gone.clear();
        wantsNext.clear();
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                if (inWorld(p)) {
                    toHub(p);
                } else {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    p.setGlowing(false);
                    removeKit(p);
                }
            }
        }
        boards.clear();
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        state = State.WAITING;
        sendStatus();
    }

    void updateBar() {
        if (bar == null) {
            return;
        }
        switch (state) {
            case FLEE -> {
                int tot = Math.max(1, getConfig().getInt("flee-time", 30));
                bar.setTitle(legacy("&cFuite : les zombies sortent dans &f" + fleeLeft + " s"));
                bar.setColor(BarColor.RED);
                bar.setProgress(Math.max(0, Math.min(1, fleeLeft / (double) tot)));
            }
            case PLAYING -> {
                int tot = Math.max(1, getConfig().getInt("game-time", 480));
                bar.setTitle(legacy((lastMinute ? "&c" : "&a") + "Survivre : &f" + fmt(timeLeft) + " &8| &a" + survivors() + " survivant(s) &8| &2" + zombies() + " zombie(s)"));
                bar.setColor(lastMinute ? BarColor.RED : BarColor.GREEN);
                bar.setProgress(Math.max(0, Math.min(1, timeLeft / (double) tot)));
            }
            case ENDING -> {
                bar.setTitle(legacy("&7Fin de partie"));
                bar.setProgress(0);
            }
            default -> {
            }
        }
    }

    static String fmt(int s) {
        s = Math.max(0, s);
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    Scoreboard boardOf(UUID id) {
        return boards.computeIfAbsent(id, k -> Bukkit.getScoreboardManager().getNewScoreboard());
    }

    void updateBoards() {
        int s = survivors();
        int z = zombies();
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Scoreboard b = boardOf(id);
            if (p.getScoreboard() != b && inWorld(p)) {
                p.setScoreboard(b);
            }
            // equipes : pseudos colores
            Team ts = b.getTeam("a_surv");
            if (ts == null) {
                ts = b.registerNewTeam("a_surv");
                ts.color(NamedTextColor.WHITE);
                ts.prefix(c("&f[S] "));
            }
            // 1.6.0 : la couleur de l'equipe donne aussi la couleur du contour lumineux (glowing) vu par ce joueur :
            // survivants en blanc, zombies en vert (demande d'Esteban, 16/11/2026)
            Team tz = b.getTeam("b_zomb");
            if (tz == null) {
                tz = b.registerNewTeam("b_zomb");
                tz.color(NamedTextColor.GREEN);
                tz.prefix(c("&2[Z] "));
            }
            for (Gamer g : players.values()) {
                if (gone.contains(g.id)) {
                    continue;
                }
                Team want = g.zombie ? tz : ts;
                if (!want.hasEntry(g.name)) {
                    want.addEntry(g.name);
                }
            }
            // sous le pseudo : PV des zombies, blessure des survivants
            if (state == State.FLEE || state == State.PLAYING) {
                if (b.getObjective("pv") == null) {
                    Objective pv = b.registerNewObjective("pv", Criteria.DUMMY, c("&c❤"));
                    pv.setDisplaySlot(DisplaySlot.BELOW_NAME);
                }
            } else {
                Objective pv = b.getObjective("pv");
                if (pv != null) {
                    pv.unregister();
                }
            }
        }
        if (state == State.FLEE || state == State.PLAYING) {
            for (Gamer g : players.values()) {
                refreshHealthTag(g);
            }
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Scoreboard b = boardOf(id);
            Objective o = b.getObjective("inf");
            if (o != null) {
                o.unregister();
            }
            o = b.registerNewObjective("inf", Criteria.DUMMY, c("&2&lINFECTION"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            Gamer me = players.get(id);
            int line = 10;
            String time = state == State.FLEE ? "&cFuite : &f" + fleeLeft + " s" : state == State.PLAYING ? "&7Temps : &f" + fmt(timeLeft) : "&7Fin de partie";
            o.getScore(legacy(time)).setScore(line--);
            o.getScore(legacy(" ")).setScore(line--);
            o.getScore(legacy("&aSurvivants : &f" + s)).setScore(line--);
            o.getScore(legacy("&2Zombies : &f" + z)).setScore(line--);
            o.getScore(legacy("  ")).setScore(line--);
            if (me != null && !gone.contains(id)) {
                o.getScore(legacy("&7Rôle : " + (me.zombie ? (me.alpha ? "&2Zombie alpha" : "&2Zombie") : "&aSurvivant"))).setScore(line--);
                o.getScore(legacy("&7Infections : &c" + me.infections)).setScore(line--);
                if (!me.zombie || me.hits > 0) {
                    o.getScore(legacy("&7Zombies abattus : &e" + me.hits)).setScore(line--);
                }
            } else {
                o.getScore(legacy("&7Rôle : &8spectateur")).setScore(line--);
            }
            o.getScore(legacy("   ")).setScore(line--);
            o.getScore(legacy("&eeemicraft.mine.fun")).setScore(line);
        }
    }

    // ================================================================ entree / sortie

    String coreId() {
        return getConfig().getString("eemicore-id", "infection");
    }

    void eemi(String cmd) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private String lastStatus = "";

    void sendStatus() {
        String st = switch (state) {
            case WAITING, COUNTDOWN -> "waiting";
            case FLEE, PLAYING -> "playing";
            case ENDING -> "ending";
        };
        boolean inGame = state == State.FLEE || state == State.PLAYING;
        int waiting = inGame ? 0 : players.size();
        int playing = inGame ? survivors() + zombies() : 0;
        String cmd = "eemi status " + coreId() + " " + st + " " + waiting + " " + playing + " " + getConfig().getInt("max-players", 20);
        // pas de spam de la console : seulement si quelque chose change
        if (!cmd.equals(lastStatus)) {
            lastStatus = cmd;
            eemi(cmd);
        }
    }

    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!inWorld(p) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemiinfection.admin")) {
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
        if (spectators.contains(id) && (state == State.WAITING || state == State.COUNTDOWN)) {
            spectators.remove(id);
        } else if (players.containsKey(id) && !gone.contains(id)) {
            msg(p, "&7Tu es déjà dans la partie. &f/infection leave &7pour sortir.");
            return;
        } else if (spectators.contains(id)) {
            // 1.7.0 : un spectateur qui fait /infection join jouera a la partie suivante
            wantsNext.add(id);
            msg(p, "&7Tu regardes la partie, tu joueras à la suivante.");
            return;
        }
        if (state != State.WAITING && state != State.COUNTDOWN) {
            msg(p, "&7Une partie est en cours : tu la regardes, tu joueras à la suivante.");
            spectate(p);
            wantsNext.add(id);
            return;
        }
        if (players.size() >= getConfig().getInt("max-players", 20)) {
            msg(p, "&cLa partie est pleine.");
            return;
        }
        players.put(id, new Gamer(id, p.getName()));
        p.teleport(waitSpawn());
        prepareWaiting(p);
        tell("&a" + p.getName() + " &7rejoint l'Infection (&f" + players.size() + "&7/&f" + getConfig().getInt("max-players", 20) + "&7)");
        int min = getConfig().getInt("min-players", 3);
        if (players.size() < min) {
            msg(p, "&7En attente de &f" + (min - players.size()) + " &7joueur(s)...");
        }
        sendStatus();
    }

    /** Salle d'attente : inventaire vide 2 ticks apres la teleportation (Multiverse-Inventories). */
    void prepareWaiting(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setAllowFlight(false);
        p.setFlying(false);
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player q = Bukkit.getPlayer(id);
            if (q == null || !inWorld(q) || !players.containsKey(id) || (state != State.WAITING && state != State.COUNTDOWN)) {
                return;
            }
            q.getInventory().clear();
            q.getInventory().setArmorContents(new ItemStack[4]);
            clearEffects(q);
            q.setGlowing(false);
            fullHealth(q);
            q.getInventory().setItem(4, arsenal.kitMenuItem());
            q.getInventory().setItem(8, leaveItem());
            q.getInventory().setHeldItemSlot(4);
        }, 2L);
    }

    void spectate(Player p) {
        UUID id = p.getUniqueId();
        if (!spectators.contains(id)) {
            spectators.add(id);
        }
        p.teleport(spectSpawn());
        p.setGameMode(GameMode.SPECTATOR);
        if (bar != null) {
            bar.addPlayer(p);
        }
        if (state == State.FLEE || state == State.PLAYING || state == State.ENDING) {
            p.setScoreboard(boardOf(id));
        }
    }

    /** Sortie de la partie (commande, deconnexion, changement de monde). */
    void leave(Player p, boolean teleport) {
        UUID id = p.getUniqueId();
        boolean was = players.containsKey(id) || spectators.contains(id);
        if (!was) {
            return;
        }
        spectators.remove(id);
        wantsNext.remove(id);
        Gamer g = players.get(id);
        if (g != null) {
            if (state == State.FLEE || state == State.PLAYING) {
                if (!gone.contains(id)) {
                    gone.add(id);
                    tell("&7" + g.name + " &7(" + (g.zombie ? "&2zombie" : "&asurvivant") + "&7) abandonne.");
                    later(1L, this::checkState);
                }
            } else if (state == State.ENDING) {
                gone.add(id);
            } else {
                players.remove(id);
                tell("&7" + g.name + " quitte la partie.");
            }
        }
        if (bar != null) {
            bar.removePlayer(p);
        }
        boards.remove(id);
        p.setGlowing(false);
        if (g != null) {
            g.choosingPower = false;
        }
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof PowerMenu) {
            p.closeInventory();
        }
        if (teleport && inWorld(p)) {
            toHub(p);
        } else {
            // deja sorti du monde : on ne retire que nos objets et nos effets
            removeKit(p);
            clearEffects(p);
            if (!inWorld(p)) {
                fullHealth(p);
            }
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // arrive au hub par /lobby : plus en spectateur/aventure (les ops ne sont pas remis par Multiverse)
            if (!inWorld(p) && p.getWorld().getName().equals(getConfig().getString("hub.world", "lobby"))
                    && (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.ADVENTURE)) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
        sendStatus();
    }

    Location hub() {
        String wn = getConfig().getString("hub.world", "lobby");
        World w = Bukkit.getWorld(wn);
        if (w == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(w, getConfig().getDouble("hub.x", 722.5), getConfig().getDouble("hub.y", 51), getConfig().getDouble("hub.z", 650.5),
                (float) getConfig().getDouble("hub.yaw", 90), (float) getConfig().getDouble("hub.pitch", 0));
    }

    void toHub(Player p) {
        // on vide AVANT de changer de monde : rien du jeu ne part au lobby
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        clearEffects(p);
        p.setGlowing(false);
        p.setFireTicks(0);
        p.setFoodLevel(20);
        fullHealth(p);
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Arsenal.KitMenu
                || p.getOpenInventory().getTopInventory().getHolder() instanceof PowerMenu) {
            p.closeInventory();
        }
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (bar != null) {
            bar.removePlayer(p);
        }
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(hub());
    }

    // ================================================================ evenements

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inWorld(p) || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location to = e.getTo();
        Location from = e.getFrom();
        Gamer g = active(p);
        if (g != null && g.frozen && state == State.FLEE) {
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location l = from.clone();
                l.setYaw(to.getYaw());
                l.setPitch(to.getPitch());
                e.setTo(l);
            }
            return;
        }
        if (to.getY() < G - 14) {
            // securite : tombe sous la ville
            if (g == null) {
                if (!(p.isOp() && p.getGameMode() == GameMode.CREATIVE)) {
                    p.teleport(players.containsKey(p.getUniqueId()) ? waitSpawn() : spectSpawn());
                }
            } else if (state == State.WAITING || state == State.COUNTDOWN) {
                p.teleport(waitSpawn());
            } else if (g.zombie) {
                g.chargeUntil = 0;
                p.teleport(g.inCrypt ? cryptSpawn() : zombieSpawn());
            } else {
                p.teleport(disperse(List.of()));
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
                // un joueur parti en pleine partie (garde dans players + gone) revient au hub, pas en fantome dans la ville
                if (p.isOnline() && inWorld(p) && active(p) == null) {
                    toHub(p);
                }
            }, 10L);
        }
    }

    private boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE && !players.containsKey(p.getUniqueId());
    }

    /** Tous les degats sont annules ; on applique l'effet du jeu (infection, crypte, recul). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world)) {
            return;
        }
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent ev) || !(e.getEntity() instanceof Player victim) || state != State.PLAYING) {
            return;
        }
        Gamer gv = active(victim);
        if (gv == null) {
            return;
        }
        Entity d = ev.getDamager();
        if (d instanceof Player attacker) {
            Gamer ga = active(attacker);
            if (ga == null || attacker.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                return;
            }
            if (ga.zombie && !gv.zombie && !ga.inCrypt && victim.getGameMode() != GameMode.SPECTATOR) {
                zombieHit(victim, attacker, gv);
            } else if (!ga.zombie && gv.zombie && !gv.inCrypt) {
                knockback(attacker, victim);
            }
        } else if (d instanceof AbstractArrow arrow) {
            Bukkit.getScheduler().runTask(this, arrow::remove);
        }
    }

    /**
     * 1.6.1 : aucune regeneration vanilla pour les inscrits dans le monde du jeu. Les coeurs affichent les PV du plugin
     * (zombies) ou les blessures (survivants) : une regen naturelle remonterait la vie et la remise a niveau suivante
     * referait l'inclinaison de degat chaque seconde.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRegain(org.bukkit.event.entity.EntityRegainHealthEvent e) {
        if (world == null || !(e.getEntity() instanceof Player p) || !p.getWorld().equals(world)) {
            return;
        }
        if (active(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world)) {
            return;
        }
        if (spawning) {
            // nos grenades et crachats acides
            e.setCancelled(false);
            return;
        }
        if (e.getEntity().getShooter() instanceof Player p && builder(p)) {
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world)) {
            return;
        }
        Entity pr = e.getEntity();
        UUID id = pr.getUniqueId();
        if (arsenal.grenades.containsKey(id)) {
            UUID owner = arsenal.grenades.remove(id);
            Location at = pr.getLocation();
            Bukkit.getScheduler().runTask(this, () -> arsenal.explode(at, owner));
            pr.remove();
        } else if (arsenal.spits.containsKey(id)) {
            UUID owner = arsenal.spits.remove(id);
            Location at = pr.getLocation();
            Bukkit.getScheduler().runTask(this, () -> arsenal.puddle(at, owner));
            pr.remove();
        } else if (pr instanceof AbstractArrow a) {
            Bukkit.getScheduler().runTaskLater(this, a::remove, 2L);
        }
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

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (world == null || !p.getWorld().equals(world) || builder(p)) {
            return;
        }
        boolean door = e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && openable(e.getClickedBlock().getType());
        if (door) {
            // portes, portillons et trappes en bois de la ville : on peut les ouvrir (sinon on reste enferme dans une maison)
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.ALLOW);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack it = e.getItem();
            if (isLeaveItem(it)) {
                e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                if (players.containsKey(p.getUniqueId())) {
                    leave(p, true);
                }
                return;
            }
            if (useAbility(p, it)) {
                e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                e.setCancelled(true);
                return;
            }
            String tag = arsenal.tag(it);
            if (tag != null && e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                Gamer g = active(p);
                if ("kitmenu".equals(tag)) {
                    if (g != null && (state == State.WAITING || state == State.COUNTDOWN || (state == State.FLEE && !g.zombie))) {
                        arsenal.openMenu(p);
                    }
                } else if (POWER_MENU_TAG.equals(tag)) {
                    if (g != null && g.zombie && g.choosingPower) {
                        openPowerMenu(p);
                    } else {
                        removePowerMenuItem(p);
                    }
                } else if (g != null && !g.zombie && p.getGameMode() != GameMode.SPECTATOR && !door
                        && (state == State.FLEE || state == State.PLAYING)) {
                    if (tag.startsWith("gun:")) {
                        Arsenal.Gun gun = arsenal.guns.get(tag.substring(4));
                        if (gun != null) {
                            arsenal.trigger(p, g, gun);
                        }
                    } else if (tag.equals("grenade")) {
                        arsenal.throwGrenade(p, g);
                    }
                }
            }
            // objets du lobby (perles, fusees, charges de vent...) inutilisables ici
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    /** Menu des kits (sans ignoreCancelled : le clic est de toute facon annule dans ce monde). */
    @EventHandler(priority = EventPriority.LOW)
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Arsenal.KitMenu && e.getWhoClicked() instanceof Player p) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().getHolder() instanceof Arsenal.KitMenu) {
                ItemStack it = e.getCurrentItem();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (p.isOnline()) {
                        arsenal.menuClick(p, it);
                    }
                });
            }
        }
    }

    /** Menu des pouvoirs : un clic = un choix, traite au tick suivant (le double clic retrouve choosingPower a false). */
    @EventHandler(priority = EventPriority.LOW)
    public void onPowerMenuClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof PowerMenu && e.getWhoClicked() instanceof Player p) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().getHolder() instanceof PowerMenu) {
                ItemStack it = e.getCurrentItem();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (p.isOnline()) {
                        powerMenuClick(p, it);
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMenuDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Arsenal.KitMenu || e.getView().getTopInventory().getHolder() instanceof PowerMenu) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && inWorld(p) && !builder(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (inWorld(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
            // touche F : recharger l'arme en main
            Gamer g = active(e.getPlayer());
            if (g != null && !g.zombie) {
                arsenal.manualReload(e.getPlayer(), g);
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
    public void onFood(FoodLevelChangeEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    // ================================================================ commandes

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "join" : a[0].toLowerCase();
        boolean admin = s.hasPermission("eemiinfection.admin");
        switch (sub) {
            case "join", "rejoindre" -> {
                if (s instanceof Player p) {
                    join(p);
                }
            }
            case "leave", "quitter" -> {
                if (s instanceof Player p) {
                    if ((players.containsKey(p.getUniqueId()) && !gone.contains(p.getUniqueId())) || spectators.contains(p.getUniqueId())) {
                        leave(p, true);
                    } else {
                        msg(p, "&7Tu n'es pas dans la partie.");
                    }
                }
            }
            case "kit", "arme" -> {
                if (s instanceof Player p) {
                    Gamer g = active(p);
                    if (g != null && inWorld(p) && (state == State.WAITING || state == State.COUNTDOWN || (state == State.FLEE && !g.zombie))) {
                        arsenal.openMenu(p);
                    } else {
                        msg(p, "&7Le choix de l'arme se fait avant la partie et pendant la fuite.");
                    }
                }
            }
            case "pouvoir", "power" -> {
                if (s instanceof Player p) {
                    Gamer g = active(p);
                    if (g != null && g.zombie && g.choosingPower && inWorld(p)) {
                        openPowerMenu(p);
                    } else {
                        msg(p, "&7Aucun pouvoir a choisir pour l'instant.");
                    }
                }
            }
            case "spectate", "regarder" -> {
                if (s instanceof Player p) {
                    if (players.containsKey(p.getUniqueId()) && !gone.contains(p.getUniqueId())) {
                        msg(p, "&7Tu es déjà inscrit.");
                    } else if (canEnter(p)) {
                        spectate(p);
                    }
                }
            }
            case "start" -> {
                if (!admin) {
                    msg(s, "&cPermission refusée.");
                } else if (state != State.WAITING && state != State.COUNTDOWN) {
                    msg(s, "&cUne partie est déjà en cours.");
                } else if (players.isEmpty()) {
                    msg(s, "&cPersonne n'est inscrit.");
                } else {
                    start();
                }
            }
            case "stop" -> {
                if (!admin) {
                    msg(s, "&cPermission refusée.");
                } else {
                    if (state == State.FLEE || state == State.PLAYING || state == State.ENDING) {
                        tell("&cPartie arrêtée par un admin.");
                    }
                    finish();
                    msg(s, "&7Partie arrêtée, attente remise à zéro.");
                }
            }
            case "build" -> {
                if (!admin) {
                    msg(s, "&cPermission refusée.");
                } else if (state != State.WAITING && state != State.COUNTDOWN) {
                    msg(s, "&cImpossible pendant une partie.");
                } else {
                    for (UUID id : new ArrayList<>(players.keySet())) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            msg(p, "&7La ville est reconstruite, reviens dans un instant.");
                            leave(p, true);
                        }
                    }
                    removeEntities();
                    build();
                    spawnRules();
                    msg(s, "&aVille reconstruite (" + spots.size() + " cases de dispersion).");
                }
            }
            case "reload" -> {
                if (!admin) {
                    msg(s, "&cPermission refusée.");
                } else {
                    reloadConfig();
                    arsenal.load();
                    applyAmbience();
                    removeEntities();
                    spawnRules();
                    msg(s, "&aConfig rechargée.");
                }
            }
            case "info" -> msg(s, "&7État &f" + state + "&7, inscrits &f" + players.size() + "&7, survivants &f" + survivors() + "&7, zombies &f" + zombies()
                    + "&7, temps &f" + (state == State.FLEE ? fleeLeft + " s (fuite)" : fmt(timeLeft)) + "&7, cases &f" + spots.size());
            default -> {
                msg(s, "&f/infection join &7- rejoindre | &f/infection leave &7- quitter | &f/infection kit &7- choisir l'arme | &f/infection spectate &7- regarder");
                if (admin) {
                    msg(s, "&c/infection start | stop | build | reload | info");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>(List.of("join", "leave", "kit", "pouvoir", "spectate"));
        if (s.hasPermission("eemiinfection.admin")) {
            out.addAll(List.of("start", "stop", "build", "reload", "info"));
        }
        if (a.length == 1) {
            out.removeIf(x -> !x.startsWith(a[0].toLowerCase()));
            return out;
        }
        return List.of();
    }
}
