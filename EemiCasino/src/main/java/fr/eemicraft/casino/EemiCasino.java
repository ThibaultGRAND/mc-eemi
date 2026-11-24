package fr.eemicraft.casino;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class EemiCasino extends JavaPlugin implements Listener {
    static final String TAG = "eemicasino";
    static final int DEALER_ROULETTE = 3;
    static final int DEALER_CASHIER = 4;
    static final int DEALER_SHOP = 5;

    enum Kind { SLOT, VP, BJ, ROULETTE, POKER, CASHIER, SHOP, EXIT, WHEEL, PLINKO }

    record Station(Kind kind, int index) {
    }

    World world;
    long jackpot;
    private final Map<UUID, PData> players = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<String> bigWins = new ArrayList<>();
    /** Classement permanent des 10 plus gros gains en un coup (carte #113). */
    final BigWins bigTop = new BigWins();
    private List<PData> top = new ArrayList<>();
    private boolean dirty;
    private boolean spawning;
    private int buildVersion;
    private File dataFile;
    private final Random rnd = new Random();

    private final Map<UUID, Station> stations = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final Mannequin[] dealers = new Mannequin[6];
    private final TextDisplay[] bubbles = new TextDisplay[6];
    private final int[] bubbleToken = new int[6];
    private final TextDisplay[] slotScreens = new TextDisplay[Layout.SLOT_X.length];
    private final TextDisplay[] pokerLabels = new TextDisplay[Layout.PK_Z.length];
    private TextDisplay rouletteLabel;
    private TextDisplay jackpotBoard;
    private TextDisplay topBoard;
    private TextDisplay winsBoard;
    private TextDisplay bigTopBoard;
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Long> menuClick = new HashMap<>();
    private volatile Map<UUID, Integer> ranks = new HashMap<>();

    Slots slots;
    Blackjack blackjack;
    Roulette roulette;
    VideoPoker videoPoker;
    Poker poker;
    Fortune fortune;
    Plinko plinko;
    RouletteWheel rouletteWheel;
    TableCards tableCards;
    Machines machines;

    // =========================================================== cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();
        slots = new Slots(this);
        blackjack = new Blackjack(this);
        roulette = new Roulette(this);
        videoPoker = new VideoPoker(this);
        poker = new Poker(this);
        fortune = new Fortune(this);
        plinko = new Plinko(this);
        rouletteWheel = new RouletteWheel(this);
        tableCards = new TableCards(this);
        machines = new Machines(this);
        Icons.on = Models.on(this);
        createWorld();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, this::spawnEntities);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (dirty || !poker.onTables().isEmpty()) {
                saveData();
            }
        }, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, this::updateJackpotBoard, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            computeTop();
            updateTopBoard();
            updateBigTopBoard();
            sendStatus();
        }, 60L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, poker::tick, 10L, 10L);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new Papi(this).register();
                getLogger().info("Placeholders %eemicasino_...% enregistres.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI : " + t);
            }
        }
        computeTop();
        getLogger().info("Casino pret : " + players.size() + " comptes, jackpot " + jackpot + " jetons.");
    }

    @Override
    public void onDisable() {
        // poker en premier (tapis), chaque jeu protege des autres
        Runnable[] stops = {poker::shutdown, blackjack::shutdown, roulette::shutdown, videoPoker::shutdown, slots::shutdown, fortune::shutdown, plinko::shutdown, rouletteWheel::stop};
        for (Runnable r : stops) {
            try {
                r.run();
            } catch (Throwable t) {
                getLogger().warning("Arret d'un jeu : " + t);
            }
        }
        removeEntities();
        saveData();
    }

    // =========================================================== monde

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
        String name = getConfig().getString("world", "casino");
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
        world.setTime(6000);
        world.setStorm(false);
        world.setSpawnLocation(spawn());
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
            }
        }
        if (buildVersion != buildSignature()) {
            rebuild();
        }
    }

    void rebuild() {
        long t0 = System.currentTimeMillis();
        BukkitSink sink = new BukkitSink(world, getLogger());
        new Builder(sink, Models.on(this)).build();
        buildVersion = buildSignature();
        dirty = true;
        getLogger().info("Salle du casino construite (" + sink.operations() + " blocs, " + sink.invalidStates().size() + " invalides, " + (System.currentTimeMillis() - t0) + " ms).");
    }

    /** Version de la salle + modeles actifs (les cabines en blocs disparaissent avec les machines 3D). */
    int buildSignature() {
        return Layout.BUILD_VERSION * 10 + (Models.on(this) ? 1 : 0);
    }

    Location spawn() {
        List<Double> s = getConfig().getDoubleList("spawn");
        if (s.size() < 3) {
            return new Location(world, 0.5, 101, 26.5, 180, 0);
        }
        return new Location(world, s.get(0), s.get(1), s.get(2), s.size() > 3 ? s.get(3).floatValue() : 0f, s.size() > 4 ? s.get(4).floatValue() : 0f);
    }

    boolean inCasino(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    // =========================================================== entites

    private void removeEntities() {
        for (Entity e : entities) {
            if (e.isValid()) {
                e.remove();
            }
        }
        entities.clear();
        stations.clear();
        if (world != null) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
    }

    void spawnEntities() {
        if (world == null) {
            return;
        }
        removeEntities();
        final double Y = Layout.FLOOR + 1;
        final boolean models3d = Models.on(this);
        for (int i = 0; i < Layout.SLOT_X.length; i++) {
            double x = Layout.SLOT_X[i] + 0.5;
            interaction(x, Y, Layout.SLOT_Z + 0.5, 1.1f, 3.2f, new Station(Kind.SLOT, i));
            slotScreens[i] = models3d ? text(x, Y + 3.15, Layout.SLOT_Z + 1.25, "", 0.45f, Display.Billboard.VERTICAL)
                    : text(x, Y + 1.25, Layout.SLOT_Z + 1.08, "&f[ &c&l7 &f| &c&l7 &f| &c&l7 &f]", 0.45f, Display.Billboard.VERTICAL);
        }
        text(0.5, Y + 4.4, Layout.SLOT_Z + 1.2, "&e&lMACHINES A SOUS &7- clic sur une machine", 0.9f, Display.Billboard.VERTICAL);
        for (int i = 0; i < Layout.VP_Z.length; i++) {
            interaction(Layout.VP_X + 0.5, Y, Layout.VP_Z[i] + 0.5, 1.1f, 3.2f, new Station(Kind.VP, i));
            if (!models3d) {
                text(Layout.VP_X - 0.2, Y + 1.25, Layout.VP_Z[i] + 0.5, "&9&lVIDEO\n&9&lPOKER", 0.45f, Display.Billboard.VERTICAL);
            }
        }
        for (int i = 0; i < Layout.BJ_Z.length; i++) {
            double cz = Layout.BJ_Z[i] + 0.5;
            interaction(Layout.BJ_X1 + 1.5, Y, cz, 3.2f, 1.3f, new Station(Kind.BJ, i));
            text(Layout.BJ_X1 + 1.5, Y + 2.3, cz, "&a&lBLACKJACK\n&7Clic sur la table pour jouer\n&8Mises de 10 a " + U.fmt(cfgLong("max-bet.blackjack", 10000)), 0.8f, Display.Billboard.CENTER);
            dealer(i, Layout.BJ_X1 - 0.5, Y, cz, -90f, new Station(Kind.BJ, i), "&7Croupier de blackjack");
        }
        interaction(-1.5, Y, 0.5, 5.2f, 1.3f, new Station(Kind.ROULETTE, 0));
        interaction(2.5, Y, 0.5, 5.2f, 1.3f, new Station(Kind.ROULETTE, 0));
        rouletteLabel = text(0.5, Y + 2.4, 0.5, "", 0.8f, Display.Billboard.CENTER);
        rouletteBoard();
        dealer(DEALER_ROULETTE, -4.5, Y, 0.5, -90f, new Station(Kind.ROULETTE, 0), "&7Croupier de roulette");
        for (int i = 0; i < Layout.PK_Z.length && i < poker.tables.size(); i++) {
            double cz = Layout.PK_Z[i] + 0.5;
            interaction(20.5, Y, cz, 7.2f, 1.3f, new Station(Kind.POKER, i));
            pokerLabels[i] = text(20.5, Y + 2.4, cz, poker.label(poker.tables.get(i)), 0.8f, Display.Billboard.CENTER);
        }
        interaction(-10.5, Y, Layout.COUNTER_Z + 0.5, 7.2f, 1.5f, new Station(Kind.CASHIER, 0));
        text(-10.5, Y + 3.2, Layout.COUNTER_Z + 0.5, "&6&lCAISSE\n&7Achat de jetons\n&e&lBonus du jour", 1.0f, Display.Billboard.CENTER);
        dealer(DEALER_CASHIER, -10.5, Y, Layout.COUNTER_Z + 2.0, 180f, new Station(Kind.CASHIER, 0), "&7Caissier");
        interaction(11.5, Y, Layout.COUNTER_Z + 0.5, 7.2f, 1.5f, new Station(Kind.SHOP, 0));
        text(11.5, Y + 3.2, Layout.COUNTER_Z + 0.5, "&d&lBOUTIQUE DU CASINO\n&7Titres et cosmetiques exclusifs", 1.0f, Display.Billboard.CENTER);
        dealer(DEALER_SHOP, 11.5, Y, Layout.COUNTER_Z + 2.0, 180f, new Station(Kind.SHOP, 0), "&7Boutiquier");
        interaction(0.5, Y, 29.5, 3.2f, 3.2f, new Station(Kind.EXIT, 0));
        text(0.5, Y + 4.8, 29.2, "&a&lSORTIE\n&7Retour au lobby", 1.0f, Display.Billboard.CENTER);
        jackpotBoard = text(0.5, Y + 6.8, Layout.SLOT_Z + 1.5, "", 2.6f, Display.Billboard.VERTICAL);
        topBoard = text(-23.5, Y + 2.4, 24.5, "", 0.9f, Display.Billboard.VERTICAL);
        winsBoard = text(23.5, Y + 2.4, 24.5, "", 0.9f, Display.Billboard.VERTICAL);
        text(0.5, Y + 3.0, 21.5, "&6&l★ CASINO EEMICRAFT ★\n&7Jetons a la caisse, bonus chaque jour\n&e/casino &7pour le menu", 1.0f, Display.Billboard.VERTICAL);
        // plus gros gains en un coup : au-dessus du panneau d'accueil, dans l'allee d'entree (entre les deux kiosques)
        bigTopBoard = text(0.5, Y + 4.7, 21.5, "", 0.9f, Display.Billboard.VERTICAL);
        // grande roue (ouest) et plinko (est), entre la roulette et la sortie
        for (double z : new double[]{12.0, 13.5, 15.0}) {
            interaction(-9.6, Y, z, 1.5f, 6.2f, new Station(Kind.WHEEL, 0));
            interaction(10.6, Y, z, 1.5f, 6.2f, new Station(Kind.PLINKO, 0));
        }
        text(-8.6, Y + 6.6, 13.5, "&6&lROUE DE LA FORTUNE\n&7x0 a x10 - clic pour miser", 0.9f, Display.Billboard.CENTER);
        text(9.4, Y + 6.6, 13.5, "&d&lPLINKO\n&7Clic droit : lacher une bille\n&7Clic gauche : choisir la mise", 0.9f, Display.Billboard.CENTER);
        // modeles 3D (pack de textures) : roue de roulette, grande roue, plinko, cartes sur les tables
        TextDisplay[] scores = new TextDisplay[Layout.BJ_Z.length];
        for (int i = 0; i < Layout.BJ_Z.length; i++) {
            scores[i] = text(Layout.BJ_X1 + 1.9, Y + 1.45, Layout.BJ_Z[i] + 0.5, "", 0.5f, Display.Billboard.CENTER);
        }
        tableCards.reset(scores);
        rouletteWheel.spawn();
        machines.spawn();
        fortune.spawn();
        plinko.spawn();
        updateJackpotBoard();
        updateTopBoard();
        updateWinsBoard();
        updateBigTopBoard();
        getLogger().info("Entites du casino : " + entities.size() + " (croupiers : " + countDealers() + "/6).");
    }

    private int countDealers() {
        int n = 0;
        for (Mannequin m : dealers) {
            if (m != null && m.isValid()) {
                n++;
            }
        }
        return n;
    }

    private void interaction(double x, double y, double z, float w, float h, Station st) {
        spawning = true;
        try {
            Interaction e = world.spawn(new Location(world, x, y, z), Interaction.class, it -> {
                it.setInteractionWidth(w);
                it.setInteractionHeight(h);
                it.setResponsive(true);
                it.setPersistent(false);
                it.addScoreboardTag(TAG);
            });
            entities.add(e);
            stations.put(e.getUniqueId(), st);
        } finally {
            spawning = false;
        }
    }

    /** Entite ItemDisplay du casino (modeles 3D) : non sauvegardee, taguee, apparition retablie malgre WorldGuard. */
    ItemDisplay spawnDisplay(Location l, java.util.function.Consumer<ItemDisplay> setup) {
        if (world == null) {
            return null;
        }
        spawning = true;
        try {
            ItemDisplay d = world.spawn(l, ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.addScoreboardTag(TAG);
                setup.accept(e);
            });
            return d.isValid() ? d : null;
        } finally {
            spawning = false;
        }
    }

    /** Message aux joueurs du casino proches d'un point. */
    void broadcastNear(Location l, double radius, String m) {
        if (world == null) {
            return;
        }
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= radius * radius) {
                U.msg(p, m);
            }
        }
    }

    TextDisplay text(double x, double y, double z, String txt, float scale, Display.Billboard bb) {
        spawning = true;
        try {
            TextDisplay d = world.spawn(new Location(world, x, y, z), TextDisplay.class, t -> {
                t.text(U.c(txt));
                t.setBillboard(bb);
                t.setPersistent(false);
                t.setShadowed(true);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                t.setLineWidth(400);
                t.setViewRange(1.5f);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                t.setBrightness(new Display.Brightness(15, 15));
                t.addScoreboardTag(TAG);
            });
            entities.add(d);
            return d;
        } finally {
            spawning = false;
        }
    }

    private void dealer(int idx, double x, double y, double z, float yaw, Station st, String desc) {
        spawning = true;
        try {
            String tex = getConfig().getString("dealer.texture", "");
            String sig = getConfig().getString("dealer.signature", "");
            Mannequin m = world.spawn(new Location(world, x, y, z, yaw, 0f), Mannequin.class, e -> {
                if (!tex.isEmpty()) {
                    e.setProfile(ResolvableProfile.resolvableProfile().name("Croupier").addProperty(new ProfileProperty("textures", tex, sig.isEmpty() ? null : sig)).build());
                }
                e.setImmovable(true);
                e.setInvulnerable(true);
                e.setGravity(false);
                e.setSilent(true);
                e.setPersistent(false);
                e.setCollidable(false);
                e.customName(U.c(getConfig().getString("dealer.name", "&6Croupier")));
                e.setCustomNameVisible(true);
                e.setDescription(U.c(desc));
                e.addScoreboardTag(TAG);
            });
            if (m.isValid()) {
                dealers[idx] = m;
                entities.add(m);
                stations.put(m.getUniqueId(), st);
            } else {
                getLogger().warning("Le croupier " + idx + " n'a pas pu apparaitre.");
            }
            bubbles[idx] = text(x, y + 2.55, z, "", 0.7f, Display.Billboard.CENTER);
        } finally {
            spawning = false;
        }
    }

    /** WorldGuard bloque les apparitions par plugin : on retablit les notres. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent e) {
        if (spawning && e.getEntity().getWorld().equals(world)) {
            e.setCancelled(false);
        }
    }

    void dealerSay(int idx, String text) {
        if (idx < 0 || idx >= bubbles.length || bubbles[idx] == null || !bubbles[idx].isValid()) {
            return;
        }
        bubbles[idx].text(U.c(text));
        int token = ++bubbleToken[idx];
        later(80, () -> {
            if (bubbleToken[idx] == token && bubbles[idx] != null && bubbles[idx].isValid()) {
                bubbles[idx].text(U.c(""));
            }
        });
    }

    void dealerLook(int idx, Player p) {
        if (idx < 0 || idx >= dealers.length) {
            return;
        }
        Mannequin m = dealers[idx];
        if (m == null || !m.isValid() || p == null || !p.isOnline() || !p.getWorld().equals(world) || p.getLocation().distanceSquared(m.getLocation()) > 400) {
            return;
        }
        double dx = p.getX() - m.getX();
        double dz = p.getZ() - m.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        m.setRotation(yaw, 0f);
    }

    void dealerSwing(int idx, Player p) {
        if (idx < 0 || idx >= dealers.length) {
            return;
        }
        Mannequin m = dealers[idx];
        if (m == null || !m.isValid()) {
            return;
        }
        dealerLook(idx, p);
        m.swingMainHand();
    }

    void slotScreen(int machine, String text, boolean win) {
        if (machine < 0 || machine >= slotScreens.length || slotScreens[machine] == null || !slotScreens[machine].isValid()) {
            return;
        }
        slotScreens[machine].text(U.c(text));
        if (win) {
            Location l = slotScreens[machine].getLocation();
            world.spawnParticle(Particle.HAPPY_VILLAGER, l, 15, 0.4, 0.4, 0.2, 0);
        }
    }

    void rouletteBoard() {
        if (rouletteLabel == null || !rouletteLabel.isValid()) {
            return;
        }
        StringBuilder b = new StringBuilder("&c&lROULETTE\n&7Clic sur la table pour jouer");
        List<Integer> h = roulette.history;
        if (!h.isEmpty()) {
            b.append("\n");
            for (int i = h.size() - 1; i >= Math.max(0, h.size() - 8); i--) {
                b.append(Roulette.color(h.get(i))).append("&l").append(h.get(i)).append(' ');
            }
        }
        rouletteLabel.text(U.c(b.toString()));
    }

    void pokerBoard(int id, String text) {
        if (id >= 0 && id < pokerLabels.length && pokerLabels[id] != null && pokerLabels[id].isValid()) {
            pokerLabels[id].text(U.c(text));
        }
    }

    private void updateJackpotBoard() {
        if (jackpotBoard != null && jackpotBoard.isValid()) {
            jackpotBoard.text(U.c("&6&l★ JACKPOT ★\n&e&l" + U.fmt(jackpot) + " jetons"));
        }
    }

    private void updateTopBoard() {
        if (topBoard == null || !topBoard.isValid()) {
            return;
        }
        StringBuilder b = new StringBuilder("&6&lLES PLUS RICHES");
        for (int i = 0; i < 10; i++) {
            if (i < top.size()) {
                PData d = top.get(i);
                b.append("\n").append(i == 0 ? "&6" : i == 1 ? "&f" : i == 2 ? "&c" : "&7").append(i + 1).append(". &f").append(d.name).append(" &7- &e").append(U.fmt(d.chips));
            }
        }
        if (top.isEmpty()) {
            b.append("\n&7Personne pour l'instant");
        }
        topBoard.text(U.c(b.toString()));
    }

    private void updateWinsBoard() {
        if (winsBoard == null || !winsBoard.isValid()) {
            return;
        }
        StringBuilder b = new StringBuilder("&a&lDERNIERS GROS GAINS");
        for (int i = bigWins.size() - 1; i >= Math.max(0, bigWins.size() - 8); i--) {
            b.append("\n").append(bigWins.get(i));
        }
        if (bigWins.isEmpty()) {
            b.append("\n&7A toi d'ouvrir le bal !");
        }
        winsBoard.text(U.c(b.toString()));
    }

    private static String rankColor(int i) {
        return i == 0 ? "&6&l" : i == 1 ? "&f" : i == 2 ? "&c" : "&7";
    }

    void updateBigTopBoard() {
        if (bigTopBoard == null || !bigTopBoard.isValid()) {
            return;
        }
        StringBuilder b = new StringBuilder("&6&l★ PLUS GROS GAINS ★\n&7en un seul coup");
        List<BigWins.Entry> l = bigTop.list();
        for (int i = 0; i < l.size(); i++) {
            BigWins.Entry e = l.get(i);
            String g = BigWins.gameName(e.game());
            b.append("\n").append(rankColor(i)).append(i + 1).append(". &f").append(e.name()).append(" &7- &6").append(U.fmt(e.amount()))
                    .append(g.equals("?") ? "" : " &8" + g);
        }
        if (l.isEmpty()) {
            b.append("\n&7À toi d'écrire l'histoire !");
        }
        bigTopBoard.text(U.c(b.toString()));
    }

    /** Propose un gain net au classement des plus gros gains, annonce une entree ou un record. */
    private void offerBigWin(Player p, long net, String game) {
        if (net <= 0) {
            return;
        }
        BigWins.Change c = bigTop.offer(p.getUniqueId(), p.getName(), net, game, System.currentTimeMillis());
        if (!c.entered()) {
            return;
        }
        dirty = true;
        updateBigTopBoard();
        String g = BigWins.gameName(game);
        String jeu = g.equals("?") ? "" : " &8(" + g + ")";
        if (c.record()) {
            broadcast("&6&l★ NOUVEAU RECORD ! &e" + p.getName() + " &7remporte &6&l" + U.fmt(net) + " jetons &7en un seul coup" + jeu + " &7: plus gros gain du casino !");
            // apres le titre du resultat du jeu
            later(40, () -> {
                if (p.isOnline()) {
                    p.showTitle(U.title("&6&l★ RECORD ★", "&ePlus gros gain du casino !"));
                    celebrate(p, true);
                }
            });
            return;
        }
        String m = c.oldRank() < 0
                ? "&e" + p.getName() + " &7entre à la &f" + c.newRank() + (c.newRank() == 1 ? "re" : "e") + " place &7des plus gros gains : &6" + U.fmt(net) + " jetons" + jeu + " !"
                : "&e" + p.getName() + " &7bat son record et passe à la &f" + c.newRank() + (c.newRank() == 1 ? "re" : "e") + " place &7des plus gros gains : &6" + U.fmt(net) + " jetons" + jeu + " !";
        if (world != null) {
            for (Player o : world.getPlayers()) {
                U.msg(o, m);
            }
        }
        if (!inCasino(p)) {
            U.msg(p, m);
        }
    }

    // =========================================================== comptes et jetons

    PData data(Player p) {
        PData d = players.computeIfAbsent(p.getUniqueId(), PData::new);
        if (!p.getName().equals(d.name)) {
            d.name = p.getName();
            bigTop.rename(p.getUniqueId(), d.name);
            dirty = true;
        }
        return d;
    }

    long cfgLong(String path, long def) {
        return getConfig().getLong(path, def);
    }

    void later(long ticks, Runnable r) {
        if (!isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, r, Math.max(1, ticks));
    }

    /** Mise a un jeu (compte dans les statistiques). */
    boolean take(Player p, long n) {
        PData d = data(p);
        if (n <= 0 || d.chips < n) {
            return false;
        }
        d.chips -= n;
        d.wagered += n;
        dirty = true;
        return true;
    }

    /** Retrait sans statistique (cave au poker, achat a la boutique). */
    boolean withdraw(Player p, long n) {
        PData d = data(p);
        if (n <= 0 || d.chips < n) {
            return false;
        }
        d.chips -= n;
        dirty = true;
        return true;
    }

    void deposit(UUID id, long n) {
        if (n <= 0) {
            return;
        }
        players.computeIfAbsent(id, PData::new).chips += n;
        dirty = true;
    }

    void refund(UUID id, long n) {
        if (n <= 0) {
            return;
        }
        PData d = players.computeIfAbsent(id, PData::new);
        d.chips += n;
        d.wagered = Math.max(0, d.wagered - n);
        dirty = true;
    }

    /** Gain : gross = jetons rendus (mise comprise), stake = mise de la partie. */
    void win(Player p, long gross, long stake, String game) {
        PData d = data(p);
        d.chips += gross;
        d.won += gross;
        long net = gross - stake;
        if (net > d.biggest) {
            d.biggest = net;
        }
        dirty = true;
        if (net >= cfgLong("broadcast-min", 5000)) {
            broadcast("&e" + p.getName() + " &7gagne &6&l" + U.fmt(net) + " jetons &7a la " + game + " !");
            bigWins.add("&e" + p.getName() + " &7+&6" + U.fmt(net) + " &8(" + game + ")");
            while (bigWins.size() > 20) {
                bigWins.remove(0);
            }
            updateWinsBoard();
        }
        try {
            offerBigWin(p, net, game);
        } catch (RuntimeException ex) {
            // un souci d'affichage ne doit jamais interrompre le paiement (deja fait ci-dessus)
            getLogger().warning("Classement des plus gros gains : " + ex);
        }
    }

    void broadcast(String m) {
        Bukkit.broadcast(U.c(U.P + m));
    }

    void celebrate(Player p, boolean big) {
        if (!p.isOnline()) {
            return;
        }
        Location l = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l, big ? 120 : 40, 0.7, 0.9, 0.7, 0.35);
        p.getWorld().playSound(l, big ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        if (big) {
            p.getWorld().playSound(l, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
        }
    }

    /** Mise maximale d'un jeu : meme valeur par defaut partout (le config du serveur peut ne pas avoir la cle). */
    long maxBet(String game) {
        long def = switch (game) {
            case "slots", "videopoker", "roue", "plinko" -> 5000;
            default -> 10000;
        };
        return cfgLong("max-bet." + game, def);
    }

    List<Long> betValues(String game) {
        long max = maxBet(game);
        List<Long> out = new ArrayList<>();
        for (Long v : getConfig().getLongList("bets")) {
            if (v > 0 && v <= max) {
                out.add(v);
            }
        }
        if (out.isEmpty()) {
            out.add(10L);
        }
        return out;
    }

    long currentBet(Player p, String game) {
        List<Long> vals = betValues(game);
        long b = data(p).bet(game, vals.get(0));
        long best = vals.get(0);
        for (long v : vals) {
            if (v <= b) {
                best = v;
            }
        }
        return best;
    }

    void betBar(Gui g, Player p, String game, int[] slotsIdx, Runnable refresh) {
        List<Long> vals = betValues(game);
        long cur = currentBet(p, game);
        long chips = data(p).chips;
        for (int k = 0; k < slotsIdx.length && k < vals.size(); k++) {
            long v = vals.get(k);
            boolean sel = v == cur;
            g.set(slotsIdx[k], U.item(U.chip(v), (sel ? "&a▶ " : "&e") + U.fmt(v) + " jetons", List.of(sel ? "&aMise choisie" : chips >= v ? "&7Clic pour miser " + U.fmt(v) : "&8Tu n'as pas assez de jetons"), sel), t -> {
                data(p).bets.put(game, v);
                dirty = true;
                p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.5f, 1.8f);
                refresh.run();
            });
        }
    }

    /** Classement recalcule sur le fil principal (lu par les placeholders, parfois hors fil principal). */
    private void computeTop() {
        List<PData> l = new ArrayList<>(players.values());
        l.sort((a, b) -> Long.compare(b.chips, a.chips));
        Map<UUID, Integer> r = new HashMap<>();
        for (int i = 0; i < l.size(); i++) {
            r.put(l.get(i).uuid, i + 1);
        }
        ranks = r;
        l.removeIf(d -> d.chips <= 0 || d.name == null || d.name.equals("?"));
        top = l.size() > 10 ? new ArrayList<>(l.subList(0, 10)) : l;
    }

    List<PData> top() {
        return top;
    }

    int rank(UUID id) {
        Integer r = ranks.get(id);
        return r == null ? ranks.size() + 1 : r;
    }

    PData offline(UUID id) {
        return players.get(id);
    }

    private void loadData() {
        players.clear();
        bigWins.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        jackpot = y.getLong("jackpot", cfgLong("slots.jackpot-seed", 10000));
        buildVersion = y.getInt("build-version", 0);
        bigWins.addAll(y.getStringList("big-wins"));
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(k);
                    ConfigurationSection sec = ps.getConfigurationSection(k);
                    PData d = PData.load(id, sec);
                    // tapis de poker encore aux tables a la derniere sauvegarde = arret brutal : on les rend
                    long onTable = sec.getLong("on-table", 0);
                    if (onTable > 0) {
                        d.chips += onTable;
                        dirty = true;
                        getLogger().warning("Arret brutal : " + onTable + " jetons de poker rendus a " + d.name + ".");
                    }
                    players.put(id, d);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        bigTop.load(y);
        int seeded = bigTop.seedIfNeeded(players.values());
        if (seeded > 0) {
            dirty = true;
            getLogger().info("Plus gros gains : classement amorce avec " + seeded + " record(s) existant(s) (jeu et date inconnus).");
        }
    }

    void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("jackpot", jackpot);
        y.set("build-version", buildVersion);
        y.set("big-wins", bigWins);
        bigTop.save(y);
        Map<UUID, Long> onTables = poker == null ? Map.of() : poker.onTables();
        for (PData d : players.values()) {
            ConfigurationSection sec = y.createSection("players." + d.uuid);
            d.save(sec);
            Long ot = onTables.get(d.uuid);
            if (ot != null && ot > 0) {
                sec.set("on-table", ot);
            }
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            getLogger().warning("Sauvegarde impossible : " + e.getMessage());
        }
    }

    // =========================================================== EemiCore

    private boolean eemiCore() {
        Plugin pl = Bukkit.getPluginManager().getPlugin("EemiCore");
        return pl != null && pl.isEnabled();
    }

    /** Pieces EemiCore du joueur, -1 si illisibles. */
    long coins(Player p) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null || !eemiCore()) {
            return -1;
        }
        try {
            return Long.parseLong(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, "%eemi_coins%").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void sendStatus() {
        if (!eemiCore() || world == null) {
            return;
        }
        int n = world.getPlayers().size();
        String id = getConfig().getString("eemicore-id", "casino");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi status " + id + " playing 0 " + n + " 100");
    }

    // =========================================================== caisse

    void openCashier(Player p) {
        Gui g = new Gui("cashier", 36, "&8Caisse du casino");
        g.fill(Material.BLACK_STAINED_GLASS_PANE);
        PData d = data(p);
        long coins = coins(p);
        g.set(4, U.item(Material.SUNFLOWER, "&6Tes jetons : &e" + U.fmt(d.chips), List.of(coins >= 0 ? "&7Tes pieces : &6" + U.fmt(coins) : "&cPieces illisibles (EemiCore ?)",
                "", "&7Les jetons s'achetent avec des pieces", "&7et ne se reconvertissent pas."), false), null);
        List<Map<?, ?>> packs = getConfig().getMapList("cashier.packs");
        int[] pslots = {11, 12, 13, 14, 15};
        Material[] mats = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.EMERALD_BLOCK};
        for (int k = 0; k < packs.size() && k < pslots.length; k++) {
            Map<?, ?> m = packs.get(k);
            long cost = m.get("coins") instanceof Number n ? n.longValue() : 0;
            long chips = m.get("chips") instanceof Number n ? n.longValue() : 0;
            if (cost <= 0 || chips <= 0) {
                continue;
            }
            boolean ok = coins >= cost;
            long bonus = chips - cost * 10;
            g.set(pslots[k], Icons.icon(U.item(mats[k], "&e&l" + U.fmt(chips) + " jetons", List.of("&7Prix : &6" + U.fmt(cost) + " pieces", bonus > 0 ? "&a+" + U.fmt(bonus) + " jetons offerts !" : "",
                    "", ok ? "&aClic pour acheter" : "&cPas assez de pieces"), bonus > 0), "pack_" + (k + 1)), ok ? t -> buyPack(p, cost, chips) : null);
        }
        String today = LocalDate.now().toString();
        boolean taken = today.equals(d.dailyDate);
        int streak = nextStreak(d);
        long amount = dailyAmount(streak);
        List<String> lore = new ArrayList<>();
        if (taken) {
            LocalDateTime now = LocalDateTime.now();
            long min = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMinutes();
            lore.add("&7Deja recupere aujourd'hui.");
            lore.add("&7Prochain bonus dans &f" + min / 60 + "h" + String.format("%02d", min % 60));
            lore.add("&7Serie actuelle : &e" + d.streak + " jour(s)");
        } else {
            lore.add("&7Bonus : &e" + U.fmt(amount) + " jetons");
            lore.add("&7Serie : &e" + streak + " jour(s) &8(max " + cfgLong("cashier.daily-max-streak", 7) + ")");
            lore.add("&7Reviens chaque jour pour l'augmenter !");
            lore.add("");
            lore.add("&aClic pour recuperer");
        }
        g.set(22, U.item(taken ? Material.MINECART : Material.CHEST_MINECART, taken ? "&7Bonus du jour" : "&a&lBONUS DU JOUR", lore, !taken), taken ? null : t -> claimDaily(p));
        g.set(31, U.item(Material.ARROW, "&eMenu du casino", List.of(), false), t -> openMain(p));
        p.openInventory(g.inv);
    }

    private void buyPack(Player p, long cost, long chips) {
        long before = coins(p);
        if (before < cost) {
            U.msg(p, "&cIl te faut " + U.fmt(cost) + " pieces.");
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi give " + p.getName() + " coins -" + cost);
        long after = coins(p);
        if (after < 0 || after > before - cost) {
            U.msg(p, "&cL'achat n'a pas pu etre fait (pieces non debitees).");
            getLogger().warning("Achat de jetons refuse pour " + p.getName() + " : pieces " + before + " -> " + after);
            return;
        }
        deposit(p.getUniqueId(), chips);
        getLogger().info(p.getName() + " achete " + chips + " jetons pour " + cost + " pieces.");
        U.msg(p, "&a+" + U.fmt(chips) + " jetons &7(-" + U.fmt(cost) + " pieces). Bonne chance !");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        dealerSwing(DEALER_CASHIER, p);
        dealerSay(DEALER_CASHIER, "&eMerci " + p.getName() + " !");
        openCashier(p);
    }

    private int nextStreak(PData d) {
        String yesterday = LocalDate.now().minusDays(1).toString();
        int max = (int) cfgLong("cashier.daily-max-streak", 7);
        return yesterday.equals(d.dailyDate) ? Math.min(d.streak + 1, max) : 1;
    }

    private long dailyAmount(int streak) {
        return cfgLong("cashier.daily-base", 200) + cfgLong("cashier.daily-per-day", 100) * (streak - 1);
    }

    void claimDaily(Player p) {
        PData d = data(p);
        String today = LocalDate.now().toString();
        if (today.equals(d.dailyDate)) {
            U.msg(p, "&7Tu as deja pris ton bonus aujourd'hui. Reviens demain !");
            return;
        }
        int streak = nextStreak(d);
        long amount = dailyAmount(streak);
        d.streak = streak;
        d.dailyDate = today;
        d.chips += amount;
        dirty = true;
        U.msg(p, "&a&lBonus du jour : &e+" + U.fmt(amount) + " jetons &7(serie de " + streak + " jour" + (streak > 1 ? "s" : "") + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        celebrate(p, false);
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Gui g && g.kind.equals("cashier")) {
            openCashier(p);
        }
    }

    boolean dailyAvailable(PData d) {
        return !LocalDate.now().toString().equals(d.dailyDate);
    }

    // =========================================================== boutique

    void openShop(Player p) {
        Gui g = new Gui("shop", 45, "&8Boutique du casino");
        g.fill(Material.PURPLE_STAINED_GLASS_PANE);
        PData d = data(p);
        g.set(4, U.item(Material.SUNFLOWER, "&6Tes jetons : &e" + U.fmt(d.chips), List.of("&7Cosmetiques reserves au casino :", "&7on ne peut les avoir qu'ici.", "&7Ils s'equipent ensuite dans &f/boutique&7."), false), null);
        ConfigurationSection sec = getConfig().getConfigurationSection("shop");
        int[] sl = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int k = 0;
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                if (k >= sl.length) {
                    break;
                }
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                long price = s.getLong("price");
                boolean owned = d.owned.contains(id);
                Material m = Material.matchMaterial(s.getString("icon", "PAPER"));
                List<String> lore = new ArrayList<>(s.getStringList("lore"));
                lore.add("");
                lore.add(owned ? "&aDeja achete &7- equipe-le dans /boutique" : (d.chips >= price ? "&6" : "&c") + "Prix : " + U.fmt(price) + " jetons");
                g.set(sl[k++], Icons.vanilla(U.item(m == null || !m.isItem() ? Material.PAPER : m, s.getString("name", id), lore, owned)), owned ? null : t -> buy(p, id, s.getString("name", id), price));
            }
        }
        g.set(40, U.item(Material.ARROW, "&eMenu du casino", List.of(), false), t -> openMain(p));
        p.openInventory(g.inv);
    }

    private boolean coreSupportsCosmetics() {
        Plugin pl = Bukkit.getPluginManager().getPlugin("EemiCore");
        if (pl == null || !pl.isEnabled()) {
            return false;
        }
        String[] v = pl.getPluginMeta().getVersion().split("\\.");
        try {
            int a = Integer.parseInt(v[0]);
            int b = v.length > 1 ? Integer.parseInt(v[1]) : 0;
            int c = v.length > 2 ? Integer.parseInt(v[2].replaceAll("[^0-9].*", "")) : 0;
            return a > 1 || (a == 1 && (b > 5 || (b == 5 && c >= 2)));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void buy(Player p, String id, String name, long price) {
        PData d = data(p);
        if (d.owned.contains(id)) {
            return;
        }
        if (!coreSupportsCosmetics()) {
            U.msg(p, "&cLa boutique ouvre bientot (EemiCore 1.5.2 requis).");
            return;
        }
        Plugin core = Bukkit.getPluginManager().getPlugin("EemiCore");
        ConfigurationSection cs = core == null ? null : core.getConfig().getConfigurationSection("cosmetics." + id);
        if (cs == null || !cs.getBoolean("casino", false) || cs.getString("type") == null) {
            U.msg(p, "&cCet article n'est pas encore disponible.");
            getLogger().warning("Cosmetique " + id + " absent (ou sans casino: true) du config d'EemiCore : achat refuse.");
            return;
        }
        if (!withdraw(p, price)) {
            U.msg(p, "&cIl te manque " + U.fmt(price - d.chips) + " jetons.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        d.owned.add(id);
        dirty = true;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi cosmetic " + p.getName() + " " + id);
        getLogger().info(p.getName() + " achete " + id + " pour " + price + " jetons.");
        U.msg(p, "&aAchete : " + name + " &7(-" + U.fmt(price) + " jetons). &7Il est equipe !");
        celebrate(p, false);
        dealerSwing(DEALER_SHOP, p);
        dealerSay(DEALER_SHOP, "&dExcellent choix !");
        openShop(p);
    }

    // =========================================================== menus

    void openMain(Player p) {
        Gui g = new Gui("main", 45, "&8Casino EEMICRAFT");
        g.fill(Material.BLACK_STAINED_GLASS_PANE);
        PData d = data(p);
        g.set(4, U.head(p.getUniqueId(), "&6" + p.getName(), List.of("&7Jetons : &e" + U.fmt(d.chips), "&7Classement : &f" + rank(p.getUniqueId()) + "e",
                "&7Mises totales : &f" + U.fmt(d.wagered), "&7Gains totaux : &f" + U.fmt(d.won), "&7Plus gros gain : &a" + U.fmt(d.biggest),
                "&7Jackpots : &6" + d.jackpots), false), null);
        boolean in = inCasino(p);
        g.set(10, Icons.icon(U.item(Material.REDSTONE_BLOCK, "&c&lMachines a sous", List.of("&7Aligne 3 symboles.", "&6Jackpot : " + U.fmt(jackpot)), false), "jeu_slots"), t -> {
            if (in) {
                slots.open(p, -1);
            } else {
                join(p);
            }
        });
        g.set(12, Icons.icon(U.item(Material.PAPER, "&a&lBlackjack", List.of("&7Bats le croupier sans depasser 21.", "&7Blackjack paye 3 pour 2."), false), "jeu_blackjack"), t -> {
            if (in) {
                blackjack.open(p, -1);
            } else {
                join(p);
            }
        });
        g.set(14, Icons.icon(U.item(Material.ENDER_PEARL, "&c&lRoulette", List.of("&7Rouge, noir, numero plein (x36)..."), false), "jeu_roulette"), t -> {
            if (in) {
                roulette.open(p, false);
            } else {
                join(p);
            }
        });
        g.set(16, Icons.icon(U.item(Material.BLUE_STAINED_GLASS, "&9&lVideo poker", List.of("&7Jacks or Better : fais la meilleure", "&7main de 5 cartes."), false), "jeu_videopoker"), t -> {
            if (in) {
                videoPoker.open(p);
            } else {
                join(p);
            }
        });
        g.set(20, Icons.icon(U.item(Material.CLOCK, "&6&lRoue de la fortune", List.of("&7Mise une somme, la roue la multiplie", "&7de x0 a x10 !"), false), "jeu_roue"), t -> {
            if (in) {
                fortune.open(p, false);
            } else {
                join(p);
            }
        });
        g.set(24, U.item(Material.HEAVY_CORE, "&d&lPlinko", List.of("&7Lache des billes a travers les picots.", "&7Jusqu'a x5,6 ta mise !"), false), t -> {
            if (in) {
                plinko.open(p);
            } else {
                join(p);
            }
        });
        List<String> pk = new ArrayList<>();
        for (Poker.Table t : poker.tables) {
            pk.add("&7" + t.name + " : &f" + t.players() + "/6 &8(" + U.fmt(t.sb) + "/" + U.fmt(t.bb) + ")");
        }
        pk.add("");
        pk.add("&7Joue contre les autres joueurs !");
        g.set(22, U.item(Material.TNT_MINECART, "&b&lTexas Hold'em", pk, false), t -> {
            if (in) {
                openPokerList(p);
            } else {
                join(p);
            }
        });
        g.set(28, U.item(Material.GOLD_BLOCK, "&6&lCaisse", List.of("&7Acheter des jetons avec tes pieces."), false), t -> openCashier(p));
        g.set(30, U.item(dailyAvailable(d) ? Material.CHEST_MINECART : Material.MINECART, "&a&lBonus du jour", List.of(dailyAvailable(d) ? "&aDisponible !" : "&7Deja recupere"), dailyAvailable(d)), t -> claimDaily(p));
        g.set(32, U.item(Material.AMETHYST_SHARD, "&d&lBoutique du casino", List.of("&7Titres et cosmetiques exclusifs."), false), t -> openShop(p));
        g.set(34, U.item(Material.GOLDEN_HELMET, "&e&lLes plus riches", List.of("&7Classement des jetons."), false), t -> openTop(p));
        BigWins.Entry rec = bigTop.get(1);
        int myBig = bigTop.rankOf(p.getUniqueId());
        g.set(42, U.item(Material.NETHER_STAR, "&6&lPlus gros gains", List.of("&7Top 10 des plus gros gains", "&7en un seul coup, tous jeux confondus.",
                rec == null ? "&7Aucun record pour l'instant." : "&7Record : &e" + rec.name() + " &6" + U.fmt(rec.amount()),
                myBig > 0 ? "&aTu es " + myBig + (myBig == 1 ? "er" : "e") + " !" : "&7Ton meilleur coup : &a" + U.fmt(d.biggest)), false), t -> openBigWins(p));
        if (inCasino(p)) {
            g.set(40, U.item(Material.OAK_DOOR, "&cQuitter le casino", List.of("&7Retour au lobby"), false), t -> leave(p));
        } else {
            g.set(40, U.item(Material.ENDER_EYE, "&aAller au casino", List.of(), false), t -> join(p));
        }
        p.openInventory(g.inv);
    }

    void openPokerList(Player p) {
        Poker.Table mine = poker.tableOf(p.getUniqueId());
        if (mine != null) {
            poker.click(p, mine.id);
            return;
        }
        Gui g = new Gui("pokerlist", 27, "&8Texas Hold'em");
        g.fill(Material.GREEN_STAINED_GLASS_PANE);
        int[] sl = {11, 15, 13};
        for (int i = 0; i < poker.tables.size() && i < sl.length; i++) {
            Poker.Table t = poker.tables.get(i);
            final int idx = i;
            g.set(sl[i], U.item(Material.TNT_MINECART, "&b&l" + t.name, List.of("&7Blindes : &f" + U.fmt(t.sb) + "/" + U.fmt(t.bb), "&7Joueurs : &f" + t.players() + "/6",
                    "&7Cave : &f" + U.fmt(t.minBuy) + " a " + U.fmt(t.maxBuy), "", "&aClic pour t'asseoir"), false), c -> poker.click(p, idx));
        }
        g.set(22, U.item(Material.ARROW, "&eMenu du casino", List.of(), false), t -> openMain(p));
        p.openInventory(g.inv);
    }

    void openTop(Player p) {
        computeTop();
        Gui g = new Gui("top", 36, "&8Les plus riches du casino");
        g.fill(Material.BLACK_STAINED_GLASS_PANE);
        int[] sl = {4, 12, 14, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < top.size() && i < sl.length; i++) {
            PData d = top.get(i);
            g.set(sl[i], U.head(d.uuid, (i == 0 ? "&6&l" : "&e") + (i + 1) + ". " + d.name, List.of("&7Jetons : &e" + U.fmt(d.chips), "&7Plus gros gain : &a" + U.fmt(d.biggest), "&7Jackpots : &6" + d.jackpots), i == 0), null);
        }
        g.set(31, U.item(Material.ARROW, "&eMenu du casino", List.of("&7Ta place : &f" + rank(p.getUniqueId()) + "e"), false), t -> openMain(p));
        p.openInventory(g.inv);
    }

    /** Top 10 des plus gros gains en un coup (meme fond que "Les plus riches" : 10 cases + retour). */
    void openBigWins(Player p) {
        Gui g = new Gui("top", 36, "&8Plus gros gains en un coup");
        g.fill(Material.BLACK_STAINED_GLASS_PANE);
        int[] sl = {4, 12, 14, 19, 20, 21, 22, 23, 24, 25};
        List<BigWins.Entry> l = bigTop.list();
        for (int i = 0; i < l.size() && i < sl.length; i++) {
            BigWins.Entry e = l.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7Gain : &6&l" + U.fmt(e.amount()) + " jetons");
            lore.add("&7Jeu : &f" + BigWins.gameName(e.game()));
            String date = BigWins.date(e.time());
            if (!date.isEmpty()) {
                lore.add("&7Le &f" + date);
            }
            if (e.uuid().equals(p.getUniqueId())) {
                lore.add("");
                lore.add("&aC'est toi !");
            }
            g.set(sl[i], U.head(e.uuid(), rankColor(i) + (i + 1) + ". " + e.name(), lore, i == 0), null);
        }
        int mine = bigTop.rankOf(p.getUniqueId());
        PData d = data(p);
        List<String> back = new ArrayList<>();
        if (mine > 0) {
            back.add("&7Ta place : &a" + mine + (mine == 1 ? "re" : "e"));
        } else {
            back.add("&7Ton meilleur coup : &a" + U.fmt(d.biggest) + " jetons");
            BigWins.Entry last = l.size() >= BigWins.SIZE ? l.get(l.size() - 1) : null;
            back.add(last == null ? "&7Une place est libre : gagne un coup pour entrer !" : "&7Pour entrer : plus de &6" + U.fmt(last.amount()) + " jetons");
        }
        if (l.isEmpty()) {
            back.add("&7Personne pour l'instant : à toi de jouer !");
        }
        g.set(31, U.item(Material.ARROW, "&eMenu du casino", back, false), t -> openMain(p));
        p.openInventory(g.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui g)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != g.inv || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
            return;
        }
        var a = g.actions.get(e.getRawSlot());
        if (a == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = menuClick.get(p.getUniqueId());
        if (last != null && now - last < 200) {
            return;
        }
        menuClick.put(p.getUniqueId(), now);
        var click = e.getClick();
        // action au tick suivant (ouvrir un menu pendant un clic est deconseille)
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != g) {
                return;
            }
            try {
                a.accept(click);
            } catch (RuntimeException ex) {
                getLogger().warning("Erreur dans le menu " + g.kind + " : " + ex);
                ex.printStackTrace();
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g && g.onClose != null) {
            g.onClose.run();
        }
    }

    // =========================================================== entree / sortie

    void join(Player p) {
        if (world == null) {
            U.msg(p, "&cLe casino est ferme.");
            return;
        }
        if (inCasino(p)) {
            return;
        }
        List<String> from = getConfig().getStringList("join-from");
        if (!from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemicasino.admin")) {
            U.msg(p, "&cOn entre au casino depuis le lobby (&f/lobby&c).");
            return;
        }
        p.teleport(spawn());
    }

    void leave(Player p) {
        p.closeInventory();
        String cmd = getConfig().getString("lobby-command", "lobby");
        if (!p.performCommand(cmd)) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    private void enter(Player p) {
        if (!(p.isOp() && p.getGameMode() == GameMode.CREATIVE)) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        p.setFoodLevel(20);
        p.setAllowFlight(false);
        PData d = data(p);
        p.showTitle(Title.title(U.c("&6&l★ CASINO ★"), U.c("&eFaites vos jeux !"), Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        if (!d.welcomed) {
            d.welcomed = true;
            long w = cfgLong("cashier.welcome-chips", 1000);
            d.chips += w;
            dirty = true;
            U.msg(p, "&a&lBienvenue au casino ! &eOn t'offre " + U.fmt(w) + " jetons pour commencer.");
        }
        U.msg(p, "&7Tes jetons : &e" + U.fmt(d.chips) + (dailyAvailable(d) ? " &7- &a&lton bonus du jour t'attend a la caisse !" : "") + " &7(&f/casino &7pour le menu)");
        later(5, this::sendStatus);
    }

    private void leaveGames(Player p) {
        // chaque jeu protege des autres : une erreur dans l'un ne saute pas le reglement des suivants
        Runnable[] quits = {() -> slots.quit(p), () -> blackjack.quit(p), () -> roulette.quit(p), () -> videoPoker.quit(p),
                () -> poker.standUp(p.getUniqueId(), null), () -> fortune.quit(p), () -> plinko.quit(p)};
        for (Runnable r : quits) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                getLogger().warning("Sortie d'un jeu pour " + p.getName() + " : " + ex);
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (world == null) {
            return;
        }
        if (e.getFrom().equals(world)) {
            leaveGames(p);
            later(5, this::sendStatus);
        }
        if (p.getWorld().equals(world)) {
            enter(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        data(p);
        if (inCasino(p)) {
            later(10, () -> {
                if (p.isOnline() && inCasino(p)) {
                    enter(p);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        leaveGames(p);
        lastClick.remove(p.getUniqueId());
        later(5, this::sendStatus);
    }

    // =========================================================== postes

    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < 250) {
            return true;
        }
        lastClick.put(p.getUniqueId(), now);
        return false;
    }

    private void use(Player p, Station st, boolean attack) {
        if (debounce(p)) {
            return;
        }
        switch (st.kind()) {
            case WHEEL -> fortune.open(p, true);
            case PLINKO -> {
                // clic droit = lacher une bille avec la mise choisie, clic gauche = menu
                if (attack || data(p).chips < currentBet(p, "plinko")) {
                    plinko.open(p);
                } else {
                    plinko.drop(p, 1);
                }
            }
            case SLOT -> slots.open(p, st.index());
            case VP -> videoPoker.open(p, st.index());
            case BJ -> blackjack.open(p, st.index());
            case ROULETTE -> roulette.open(p, true);
            case POKER -> poker.click(p, st.index());
            case CASHIER -> {
                dealerLook(DEALER_CASHIER, p);
                openCashier(p);
            }
            case SHOP -> {
                dealerLook(DEALER_SHOP, p);
                openShop(p);
            }
            case EXIT -> leave(p);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Station st = stations.get(e.getRightClicked().getUniqueId());
        if (st == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) {
            use(e.getPlayer(), st, false);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAttackEntity(PrePlayerAttackEntityEvent e) {
        Station st = stations.get(e.getAttacked().getUniqueId());
        if (st == null) {
            return;
        }
        e.setCancelled(true);
        use(e.getPlayer(), st, true);
    }

    // =========================================================== protections

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

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (world == null || !e.getPlayer().getWorld().equals(world) || builder(e.getPlayer())) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakEvent e) {
        if (e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getY() < Layout.FLOOR - 5 && e.getPlayer().getWorld().equals(world)) {
            e.getPlayer().teleport(spawn());
        }
    }

    // =========================================================== commandes

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return admin(sender, args);
        }
        if (!(sender instanceof Player p)) {
            U.msg(sender, "&c/casino admin ...");
            return true;
        }
        if (label.equalsIgnoreCase("jetons")) {
            PData d = data(p);
            U.msg(p, "&7Tes jetons : &e" + U.fmt(d.chips) + " &7- classement : &f" + rank(p.getUniqueId()) + "e");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (!inCasino(p) && (sub.isEmpty() || sub.equals("join") || sub.equals("aller"))) {
            join(p);
            return true;
        }
        switch (sub) {
            case "", "menu" -> openMain(p);
            case "join", "aller" -> openMain(p);
            case "caisse" -> openCashier(p);
            case "boutique", "shop" -> openShop(p);
            case "bonus", "daily" -> claimDaily(p);
            case "top", "classement" -> openTop(p);
            case "gains", "records", "record" -> openBigWins(p);
            case "poker" -> {
                if (inCasino(p)) {
                    openPokerList(p);
                } else {
                    join(p);
                }
            }
            case "stats" -> {
                PData d = data(p);
                U.msg(p, "&7Jetons &e" + U.fmt(d.chips) + " &7- mises &f" + U.fmt(d.wagered) + " &7- gains &f" + U.fmt(d.won) + " &7- record &a" + U.fmt(d.biggest) + " &7- jackpots &6" + d.jackpots);
            }
            case "quitter", "leave" -> {
                if (inCasino(p)) {
                    leave(p);
                }
            }
            default -> U.msg(p, "&7/casino [menu|caisse|boutique|bonus|top|gains|poker|stats|quitter]");
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("eemicasino.admin")) {
            U.msg(s, "&cPermission refusee.");
            return true;
        }
        String sub = a.length > 1 ? a[1].toLowerCase() : "";
        switch (sub) {
            case "give", "take", "set" -> {
                if (a.length < 4) {
                    U.msg(s, "&c/casino admin " + sub + " <joueur> <jetons>");
                    return true;
                }
                PData d = byName(a[2]);
                if (d == null) {
                    U.msg(s, "&cJoueur inconnu du casino.");
                    return true;
                }
                long n;
                try {
                    n = Long.parseLong(a[3]);
                } catch (NumberFormatException e) {
                    U.msg(s, "&cNombre invalide.");
                    return true;
                }
                d.chips = Math.max(0, sub.equals("give") ? d.chips + n : sub.equals("take") ? d.chips - n : n);
                dirty = true;
                U.msg(s, "&a" + d.name + " : " + U.fmt(d.chips) + " jetons.");
            }
            case "jackpot" -> {
                if (a.length > 2) {
                    try {
                        jackpot = Long.parseLong(a[2]);
                        dirty = true;
                    } catch (NumberFormatException ignored) {
                    }
                }
                updateJackpotBoard();
                U.msg(s, "&6Jackpot : " + U.fmt(jackpot));
            }
            case "build" -> {
                rebuild();
                spawnEntities();
                U.msg(s, "&aSalle reconstruite.");
            }
            case "entities" -> {
                spawnEntities();
                U.msg(s, "&aEntites recreees (" + entities.size() + ").");
            }
            case "reload" -> {
                reloadConfig();
                Icons.on = Models.on(this);
                if (buildVersion != buildSignature()) {
                    rebuild();
                }
                spawnEntities();
                U.msg(s, "&aConfig rechargee.");
            }
            case "bigwins" -> {
                String op = a.length > 2 ? a[2].toLowerCase() : "";
                if (op.equals("remove") && a.length > 3) {
                    BigWins.Entry e = bigTop.remove(a[3]);
                    if (e == null) {
                        U.msg(s, "&c" + a[3] + " n'est pas dans le classement des plus gros gains.");
                    } else {
                        dirty = true;
                        updateBigTopBoard();
                        getLogger().info(s.getName() + " retire " + e.name() + " (" + e.amount() + ") du classement des plus gros gains.");
                        U.msg(s, "&aEntrée retirée : " + e.name() + " (" + U.fmt(e.amount()) + " jetons). Son « plus gros gain » de profil n'est pas modifié.");
                    }
                } else if (op.equals("reset")) {
                    bigTop.reset();
                    dirty = true;
                    updateBigTopBoard();
                    getLogger().info(s.getName() + " remet a zero le classement des plus gros gains.");
                    U.msg(s, "&aClassement des plus gros gains remis à zéro.");
                } else {
                    List<BigWins.Entry> l = bigTop.list();
                    U.msg(s, "&7Plus gros gains (" + l.size() + "/" + BigWins.SIZE + ") : &c/casino admin bigwins remove <pseudo> &7| &creset");
                    for (int i = 0; i < l.size(); i++) {
                        BigWins.Entry e = l.get(i);
                        U.msg(s, "&7" + (i + 1) + ". &f" + e.name() + " &6" + U.fmt(e.amount()) + " &8" + BigWins.gameName(e.game()) + " " + BigWins.date(e.time()));
                    }
                }
            }
            case "save" -> {
                saveData();
                U.msg(s, "&aDonnees sauvegardees.");
            }
            case "info" -> {
                long total = 0;
                for (PData d : players.values()) {
                    total += d.chips;
                }
                U.msg(s, "&7Comptes " + players.size() + ", jetons en circulation " + U.fmt(total) + ", jackpot " + U.fmt(jackpot)
                        + ", entites " + entities.size() + ", croupiers " + countDealers() + "/6, joueurs au casino " + (world == null ? 0 : world.getPlayers().size()));
            }
            default -> U.msg(s, "&c/casino admin <give|take|set> <joueur> <n> | jackpot [n] | bigwins [remove <pseudo>|reset] | build | entities | reload | save | info");
        }
        return true;
    }

    private PData byName(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            return data(p);
        }
        for (PData d : players.values()) {
            if (d.name.equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("menu", "caisse", "boutique", "bonus", "top", "gains", "poker", "stats", "quitter"));
            if (sender.hasPermission("eemicasino.admin")) {
                out.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("eemicasino.admin")) {
            out.addAll(List.of("give", "take", "set", "jackpot", "bigwins", "build", "entities", "reload", "save", "info"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("bigwins")) {
            out.addAll(List.of("remove", "reset"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("bigwins") && args[2].equalsIgnoreCase("remove")
                && sender.hasPermission("eemicasino.admin")) {
            for (BigWins.Entry e : bigTop.list()) {
                out.add(e.name());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }

    Random random() {
        return rnd;
    }
}
