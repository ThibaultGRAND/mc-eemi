package fr.eemicraft.deacoudre;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import fr.eemicraft.deacoudre.build.RecordingSink;

/**
 * De a coudre : chacun son tour, on saute du plongeoir dans la piscine.
 * Dans l'eau = un bloc a sa couleur remplace l'eau. Sur un bloc = -1 vie. Trou d'eau entoure de 4 blocs = +1 vie.
 * Dernier en vie gagne ; piscine pleine = le plus de vies gagne.
 */
public final class EemiDeACoudre extends JavaPlugin implements Listener {
    static final String P = "&b&l[De a coudre] &r";
    static final int BUILD_VERSION = Builder.VERSION;
    /** Surface de l'eau. */
    static final int WY = Builder.WY;
    /** Sol du plongeoir et de la salle d'attente (surface de l'eau + board-height). */
    int by = WY + 40;

    enum State { WAITING, COUNTDOWN, PLAYING, ENDING }

    static final Material[] COLORS = {
        Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
        Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, Material.CYAN_CONCRETE, Material.PINK_CONCRETE,
        Material.WHITE_CONCRETE, Material.BLACK_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
        Material.GREEN_CONCRETE, Material.BROWN_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE};
    static final String[] COLOR_CODES = {"&c", "&9", "&a", "&e", "&6", "&5", "&3", "&d", "&f", "&0", "&d", "&b", "&2", "&6", "&8", "&7"};
    static final String[] COLOR_NAMES = {"rouge", "bleu", "vert", "jaune", "orange", "violet", "cyan", "rose", "blanc", "noir", "magenta", "bleu clair", "vert fonce", "marron", "gris", "gris clair"};

    static final class Racer {
        final UUID id;
        String name;
        int color;
        int lives;
        int jumps;
        int perfects;

