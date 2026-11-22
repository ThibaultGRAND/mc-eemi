package fr.eemicraft.ballsofsteel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Balls of Steel d'EEMICRAFT, sur la map « Balls of Steel » (1.16.5, Planet Minecraft). Jusqu'à 4 équipes
 * (Rouge, Vert, Jaune, Bleu), chacune dans sa base. Les boules flottantes contiennent des minerais de diamant :
 * on les mine et on les dépose sur la dalle de son équipe au centre (1 diamant = 1 point). Un marchand au
 * centre échange des diamants contre du matériel. Pas de régénération naturelle (sauf dans sa base), +1 niveau
 * d'XP pour toute l'équipe à chaque kill, on ne casse pas les bases ennemies mais on peut piller leurs coffres.
 * Les scores sont cachés en fin de partie. Toutes les modifications de l'arène sont annulées à la fin.
 *
 * Multi-partie : partie 1 = le monde d'origine (« ballsofsteel »), parties 2..max-instances = copies
 * « ballsofsteel_n » faites depuis le modèle propre « ballsofsteel_modele » (copié au démarrage).
 * Le salon d'attente est dans la map : chaque partie a le sien.
 */
public final class EemiBallsOfSteel extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§9Balls of Steel§8] §7";

    enum State { WAITING, STARTING, PLAYING, ENDING }

    /** Ordre d'attribution : Rouge et Vert sont en coins opposés, puis Jaune et Bleu. */
    enum Side {
        ROUGE("rouge", "Rouge", NamedTextColor.RED, Color.RED, "§c"),
        VERT("vert", "Vert", NamedTextColor.GREEN, Color.fromRGB(0x5E7C16), "§a"),
        JAUNE("jaune", "Jaune", NamedTextColor.YELLOW, Color.YELLOW, "§e"),
        BLEU("bleu", "Bleu", NamedTextColor.BLUE, Color.fromRGB(0x3C44AA), "§9");

        final String key;
        final String label;
        final NamedTextColor color;
        final Color armor;
        final String code;

        Side(String key, String label, NamedTextColor color, Color armor, String code) {
            this.key = key;
            this.label = label;
            this.color = color;
            this.armor = armor;
            this.code = code;
        }

        /** Nom accordé avec « équipe » (féminin) : Rouge, Verte, Jaune, Bleue. */
        String fem() {
            return label.equals("Bleu") ? "Bleue" : label.equals("Vert") ? "Verte" : label;
        }
    }

    /** Boîte de blocs (bornes incluses). */
    private record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        static Box parse(String s) {
            String[] p = s.split(",");
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) v[i] = (int) Math.floor(Double.parseDouble(p[i].trim()));
            return new Box(Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5]),
                    Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5]));
        }

        boolean contains(int x, int y, int z) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }

        boolean contains(Location l) {
            return contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        }
    }

    /** Coordonnées d'une équipe (positions sans monde : rattachées au monde de chaque partie). */
    private static final class TeamData {
        Location spawn;
        Box pad;
        Location arrows;
        Box base;
    }

    private NamespacedKey kitKey;
    /** Nom du monde de la partie 1 (fixé au démarrage). */
    private String baseName;
    /** Positions sans monde, communes à toutes les parties. */
    private Location lobbyPos, merchantPos;
    private Box arena, center;
    private int voidY;
    private final Map<Side, TeamData> data = new EnumMap<>(Side.class);

    /** Parties existantes par numéro (1 = monde d'origine). */
    private final Map<Integer, Game> games = new TreeMap<>();
    /** Inscrits → leur partie. */
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    /** Spectateurs → partie regardée. */
    private final Map<UUID, Game> spectating = new HashMap<>();
    /** Copies en préparation : numéro → joueurs qui l'attendent (au hub). */
    private final Map<Integer, List<UUID>> creating = new LinkedHashMap<>();
    private final Map<UUID, Integer> pendingOf = new HashMap<>();
    /** Modèle propre (copie de la partie 1 faite au démarrage), source de toutes les copies. */
    private World model;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        saveDefaultConfig();
        baseName = getConfig().getString("world", "ballsofsteel");
        loadSettings();
        World base = Bukkit.getWorld(baseName);
        if (base == null) getLogger().warning("Monde " + baseName + " introuvable.");
        else games.put(1, new Game(1, base));
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Game g : new ArrayList<>(games.values())) if (!g.destroyed) g.tick();
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Game g : new ArrayList<>(games.values())) if (!g.destroyed) g.fastTick();
        }, 2L, 2L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Game g1 = games.get(1);
            if (g1 == null) return;
            prepareWorld(g1.world);
            WorldClone.cleanup(this, g1.world, baseName, maxInstances());
            // la partie 1 est propre au démarrage (remise en état à l'arrêt) : on en fait le modèle
            Bukkit.getScheduler().runTaskLater(this, this::makeModel, 20L);
        }, 40L);
        reportStatus();
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) {
            g.sendSpectatorsToHub();
            for (Player p : online(g.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                sendToHub(p);
            }
            if (g.n == 1) {
                // remise en état immédiate si le serveur s'arrête en pleine partie
                for (Map.Entry<Long, BlockData> en : g.original.entrySet()) {
                    long k = en.getKey();
                    g.world.getBlockAt(kx(k), ky(k), kz(k)).setBlockData(en.getValue(), false);
                }
                g.original.clear();
            } else {
                // les fichiers de la copie seront supprimés au prochain démarrage (WorldClone.cleanup)
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

    private String modelName() {
        return baseName + "_modele";
    }

    /** (Re)crée le modèle propre depuis la partie 1. */
    private void makeModel() {
        Game g1 = games.get(1);
        if (g1 == null) return;
        World old = Bukkit.getWorld(modelName());
        model = null;
        if (old != null) {
            for (Player p : old.getPlayers()) sendToHub(p);
            Bukkit.unloadWorld(old, false);
        }
        WorldClone.create(this, g1.world, modelName(), w -> {
            model = w;
            if (w == null) getLogger().warning("Modèle " + modelName() + " non cree : une seule partie possible.");
            else getLogger().info("Modèle " + modelName() + " prêt (" + maxInstances() + " parties max).");
        });
    }

    private void loadSettings() {
        reloadConfig();
        lobbyPos = loc(getConfig().getString("lobby", "-35.5,50,-1.5,180,0"));
        merchantPos = loc(getConfig().getString("merchant", "1000.5,130,1000.5"));
        arena = Box.parse(getConfig().getString("arena", "889,0,889,1111,255,1111"));
        center = Box.parse(getConfig().getString("center", "979,108,979,1021,150,1021"));
        voidY = cfg("void-y", 40);
        data.clear();
        for (Side s : Side.values()) {
            String p = "teams." + s.key + ".";
            TeamData d = new TeamData();
            d.spawn = loc(getConfig().getString(p + "spawn"));
            d.pad = Box.parse(getConfig().getString(p + "pad"));
            d.arrows = loc(getConfig().getString(p + "arrows"));
            d.base = Box.parse(getConfig().getString(p + "base"));
            data.put(s, d);
        }
    }

    /** Règles du monde : pas de régénération naturelle, jour fixe, pas de mobs ni de feu qui se propage. */
    private void prepareWorld(World world) {
        if (world == null) return;
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setTime(6000);
        world.getWorldBorder().setSize(59_999_968);
        setupMerchant(world);
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    /** Position sans monde (rattachée ensuite au monde de la partie avec at()). */
    private Location loc(String s) {
        String[] p = s.split(",");
        Location l = new Location(null, Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
        if (p.length >= 5) {
            l.setYaw(Float.parseFloat(p[3].trim()));
            l.setPitch(Float.parseFloat(p[4].trim()));
        }
        return l;
    }

    private static Location at(Location pos, World w) {
        Location l = pos.clone();
        l.setWorld(w);
        return l;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int kx(long k) {
        return (int) (k >> 38) << 6 >> 6;
    }

    private static int kz(long k) {
        return (int) ((k >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    private static int ky(long k) {
        return (int) (k & 0xFFF) << 20 >> 20;
    }

    // ------------------------------------------------------------------ parties : recherche

    private Game gameOfWorld(World w) {
        if (w == null) return null;
        for (Game g : games.values()) if (!g.destroyed && g.world.equals(w)) return g;
        return null;
    }

    private boolean isModel(World w) {
        return w != null && w.getName().equals(modelName());
    }

    /** Monde protégé : monde d'une partie, copie en cours de chargement ou modèle. */
    private boolean isGameWorld(World w) {
        return w != null && (gameOfWorld(w) != null || isModel(w) || WorldClone.index(baseName, w.getName()) > 0);
    }

    // ------------------------------------------------------------------ marchand

    /** Le marchand du centre (échanges de la map d'origine), recréé si besoin, immobile et invulnérable. */
    private void setupMerchant(World world) {
        if (world == null) return;
        Location merchant = at(merchantPos, world);
        Villager v = null;
        for (Entity e : world.getNearbyEntities(merchant, 4, 4, 4)) {
            if (e instanceof Villager vi) {
                if (v == null) v = vi;
                else vi.remove();
            }
        }
        if (v == null) v = world.spawn(merchant, Villager.class);
        v.teleport(merchant);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
        v.setProfession(Villager.Profession.NITWIT);
        v.customName(Component.text("Marchand", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        v.setCustomNameVisible(true);
        List<MerchantRecipe> r = new ArrayList<>();
        r.add(trade(new ItemStack(Material.WATER_BUCKET), new ItemStack(Material.DRIED_KELP_BLOCK, 16), new ItemStack(Material.BUCKET)));
        r.add(trade(tool(Material.IRON_PICKAXE), new ItemStack(Material.IRON_PICKAXE), new ItemStack(Material.DIAMOND, 8)));
        r.add(trade(tool(Material.STONE_AXE), new ItemStack(Material.STONE_AXE), new ItemStack(Material.DIAMOND, 8)));
        r.add(trade(tool(Material.STONE_SHOVEL), new ItemStack(Material.STONE_SHOVEL), new ItemStack(Material.DIAMOND, 8)));
        r.add(trade(new ItemStack(Material.ENCHANTING_TABLE), new ItemStack(Material.OBSIDIAN, 4), new ItemStack(Material.DIAMOND, 2)));
        r.add(trade(book(Enchantment.FIRE_ASPECT, 1), new ItemStack(Material.LAVA_BUCKET), new ItemStack(Material.LAVA_BUCKET)));
        r.add(trade(book(Enchantment.FORTUNE, 2), new ItemStack(Material.STONE_BRICKS, 64), new ItemStack(Material.IRON_INGOT, 64)));
        r.add(trade(book(Enchantment.FLAME, 1), new ItemStack(Material.LAVA_BUCKET), new ItemStack(Material.LAVA_BUCKET)));
        v.setRecipes(r);
    }

    private MerchantRecipe trade(ItemStack result, ItemStack a, ItemStack b) {
        MerchantRecipe m = new MerchantRecipe(result, Integer.MAX_VALUE);
        m.setExperienceReward(false);
        m.addIngredient(a);
        m.addIngredient(b);
        return m;
    }

    private ItemStack tool(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 2, true);
        meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack book(Enchantment e, int level) {
        ItemStack it = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) it.getItemMeta();
        meta.addStoredEnchant(e, level, true);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ une partie

    private final class Game {
        final int n;
        final World world;
        final Set<UUID> players = new LinkedHashSet<>();
        final Map<UUID, Side> teams = new HashMap<>();
        final Map<Side, Integer> score = new EnumMap<>(Side.class);
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, Integer> deposited = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Spectateurs non inscrits de cette partie (/ballsofsteel spectate). */
        final Set<UUID> spectators = new HashSet<>();
        /** Retour en arrière : état d'origine de chaque bloc de l'arène modifié pendant la partie. */
        final Map<Long, BlockData> original = new LinkedHashMap<>();

        State state = State.WAITING;
        int timer;
        int elapsed;
        boolean resetting;
        boolean destroyed;

        Game(int n, World world) {
            this.n = n;
            this.world = world;
        }

        Location lobby() {
            return at(lobbyPos, world);
        }

        Location merchant() {
            return at(merchantPos, world);
        }

        Location spawn(Side s) {
            return at(data.get(s).spawn, world);
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        // ---------------------------------------------------------- boucles

        void tick() {
            switch (state) {
                case WAITING -> {
                    if (!resetting && players.size() >= cfg("min-players", 2)) {
                        state = State.STARTING;
                        timer = cfg("countdown", 30);
                        broadcast("§eAssez de joueurs : la partie commence dans §6" + timer + " s§e.");
                        reportStatus();
                    }
                }
                case STARTING -> {
                    if (players.size() < cfg("min-players", 2)) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                        reportStatus();
                    } else {
                        if (players.size() >= cfg("max-players", 24)) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0) for (Player p : online(players)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> {
                    timer--;
                    elapsed++;
                    for (Player p : online(players)) if (teams.containsKey(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL) perSecond(p);
                    if (timer == 300 || timer == 60) broadcast("§eFin de la partie dans §6" + (timer / 60) + " min§e !");
                    if (hideAfter() > 0 && elapsed == hideAfter()) broadcast("§7Les scores sont maintenant §ccachés §7jusqu'à la fin !");
                    if (timer <= 0) endGame(true);
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (destroyed) return;
            for (Player p : online(players)) updateSidebar(p);
        }

        boolean scoresHidden() {
            return state == State.PLAYING && hideAfter() > 0 && elapsed >= hideAfter();
        }

        /** Chaque seconde, pour un joueur en jeu : terre infinie, flèches au générateur, soin dans sa base, hache affaiblie. */
        void perSecond(Player p) {
            Side s = teams.get(p.getUniqueId());
            TeamData d = data.get(s);
            if (d == null || !mayTouch(p)) return;
            // terre infinie : toujours au moins 16 blocs de terre, en main gauche si elle est libre
            int dirt = 0;
            for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == Material.DIRT) dirt += it.getAmount();
            if (dirt < 16) {
                ItemStack off = p.getInventory().getItemInOffHand();
                if (off.getType() == Material.DIRT) off.setAmount(Math.min(64, off.getAmount() + 16 - dirt));
                else if (off.getType() == Material.AIR) p.getInventory().setItemInOffHand(kit(new ItemStack(Material.DIRT, 16 - dirt)));
                else p.getInventory().addItem(kit(new ItemStack(Material.DIRT, 16 - dirt)));
            }
            if (p.getWorld().equals(world) && p.getLocation().distanceSquared(at(d.arrows, world)) <= 2.25) {
                int have = 0;
                for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == Material.ARROW) have += it.getAmount();
                if (have < 64) p.getInventory().addItem(new ItemStack(Material.ARROW, Math.min(4, 64 - have)));
            }
            if (d.base.contains(p.getLocation())) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 1, false, false, true));
            if (p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE"))
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false, true));
        }

        /** Toutes les 2 ticks : dépôt des diamants sur la dalle de l'équipe (1 diamant = 1 point). */
        void fastTick() {
            if (state != State.PLAYING) return;
            for (Player p : online(players)) {
                Side s = teams.get(p.getUniqueId());
                if (s == null || p.getGameMode() != GameMode.SURVIVAL || !p.getWorld().equals(world)) continue;
                Box pad = data.get(s).pad;
                Location l = p.getLocation();
                if (l.getBlockX() < pad.x1() || l.getBlockX() > pad.x2() || l.getBlockZ() < pad.z1() || l.getBlockZ() > pad.z2()
                        || l.getY() < pad.y1() || l.getY() > pad.y1() + 3) continue;
                if (!p.getInventory().removeItem(new ItemStack(Material.DIAMOND, 1)).isEmpty()) continue;
                int v = score.merge(s, 1, Integer::sum);
                deposited.merge(p.getUniqueId(), 1, Integer::sum);
                p.playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
                if (!scoresHidden()) p.sendActionBar(Component.text("+1 diamant pour l'équipe " + s.fem() + " (" + v + ")", s.color));
                else p.sendActionBar(Component.text("+1 diamant déposé", s.color));
            }
        }

        List<Side> activeSides() {
            List<Side> l = new ArrayList<>();
            for (Side s : Side.values()) if (teams.containsValue(s)) l.add(s);
            return l;
        }

        // ---------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("bos", Criteria.DUMMY,
                        Component.text("BALLS ", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                                .append(Component.text("OF STEEL", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("bos");
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
            switch (state) {
                case WAITING, STARTING -> {
                    l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("#" + n, NamedTextColor.WHITE)));
                    l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + cfg("max-players", 24), NamedTextColor.WHITE)));
                    l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                    l.add(Component.text("Durée : ", NamedTextColor.GRAY).append(Component.text(cfg("game-minutes", 30) + " min", NamedTextColor.WHITE)));
                    l.add(Component.text("  "));
                    if (resetting) l.add(Component.text("Remise à zéro...", NamedTextColor.GOLD));
                    else if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GOLD)));
                    else l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                }
                case PLAYING, ENDING -> {
                    boolean hidden = scoresHidden();
                    for (Side s : activeSides())
                        l.add(Component.text(s.label + " : ", s.color).append(Component.text(
                                hidden ? "???" : score.getOrDefault(s, 0) + " diamants", NamedTextColor.WHITE)));
                    l.add(Component.text("  "));
                    Side s = teams.get(p.getUniqueId());
                    if (s != null) l.add(Component.text("Équipe : ", NamedTextColor.GRAY).append(Component.text(s.fem(), s.color)));
                    l.add(Component.text("Déposés : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(deposited.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.AQUA)));
                    l.add(Component.text("Kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                    l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(
                            String.format("%d:%02d", Math.max(0, timer) / 60, Math.max(0, timer) % 60), NamedTextColor.WHITE)));
                }
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        /** Pseudos colorés par équipe, sans tir ami. */
        void colorNames() {
            for (Player viewer : online(players)) {
                Scoreboard b = board(viewer);
                for (Side s : Side.values()) {
                    Team t = b.getTeam("bos_" + s.key);
                    if (t == null) {
                        t = b.registerNewTeam("bos_" + s.key);
                        t.color(s.color);
                        t.prefix(Component.text("[" + s.label + "] ", s.color));
                        t.setAllowFriendlyFire(false);
                    }
                    for (String e : new ArrayList<>(t.getEntries())) t.removeEntry(e);
                }
                for (Map.Entry<UUID, Side> e : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) b.getTeam("bos_" + e.getValue().key).addEntry(p.getName());
                }
            }
        }

        void clearNames() {
            for (Scoreboard b : boards.values())
                for (Side s : Side.values()) {
                    Team t = b.getTeam("bos_" + s.key);
                    if (t != null) t.unregister();
                }
        }

        // ---------------------------------------------------------- inscription

        void add(Player p) {
            UUID id = p.getUniqueId();
            Game sg = spectating.remove(id);
            if (sg != null) sg.spectators.remove(id);
            players.add(id);
            byPlayer.put(id, this);
            p.teleport(lobby());
            later(() -> prepareLobbyPlayer(p));
            broadcast("§a" + p.getName() + " §7a rejoint Balls of Steel (§f" + players.size() + "§7).");
            if (state == State.PLAYING || state == State.ENDING) p.sendMessage(PREFIX + "§7Une partie est en cours, tu joueras à la suivante.");
            reportStatus();
        }

        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || !players.contains(p.getUniqueId()) || !mayTouch(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter Balls of Steel"));
            updateSidebar(p);
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            byPlayer.remove(id, this);
            Side side = teams.remove(id);
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) sendToHub(p);
            colorNames();
            if (state == State.PLAYING && side != null) {
                broadcast("§7" + p.getName() + " a quitté la partie.");
                if (teams.isEmpty()) forceStop();
            }
            reportStatus();
            checkEmpty();
        }

        /** Une copie vide et en attente ne sert plus : détruite (la prochaine repartira du modèle). */
        void checkEmpty() {
            if (n < 2 || destroyed) return;
            Bukkit.getScheduler().runTask(EemiBallsOfSteel.this, () -> {
                if (!destroyed && state == State.WAITING && players.isEmpty()) destroyGame(this);
            });
        }

        // ---------------------------------------------------------- partie

        void startGame() {
            List<Player> list = online(players);
            if (list.size() < 2 || resetting) { state = State.WAITING; reportStatus(); return; }
            Collections.shuffle(list);
            teams.clear();
            kills.clear();
            deposited.clear();
            score.clear();
            original.clear();
            int count = Math.max(2, Math.min(4, list.size()));
            Side[] order = Side.values();
            for (int i = 0; i < list.size(); i++) teams.put(list.get(i).getUniqueId(), order[i % count]);
            for (Side s : activeSides()) score.put(s, 0);
            colorNames();
            setupMerchant(world);
            clearArenaEntities();
            // « x,z » (et non x,y,z) : centre de la bordure de l'arène (bordure propre au monde de la partie)
            String[] c = getConfig().getString("arena-center", "1000.5,1000.5").split(",");
            world.getWorldBorder().setCenter(Double.parseDouble(c[0].trim()), Double.parseDouble(c[1].trim()));
            world.getWorldBorder().setSize(cfg("border-size", 226));
            state = State.PLAYING;
            timer = cfg("game-minutes", 30) * 60;
            elapsed = 0;
            for (Player p : list) {
                Side s = teams.get(p.getUniqueId());
                p.teleport(spawn(s));
                later(() -> equip(p));
                p.showTitle(Title.title(Component.text("Équipe " + s.fem(), s.color).decorate(TextDecoration.BOLD),
                        Component.text("Mine et dépose les diamants au centre", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            }
            broadcast("§eLa partie commence ! §7Mine les diamants des boules et dépose-les sur la dalle de ton équipe au centre. "
                    + "§cPas de régénération naturelle §7(sauf dans ta base). §6" + cfg("game-minutes", 30) + " min§7.");
            reportStatus();
        }

        /** Kit de la map d'origine : outils de pierre/fer incassables, arc, plastron de l'équipe, terre infinie. */
        void equip(Player p) {
            Side s = teams.get(p.getUniqueId());
            if (!p.isOnline() || s == null || state != State.PLAYING || !mayTouch(p)) return;
            resetPlayer(p, GameMode.SURVIVAL);
            PlayerInventory inv = p.getInventory();
            inv.setItem(0, unbreakable(kit(new ItemStack(Material.STONE_SWORD))));
            inv.setItem(1, unbreakable(kit(new ItemStack(Material.BOW))));
            inv.setItem(2, unbreakable(kit(new ItemStack(Material.IRON_PICKAXE))));
            inv.setItem(3, unbreakable(kit(new ItemStack(Material.STONE_AXE))));
            inv.setItem(4, unbreakable(kit(new ItemStack(Material.STONE_SHOVEL))));
            inv.setItem(5, new ItemStack(Material.WATER_BUCKET));
            inv.setItem(8, kit(new ItemStack(Material.ARROW, 16)));
            ItemStack chest = unbreakable(kit(new ItemStack(Material.LEATHER_CHESTPLATE)));
            LeatherArmorMeta meta = (LeatherArmorMeta) chest.getItemMeta();
            meta.setColor(s.armor);
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            chest.setItemMeta(meta);
            inv.setChestplate(chest);
            inv.setItemInOffHand(kit(new ItemStack(Material.DIRT, 16)));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 3 * 20, 127));
        }

        /** rewards = false pour un arrêt admin : pas de récompenses EemiCore. */
        void endGame(boolean rewards) {
            state = State.ENDING;
            timer = cfg("end-time", 10);
            int best = -1;
            List<Side> winners = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            for (Side s : activeSides()) {
                int v = score.getOrDefault(s, 0);
                sb.append(s.code).append(s.label).append(" §f").append(v).append("  ");
                if (v > best) { best = v; winners.clear(); winners.add(s); }
                else if (v == best) winners.add(s);
            }
            Component title = winners.size() == 1
                    ? Component.text("Victoire " + winners.get(0).label + " !", winners.get(0).color).decorate(TextDecoration.BOLD) // titre court (≤ 16 caractères)
                    : Component.text("Égalité !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
            for (Player p : online(players)) {
                if (!teams.containsKey(p.getUniqueId())) continue;
                p.showTitle(Title.title(title, Component.text((winners.size() > 1 ? winners.size() + " équipes à égalité : " : "")
                        + Math.max(0, best) + " diamants", NamedTextColor.AQUA)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (mayTouch(p)) p.setGameMode(GameMode.SPECTATOR);
            }
            broadcast("§6Fin de la partie ! §7Scores : " + sb.toString().trim());
            // meilleur tueur
            UUID top = null;
            int topKills = 0;
            for (Map.Entry<UUID, Integer> e : kills.entrySet()) if (e.getValue() > topKills) { topKills = e.getValue(); top = e.getKey(); }
            if (top != null && Bukkit.getPlayer(top) != null)
                broadcast("§7Meilleur combattant : §f" + Bukkit.getPlayer(top).getName() + " §7(" + topKills + " kills)");
            if (rewards) {
                // victoire seulement s'il y a une équipe gagnante (pas d'égalité)
                for (Map.Entry<UUID, Side> en : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p == null) continue;
                    eemi("reward " + p.getName() + " ballsofsteel play");
                    if (winners.size() == 1 && winners.get(0) == en.getValue()) eemi("reward " + p.getName() + " ballsofsteel win");
                }
            }
            reportStatus();
        }

        /** Retour au salon d'attente de cette partie ; une copie restée vide est ensuite détruite. */
        void backToLobby() {
            clearNames();
            world.getWorldBorder().setSize(59_999_968);
            for (UUID id : new ArrayList<>(teams.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !players.contains(id)) continue;
                p.teleport(lobby());
                later(() -> prepareLobbyPlayer(p));
            }
            teams.clear();
            state = State.WAITING;
            sendSpectatorsToHub();
            reportStatus();
            Bukkit.getScheduler().runTaskLater(EemiBallsOfSteel.this, this::rollback, 20L);
            checkEmpty();
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(false);
            if (state == State.ENDING) backToLobby();
            state = State.WAITING;
            reportStatus();
        }

        // ---------------------------------------------------------- remise à zéro de l'arène

        /** Garde l'état d'origine d'un bloc de l'arène avant sa première modification. */
        void record(Block b) {
            if (state != State.PLAYING || !arena.contains(b.getX(), b.getY(), b.getZ())) return;
            original.putIfAbsent(key(b.getX(), b.getY(), b.getZ()), b.getBlockData());
        }

        void record(BlockState st) {
            if (state != State.PLAYING || !arena.contains(st.getX(), st.getY(), st.getZ())) return;
            original.putIfAbsent(key(st.getX(), st.getY(), st.getZ()), st.getBlockData());
        }

        /** Remet tous les blocs modifiés, vide les coffres des bases et retire objets et projectiles. */
        void rollback() {
            if (destroyed || resetting) return;
            resetting = true;
            clearArenaEntities();
            List<Map.Entry<Long, BlockData>> todo = new ArrayList<>(original.entrySet());
            original.clear();
            restore(todo, 0);
        }

        void restore(List<Map.Entry<Long, BlockData>> todo, int from) {
            if (destroyed) return;
            int end = Math.min(todo.size(), from + 5000);
            for (int i = from; i < end; i++) {
                long k = todo.get(i).getKey();
                world.getBlockAt(kx(k), ky(k), kz(k)).setBlockData(todo.get(i).getValue(), false);
            }
            if (end < todo.size()) {
                Bukkit.getScheduler().runTaskLater(EemiBallsOfSteel.this, () -> restore(todo, end), 1L);
                return;
            }
            clearBaseChests();
            clearArenaEntities();
            setupMerchant(world);
            resetting = false;
            getLogger().info("Arène de la partie " + n + " (" + world.getName() + ") remise a zéro (" + todo.size() + " blocs).");
        }

        void clearBaseChests() {
            for (TeamData d : data.values()) {
                for (int cx = d.base.x1() >> 4; cx <= d.base.x2() >> 4; cx++)
                    for (int cz = d.base.z1() >> 4; cz <= d.base.z2() >> 4; cz++) {
                        Chunk ch = world.getChunkAt(cx, cz);
                        for (BlockState st : ch.getTileEntities()) {
                            if (st instanceof Container c && d.base.contains(st.getX(), st.getY(), st.getZ())) c.getInventory().clear();
                        }
                    }
            }
        }

        void clearArenaEntities() {
            for (Entity e : world.getEntities()) {
                if (e instanceof Player || !arena.contains(e.getLocation())) continue;
                if (e instanceof Item || e instanceof Projectile || e instanceof FallingBlock || e instanceof ExperienceOrb
                        || e instanceof org.bukkit.entity.TNTPrimed) e.remove();
            }
        }

        // ---------------------------------------------------------- spectateurs

        void sendSpectatorsToHub() {
            for (Player sp : online(spectators)) {
                spectating.remove(sp.getUniqueId(), this);
                sendToHub(sp);
            }
            for (UUID id : spectators) spectating.remove(id, this);
            spectators.clear();
        }

        int inGame() {
            return state == State.PLAYING || state == State.ENDING ? teams.size() : 0;
        }

        int waiting() {
            return (int) players.stream().filter(u -> !teams.containsKey(u)).count();
        }
    }

    // ------------------------------------------------------------------ création / destruction des parties

    /** /ballsofsteel join : partie en attente la plus remplie, sinon une nouvelle copie, sinon refus. */
    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (byPlayer.containsKey(id) || pendingOf.containsKey(id)) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (games.get(1) == null) { p.sendMessage(PREFIX + "§cLa map n'est pas chargée."); return; }
        int max = cfg("max-players", 24);
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
            p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
            reportStatus();
            return;
        }
        if (games.size() + creating.size() >= maxInstances()) { p.sendMessage(PREFIX + "§cToutes les parties sont pleines."); return; }
        if (model == null) { p.sendMessage(PREFIX + "§cLes parties ne sont pas encore prêtes, réessaie dans un instant."); return; }
        int n = 2;
        while (games.containsKey(n) || creating.containsKey(n)) n++;
        List<UUID> list = new ArrayList<>();
        list.add(id);
        creating.put(n, list);
        pendingOf.put(id, n);
        p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
        reportStatus();
        int num = n;
        WorldClone.create(this, model, WorldClone.name(baseName, num), w -> {
            List<UUID> waiting = creating.remove(num);
            if (waiting == null) waiting = List.of();
            if (w == null || !isEnabled()) {
                for (UUID u : waiting) {
                    if (!pendingOf.remove(u, num)) continue;
                    Player pl = Bukkit.getPlayer(u);
                    if (pl != null) pl.sendMessage(PREFIX + "§cImpossible de préparer une nouvelle partie.");
                }
                reportStatus();
                return;
            }
            Game g = new Game(num, w);
            games.put(num, g);
            prepareWorld(w);
            for (UUID u : waiting) {
                if (!pendingOf.remove(u, num)) continue;
                Player pl = Bukkit.getPlayer(u);
                if (pl != null && g.players.size() < max) g.add(pl);
            }
            if (g.players.isEmpty()) destroyGame(g);
            reportStatus();
        });
    }

    private void destroyGame(Game g) {
        if (g.n < 2 || g.destroyed) return;
        g.destroyed = true;
        games.remove(g.n);
        g.sendSpectatorsToHub();
        for (Player p : online(g.players)) {
            g.players.remove(p.getUniqueId());
            byPlayer.remove(p.getUniqueId(), g);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            sendToHub(p);
        }
        for (UUID id : g.players) byPlayer.remove(id, g);
        g.players.clear();
        WorldClone.destroy(this, g.world, baseName, this::sendToHub);
        getLogger().info("Partie " + g.n + " terminée, copie " + g.world.getName() + " supprimée.");
        reportStatus();
    }

    // ------------------------------------------------------------------ joueurs

    /**
     * Le plugin ne touche au joueur (inventaire, armure, XP, vie, faim, effets, mode de jeu) que s'il est
     * dans un monde du jeu (partie, copies, modèle) ou au hub : ailleurs (skyblock...) c'est l'inventaire
     * de CE monde que Multiverse-Inventories sauvegarderait.
     */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        return isGameWorld(w) || w.getName().equals(getConfig().getString("hub-world", "lobby"));
    }

    /**
     * Renvoi au hub. Joueur dans un monde du jeu : réinitialisé dans ce monde puis téléporté (MV-Inv sauvegarde
     * l'inventaire du jeu et recharge celui du hub, objets du hub compris). Déjà au hub : réinitialisé sur place.
     * Ailleurs (skyblock...) : ni téléporté ni réinitialisé.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    private void resetPlayer(Player p, GameMode mode) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    private int hideAfter() {
        return cfg("hide-scores-after", 20) * 60;
    }

    private ItemStack unbreakable(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack named(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    /** Objet de kit : marqué pour ne pas tomber à la mort ni être jeté. */
    private ItemStack kit(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ événements : blocs

    /** Peut-on modifier ce bloc ? Arène hors centre ; pas dans une base ennemie. Admins en créatif : libres. */
    private boolean canEdit(Player p, Block b) {
        if (!isGameWorld(b.getWorld())) return true;
        if (p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemiballsofsteel.admin")) return true;
        Game g = gameOfWorld(b.getWorld());
        if (g == null || byPlayer.get(p.getUniqueId()) != g) return false;
        Side s = g.teams.get(p.getUniqueId());
        if (g.state != State.PLAYING || s == null) return false;
        if (!arena.contains(b.getX(), b.getY(), b.getZ()) || inPlatform(b.getX(), b.getY(), b.getZ())) return false;
        for (Map.Entry<Side, TeamData> e : data.entrySet())
            if (e.getKey() != s && e.getValue().base.contains(b.getX(), b.getY(), b.getZ())) return false;
        return true;
    }

    private void record(Block b) {
        Game g = gameOfWorld(b.getWorld());
        if (g != null) g.record(b);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!canEdit(p, e.getBlock())) {
            e.setCancelled(true);
            Game g = byPlayer.get(p.getUniqueId());
            if (g != null && g.teams.containsKey(p.getUniqueId())) p.sendActionBar(Component.text("Tu ne peux pas casser ici !", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakRecord(BlockBreakEvent e) {
        record(e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (!canEdit(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceRecord(BlockPlaceEvent e) {
        Game g = gameOfWorld(e.getBlock().getWorld());
        if (g != null) g.record(e.getBlockReplacedState());
    }

    /** Cut clean : le fer et l'or minés tombent au sol directement en lingots (drop normal sinon). */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        if (!isGameWorld(e.getBlock().getWorld())) return;
        for (Item it : e.getItems()) {
            ItemStack s = it.getItemStack();
            if (s.getType() == Material.RAW_IRON) it.setItemStack(new ItemStack(Material.IRON_INGOT, s.getAmount()));
            else if (s.getType() == Material.RAW_GOLD) it.setItemStack(new ItemStack(Material.GOLD_INGOT, s.getAmount()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canEdit(e.getPlayer(), e.getBlock())) e.setCancelled(true);
        else record(e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canEdit(e.getPlayer(), e.getBlock())) e.setCancelled(true);
        else record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!isGameWorld(e.getBlock().getWorld())) return;
        Block to = e.getToBlock();
        // les liquides ne coulent ni dans le centre ni dans les bases
        if (inPlatform(to.getX(), to.getY(), to.getZ())) { e.setCancelled(true); return; }
        record(to);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!isGameWorld(e.getLocation().getWorld())) return;
        e.blockList().removeIf(b -> inPlatform(b.getX(), b.getY(), b.getZ()) || inAnyBase(b));
        for (Block b : e.blockList()) record(b);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!isGameWorld(e.getBlock().getWorld())) return;
        e.blockList().removeIf(b -> inPlatform(b.getX(), b.getY(), b.getZ()) || inAnyBase(b));
        for (Block b : e.blockList()) record(b);
    }

    /**
     * Plateforme du centre : le disque de quartz (rayon platform-radius autour du centre de la boîte
     * « center », sur sa hauteur). Intouchable ; autour, on construit librement (ponts pour la rejoindre).
     */
    private boolean inPlatform(int x, int y, int z) {
        if (y < center.y1() || y > center.y2()) return false;
        double cx = (center.x1() + center.x2()) / 2.0, cz = (center.z1() + center.z2()) / 2.0;
        double r = getConfig().getDouble("platform-radius", 21.5);
        double dx = x + 0.5 - (cx + 0.5), dz = z + 0.5 - (cz + 0.5);
        return dx * dx + dz * dz <= r * r;
    }

    private boolean inAnyBase(Block b) {
        for (TeamData d : data.values()) if (d.base.contains(b.getX(), b.getY(), b.getZ())) return true;
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        record(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        record(e.getBlock());
    }

    // ------------------------------------------------------------------ événements : joueurs

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isGameWorld(e.getLocation().getWorld()) && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        // le marchand est intouchable
        if (e.getEntity() instanceof Villager && isGameWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
            return;
        }
        if (!(e.getEntity() instanceof Player victim)) return;
        UUID id = victim.getUniqueId();
        Game g = byPlayer.get(id);
        if (g == null) return;
        if (g.state != State.PLAYING || !g.teams.containsKey(id) || victim.getGameMode() != GameMode.SURVIVAL) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent by) {
            Player attacker = by.getDamager() instanceof Player pl ? pl
                    : by.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pl ? pl : null;
            if (attacker != null && g.teams.get(attacker.getUniqueId()) == g.teams.get(id)) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null) return;
        Side s = g.teams.get(p.getUniqueId());
        if (s == null) return;
        e.getDrops().removeIf(this::isKit);
        e.setKeepLevel(false);
        e.deathMessage(null);
        Player killer = p.getKiller();
        if (killer != null && g.teams.containsKey(killer.getUniqueId()) && killer != p) {
            Side ks = g.teams.get(killer.getUniqueId());
            g.kills.merge(killer.getUniqueId(), 1, Integer::sum);
            if (g.state == State.PLAYING) eemi("reward " + killer.getName() + " ballsofsteel kill");
            // +1 niveau d'XP pour toute l'équipe du tueur
            for (Player m : online(g.players)) if (g.teams.get(m.getUniqueId()) == ks && mayTouch(m)) m.giveExpLevels(1);
            g.broadcast(ks.code + killer.getName() + " §7a tué " + s.code + p.getName());
        } else {
            g.broadcast(s.code + p.getName() + " §7est mort.");
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || g.destroyed) return;
        Side s = g.teams.get(p.getUniqueId());
        if (s != null && g.state == State.PLAYING) {
            e.setRespawnLocation(g.spawn(s));
            later(() -> g.equip(p));
            // sécurité : si le kit n'est pas arrivé (autre plugin, mode de jeu), on le redonne
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || g.state != State.PLAYING || !g.teams.containsKey(p.getUniqueId()) || !mayTouch(p)) return;
                if (p.getGameMode() != GameMode.SURVIVAL || !p.getInventory().contains(Material.STONE_SWORD)) g.equip(p);
            }, 20L);
            return;
        }
        if (s != null && g.state == State.ENDING) {
            e.setRespawnLocation(g.spawn(s));
            later(() -> { if (p.isOnline() && mayTouch(p)) p.setGameMode(GameMode.SPECTATOR); });
            return;
        }
        e.setRespawnLocation(g.lobby());
        later(() -> g.prepareLobbyPlayer(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null) return;
        Location to = e.getTo();
        if (!to.getWorld().equals(g.world)) return;
        if (to.getBlockY() == e.getFrom().getBlockY() && to.getBlockX() == e.getFrom().getBlockX()
                && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        if (g.state == State.PLAYING && g.teams.containsKey(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL) {
            if (to.getY() < voidY) p.setHealth(0);
            return;
        }
        if (to.getY() < voidY) p.teleport(g.lobby());
    }

    /** Un inscrit qui quitte le monde de sa partie (/lobby, /mv tp...) quitte la partie. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null && e.getFrom().equals(g.world) && !p.getWorld().equals(g.world)) g.leave(p, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isGameWorld(p.getWorld())) return;
        // panneaux du lobby de la map (anciennes commandes : équipes, start, reset) : désactivés
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().name().contains("SIGN") && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
            return;
        }
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté Balls of Steel.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        // le marchand n'échange qu'avec les joueurs de la partie en cours de ce monde
        if (!(e.getRightClicked() instanceof Villager) || !isGameWorld(e.getRightClicked().getWorld())) return;
        if (e.getPlayer().hasPermission("eemiballsofsteel.admin")) return;
        Game g = gameOfWorld(e.getRightClicked().getWorld());
        if (g == null || g.state != State.PLAYING || !g.teams.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent e) {
        Game g = byPlayer.get(e.getPlayer().getUniqueId());
        if (g == null) return;
        if (g.state != State.PLAYING || isKit(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && byPlayer.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        cancelPending(p.getUniqueId());
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null) g.leave(p, true);
    }

    /** Un spectateur qui quitte le monde de la partie regardée (/lobby...) redevient un joueur normal. */
    @EventHandler
    public void onSpectatorWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = spectating.get(p.getUniqueId());
        if (g == null || p.getWorld().equals(g.world)) return;
        spectating.remove(p.getUniqueId());
        g.spectators.remove(p.getUniqueId());
        // seulement s'il arrive au hub : ailleurs le mode est celui du monde (Multiverse)
        if (mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
    }

    @EventHandler
    public void onSpectatorQuit(PlayerQuitEvent e) {
        Game g = spectating.remove(e.getPlayer().getUniqueId());
        if (g != null) {
            g.spectators.remove(e.getPlayer().getUniqueId());
            sendToHub(e.getPlayer());
        }
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

    // ------------------------------------------------------------------ EemiCore et spectateurs

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /** Statut agrégé de toutes les parties pour EemiCore. */
    private void reportStatus() {
        boolean anyWaiting = games.isEmpty(), anyPlaying = false;
        int waiting = 0, playing = 0;
        for (Game g : games.values()) {
            if (g.destroyed) continue;
            if (g.state == State.WAITING || g.state == State.STARTING) anyWaiting = true;
            else anyPlaying = true;
            waiting += g.waiting();
            playing += g.inGame();
        }
        for (List<UUID> l : creating.values()) {
            waiting += l.size();
            anyWaiting = true;
        }
        String st = anyWaiting ? "waiting" : anyPlaying ? "playing" : "waiting";
        eemi("status ballsofsteel " + st + " " + waiting + " " + playing + " " + (cfg("max-players", 24) * maxInstances()));
    }

    /** /ballsofsteel spectate [joueur] : regarder une partie en cours en spectateur, sans y participer. */
    private void spectate(Player p, String targetName) {
        if (byPlayer.containsKey(p.getUniqueId()) || pendingOf.containsKey(p.getUniqueId())) {
            p.sendMessage(PREFIX + "§cTu es inscrit : /ballsofsteel leave d'abord.");
            return;
        }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            if (t == null) { p.sendMessage(PREFIX + "§cJoueur introuvable."); return; }
            g = byPlayer.get(t.getUniqueId());
            if (g == null || g.state != State.PLAYING) { p.sendMessage(PREFIX + "§c" + t.getName() + " n'est pas dans une partie en cours."); return; }
        } else {
            for (Game c : games.values())
                if (!c.destroyed && c.state == State.PLAYING && (g == null || c.teams.size() > g.teams.size())) g = c;
            if (g == null) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return; }
        }
        Game old = spectating.remove(p.getUniqueId());
        if (old != null) old.spectators.remove(p.getUniqueId());
        // au-dessus de la plateforme centrale (marchand), regard vers le bas
        Location view = g.merchant().add(0, 25, 0);
        view.setPitch(50f);
        p.teleport(view);
        g.spectators.add(p.getUniqueId());
        spectating.put(p.getUniqueId(), g);
        if (mayTouch(p)) p.setGameMode(GameMode.SPECTATOR);
        Game gg = g;
        later(() -> { if (p.isOnline() && gg.spectators.contains(p.getUniqueId()) && mayTouch(p)) p.setGameMode(GameMode.SPECTATOR); });
        p.sendMessage(PREFIX + "§7Tu regardes la partie " + g.n + ", /lobby pour revenir.");
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
                sender.sendMessage(PREFIX + "§cNuméro de partie invalide.");
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
        if (g == null) sender.sendMessage(PREFIX + "§cLa map n'est pas chargée.");
        return g;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemiballsofsteel.admin");
        switch (sub) {
            case "join" -> {
                Player target = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target);
            }
            case "leave" -> {
                if (p == null) return true;
                if (cancelPending(p.getUniqueId())) { p.sendMessage(PREFIX + "§7Tu as quitté Balls of Steel."); return true; }
                Game g = byPlayer.get(p.getUniqueId());
                if (g == null) return true;
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté Balls of Steel.");
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "start" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) return true;
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cLa partie " + g.n + " est déjà en cours."); return true; }
                if (g.players.size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs inscrits (partie " + g.n + ")."); return true; }
                if (g.resetting) { sender.sendMessage(PREFIX + "§cRemise à zéro en cours."); return true; }
                g.startGame();
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) return true;
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie " + g.n + " arrêtée.");
            }
            case "reset" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) return true;
                if (g.state == State.PLAYING) { sender.sendMessage(PREFIX + "§cAttends la fin de la partie " + g.n + "."); return true; }
                g.rollback();
                sender.sendMessage(PREFIX + "§aRemise à zéro de la partie " + g.n + " lancée.");
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.state == State.PLAYING || g.state == State.ENDING) { sender.sendMessage(PREFIX + "§cAttends la fin des parties en cours."); return true; }
                loadSettings();
                for (Game g : games.values()) prepareWorld(g.world);
                sender.sendMessage(PREFIX + "§aConfig rechargée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ", modèle "
                        + (model != null ? "§aprêt" : "§cabsent") + "§7) :");
                for (Game g : games.values())
                    sender.sendMessage("§7 #" + g.n + " §f" + g.world.getName() + " §7- " + g.state.name().toLowerCase()
                            + (g.resetting ? " (remise à zéro)" : "") + " - §f" + g.players.size() + " §7inscrits, §f"
                            + g.inGame() + " §7en jeu, §f" + g.spectators.size() + " §7spectateurs");
                for (Map.Entry<Integer, List<UUID>> c : creating.entrySet())
                    sender.sendMessage("§7 #" + c.getKey() + " §f" + WorldClone.name(baseName, c.getKey()) + " §7- préparation - §f"
                            + c.getValue().size() + " §7en attente");
            }
            default -> sender.sendMessage(PREFIX + "§7/ballsofsteel <join|leave|spectate [joueur]>");
        }
        return true;
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
