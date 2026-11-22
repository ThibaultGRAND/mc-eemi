package fr.eemicraft.bingo;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Bingo d'EEMICRAFT : chaque équipe doit trouver les items d'une grille 5x5 tirée au hasard,
 * dans un monde neuf (graine aléatoire) recréé à chaque partie. Un item trouvé est bloqué pour
 * les autres équipes. Première ligne (ligne, colonne ou diagonale) = victoire ; à la fin du temps,
 * l'équipe qui a le plus d'items gagne.
 * Modes = taille des équipes : solo (1), duo (2), quatuor (4). Une file par mode, les inscrits
 * attendent dans le lobby EEMINGO (monde dédié, salle construite par /bingo buildlobby).
 * La grille est aussi affichée sur une carte bloquée dans la deuxième main.
 * Plusieurs parties en même temps (max-instances, 3 par défaut) : partie 1 dans « bingo », partie n dans « bingo_n ».
 */
public final class EemiBingo extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§aEEMINGO§8] §7";

    /** Préfixe de la charte en Component : §8[§aEEMINGO§8] §7. */
    static Component prefixC() {
        return Component.text("[", NamedTextColor.DARK_GRAY).append(Component.text("EEMINGO", NamedTextColor.GREEN))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));
    }

    enum State { PLAYING, ENDING }

    /** Mode = taille des équipes. */
    enum Mode {
        SOLO(1, "Solo"), DUO(2, "Duo"), QUATUOR(4, "Quatuor");

        final int size;
        final String label;

        Mode(int size, String label) {
            this.size = size;
            this.label = label;
        }
    }

    enum TeamColor {
        ROUGE("Rouge", NamedTextColor.RED, Material.RED_STAINED_GLASS_PANE),
        BLEU("Bleu", NamedTextColor.BLUE, Material.BLUE_STAINED_GLASS_PANE),
        VERT("Vert", NamedTextColor.GREEN, Material.LIME_STAINED_GLASS_PANE),
        JAUNE("Jaune", NamedTextColor.YELLOW, Material.YELLOW_STAINED_GLASS_PANE),
        ROSE("Rose", NamedTextColor.LIGHT_PURPLE, Material.PINK_STAINED_GLASS_PANE),
        CYAN("Cyan", NamedTextColor.AQUA, Material.CYAN_STAINED_GLASS_PANE),
        ORANGE("Orange", NamedTextColor.GOLD, Material.ORANGE_STAINED_GLASS_PANE),
        VIOLET("Violet", NamedTextColor.DARK_PURPLE, Material.PURPLE_STAINED_GLASS_PANE);

        final String label;
        final NamedTextColor color;
        final Material pane;

        TeamColor(String label, NamedTextColor color, Material pane) {
            this.label = label;
            this.color = color;
            this.pane = pane;
        }

        Color awt() {
            return new Color(color.value());
        }
    }

    /** Marqueur de l'inventaire de la grille, pour reconnaître ses clics. */
    private static final class GridHolder implements InventoryHolder {
        private Inventory inv;
        /** Partie dont la grille est affichée. */
        private Game game;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Types d'objets du plugin (valeur stockée dans le PDC). */
    private static final byte TOOL_GRID = 1;
    private static final byte TOOL_QUIT = 2;
    private static final byte TOOL_BOOK = 3;

    /** Abréviations françaises (2 lignes de 4 caractères max) affichées sur la carte. */
    private static final Map<String, String> ABBR = new HashMap<>();

    static {
        String[] t = {
                // faciles
                "CRAFTING_TABLE", "ETA|BLI", "STICK", "BA|TON", "TORCH", "TOR|CHE", "FURNACE", "FOUR",
                "CHEST", "COF|FRE", "STONE_PICKAXE", "PIOC|PIER", "STONE_SWORD", "EPEE|PIER", "STONE_AXE", "HACH|PIER",
                "LADDER", "ECH|ELLE", "BOWL", "BOL", "FLINT", "SI|LEX", "COAL", "CHAR|BON", "CHARCOAL", "CHAR|BOIS",
                "WHEAT_SEEDS", "GRAI|BLE", "DANDELION", "PISS|ENLI", "POPPY", "COQ|LICO", "FEATHER", "PLU|ME",
                "EGG", "OEUF", "LEATHER", "CUIR", "ROTTEN_FLESH", "CHAI|POUR", "STRING", "FIL",
                "OAK_SAPLING", "POUS|CHEN", "BIRCH_LOG", "BUCH|BOUL", "SPRUCE_LOG", "BUCH|SAP", "OAK_BOAT", "BAT|EAU",
                "COBBLESTONE_STAIRS", "ESC|PIER", "STONE_BRICKS", "BRIQ|PIER", "SAND", "SA|BLE", "GRAVEL", "GRA|VIER",
                "OAK_DOOR", "POR|TE", "OAK_FENCE", "BARR|IERE", "WHITE_WOOL", "LAIN|BLAN", "APPLE", "POM|ME",
                "SUGAR_CANE", "CANN|SUCR",
                // moyens
                "IRON_INGOT", "LING|FER", "IRON_PICKAXE", "PIOC|FER", "BUCKET", "SEAU", "WATER_BUCKET", "SEAU|EAU",
                "SHIELD", "BOU|CLIE", "SHEARS", "CIS|EAUX", "BOOK", "LIV|RE", "BREAD", "PAIN", "BOW", "ARC",
                "ARROW", "FLE|CHE", "FISHING_ROD", "CANN|PECH", "COD", "MO|RUE", "COOKED_BEEF", "BOEU|CUIT",
                "COOKED_PORKCHOP", "PORC|CUIT", "COOKED_CHICKEN", "POUL|CUIT", "COOKED_MUTTON", "MOUT|CUIT",
                "BAKED_POTATO", "PATA|CUIT", "CARROT", "CAR|OTTE", "BEETROOT", "BET|RAVE", "PUMPKIN", "CIT|ROUI",
                "MELON_SLICE", "PAST|EQUE", "CACTUS", "CAC|TUS", "GLASS", "VER|RE", "CLAY_BALL", "ARGI|LE",
                "BRICK", "BRI|QUE", "SNOWBALL", "BOUL|NEIG", "LILY_PAD", "NENU|PHAR", "SWEET_BERRIES", "BAIE|SUCR",
                "KELP", "VAR|ECH", "BAMBOO", "BAM|BOU", "COCOA_BEANS", "CAC|AO", "GOLD_INGOT", "LING|OR",
                "COPPER_INGOT", "LING|CUIV", "REDSTONE", "RED|STON", "LAPIS_LAZULI", "LA|PIS",
                "RED_MUSHROOM", "CHAM|ROUG", "BROWN_MUSHROOM", "CHAM|BRUN", "MUSHROOM_STEW", "SOUP|CHAM",
                "BONE", "OS", "SPIDER_EYE", "OEIL|ARAI", "INK_SAC", "POCH|ENCR", "PAPER", "PAP|IER",
                "PAINTING", "TAB|LEAU", "ITEM_FRAME", "CAD|RE", "CAMPFIRE", "FEU|CAMP", "LANTERN", "LAN|TERN",
                "HAY_BLOCK", "BOTT|FOIN", "WHITE_BED", "LIT", "SMOKER", "FUM|OIR", "COMPOSTER", "COMP|OST",
                "BARREL", "TON|NEAU", "FLOWER_POT", "POT|FLEU", "MILK_BUCKET", "SEAU|LAIT", "TERRACOTTA", "TERR|CUIT",
                "SANDSTONE", "GRES", "MOSSY_COBBLESTONE", "PIER|MOUS", "DEEPSLATE", "ARDO|ISE", "TUFF", "TUF",
                // difficiles
                "DIAMOND", "DIAM|ANT", "OBSIDIAN", "OBSI|DIEN", "GOLDEN_APPLE", "POMM|OR", "CAKE", "GAT|EAU",
                "COMPASS", "BOUS|SOLE", "CLOCK", "HOR|LOGE", "MAP", "CAR|TE", "GUNPOWDER", "POU|DRE", "TNT", "TNT",
                "SLIME_BALL", "BOUL|SLIM", "LEAD", "LAIS|SE", "LAVA_BUCKET", "SEAU|LAVE", "COBWEB", "TOIL|ARAI",
                "PUMPKIN_PIE", "TART|CITR", "GLOW_INK_SAC", "ENCR|LUMI", "JUKEBOX", "JUKE|BOX",
                "IRON_CHESTPLATE", "PLAS|FER", "GOLDEN_CARROT", "CARO|OR", "EMERALD", "EME|RAUD",
                "AMETHYST_SHARD", "AME|THYS", "HONEYCOMB", "RAY|MIEL", "ENDER_PEARL", "PERL|ENDR",
                "BLAST_FURNACE", "HAUT|FOUR", "RAIL", "RAIL", "BOOKSHELF", "BIBL|IO",
                // case de secours
                "DIRT", "TER|RE"
        };
        for (int i = 0; i + 1 < t.length; i += 2) ABBR.put(t[i], t[i + 1]);
    }

    private NamespacedKey toolKey;

    /** Inscrits (en attente au lobby EEMINGO ou en partie) et leur mode, dans l'ordre d'arrivée. */
    private final Map<UUID, Mode> queue = new LinkedHashMap<>();
    /** Scoreboard personnel (sidebar + pseudos colorés) de chaque inscrit. */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** Décompte en cours par file (absent = pas de décompte). */
    private final Map<Mode, Integer> countdown = new EnumMap<>(Mode.class);
    private final Set<Mode> shortened = EnumSet.noneOf(Mode.class);
    private final Set<Mode> fullWarned = EnumSet.noneOf(Mode.class);
    /** Parties en cours par numéro (1 = monde « bingo », n = « bingo_n »). */
    private final Map<Integer, Game> games = new TreeMap<>();
    /** Numéros dont le monde est en cours de suppression. */
    private final Set<Integer> deleting = new HashSet<>();
    /** Joueur en partie → sa partie. */
    private final Map<UUID, Game> playerGame = new HashMap<>();
    /** Spectateur non inscrit → partie regardée (/bingo spectate). */
    private final Map<UUID, Game> spectatorGame = new HashMap<>();
    private String lastWinner;
    private String lastStatus;

    /** Icônes 16x16 des items (ARGB), chargées depuis icons/*.png du jar. */
    private final Map<Material, int[]> icons = new HashMap<>();

    /** Cartes décoratives du lobby (ids sauvés dans lobby.yml pour remettre le rendu au démarrage). */
    private final List<Integer> decorMaps = new ArrayList<>();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        toolKey = new NamespacedKey(this, "tool");
        saveDefaultConfig();
        loadIcons();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::scanInventories, 40L, 10L);
        Bukkit.getScheduler().runTask(this, () -> {
            // mondes de partie restés d'un arrêt brutal (bingo, bingo_2, ...) supprimés
            deleteLeftoverWorlds();
            loadDecorMaps();
        });
        reportStatus();
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(spectatorGame.keySet())) {
            Player s = Bukkit.getPlayer(id);
            if (s != null) sendToHub(s);
        }
        spectatorGame.clear();
        for (Player p : online(queue.keySet())) {
            Game g = playerGame.get(p.getUniqueId());
            if (g != null && g.bar != null) p.hideBossBar(g.bar);
            sendToHub(p);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        queue.clear();
        boards.clear();
        playerGame.clear();
        for (Game g : new ArrayList<>(games.values())) deleteWorld(g.worldName);
        games.clear();
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private String baseName() {
        return getConfig().getString("world-name", "bingo");
    }

    /** Monde de la partie n : n = 1 → « bingo », n ≥ 2 → « bingo_n ». */
    private String worldName(int n) {
        return n <= 1 ? baseName() : baseName() + "_" + n;
    }

    /** Numéro de partie d'un nom de monde (« bingo_3 » → 3, « bingo » → 1, autre → 0). */
    private int worldIndex(String name) {
        String base = baseName();
        if (name.equalsIgnoreCase(base)) return 1;
        if (!name.toLowerCase().startsWith(base.toLowerCase() + "_")) return 0;
        try {
            int n = Integer.parseInt(name.substring(base.length() + 1));
            return n >= 2 ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isGameWorld(World w) {
        return w != null && worldIndex(w.getName()) > 0;
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    /** Plus petit numéro libre (1..max-instances), 0 si toutes les parties sont occupées. */
    private int freeIndex() {
        for (int n = 1; n <= maxInstances(); n++) if (!games.containsKey(n) && !deleting.contains(n)) return n;
        return 0;
    }

    private int minPlayers() {
        return Math.max(1, cfg("min-players", 2));
    }

    private int maxPlayers() {
        return Math.max(minPlayers(), cfg("max-players", 16));
    }

    private boolean modeEnabled(Mode m) {
        List<String> list = getConfig().getStringList("modes");
        if (list.isEmpty()) return true;
        for (String s : list) if (s.equalsIgnoreCase(m.name())) return true;
        return false;
    }

    private Set<UUID> spectatorsOf(Game g) {
        Set<UUID> set = new LinkedHashSet<>();
        for (Map.Entry<UUID, Game> e : spectatorGame.entrySet()) if (e.getValue() == g) set.add(e.getKey());
        return set;
    }

    // ------------------------------------------------------------------ boucle (1x / seconde)

    private void tick() {
        boolean started = false;
        for (Mode m : Mode.values()) {
            List<UUID> waiting = waitingFor(m);
            Integer t = countdown.get(m);
            if (t == null) {
                if (waiting.size() >= minPlayers()) {
                    countdown.put(m, cfg("countdown", 30));
                    shortened.remove(m);
                    fullWarned.remove(m);
                    broadcastTo(waiting, "§eFile " + m.label + " prête : l'EEMINGO commence dans §6" + cfg("countdown", 30) + " s§e.");
                    reportStatus();
                }
                continue;
            }
            if (waiting.size() < minPlayers()) {
                countdown.remove(m);
                broadcastTo(waiting, "§cPas assez de joueurs, lancement annulé.");
                reportStatus();
                continue;
            }
            if (!shortened.contains(m) && waiting.size() >= maxPlayers() && t > 10) {
                shortened.add(m);
                t = 10;
                countdown.put(m, t);
                broadcastTo(waiting, "§eFile pleine : départ dans §610 s§e.");
            }
            if (t > 0) {
                for (Player p : online(waiting)) if (t <= 5) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                if (t == 1) broadcastTo(waiting, "§7Génération du monde...");
                countdown.put(m, t - 1);
                continue;
            }
            if (freeIndex() == 0) {
                if (fullWarned.add(m)) broadcastTo(waiting, "§cToutes les parties sont pleines : la tienne partira dès qu'une partie se termine.");
                continue;
            }
            if (started) continue; // une seule génération de monde par seconde
            started = true;
            countdown.remove(m);
            fullWarned.remove(m);
            startGame(m, waiting);
        }
        for (Game g : new ArrayList<>(games.values())) g.tick();
        updateSidebars();
    }

    // ------------------------------------------------------------------ inscription et lobby

    private void join(Player p, Mode m) {
        UUID id = p.getUniqueId();
        if (playerGame.containsKey(id)) {
            p.sendMessage(PREFIX + "§7Tu es déjà en partie.");
            return;
        }
        if (!modeEnabled(m)) {
            p.sendMessage(PREFIX + "§cLe mode " + m.label + " est désactivé.");
            return;
        }
        if (lobbyWorld() == null) {
            p.sendMessage(PREFIX + "§cLe lobby EEMINGO (monde " + lobbyName() + ") est introuvable, préviens un admin.");
            return;
        }
        boolean fresh = !queue.containsKey(id);
        spectatorGame.remove(id);
        queue.put(id, m);
        if (fresh) {
            createBoard(p);
            sendToLobby(p);
        }
        List<UUID> waiting = waitingFor(m);
        p.sendMessage(PREFIX + (fresh ? "§aInscrit" : "§aFile changée") + " : §6" + m.label + " §7(équipes de " + m.size + ", "
                + waiting.size() + "/" + minPlayers() + " joueurs min). §fLit rouge §7ou §f/bingo leave §7pour quitter.");
        List<UUID> others = new ArrayList<>(waiting);
        others.remove(id);
        broadcastTo(others, "§a" + p.getName() + " §7rejoint la file " + m.label + " (§f" + waiting.size() + "§7).");
        if (freeIndex() == 0) p.sendMessage(PREFIX + "§7Toutes les parties sont occupées, la tienne partira dès qu'une se termine.");
        reportStatus();
    }

    /** Inscrits en ligne qui attendent une partie de ce mode (hors partie en cours), dans l'ordre d'arrivée. */
    private List<UUID> waitingFor(Mode m) {
        List<UUID> list = new ArrayList<>();
        for (Map.Entry<UUID, Mode> e : queue.entrySet()) {
            if (e.getValue() == m && !playerGame.containsKey(e.getKey()) && Bukkit.getPlayer(e.getKey()) != null) list.add(e.getKey());
        }
        return list;
    }

    private void leave(Player p, boolean teleport) {
        UUID id = p.getUniqueId();
        if (queue.remove(id) == null) return;
        Game g = playerGame.remove(id);
        TeamColor t = g == null ? null : g.teams.remove(id);
        if (g != null && g.bar != null) p.hideBossBar(g.bar);
        for (Scoreboard b : boards.values()) {
            Team team = b.getEntryTeam(p.getName());
            if (team != null && team.getName().startsWith("bingo_")) team.removeEntry(p.getName());
        }
        Scoreboard own = boards.remove(id);
        if (own != null && p.getScoreboard() == own) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (teleport) sendToHub(p);
        else if (isHub(p.getWorld())) {
            // parti au hub par un autre moyen : on retire les objets du plugin. Ailleurs (skyblock...), on ne
            // touche à rien : Multiverse-Inventories a déjà chargé l'inventaire du monde d'arrivée.
            removeTools(p);
            if (p.getGameMode() == GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
            p.removePotionEffect(PotionEffectType.SATURATION);
        }
        if (g != null && t != null && g.state == State.PLAYING && g.teams.isEmpty()) {
            g.broadcast("§7Plus personne en jeu, partie terminée.");
            g.endGame(null, "Plus personne en jeu.", true);
            g.timer = 1;
        }
        reportStatus();
    }

    /**
     * Renvoi au hub : seulement depuis un monde de l'EEMINGO (salon, partie) ou le hub (jamais depuis le skyblock
     * ou un autre jeu). Téléportation d'abord (Multiverse-Inventories garde l'inventaire du monde quitté), puis
     * remise à zéro au hub.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
        if (hub != null) p.teleport(hub.getSpawnLocation());
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
    }

    /**
     * Salon d'attente : téléportation d'abord, puis inventaire vidé et lit rouge, une fois arrivé dans le salon
     * (vider avant aurait effacé l'inventaire du monde de départ, ex. skyblock).
     */
    private void sendToLobby(Player p) {
        Location spawn = lobbySpawn();
        if (spawn != null) p.teleport(spawn);
        if (!isLobby(p.getWorld())) return;
        resetPlayer(p, GameMode.ADVENTURE);
        p.getInventory().setItem(8, tool(Material.RED_BED, TOOL_QUIT, "Quitter l'EEMINGO (clic droit)", NamedTextColor.RED));
    }

    private boolean isHub(World w) {
        return w != null && w.getName().equals(getConfig().getString("hub-world", "lobby"));
    }

    /**
     * On ne touche à l'inventaire / l'état d'un joueur que s'il est dans un monde de l'EEMINGO (salon eemingo,
     * monde de partie) ou au hub : ailleurs (skyblock...), Multiverse-Inventories a déjà chargé l'inventaire de
     * ce monde-là.
     */
    private boolean mayTouch(Player p) {
        return p != null && p.isOnline() && (isGameWorld(p.getWorld()) || isLobby(p.getWorld()) || isHub(p.getWorld()));
    }

    private void resetPlayer(Player p, GameMode mode) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setItemOnCursor(null);
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
    }

    private String lobbyName() {
        return getConfig().getString("lobby-world", "eemingo");
    }

    private World lobbyWorld() {
        return Bukkit.getWorld(lobbyName());
    }

    private boolean isLobby(World w) {
        return w != null && w.getName().equalsIgnoreCase(lobbyName());
    }

    private int[] lobbyCenter() {
        return new int[]{cfg("lobby-center.x", 0), cfg("lobby-center.y", 100), cfg("lobby-center.z", 0)};
    }

    /** Point d'apparition du lobby : dans la salle face à la table, ou spawn du monde si la salle n'est pas construite. */
    private Location lobbySpawn() {
        World w = lobbyWorld();
        if (w == null) return null;
        int[] c = lobbyCenter();
        if (w.getBlockAt(c[0], c[1], c[2] + 5).getType().isAir()) return w.getSpawnLocation();
        return new Location(w, c[0] + 0.5, c[1] + 1, c[2] + 5.5, 180f, 0f);
    }

    // ------------------------------------------------------------------ lancement d'une partie

    private Game startGame(Mode m, List<UUID> waiting) {
        int index = freeIndex();
        if (index == 0) {
            broadcastTo(waiting, "§cToutes les parties sont pleines.");
            return null;
        }
        List<UUID> picked = new ArrayList<>(waiting.subList(0, Math.min(waiting.size(), maxPlayers())));
        List<Player> list = online(picked);
        if (list.isEmpty()) return null;
        Location spawn = createGameWorld(worldName(index));
        if (spawn == null) {
            broadcastTo(picked, "§cImpossible de créer le monde de jeu.");
            return null;
        }
        Game g = new Game(index, m, spawn);
        games.put(index, g);
        g.start(list);
        reportStatus();
        return g;
    }

    /** Crée le monde de jeu ; recommence avec une autre graine (3 essais max) si le centre est trop pauvre en biomes. Rend le point de départ. */
    private Location createGameWorld(String name) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            deleteWorld(name);
            World w = new WorldCreator(name)
                    .seed(ThreadLocalRandom.current().nextLong())
                    .type(WorldType.NORMAL)
                    .environment(World.Environment.NORMAL)
                    .createWorld();
            if (w == null) return null;
            int[] best = findCenter(w);   // {x, z, nombre de biomes} ou null
            if ((best != null && best[2] >= 3) || attempt == 3) {
                int x = best == null ? w.getSpawnLocation().getBlockX() : best[0];
                int z = best == null ? w.getSpawnLocation().getBlockZ() : best[1];
                getLogger().info("Monde " + name + " : centre " + x + " " + z + (best == null ? " (aucune terre trouvee)" : " (" + best[2] + " biomes)") + ", essai " + attempt + ".");
                return new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
            }
            getLogger().info("Monde " + name + " trop pauvre (essai " + attempt + "), nouvelle graine.");
        }
        return null;
    }

    /** Échantillonne une grille 5x5 sur ~600 blocs autour du spawn ; garde le point sur terre ferme le plus varié. */
    private int[] findCenter(World w) {
        Location s = w.getSpawnLocation();
        int[] best = null;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int x = s.getBlockX() + i * 150, z = s.getBlockZ() + j * 150;
                int y = w.getHighestBlockYAt(x, z);
                Block top = w.getBlockAt(x, y, z);
                if (top.isLiquid() || top.getType() == Material.ICE || top.getType() == Material.PACKED_ICE) continue;
                if (isWater(w.getBiome(x, y, z))) continue;
                int variety = biomeVariety(w, x, z);
                if (best == null || variety > best[2]) best = new int[]{x, z, variety};
            }
        }
        return best;
    }

    /** Nombre de biomes de surface différents dans un rayon de ~200 blocs (biomes calculés sans générer les chunks). */
    private int biomeVariety(World w, int cx, int cz) {
        Set<String> found = new HashSet<>();
        int y = Math.max(w.getSeaLevel() + 6, 68);
        for (int dx = -200; dx <= 200; dx += 40) {
            for (int dz = -200; dz <= 200; dz += 40) {
                if (dx * dx + dz * dz > 200 * 200) continue;
                String key = biomeKey(w.getBiome(cx + dx, y, cz + dz));
                if (key.contains("cave") || key.contains("deep_dark")) continue;
                found.add(key);
            }
        }
        return found.size();
    }

    @SuppressWarnings("deprecation")
    private static String biomeKey(Biome b) {
        return b == null ? "" : b.getKey().getKey();
    }

    private static boolean isWater(Biome b) {
        String k = biomeKey(b);
        return k.contains("ocean") || k.contains("river");
    }

    private PotionEffect saturation() {
        return new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, false, false, false);
    }

    private List<Material> pool(String key) {
        List<Material> list = new ArrayList<>();
        for (String s : getConfig().getStringList("items." + key)) {
            Material m = Material.matchMaterial(s);
            if (m != null && m.isItem() && !list.contains(m)) list.add(m);
            else if (m == null) getLogger().warning("Item inconnu dans la config : " + s);
        }
        Collections.shuffle(list);
        return list;
    }

    /** Toutes les 10 ticks : validation des cases, carte en deuxième main et saturation des joueurs en partie. */
    private void scanInventories() {
        for (Game g : new ArrayList<>(games.values())) g.scan();
    }

    /** Décharge (sans sauvegarder) et supprime le monde name ; les joueurs encore dedans vont au lobby EEMINGO ou au hub. */
    private void deleteWorld(String name) {
        World w = Bukkit.getWorld(name);
        Path folder = null;
        if (w != null) {
            for (Player p : w.getPlayers()) {
                if (queue.containsKey(p.getUniqueId())) sendToLobby(p);
                else sendToHub(p);
            }
            folder = w.getWorldFolder().toPath();
            if (!Bukkit.unloadWorld(w, false)) {
                getLogger().warning("Impossible de decharger le monde " + name + ".");
                return;
            }
        }
        Path container = Bukkit.getWorldContainer().toPath();
        deleteFolder(folder);
        deleteFolder(container.resolve(name));
        World main = Bukkit.getWorlds().get(0);
        deleteFolder(main.getWorldFolder().toPath().resolve("dimensions").resolve("minecraft").resolve(name));
    }

    /** Au démarrage : supprime tous les mondes de partie restés (bingo, bingo_2, ...), chargés ou sur le disque. */
    private void deleteLeftoverWorlds() {
        Set<String> names = new LinkedHashSet<>();
        for (World w : Bukkit.getWorlds()) if (worldIndex(w.getName()) > 0) names.add(w.getName());
        World main = Bukkit.getWorlds().get(0);
        for (Path dir : List.of(Bukkit.getWorldContainer().toPath(), main.getWorldFolder().toPath().resolve("dimensions").resolve("minecraft"))) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) if (Files.isDirectory(p) && worldIndex(p.getFileName().toString()) > 0) names.add(p.getFileName().toString());
            } catch (IOException e) {
                getLogger().warning("Lecture de " + dir + " impossible : " + e.getMessage());
            }
        }
        names.add(baseName());
        for (String n : names) deleteWorld(n);
    }

    private void deleteFolder(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // fichier verrouillé : il sera écrasé à la prochaine création
                }
            });
        } catch (IOException e) {
            getLogger().warning("Suppression de " + dir + " incomplete : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ une partie

    final class Game {
        final int index;
        final Mode mode;
        final String worldName;
        final World world;
        final Location spawn;
        final Map<UUID, TeamColor> teams = new LinkedHashMap<>();
        final Material[] grid = new Material[25];
        final TeamColor[] owner = new TeamColor[25];
        State state = State.PLAYING;
        int timer;
        BossBar bar;
        String winnerName;
        MapView gridView;
        GridRenderer gridRenderer;

        Game(int index, Mode mode, Location spawn) {
            this.index = index;
            this.mode = mode;
            this.spawn = spawn;
            this.world = spawn.getWorld();
            this.worldName = world.getName();
        }

        void start(List<Player> list) {
            world.setPVP(false);
            world.setTime(1000);
            world.setSpawnLocation(spawn);
            world.getWorldBorder().setCenter(spawn);
            world.getWorldBorder().setSize(cfg("border", 400));

            buildGrid();
            createGridMap();

            // équipes équilibrées : au moins 2 équipes dès 2 joueurs, 8 au maximum
            Collections.shuffle(list);
            int n = list.size();
            int nTeams = (n + mode.size - 1) / mode.size;
            if (n >= 2) nTeams = Math.max(2, nTeams);
            nTeams = Math.min(nTeams, TeamColor.values().length);
            for (int i = 0; i < n; i++) {
                UUID id = list.get(i).getUniqueId();
                teams.put(id, TeamColor.values()[i % nTeams]);
                playerGame.put(id, this);
            }
            colorNames(this);

            bar = BossBar.bossBar(Component.text("EEMINGO"), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            timer = cfg("game-time", 1200);
            updateBar();
            for (Player p : list) {
                TeamColor t = teams.get(p.getUniqueId());
                p.teleport(spawn);
                p.showBossBar(bar);
                later(() -> equip(p));
                p.showTitle(Title.title(Component.text("EEMINGO", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                        Component.text("Équipe " + t.label, t.color), // titre court : le détail (livre, membres) est dans le chat
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
                p.sendMessage(prefixC()
                        .append(Component.text("Ton équipe : " + t.label + " (" + String.join(", ", memberNames(t)) + ") - livre en slot 9 : la grille", t.color)));
            }
            getLogger().info("Partie " + index + " (" + worldName + ", " + mode.label + ") lancee avec " + n + " joueurs.");
            broadcast("§eC'est parti (" + mode.label + ", " + nTeams + " équipes) ! Clic droit avec le livre ou la carte pour la grille. Un item trouvé est bloqué pour les autres.");
        }

        void tick() {
            if (state == State.PLAYING) {
                timer--;
                updateBar();
                if (timer <= 0) timeUp();
            } else if (--timer <= 0) {
                finish();
            }
        }

        private void updateBar() {
            if (bar == null) return;
            int total = cfg("game-time", 1200);
            bar.progress(Math.max(0f, Math.min(1f, timer / (float) total)));
            TeamColor best = leader();
            bar.name(Component.text("EEMINGO  " + clock(timer), NamedTextColor.WHITE)
                    .append(best == null ? Component.empty()
                            : Component.text("   En tête : " + teamName(best) + " (" + count(best) + ")", best.color)));
        }

        private void equip(Player p) {
            if (!p.isOnline() || playerGame.get(p.getUniqueId()) != this || state != State.PLAYING) return;
            if (p.getWorld() != world) return; // kit seulement une fois arrivé dans le monde de la partie
            resetPlayer(p, GameMode.SURVIVAL);
            p.getInventory().setItemInOffHand(mapItem());
            p.getInventory().setItem(8, bookItem());
            p.addPotionEffect(saturation());
        }

        private void buildGrid() {
            List<Material> easy = pool("easy"), medium = pool("medium"), hard = pool("hard");
            int nEasy = Math.min(easy.size(), cfg("easy-count", 8));
            int nHard = Math.min(hard.size(), cfg("hard-count", 6));
            List<Material> chosen = new ArrayList<>();
            chosen.addAll(easy.subList(0, nEasy));
            chosen.addAll(hard.subList(0, nHard));
            for (Material m : medium) {
                if (chosen.size() >= 25) break;
                chosen.add(m);
            }
            for (Material m : easy.subList(nEasy, easy.size())) if (chosen.size() < 25) chosen.add(m);
            Collections.shuffle(chosen);
            for (int i = 0; i < 25; i++) {
                grid[i] = i < chosen.size() ? chosen.get(i) : Material.DIRT;
                owner[i] = null;
            }
        }

        void scan() {
            if (state != State.PLAYING) return;
            for (Player p : online(teams.keySet())) {
                TeamColor t = teams.get(p.getUniqueId());
                if (t == null || p.getWorld() != world) continue;
                if (!p.isDead()) {
                    ensureMap(p, this);
                    if (!p.hasPotionEffect(PotionEffectType.SATURATION)) p.addPotionEffect(saturation());
                }
                for (int i = 0; i < 25; i++) {
                    if (owner[i] == null && hasItem(p, grid[i])) claim(i, t, p);
                    if (state != State.PLAYING) return;
                }
            }
        }

        private void claim(int i, TeamColor t, Player p) {
            owner[i] = t;
            Component item = Component.translatable(grid[i]).color(NamedTextColor.WHITE);
            Component msg = prefixC()
                    .append(Component.text(p.getName(), t.color))
                    .append(Component.text(" a trouvé ", NamedTextColor.GRAY)).append(item)
                    .append(Component.text(" : bloqué pour les autres. ", NamedTextColor.GRAY))
                    .append(Component.text(t.label + " " + count(t) + "/25", t.color));
            for (Player o : online(teams.keySet())) {
                o.sendMessage(msg);
                o.playSound(o.getLocation(), teams.get(o.getUniqueId()) == t ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 1.4f);
            }
            for (Player o : online(spectatorsOf(this))) o.sendMessage(msg);
            if (gridRenderer != null) gridRenderer.dirty();
            refreshOpenGrids();
            updateBar();
            if (hasLine(t)) endGame(t, teamName(t) + " a complété une ligne !", true);
        }

        private boolean hasLine(TeamColor t) {
            for (int r = 0; r < 5; r++) {
                boolean row = true, col = true;
                for (int c = 0; c < 5; c++) {
                    row &= owner[r * 5 + c] == t;
                    col &= owner[c * 5 + r] == t;
                }
                if (row || col) return true;
            }
            boolean d1 = true, d2 = true;
            for (int k = 0; k < 5; k++) {
                d1 &= owner[k * 5 + k] == t;
                d2 &= owner[k * 5 + (4 - k)] == t;
            }
            return d1 || d2;
        }

        List<String> memberNames(TeamColor t) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<UUID, TeamColor> e : teams.entrySet()) {
                if (e.getValue() != t) continue;
                String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
                names.add(n == null ? "?" : n);
            }
            return names;
        }

        /** Nom affiché d'une équipe : le pseudo si elle n'a qu'un joueur, sinon « Equipe Couleur ». */
        String teamName(TeamColor t) {
            List<String> names = memberNames(t);
            return names.size() == 1 ? names.get(0) : "Équipe " + t.label;
        }

        int count(TeamColor t) {
            int n = 0;
            for (TeamColor o : owner) if (o == t) n++;
            return n;
        }

        TeamColor leader() {
            Set<TeamColor> used = new HashSet<>(teams.values());
            return used.stream().filter(t -> count(t) > 0).max(Comparator.comparingInt(this::count)).orElse(null);
        }

        /** Équipes à égalité à la fin du temps (pour le sous-titre). */
        List<TeamColor> tieTeams = List.of();

        private void timeUp() {
            Set<TeamColor> used = new HashSet<>(teams.values());
            int best = used.stream().mapToInt(this::count).max().orElse(0);
            List<TeamColor> top = used.stream().filter(t -> count(t) == best).toList();
            if (best == 0 || top.size() > 1) { tieTeams = top; endGame(null, "Temps écoulé : égalité (" + best + " items).", true); }
            else endGame(top.get(0), teamName(top.get(0)) + " a le plus d'items (" + best + ") à la fin du temps !", true);
        }

        /** rewards = false pour un arrêt admin : pas de récompenses EemiCore. */
        void endGame(TeamColor winner, String reason, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            if (rewards) {
                for (Map.Entry<UUID, TeamColor> en : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p == null) continue;
                    eemi("reward " + p.getName() + " bingo play");
                    if (en.getValue() == winner) eemi("reward " + p.getName() + " bingo win");
                }
            }
            timer = cfg("end-time", 10);
            winnerName = winner == null ? "Égalité" : teamName(winner);
            lastWinner = winnerName;
            boolean tie = winner == null && rewards && !tieTeams.isEmpty();
            Component title = winner == null
                    ? (tie ? Component.text("Égalité !", NamedTextColor.YELLOW) : Component.text("Fin de partie", NamedTextColor.GRAY)).decorate(TextDecoration.BOLD)
                    : Component.text(teamName(winner), winner.color).decorate(TextDecoration.BOLD);
            // Titre court (nom seul) ; la raison complète part dans le chat juste après
            String subText;
            if (winner != null) subText = "remporte la partie !";
            else if (tie) {
                List<String> names = new ArrayList<>();
                for (TeamColor t : tieTeams) names.add(teamName(t));
                String joined = String.join(", ", names);
                subText = joined.length() <= 38 ? joined : tieTeams.size() + " gagnants";
            } else subText = reason.length() > 38 ? "Fin de partie" : reason;
            Component sub = Component.text(subText, winner == null ? (tie ? NamedTextColor.YELLOW : NamedTextColor.GRAY) : NamedTextColor.GOLD);
            Set<UUID> audience = new LinkedHashSet<>(teams.keySet());
            audience.addAll(spectatorsOf(this));
            for (Player p : online(audience)) {
                p.showTitle(Title.title(title, sub));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (bar != null) p.hideBossBar(bar);
            }
            StringBuilder sb = new StringBuilder("§6" + reason + " §7Scores :");
            for (TeamColor t : new LinkedHashSet<>(teams.values())) sb.append(" §f").append(t.label).append(" ").append(count(t));
            broadcast(sb.toString());
            getLogger().info("Fin de la partie " + index + " (" + worldName + ") : " + winnerName);
            reportStatus();
        }

        /** Fin de partie : retour au lobby EEMINGO (les joueurs restent dans leur file), suppression du monde de cette partie. */
        void finish() {
            removeColorTeams(this);
            List<UUID> ids = new ArrayList<>(teams.keySet());
            games.remove(index);
            deleting.add(index);
            for (UUID id : ids) {
                playerGame.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                if (bar != null) p.hideBossBar(bar);
                Mode m = queue.get(id);
                if (m == null) continue;
                sendToLobby(p);
                p.sendMessage(PREFIX + "§7Retour au lobby. Tu restes dans la file §6" + m.label + "§7.");
            }
            teams.clear();
            bar = null;
            gridView = null;
            gridRenderer = null;
            for (UUID id : spectatorsOf(this)) {
                spectatorGame.remove(id);
                Player s = Bukkit.getPlayer(id);
                if (s != null) sendToHub(s);
            }
            reportStatus();
            Bukkit.getScheduler().runTaskLater(EemiBingo.this, () -> {
                deleteWorld(worldName);
                deleting.remove(index);
            }, 40L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, "Partie arrêtée par un admin.", false);
            if (state == State.ENDING && games.get(index) == this) finish();
        }

        private void createGridMap() {
            gridView = Bukkit.createMap(world);
            for (MapRenderer r : new ArrayList<>(gridView.getRenderers())) gridView.removeRenderer(r);
            gridView.setTrackingPosition(false);
            gridView.setUnlimitedTracking(false);
            gridRenderer = new GridRenderer(this);
            gridView.addRenderer(gridRenderer);
        }

        ItemStack mapItem() {
            ItemStack it = tool(Material.FILLED_MAP, TOOL_GRID, "Grille EEMINGO (clic droit)", NamedTextColor.GREEN);
            if (gridView != null && it.getItemMeta() instanceof MapMeta meta) {
                meta.setMapView(gridView);
                it.setItemMeta(meta);
            }
            return it;
        }

        /** Message aux joueurs et spectateurs de cette partie. */
        void broadcast(String msg) {
            Set<UUID> audience = new LinkedHashSet<>(teams.keySet());
            audience.addAll(spectatorsOf(this));
            broadcastTo(audience, msg);
        }
    }

    // ------------------------------------------------------------------ grille (inventaire)

    /** Partie dont le joueur voit la grille : la sienne, sinon celle qu'il regarde. */
    private Game viewedGame(Player p) {
        Game g = playerGame.get(p.getUniqueId());
        return g != null ? g : spectatorGame.get(p.getUniqueId());
    }

    private void openGrid(Player p) {
        Game g = viewedGame(p);
        if (g == null) {
            p.sendMessage(PREFIX + "§7Pas de partie en cours.");
            return;
        }
        GridHolder holder = new GridHolder();
        holder.game = g;
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("Grille EEMINGO"));
        holder.inv = inv;
        fillGrid(inv, g, g.teams.get(p.getUniqueId()));
        p.openInventory(inv);
    }

    private void fillGrid(Inventory inv, Game g, TeamColor viewer) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int s = 0; s < 45; s++) inv.setItem(s, filler);
        for (int i = 0; i < 25; i++) {
            int slot = (i / 5) * 9 + 2 + (i % 5);
            TeamColor o = g.owner[i];
            ItemStack it = new ItemStack(o == null ? g.grid[i] : o.pane);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.translatable(g.grid[i]).color(o == null ? NamedTextColor.WHITE : o.color)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(o == null
                    ? Component.text("À trouver", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    : Component.text((o == viewer ? "Trouvé par ton équipe" : "Pris par " + g.teamName(o)), o.color)
                    .decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(meta);
            inv.setItem(slot, it);
        }
    }

    private void refreshOpenGrids() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof GridHolder h && h.game != null)
                fillGrid(h.getInventory(), h.game, h.game.teams.get(p.getUniqueId()));
        }
    }

    // ------------------------------------------------------------------ grille (carte en deuxième main)

    /** Charge l'icône de chaque item des réserves et logue celles qui manquent (repli : abréviation). */
    private void loadIcons() {
        icons.clear();
        List<String> missing = new ArrayList<>();
        for (String key : List.of("easy", "medium", "hard")) {
            for (String name : getConfig().getStringList("items." + key)) {
                Material m = Material.matchMaterial(name);
                if (m == null || icons.containsKey(m)) continue;
                int[] px = readIcon(m);
                if (px == null) missing.add(m.name());
                else icons.put(m, px);
            }
        }
        getLogger().info(icons.size() + " icones chargees" + (missing.isEmpty() ? "." : ", manquantes (abreviation affichee) : " + String.join(", ", missing)));
    }

    private int[] readIcon(Material m) {
        try (InputStream in = getResource("icons/" + m.name().toLowerCase() + ".png")) {
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            int[] px = new int[256];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                px[y * 16 + x] = img.getRGB(x * img.getWidth() / 16, y * img.getHeight() / 16);
            return px;
        } catch (IOException e) {
            return null;
        }
    }

    private ItemStack bookItem() {
        return tool(Material.BOOK, TOOL_BOOK, "Voir la grille (clic droit)", NamedTextColor.GREEN);
    }

    /** L'item est-il dans l'inventaire ? Les objets du plugin sont ignorés (le livre « Grille » n'est pas un vrai livre). */
    private boolean hasItem(Player p, Material m) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == m && !isTool(it)) return true;
        }
        return false;
    }

    /** La carte est-elle celle de la grille de cette partie ? */
    @SuppressWarnings("deprecation")
    private boolean isMapOf(ItemStack it, Game g) {
        if (toolType(it) != TOOL_GRID || g.gridView == null) return false;
        return it.getItemMeta() instanceof MapMeta meta && meta.hasMapView() && meta.getMapView() != null
                && meta.getMapView().getId() == g.gridView.getId();
    }

    /** Remet la carte de SA partie en deuxième main et le livre « Grille » en slot 8 s'ils ont disparu, supprime les copies ailleurs. */
    private void ensureMap(Player p, Game g) {
        PlayerInventory inv = p.getInventory();
        for (int s = 0; s < inv.getSize(); s++) {
            byte type = toolType(inv.getItem(s));
            if (type != 0 && !(s == 40 && type == TOOL_GRID) && !(s == 8 && type == TOOL_BOOK)) inv.setItem(s, null);
        }
        if (isTool(p.getItemOnCursor())) p.setItemOnCursor(null);
        ItemStack off = inv.getItemInOffHand();
        if (!isMapOf(off, g)) {
            if (off != null && !off.getType().isAir() && !isTool(off)) {
                inv.setItemInOffHand(null);
                for (ItemStack left : inv.addItem(off).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
            inv.setItemInOffHand(g.mapItem());
        }
        ItemStack slot8 = inv.getItem(8);
        if (toolType(slot8) != TOOL_BOOK) {
            inv.setItem(8, bookItem());
            if (slot8 != null && !slot8.getType().isAir() && !isTool(slot8)) {
                for (ItemStack left : inv.addItem(slot8).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    /** Rendu de la grille d'une partie : un canevas par joueur (rendu contextuel), redessiné seulement après une prise. */
    private final class GridRenderer extends MapRenderer {
        private final Game game;
        private final Set<Player> drawn = Collections.newSetFromMap(new WeakHashMap<>());

        GridRenderer(Game game) {
            super(true);
            this.game = game;
        }

        void dirty() {
            drawn.clear();
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            if (!drawn.add(player)) return;
            drawGrid(canvas, game, game.teams.get(player.getUniqueId()));
        }
    }

    private static final Color MAP_BG = new Color(58, 42, 28);
    private static final Color CELL_FREE = new Color(185, 185, 185);
    private static final Color CELL_FREE_IN = new Color(92, 80, 66);
    private static final Color CELL_MINE = new Color(60, 200, 60);
    private static final Color CELL_MINE_IN = new Color(40, 100, 40);

    /**
     * Carte 128x128 : bandeau titre (9 px) puis 25 cases de 23x22. Chaque case = bordure de 3 px à la couleur
     * de l'état (gris libre, vert ton équipe, couleur de l'équipe adverse) et icône 16x16 de l'item au centre.
     * Case prise par une autre équipe : icône voilée et croix à sa couleur.
     */
    private void drawGrid(MapCanvas c, Game g, TeamColor viewer) {
        fill(c, 0, 0, 128, 128, MAP_BG);
        String title = "EEMINGO";
        drawString(c, (128 - textWidth(title)) / 2, 1, title, new Color(120, 220, 110));
        for (int i = 0; i < 25; i++) {
            int x = 4 + (i % 5) * 24, y = 10 + (i / 5) * 23;
            TeamColor o = g.owner[i];
            Material item = g.grid[i] == null ? Material.DIRT : g.grid[i];
            boolean other = o != null && o != viewer;
            Color border = o == null ? CELL_FREE : o == viewer ? CELL_MINE : o.awt();
            Color inner = o == null ? CELL_FREE_IN : o == viewer ? CELL_MINE_IN : shade(o.awt(), 0.35);
            fill(c, x, y, 23, 22, border);
            fill(c, x + 3, y + 3, 17, 16, inner);
            int[] icon = icons.get(item);
            if (icon != null) {
                for (int py = 0; py < 16; py++) for (int px = 0; px < 16; px++) {
                    int argb = icon[py * 16 + px];
                    if ((argb >>> 24) < 128) continue;
                    Color col = new Color(argb & 0xFFFFFF);
                    c.setPixelColor(x + 4 + px, y + 3 + py, other ? shade(col, 0.4) : col);
                }
            } else {
                // repli : abréviation française
                String[] lines = abbr(item);
                Color fg = other ? new Color(150, 150, 150) : Color.WHITE;
                int ly = lines.length == 1 ? y + 7 : y + 3;
                for (int k = 0; k < lines.length; k++) {
                    String s = fit(lines[k], 17);
                    drawString(c, x + 3 + (17 - textWidth(s)) / 2, ly + k * 8, s, fg);
                }
            }
            if (other) {
                // croix à la couleur de l'équipe qui a pris la case
                Color cross = o.awt();
                for (int k = 0; k < 16; k++) {
                    c.setPixelColor(x + 4 + k, y + 3 + k, cross);
                    c.setPixelColor(x + 4 + 15 - k, y + 3 + k, cross);
                }
            }
        }
    }

    private static Color shade(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }

    private static String[] abbr(Material m) {
        String s = ABBR.get(m.name());
        if (s == null) {
            // repli : début du nom anglais
            String n = m.name().replace("_", "");
            s = n.substring(0, Math.min(4, n.length())) + (n.length() > 4 ? "|" + n.substring(4, Math.min(8, n.length())) : "");
        }
        return s.split("\\|");
    }

    private static void fill(MapCanvas c, int x, int y, int w, int h, Color col) {
        for (int i = x; i < x + w && i < 128; i++) for (int j = y; j < y + h && j < 128; j++) c.setPixelColor(i, j, col);
    }

    private static int textWidth(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) {
            MapFont.CharacterSprite sp = MinecraftFont.Font.getChar(ch);
            w += (sp == null ? 4 : sp.getWidth()) + 1;
        }
        return Math.max(0, w - 1);
    }

    private static String fit(String s, int max) {
        while (s.length() > 1 && textWidth(s) > max) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static void drawString(MapCanvas c, int x, int y, String s, Color col) {
        for (char ch : s.toCharArray()) {
            MapFont.CharacterSprite sp = MinecraftFont.Font.getChar(ch);
            if (sp == null) { x += 5; continue; }
            for (int r = 0; r < sp.getHeight(); r++) {
                for (int k = 0; k < sp.getWidth(); k++) {
                    int px = x + k, py = y + r;
                    if (sp.get(r, k) && px >= 0 && px < 128 && py >= 0 && py < 128) c.setPixelColor(px, py, col);
                }
            }
            x += sp.getWidth() + 1;
        }
    }

    // ------------------------------------------------------------------ scoreboard latéral et pseudos colorés

    private void createBoard(Player p) {
        Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = b.registerNewObjective("eemingo", Criteria.DUMMY,
                Component.text("EEMINGO", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            o.numberFormat(NumberFormat.blank());
        } catch (Throwable ignored) {
            // numéros visibles si non supporté
        }
        boards.put(p.getUniqueId(), b);
        p.setScoreboard(b);
    }

    private void updateSidebars() {
        for (Player p : online(queue.keySet())) {
            UUID id = p.getUniqueId();
            Mode m = queue.get(id);
            Game g = playerGame.get(id);
            TeamColor t = g == null ? null : g.teams.get(id);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.empty());
            if (g != null && t != null) {
                lines.add(Component.text("Équipe : ", NamedTextColor.GRAY).append(Component.text(t.label, t.color)));
                lines.add(Component.text("Items : ", NamedTextColor.GRAY).append(Component.text(g.count(t) + "/25", NamedTextColor.WHITE)));
                TeamColor lead = g.leader();
                lines.add(Component.text("En tête : ", NamedTextColor.GRAY).append(lead == null
                        ? Component.text("personne", NamedTextColor.WHITE)
                        : Component.text(g.teamName(lead) + " (" + g.count(lead) + ")", lead.color)));
                lines.add(Component.empty());
                if (g.state == State.PLAYING) {
                    lines.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(g.timer), NamedTextColor.YELLOW)));
                } else {
                    lines.add(Component.text("Gagnant : ", NamedTextColor.GRAY).append(Component.text(g.winnerName == null ? "-" : g.winnerName, NamedTextColor.GOLD)));
                    lines.add(Component.text("Lobby dans " + Math.max(0, g.timer) + " s", NamedTextColor.GRAY));
                }
                lines.add(Component.empty());
                lines.add(Component.text("Mode " + g.mode.label + " - partie " + g.index, NamedTextColor.DARK_GREEN));
            } else {
                lines.add(Component.text("Mode : ", NamedTextColor.GRAY).append(Component.text(m.label + " (équipes de " + m.size + ")", NamedTextColor.WHITE)));
                lines.add(Component.text("File : ", NamedTextColor.GRAY).append(Component.text(waitingFor(m).size() + " / " + minPlayers() + " min", NamedTextColor.WHITE)));
                lines.add(Component.empty());
                Integer c = countdown.get(m);
                if (c != null && c > 0) {
                    lines.add(Component.text("Départ dans " + c + " s", NamedTextColor.GOLD));
                } else if (c != null) {
                    lines.add(Component.text(freeIndex() == 0 ? "Attente d'une partie libre" : "Lancement...", NamedTextColor.YELLOW));
                } else {
                    lines.add(Component.text("En attente de joueurs", NamedTextColor.YELLOW));
                }
                if (!games.isEmpty()) lines.add(Component.text(games.size() + " partie(s) en cours", NamedTextColor.GRAY));
                if (lastWinner != null) lines.add(Component.text("Dernier gagnant : ", NamedTextColor.GRAY).append(Component.text(lastWinner, NamedTextColor.GOLD)));
                lines.add(Component.empty());
                lines.add(Component.text("Lit rouge : quitter", NamedTextColor.DARK_GRAY));
            }
            setLines(id, lines);
        }
    }

    /** Une équipe par ligne, entrée invisible (code couleur) et texte dans le prefix. */
    private void setLines(UUID id, List<Component> lines) {
        Scoreboard b = boards.get(id);
        if (b == null) return;
        Objective o = b.getObjective("eemingo");
        if (o == null) return;
        String hex = "0123456789abcdef";
        for (int i = 0; i < 15; i++) {
            String entry = "§" + hex.charAt(i) + "§r";
            Team team = b.getTeam("eem_l" + i);
            if (i < lines.size()) {
                if (team == null) {
                    team = b.registerNewTeam("eem_l" + i);
                    team.addEntry(entry);
                }
                team.prefix(lines.get(i));
                o.getScore(entry).setScore(lines.size() - i);
            } else if (team != null) {
                b.resetScores(entry);
            }
        }
    }

    /** Pseudos colorés : les équipes d'une partie sur le scoreboard de chacun de ses joueurs. */
    private void colorNames(Game g) {
        for (UUID id : g.teams.keySet()) {
            Scoreboard b = boards.get(id);
            if (b != null) applyColors(b, g);
        }
    }

    private void applyColors(Scoreboard board, Game g) {
        for (Map.Entry<UUID, TeamColor> e : g.teams.entrySet()) {
            String player = Bukkit.getOfflinePlayer(e.getKey()).getName();
            if (player == null) continue;
            TeamColor t = e.getValue();
            String name = "bingo_" + t.name().toLowerCase();
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
                team.color(t.color);
                team.prefix(Component.text("[" + t.label + "] ", t.color));
            }
            team.addEntry(player);
        }
    }

    private void removeColorTeams(Game g) {
        for (UUID id : g.teams.keySet()) {
            Scoreboard board = boards.get(id);
            if (board == null) continue;
            for (TeamColor t : TeamColor.values()) {
                Team team = board.getTeam("bingo_" + t.name().toLowerCase());
                if (team != null) team.unregister();
            }
        }
    }

    // ------------------------------------------------------------------ salle du lobby (/bingo buildlobby)

    private void buildLobby(CommandSender sender) {
        World w = lobbyWorld();
        if (w == null) {
            sender.sendMessage(PREFIX + "§cMonde " + lobbyName() + " introuvable : crée-le d'abord (monde vide, Multiverse).");
            return;
        }
        int[] c = lobbyCenter();
        int cx = c[0], cy = c[1], cz = c[2];
        // anciens cadres retirés
        for (Entity e : w.getNearbyEntities(new Location(w, cx + 0.5, cy + 4, cz + 0.5), 10, 6, 10)) {
            if (e instanceof ItemFrame) e.remove();
        }
        decorMaps.clear();

        // coque : sol, plafond à poutres, intérieur vidé
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                boolean ring = Math.abs(x) == 7 || Math.abs(z) == 7;
                set(w, cx + x, cy, cz + z, ring && Math.abs(x) <= 7 && Math.abs(z) <= 7 ? "minecraft:stripped_dark_oak_wood" : "minecraft:dark_oak_planks");
                String ceil = "minecraft:dark_oak_planks";
                if (z == -4 || z == 0 || z == 4) ceil = "minecraft:stripped_dark_oak_log[axis=x]";
                else if (x == -4 || x == 0 || x == 4) ceil = "minecraft:stripped_dark_oak_log[axis=z]";
                set(w, cx + x, cy + 7, cz + z, ceil);
                if (Math.abs(x) <= 7 && Math.abs(z) <= 7) for (int h = 1; h <= 6; h++) set(w, cx + x, cy + h, cz + z, "minecraft:air");
            }
        }
        // coins
        for (int sx = -8; sx <= 8; sx += 16) for (int sz = -8; sz <= 8; sz += 16)
            for (int h = 1; h <= 6; h++) set(w, cx + sx, cy + h, cz + sz, "minecraft:dark_oak_log[axis=y]");

        // murs : piliers, sections bibliothèque et sections « mur des cartes »
        BlockFace[] faces = {BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST};
        Random rnd = new Random(cx * 31L + cz);
        for (int wall = 0; wall < 4; wall++) {
            BlockFace face = faces[wall];
            String facing = face.name().toLowerCase();
            String axis = wall < 2 ? "x" : "z";
            for (int p = -7; p <= 7; p++) {
                int x = wall < 2 ? p : (wall == 2 ? -8 : 8);
                int z = wall < 2 ? (wall == 0 ? -8 : 8) : p;
                int ix = x + face.getModX(), iz = z + face.getModZ();
                boolean pillar = p == -4 || p == 0 || p == 4;
                boolean maps = Math.abs(p) >= 5;
                for (int h = 1; h <= 6; h++) {
                    String b;
                    if (pillar) b = "minecraft:dark_oak_log[axis=y]";
                    else if (h == 6) b = "minecraft:stripped_dark_oak_log[axis=" + axis + "]";
                    else if (maps) b = h <= 2 ? "minecraft:bookshelf" : h <= 4 ? "minecraft:spruce_planks" : "minecraft:dark_oak_planks";
                    else if (h % 2 == 0) b = chiseled(facing, rnd);
                    else b = "minecraft:bookshelf";
                    set(w, cx + x, cy + h, cz + z, b);
                }
                if (maps) {
                    for (int h = 3; h <= 4; h++) frame(w, cx + ix, cy + h, cz + iz, face, decorMap(w));
                }
            }
        }
        // porte décorative au sud
        set(w, cx, cy + 1, cz + 8, "minecraft:dark_oak_door[facing=north,half=lower,hinge=left]");
        set(w, cx, cy + 2, cz + 8, "minecraft:dark_oak_door[facing=north,half=upper,hinge=left]");

        // tapis autour de la table
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 2; z++) {
            boolean border = Math.abs(x) == 3 || z == -3 || z == 2;
            set(w, cx + x, cy + 1, cz + z, border ? "minecraft:brown_carpet" : "minecraft:green_carpet");
        }
        // grande table des cartes
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 0; z++) set(w, cx + x, cy + 1, cz + z, "minecraft:cartography_table");
        set(w, cx - 1, cy + 2, cz - 1, "minecraft:lantern[hanging=false]");
        set(w, cx + 1, cy + 2, cz, "minecraft:lantern[hanging=false]");
        frame(w, cx, cy + 2, cz - 1, BlockFace.UP, decorMap(w));
        frame(w, cx, cy + 2, cz, BlockFace.UP, decorMap(w));
        frame(w, cx - 1, cy + 2, cz, BlockFace.UP, new ItemStack(Material.COMPASS));
        frame(w, cx + 1, cy + 2, cz - 1, BlockFace.UP, new ItemStack(Material.SPYGLASS));

        // lustres : lanternes suspendues à des chaînes
        for (int sx = -4; sx <= 4; sx += 8) for (int sz = -4; sz <= 4; sz += 8) {
            set(w, cx + sx, cy + 6, cz + sz, "minecraft:iron_chain[axis=y]");
            set(w, cx + sx, cy + 5, cz + sz, "minecraft:lantern[hanging=true]");
        }
        // lumière invisible pour une salle bien éclairée
        int[][] lights = {{0, 5, 4}, {0, 5, -4}, {4, 5, 0}, {-4, 5, 0}, {0, 6, 0}, {5, 3, 5}, {-5, 3, 5}, {5, 3, -5}, {-5, 3, -5}};
        for (int[] l : lights) set(w, cx + l[0], cy + l[1], cz + l[2], "minecraft:light[level=15]");

        // coins : tonneaux et plantes en pot
        String[] plants = {"minecraft:potted_fern", "minecraft:potted_azalea_bush", "minecraft:potted_azalea_bush", "minecraft:potted_fern"};
        int k = 0;
        for (int sx = -7; sx <= 7; sx += 14) for (int sz = -7; sz <= 7; sz += 14) {
            set(w, cx + sx, cy + 1, cz + sz, "minecraft:barrel[facing=up]");
            set(w, cx + sx, cy + 2, cz + sz, plants[k++]);
        }
        set(w, cx - 6, cy + 1, cz - 7, "minecraft:potted_spruce_sapling");
        set(w, cx + 6, cy + 1, cz - 7, "minecraft:potted_spruce_sapling");
        // pupitres et bancs le long des murs est et ouest
        set(w, cx - 7, cy + 1, cz, "minecraft:lectern[facing=east]");
        set(w, cx + 7, cy + 1, cz, "minecraft:lectern[facing=west]");
        for (int z : new int[]{-3, -2, 2, 3}) {
            set(w, cx - 7, cy + 1, cz + z, "minecraft:dark_oak_stairs[facing=west]");
            set(w, cx + 7, cy + 1, cz + z, "minecraft:dark_oak_stairs[facing=east]");
        }
        // panneau titre sur le mur nord
        set(w, cx, cy + 3, cz - 7, "minecraft:dark_oak_wall_sign[facing=south]");
        if (w.getBlockAt(cx, cy + 3, cz - 7).getState() instanceof Sign sign) {
            SignSide side = sign.getSide(Side.FRONT);
            side.line(0, Component.text("EEMINGO", NamedTextColor.DARK_GREEN).decorate(TextDecoration.BOLD));
            side.line(1, Component.text("Salle des cartes"));
            side.line(2, Component.text("Le Bingo"));
            side.line(3, Component.text("d'EEMICRAFT"));
            sign.setWaxed(true);
            sign.update(true, false);
        }

        Location spawn = new Location(w, cx + 0.5, cy + 1, cz + 5.5, 180f, 0f);
        w.setSpawnLocation(spawn);
        try {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setTime(6000);
        } catch (Throwable t) {
            getLogger().warning("Regles du monde lobby non appliquees : " + t.getMessage());
        }
        saveDecorMaps();
        sender.sendMessage(PREFIX + "§aSalle EEMINGO construite autour de " + cx + " " + cy + " " + cz + " (" + decorMaps.size() + " cartes décoratives).");
    }

    private String chiseled(String facing, Random rnd) {
        StringBuilder sb = new StringBuilder("minecraft:chiseled_bookshelf[facing=" + facing);
        for (int s = 0; s < 6; s++) sb.append(",slot_").append(s).append("_occupied=").append(rnd.nextInt(4) != 0);
        return sb.append("]").toString();
    }

    private void set(World w, int x, int y, int z, String data) {
        try {
            BlockData bd = Bukkit.createBlockData(data);
            w.getBlockAt(x, y, z).setBlockData(bd, false);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Bloc invalide " + data + " : " + e.getMessage());
        }
    }

    private void frame(World w, int x, int y, int z, BlockFace face, ItemStack item) {
        try {
            w.spawn(new Location(w, x + 0.5, y + 0.5, z + 0.5), ItemFrame.class, f -> {
                f.setFacingDirection(face, true);
                f.setItem(item, false);
                f.setItemDropChance(0f);
                f.setFixed(true);
                f.setInvulnerable(true);
            });
        } catch (IllegalArgumentException e) {
            getLogger().warning("Cadre impossible en " + x + " " + y + " " + z + " : " + e.getMessage());
        }
    }

    /** Carte décorative (vieille carte au trésor dessinée par un renderer). */
    private ItemStack decorMap(World w) {
        MapView v = Bukkit.createMap(w);
        attachDecor(v);
        decorMaps.add(v.getId());
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        if (it.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(v);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void attachDecor(MapView v) {
        for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.addRenderer(new DecorRenderer(v.getId()));
    }

    private File lobbyFile() {
        return new File(getDataFolder(), "lobby.yml");
    }

    private void saveDecorMaps() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("decor-maps", decorMaps);
        try {
            y.save(lobbyFile());
        } catch (IOException e) {
            getLogger().warning("Impossible d'ecrire lobby.yml : " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void loadDecorMaps() {
        if (!lobbyFile().exists()) return;
        decorMaps.clear();
        for (int id : YamlConfiguration.loadConfiguration(lobbyFile()).getIntegerList("decor-maps")) {
            MapView v = Bukkit.getMap(id);
            if (v == null) continue;
            attachDecor(v);
            decorMaps.add(id);
        }
    }

    /** Dessine une vieille carte : parchemin, îles, chemin pointillé et croix. */
    private static final class DecorRenderer extends MapRenderer {
        private final long seed;
        private boolean done;

        DecorRenderer(long seed) {
            super(false);
            this.seed = seed;
        }

        @Override
        public void render(MapView view, MapCanvas c, Player player) {
            if (done) return;
            done = true;
            Random r = new Random(seed * 7919L + 17);
            int blobs = 2 + r.nextInt(3);
            int[][] b = new int[blobs][3];
            for (int i = 0; i < blobs; i++) b[i] = new int[]{24 + r.nextInt(80), 24 + r.nextInt(80), 12 + r.nextInt(18)};
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    double field = 0;
                    for (int[] k : b) {
                        double dx = x - k[0], dy = y - k[1];
                        field += (k[2] * k[2]) / (dx * dx + dy * dy + 1);
                    }
                    field += (r.nextDouble() - 0.5) * 0.12;
                    int n = r.nextInt(10) - 5;
                    Color col;
                    if (x < 3 || y < 3 || x > 124 || y > 124) col = new Color(105, 75, 40);
                    else if (x == 5 || y == 5 || x == 122 || y == 122) col = new Color(140, 105, 65);
                    else if (field > 2.2) col = new Color(105 + n, 130 + n, 70 + n);
                    else if (field > 1.0) col = new Color(150 + n, 160 + n, 95 + n);
                    else if (field > 0.85) col = new Color(215 + n, 195 + n, 140 + n);
                    else col = new Color(200 + n, 185 + n, 145 + n);
                    c.setPixelColor(x, y, col);
                }
            }
            // forêts
            for (int i = 0; i < 40; i++) {
                int[] k = b[r.nextInt(blobs)];
                int x = k[0] + r.nextInt(k[2]) - k[2] / 2, y = k[1] + r.nextInt(k[2]) - k[2] / 2;
                if (x > 6 && y > 6 && x < 121 && y < 121) {
                    c.setPixelColor(x, y, new Color(60, 85, 45));
                    c.setPixelColor(x, y + 1, new Color(60, 85, 45));
                }
            }
            // chemin pointillé vers la croix
            int[] a = b[0], z = b[blobs - 1];
            int steps = 60;
            for (int s = 0; s < steps; s++) {
                if (s % 4 >= 2) continue;
                double t = s / (double) steps;
                int x = (int) (a[0] + (z[0] - a[0]) * t + Math.sin(t * Math.PI * 2) * 8);
                int y = (int) (a[1] + (z[1] - a[1]) * t);
                if (x > 6 && y > 6 && x < 121 && y < 121) c.setPixelColor(x, y, new Color(110, 40, 30));
            }
            for (int d = -3; d <= 3; d++) {
                int x = z[0], y = z[1];
                if (x + 3 < 121 && y + 3 < 121 && x - 3 > 6 && y - 3 > 6) {
                    c.setPixelColor(x + d, y + d, new Color(150, 25, 20));
                    c.setPixelColor(x + d, y - d, new Color(150, 25, 20));
                }
            }
            // rose des vents
            int rx = 108, ry = 108;
            for (int d = -6; d <= 6; d++) {
                c.setPixelColor(rx + d, ry, new Color(90, 60, 35));
                c.setPixelColor(rx, ry + d, new Color(90, 60, 35));
            }
            c.setPixelColor(rx, ry - 7, new Color(150, 25, 20));
        }
    }

    // ------------------------------------------------------------------ événements : partie

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && playerGame.containsKey(p.getUniqueId())) later(this::scanInventories);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p && playerGame.containsKey(p.getUniqueId())) later(this::scanInventories);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GridHolder) { e.setCancelled(true); return; }
        if (!(e.getWhoClicked() instanceof Player p) || !queue.containsKey(p.getUniqueId())) return;
        if (!playerGame.containsKey(p.getUniqueId())) { e.setCancelled(true); return; }   // lobby : inventaire figé
        // carte bloquée en deuxième main
        boolean offhandSlot = e.getClickedInventory() instanceof PlayerInventory && e.getSlot() == 40;
        boolean hotbarTool = e.getHotbarButton() >= 0 && isTool(p.getInventory().getItem(e.getHotbarButton()));
        if (offhandSlot || hotbarTool || e.getClick() == ClickType.SWAP_OFFHAND || isTool(e.getCurrentItem()) || isTool(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GridHolder) { e.setCancelled(true); return; }
        if (!(e.getWhoClicked() instanceof Player p) || !queue.containsKey(p.getUniqueId())) return;
        if (!playerGame.containsKey(p.getUniqueId()) || isTool(e.getOldCursor())) { e.setCancelled(true); return; }
        for (int raw : e.getRawSlots()) {
            if (e.getView().getInventory(raw) instanceof PlayerInventory && e.getView().convertSlot(raw) == 40) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (queue.containsKey(e.getPlayer().getUniqueId()) || isTool(e.getMainHandItem()) || isTool(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (isTool(e.getItemDrop().getItemStack())
                || (queue.containsKey(p.getUniqueId()) && !playerGame.containsKey(p.getUniqueId()))) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isTool);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack it = e.getItem();
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (right && toolType(it) == TOOL_QUIT) {
            e.setCancelled(true);
            if (queue.containsKey(p.getUniqueId())) {
                leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté l'EEMINGO.");
            } else if (mayTouch(p)) {
                removeTools(p);
            }
            return;
        }
        if (right && (toolType(it) == TOOL_GRID || toolType(it) == TOOL_BOOK) && playerGame.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            openGrid(p);
            return;
        }
        // lobby : décor non modifiable (portes, pupitres, bibliothèques, pots)
        if (isLobby(p.getWorld()) && p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItem(e.getHand());
        if (isTool(hand)) { e.setCancelled(true); return; }   // pas de carte dans un cadre
        if (isLobby(p.getWorld()) && p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!isLobby(e.getEntity().getWorld())) return;
        if (!(e.getRemover() instanceof Player p) || p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    /** Joueur à l'origine d'un dégât (direct, projectile, TNT, nuage de potion). */
    private Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) return p;
        return null;
    }

    /** Pas de PvP : joueurs en partie, lobby EEMINGO et tous les mondes de partie (bingo, bingo_n). */
    private boolean protectedFromPvp(Player victim) {
        return playerGame.containsKey(victim.getUniqueId()) || isLobby(victim.getWorld()) || isGameWorld(victim.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Player a = attacker(e.getDamager());
        if (a != null && a != v && protectedFromPvp(v)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSplash(PotionSplashEvent e) {
        if (!(e.getPotion().getShooter() instanceof Player a)) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player v && v != a && protectedFromPvp(v)) e.setIntensity(le, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        if (!(e.getEntity().getSource() instanceof Player a)) return;
        e.getAffectedEntities().removeIf(le -> le instanceof Player v && v != a && protectedFromPvp(v));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = playerGame.get(p.getUniqueId());
        if (g == null || g.state != State.PLAYING) return;
        boolean ownBed = (e.isBedSpawn() || e.isAnchorSpawn()) && e.getRespawnLocation().getWorld() == g.world;
        if (!ownBed) e.setRespawnLocation(g.spawn);
        later(() -> {
            if (p.isOnline() && playerGame.get(p.getUniqueId()) == g && g.state == State.PLAYING && p.getWorld() == g.world) {
                ensureMap(p, g);
                p.addPotionEffect(saturation());
            }
        });
    }

    // ------------------------------------------------------------------ événements : lobby et connexions

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isLobby(p.getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && isLobby(p.getWorld()) && e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getBlockY() == e.getFrom().getBlockY() || !isLobby(e.getTo().getWorld())) return;
        if (e.getTo().getY() < lobbyCenter()[1] - 20) {
            Location spawn = lobbySpawn();
            if (spawn != null) e.getPlayer().teleport(spawn);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!queue.containsKey(p.getUniqueId())) return;
        World now = p.getWorld();
        Game g = playerGame.get(p.getUniqueId());
        if (isLobby(now) || (g != null && now == g.world)) return;
        // parti par un autre moyen (menu du hub, téléportation, autre partie...) : désinscription
        leave(p, false);
        p.sendMessage(PREFIX + "§7Tu as quitté l'EEMINGO.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // reconnecté dans le lobby EEMINGO (ou dans un monde de partie) sans être inscrit : retour au hub
        boolean lobby = isLobby(p.getWorld()) && !p.hasPermission("eemibingo.admin");
        if (lobby || isGameWorld(p.getWorld())) Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline() && !queue.containsKey(p.getUniqueId())) sendToHub(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (queue.containsKey(e.getPlayer().getUniqueId())) leave(e.getPlayer(), true);
    }

    /** Un spectateur qui quitte le monde de la partie regardée (/lobby...) redevient un joueur normal. */
    @EventHandler
    public void onSpectatorWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = spectatorGame.get(p.getUniqueId());
        if (g == null || p.getWorld() == g.world) return;
        spectatorGame.remove(p.getUniqueId());
        // seulement au hub : dans un autre monde (skyblock...), on ne touche pas au mode de jeu
        if (isHub(p.getWorld())) p.setGameMode(GameMode.SURVIVAL);
    }

    @EventHandler
    public void onSpectatorQuit(PlayerQuitEvent e) {
        if (spectatorGame.remove(e.getPlayer().getUniqueId()) != null) sendToHub(e.getPlayer());
    }

    // ------------------------------------------------------------------ EemiCore et spectateurs

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /**
     * Statut agrégé pour EemiCore : waiting si des inscrits attendent au lobby (sinon playing si une partie tourne),
     * attente = inscrits hors partie, en jeu = joueurs de toutes les parties, max = max-players × max-instances.
     */
    private void reportStatus() {
        long waiting = queue.keySet().stream().filter(u -> !playerGame.containsKey(u)).count();
        int playing = 0;
        boolean anyPlaying = false;
        for (Game g : games.values()) {
            playing += g.teams.size();
            if (g.state == State.PLAYING) anyPlaying = true;
        }
        String state = waiting > 0 || !countdown.isEmpty() ? "waiting" : (anyPlaying ? "playing" : "waiting");
        String status = "status bingo " + state + " " + waiting + " " + playing + " " + (maxPlayers() * maxInstances());
        if (status.equals(lastStatus)) return;
        lastStatus = status;
        eemi(status);
    }

    /** /bingo spectate [joueur] : la partie de ce joueur, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String targetName) {
        if (queue.containsKey(p.getUniqueId())) { p.sendMessage(PREFIX + "§cTu es inscrit : /bingo leave d'abord."); return; }
        Game g = null;
        Player followed = null;
        if (targetName != null) {
            followed = Bukkit.getPlayerExact(targetName);
            if (followed != null) g = playerGame.get(followed.getUniqueId());
            if (g == null) { p.sendMessage(PREFIX + "§c" + targetName + " n'est pas dans une partie d'EEMINGO."); return; }
        } else {
            for (Game o : games.values()) {
                if (o.state != State.PLAYING) continue;
                if (g == null || o.teams.size() > g.teams.size()) g = o;
            }
        }
        if (g == null || g.state != State.PLAYING) {
            p.sendMessage(PREFIX + "§cAucune partie en cours.");
            return;
        }
        Location view;
        if (followed != null && followed.getWorld() == g.world) {
            view = followed.getLocation().add(0, 8, 0);
        } else {
            // au-dessus du point de départ de la partie, regard vers le bas
            view = g.spawn.clone().add(0, 30, 0);
        }
        view.setPitch(60f);
        spectatorGame.remove(p.getUniqueId()); // s'il regardait déjà une autre partie
        p.teleport(view);
        if (p.getWorld() != g.world) return;
        spectatorGame.put(p.getUniqueId(), g);
        p.setGameMode(GameMode.SPECTATOR);
        later(() -> { if (p.isOnline() && spectatorGame.containsKey(p.getUniqueId()) && mayTouch(p)) p.setGameMode(GameMode.SPECTATOR); });
        p.sendMessage(PREFIX + "§7Tu regardes la partie " + g.index + ", /lobby pour revenir.");
    }

    // ------------------------------------------------------------------ commandes

    /** Partie visée par une commande admin : numéro donné, sinon celle de l'admin, sinon la 1, sinon la seule en cours. */
    private Game targetGame(CommandSender sender, String[] args, int argIndex) {
        if (args.length > argIndex) {
            try {
                return games.get(Integer.parseInt(args[argIndex]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (sender instanceof Player p) {
            Game g = viewedGame(p);
            if (g == null) for (Game o : games.values()) if (o.world == p.getWorld()) g = o;
            if (g != null) return g;
        }
        if (games.containsKey(1)) return games.get(1);
        return games.size() == 1 ? games.values().iterator().next() : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = sender instanceof Player pl ? pl : null;
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        boolean admin = sender.hasPermission("eemibingo.admin");
        switch (sub) {
            case "" -> {
                if (p != null && playerGame.containsKey(p.getUniqueId())) openGrid(p);
                else if (p != null && queue.containsKey(p.getUniqueId())) p.sendMessage(PREFIX + "§7Tu attends dans la file §6" + queue.get(p.getUniqueId()).label + "§7. §f/bingo leave §7pour quitter.");
                else sendMenu(sender);
            }
            case "join", "solo", "duo", "quatuor" -> {
                // /bingo join <solo|duo|quatuor> [joueur]  ou  /bingo duo
                if (sub.equals("join") && args.length < 2) { sendMenu(sender); return true; }
                Mode chosen = parseMode(sub.equals("join") ? args[1] : sub);
                if (chosen == null) { sender.sendMessage(PREFIX + "§7/bingo join <solo|duo|quatuor>"); return true; }
                int nameArg = sub.equals("join") ? 2 : 1;
                Player target = args.length > nameArg && admin ? Bukkit.getPlayerExact(args[nameArg]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target, chosen);
            }
            case "leave" -> {
                if (p == null || !queue.containsKey(p.getUniqueId())) return true;
                leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté l'EEMINGO.");
            }
            case "grille", "grid" -> {
                if (p != null) openGrid(p);
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "start" -> {
                // force le lancement d'une file, même incomplète, dans une nouvelle partie : /bingo start <solo|duo|quatuor>
                if (!admin) return true;
                Mode m = args.length > 1 ? parseMode(args[1]) : null;
                if (m == null) { sender.sendMessage(PREFIX + "§7/bingo start <solo|duo|quatuor>"); return true; }
                if (freeIndex() == 0) { sender.sendMessage(PREFIX + "§cToutes les parties sont occupées (" + maxInstances() + " max)."); return true; }
                List<UUID> waiting = waitingFor(m);
                if (waiting.isEmpty()) { sender.sendMessage(PREFIX + "§cPersonne dans la file " + m.label + "."); return true; }
                countdown.remove(m);
                fullWarned.remove(m);
                broadcastTo(waiting, "§7Lancement forcé. Génération du monde...");
                Game g = startGame(m, waiting);
                if (g != null) sender.sendMessage(PREFIX + "§7Partie " + g.index + " lancée (" + g.worldName + ").");
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = targetGame(sender, args, 1);
                if (g == null) { sender.sendMessage(PREFIX + "§cAucune partie trouvée. §7/bingo stop [numéro] (voir /bingo parties)"); return true; }
                g.forceStop();
                reportStatus();
                sender.sendMessage(PREFIX + "§7Partie " + g.index + " arrêtée (les joueurs restent dans leur file).");
            }
            case "reset" -> {
                if (!admin) return true;
                for (Game g : new ArrayList<>(games.values())) g.forceStop();
                countdown.clear();
                for (Player o : online(new ArrayList<>(queue.keySet()))) {
                    leave(o, true);
                    o.sendMessage(PREFIX + "§7Les files EEMINGO ont été vidées par un admin.");
                }
                queue.clear();
                reportStatus();
                sender.sendMessage(PREFIX + "§7Parties arrêtées et files vidées.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties : " + games.size() + "/" + maxInstances());
                for (Game g : games.values()) {
                    sender.sendMessage("§8- §f#" + g.index + " §7" + g.worldName + " §f" + g.mode.label + " §7"
                            + (g.state == State.PLAYING ? "en jeu, reste " + clock(g.timer) : "fin") + " §7- " + g.teams.size() + " joueurs, "
                            + spectatorsOf(g).size() + " spectateur(s)");
                }
                StringBuilder sb = new StringBuilder("§7Files :");
                for (Mode m : Mode.values()) {
                    sb.append(" §f").append(m.label).append(" ").append(waitingFor(m).size());
                    Integer t = countdown.get(m);
                    if (t != null) sb.append(" §7(").append(t).append(" s)");
                }
                sender.sendMessage(sb.toString());
            }
            case "buildlobby" -> {
                if (!admin) return true;
                buildLobby(sender);
            }
            case "files", "queues" -> {
                StringBuilder sb = new StringBuilder("§7Files :");
                for (Mode m : Mode.values()) sb.append(" §f").append(m.label).append(" ").append(waitingFor(m).size());
                sb.append(" §7(min ").append(minPlayers()).append(", max ").append(maxPlayers()).append(") - ")
                        .append(games.size()).append("/").append(maxInstances()).append(" partie(s) en cours");
                sender.sendMessage(PREFIX + sb);
            }
            default -> sender.sendMessage(PREFIX + "§7/bingo <join solo|duo|quatuor|leave|grille|files|spectate [joueur]>"
                    + (admin ? " §8| admin : start <mode>, stop [n], parties, reset, buildlobby" : ""));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("join", "leave", "grille", "files", "spectate", "solo", "duo", "quatuor"));
            if (sender.hasPermission("eemibingo.admin")) subs.addAll(List.of("start", "stop", "parties", "reset", "buildlobby"));
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("start"))) {
            for (Mode m : Mode.values()) if (m.name().toLowerCase().startsWith(args[1].toLowerCase())) out.add(m.name().toLowerCase());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            for (Integer n : games.keySet()) out.add(String.valueOf(n));
        } else {
            return null;
        }
        return out;
    }

    /** Choix du mode cliquable dans le chat. */
    private void sendMenu(CommandSender s) {
        Component msg = prefixC().append(Component.text("Choisis ton mode : ", NamedTextColor.GRAY));
        for (Mode m : Mode.values()) {
            if (!modeEnabled(m)) continue;
            msg = msg.append(Component.text("[" + m.label + "] ", NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/bingo join " + m.name().toLowerCase())));
        }
        s.sendMessage(msg);
    }

    // ------------------------------------------------------------------ utilitaires

    private ItemStack tool(Material type, byte kind, String name, NamedTextColor color) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, kind);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isTool(ItemStack it) {
        return toolType(it) != 0;
    }

    private byte toolType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Byte b = it.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.BYTE);
        return b == null ? 0 : b;
    }

    private void removeTools(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int s = 0; s < inv.getSize(); s++) if (isTool(inv.getItem(s))) inv.setItem(s, null);
        if (isTool(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    private static String clock(int seconds) {
        int s = Math.max(0, seconds);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTaskLater(this, r, 3L);
    }

    private List<Player> online(Collection<UUID> ids) {
        List<Player> list = new ArrayList<>();
        for (UUID id : new ArrayList<>(ids)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) list.add(p);
        }
        return list;
    }

    private Mode parseMode(String s) {
        try {
            return Mode.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void broadcastTo(Collection<UUID> ids, String msg) {
        for (Player p : online(new LinkedHashSet<>(ids))) p.sendMessage(PREFIX + msg);
    }
}