        Racer(UUID id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    World world;
    State state = State.WAITING;
    final Map<UUID, Racer> players = new LinkedHashMap<>();
    final List<UUID> spectators = new ArrayList<>();
    /** Joueurs partis en pleine partie : gardes au tableau, plus pilotes par le jeu. */
    final java.util.Set<UUID> gone = new java.util.HashSet<>();
    /** Arrives pendant une partie avec /dac join : ils la regardent et jouent la suivante. */
    final java.util.Set<UUID> nextRound = new java.util.LinkedHashSet<>();
    /** Inscrits qui ont bouge la tete pendant la partie : seuls eux restent en file a la fin (anti-AFK). */
    final java.util.Set<UUID> active = new java.util.HashSet<>();
    /** Cle PDC de l'objet « Retour au lobby ». */
    NamespacedKey leaveKey;
    /** Numero de partie : une tache planifiee d'une autre partie ne fait rien. */
    int gen;
    long jumpedAt;
    final List<UUID> order = new ArrayList<>();
    int turnIndex = -1;
    UUID jumper;
    boolean jumped;
    boolean resolving;
    int jumpLeft;
    int countdown;
    int round;
    BukkitTask ticker;
    Scoreboard board;
    int radius;
    private int buildVersion;

    static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        s.sendMessage(c(P + m));
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
        l.removeAll(gone);
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

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        leaveKey = new NamespacedKey(this, "leave");
        radius = Math.max(3, Math.min(10, getConfig().getInt("pool-radius", 4)));
        by = WY + Math.max(15, Math.min(80, getConfig().getInt("board-height", 40)));
        File f = new File(getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(f);
        buildVersion = data.getInt("build-version", 0);
        if (data.getInt("radius", radius) != radius || data.getInt("board-y", by) != by) {
            // rayon ou hauteur changes dans le config : la piscine est reconstruite
            buildVersion = 0;
        }
        createWorld();
        getServer().getPluginManager().registerEvents(this, this);
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        sendStatus();
    }

    @Override
    public void onDisable() {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (inWorld(p)) {
                    p.setGlowing(false);
                    if (p.getGameMode() == GameMode.SPECTATOR) {
                        p.setGameMode(GameMode.ADVENTURE);
                    }
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
                removeLeaveItem(p);
            }
        }
        players.clear();
        spectators.clear();
        nextRound.clear();
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
        String name = getConfig().getString("world", "deacoudre");
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
        world.setSpawnLocation(waitSpawn());
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
            }
        }
        if (buildVersion != BUILD_VERSION) {
            build();
        } else {
            resetPool();
        }
    }

    private void saveBuild() {
        buildVersion = BUILD_VERSION;
        YamlConfiguration y = new YamlConfiguration();
        y.set("build-version", buildVersion);
        y.set("radius", radius);
        y.set("board-y", by);
        try {
            getDataFolder().mkdirs();
            y.save(new File(getDataFolder(), "data.yml"));
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    Location waitSpawn() {
        return new Location(world, 0.5, by + 1, -(radius + 7) + 0.5, 0f, 10f);
    }

    Location boardSpawn() {
        return new Location(world, 0.5, by + 1, -(radius + 2) + 0.5, 0f, 30f);
    }

    boolean inWorld(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    // ================================================================ construction

    private final Map<String, BlockData> cache = new HashMap<>();
    private int ops;

    private void set(int x, int y, int z, String b) {
        BlockData d = cache.computeIfAbsent(b, k -> {
            try {
                return Bukkit.createBlockData(k);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bloc invalide ignore : " + k);
                return Bukkit.createBlockData("minecraft:air");
            }
        });
        world.getBlockAt(x, y, z).setBlockData(d, false);
        ops++;
    }


    boolean inPool(int x, int z) {
        return x * x + z * z <= radius * radius + radius;
    }

    /** Piscine, crique, tour, plongeoir et belvedere (decor dessine par {@link Builder}). */
    void build() {
        long t0 = System.currentTimeMillis();
        ops = 0;
        radius = Math.max(3, Math.min(10, getConfig().getInt("pool-radius", 4)));
        by = WY + Math.max(15, Math.min(80, getConfig().getInt("board-height", 40)));
        world.setSpawnLocation(waitSpawn());
        // 1. le decor est d'abord dessine en memoire (aucun acces au monde)
        RecordingSink rec = new RecordingSink();
        Builder.build(rec, radius, by);
        // 2. puis pose : tout bloc du decor, et de l'air la ou il reste un bloc d'une ancienne construction
        //    (l'ancienne version allait jusqu'a +-24 et WY+90 ; la zone actuelle couvre +-30 et le belvedere)
        int a = Builder.AREA;
        int yTop = Math.max(WY + 92, by + 24);
        int cleared = 0;
        for (int x = -a; x <= a; x++) {
            for (int z = -a; z <= a; z++) {
                for (int y = WY - 10; y <= yTop; y++) {
                    int id = rec.id(x, y, z);
                    if (id != 0) {
                        set(x, y, z, rec.state(id));
                    } else if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        set(x, y, z, "minecraft:air");
                        cleared++;
                    }
                }
            }
        }
        saveBuild();
        getLogger().info("Piscine construite (decor v" + BUILD_VERSION + ") : rayon " + radius + ", " + countWater() + " cases d'eau, "
                + ops + " blocs poses (" + cleared + " effaces), " + rec.paletteSize() + " etats, en " + (System.currentTimeMillis() - t0) + " ms.");
    }


    /** Remet l'eau dans toute la piscine. */
    void resetPool() {
        if (world == null) {
            return;
        }
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                if (inPool(x, z)) {
                    set(x, WY, z, "minecraft:water");
                    set(x, WY - 1, z, "minecraft:water");
                }
            }
        }
    }

    int countWater() {
        int n = 0;
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                if (inPool(x, z) && world.getBlockAt(x, WY, z).getType() == Material.WATER) {
                    n++;
                }
            }
        }
        return n;
    }

    // ================================================================ deroulement

    private void tick() {
        if (world == null) {
            return;
        }
        int min = Math.max(1, getConfig().getInt("min-players", 2));
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
                    tell("&cPas assez de joueurs, decompte annule.");
                    sendStatus();
                    return;
                }
                countdown--;
                if (countdown <= 0) {
                    start();
                } else if (countdown <= 5 || countdown % 10 == 0) {
                    for (UUID id : players.keySet()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            p.showTitle(Title.title(c("&b" + countdown), c("&7Preparez-vous a plonger"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                        }
                    }
                }
            }
            case PLAYING -> {
                if (jumper != null && jumped && !resolving && System.currentTimeMillis() - jumpedAt > 7000) {
                    // en l'air ou coince depuis trop longtemps (pose sur un rebord, une feuille...)
                    miss(jumper, "Pas tombe dans l'eau !");
                    return;
                }
                if (jumper != null && !jumped && !resolving) {
                    jumpLeft--;
                    Player p = Bukkit.getPlayer(jumper);
                    if (p != null) {
                        p.sendActionBar(c("&eSaute ! &f" + Math.max(0, jumpLeft) + " s"));
                        if (jumpLeft <= 3 && jumpLeft > 0) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
                        }
                    }
                    if (jumpLeft <= 0) {
                        Racer r = players.get(jumper);
                        tell("&e" + (r == null ? "?" : r.name) + " &7a mis trop de temps a sauter.");
                        miss(jumper, "Trop lent !");
                    }
                }
            }
            default -> {
            }
        }
    }

    void start() {
        gen++;
        gone.clear();
        active.clear();
        // le lit « Retour au lobby » ne sert que dans la file : retire pendant la partie (redonne a la fin)
        for (UUID id : players.keySet()) {
            Player lp = Bukkit.getPlayer(id);
            if (lp != null && inWorld(lp)) {
                removeLeaveItem(lp);
            }
        }
        resolving = false;
        jumped = false;
        jumper = null;
        state = State.PLAYING;
        round = 1;
        resetPool();
        order.clear();
        order.addAll(players.keySet());
        Collections.shuffle(order);
        int lives = Math.max(1, getConfig().getInt("start-lives", 1));
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        for (Racer r : players.values()) {
            r.lives = lives;
            r.jumps = 0;
            r.perfects = 0;
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(board);
            }
        }
        for (Racer r : players.values()) {
            eemi("eemi reward " + r.name + " " + coreId() + " play");
        }
        tell("&b&lC'est parti ! &7Chacun son tour, saute dans l'eau sans toucher les blocs.");
        tell("&7Trou d'eau entoure de 4 blocs = &6&lDE A COUDRE &7(+1 vie).");
        turnIndex = -1;
        sendStatus();
        updateBoard();
        nextTurn();
    }

    void nextTurn() {
        if (state != State.PLAYING) {
            return;
        }
        resolving = false;
        jumped = false;
        unglow(jumper);
        jumper = null;
        int alive = alive();
        int min = Math.max(1, getConfig().getInt("min-players", 2));
        if (alive == 0 || (alive == 1 && order.size() >= 2 && min >= 2) || countWater() == 0) {
            end();
            return;
        }
        for (int k = 0; k < order.size(); k++) {
            turnIndex++;
            if (turnIndex >= order.size()) {
                turnIndex = 0;
                round++;
                tell("&7--- Tour &b" + round + " &7--- &8(" + countWater() + " cases d'eau)");
            }
            Racer r = players.get(order.get(turnIndex));
            if (r != null && r.lives > 0 && Bukkit.getPlayer(r.id) != null) {
                jumper = r.id;
                break;
            }
        }
        if (jumper == null) {
            end();
            return;
        }
        Racer r = players.get(jumper);
        Player p = Bukkit.getPlayer(jumper);
        jumpLeft = Math.max(5, getConfig().getInt("jump-time", 15));
        p.teleport(boardSpawn());
        p.setGameMode(GameMode.ADVENTURE);
        // contour lumineux : les autres le suivent pendant toute sa chute, meme a travers les blocs
        p.setGlowing(true);
        p.showTitle(Title.title(c(COLOR_CODES[r.color] + "&lA toi !"), c("&7Saute du plongeoir"), Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofMillis(300))));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        tell("&7Au tour de " + COLOR_CODES[r.color] + r.name + " &7(" + hearts(r.lives) + "&7)");
        updateBoard();
    }

    void unglow(UUID id) {
        Player p = id == null ? null : Bukkit.getPlayer(id);
        if (p != null) {
            p.setGlowing(false);
        }
    }

    int alive() {
        int n = 0;
        for (Racer r : players.values()) {
            if (r.lives > 0) {
                n++;
            }
        }
        return n;
    }

    static String hearts(int n) {
        return "&c" + "❤".repeat(Math.max(0, n));
    }

    /** Saut reussi dans l'eau en (x, z). */
    void land(Player p, int x, int z) {
        Racer r = players.get(p.getUniqueId());
        if (r == null || resolving) {
            return;
        }
        resolving = true;
        r.jumps++;
        boolean perfect = isSolid(x + 1, z) && isSolid(x - 1, z) && isSolid(x, z + 1) && isSolid(x, z - 1);
        p.teleport(waitSpawn());
        Block b = world.getBlockAt(x, WY, z);
        b.setType(perfect ? Material.EMERALD_BLOCK : COLORS[r.color], false);
        world.spawnParticle(Particle.SPLASH, x + 0.5, WY + 1, z + 0.5, 60, 0.5, 0.3, 0.5, 0.1);
        world.playSound(new Location(world, x + 0.5, WY + 1, z + 0.5), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 1f);
        if (perfect) {
            int max = Math.max(1, getConfig().getInt("max-lives", 5));
            r.perfects++;
            boolean gain = r.lives < max;
            r.lives = Math.min(max, r.lives + 1);
            tell("&6&l★ DE A COUDRE ! ★ " + COLOR_CODES[r.color] + r.name + (gain ? " &7gagne une vie (" : " &7est deja au maximum (") + hearts(r.lives) + "&7)");
            p.showTitle(Title.title(c("&6&lDE A COUDRE !"), c("&a+1 vie"), Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, x + 0.5, WY + 1.5, z + 0.5, 80, 0.4, 0.8, 0.4, 0.3);
        } else {
            tell(COLOR_CODES[r.color] + r.name + " &aplonge dans l'eau !");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        }
        updateBoard();
        later(30L, this::nextTurn);
    }

    boolean isSolid(int x, int z) {
        return world.getBlockAt(x, WY, z).getType() != Material.WATER;
    }

    /** Saut rate (bloc, plage, trop lent). */
    void miss(UUID id, String why) {
        Racer r = players.get(id);
        if (r == null || resolving) {
            return;
        }
        resolving = true;
        r.lives--;
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.teleport(waitSpawn());
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
        }
        if (r.lives <= 0) {
            tell(COLOR_CODES[r.color] + r.name + " &cest elimine ! &8(" + why + ")");
            if (p != null) {
                p.showTitle(Title.title(c("&c&lELIMINE"), c("&7" + why), Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(500))));
                p.setGameMode(GameMode.SPECTATOR);
            }
        } else {
            tell(COLOR_CODES[r.color] + r.name + " &c" + why + " &7(" + hearts(r.lives) + "&7)");
            if (p != null) {
                p.showTitle(Title.title(c("&c&lRATE !"), c("&7" + why + " - il te reste " + r.lives + " vie" + (r.lives > 1 ? "s" : "")), Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
        }
        updateBoard();
        later(30L, this::nextTurn);
    }

    void end() {
        if (state != State.PLAYING) {
            return;
        }
        state = State.ENDING;
        jumper = null;
        int best = 0;
        for (Racer r : players.values()) {
            best = Math.max(best, r.lives);
        }
        List<Racer> winners = new ArrayList<>();
        if (best > 0) {
            for (Racer r : players.values()) {
                if (r.lives == best) {
                    winners.add(r);
                }
            }
        }
        boolean full = countWater() == 0;
        if (winners.isEmpty()) {
            tell("&7Personne ne gagne cette fois.");
        } else {
            StringBuilder names = new StringBuilder();
            for (Racer r : winners) {
                if (names.length() > 0) {
                    names.append("&7, ");
                }
                names.append(COLOR_CODES[r.color]).append(r.name);
                eemi("eemi reward " + r.name + " " + coreId() + " win");
            }
            String text = (full ? "&7Piscine pleine ! " : "") + "&6&lVictoire de " + names + " &6&l!";
            tell(text);
            Bukkit.broadcast(c(P + names + " &7gagne une partie de De a coudre !"));
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Racer r = players.get(id);
            boolean won = r != null && winners.contains(r);
            p.showTitle(Title.title(c(won ? "&6&lVICTOIRE !" : "&7Fin de partie"), c(winners.isEmpty() ? "" : "&e" + winners.get(0).name + (winners.size() > 1 ? " et " + (winners.size() - 1) + " autre(s)" : "")), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
            if (won) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.3);
            }
        }
        sendStatus();
        later(120L, () -> finish(true));
    }

    /**
     * Fin, piscine remise a neuf. Fin normale ({@code natural}) : les joueurs de la partie encore la (vivants et
     * elimines) et ceux qui ont demande a jouer pendant la partie restent inscrits et retournent dans la salle
     * d'attente (file de la partie suivante) ; les simples spectateurs rentrent au lobby. Arret admin : tout le
     * monde au lobby, comme avant.
     */
    void finish(boolean natural) {
        gen++;
        unglow(jumper);
        int max = getConfig().getInt("max-players", 16);
        Map<UUID, Racer> stay = new LinkedHashMap<>();
        if (natural) {
            for (Racer r : players.values()) {
                Player p = Bukkit.getPlayer(r.id);
                if (!gone.contains(r.id) && p != null && inWorld(p) && stay.size() < max) {
                    // anti-AFK retire (Esteban, 17/11) : tous les joueurs de la partie restent en file
                    stay.put(r.id, r);
                }
            }
            for (UUID id : nextRound) {
                Player p = Bukkit.getPlayer(id);
                if (spectators.contains(id) && !stay.containsKey(id) && p != null && inWorld(p)) {
                    if (stay.size() < max) {
                        stay.put(id, null);
                    } else {
                        msg(p, "&cLa partie suivante est pleine : retour au lobby.");
                    }
                }
            }
        }
        active.clear();
        List<UUID> everyone = all();
        // on vide d'abord : le changement de monde qui suit ne repasse pas par leave()
        players.clear();
        spectators.clear();
        gone.clear();
        order.clear();
        nextRound.clear();
        jumper = null;
        for (UUID id : everyone) {
            if (stay.containsKey(id)) {
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null && inWorld(p)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                p.setGlowing(false);
                removeLeaveItem(p);
                toLobby(p);
            }
        }
        board = null;
        state = State.WAITING;
        for (Map.Entry<UUID, Racer> e : stay.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                continue;
            }
            Racer r = e.getValue();
            if (r == null) {
                r = new Racer(p.getUniqueId(), p.getName(), freeColor());
            }
            r.name = p.getName();
            players.put(r.id, r);
            backToQueue(p);
        }
        // les joueurs sont dans la salle d'attente (au-dessus du plongeoir) quand l'eau est remise
        resetPool();
        sendStatus();
    }

    /** Remet un joueur de la partie finie dans la file : salle d'attente, etat d'attente, objet « Retour au lobby ». */
    void backToQueue(Player p) {
        p.setGlowing(false);
        // teleporter AVANT de toucher a l'inventaire
        p.teleport(waitSpawn());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        org.bukkit.attribute.AttributeInstance hp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20.0 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0);
        for (org.bukkit.potion.PotionEffect fx : p.getActivePotionEffects()) {
            p.removePotionEffect(fx.getType());
        }
        giveLeaveItem(p);
        msg(p, "&7Tu restes dans la file du &bDé à coudre&7. Clique sur le lit (ou &f/dac leave&7) pour revenir au lobby.");
    }

    // ================================================================ objet « Retour au lobby »

    ItemStack leaveItem() {
        ItemStack it = new ItemStack(Material.RED_BED);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c("&cRetour au lobby &7(clic droit)"));
        meta.lore(List.of(c("&7Quitte la file du Dé à coudre")));
        meta.getPersistentDataContainer().set(leaveKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    boolean isLeaveItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(leaveKey, PersistentDataType.BYTE);
    }

    /**
     * Le jeu ne vide pas l'inventaire (le joueur garde ses objets du lobby, bloques ici) : le lit va en case 8
     * seulement si elle est libre, sinon dans la premiere case libre ; jamais par-dessus un objet du joueur.
     */
    void giveLeaveItem(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLeaveItem(inv.getItem(i))) {
                return;
            }
        }
        ItemStack s8 = inv.getItem(8);
        if (s8 == null || s8.getType().isAir()) {
            inv.setItem(8, leaveItem());
            return;
        }
        int free = inv.firstEmpty();
        if (free >= 0) {
            inv.setItem(free, leaveItem());
        }
    }

    void removeLeaveItem(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLeaveItem(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (isLeaveItem(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
        }
    }

    void updateBoard() {
        if (board == null) {
            return;
        }
        Objective o = board.getObjective("dac");
        if (o != null) {
            o.unregister();
        }
        o = board.registerNewObjective("dac", Criteria.DUMMY, c("&b&lDE A COUDRE"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        int line = 15;
        o.getScore(legacy("&7Tour " + round + " &8- &3" + countWater() + " eau")).setScore(line--);
        Racer j = jumper == null ? null : players.get(jumper);
        o.getScore(legacy("&fSaute : " + (j == null ? "&8-" : COLOR_CODES[j.color] + j.name))).setScore(line--);
        o.getScore(legacy(" ")).setScore(line--);
        for (Racer r : players.values()) {
            if (line <= 0) {
                break;
            }
            String s = r.lives > 0 ? COLOR_CODES[r.color] + r.name + " " + hearts(r.lives) : "&8&m" + r.name;
            o.getScore(legacy(s)).setScore(line--);
        }
    }

    static String legacy(String s) {
        return s.replace('&', '§');
    }

    // ================================================================ entree / sortie

    String coreId() {
        return getConfig().getString("eemicore-id", "deacoudre");
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
        int waiting = state == State.PLAYING ? 0 : players.size();
        int inGame = state == State.PLAYING ? alive() : 0;
        eemi("eemi status " + coreId() + " " + st + " " + waiting + " " + inGame + " " + getConfig().getInt("max-players", 16));
    }

    void join(Player p) {
        if (world == null) {
            msg(p, "&cLe jeu est indisponible.");
            return;
        }
        if (!canEnter(p)) {
            return;
        }
        Racer old = players.get(p.getUniqueId());
        if (old != null && state == State.PLAYING && old.lives <= 0) {
            // revenu apres avoir abandonne : il regarde la fin de la partie
            gone.remove(p.getUniqueId());
            spectate(p);
            return;
        }
        if (spectators.contains(p.getUniqueId()) && (state == State.WAITING || state == State.COUNTDOWN)) {
            // spectateur qui veut jouer pendant l'attente
            spectators.remove(p.getUniqueId());
            nextRound.remove(p.getUniqueId());
            old = null;
        } else if (spectators.contains(p.getUniqueId()) && old == null && nextRound.add(p.getUniqueId())) {
            // spectateur qui demande a jouer pendant la partie : il jouera la suivante
            msg(p, "&7Tu joueras a la partie suivante.");
            return;
        } else if (old != null || spectators.contains(p.getUniqueId())) {
            msg(p, "&7Tu es deja dans la partie. &f/dac leave &7pour sortir.");
            return;
        }
        if (state == State.PLAYING || state == State.ENDING) {
            msg(p, "&7Une partie est en cours : tu la regardes, tu joueras a la suivante.");
            nextRound.add(p.getUniqueId());
            spectate(p);
            return;
        }
        if (players.size() >= getConfig().getInt("max-players", 16)) {
            msg(p, "&cLa partie est pleine.");
            return;
        }
        int color = freeColor();
        Racer r = new Racer(p.getUniqueId(), p.getName(), color);
        players.put(p.getUniqueId(), r);
        p.teleport(waitSpawn());
        p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setAllowFlight(false);
        // le joueur arrive du lobby : on laisse a Multiverse-Inventories le temps du changement de monde, puis on
        // revérifie qu'il est toujours inscrit et dans le monde du jeu avant de donner le lit
        UUID jid = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inWorld(p) && players.containsKey(jid) && (state == State.WAITING || state == State.COUNTDOWN)) {
                giveLeaveItem(p);
            }
        }, 2L);
        tell(COLOR_CODES[color] + p.getName() + " &7rejoint la partie (&f" + players.size() + "&7/&f" + getConfig().getInt("max-players", 16) + "&7) - couleur " + COLOR_CODES[color] + COLOR_NAMES[color]);
        int min = getConfig().getInt("min-players", 2);
        if (players.size() < min) {
            msg(p, "&7En attente de &f" + (min - players.size()) + " &7joueur(s)...");
        }
        sendStatus();
    }

    /** On n'entre que depuis le lobby (ou le monde du jeu). */
    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!inWorld(p) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemideacoudre.admin")) {
            msg(p, "&cOn rejoint le De a coudre depuis le lobby (&f/lobby&c).");
            return false;
        }
        return true;
    }

    int freeColor() {
        boolean[] used = new boolean[COLORS.length];
        for (Racer r : players.values()) {
            used[r.color] = true;
        }
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return players.size() % COLORS.length;
    }

    void spectate(Player p) {
        if (!spectators.contains(p.getUniqueId())) {
            spectators.add(p.getUniqueId());
        }
        p.teleport(waitSpawn().add(0, 3, 0));
        p.setGameMode(GameMode.SPECTATOR);
        if (board != null) {
            p.setScoreboard(board);
        }
    }

    /** Sortie de la partie (commande, deconnexion, changement de monde). */
    void leave(Player p, boolean teleport) {
        UUID id = p.getUniqueId();
        p.setGlowing(false);
        boolean was = players.containsKey(id) || spectators.contains(id);
        spectators.remove(id);
        nextRound.remove(id);
        if (was) {
            removeLeaveItem(p);
        }
        Racer r = players.get(id);
        if (r != null) {
            if (state == State.PLAYING) {
                // abandon : la place reste au tableau, sans vie, et le jeu ne le pilote plus
                boolean wasAlive = r.lives > 0;
                r.lives = 0;
                gone.add(id);
                if (wasAlive) {
                    tell(COLOR_CODES[r.color] + r.name + " &7abandonne.");
                }
                if (id.equals(jumper) && !resolving) {
                    resolving = true;
                    later(20L, this::nextTurn);
                } else if (alive() <= 1 && order.size() >= 2) {
                    later(20L, this::nextTurn);
                }
                updateBoard();
            } else {
                players.remove(id);
                tell(COLOR_CODES[r.color] + r.name + " &7quitte la partie.");
            }
        }
        if (was) {
            if (board != null && p.getScoreboard() == board) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            if (teleport && inWorld(p)) {
                toLobby(p);
            }
            sendStatus();
        }
    }

    void toLobby(Player p) {
        String cmd = getConfig().getString("lobby-command", "lobby");
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        if (!p.performCommand(cmd)) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
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
        Location to = e.getTo();
        UUID id = p.getUniqueId();
        boolean inGame = players.containsKey(id);
        if (state == State.PLAYING && id.equals(jumper) && !resolving) {
            if (!jumped && to.getY() < by - 0.5) {
                jumped = true;
                jumpedAt = System.currentTimeMillis();
            }
            if (!jumped) {
                return;
            }
            Block feet = to.getBlock();
            int x = feet.getX();
            int z = feet.getZ();
            if (feet.getType() == Material.WATER && inPool(x, z) && feet.getY() >= WY - 1) {
                land(p, x, z);
                return;
            }
            Block below = to.clone().subtract(0, 0.08, 0).getBlock();
            // pose sur un bord de bloc (centre au-dessus de l'eau) : p.isOnGround() le voit, pas le bloc sous le centre
            @SuppressWarnings("deprecation")
            boolean grounded = p.isOnGround();
            boolean onGround = (below.getType().isSolid() || grounded) && to.getY() < by - 1;
            if (onGround) {
                miss(id, inPool(below.getX(), below.getZ()) ? "Tombe sur un bloc !" : "Rate la piscine !");
                return;
            }
            if (to.getY() < WY - 8) {
                miss(id, "Rate la piscine !");
            }
            return;
        }
        // personne d'autre sur le plongeoir ni dans l'eau
        boolean onBoard = to.getY() >= by - 1 && to.getZ() > -(radius + 3) + 0.0 && Math.abs(to.getX() - 0.5) < 2;
        if (to.getY() < by - 3 || onBoard) {
            if (inGame || !p.hasPermission("eemideacoudre.admin") || p.getGameMode() != GameMode.CREATIVE) {
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
                if (p.isOnline() && inWorld(p)) {
                    removeLeaveItem(p);
                    toLobby(p);
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

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (world == null || !e.getPlayer().getWorld().equals(world) || builder(e.getPlayer())) {
            return;
        }
        Player ip = e.getPlayer();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND && isLeaveItem(e.getItem())) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (players.containsKey(ip.getUniqueId()) || spectators.contains(ip.getUniqueId())) {
                leave(ip, true);
                msg(ip, "&7Tu as quitté la file du Dé à coudre.");
            } else {
                removeLeaveItem(ip);
            }
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // objets du lobby (perles, charges de vent, fusees...) inutilisables ici
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)
                && !(e.getEntity().getShooter() instanceof Player p && builder(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world) && e.isGliding()) {
            e.setCancelled(true);
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

    /** Le lit « Retour au lobby » ne se deplace pas (ni grille d'artisanat, ni curseur, ni main gauche). */
    @EventHandler(ignoreCancelled = true)
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (isLeaveItem(e.getCurrentItem()) || isLeaveItem(e.getCursor())
                || (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player hp
                    && isLeaveItem(hp.getInventory().getItem(e.getHotbarButton())))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        if (isLeaveItem(e.getMainHandItem()) || isLeaveItem(e.getOffHandItem())) {
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
        boolean admin = s.hasPermission("eemideacoudre.admin");
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
                    } else if (canEnter(p)) {
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
                } else if (state == State.PLAYING) {
                    tell("&cPartie arretee par un admin.");
                    state = State.ENDING;
                    finish(false);
                } else {
                    // arret admin : tout le monde au lobby (comportement d'avant)
                    finish(false);
                    msg(s, "&7Attente remise a zero.");
                }
            }
            case "build" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else if (state == State.PLAYING) {
                    msg(s, "&cImpossible pendant une partie.");
                } else {
                    build();
                    msg(s, "&aPiscine reconstruite (" + countWater() + " cases d'eau).");
                }
            }
            case "reload" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                } else {
                    reloadConfig();
                    msg(s, "&aConfig rechargee. Nouveau rayon de piscine : &f/dac build&a (hors partie).");
                }
            }
            case "info" -> msg(s, "&7Etat &f" + state + "&7, inscrits &f" + players.size() + "&7, en vie &f" + alive() + "&7, eau &f" + (world == null ? 0 : countWater()));
            default -> {
                msg(s, "&f/dac join &7- rejoindre | &f/dac leave &7- quitter | &f/dac spectate &7- regarder");
                if (admin) {
                    msg(s, "&c/dac start | stop | build | reload | info");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>(List.of("join", "leave", "spectate"));
        if (s.hasPermission("eemideacoudre.admin")) {
            out.addAll(List.of("start", "stop", "build", "reload", "info"));
        }
        if (a.length == 1) {
            out.removeIf(x -> !x.startsWith(a[0].toLowerCase()));
            return out;
        }
        return List.of();
    }
}
