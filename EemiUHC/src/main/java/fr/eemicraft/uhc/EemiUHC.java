package fr.eemicraft.uhc;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
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
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * UHC Run d'EEMICRAFT : monde neuf (graine aléatoire) créé à chaque partie et supprimé à la fin.
 * Pas de régénération naturelle (pommes d'or, têtes d'or, potions), cut clean, Timber, Hastey Boys,
 * kit de départ. Minage avant le PvP, puis bordure qui rétrécit jusqu'au combat final.
 * Modes = taille des équipes : solo (1), duo (2), trio (3). Une file par mode, les inscrits attendent au hub.
 * Plusieurs parties en même temps (max-instances, 3 par défaut) : partie 1 dans « uhc », partie n dans « uhc_n ».
 * Mort, déconnexion ou départ du monde = éliminé ; dernière équipe en vie gagne, retour au hub.
 */
public final class EemiUHC extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§aUHC Run§8] §7";

    enum State { PLAYING, ENDING }

    /** Mode = taille des équipes. */
    enum Mode {
        SOLO(1, "Solo"), DUO(2, "Duo"), TRIO(3, "Trio");

        final int size;
        final String label;

        Mode(int size, String label) {
            this.size = size;
            this.label = label;
        }
    }

    /** Couleurs des équipes (duo et trio ; en solo chaque joueur est sa propre équipe). */
    enum TeamColor {
        ROUGE("Rouge", NamedTextColor.RED), BLEU("Bleu", NamedTextColor.BLUE), VERT("Vert", NamedTextColor.GREEN),
        JAUNE("Jaune", NamedTextColor.YELLOW), ROSE("Rose", NamedTextColor.LIGHT_PURPLE), CYAN("Cyan", NamedTextColor.AQUA),
        ORANGE("Orange", NamedTextColor.GOLD), VIOLET("Violet", NamedTextColor.DARK_PURPLE);

        final String label;
        final NamedTextColor color;

        TeamColor(String label, NamedTextColor color) {
            this.label = label;
            this.color = color;
        }
    }

    private NamespacedKey headKey;
    private NamespacedKey recipeKey;
    /** Marque de l'objet « Quitter la file » donné aux inscrits qui attendent au hub. */
    private NamespacedKey leaveKey;

    /** Inscrits qui attendent au hub et leur mode, dans l'ordre d'arrivée. */
    private final Map<UUID, Mode> queue = new LinkedHashMap<>();
    /** Décompte en cours par file (absent = pas de décompte). */
    private final Map<Mode, Integer> countdown = new EnumMap<>(Mode.class);
    private final Set<Mode> shortened = EnumSet.noneOf(Mode.class);
    private final Set<Mode> fullWarned = EnumSet.noneOf(Mode.class);
    /** Parties en cours par numéro (1 = monde « uhc », n = « uhc_n »). */
    private final Map<Integer, Game> games = new TreeMap<>();
    /** Numéros dont le monde est en cours de suppression (pas réutilisables tout de suite). */
    private final Set<Integer> deleting = new HashSet<>();
    /** Participant → sa partie. */
    private final Map<UUID, Game> playerGame = new HashMap<>();
    /** Spectateur (non inscrit) → partie regardée. */
    private final Map<UUID, Game> spectatorGame = new HashMap<>();
    private String lastStatus;
    private long lastStatusAt;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        headKey = new NamespacedKey(this, "golden_head");
        recipeKey = new NamespacedKey(this, "golden_head");
        leaveKey = new NamespacedKey(this, "leave_queue");
        saveDefaultConfig();
        registerRecipe();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        // mondes de partie restés d'un arrêt brutal (uhc, uhc_2, ...) supprimés
        Bukkit.getScheduler().runTask(this, this::deleteLeftoverWorlds);
        // statut initial pour EemiCore (une fois tous les plugins actives)
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) removeLeaveItem(p);
        for (Game g : new ArrayList<>(games.values())) {
            for (Player p : online(g.teamOf.keySet())) {
                if (g.bar != null) p.hideBossBar(g.bar);
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                sendToHub(p);
            }
            for (Player p : online(spectatorsOf(g))) sendToHub(p);
            deleteWorld(g.worldName);
        }
        games.clear();
        playerGame.clear();
        spectatorGame.clear();
        queue.clear();
        Bukkit.removeRecipe(recipeKey);
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private boolean scenario(String key) {
        return getConfig().getBoolean("scenarios." + key, true);
    }

    private String baseName() {
        return getConfig().getString("world-name", "uhc");
    }

    /** Monde de la partie n : n = 1 → « uhc », n ≥ 2 → « uhc_n ». */
    private String worldName(int n) {
        return n <= 1 ? baseName() : baseName() + "_" + n;
    }

    /** Numéro de partie d'un nom de monde (« uhc_3 » → 3, « uhc » → 1, autre → 0). */
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

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private int minPlayers() {
        return Math.max(1, cfg("min-players", 2));
    }

    private int maxPlayers() {
        return Math.max(minPlayers(), cfg("max-players", 10));
    }

    private boolean modeEnabled(Mode m) {
        List<String> list = getConfig().getStringList("modes");
        if (list.isEmpty()) return true;
        for (String s : list) if (s.equalsIgnoreCase(m.name())) return true;
        return false;
    }

    /** Vivant d'une partie et dans le monde de cette partie. */
    private boolean inGame(Player p) {
        Game g = playerGame.get(p.getUniqueId());
        return g != null && p.getWorld() == g.world && g.alive.contains(p.getUniqueId());
    }

    /** Monde de partie UHC (uhc, uhc_n), qu'une partie y tourne ou non. */
    private boolean isGameWorld(World w) {
        return w != null && worldIndex(w.getName()) > 0;
    }

    private Game gameOfWorld(World w) {
        if (w == null) return null;
        for (Game g : games.values()) if (g.world == w) return g;
        return null;
    }

    /** Plus petit numéro libre (1..max-instances), 0 si toutes les parties sont occupées. */
    private int freeIndex() {
        for (int n = 1; n <= maxInstances(); n++) if (!games.containsKey(n) && !deleting.contains(n)) return n;
        return 0;
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
                    broadcastTo(waiting, "§eFile " + m.label + " prête : l'UHC commence dans §6" + cfg("countdown", 30) + " s§e.");
                }
                continue;
            }
            if (waiting.size() < minPlayers()) {
                countdown.remove(m);
                broadcastTo(waiting, "§cPas assez de joueurs, lancement annulé.");
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
            // décompte fini : une partie libre ?
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
        waitingActionBar();
        for (Player p : Bukkit.getOnlinePlayers()) syncLeaveItem(p);
        reportStatus();
    }

    // ------------------------------------------------------------------ inscription (attente au hub)

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
        Game watched = spectatorGame.remove(id);
        if (watched != null && p.getGameMode() == GameMode.SPECTATOR && mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
        boolean fresh = !queue.containsKey(id);
        queue.put(id, m);
        World hub = hubWorld();
        if (hub != null && p.getWorld() != hub) p.teleport(hub.getSpawnLocation());
        // objet « Quitter la file » : tout de suite, et après la redistribution des objets de DeluxeHub (5 ticks)
        syncLeaveItem(p);
        Bukkit.getScheduler().runTaskLater(this, () -> syncLeaveItem(p), 7L);
        List<UUID> waiting = waitingFor(m);
        p.sendMessage(PREFIX + (fresh ? "§aInscrit" : "§aFile changée") + " : §6" + m.label + " §7(équipes de " + m.size + ", "
                + waiting.size() + "/" + minPlayers() + " joueurs min). Tu attends au hub. §f/uhc leave §7pour quitter.");
        List<UUID> others = new ArrayList<>(waiting);
        others.remove(id);
        broadcastTo(others, "§a" + p.getName() + " §7rejoint la file " + m.label + " (§f" + waiting.size() + "§7).");
        reportStatus();
        if (freeIndex() == 0) p.sendMessage(PREFIX + "§7Toutes les parties sont occupées, la tienne partira dès qu'une se termine.");
    }

    /** Inscrits en ligne qui attendent une partie de ce mode, dans l'ordre d'arrivée. */
    private List<UUID> waitingFor(Mode m) {
        List<UUID> list = new ArrayList<>();
        for (Map.Entry<UUID, Mode> e : queue.entrySet()) {
            if (e.getValue() == m && !playerGame.containsKey(e.getKey()) && Bukkit.getPlayer(e.getKey()) != null) list.add(e.getKey());
        }
        return list;
    }

    private void waitingActionBar() {
        for (Map.Entry<UUID, Mode> e : queue.entrySet()) {
            if (playerGame.containsKey(e.getKey())) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            Mode m = e.getValue();
            String text = "UHC " + m.label + " : " + waitingFor(m).size() + " inscrit(s), min " + minPlayers();
            Integer t = countdown.get(m);
            if (t != null) text += t > 0 ? " - départ dans " + t + " s" : (freeIndex() == 0 ? " - en attente d'une partie libre" : " - départ imminent");
            if (!games.isEmpty()) text += " - " + games.size() + " partie(s) en cours";
            p.sendActionBar(Component.text(text, NamedTextColor.GOLD));
        }
    }

    /** Désinscription d'un joueur qui attend (pas d'un participant). */
    private void leaveQueue(Player p) {
        if (queue.remove(p.getUniqueId()) != null) p.sendMessage(PREFIX + "§7Tu as quitté la file UHC.");
        removeLeaveItem(p);
        reportStatus();
    }

    // ------------------------------------------------------------------ objet « Quitter la file » (hub)

    private static final int LEAVE_SLOT = 8; // DeluxeHub occupe 0, 2, 4, 6

    private boolean isLeaveItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(leaveKey, PersistentDataType.BYTE);
    }

    private ItemStack leaveItem() {
        ItemStack it = new ItemStack(Material.RED_BED);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Quitter la file", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Clic droit : quitter la file UHC", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(leaveKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    /** Retire l'objet, seulement au hub (ailleurs, l'inventaire est celui d'un autre monde : on n'y touche pas). */
    private void removeLeaveItem(Player p) {
        if (p == null || !isHub(p.getWorld())) return;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (isLeaveItem(inv.getItem(i))) inv.setItem(i, null);
        if (isLeaveItem(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    /** Inscrit qui attend au hub : l'objet en case 8 (sinon une case libre de la barre) ; tous les autres : retiré. */
    private void syncLeaveItem(Player p) {
        if (p == null || !p.isOnline() || !isHub(p.getWorld())) return;
        UUID id = p.getUniqueId();
        boolean want = queue.containsKey(id) && !playerGame.containsKey(id) && p.getGameMode() != GameMode.SPECTATOR;
        if (!want) {
            removeLeaveItem(p);
            return;
        }
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) if (isLeaveItem(it)) return;
        if (isLeaveItem(p.getItemOnCursor())) return;
        // ne jamais écraser un objet (DeluxeHub ou autre) : case 8, sinon une case libre de la barre
        for (int slot : new int[]{LEAVE_SLOT, 7, 5, 3, 1}) {
            ItemStack cur = inv.getItem(slot);
            if (cur == null || cur.getType().isAir()) {
                inv.setItem(slot, leaveItem());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeaveItemUse(PlayerInteractEvent e) {
        if (!isLeaveItem(e.getItem())) return;
        // ne jamais poser le lit
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (queue.containsKey(p.getUniqueId()) && !playerGame.containsKey(p.getUniqueId())) leaveQueue(p);
        else removeLeaveItem(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaveItemDrop(PlayerDropItemEvent e) {
        if (isLeaveItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaveItemClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isLeaveItem(e.getCurrentItem()) || isLeaveItem(e.getCursor())
                || (e.getClick() == ClickType.NUMBER_KEY && isLeaveItem(p.getInventory().getItem(e.getHotbarButton())))
                || (e.getClick() == ClickType.SWAP_OFFHAND && isLeaveItem(p.getInventory().getItemInOffHand())))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaveItemDrag(InventoryDragEvent e) {
        if (isLeaveItem(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaveItemSwap(PlayerSwapHandItemsEvent e) {
        if (isLeaveItem(e.getMainHandItem()) || isLeaveItem(e.getOffHandItem())) e.setCancelled(true);
    }

    /** Départ du hub par téléportation : objet retiré AVANT que Multiverse-Inventories sauvegarde l'inventaire du hub. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeaveHubTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == e.getFrom().getWorld()) return;
        if (isHub(e.getFrom().getWorld())) removeLeaveItem(e.getPlayer());
    }

    private World hubWorld() {
        return Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
    }

    /**
     * Renvoi au hub : seulement depuis un monde de partie UHC ou le hub (jamais depuis le skyblock ou un autre jeu).
     * Téléportation d'abord (Multiverse-Inventories garde l'inventaire du monde quitté), puis remise à zéro au hub.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
    }

    private boolean isHub(World w) {
        World hub = hubWorld();
        return w != null && hub != null && w.equals(hub);
    }

    /**
     * On ne touche à l'inventaire / l'état d'un joueur que s'il est dans un monde de partie UHC ou au hub :
     * ailleurs (skyblock...), Multiverse-Inventories a déjà chargé l'inventaire de ce monde-là.
     */
    private boolean mayTouch(Player p) {
        return p != null && p.isOnline() && (isGameWorld(p.getWorld()) || isHub(p.getWorld()));
    }

    private void resetPlayer(Player p, GameMode gm) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setItemOnCursor(null);
        p.setGameMode(gm);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        heal(p);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    private void heal(Player p) {
        if (!mayTouch(p)) return;
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
    }

    // ------------------------------------------------------------------ lancement d'une partie

    /** Lance une partie avec les premiers inscrits de la file (dans un nouveau monde). Rend la partie ou null. */
    private Game startGame(Mode m, List<UUID> waiting) {
        int index = freeIndex();
        if (index == 0) {
            broadcastTo(waiting, "§cToutes les parties sont pleines.");
            return null;
        }
        List<UUID> picked = new ArrayList<>(waiting.subList(0, Math.min(waiting.size(), maxPlayers())));
        List<Player> list = online(picked);
        if (list.isEmpty()) return null;
        String name = worldName(index);
        Location center = createGameWorld(name);
        if (center == null) {
            broadcastTo(picked, "§cImpossible de créer le monde de jeu.");
            return null;
        }
        Game g = new Game(index, m, center);
        games.put(index, g);
        g.start(list);
        return g;
    }

    /** Crée le monde de jeu ; recommence avec une autre graine (3 essais) si la zone de jeu a trop d'eau. Rend le centre. */
    private Location createGameWorld(String name) {
        int half = cfg("border-start", 1000) / 2;
        for (int attempt = 1; attempt <= 3; attempt++) {
            deleteWorld(name);
            World w = new WorldCreator(name)
                    .seed(ThreadLocalRandom.current().nextLong())
                    .type(WorldType.NORMAL)
                    .environment(World.Environment.NORMAL)
                    .createWorld();
            if (w == null) return null;
            // centre : le point (spawn ou décalé de 400 blocs) dont la zone de jeu a le plus de terre
            Location s = w.getSpawnLocation();
            int bestX = s.getBlockX(), bestZ = s.getBlockZ();
            double bestLand = -1;
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    int x = s.getBlockX() + i * 400, z = s.getBlockZ() + j * 400;
                    double land = landRatio(w, x, z, half);
                    if (land > bestLand + 0.05 || (i == 0 && j == 0 && land >= bestLand)) {
                        bestLand = land;
                        bestX = x;
                        bestZ = z;
                    }
                }
            }
            if (bestLand >= 0.75 || attempt == 3) {
                getLogger().info("Monde " + name + " : centre " + bestX + " " + bestZ + ", " + Math.round(bestLand * 100) + " % de terre, essai " + attempt + ".");
                return new Location(w, bestX + 0.5, w.getHighestBlockYAt(bestX, bestZ) + 1, bestZ + 0.5);
            }
            getLogger().info("Monde " + name + " trop aquatique (" + Math.round(bestLand * 100) + " % de terre, essai " + attempt + "), nouvelle graine.");
        }
        return null;
    }

    /** Part de terre (biomes hors océan et rivière) sur un carré de demi-côté half (biomes calculés sans générer les chunks). */
    private double landRatio(World w, int cx, int cz, int half) {
        int land = 0, total = 0;
        int y = w.getSeaLevel();
        for (int dx = -half; dx <= half; dx += 50) {
            for (int dz = -half; dz <= half; dz += 50) {
                total++;
                if (!isWater(w.getBiome(cx + dx, y, cz + dz))) land++;
            }
        }
        return total == 0 ? 0 : land / (double) total;
    }

    @SuppressWarnings("deprecation")
    private static boolean isWater(Biome b) {
        String k = b == null ? "" : b.getKey().getKey();
        return k.contains("ocean") || k.contains("river");
    }

    /** Décharge (sans sauvegarder) et supprime le monde name (joueurs encore dedans renvoyés au hub). */
    private void deleteWorld(String name) {
        World w = Bukkit.getWorld(name);
        Path folder = null;
        if (w != null) {
            for (Player p : w.getPlayers()) sendToHub(p);
            folder = w.getWorldFolder().toPath();
            if (!Bukkit.unloadWorld(w, false)) {
                getLogger().warning("Impossible de décharger le monde " + name + ".");
                return;
            }
        }
        Path container = Bukkit.getWorldContainer().toPath();
        deleteFolder(folder);
        deleteFolder(container.resolve(name));
        World main = Bukkit.getWorlds().get(0);
        deleteFolder(main.getWorldFolder().toPath().resolve("dimensions").resolve("minecraft").resolve(name));
    }

    /** Au démarrage : supprime tous les mondes de partie restés (uhc, uhc_2, uhc_3, ...), chargés ou sur le disque. */
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
            getLogger().warning("Suppression de " + dir + " incomplète : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ une partie

    final class Game {
        final int index;
        final Mode mode;
        final String worldName;
        final World world;
        final Location center;
        /** Participants (vivants et morts) et numéro de leur équipe. */
        final Map<UUID, Integer> teamOf = new LinkedHashMap<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, String> names = new HashMap<>();
        final Map<UUID, Location> lastLoc = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        State state = State.PLAYING;
        int timer;
        int elapsed;
        int teamCount;
        BossBar bar;
        String lastWinner;

        Game(int index, Mode mode, Location center) {
            this.index = index;
            this.mode = mode;
            this.center = center;
            this.world = center.getWorld();
            this.worldName = world.getName();
        }

        void start(List<Player> list) {
            setupWorld();
            // équipes équilibrées : au moins 2 équipes dès 2 joueurs
            Collections.shuffle(list);
            int n = list.size();
            teamCount = (n + mode.size - 1) / mode.size;
            if (n >= 2) teamCount = Math.max(2, teamCount);
            if (mode.size > 1) teamCount = Math.min(teamCount, TeamColor.values().length);
            for (int i = 0; i < n; i++) {
                Player p = list.get(i);
                UUID id = p.getUniqueId();
                teamOf.put(id, i % teamCount);
                alive.add(id);
                names.put(id, p.getName());
                queue.remove(id);
                removeLeaveItem(p); // au hub seulement
                playerGame.put(id, this);
                Game watched = spectatorGame.remove(id);
                if (watched != null && p.getGameMode() == GameMode.SPECTATOR && mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
            }
            bar = BossBar.bossBar(Component.text("UHC"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            for (Player p : list) {
                createBoard(p);
                p.showBossBar(bar);
            }
            colorNames();
            updateBar();
            spread(list);
            reportStatus();
            getLogger().info("Partie " + index + " (" + worldName + ", " + mode.label + ") lancée avec " + n + " joueurs.");
            broadcast("§eC'est parti (" + mode.label + ", " + teamCount + "équipes) ! PvP à " + clock(cfg("pvp-time", 600))
                    + ". Pas de régénération naturelle : pommes d'or et têtes d'or (8 lingots d'or autour d'une tête de joueur).");
        }

        void tick() {
            if (state == State.PLAYING) {
                elapsed++;
                for (Player p : online(alive)) if (p.getWorld() == world) lastLoc.put(p.getUniqueId(), p.getLocation());
                schedule();
                if (state == State.PLAYING) updateBar();
            } else if (--timer <= 0) {
                finish();
                return;
            }
            updateSidebars();
        }

        private void setupWorld() {
            world.setDifficulty(Difficulty.NORMAL);
            world.setGameRule(GameRules.PVP, false);
            // 1.3 : régénération naturelle pendant la première phase (jusqu'au final heal), coupée ensuite
            world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, regenUntil() > 0);
            // 1.3 : moins de monstres (limite d'apparition du monde, 70 en vanilla)
            world.setSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER, Math.max(0, cfg("monster-spawn-limit", 25)));
            world.setGameRule(GameRules.KEEP_INVENTORY, false);
            world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
            world.setGameRule(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
            world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
            if (getConfig().getBoolean("always-day", true)) {
                world.setGameRule(GameRules.ADVANCE_TIME, false);
                world.setTime(6000);
            } else {
                world.setTime(0);
            }
            world.setSpawnLocation(center);
            world.getWorldBorder().setCenter(center);
            world.getWorldBorder().setSize(cfg("border-start", 1000));
            world.getWorldBorder().setDamageAmount(1.0);
            world.getWorldBorder().setDamageBuffer(0);
        }

        /** Étapes de la partie, déclenchées à la seconde près. */
        private void schedule() {
            int t = elapsed;
            int pvp = cfg("pvp-time", 600);
            if (t == cfg("invincibility", 10)) broadcast("§7Les dégâts sont actives. Bon minage !");
            if (t == pvp - 60) broadcast("§cPvP dans 1 minute.");
            if (t == regenUntil()) world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
            if (t == cfg("final-heal", 540)) {
                for (Player p : online(alive)) {
                    if (p.getWorld() == world) heal(p);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                }
                broadcast("§aFinal heal : tout le monde est soigné. Plus aucune régénération naturelle jusqu'à la fin !");
            }
            if (t == pvp) {
                world.setGameRule(GameRules.PVP, true);
                for (Player p : online(teamOf.keySet())) {
                    p.showTitle(Title.title(Component.text("PvP actif", NamedTextColor.RED).decorate(TextDecoration.BOLD), Component.empty(),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
                }
            }
            if (t == cfg("border-shrink-start", 600)) {
                int end = cfg("border-end", 50), time = cfg("border-shrink-time", 600);
                world.getWorldBorder().setSize(end, time);
                broadcast("§cLa bordure se réduit : §f" + cfg("border-start", 1000) + " -> " + end + " §cen " + clock(time) + ". Direction le centre (§f" + center.getBlockX() + " " + center.getBlockZ() + "§c).");
            }
            if (t == cfg("meetup-time", 1200)) {
                for (Player p : online(alive)) if (p.getWorld() == world) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false, false));
                broadcast("§eMeetup : tous les joueurs sont en surbrillance.");
            }
            if (t == cfg("final-shrink-time", 1500)) {
                int size = cfg("final-border", 10), time = cfg("final-shrink-duration", 120);
                world.getWorldBorder().setSize(size, time);
                broadcast("§cDernière réduction : bordure -> " + size + " en " + clock(time) + ".");
            }
            if (t >= cfg("max-time", 2100)) timeUp();
        }

        /** Prochaine étape affichée dans la barre et le scoreboard. */
        String nextStep() {
            int t = elapsed;
            int inv = cfg("invincibility", 10), pvp = cfg("pvp-time", 600), shrink = cfg("border-shrink-start", 600);
            int shrinkEnd = shrink + cfg("border-shrink-time", 600), meetup = cfg("meetup-time", 1200);
            int fin = cfg("final-shrink-time", 1500), max = cfg("max-time", 2100);
            if (t < inv) return "Invincibilité : " + clock(inv - t);
            if (t < pvp) return "PvP dans " + clock(pvp - t);
            if (t < shrink) return "Bordure dans " + clock(shrink - t);
            if (t < shrinkEnd) return "Bordure réduite dans " + clock(shrinkEnd - t);
            if (t < meetup) return "Meetup dans " + clock(meetup - t);
            if (t < fin) return "Réduction finale dans " + clock(fin - t);
            return "Fin dans " + clock(max - t);
        }

        private void updateBar() {
            if (bar == null) return;
            bar.progress(Math.max(0f, Math.min(1f, 1f - elapsed / (float) cfg("max-time", 2100))));
            bar.name(Component.text("UHC  " + clock(elapsed) + "   ", NamedTextColor.WHITE)
                    .append(Component.text(nextStep(), NamedTextColor.GOLD))
                    .append(Component.text("   Bordure " + (int) Math.round(world.getWorldBorder().getSize()), NamedTextColor.GRAY)));
        }

        /** Téléporte chaque équipe à un point au hasard sur la terre ferme, loin des autres. */
        private void spread(List<Player> players) {
            Map<Integer, List<Player>> byTeam = new LinkedHashMap<>();
            for (Player p : players) byTeam.computeIfAbsent(teamOf.get(p.getUniqueId()), k -> new ArrayList<>()).add(p);
            double radius = cfg("border-start", 1000) / 2.0 - 40;
            double minDist = cfg("spread-min-distance", 150);
            List<int[]> spots = new ArrayList<>();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int team = 0; team < byTeam.size(); team++) {
                int[] spot = null;
                for (int attempt = 0; attempt < 300 && spot == null; attempt++) {
                    double need = attempt < 150 ? minDist : minDist / 2;
                    int x = center.getBlockX() + (int) r.nextDouble(-radius, radius);
                    int z = center.getBlockZ() + (int) r.nextDouble(-radius, radius);
                    if (isWater(world.getBiome(x, world.getSeaLevel(), z))) continue;
                    boolean ok = true;
                    for (int[] s : spots) if (Math.hypot(s[0] - x, s[1] - z) < need) { ok = false; break; }
                    if (ok) spot = new int[]{x, z};
                }
                if (spot == null) spot = new int[]{center.getBlockX() + (int) r.nextDouble(-radius, radius), center.getBlockZ() + (int) r.nextDouble(-radius, radius)};
                spots.add(spot);
            }
            int i = 0;
            for (List<Player> members : byTeam.values()) {
                int[] s = spots.get(i++);
                world.getChunkAtAsync(s[0] >> 4, s[1] >> 4).thenAccept(chunk -> {
                    if (state != State.PLAYING || games.get(index) != this) return;
                    Location loc = new Location(world, s[0] + 0.5, world.getHighestBlockYAt(s[0], s[1]) + 1, s[1] + 0.5);
                    for (Player p : members) {
                        if (!p.isOnline() || !alive.contains(p.getUniqueId())) continue;
                        p.teleport(loc);
                        lastLoc.put(p.getUniqueId(), loc);
                        later(() -> equip(p));
                    }
                });
            }
        }

        private void equip(Player p) {
            if (!p.isOnline() || !alive.contains(p.getUniqueId()) || state != State.PLAYING) return;
            if (p.getWorld() != world) return; // kit seulement une fois arrivé dans le monde de la partie
            resetPlayer(p, GameMode.SURVIVAL);
            int haste = cfg("haste-level", 1);
            if (haste > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, haste - 1, false, false, true));
            if (scenario("starter-kit")) {
                p.getInventory().addItem(hastey(new ItemStack(Material.STONE_PICKAXE)), new ItemStack(Material.COOKED_BEEF, 16), new ItemStack(Material.BOOK, 3));
            }
            Integer team = teamOf.get(p.getUniqueId());
            int mates = 0;
            for (Map.Entry<UUID, Integer> e : teamOf.entrySet()) if (e.getValue().equals(team) && !e.getKey().equals(p.getUniqueId())) mates++;
            Component sub = mode.size > 1
                    ? Component.text("Équipe " + teamName(team), color(team))
                    : Component.text("Chacun pour soi", NamedTextColor.GRAY);
            p.showTitle(Title.title(Component.text("UHC", NamedTextColor.GOLD).decorate(TextDecoration.BOLD), sub,
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            if (mode.size > 1 && mates > 0) p.sendMessage(PREFIX + "§7Ton équipe : §f" + String.join(", ", memberNames(team)));
            if (mates == 0 && mode.size > 1) p.sendMessage(PREFIX + "§7Ton équipe est incomplète, bonne chance !");
        }

        /** Élimine un joueur (mort, déconnexion, départ du monde) et vérifie s'il reste une seule équipe. */
        void eliminate(UUID id, Player killer, String how) {
            if (!alive.remove(id)) return;
            Integer team = teamOf.get(id);
            String name = names.getOrDefault(id, "?");
            Component msg = Component.text("[UHC] ", NamedTextColor.GOLD).append(Component.text(name, color(team)));
            if (killer != null && alive.contains(killer.getUniqueId())) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
                eemi("reward " + killer.getName() + " uhc kill");
                msg = msg.append(Component.text("a été tué par ", NamedTextColor.GRAY))
                        .append(Component.text(killer.getName(), color(teamOf.get(killer.getUniqueId()))));
            } else {
                msg = msg.append(Component.text(" " + how, NamedTextColor.GRAY));
            }
            msg = msg.append(Component.text("  (" + alive.size() + " restants)", NamedTextColor.DARK_GRAY));
            Set<UUID> audience = new LinkedHashSet<>(teamOf.keySet());
            audience.addAll(spectatorsOf(this));
            for (Player o : online(audience)) {
                o.sendMessage(msg);
                o.playSound(o.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.8f);
            }
            reportStatus();
            checkWin();
        }

        Set<Integer> aliveTeams() {
            Set<Integer> set = new LinkedHashSet<>();
            for (UUID id : alive) set.add(teamOf.get(id));
            return set;
        }

        private void checkWin() {
            if (state != State.PLAYING) return;
            Set<Integer> teams = aliveTeams();
            if (teams.size() == 1) endGame(teams.iterator().next(), "Dernière équipe en vie.", true);
            else if (teams.isEmpty()) endGame(null, "Plus personne en vie.", true);
        }

        /** Temps écoulé : l'équipe avec le plus de vivants gagne, puis le plus de kills ; sinon égalité. */
        private void timeUp() {
            Integer best = null;
            int bestAlive = -1, bestKills = -1;
            boolean tie = false;
            for (Integer t : aliveTeams()) {
                int a = 0, k = 0;
                for (UUID id : alive) if (teamOf.get(id).equals(t)) a++;
                for (Map.Entry<UUID, Integer> e : kills.entrySet()) if (t.equals(teamOf.get(e.getKey()))) k += e.getValue();
                if (a > bestAlive || (a == bestAlive && k > bestKills)) {
                    best = t;
                    bestAlive = a;
                    bestKills = k;
                    tie = false;
                } else if (a == bestAlive && k == bestKills) {
                    tie = true;
                }
            }
            endGame(tie ? null : best, "Temps écoulé.", true);
        }

        /** rewards = false pour un arrêt admin (pas de récompenses EemiCore). */
        void endGame(Integer winner, String reason, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            if (rewards) {
                for (Map.Entry<UUID, Integer> e : teamOf.entrySet()) {
                    String n = names.get(e.getKey());
                    if (n == null) continue;
                    eemi("reward " + n + " uhc play");
                    if (winner != null && winner.equals(e.getValue())) eemi("reward " + n + " uhc win");
                }
            }
            reportStatus();
            timer = cfg("end-time", 10);
            lastWinner = winner == null ? "Égalité" : teamName(winner);
            // titre court (≤ 16 caractères) : nom de l'équipe ou du joueur seul
            String titleText = winner == null ? "Égalité !" : (mode.size > 1 ? "Équipe " + teamName(winner) : teamName(winner));
            if (titleText.length() > 16) titleText = titleText.substring(0, 16);
            Component title = Component.text(titleText, winner == null ? NamedTextColor.YELLOW : color(winner)).decorate(TextDecoration.BOLD);
            Component sub = Component.text(winner == null ? reason : "remporte la partie !", winner == null ? NamedTextColor.GRAY : NamedTextColor.GOLD);
            Set<UUID> audience = new LinkedHashSet<>(teamOf.keySet());
            audience.addAll(spectatorsOf(this));
            for (Player p : online(audience)) {
                p.showTitle(Title.title(title, sub));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (bar != null) p.hideBossBar(bar);
            }
            List<UUID> top = new ArrayList<>(kills.keySet());
            top.sort((a, b) -> kills.get(b) - kills.get(a));
            StringBuilder sb = new StringBuilder("§6" + (winner == null ? "Égalité" : "Victoire de " + teamName(winner)) + " §7(" + reason + ")");
            if (winner != null && mode.size > 1) sb.append(" §f").append(String.join(", ", memberNames(winner)));
            if (!top.isEmpty()) {
                sb.append(" §7Kills :");
                for (int i = 0; i < Math.min(3, top.size()); i++) sb.append(" §f").append(names.get(top.get(i))).append(" ").append(kills.get(top.get(i)));
            }
            broadcast(sb.toString());
            getLogger().info("Fin de la partie " + index + " (" + worldName + ") : " + lastWinner + " (" + reason + ")");
        }

        /** Fin de partie : tout le monde au hub, désinscrit ; suppression du monde de cette partie. */
        void finish() {
            removeColorTeams();
            List<UUID> ids = new ArrayList<>(teamOf.keySet());
            games.remove(index);
            deleting.add(index);
            for (UUID id : ids) {
                playerGame.remove(id);
                queue.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                if (bar != null) p.hideBossBar(bar);
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                sendToHub(p);
                p.sendMessage(Component.text("[UHC] ", NamedTextColor.GOLD).append(Component.text("Retour au hub. ", NamedTextColor.GRAY))
                        .append(Component.text("[Rejouer]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/uhc join " + mode.name().toLowerCase()))));
            }
            teamOf.clear();
            alive.clear();
            boards.clear();
            bar = null;
            for (UUID id : spectatorsOf(this)) {
                spectatorGame.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                sendToHub(p);
                p.sendMessage(PREFIX + "§7Partie terminée, retour au hub.");
            }
            reportStatus();
            Bukkit.getScheduler().runTaskLater(EemiUHC.this, () -> {
                deleteWorld(worldName);
                deleting.remove(index);
            }, 40L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, "Partie arrêtée par un admin.", false);
            if (state == State.ENDING && games.get(index) == this) finish();
        }

        // -------------------------------------------------- équipes

        String teamName(Integer team) {
            if (team == null) return "?";
            if (mode.size == 1) {
                for (Map.Entry<UUID, Integer> e : teamOf.entrySet()) if (e.getValue().equals(team)) return names.getOrDefault(e.getKey(), "?");
                return "?";
            }
            return TeamColor.values()[team % TeamColor.values().length].label;
        }

        NamedTextColor color(Integer team) {
            if (team == null || mode.size == 1) return NamedTextColor.WHITE;
            return TeamColor.values()[team % TeamColor.values().length].color;
        }

        List<String> memberNames(Integer team) {
            List<String> list = new ArrayList<>();
            for (Map.Entry<UUID, Integer> e : teamOf.entrySet()) if (e.getValue().equals(team)) list.add(names.getOrDefault(e.getKey(), "?"));
            return list;
        }

        boolean sameTeam(Player a, Player b) {
            Integer ta = teamOf.get(a.getUniqueId()), tb = teamOf.get(b.getUniqueId());
            return ta != null && ta.equals(tb) && !a.equals(b);
        }

        // -------------------------------------------------- scoreboard latéral, pseudos colorés, vie dans le tab

        private void createBoard(Player p) {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = b.registerNewObjective("uhc", Criteria.DUMMY, Component.text("UHC", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            try {
                o.numberFormat(NumberFormat.blank());
            } catch (Throwable ignored) {
                // numéros visibles si non supporté
            }
            Objective hp = b.registerNewObjective("uhc_vie", Criteria.HEALTH, Component.text("Vie", NamedTextColor.RED));
            hp.setDisplaySlot(DisplaySlot.PLAYER_LIST);
            boards.put(p.getUniqueId(), b);
            p.setScoreboard(b);
        }

        private void updateSidebars() {
            int players = alive.size(), teams = aliveTeams().size();
            boolean pvp = Boolean.TRUE.equals(world.getGameRuleValue(GameRules.PVP));
            for (Player p : online(teamOf.keySet())) {
                UUID id = p.getUniqueId();
                List<Component> lines = new ArrayList<>();
                lines.add(Component.empty());
                lines.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(clock(elapsed), NamedTextColor.YELLOW)));
                if (mode.size > 1) lines.add(Component.text("Équipe : ", NamedTextColor.GRAY).append(Component.text(teamName(teamOf.get(id)), color(teamOf.get(id)))));
                lines.add(Component.text("Kills : ", NamedTextColor.GRAY).append(Component.text(kills.getOrDefault(id, 0), NamedTextColor.WHITE)));
                if (!alive.contains(id)) lines.add(Component.text("Éliminé (spectateur)", NamedTextColor.DARK_GRAY));
                lines.add(Component.empty());
                lines.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players + (mode.size > 1 ? " (" + teams + " équipes)" : ""), NamedTextColor.WHITE)));
                lines.add(Component.text("Bordure : ", NamedTextColor.GRAY).append(Component.text("+-" + (int) Math.round(world.getWorldBorder().getSize() / 2), NamedTextColor.WHITE)));
                if (p.getWorld() == world) {
                    Location l = p.getLocation();
                    lines.add(Component.text("Centre : ", NamedTextColor.GRAY).append(Component.text((int) Math.round(l.distance(new Location(world, center.getX(), l.getY(), center.getZ()))) + " blocs", NamedTextColor.WHITE)));
                }
                lines.add(Component.empty());
                if (state == State.PLAYING) {
                    lines.add(Component.text(nextStep(), NamedTextColor.GOLD));
                    lines.add(Component.text(pvp ? "PvP actif" : "PvP désactivé", pvp ? NamedTextColor.RED : NamedTextColor.GREEN));
                } else {
                    lines.add(Component.text("Gagnant : ", NamedTextColor.GRAY).append(Component.text(lastWinner == null ? "-" : lastWinner, NamedTextColor.GOLD)));
                    lines.add(Component.text("Hub dans " + Math.max(0, timer) + " s", NamedTextColor.GRAY));
                }
                lines.add(Component.empty());
                lines.add(Component.text("Mode " + mode.label + " - partie " + index, NamedTextColor.DARK_GREEN));
                setLines(id, lines);
            }
        }

        /** Une équipe par ligne, entrée invisible (code couleur) et texte dans le prefix. */
        private void setLines(UUID id, List<Component> lines) {
            Scoreboard b = boards.get(id);
            if (b == null) return;
            Objective o = b.getObjective("uhc");
            if (o == null) return;
            String hex = "0123456789abcdef";
            for (int i = 0; i < 15; i++) {
                String entry = "§" + hex.charAt(i) + "§r";
                Team team = b.getTeam("uhc_l" + i);
                if (i < lines.size()) {
                    if (team == null) {
                        team = b.registerNewTeam("uhc_l" + i);
                        team.addEntry(entry);
                    }
                    team.prefix(lines.get(i));
                    o.getScore(entry).setScore(lines.size() - i);
                } else if (team != null) {
                    b.resetScores(entry);
                }
            }
        }

        /** Pseudos colorés par équipe (duo et trio) sur le scoreboard de chaque participant. */
        private void colorNames() {
            if (mode.size == 1) return;
            for (Scoreboard board : boards.values()) {
                for (Map.Entry<UUID, Integer> e : teamOf.entrySet()) {
                    String player = names.get(e.getKey());
                    if (player == null) continue;
                    TeamColor c = TeamColor.values()[e.getValue() % TeamColor.values().length];
                    String name = "uhc_" + c.name().toLowerCase();
                    Team team = board.getTeam(name);
                    if (team == null) {
                        team = board.registerNewTeam(name);
                        team.color(c.color);
                        team.prefix(Component.text("[" + c.label + "] ", c.color));
                        team.setAllowFriendlyFire(false);
                    }
                    team.addEntry(player);
                }
            }
        }

        private void removeColorTeams() {
            for (Scoreboard board : boards.values()) {
                for (TeamColor c : TeamColor.values()) {
                    Team team = board.getTeam("uhc_" + c.name().toLowerCase());
                    if (team != null) team.unregister();
                }
            }
        }

        /** Message aux participants (vivants et éliminés) et aux spectateurs de cette partie. */
        void broadcast(String msg) {
            Set<UUID> audience = new LinkedHashSet<>(teamOf.keySet());
            audience.addAll(spectatorsOf(this));
            broadcastTo(audience, msg);
        }
    }

    // ------------------------------------------------------------------ têtes d'or et Hastey Boys

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, goldenHead());
        recipe.shape("GGG", "GHG", "GGG");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('H', Material.PLAYER_HEAD);
        Bukkit.addRecipe(recipe);
    }

    private ItemStack goldenHead() {
        ItemStack it = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Tête d'or", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Régénération II " + cfg("golden-head.regen-seconds", 10) + " s + Absorption", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(headKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isGoldenHead(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(headKey, PersistentDataType.BYTE);
    }

    private ItemStack playerHead(UUID id, String name) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        meta.displayName(Component.text("Tête de " + name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("8 lingots d'or autour = tête d'or", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        it.setItemMeta(meta);
        return it;
    }

    private static boolean isTool(Material m) {
        return Tag.ITEMS_PICKAXES.isTagged(m) || Tag.ITEMS_AXES.isTagged(m) || Tag.ITEMS_SHOVELS.isTagged(m);
    }

    private ItemStack hastey(ItemStack it) {
        if (!scenario("hastey-boys") || !isTool(it.getType())) return it;
        it.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3);
        it.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return it;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (result == null) return;
        boolean playing = e.getView().getPlayer() instanceof Player p && inGame(p);
        if (isGoldenHead(result)) {
            if (!playing) e.getInventory().setResult(null);
            return;
        }
        if (playing && isTool(result.getType()) && result.getEnchantments().isEmpty()) e.getInventory().setResult(hastey(result.clone()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!isGoldenHead(e.getItem())) return;
        Player p = e.getPlayer();
        int regen = cfg("golden-head.regen-seconds", 10) * 20, abs = cfg("golden-head.absorption-seconds", 120) * 20;
        // après les effets de la pomme d'or, qu'on remplace
        Bukkit.getScheduler().runTask(this, () -> {
            if (!isGameWorld(p.getWorld())) return;
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regen, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, abs, 0));
        });
    }

    // ------------------------------------------------------------------ règles des mondes de partie

    /** Pas de régénération naturelle (en plus de la gamerule, au cas où un autre plugin soigne par la faim). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isGameWorld(p.getWorld())) return;
        if (e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED && e.getRegainReason() != EntityRegainHealthEvent.RegainReason.REGEN) return;
        Game g = playerGame.get(p.getUniqueId());
        if (g == null || g.state != State.PLAYING || g.elapsed >= regenUntil()) e.setCancelled(true);
    }

    /** Fin de la régénération naturelle (secondes de partie) : par défaut au final heal. 0 = jamais de régénération. */
    private int regenUntil() {
        return cfg("regen-until", cfg("final-heal", 540));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = playerGame.get(p.getUniqueId());
        if (g == null || !g.alive.contains(p.getUniqueId())) return;
        if (g.state != State.PLAYING || g.elapsed < cfg("invincibility", 10)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Game g = playerGame.get(victim.getUniqueId());
        if (g == null || !g.alive.contains(victim.getUniqueId())) return;
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (playerGame.get(attacker.getUniqueId()) != g || g.elapsed < cfg("pvp-time", 600) || g.sameTeam(attacker, victim)) e.setCancelled(true);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (isGameWorld(e.getFrom().getWorld())) e.setCancelled(true);
    }

    /** Cut clean : minerais en lingots, silex garanti ; pommes plus fréquentes. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent e) {
        if (!isGameWorld(e.getBlock().getWorld())) return;
        Material broken = e.getBlockState().getType();
        for (Item item : e.getItems()) {
            ItemStack s = item.getItemStack();
            Material to = switch (s.getType()) {
                case RAW_IRON -> Material.IRON_INGOT;
                case RAW_GOLD -> Material.GOLD_INGOT;
                case RAW_COPPER -> Material.COPPER_INGOT;
                case GRAVEL -> broken == Material.GRAVEL ? Material.FLINT : null;
                default -> null;
            };
            if (to != null) s = s.withType(to);
            // 1.3 : minerais doublés (ore-multiplier)
            if (broken.name().endsWith("_ORE") || broken == Material.ANCIENT_DEBRIS)
                s.setAmount(Math.min(s.getMaxStackSize(), s.getAmount() * Math.max(1, cfg("ore-multiplier", 2))));
            item.setItemStack(s);
        }
        if (Tag.LEAVES.isTagged(broken)) maybeApple(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (isGameWorld(e.getBlock().getWorld())) maybeApple(e.getBlock());
    }

    private void maybeApple(Block b) {
        if (ThreadLocalRandom.current().nextDouble() < getConfig().getDouble("apple-chance", 0.03)) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.APPLE));
        }
    }

    /** Cut clean : viande cuite. */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player || !isGameWorld(e.getEntity().getWorld())) return;
        ListIterator<ItemStack> it = e.getDrops().listIterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            Material to = switch (s.getType()) {
                case BEEF -> Material.COOKED_BEEF;
                case PORKCHOP -> Material.COOKED_PORKCHOP;
                case CHICKEN -> Material.COOKED_CHICKEN;
                case MUTTON -> Material.COOKED_MUTTON;
                case RABBIT -> Material.COOKED_RABBIT;
                case COD -> Material.COOKED_COD;
                case SALMON -> Material.COOKED_SALMON;
                default -> null;
            };
            if (to != null) it.set(s.withType(to));
        }
    }

    /** Timber : casser une bûche naturelle abat tout l'arbre. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block start = e.getBlock();
        if (!scenario("timber") || !inGame(p) || !Tag.OVERWORLD_NATURAL_LOGS.isTagged(start.getType())) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> todo = new ArrayDeque<>();
        seen.add(start);
        todo.add(start);
        List<Block> logs = new ArrayList<>();
        while (!todo.isEmpty() && logs.size() < 256) {
            Block b = todo.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (seen.add(n) && Tag.OVERWORLD_NATURAL_LOGS.isTagged(n.getType())) {
                            logs.add(n);
                            todo.add(n);
                        }
                    }
                }
            }
        }
        Bukkit.getScheduler().runTask(this, () -> {
            for (Block b : logs) if (Tag.OVERWORLD_NATURAL_LOGS.isTagged(b.getType())) b.breakNaturally(tool);
        });
    }

    // ------------------------------------------------------------------ mort, départ, reconnexion

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Game g = playerGame.get(p.getUniqueId());
        if (g == null || !g.alive.contains(p.getUniqueId())) return;
        e.deathMessage(null);
        e.setKeepInventory(false);
        e.getDrops().add(playerHead(p.getUniqueId(), p.getName()));
        Location at = p.getLocation();
        g.lastLoc.put(p.getUniqueId(), at);
        p.getWorld().strikeLightningEffect(at);
        Player killer = p.getKiller();
        String how = switch (p.getLastDamageCause() == null ? EntityDamageEvent.DamageCause.CUSTOM : p.getLastDamageCause().getCause()) {
            case FALL -> "s'est écrasé au sol";
            case WORLD_BORDER -> "a été rattrapé par la bordure";
            case LAVA, FIRE, FIRE_TICK -> "a brûlé";
            case DROWNING -> "s'est noyé";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "a explosé";
            case ENTITY_ATTACK, PROJECTILE -> "a été tué par un monstre";
            case STARVATION -> "est mort de faim";
            case SUFFOCATION -> "a suffoqué";
            default -> "est mort";
        };
        g.eliminate(p.getUniqueId(), killer, how);
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && p.isDead()) p.spigot().respawn(); }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Game g = playerGame.get(id);
        if (g == null || !games.containsValue(g)) return;
        // spectateur là où il est mort (ou au centre s'il est tombé dans le vide)
        Location at = g.lastLoc.getOrDefault(id, g.center);
        if (at.getWorld() != g.world || at.getY() < g.world.getMinHeight() + 5) at = g.center.clone().add(0, 30, 0);
        e.setRespawnLocation(at);
        later(() -> {
            if (p.isOnline() && playerGame.get(id) == g && p.getWorld() == g.world) p.setGameMode(GameMode.SPECTATOR);
        });
    }

    /**
     * Un vivant qui s'en va laisse ses objets et sa tête là où il était. S'il a déjà changé de monde (/lobby,
     * /is...), son inventaire est celui du monde d'arrivée (Multiverse-Inventories a gardé celui de la partie) :
     * on n'y touche pas, seule la tête est laissée à sa dernière position.
     */
    private void dropAndEliminate(Game g, Player p, String how) {
        UUID id = p.getUniqueId();
        boolean here = p.getWorld() == g.world;
        Location at = here ? p.getLocation() : g.lastLoc.get(id);
        if (at != null && at.getWorld() == g.world) {
            if (here) for (ItemStack it : p.getInventory().getContents()) if (it != null && !it.getType().isAir()) g.world.dropItemNaturally(at, it);
            g.world.dropItemNaturally(at, playerHead(id, p.getName()));
        }
        if (here) p.getInventory().clear();
        g.eliminate(id, null, how);
    }

    /** Quitte la partie (participant) : éliminé s'il était vivant, retiré de la partie. */
    private void quitGame(Player p, String how, boolean toHub) {
        UUID id = p.getUniqueId();
        Game g = playerGame.get(id);
        if (g == null) return;
        if (g.alive.contains(id)) dropAndEliminate(g, p, how);
        g.teamOf.remove(id);
        playerGame.remove(id);
        queue.remove(id);
        if (g.bar != null) p.hideBossBar(g.bar);
        g.boards.remove(id);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (toHub) sendToHub(p);
        else if (isHub(p.getWorld())) {
            // départ par changement de monde : seulement au hub (ailleurs, skyblock..., on ne touche à rien)
            for (PotionEffect ef : p.getActivePotionEffects()) p.removePotionEffect(ef.getType());
            if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
        }
        if (g.state == State.PLAYING && g.teamOf.isEmpty()) g.forceStop();
        reportStatus();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Game watched = spectatorGame.get(id);
        if (watched != null && p.getWorld() != watched.world) {
            // spectateur parti (/lobby) : il redevient un joueur normal
            spectatorGame.remove(id);
            // seulement au hub : dans un autre monde (skyblock...), on ne touche pas au mode de jeu
            if (p.getGameMode() == GameMode.SPECTATOR && isHub(p.getWorld())) p.setGameMode(GameMode.SURVIVAL);
        }
        Game g = playerGame.get(id);
        if (g != null) {
            if (p.getWorld() == g.world) return;
            // /lobby, menu, téléportation : éliminé et objets laissés sur place
            quitGame(p, "a quitté la partie", false);
            p.sendMessage(PREFIX + "§7Tu as quitté l'UHC.");
            return;
        }
        World hub = hubWorld();
        if (queue.containsKey(id) && hub != null && p.getWorld() != hub) leaveQueue(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // reconnecté dans un monde UHC sans être en partie : retour au hub
        if (isGameWorld(p.getWorld())) Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline() && !playerGame.containsKey(p.getUniqueId())) sendToHub(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        removeLeaveItem(p);
        spectatorGame.remove(p.getUniqueId());
        if (playerGame.containsKey(p.getUniqueId())) quitGame(p, "s'est déconnecté", true);
        else if (queue.remove(p.getUniqueId()) != null) reportStatus();
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
            Game g = playerGame.get(p.getUniqueId());
            if (g == null) g = spectatorGame.get(p.getUniqueId());
            if (g == null) g = gameOfWorld(p.getWorld());
            if (g != null) return g;
        }
        if (games.containsKey(1)) return games.get(1);
        return games.size() == 1 ? games.values().iterator().next() : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = sender instanceof Player pl ? pl : null;
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        boolean admin = sender.hasPermission("eemiuhc.admin");
        switch (sub) {
            case "" -> {
                if (p != null && queue.containsKey(p.getUniqueId()) && !playerGame.containsKey(p.getUniqueId()))
                    p.sendMessage(PREFIX + "§7Tu attends dans la file §6" + queue.get(p.getUniqueId()).label + "§7. §f/uhc leave §7pour quitter.");
                else sendMenu(sender);
            }
            case "join", "solo", "duo", "trio" -> {
                // /uhc join <solo|duo|trio> [joueur]  ou  /uhc duo
                if (sub.equals("join") && args.length < 2) { sendMenu(sender); return true; }
                Mode chosen = parseMode(sub.equals("join") ? args[1] : sub);
                if (chosen == null) { sender.sendMessage(PREFIX + "§7/uhc join <solo|duo|trio>"); return true; }
                int nameArg = sub.equals("join") ? 2 : 1;
                Player target = args.length > nameArg && admin ? Bukkit.getPlayerExact(args[nameArg]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target, chosen);
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "leave" -> {
                if (p == null) return true;
                if (spectatorGame.containsKey(p.getUniqueId())) {
                    spectatorGame.remove(p.getUniqueId());
                    sendToHub(p);
                } else if (playerGame.containsKey(p.getUniqueId())) {
                    quitGame(p, "a abandonné", true);
                    p.sendMessage(PREFIX + "§7Tu as quitté l'UHC.");
                } else {
                    leaveQueue(p);
                }
            }
            case "start" -> {
                // force le lancement d'une file, même incomplète, dans une nouvelle partie : /uhc start <solo|duo|trio>
                if (!admin) return true;
                Mode m = args.length > 1 ? parseMode(args[1]) : null;
                if (m == null) { sender.sendMessage(PREFIX + "§7/uhc start <solo|duo|trio>"); return true; }
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
                if (g == null) { sender.sendMessage(PREFIX + "§cAucune partie trouvée. §7/uhc stop [numéro] (voir /uhc parties)"); return true; }
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie " + g.index + " arrêtée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties : " + games.size() + "/" + maxInstances());
                for (Game g : games.values()) {
                    sender.sendMessage("§8- §f#" + g.index + " §7" + g.worldName + " §f" + g.mode.label + " §7" + (g.state == State.PLAYING ? "en jeu " + clock(g.elapsed) : "fin")
                            + " §7- " + g.alive.size() + " vivants / " + g.teamOf.size() + " joueurs, " + spectatorsOf(g).size() + " spectateur(s)");
                }
                StringBuilder sb = new StringBuilder("§7Files :");
                for (Mode m : Mode.values()) {
                    sb.append(" §f").append(m.label).append(" ").append(waitingFor(m).size());
                    Integer t = countdown.get(m);
                    if (t != null) sb.append(" §7(").append(t).append(" s)");
                }
                sender.sendMessage(sb.toString());
            }
            case "reload" -> {
                if (!admin) return true;
                reloadConfig();
                registerRecipe();
                sender.sendMessage(PREFIX + "§7Configuration rechargée (les durées s'appliquent à la prochaine partie ou en cours).");
            }
            case "files", "queues" -> {
                StringBuilder sb = new StringBuilder("§7Files :");
                for (Mode m : Mode.values()) sb.append(" §f").append(m.label).append(" ").append(waitingFor(m).size());
                sb.append(" §7(min ").append(minPlayers()).append(", max ").append(maxPlayers()).append(") - ")
                        .append(games.size()).append("/").append(maxInstances()).append(" partie(s) en cours");
                for (Game g : games.values()) {
                    if (g.state == State.PLAYING) sb.append(" §7- #").append(g.index).append(" ").append(g.mode.label).append(" : ")
                            .append(g.alive.size()).append(" vivants, ").append(clock(g.elapsed));
                }
                sender.sendMessage(PREFIX + sb);
            }
            default -> sender.sendMessage(PREFIX + "§7/uhc <join solo|duo|trio|leave|files|spectate [joueur]>"
                    + (admin ? " §8| admin : start <mode>, stop [n], parties, reload" : ""));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("join", "leave", "files", "spectate", "solo", "duo", "trio"));
            if (sender.hasPermission("eemiuhc.admin")) subs.addAll(List.of("start", "stop", "parties", "reload"));
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

    /** Regarder une partie en spectateur : celle du joueur nommé, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String targetName) {
        UUID id = p.getUniqueId();
        if (playerGame.containsKey(id) || queue.containsKey(id)) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/uhc leave §cd'abord."); return; }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            if (t != null) g = playerGame.get(t.getUniqueId());
            if (g == null) { p.sendMessage(PREFIX + "§c" + targetName + " n'est pas dans une partie d'UHC."); return; }
        } else {
            for (Game o : games.values()) {
                if (o.state != State.PLAYING) continue;
                if (g == null || o.alive.size() > g.alive.size()) g = o;
            }
        }
        if (g == null || g.state != State.PLAYING) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return; }
        List<Player> vivants = new ArrayList<>();
        Player followed = targetName == null ? null : Bukkit.getPlayerExact(targetName);
        if (followed != null && followed.getWorld() == g.world && g.alive.contains(followed.getUniqueId())) vivants.add(followed);
        else for (Player o : online(g.alive)) if (o.getWorld() == g.world) vivants.add(o);
        Location at = vivants.isEmpty() ? g.center.clone().add(0, 30, 0)
                : vivants.get(ThreadLocalRandom.current().nextInt(vivants.size())).getLocation().add(0, 8, 0);
        spectatorGame.remove(id); // s'il regardait déjà une partie
        // téléportation d'abord : vider avant aurait effacé l'inventaire du monde de départ (skyblock...)
        p.teleport(at);
        if (p.getWorld() != g.world) return;
        p.getInventory().clear();
        if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
        spectatorGame.put(id, g); // après la téléportation (le changement de monde retirerait le spectateur)
        p.sendMessage(PREFIX + "§7Tu regardes la partie " + g.index + ", §f/lobby §7pour revenir.");
    }

    // ------------------------------------------------------------------ EemiCore

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /**
     * Statut agrégé pour EemiCore : waiting si des joueurs attendent dans une file (sinon playing si une partie
     * tourne), attente = inscrits des files, en jeu = vivants de toutes les parties, max = max-players × max-instances.
     */
    private void reportStatus() {
        int waiting = 0;
        for (Mode m : Mode.values()) waiting += waitingFor(m).size();
        int playing = 0;
        boolean anyPlaying = false;
        for (Game g : games.values()) {
            playing += g.alive.size();
            if (g.state == State.PLAYING) anyPlaying = true;
        }
        String state = waiting > 0 || !countdown.isEmpty() ? "waiting" : (anyPlaying ? "playing" : "waiting");
        String status = "status uhc " + state + " " + waiting + " " + playing + " " + (maxPlayers() * maxInstances());
        long now = System.currentTimeMillis();
        if (status.equals(lastStatus) && now - lastStatusAt < 30_000) return;
        lastStatus = status;
        lastStatusAt = now;
        eemi(status);
    }

    /** Choix du mode cliquable dans le chat. */
    private void sendMenu(CommandSender s) {
        Component msg = Component.text("[UHC] ", NamedTextColor.GOLD).append(Component.text("Choisis ton mode : ", NamedTextColor.GRAY));
        for (Mode m : Mode.values()) {
            if (!modeEnabled(m)) continue;
            msg = msg.append(Component.text("[" + m.label + "] ", NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/uhc join " + m.name().toLowerCase())));
        }
        s.sendMessage(msg);
    }

    // ------------------------------------------------------------------ utilitaires

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
