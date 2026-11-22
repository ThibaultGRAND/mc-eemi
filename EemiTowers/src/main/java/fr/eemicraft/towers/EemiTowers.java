package fr.eemicraft.towers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Towers d'EEMICRAFT, sur la map « The Towers Remastered » (CrossFace22, d'après Quijx).
 * Rouge contre Bleu : chaque camp est une ligne de tours au-dessus du vide ; au centre, trois tours
 * avec du stuff puissant (balise, générateurs de fer et de lapis, coffres). Les joueurs construisent
 * leurs ponts avec leurs blocs ; sauter dans le bassin adverse (au-dessus de son spawn) = 1 point.
 *
 * Plusieurs parties en même temps (max-instances, 3 par défaut) : la partie 1 joue dans le monde
 * d'origine (thetowers), la partie n ≥ 2 dans une copie « thetowers_n » faite depuis « thetowers_modele »
 * (copie propre faite au démarrage après la remise à zéro de la partie 1). Chaque partie a son salon
 * d'attente (la salle d'accueil de la map, dans son monde) et se remet à zéro dans SON monde depuis la
 * copie de sauvegarde incluse dans la map (8 clone lancés par « execute in <son monde> »).
 * Toutes les coordonnées viennent de la config (relevées dans le datapack d'origine).
 */
public final class EemiTowers extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§9The Towers§8] §7";
    /** Délai entre la destruction d'une copie et la création d'une nouvelle copie du même nom (suppression asynchrone). */
    private static final long RECREATE_DELAY_MS = 10_000L;

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Side {
        ROUGE("Rouge", NamedTextColor.RED, Color.RED, Material.RED_STAINED_GLASS),
        BLEU("Bleu", NamedTextColor.BLUE, Color.fromRGB(0x3C44AA), Material.BLUE_STAINED_GLASS);

        final String label;
        final NamedTextColor color;
        final Color armor;
        final Material glass;

        Side(String label, NamedTextColor color, Color armor, Material glass) {
            this.label = label;
            this.color = color;
            this.armor = armor;
            this.glass = glass;
        }

        Side other() {
            return this == ROUGE ? BLEU : ROUGE;
        }

        /** Nom accordé avec « équipe » (féminin) : Rouge, Bleue. */
        String fem() {
            return this == BLEU ? "Bleue" : label;
        }

        String code() {
            return this == ROUGE ? "§c" : "§9";
        }

        String key() {
            return name().toLowerCase();
        }
    }

    /** Boîte de blocs (bornes incluses). */
    private record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        static Box parse(String s) {
            String[] p = s.split(",");
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(p[i].trim());
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

    private NamespacedKey kitKey;
    /** Nom du monde d'origine (partie 1) ; copies « base_n », modèle « base_modele ». */
    private String base;
    /** Positions sans monde : chaque partie les place dans son propre monde. */
    private Location lobby;
    private final Map<Side, Location> spawns = new HashMap<>();
    private final Map<Side, Box> pools = new HashMap<>();
    private final List<Box> protectedBoxes = new ArrayList<>();
    private Box area;
    private int voidY;

    /** Parties par numéro (la partie 1 existe toujours). */
    private final TreeMap<Integer, Game> games = new TreeMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private final Map<UUID, Game> bySpectator = new HashMap<>();
    /** Copies détruites récemment (nom → heure) : on attend la fin de la suppression avant d'en refaire une. */
    private final Map<String, Long> destroyedAt = new HashMap<>();
    /** Modèle en cours de copie (pas de nouvelle copie, pas de départ de la partie 1 pendant ce temps). */
    private boolean modelBusy;
    private int pendingCopies;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        saveDefaultConfig();
        loadSettings();
        Game first = new Game(1);
        first.world = Bukkit.getWorld(base);
        games.put(1, first);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::spawnItems, 100L, 20L * Math.max(1, cfg("generator-interval", 5)));
        // copies restées sur le disque, remise à zéro de la partie 1, puis modèle propre
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = Bukkit.getWorld(base);
            if (w == null) {
                getLogger().warning("Monde " + base + " introuvable : pas de modèle, une seule partie.");
                return;
            }
            WorldClone.cleanup(this, w, base, maxInstances());
            Game g1 = games.get(1);
            g1.world = w;
            modelBusy = true; // la partie 1 ne démarre pas avant que le modèle soit copié
            g1.resetMap(() -> {
                modelBusy = false;
                refreshModel();
            });
        }, 20L);
        reportStatus();
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) g.shutdown();
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "thetowers");
        if (Bukkit.getWorld(base) == null) getLogger().warning("Monde " + base + " introuvable.");
        lobby = loc(getConfig().getString("lobby", "0.5,64,1024.5,180,0"));
        for (Side s : Side.values()) {
            spawns.put(s, loc(getConfig().getString("spawn." + s.key())));
            pools.put(s, Box.parse(getConfig().getString("pool." + s.key())));
        }
        protectedBoxes.clear();
        for (String b : getConfig().getStringList("protected")) protectedBoxes.add(Box.parse(b));
        area = Box.parse(getConfig().getString("area", "-103,0,1122,104,268,1182"));
        voidY = cfg("void-y", 160);
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private int maxPlayers() {
        return cfg("max-players", 16);
    }

    /** Position de config sans monde (placée dans le monde de chaque partie par at()). */
    private static Location loc(String s) {
        String[] p = s.split(",");
        Location l = new Location(null, Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
        if (p.length >= 5) {
            l.setYaw(Float.parseFloat(p[3].trim()));
            l.setPitch(Float.parseFloat(p[4].trim()));
        }
        return l;
    }

    private static Location at(Location tpl, World w) {
        Location l = tpl.clone();
        l.setWorld(w);
        return l;
    }

    // ------------------------------------------------------------------ mondes : modèle et copies

    private String modelName() {
        return base + "_modele";
    }

    /** Monde de partie (origine ou copie _n) ou modèle : toutes les protections s'y appliquent. */
    private boolean isTowersWorld(World w) {
        return w != null && (WorldClone.index(base, w.getName()) > 0 || w.getName().equals(modelName()));
    }

    private Game gameOfWorld(World w) {
        if (w == null) return null;
        for (Game g : games.values()) if (w.equals(g.world)) return g;
        return null;
    }

    /**
     * (Re)fait « base_modele » depuis le monde d'origine. Seulement quand la partie 1 n'est pas en cours
     * et qu'aucune copie n'est en train d'être faite depuis l'ancien modèle.
     */
    private boolean refreshModel() {
        World template = Bukkit.getWorld(base);
        Game g1 = games.get(1);
        if (template == null || modelBusy || pendingCopies > 0) return false;
        if (g1.state == State.PLAYING || g1.state == State.ENDING || g1.resetting) return false;
        World old = Bukkit.getWorld(modelName());
        if (old != null && !Bukkit.unloadWorld(old, false)) {
            getLogger().warning("Impossible de décharger " + old.getName() + " : modèle non refait.");
            return false;
        }
        modelBusy = true;
        WorldClone.create(this, template, modelName(), w -> {
            modelBusy = false;
            if (w == null) getLogger().warning("Modèle " + modelName() + " non cree : pas de partie 2+.");
        });
        return true;
    }

    /** Copie « base_n » depuis le modèle (attend le modèle et la fin de la suppression d'une ancienne copie). */
    private void requestCopy(int n, Consumer<World> done) {
        String name = WorldClone.name(base, n);
        World model = Bukkit.getWorld(modelName());
        long since = System.currentTimeMillis() - destroyedAt.getOrDefault(name, 0L);
        if (model == null || modelBusy || since < RECREATE_DELAY_MS) {
            if (model == null && !modelBusy && !refreshModel()) {
                done.accept(null);
                return;
            }
            Bukkit.getScheduler().runTaskLater(this, () -> requestCopy(n, done), 40L);
            return;
        }
        pendingCopies++;
        WorldClone.create(this, model, name, w -> {
            pendingCopies--;
            done.accept(w);
        });
    }

    private void destroyCopy(World w) {
        if (w == null || w.getName().equals(base)) return;
        destroyedAt.put(w.getName(), System.currentTimeMillis());
        WorldClone.destroy(this, w, base, this::sendToHub);
    }

    // ------------------------------------------------------------------ boucles

    private void tick() {
        for (Game g : new ArrayList<>(games.values())) g.tick();
    }

    /** Générateurs du centre : un objet toutes les N secondes si un joueur est proche et qu'il n'y en a pas déjà un. */
    private void spawnItems() {
        for (Game g : games.values()) if (g.state == State.PLAYING && g.world != null) g.spawnItems();
    }

    /** Statut agrégé pour EemiCore : un seul envoi pour toutes les parties. */
    private void reportStatus() {
        boolean anyWaiting = false, anyPlaying = false;
        long waiting = 0;
        int playing = 0;
        for (Game g : games.values()) {
            if (g.state == State.WAITING || g.state == State.STARTING) anyWaiting = true;
            else anyPlaying = true;
            waiting += g.players.stream().filter(u -> !g.teams.containsKey(u)).count();
            if (g.state == State.PLAYING || g.state == State.ENDING) playing += g.teams.size();
        }
        String st = anyWaiting ? "waiting" : anyPlaying ? "playing" : "waiting";
        eemi("status towers " + st + " " + waiting + " " + playing + " " + (maxPlayers() * maxInstances()));
    }

    // ------------------------------------------------------------------ inscription (choix de la partie)

    private void join(Player p) {
        if (byPlayer.containsKey(p.getUniqueId())) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (Bukkit.getWorld(base) == null) { p.sendMessage(PREFIX + "§cLa map n'est pas chargée."); return; }
        int max = maxPlayers();
        // partie en attente ou en décompte avec de la place, la plus remplie d'abord (garde les groupes ensemble)
        Game best = games.values().stream()
                .filter(g -> (g.state == State.WAITING || g.state == State.STARTING) && g.players.size() < max)
                .max(Comparator.<Game>comparingInt(g -> g.players.size()).thenComparing(g -> -g.n))
                .orElse(null);
        if (best == null && games.size() < maxInstances()) {
            int n = 2;
            while (games.containsKey(n)) n++;
            best = new Game(n);
            games.put(n, best);
            best.create();
        }
        if (best == null) {
            // toutes les parties jouent : file d'attente (salon de la partie) pour la suivante, comme avant
            best = games.values().stream().filter(g -> g.players.size() < max && g.world != null)
                    .min(Comparator.comparingInt(g -> g.players.size())).orElse(null);
        }
        if (best == null) { p.sendMessage(PREFIX + "§cToutes les parties sont pleines."); return; }
        best.join(p);
    }

    private void removeGame(Game g) {
        if (g.n == 1 || g.removed) return;
        g.removed = true;
        g.prepToken++;
        for (UUID id : g.players) byPlayer.remove(id);
        g.sendSpectatorsToHub();
        if (g.world != null) destroyCopy(g.world);
        g.world = null;
        games.remove(g.n);
        getLogger().info("Partie de Towers " + g.n + " fermée.");
        reportStatus();
    }

    /**
     * Le plugin ne touche au joueur (inventaire, armure, XP, vie, faim, effets, mode de jeu) que s'il est
     * dans un monde du jeu (partie, copies, modèle) ou au hub : ailleurs (skyblock...) c'est l'inventaire
     * de CE monde que Multiverse-Inventories sauvegarderait.
     */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        return isTowersWorld(w) || w.getName().equals(getConfig().getString("hub-world", "lobby"));
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
    }

    // ================================================================== une partie

    final class Game {
        final int n;
        /** Monde de la partie (origine pour la partie 1, copie pour les autres) ; null pendant la copie. */
        World world;
        boolean removed;
        int prepToken;
        /** Copie vide pendant une remise à zéro : à fermer dès qu'elle est finie. */
        boolean closeAfterReset;

        final Set<UUID> players = new LinkedHashSet<>();
        final Map<UUID, Side> teams = new HashMap<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<Side, Integer> points = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Spectateurs non inscrits de la partie en cours (/towers spectate). */
        final Set<UUID> spectators = new HashSet<>();

        State state = State.WAITING;
        int timer;
        boolean resetting;

        Game(int n) {
            this.n = n;
            points.put(Side.ROUGE, 0);
            points.put(Side.BLEU, 0);
        }

        String label() {
            return "Partie " + n;
        }

        Location lobbyLoc() {
            return at(lobby, world);
        }

        // -------------------------------------------------------------- création de la copie

        /** Partie n ≥ 2 : copie du modèle, puis téléportation des inscrits qui attendent au hub. */
        void create() {
            getLogger().info("Nouvelle partie de Towers : " + n + " (copie de " + modelName() + ").");
            int token = ++prepToken;
            requestCopy(n, w -> {
                if (removed || token != prepToken) {
                    if (w != null && gameOfWorld(w) == null) destroyCopy(w);
                    return;
                }
                if (w == null) {
                    for (Player p : online(players)) p.sendMessage(PREFIX + "§cPréparation de la partie impossible, réessaie plus tard.");
                    removeGame(this);
                    return;
                }
                world = w;
                for (Player p : online(players)) {
                    p.teleport(lobbyLoc());
                    later(() -> prepareLobbyPlayer(p));
                }
                broadcast("§aLa partie " + n + " est prête !");
                reportStatus();
            });
        }

        // -------------------------------------------------------------- boucle

        void tick() {
            switch (state) {
                case WAITING -> {
                    boolean blocked = resetting || world == null || (n == 1 && modelBusy);
                    if (!blocked && players.size() >= cfg("min-players", 2)) {
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
                        if (players.size() >= maxPlayers()) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0) for (Player p : online(players)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> {
                    timer--;
                    if (timer <= 0) {
                        int r = points.get(Side.ROUGE), b = points.get(Side.BLEU);
                        endGame(r == b ? null : (r > b ? Side.ROUGE : Side.BLEU), "Temps écoulé !");
                    }
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (world != null) for (Player p : online(players)) updateSidebar(p);
        }

        // -------------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("towers", Criteria.DUMMY,
                        Component.text("THE ", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                                .append(Component.text("TOWERS", NamedTextColor.BLUE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        /** Écrit les lignes du scoreboard (une équipe par ligne, sans clignotement). */
        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("towers");
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
                    l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(n), NamedTextColor.WHITE)));
                    l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + maxPlayers(), NamedTextColor.WHITE)));
                    l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                    l.add(Component.text("  "));
                    if (resetting) l.add(Component.text("Remise à zéro...", NamedTextColor.GOLD));
                    else if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GOLD)));
                    else l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                }
                case PLAYING, ENDING -> {
                    int goal = cfg("points-to-win", 10);
                    l.add(Component.text("Rouge : ", NamedTextColor.RED).append(Component.text(points.get(Side.ROUGE) + "/" + goal, NamedTextColor.WHITE)));
                    l.add(Component.text("Bleu : ", NamedTextColor.BLUE).append(Component.text(points.get(Side.BLEU) + "/" + goal, NamedTextColor.WHITE)));
                    l.add(Component.text("  "));
                    Side s = teams.get(p.getUniqueId());
                    if (s != null) l.add(Component.text("Équipe : ", NamedTextColor.GRAY).append(Component.text(s.fem(), s.color)));
                    l.add(Component.text("Kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                    l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(
                            String.format("%d:%02d", Math.max(0, timer) / 60, Math.max(0, timer) % 60), NamedTextColor.WHITE)));
                }
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        /** Pseudos colorés par équipe, sur le scoreboard de chaque joueur de la partie. */
        void colorNames() {
            for (Player viewer : online(players)) {
                Scoreboard b = board(viewer);
                for (Side s : Side.values()) {
                    Team t = b.getTeam("towers_" + s.key());
                    if (t == null) {
                        t = b.registerNewTeam("towers_" + s.key());
                        t.color(s.color);
                        t.prefix(Component.text("[" + s.label + "] ", s.color));
                        t.setAllowFriendlyFire(false);
                    }
                    for (String e : new ArrayList<>(t.getEntries())) t.removeEntry(e);
                }
                for (Map.Entry<UUID, Side> e : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) b.getTeam("towers_" + e.getValue().key()).addEntry(p.getName());
                }
            }
        }

        void clearNames() {
            for (Scoreboard b : boards.values())
                for (Side s : Side.values()) {
                    Team t = b.getTeam("towers_" + s.key());
                    if (t != null) t.unregister();
                }
        }

        // -------------------------------------------------------------- inscription

        void join(Player p) {
            Game old = bySpectator.remove(p.getUniqueId());
            if (old != null) old.spectators.remove(p.getUniqueId());
            players.add(p.getUniqueId());
            byPlayer.put(p.getUniqueId(), this);
            if (world == null) {
                p.sendMessage(PREFIX + "§ePréparation d'une nouvelle partie... tu seras téléporté quand elle sera prête.");
            } else {
                p.teleport(lobbyLoc());
                later(() -> prepareLobbyPlayer(p));
                if (state == State.PLAYING || state == State.ENDING) p.sendMessage(PREFIX + "§7Une partie est en cours, tu joueras à la suivante.");
            }
            broadcast("§a" + p.getName() + " §7a rejoint The Towers (§f" + players.size() + "§7, partie " + n + ").");
            reportStatus();
        }

        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || !players.contains(p.getUniqueId()) || !mayTouch(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, kit(new ItemStack(Material.RED_BED), "Quitter The Towers"));
            updateSidebar(p);
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            byPlayer.remove(id);
            Side side = teams.remove(id);
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // en attente au hub d'une copie en préparation : pas de téléportation
            if (teleport && world != null) sendToHub(p);
            colorNames();
            if (state == State.PLAYING && side != null) {
                broadcast("§7" + p.getName() + " a quitté la partie.");
                if (!teams.containsValue(side)) endGame(side.other(), "L'équipe " + side.fem() + " n'a plus de joueurs.");
            }
            closeIfEmpty();
            reportStatus();
        }

        /** Copie sans inscrit hors partie : détruite (la prochaine repartira du modèle propre). */
        void closeIfEmpty() {
            if (n == 1 || removed || !players.isEmpty()) return;
            if (state != State.WAITING && state != State.STARTING) return;
            if (resetting) {
                closeAfterReset = true;
                return;
            }
            removeGame(this);
        }

        // -------------------------------------------------------------- partie

        void startGame() {
            List<Player> list = online(players);
            if (list.size() < 2 || world == null) { state = State.WAITING; reportStatus(); return; }
            Collections.shuffle(list);
            teams.clear();
            kills.clear();
            points.put(Side.ROUGE, 0);
            points.put(Side.BLEU, 0);
            for (int i = 0; i < list.size(); i++) teams.put(list.get(i).getUniqueId(), i % 2 == 0 ? Side.ROUGE : Side.BLEU);
            colorNames();
            clearEntities();
            state = State.PLAYING;
            timer = cfg("game-time", 1500);
            for (Player p : list) {
                Side s = teams.get(p.getUniqueId());
                p.teleport(at(spawns.get(s), world));
                later(() -> equip(p));
                p.showTitle(Title.title(Component.text("Équipe " + s.fem(), s.color).decorate(TextDecoration.BOLD),
                        Component.text("Saute dans le bassin adverse !", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            }
            broadcast("§eLa partie commence ! Première équipe à §6" + cfg("points-to-win", 10) + " §epoints. Construis tes ponts !");
            reportStatus();
        }

        /** Kit de réapparition (d'après le datapack d'origine) + beaucoup de blocs. */
        void equip(Player p) {
            Side s = teams.get(p.getUniqueId());
            if (!p.isOnline() || s == null || state != State.PLAYING || !mayTouch(p)) return;
            p.setGameMode(GameMode.SURVIVAL);
            PlayerInventory inv = p.getInventory();
            inv.setHelmet(armor(Material.LEATHER_HELMET, s, false));
            inv.setChestplate(armor(Material.LEATHER_CHESTPLATE, s, false));
            inv.setLeggings(armor(Material.LEATHER_LEGGINGS, s, true));
            inv.setBoots(armor(Material.LEATHER_BOOTS, s, false));
            giveKit(inv, new ItemStack(Material.BAKED_POTATO, 8), 4);
            int stacks = Math.max(1, cfg("block-stacks", 3));
            for (int i = 0; i < stacks; i++) giveKit(inv, new ItemStack(s.glass, 64), i == 0 ? 3 : -1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 4 * 20, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 8 * 20, 127));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 8 * 20, 127));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 2 * 20, 15));
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 32, 0.4, 0.8, 0.4, 0.04);
        }

        void score(Player p, Side side) {
            int pts = points.merge(side, 1, Integer::sum);
            int goal = cfg("points-to-win", 10);
            p.teleport(at(spawns.get(side), world));
            for (Player o : online(players)) {
                // Titre court : le pseudo seul, « marque » passe dans le sous-titre avec le score
                o.showTitle(Title.title(Component.text(p.getName(), side.color).decorate(TextDecoration.BOLD),
                        Component.text("marque ! Rouge " + points.get(Side.ROUGE) + " - " + points.get(Side.BLEU) + " Bleu", NamedTextColor.WHITE),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, teams.get(o.getUniqueId()) == side ? 1.5f : 0.5f);
            }
            if (pts >= goal) endGame(side, "L'équipe " + side.fem() + " atteint " + goal + " points !");
        }

        void spawnItems() {
            for (String gen : getConfig().getStringList("generators")) {
                String[] p = gen.split(",");
                Material m = Material.matchMaterial(p[0].trim());
                if (m == null) continue;
                Location loc = new Location(world, Double.parseDouble(p[1]) + 0.5, Double.parseDouble(p[2]), Double.parseDouble(p[3]) + 0.5);
                boolean near = loc.getNearbyPlayers(12).stream().anyMatch(pl -> pl.getGameMode() == GameMode.SURVIVAL);
                boolean present = loc.getNearbyEntitiesByType(Item.class, 3).stream().anyMatch(i -> i.getItemStack().getType() == m);
                if (!near || present) continue;
                world.spawnParticle(Particle.CLOUD, loc, 16, 0, 0, 0, 0.1);
                world.dropItem(loc, new ItemStack(m)).setVelocity(new org.bukkit.util.Vector());
            }
        }

        void endGame(Side winner, String reason) {
            endGame(winner, reason, true);
        }

        /** rewards = false pour un arrêt admin : pas de récompenses EemiCore. */
        void endGame(Side winner, String reason, boolean rewards) {
            state = State.ENDING;
            if (rewards) {
                for (Map.Entry<UUID, Side> en : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p == null) continue;
                    eemi("reward " + p.getName() + " towers play");
                    if (en.getValue() == winner) eemi("reward " + p.getName() + " towers win");
                }
            }
            timer = cfg("end-time", 8);
            Component title = winner == null ? Component.text("Égalité !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                    : Component.text("Victoire " + winner.label + " !", winner.color).decorate(TextDecoration.BOLD); // titre court (≤ 16 caractères)
            for (Player p : online(players)) {
                if (!teams.containsKey(p.getUniqueId())) continue;
                p.showTitle(Title.title(title, Component.text(reason, NamedTextColor.GRAY)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (mayTouch(p)) p.setGameMode(GameMode.ADVENTURE);
            }
            broadcast("§6" + reason + " §7Score final : §cRouge " + points.get(Side.ROUGE) + " §7- §9" + points.get(Side.BLEU) + " Bleu");
            reportStatus();
        }

        /**
         * Fin de partie : retour au salon de la map. Partie 1 : remise à zéro comme avant. Copie : remise à
         * zéro si des inscrits restent pour la suivante, sinon destruction.
         */
        void backToLobby() {
            clearNames();
            for (UUID id : new ArrayList<>(teams.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !players.contains(id)) continue;
                p.teleport(lobbyLoc());
                later(() -> prepareLobbyPlayer(p));
            }
            teams.clear();
            state = State.WAITING;
            sendSpectatorsToHub();
            if (n != 1 && players.isEmpty()) {
                removeGame(this);
                return;
            }
            reportStatus();
            Bukkit.getScheduler().runTaskLater(EemiTowers.this, () -> { if (!removed) resetMap(null); }, 20L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, "Partie arrêtée par un admin.", false);
            if (state == State.ENDING) backToLobby();
            if (removed) return;
            state = State.WAITING;
            reportStatus();
        }

        /**
         * Remise à zéro : comme le datapack d'origine, on clone la copie de sauvegarde (zone « backup »)
         * sur la zone de jeu, en 8 sections espacées de 3 ticks, après avoir chargé les chunks.
         * Toutes les commandes passent par « execute in <monde de CETTE partie> » : une copie se remet à
         * zéro depuis sa propre sauvegarde, sans toucher au monde d'origine. done (peut être null) est
         * appelé à la fin.
         */
        void resetMap(Runnable done) {
            if (world == null || resetting) return;
            List<String> sections = getConfig().getStringList("reset.sections");
            if (sections.isEmpty()) {
                if (done != null) done.run();
                return;
            }
            resetting = true;
            World w = world;
            String in = "execute in " + w.getKey().asString() + " run ";
            clearEntities();
            List<String> loads = getConfig().getStringList("reset.forceload");
            for (String f : loads) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), in + "forceload add " + f);
            for (int i = 0; i < sections.size(); i++) {
                String cmd = sections.get(i);
                boolean last = i == sections.size() - 1;
                Bukkit.getScheduler().runTaskLater(EemiTowers.this, () -> {
                    if (removed || !w.equals(world)) return; // copie fermée entre-temps
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), in + cmd);
                    if (last) {
                        // le clone ne recopie que les blocs : on retire aussi les entités laissées par la partie
                        // (chunks encore chargés par le forceload)
                        clearEntities();
                        for (String f : loads) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), in + "forceload remove " + f);
                        resetting = false;
                        getLogger().info("Map remise a zéro (" + w.getName() + ").");
                        if (done != null) done.run();
                        if (closeAfterReset) {
                            closeAfterReset = false;
                            closeIfEmpty();
                        }
                    }
                }, 10L + 3L * i);
            }
        }

        /**
         * Retire de la zone de jeu tout ce qu'une partie laisse : objets au sol, projectiles, minecarts et
         * bateaux, mobs, TNT allumée, blocs qui tombent, orbes d'XP. Les décors de la map (cadres, tableaux,
         * displays, supports d'armure) restent.
         */
        void clearEntities() {
            if (world == null) return;
            for (Entity e : world.getEntities()) {
                if (e instanceof Player || !area.contains(e.getLocation())) continue;
                if (e instanceof Item || e instanceof Projectile || e instanceof org.bukkit.entity.Vehicle
                        || e instanceof org.bukkit.entity.Mob || e instanceof org.bukkit.entity.TNTPrimed
                        || e instanceof org.bukkit.entity.FallingBlock || e instanceof org.bukkit.entity.ExperienceOrb) {
                    e.remove();
                }
            }
        }

        /** Arrêt du plugin : tout le monde au hub. */
        void shutdown() {
            sendSpectatorsToHub();
            for (Player p : online(players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (world != null) sendToHub(p);
            }
        }

        // -------------------------------------------------------------- spectateurs

        void spectate(Player p) {
            Game old = bySpectator.remove(p.getUniqueId());
            if (old != null) old.spectators.remove(p.getUniqueId());
            // au-dessus du centre de la zone de jeu (tours du milieu), regard vers le bas
            p.teleport(new Location(world, (area.x1() + area.x2()) / 2.0 + 0.5, 225, (area.z1() + area.z2()) / 2.0 + 0.5, 0f, 50f));
            spectators.add(p.getUniqueId());
            bySpectator.put(p.getUniqueId(), this);
            if (mayTouch(p)) p.setGameMode(GameMode.SPECTATOR);
            later(() -> { if (p.isOnline() && spectators.contains(p.getUniqueId()) && mayTouch(p)) p.setGameMode(GameMode.SPECTATOR); });
            p.sendMessage(PREFIX + "§7Tu regardes la partie " + n + ", /lobby pour revenir.");
        }

        void sendSpectatorsToHub() {
            List<Player> list = online(spectators);
            for (UUID id : spectators) bySpectator.remove(id);
            spectators.clear();
            for (Player s : list) sendToHub(s);
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        String describe() {
            String w = world != null ? world.getName() : "copie en préparation";
            return "§f" + label() + " §7: monde §f" + w + "§7, " + state.name().toLowerCase()
                    + (resetting ? " (remise à zéro)" : "")
                    + ", §f" + players.size() + "§7/" + maxPlayers() + " inscrits"
                    + (state == State.PLAYING || state == State.ENDING ? ", " + teams.size() + " en jeu, Rouge "
                    + points.get(Side.ROUGE) + " - " + points.get(Side.BLEU) + " Bleu" : "");
        }
    }

    // ------------------------------------------------------------------ objets de kit

    private void giveKit(PlayerInventory inv, ItemStack it, int slot) {
        ItemStack k = kit(it, null);
        if (slot >= 0 && inv.getItem(slot) == null) inv.setItem(slot, k);
        else inv.addItem(k);
    }

    private ItemStack armor(Material type, Side s, boolean projectileProtection) {
        ItemStack it = kit(new ItemStack(type), null);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(s.armor);
        meta.setUnbreakable(true);
        if (projectileProtection) meta.addEnchant(Enchantment.PROJECTILE_PROTECTION, 2, true);
        it.setItemMeta(meta);
        return it;
    }

    /** Objet de kit : marqué pour ne pas tomber à la mort. */
    private ItemStack kit(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        if (name != null) meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        // aucun mob naturel sur les maps (origine, copies, modèle) ; seuls les plugins/oeufs peuvent en faire apparaître
        if (isTowersWorld(e.getLocation().getWorld())
                && e.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Game g = byPlayer.get(victim.getUniqueId());
        if (g == null) return;
        UUID id = victim.getUniqueId();
        if (g.state != State.PLAYING || !g.teams.containsKey(id) || victim.getGameMode() != GameMode.SURVIVAL) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent by) {
            Player attacker = by.getDamager() instanceof Player pl ? pl
                    : by.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pl ? pl : null;
            if (attacker != null && (g.teams.get(attacker.getUniqueId()) == g.teams.get(id) || !g.teams.containsKey(attacker.getUniqueId()))) {
                e.setCancelled(true);
            }
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
        if (killer != null && g.teams.containsKey(killer.getUniqueId())) {
            g.kills.merge(killer.getUniqueId(), 1, Integer::sum);
            eemi("reward " + killer.getName() + " towers kill");
            killer.giveExpLevels(cfg("kill-xp-levels", 4));
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            g.broadcast(g.teams.get(killer.getUniqueId()).code() + killer.getName() + " §7a éliminé " + s.code() + p.getName());
        } else {
            g.broadcast(s.code() + p.getName() + " §7est mort.");
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || g.world == null) return;
        Side s = g.teams.get(p.getUniqueId());
        if (s == null || g.state != State.PLAYING) {
            e.setRespawnLocation(g.lobbyLoc());
            later(() -> g.prepareLobbyPlayer(p));
            return;
        }
        e.setRespawnLocation(at(spawns.get(s), g.world));
        later(() -> g.equip(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || g.state != State.PLAYING || g.world == null) return;
        Side s = g.teams.get(p.getUniqueId());
        if (s == null || p.getGameMode() != GameMode.SURVIVAL) return;
        Location to = e.getTo();
        if (!to.getWorld().equals(g.world)) return;
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY()
                && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        if (to.getY() < voidY || !area.contains(to.getBlockX(), Math.max(area.y1(), to.getBlockY()), to.getBlockZ())) {
            p.setHealth(0);
            return;
        }
        if (pools.get(s.other()).contains(to)) g.score(p, s);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (!canEdit(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (!canEdit(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    /**
     * Construction libre dans la zone de jeu pendant la partie, sauf spawns et bassins, pour les joueurs
     * de la partie de CE monde. Modèle et copies sans partie : intouchables. Admins en créatif : libres.
     */
    private boolean canEdit(Player p, Block b) {
        if (!isTowersWorld(b.getWorld())) return true;
        if (p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemitowers.admin")) return true;
        Game g = gameOfWorld(b.getWorld());
        if (g == null || g.state != State.PLAYING || byPlayer.get(p.getUniqueId()) != g || !g.teams.containsKey(p.getUniqueId())) return false;
        if (!area.contains(b.getX(), b.getY(), b.getZ())) return false;
        for (Box box : protectedBoxes) if (box.contains(b.getX(), b.getY(), b.getZ())) return false;
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté The Towers.");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Game g = byPlayer.get(e.getPlayer().getUniqueId());
        if (g != null && (g.state != State.PLAYING || isKit(e.getItemDrop().getItemStack()))) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null && g.state != State.PLAYING) e.setCancelled(true);
    }

    /**
     * Un inscrit qui quitte le monde de SA partie (/lobby, /mv tp...) quitte la partie, au lieu d'y être
     * renvoyé. En attente d'une copie en préparation (au hub) : changer de monde = changer d'avis.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null) return;
        if (g.world == null || !p.getWorld().equals(g.world)) g.leave(p, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game g = byPlayer.get(e.getPlayer().getUniqueId());
        if (g != null) g.leave(e.getPlayer(), true);
    }

    /** Un spectateur qui quitte le monde de la partie regardée (/lobby...) redevient un joueur normal. */
    @EventHandler
    public void onSpectatorWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = bySpectator.get(p.getUniqueId());
        if (g == null || (g.world != null && p.getWorld().equals(g.world))) return;
        bySpectator.remove(p.getUniqueId());
        g.spectators.remove(p.getUniqueId());
        // seulement s'il arrive au hub : ailleurs le mode est celui du monde (Multiverse)
        if (mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
    }

    @EventHandler
    public void onSpectatorQuit(PlayerQuitEvent e) {
        Game g = bySpectator.remove(e.getPlayer().getUniqueId());
        if (g != null) {
            g.spectators.remove(e.getPlayer().getUniqueId());
            sendToHub(e.getPlayer());
        }
    }

    // ------------------------------------------------------------------ EemiCore et spectateurs

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /** /towers spectate [joueur] : la partie de ce joueur, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String targetName) {
        if (byPlayer.containsKey(p.getUniqueId())) { p.sendMessage(PREFIX + "§cTu es inscrit : /towers leave d'abord."); return; }
        Game g;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : byPlayer.get(t.getUniqueId());
            if (g == null) { p.sendMessage(PREFIX + "§c" + targetName + " n'est pas dans une partie de Towers."); return; }
        } else {
            g = games.values().stream().filter(x -> x.state == State.PLAYING)
                    .max(Comparator.comparingInt(x -> x.teams.size())).orElse(null);
        }
        if (g == null || g.state != State.PLAYING || g.world == null || area == null) {
            p.sendMessage(PREFIX + "§cAucune partie en cours.");
            return;
        }
        g.spectate(p);
    }

    // ------------------------------------------------------------------ commandes

    /** Partie visée par start/stop : numéro donné, sinon celle de l'admin, sinon la partie 1. */
    private Game targetGame(CommandSender sender, Player p, String[] args) {
        if (args.length > 1) {
            try {
                Game g = games.get(Integer.parseInt(args[1]));
                if (g == null) sender.sendMessage(PREFIX + "§cPas de partie " + args[1] + ".");
                return g;
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "§cNuméro de partie invalide.");
                return null;
            }
        }
        if (p != null && byPlayer.containsKey(p.getUniqueId())) return byPlayer.get(p.getUniqueId());
        return games.get(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemitowers.admin");
        switch (sub) {
            case "join" -> {
                Player target = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target);
            }
            case "leave" -> {
                if (p == null) return true;
                Game g = byPlayer.get(p.getUniqueId());
                if (g == null) return true;
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté The Towers.");
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "start" -> {
                if (!admin) return true;
                Game g = targetGame(sender, p, args);
                if (g == null) return true;
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cUne partie est déjà en cours."); return true; }
                if (g.world == null || g.resetting) { sender.sendMessage(PREFIX + "§cLa map de cette partie n'est pas prête."); return true; }
                if (g.players.size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs inscrits."); return true; }
                g.startGame();
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = targetGame(sender, p, args);
                if (g == null) return true;
                g.forceStop();
                sender.sendMessage(PREFIX + "§7" + g.label() + " arrêtée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§e" + games.size() + "/" + maxInstances() + " partie(s), modèle "
                        + (modelBusy ? "en copie" : Bukkit.getWorld(modelName()) != null ? "pret" : "absent") + " :");
                for (Game g : games.values()) sender.sendMessage(PREFIX + g.describe());
            }
            case "reset" -> {
                // remise à zéro de la partie 1, puis modèle refait depuis la map propre
                if (!admin) return true;
                Game g1 = games.get(1);
                if (g1.state == State.PLAYING || g1.state == State.ENDING) { sender.sendMessage(PREFIX + "§cAttends la fin de la partie 1."); return true; }
                if (g1.resetting || modelBusy) { sender.sendMessage(PREFIX + "§cRemise à zéro ou copie du modèle déjà en cours."); return true; }
                g1.resetMap(() -> {
                    if (!refreshModel()) getLogger().warning("Modèle non refait après /towers reset (copie en cours ?).");
                });
                sender.sendMessage(PREFIX + "§aRemise à zéro de la map (partie 1) lancée, puis copie du modèle.");
            }
            case "reload" -> {
                if (!admin) return true;
                if (games.values().stream().anyMatch(g -> g.state == State.PLAYING || g.state == State.ENDING)) {
                    sender.sendMessage(PREFIX + "§cAttends la fin des parties.");
                    return true;
                }
                loadSettings();
                sender.sendMessage(PREFIX + "§aConfig rechargée.");
            }
            default -> sender.sendMessage(PREFIX + "§7/towers <join|leave|spectate [joueur]>"
                    + (admin ? " §8| admin : start [n], stop [n], parties, reset, reload" : ""));
        }
        return true;
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
