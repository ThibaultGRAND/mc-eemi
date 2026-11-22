package fr.eemicraft.tnttag;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TNT Tag d'EEMICRAFT : à chaque manche, quelques joueurs portent la TNT et doivent la refiler
 * en frappant quelqu'un. À la fin du minuteur, les porteurs explosent. Dernier en vie = gagnant.
 * L'arène (chantier de démolition / ville abandonnée, prévue pour ~20 joueurs) et le lobby
 * d'attente sont construits par /tnttag build.
 *
 * Plusieurs parties en même temps (1.4) : partie 1 = monde « tnttag », parties n ≥ 2 = copies
 * « tnttag_n » faites depuis le modèle « tnttag_modele » (copie propre du monde d'origine).
 * Tout l'état d'une partie est dans {@link Game}.
 */
public final class EemiTntTag extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§6TNT Tag§8] §7";
    /** Plafond de barrières, en blocs au-dessus du sol de l'arène. */
    private static final int CEIL = 16;
    /** Hauteur de l'anneau surélevé et des passerelles (bloc de plancher). */
    private static final int RING = 6;
    /** Le lobby d'attente (salle de contrôle) est LOBBY blocs au-dessus du sol de l'arène. */
    private static final int LOBBY = 30;
    /** Invincibilité (ms) du joueur qui vient de refiler la TNT : il ne peut pas la reprendre tout de suite. */
    private static final long PASS_IMMUNITY = 500;
    /** Nombre maximal de lignes du scoreboard latéral. */
    private static final int LINES = 10;

    enum State { PREPARING, WAITING, STARTING, ROUND, PAUSE, ENDING }

    private NamespacedKey toolKey;

    /** Monde de la partie 1 (celui que /tnttag build construit). Chaque partie a son propre monde dans Game. */
    private World world;
    /** Nom du monde de la partie 1 (copies base_2, base_3…, modèle base_modele). */
    private String base = "tnttag";
    private int cx, cy, cz, radius;

    /** Parties par numéro (1 = monde d'origine). */
    private final TreeMap<Integer, Game> games = new TreeMap<>();
    /** Copie propre du monde d'origine, source des copies (personne n'y va). */
    private World model;
    private boolean modelPending;
    private boolean modelBusy;
    /** Copies en cours depuis le modèle (on ne refait pas le modèle pendant ce temps). */
    private int copying;
    /** Numéros de copies en cours de suppression : pas réutilisés tant que le dossier n'est pas effacé. */
    private final Set<Integer> releasing = new java.util.HashSet<>();
    private String lastStatus;
    private long lastStatusAt;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        toolKey = new NamespacedKey(this, "tool");
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        // mondes chargés (Multiverse) : partie 1, nettoyage des copies, modèle
        Bukkit.getScheduler().runTask(this, this::setupGames);
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) {
            g.sendSpectatorsToHub();
            for (Player p : online(g.players)) {
                g.hideBoard(p);
                if (g.bar != null) p.hideBossBar(g.bar);
                sendToHub(p);
            }
        }
        // les copies restées sur le disque sont supprimées au prochain démarrage (WorldClone.cleanup)
    }

    private void loadSettings() {
        reloadConfig();
        String[] c = getConfig().getString("center", "0,100,0").split(",");
        cx = Integer.parseInt(c[0].trim());
        cy = Integer.parseInt(c[1].trim());
        cz = Integer.parseInt(c[2].trim());
        radius = getConfig().getInt("radius", 30);
        for (Game g : games.values()) g.spawns = null;
    }

    private void setupGames() {
        base = getConfig().getString("world", "tnttag");
        world = Bukkit.getWorld(base);
        if (world == null) {
            getLogger().warning("Monde " + base + " introuvable.");
            return;
        }
        Game g1 = new Game(1);
        g1.world = world;
        g1.state = State.WAITING;
        games.put(1, g1);
        WorldClone.cleanup(this, world, base, maxInstances());
        modelPending = true;
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private int maxInstances() {
        return Math.max(1, getConfig().getInt("max-instances", 3));
    }

    private String modelName() {
        return base + "_modele";
    }

    /** Monde d'une partie (index > 0) ou modèle. */
    private boolean isGameWorld(World w) {
        return w != null && (WorldClone.index(base, w.getName()) > 0 || w.getName().equals(modelName()));
    }

    // ------------------------------------------------------------------ boucle (1x / seconde)

    private void tick() {
        for (Game g : new ArrayList<>(games.values())) {
            if (games.get(g.index) != g) continue;
            g.tickState();
            if (games.get(g.index) != g) continue;
            for (Player p : online(g.players)) g.updateBoard(p);
            // copie vide en attente : supprimée
            if (g.index >= 2 && g.players.isEmpty() && (g.state == State.WAITING || g.state == State.STARTING)) destroyGame(g);
            else if (g.index >= 2 && g.state == State.PREPARING && g.players.isEmpty() && !g.copyStarted) games.remove(g.index);
        }
        for (Game g : new ArrayList<>(games.values()))
            if (g.state == State.PREPARING && !g.copyStarted && model != null) startCopy(g);
        maybeRemakeModel();
        reportStatus();
    }

    // ------------------------------------------------------------------ parties : recherche, copies, modèle

    private Game gameOf(Player p) {
        for (Game g : games.values()) if (g.players.contains(p.getUniqueId())) return g;
        return null;
    }

    private Game spectatorGameOf(Player p) {
        for (Game g : games.values()) if (g.spectators.contains(p.getUniqueId())) return g;
        return null;
    }

    /** Partie en attente / décompte (ou en préparation si preparing) qui a la place pour need joueurs, la plus remplie d'abord. */
    private Game joinable(int need, Game except, boolean preparing) {
        Game best = null;
        for (Game g : games.values()) {
            if (g == except) continue;
            if (g.state != State.WAITING && g.state != State.STARTING && !(preparing && g.state == State.PREPARING)) continue;
            if (g.state != State.PREPARING && g.world == null) continue;
            if (g.players.size() + need > cfg("max-players", 20)) continue;
            if (best == null || g.players.size() > best.players.size()) best = g;
        }
        return best;
    }

    private Game newGame() {
        if (games.size() >= maxInstances()) return null;
        for (int n = 2; n <= maxInstances(); n++) {
            if (games.containsKey(n) || releasing.contains(n)) continue;
            Game g = new Game(n);
            g.state = State.PREPARING;
            games.put(n, g);
            if (model != null) startCopy(g);
            return g;
        }
        return null;
    }

    /** Copie asynchrone du modèle pour la partie g ; les inscrits (restés au hub) y sont téléportés à la fin. */
    private void startCopy(Game g) {
        g.copyStarted = true;
        copying++;
        WorldClone.create(this, model, WorldClone.name(base, g.index), w -> {
            copying--;
            if (games.get(g.index) != g) {
                if (w != null) WorldClone.destroy(this, w, base, this::sendToHub);
                return;
            }
            if (w == null) {
                for (Player p : online(g.players)) p.sendMessage(PREFIX + "§cImpossible de préparer une nouvelle partie, réessaie plus tard.");
                games.remove(g.index);
                reportStatus();
                return;
            }
            g.world = w;
            g.state = State.WAITING;
            String hub = getConfig().getString("hub-world", "lobby");
            for (UUID id : new ArrayList<>(g.players)) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.getWorld().getName().equals(hub)) {
                    g.players.remove(id);
                    if (p != null) p.sendMessage(PREFIX + "§7Tu n'es plus au hub : inscription au TNT Tag annulée.");
                    continue;
                }
                g.toLobby(p);
                p.sendMessage(PREFIX + "§aLa partie " + g.index + " est prête !");
            }
            if (g.players.isEmpty()) destroyGame(g);
            else g.broadcast("§7Joueurs : §f" + g.players.size() + "/" + cfg("max-players", 20) + "§7.");
            reportStatus();
        });
    }

    /** Retire une copie (n ≥ 2) de la liste et supprime son monde au tick suivant. */
    private void destroyGame(Game g) {
        if (g.index <= 1) return;
        games.remove(g.index);
        g.sendSpectatorsToHub();
        World w = g.world;
        g.world = null;
        if (w == null) return;
        // la suppression des fichiers est asynchrone : le numéro redevient libre un peu plus tard
        int n = g.index;
        releasing.add(n);
        Bukkit.getScheduler().runTaskLater(this, () -> releasing.remove(n), 200L);
        Bukkit.getScheduler().runTask(this, () -> WorldClone.destroy(this, w, base, this::sendToHub));
    }

    /** (Re)fait le modèle depuis la partie 1 quand elle est en attente et qu'aucune copie n'est en cours. */
    private void maybeRemakeModel() {
        Game g1 = games.get(1);
        if (!modelPending || modelBusy || copying > 0 || g1 == null || g1.world == null) return;
        if (g1.state != State.WAITING && g1.state != State.STARTING) return;
        modelPending = false;
        World old = Bukkit.getWorld(modelName());
        model = null;
        if (old != null) {
            for (Player p : old.getPlayers()) sendToHub(p);
            if (!Bukkit.unloadWorld(old, false)) {
                getLogger().warning("Impossible de décharger l'ancien modèle " + modelName() + ", nouvel essai dans 1 s.");
                modelPending = true;
                return;
            }
        }
        modelBusy = true;
        WorldClone.create(this, g1.world, modelName(), w -> {
            modelBusy = false;
            model = w;
            if (w == null) getLogger().warning("Modèle " + modelName() + " impossible a créer : pas de partie supplémentaire pour l'instant.");
            else getLogger().info("Modèle " + modelName() + " prêt (parties en parallèle : " + maxInstances() + " max).");
        });
    }

    // ------------------------------------------------------------------ inscription

    private void join(Player p) {
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (games.get(1) == null) { p.sendMessage(PREFIX + "§cL'arène n'est pas configurée."); return; }
        Game sg = spectatorGameOf(p);
        if (sg != null) sg.spectators.remove(p.getUniqueId());
        Game g = joinable(1, null, true);
        boolean created = false;
        if (g == null) {
            g = newGame();
            created = g != null;
        }
        if (g == null) { p.sendMessage(PREFIX + "§cToutes les parties sont pleines, réessaie dans un instant."); return; }
        g.players.add(p.getUniqueId());
        if (g.state == State.PREPARING) {
            p.sendMessage(PREFIX + (created ? "§ePréparation d'une nouvelle partie... §7tu y seras téléporté."
                    : "§eUne nouvelle partie se prépare : §7tu y seras téléporté."));
            reportStatus();
            return;
        }
        g.toLobby(p);
        g.broadcast("§a" + p.getName() + " §7a rejoint le TNT Tag (§f" + g.players.size() + "§7).");
        reportStatus();
    }

    /**
     * Renvoi au hub. Seulement si le joueur est dans un monde du jeu ou déjà au hub : ailleurs (skyblock...)
     * on ne le téléporte ni ne le réinitialise. Dans un monde du jeu, la remise à zéro touche l'inventaire
     * de CE monde (Multiverse-Inventories charge ensuite celui du hub) ; au hub, celui du hub.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
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
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setGlowing(false);
    }

    private static String entry(int i) {
        return "§" + Integer.toHexString(i) + "§r";
    }

    private Component line(String label, String value, NamedTextColor color) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.text(value, color));
    }

    private int roundTime(int round) {
        return Math.max(cfg("min-round-time", 15), cfg("round-time", 30) - (round - 1) * 3);
    }

    // ------------------------------------------------------------------ partie

    /** Une partie : son monde (partie 1 = monde d'origine, n ≥ 2 = copie) et tout son état. */
    private final class Game {
        final int index;
        World world;
        boolean copyStarted;

        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> inGame = new LinkedHashSet<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> taggers = new HashSet<>();
        /** Joueur -> instant (ms) où il a refilé la TNT pour la dernière fois. */
        final Map<UUID, Long> passedAt = new HashMap<>();
        /** Scoreboard latéral propre à chaque inscrit. */
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Spectateurs non inscrits de la partie en cours (/tnttag spectate). */
        final Set<UUID> spectators = new HashSet<>();
        /** Arrêt admin en cours : pas de récompenses EemiCore. */
        boolean stopping;
        /** Points d'apparition sûrs (x, z relatifs au centre), calculés à la première manche. */
        List<int[]> spawns;

        State state = State.WAITING;
        int timer;
        int round;
        BossBar bar;
        String winnerName;

        Game(int index) {
            this.index = index;
        }

        Location lobby() {
            return new Location(world, cx + 0.5, cy + LOBBY + 1, cz + 0.5, 0, 0);
        }

        int roundTime() {
            return EemiTntTag.this.roundTime(round);
        }

        void tickState() {
            switch (state) {
                case WAITING -> {
                    if (players.size() >= cfg("min-players", 3)) {
                        state = State.STARTING;
                        timer = cfg("countdown", 30);
                        broadcast("§eAssez de joueurs : la partie commence dans §6" + timer + " s§e.");
                        reportStatus();
                    }
                    for (Player p : online(players)) p.sendActionBar(Component.text(
                            "En attente de joueurs (" + players.size() + "/" + cfg("min-players", 3) + ")", NamedTextColor.YELLOW));
                }
                case STARTING -> {
                    if (players.size() < cfg("min-players", 3)) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                        reportStatus();
                        return;
                    }
                    if (players.size() >= cfg("max-players", 20)) timer = Math.min(timer, cfg("full-countdown", 10));
                    for (Player p : online(players)) {
                        if (here(p)) p.setLevel(timer);
                        p.sendActionBar(Component.text("Début dans " + timer + " s", NamedTextColor.GOLD));
                        if (timer <= 5 && timer > 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    }
                    if (timer <= 0) startGame();
                    else timer--;
                }
                case ROUND -> {
                    timer--;
                    if (bar != null) {
                        bar.progress(Math.max(0f, Math.min(1f, timer / (float) roundTime())));
                        bar.name(Component.text("Manche " + round + "  -  explosion dans " + timer + " s", NamedTextColor.RED));
                    }
                    for (Player p : online(alive)) {
                        boolean t = taggers.contains(p.getUniqueId());
                        p.sendActionBar(t ? Component.text("TU AS LA TNT ! Frappe quelqu'un !", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                                : Component.text("Fuis les porteurs de TNT", NamedTextColor.GREEN));
                        if (t && timer <= 5) p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.7f, 1f);
                    }
                    if (timer <= 0) explode();
                }
                case PAUSE -> {
                    if (--timer <= 0) startRound();
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
                case PREPARING -> { }
            }
        }

        // -------------------------------------------------------------- inscription

        /** Le joueur est-il dans le monde de cette partie ? (seul endroit où l'on touche à son inventaire) */
        boolean here(Player p) {
            return world != null && p.getWorld().equals(world);
        }

        void toLobby(Player p) {
            p.teleport(lobby());
            showBoard(p);
            later(() -> prepareLobbyPlayer(p));
        }

        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || !players.contains(p.getUniqueId())) return;
            // téléportation refusée ou joueur reparti entre-temps : on ne touche à rien hors du monde de la partie
            if (!here(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, tool(Material.RED_BED, "Quitter le TNT Tag", NamedTextColor.RED));
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            if (state == State.PREPARING) {
                // encore au hub : rien à remettre en place
                if (!copyStarted && players.isEmpty()) games.remove(index);
                reportStatus();
                return;
            }
            boolean wasAlive = alive.remove(id);
            boolean wasTagger = taggers.remove(id);
            inGame.remove(id);
            passedAt.remove(id);
            if (bar != null) p.hideBossBar(bar);
            hideBoard(p);
            if (teleport) sendToHub(p);
            if (wasAlive && (state == State.ROUND || state == State.PAUSE)) {
                broadcast("§7" + p.getName() + "a quitté la partie.");
                if (wasTagger && taggers.isEmpty() && state == State.ROUND) pickTaggers();
                checkWin();
            }
            if (index >= 2 && players.isEmpty() && (state == State.WAITING || state == State.STARTING) && games.get(index) == this)
                destroyGame(this);
            reportStatus();
        }

        // -------------------------------------------------------------- scoreboard latéral

        /** Donne au joueur son propre scoreboard : une équipe par ligne (entrée invisible + préfixe), sans clignotement. */
        void showBoard(Player p) {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = sb.registerNewObjective("tnttag", Criteria.DUMMY,
                    Component.text("TNT TAG", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(NumberFormat.blank());
            for (int i = 0; i < LINES; i++) sb.registerNewTeam("l" + i).addEntry(entry(i));
            boards.put(p.getUniqueId(), sb);
            p.setScoreboard(sb);
            updateBoard(p);
        }

        void hideBoard(Player p) {
            if (boards.remove(p.getUniqueId()) != null && p.isOnline())
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }

        void updateBoard(Player p) {
            Scoreboard sb = boards.get(p.getUniqueId());
            if (sb == null) return;
            Objective o = sb.getObjective("tnttag");
            if (o == null) return;
            List<Component> lines = boardLines(p);
            for (int i = 0; i < LINES; i++) {
                Team t = sb.getTeam("l" + i);
                if (t == null) continue;
                if (i < lines.size()) {
                    if (!lines.get(i).equals(t.prefix())) t.prefix(lines.get(i));
                    o.getScore(entry(i)).setScore(lines.size() - i);
                } else {
                    sb.resetScores(entry(i));
                }
            }
        }

        private List<Component> boardLines(Player p) {
            UUID id = p.getUniqueId();
            int min = cfg("min-players", 3), max = cfg("max-players", 20);
            List<Component> l = new ArrayList<>();
            l.add(Component.empty());
            switch (state) {
                case WAITING, STARTING, PREPARING -> {
                    l.add(line("Joueurs : ", players.size() + "/" + max, NamedTextColor.WHITE));
                    l.add(line("Minimum : ", String.valueOf(min), NamedTextColor.WHITE));
                    if (maxInstances() > 1) l.add(line("Partie : ", "#" + index, NamedTextColor.WHITE));
                    l.add(Component.empty());
                    // en STARTING le minuteur a déjà été décrémenté pendant ce tick : on affiche la valeur de la barre d'action
                    l.add(state != State.STARTING ? Component.text("En attente de joueurs...", NamedTextColor.YELLOW)
                            : line("Début dans : ", Math.max(0, timer + 1) + " s", NamedTextColor.GOLD));
                }
                case ROUND, PAUSE -> {
                    l.add(line("Manche : ", String.valueOf(round), NamedTextColor.WHITE));
                    l.add(state == State.ROUND ? line("Explosion : ", Math.max(0, timer) + " s", NamedTextColor.RED)
                            : line("Manche suivante : ", Math.max(0, timer) + " s", NamedTextColor.GOLD));
                    l.add(line("En vie : ", alive.size() + "/" + inGame.size(), NamedTextColor.GREEN));
                    l.add(line("Porteurs : ", String.valueOf(taggers.size()), NamedTextColor.RED));
                    l.add(Component.empty());
                    if (!inGame.contains(id)) l.add(Component.text("Tu joues à la suivante", NamedTextColor.GRAY));
                    else if (!alive.contains(id)) l.add(Component.text("Tu as explosé", NamedTextColor.GRAY));
                    else if (taggers.contains(id)) l.add(Component.text("TU AS LA TNT !", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                    else l.add(Component.text("Tu n'as pas la TNT", NamedTextColor.GREEN));
                }
                case ENDING -> {
                    l.add(Component.text("Partie terminée", NamedTextColor.YELLOW));
                    l.add(Component.empty());
                    l.add(line("Gagnant : ", winnerName == null ? "personne" : winnerName, NamedTextColor.GOLD));
                    l.add(line("Manches : ", String.valueOf(round), NamedTextColor.WHITE));
                    l.add(Component.empty());
                    l.add(line("Retour au lobby : ", Math.max(0, timer) + " s", NamedTextColor.GRAY));
                }
            }
            l.add(Component.empty());
            l.add(Component.text("EEMICRAFT", NamedTextColor.DARK_GRAY));
            return l;
        }

        // -------------------------------------------------------------- partie

        void startGame() {
            List<Player> list = online(players);
            if (list.size() < 2) { state = State.WAITING; reportStatus(); return; }
            inGame.clear();
            alive.clear();
            for (Player p : list) {
                inGame.add(p.getUniqueId());
                alive.add(p.getUniqueId());
            }
            bar = BossBar.bossBar(Component.text("TNT Tag"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            for (Player p : list) p.showBossBar(bar);
            round = 0;
            broadcast("§eLa partie commence ! Ne garde pas la TNT quand le minuteur tombe à zéro.");
            startRound();
        }

        private void startRound() {
            round++;
            passedAt.clear();
            List<Player> list = online(alive);
            List<int[]> spots = spawnPoints();
            int m = spots.size();
            int offset = m == 0 ? 0 : ThreadLocalRandom.current().nextInt(m);
            Location center = new Location(world, cx + 0.5, cy + 1, cz + 0.5);
            for (int i = 0; i < list.size(); i++) {
                Player p = list.get(i);
                Location loc = center.clone();
                if (m > 0) {
                    // points répartis régulièrement dans la liste triée par angle -> joueurs éparpillés
                    int[] s = spots.get((int) ((offset + (long) i * m / list.size()) % m));
                    loc = new Location(world, cx + s[0] + 0.5, cy + 1, cz + s[1] + 0.5);
                    loc.setDirection(center.toVector().subtract(loc.toVector()));
                }
                p.teleport(loc);
                later(() -> equipRunner(p));
            }
            later(this::pickTaggers);
            state = State.ROUND;
            timer = roundTime();
            reportStatus();
        }

        void pickTaggers() {
            List<Player> list = online(alive);
            list.removeIf(p -> taggers.contains(p.getUniqueId()));
            Collections.shuffle(list);
            int count = Math.max(1, (alive.size() + 2) / 4);
            if (alive.size() <= 2) count = 1;
            for (int i = 0; i < count && i < list.size() && taggers.size() < count; i++) setTagger(list.get(i), null);
        }

        private void equipRunner(Player p) {
            if (!p.isOnline() || !alive.contains(p.getUniqueId()) || !here(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
        }

        void setTagger(Player p, Player from) {
            taggers.add(p.getUniqueId());
            if (!here(p)) return;
            p.getInventory().setHelmet(new ItemStack(Material.TNT));
            p.getInventory().setItem(0, tool(Material.TNT, "TNT : frappe quelqu'un !", NamedTextColor.RED));
            p.setGlowing(true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
            p.showTitle(Title.title(Component.text("Tu as la TNT !", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text(from == null ? "Refile-la vite" : "Donnée par " + from.getName(), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1.2f);
            if (from == null) broadcast("§c" + p.getName() + " §7a la TNT !");
        }

        void clearTagger(Player p) {
            taggers.remove(p.getUniqueId());
            if (!here(p)) return;
            p.getInventory().setHelmet(null);
            p.getInventory().setItem(0, null);
            p.setGlowing(false);
            p.removePotionEffect(PotionEffectType.SPEED);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
        }

        private void explode() {
            for (Player p : online(taggers)) {
                Location at = p.getLocation();
                world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                alive.remove(p.getUniqueId());
                clearTagger(p);
                if (here(p)) {
                    resetPlayer(p, GameMode.SPECTATOR);
                    p.getInventory().setItem(8, tool(Material.RED_BED, "Quitter le TNT Tag", NamedTextColor.RED));
                }
                p.showTitle(Title.title(Component.text("BOUM !", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("Tu as explosé", NamedTextColor.GRAY)));
                broadcast("§c" + p.getName() + " §7a explosé ! §f" + alive.size() + " §7joueur(s) en vie.");
            }
            taggers.clear();
            if (!checkWin()) {
                state = State.PAUSE;
                timer = cfg("pause-time", 4);
            }
            reportStatus();
        }

        boolean checkWin() {
            if (state != State.ROUND && state != State.PAUSE) return false;
            if (alive.size() > 1) return false;
            state = State.ENDING;
            timer = cfg("end-time", 8);
            Player winner = alive.isEmpty() ? null : Bukkit.getPlayer(alive.iterator().next());
            winnerName = winner == null ? null : winner.getName();
            // Titre court : le pseudo seul, « gagne » passe dans le sous-titre
            Component title = winner == null ? Component.text("Aucun survivant", NamedTextColor.GRAY)
                    : Component.text(winner.getName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
            Component sub = Component.text(winner == null ? "TNT Tag" : "gagne le TNT Tag !", NamedTextColor.RED);
            for (Player p : online(inGame)) {
                p.showTitle(Title.title(title, sub));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (bar != null) p.hideBossBar(bar);
            }
            if (winner != null) {
                broadcast("§6" + winner.getName() + " §egagne le TNT Tag en " + round + " manche(s) !");
                world.spawnParticle(Particle.FIREWORK, winner.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.1);
            }
            if (!stopping) {
                for (Player p : online(inGame)) if (players.contains(p.getUniqueId())) eemi("reward " + p.getName() + " tnttag play");
                if (winner != null) eemi("reward " + winner.getName() + " tnttag win");
            }
            reportStatus();
            return true;
        }

        /**
         * Fin : partie 1 = tout le monde au lobby d'attente (comme avant). Copie : les inscrits vont dans une autre
         * partie en attente qui a la place (puis la copie est détruite) ; sinon la copie redevient une partie en
         * attente (détruite dès qu'elle est vide).
         */
        void backToLobby() {
            List<UUID> ended = new ArrayList<>(inGame);
            inGame.clear();
            alive.clear();
            taggers.clear();
            passedAt.clear();
            bar = null;
            state = State.WAITING;
            sendSpectatorsToHub();
            if (index >= 2) {
                Game other = players.isEmpty() ? null : joinable(players.size(), this, false);
                if (other != null || players.isEmpty()) {
                    List<UUID> moved = new ArrayList<>(players);
                    players.clear();
                    boards.clear();
                    destroyGame(this);
                    if (other != null)
                        for (UUID id : moved) {
                            Player p = Bukkit.getPlayer(id);
                            if (p == null) continue;
                            other.players.add(id);
                            other.toLobby(p);
                            p.sendMessage(PREFIX + "§7Tu rejoins le lobby d'attente de la partie " + other.index + ".");
                        }
                    reportStatus();
                    return;
                }
            }
            // inscrits ayant joué : retour au lobby d'attente (ceux qui attendaient y sont déjà)
            for (UUID id : ended) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !players.contains(id)) continue;
                if (!mayTouch(p)) continue; // pas dans un monde du jeu ni au hub : on ne le téléporte pas
                p.teleport(lobby());
                later(() -> prepareLobbyPlayer(p));
            }
            reportStatus();
        }

        void forceStop() {
            if (state == State.ROUND || state == State.PAUSE) {
                alive.clear();
                stopping = true;
                checkWin();
                stopping = false;
            }
            if (state == State.ENDING) backToLobby();
            if (state == State.STARTING) state = State.WAITING;
            reportStatus();
        }

        // -------------------------------------------------------------- spectateurs

        void spectate(Player p) {
            // au-dessus de la place centrale, sous le plafond de barrières, regard vers le bas
            p.teleport(new Location(world, cx + 0.5, cy + CEIL - 3, cz + 0.5, 0f, 60f));
            // déjà téléporté : on ne passe en spectateur que s'il est bien arrivé dans le monde de la partie
            if (!here(p)) return;
            spectators.add(p.getUniqueId());
            p.setGameMode(GameMode.SPECTATOR);
            later(() -> { if (p.isOnline() && spectators.contains(p.getUniqueId()) && here(p)) p.setGameMode(GameMode.SPECTATOR); });
            p.sendMessage(PREFIX + "§7Tu regardes la partie " + index + ", /lobby pour revenir.");
        }

        void sendSpectatorsToHub() {
            List<Player> list = online(spectators);
            spectators.clear();
            for (Player s : list) sendToHub(s);
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        String stateName() {
            return switch (state) {
                case PREPARING -> "preparation";
                case WAITING -> "attente";
                case STARTING -> "décompte " + timer + " s";
                case ROUND -> "manche " + round + " (" + timer + " s)";
                case PAUSE -> "pause";
                case ENDING -> "fin";
            };
        }

        // -------------------------------------------------------------- points d'apparition

        /**
         * Points d'apparition sûrs : sol plein, ciel dégagé au-dessus de l'anneau (donc ni dans une structure ni dessous),
         * voisins praticables. Triés par angle pour pouvoir éparpiller les joueurs.
         */
        List<int[]> spawnPoints() {
            if (spawns != null) return spawns;
            List<double[]> found = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (int d = 6; d <= radius - 3; d += 2) {
                int n = Math.max(12, (int) (2 * Math.PI * d / 3));
                for (int k = 0; k < n; k++) {
                    double a = 2 * Math.PI * k / n;
                    int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
                    if (seen.add(((long) x << 32) ^ (z & 0xffffffffL)) && safeSpot(x, z)) found.add(new double[]{a, x, z});
                }
            }
            found.sort(Comparator.comparingDouble(f -> f[0]));
            List<int[]> list = new ArrayList<>();
            for (double[] f : found) list.add(new int[]{(int) f[1], (int) f[2]});
            if (list.isEmpty()) getLogger().warning("Aucun point d'apparition sur dans " + world.getName() + " : les joueurs partiront du centre (as-tu fait /tnttag build ?).");
            else getLogger().info(list.size() + " points d'apparition sûrs dans l'arène (" + world.getName() + ").");
            spawns = list;
            return list;
        }

        private boolean safeSpot(int x, int z) {
            if (!world.getBlockAt(cx + x, cy, cz + z).getType().isSolid()) return false;
            for (int y = cy + 1; y <= cy + RING + 2; y++)
                if (!world.getBlockAt(cx + x, y, cz + z).isEmpty()) return false;
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                for (int y = cy + 1; y <= cy + 2; y++)
                    if (!world.getBlockAt(cx + x + n[0], y, cz + z + n[1]).isPassable()) return false;
            return true;
        }
    }

    // ------------------------------------------------------------------ construction de l'arène

    /**
     * Arène ronde façon chantier de démolition / ville abandonnée, prévue pour un rayon de 30 (~20 joueurs) :
     * - sol de gravats, place centrale (TNT + anneau rayé rouge et blanc), route circulaire sous l'anneau ;
     * - anneau surélevé (RING) sur piliers, 4 escaliers sur les axes, 4 échelles sur les piliers ;
     * - un quartier par quart : maison en ruine (toit accessible), échafaudage relié à l'anneau par 2 passerelles,
     *   butte de gravats percée de tunnels, dépôt de caisses (tour, haies, murets) ;
     * - zone extérieure : cabanes, haies en croix, conteneurs ouverts aux deux bouts, piles de caisses ;
     * - façades en ruine en guise de mur, barrières invisibles et plafond de barrières à CEIL.
     * Le lobby d'attente (salle de contrôle avec vitre au sol) est LOBBY blocs au-dessus.
     */
    private void build() {
        int r = radius;
        Random rnd = new Random(26);
        double ri = r - 10.5, ro = r - 7.5; // bords intérieur et extérieur de l'anneau surélevé
        String[] facades = {"minecraft:bricks", "minecraft:stone_bricks", "minecraft:mud_bricks", "minecraft:deepslate_bricks"};

        // 1) on vide le volume, sol, anneau, mur de façades et barrières
        for (int x = -r - 3; x <= r + 3; x++) {
            for (int z = -r - 3; z <= r + 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                for (int y = cy - 1; y <= cy + CEIL + 2; y++) set(x, y, z, Material.AIR);
                if (d <= r) {
                    set(x, cy - 1, z, Material.STONE_BRICKS);
                    setData(x, cy, z, floorAt(x, z, d, rnd));
                    set(x, cy + CEIL, z, Material.BARRIER);
                    if (d >= ri && d <= ro)
                        setData(x, cy + RING, z, d < ri + 1 || d > ro - 1 ? "minecraft:waxed_weathered_cut_copper" : "minecraft:spruce_planks");
                } else if (d <= r + 1.5) {
                    set(x, cy - 1, z, Material.STONE_BRICKS);
                    set(x, cy, z, Material.STONE_BRICKS);
                    int seg = (int) Math.floor((Math.atan2(z, x) + Math.PI) / (2 * Math.PI) * 24);
                    int wh = 3 + Math.floorMod(seg * 7, 3); // façades de 3 à 5 blocs
                    String base = facades[Math.floorMod(seg, facades.length)];
                    for (int y = 1; y <= wh; y++) {
                        String b = base;
                        if (y == wh) b = "minecraft:polished_andesite"; // corniche
                        else if (y == 2 && Math.floorMod(x + z, 4) == 0) b = "minecraft:tinted_glass"; // fenêtres
                        else if (rnd.nextInt(5) == 0) b = base.equals("minecraft:deepslate_bricks") ? "minecraft:cracked_deepslate_bricks"
                                : base.equals("minecraft:stone_bricks") ? "minecraft:cracked_stone_bricks" : "minecraft:mossy_stone_bricks";
                        setData(x, cy + y, z, b);
                    }
                    for (int y = cy + wh + 1; y <= cy + CEIL; y++) set(x, y, z, Material.BARRIER);
                }
            }
        }

        // 2) piliers de l'anneau (échelle côté centre sur un pilier sur trois), chaînes et lanternes dessous
        int rm = r - 9; // ligne médiane de l'anneau
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(15 + 30 * k);
            int px = (int) Math.round(Math.cos(a) * rm), pz = (int) Math.round(Math.sin(a) * rm);
            for (int y = 1; y <= RING; y++) setData(px, cy + y, pz, y == 1 ? "minecraft:stone_bricks" : "minecraft:waxed_weathered_cut_copper");
            if (k % 3 == 0) {
                boolean alongX = Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a));
                int lx = alongX ? px - (int) Math.signum(Math.cos(a)) : px;
                int lz = alongX ? pz : pz - (int) Math.signum(Math.sin(a));
                String f = alongX ? (Math.cos(a) > 0 ? "west" : "east") : (Math.sin(a) > 0 ? "north" : "south");
                for (int y = 1; y <= RING; y++) setData(lx, cy + y, lz, "minecraft:ladder[facing=" + f + "]");
            }
            double b = Math.toRadians(30 * k);
            int hx = (int) Math.round(Math.cos(b) * rm), hz = (int) Math.round(Math.sin(b) * rm);
            setData(hx, cy + RING - 1, hz, "minecraft:iron_chain");
            setData(hx, cy + RING - 2, hz, "minecraft:lantern[hanging=true]");
        }

        // 3) quatre escaliers sur les axes, de la route jusqu'à l'anneau (passage libre dessous)
        int topStep = (int) Math.ceil(ri) - 1;
        for (int k = 0; k < 4; k++)
            for (int i = 0; i < RING; i++)
                for (int v = -1; v <= 1; v++) {
                    int u = topStep - (RING - 1) + i;
                    setRot(k, u, v, cy + 1 + i, "minecraft:stone_brick_stairs[facing=east]");
                    if (i == 1) setRot(k, u, v, cy + 1, "minecraft:stone_bricks");
                }

        // 4) place centrale : lampadaires et arbres morts (le centre reste libre : point de repli de onMove)
        for (int[] c : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) lamp(c[0], c[1]);
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(22.5 + 90 * k);
            tree((int) Math.round(Math.cos(a) * 7), (int) Math.round(Math.sin(a) * 7), 4);
        }

        // 5) un quartier par quart d'arène
        int q = (int) Math.round(r / 3.0);
        ruinedHouse(q, q, 3, true, rnd);
        scaffold(-q, q, ri);
        rubbleMound(-q, -q, rnd);
        depot(q, -q);

        // 6) zone extérieure, entre l'anneau et le mur
        int od = r - 4;
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int ox = (int) Math.round(Math.cos(a) * od), oz = (int) Math.round(Math.sin(a) * od);
            switch (k % 4) {
                case 0 -> ruinedHouse(ox, oz, 2, false, rnd);
                case 1 -> hedgeCross(ox, oz);
                case 2 -> container(ox, oz, Math.abs(Math.sin(a)) > Math.abs(Math.cos(a)),
                        k == 2 ? "minecraft:waxed_weathered_cut_copper" : "minecraft:red_terracotta");
                default -> crates(ox, oz);
            }
            double b = Math.toRadians(45 * k);
            lamp((int) Math.round(Math.cos(b) * (r - 2.5)), (int) Math.round(Math.sin(b) * (r - 2.5)));
        }

        // 7) gravats épars : seulement sur des cases dégagées (jamais devant une porte, une échelle ou un tunnel)
        String[] rubble = {"minecraft:cobblestone_slab[type=bottom]", "minecraft:andesite_slab[type=bottom]",
                "minecraft:stone_brick_slab[type=bottom]", "minecraft:barrel[facing=up]", "minecraft:cobblestone"};
        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * 2 * Math.PI, d = 7 + rnd.nextDouble() * (r - 9);
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            if (isClear(x, z)) setData(x, cy + 1, z, rubble[rnd.nextInt(rubble.length)]);
        }

        // 8) salle de contrôle au-dessus de l'arène : vitre au sol pour suivre la partie
        int ly = cy + LOBBY, h = 8, top = 5;
        for (int x = -h - 2; x <= h + 2; x++)
            for (int z = -h - 2; z <= h + 2; z++)
                for (int y = ly - 1; y <= ly + top + 2; y++) set(x, y, z, Material.AIR);
        for (int x = -h - 1; x <= h + 1; x++) {
            for (int z = -h - 1; z <= h + 1; z++) {
                boolean wall = Math.abs(x) == h + 1 || Math.abs(z) == h + 1;
                boolean window = Math.abs(x) <= 3 && Math.abs(z) <= 3;
                setData(x, ly, z, wall ? "minecraft:polished_blackstone_bricks"
                        : window ? "minecraft:glass"
                        : (Math.abs(x) == 4 && Math.abs(z) <= 4) || (Math.abs(z) == 4 && Math.abs(x) <= 4) ? "minecraft:red_concrete"
                        : ((x + z) & 1) == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles");
                setData(x, ly + top + 1, z, (!wall && Math.abs(x) % 3 == 0 && Math.abs(z) % 3 == 0) ? "minecraft:ochre_froglight" : "minecraft:polished_blackstone");
                if (!wall) continue;
                int along = Math.abs(x) == h + 1 ? z : x;
                boolean pillar = Math.abs(along) == h + 1 || Math.abs(along) % 3 == 0;
                for (int y = 1; y <= top; y++) {
                    String b = pillar ? "minecraft:polished_blackstone_bricks"
                            : y == 1 ? "minecraft:red_concrete"
                            : (y == 2 || y == 3) ? "minecraft:tinted_glass"
                            : "minecraft:polished_blackstone_bricks";
                    setData(x, ly + y, z, b);
                }
            }
        }
        // déco : tas de TNT dans les coins, bancs, panneau de contrôle (leviers, lampes), lanternes suspendues
        for (int[] c : new int[][]{{-h, -h}, {h, -h}, {-h, h}, {h, h}}) {
            setData(c[0], ly + 1, c[1], "minecraft:tnt");
            setData(c[0], ly + 2, c[1], "minecraft:tnt");
            setData(c[0] + Integer.signum(-c[0]), ly + 1, c[1], "minecraft:barrel[facing=up]");
        }
        for (int x = -2; x <= 2; x++) {
            setData(x, ly + 1, -h, "minecraft:polished_blackstone_bricks");
            setData(x, ly + 2, -h, x % 2 == 0 ? "minecraft:redstone_lamp[lit=true]" : "minecraft:target");
            setData(x, ly + 2, -h + 1, "minecraft:lever[face=wall,facing=south,powered=" + (x % 2 == 0) + "]");
        }
        for (int s : new int[]{-1, 1}) {
            for (int z = -2; z <= 2; z++) setData(s * (h - 1), ly + 1, z, "minecraft:dark_oak_stairs[facing=" + (s < 0 ? "east" : "west") + "]");
        }
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            setData(c[0], ly + top, c[1], "minecraft:iron_chain");
            setData(c[0], ly + top - 1, c[1], "minecraft:lantern[hanging=true]");
        }
    }

    /** Sol : place centrale, route sous l'anneau, trottoir le long du mur, gravats ailleurs. */
    private String floorAt(int x, int z, double d, Random rnd) {
        int r = radius;
        double ri = r - 10.5, ro = r - 7.5;
        if (d < 1.5) return "minecraft:tnt";
        if (d < 4.5) return ((x + z) & 1) == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:polished_blackstone";
        if (d < 5.5) return (Math.round(Math.atan2(z, x) * 4) & 1) == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete";
        if (d >= r - 1.5) return "minecraft:polished_andesite";
        if (d >= ri - 1 && d <= ro + 1) {
            if (Math.abs(d - (r - 9)) < 0.6)
                return Math.floorMod((int) Math.floor(Math.atan2(z, x) * d / 2), 2) == 0 ? "minecraft:white_concrete" : "minecraft:gray_concrete";
            int n = rnd.nextInt(10);
            return n == 0 ? "minecraft:andesite" : n == 1 ? "minecraft:cobblestone" : "minecraft:gray_concrete";
        }
        int n = rnd.nextInt(20);
        if (n < 5) return "minecraft:stone";
        if (n < 8) return "minecraft:andesite";
        if (n < 11) return "minecraft:cobblestone";
        if (n < 13) return "minecraft:gravel";
        if (n < 15) return "minecraft:coarse_dirt";
        if (n < 17) return "minecraft:cracked_stone_bricks";
        if (n < 19) return "minecraft:tuff";
        return "minecraft:mossy_cobblestone";
    }

    /** Maison en ruine : 4 portes, fenêtres cassées, toit percé ; avec échelle extérieure, le toit sert de plateforme. */
    private void ruinedHouse(int ox, int oz, int h, boolean ladder, Random rnd) {
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                int x = ox + dx, z = oz + dz;
                boolean edgeX = Math.abs(dx) == h, edgeZ = Math.abs(dz) == h;
                setData(x, cy, z, "minecraft:spruce_planks");
                if (edgeX || edgeZ) {
                    int along = edgeX ? dz : dx;
                    for (int y = 1; y <= 3; y++) {
                        String b;
                        if (edgeX && edgeZ) b = "minecraft:stripped_dark_oak_log";
                        else if (along == 0 && y <= 2) b = null; // porte
                        else if (h >= 3 && Math.abs(along) == h - 1 && y == 2) b = null; // fenêtre cassée
                        else b = rnd.nextInt(4) == 0 ? "minecraft:cracked_stone_bricks" : rnd.nextInt(3) == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:bricks";
                        if (b != null) setData(x, cy + y, z, b);
                    }
                    if (!(ladder && dx == -h && dz == 1)) setData(x, cy + 5, z, "minecraft:stone_brick_slab[type=bottom]");
                    setData(x, cy + 4, z, "minecraft:dark_oak_planks");
                } else if ((dx != 0 || dz != 0) && rnd.nextInt(6) == 0) {
                    setData(x, cy + 4, z, "minecraft:air"); // toit effondré
                } else {
                    setData(x, cy + 4, z, "minecraft:dark_oak_planks");
                }
            }
        }
        setData(ox, cy + 3, oz, "minecraft:lantern[hanging=true]");
        setData(ox + h - 1, cy + 1, oz - h + 1, "minecraft:barrel[facing=up]");
        if (h >= 3) {
            setData(ox + h - 1, cy + 1, oz - h + 2, "minecraft:barrel[facing=up]");
            setData(ox - h + 1, cy + 1, oz - h + 1, "minecraft:tnt");
            setData(ox + h - 1, cy + 1, oz + h - 1, "minecraft:cauldron");
        }
        if (ladder) for (int y = 1; y <= 4; y++) setData(ox - h - 1, cy + y, oz + 1, "minecraft:ladder[facing=west]");
    }

    /** Échafaudage : plateforme à la hauteur de l'anneau, escalier vers le centre, échelle, 2 passerelles vers l'anneau. */
    private void scaffold(int ox, int oz, double ri) {
        int y = cy + RING;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                setData(ox + dx, y, oz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "minecraft:waxed_weathered_cut_copper" : "minecraft:spruce_planks");
        for (int sx : new int[]{-2, 2})
            for (int sz : new int[]{-2, 2})
                for (int yy = 1; yy < RING; yy++) setData(ox + sx, cy + yy, oz + sz, "minecraft:stripped_spruce_log");
        // escalier qui descend vers le centre (+x)
        for (int i = 0; i < RING; i++)
            for (int dz = -1; dz <= 1; dz++) {
                setData(ox + 3 + i, cy + RING - i, oz + dz, "minecraft:spruce_stairs[facing=west]");
                if (RING - i == 2) setData(ox + 3 + i, cy + 1, oz + dz, "minecraft:spruce_planks");
            }
        // échelle sur le poteau sud-est
        for (int yy = 1; yy <= RING; yy++) setData(ox + 2, cy + yy, oz + 3, "minecraft:ladder[facing=south]");
        // passerelles vers l'ouest et vers le sud, jusqu'à l'anneau
        for (int dz = -1; dz <= 1; dz++)
            for (int x = ox - 3; dist(x, oz + dz) < ri && x > -radius; x--)
                setData(x, y, oz + dz, dz == 0 ? "minecraft:spruce_planks" : "minecraft:waxed_weathered_cut_copper");
        for (int dx = -1; dx <= 1; dx++)
            for (int z = oz + 3; dist(ox + dx, z) < ri && z < radius; z++)
                setData(ox + dx, y, z, dx == 0 ? "minecraft:spruce_planks" : "minecraft:waxed_weathered_cut_copper");
        setData(ox, cy + 1, oz, "minecraft:barrel[facing=up]");
        setData(ox - 1, cy + 1, oz + 1, "minecraft:barrel[facing=up]");
        setData(ox, cy + RING - 1, oz, "minecraft:lantern[hanging=true]");
    }

    /** Butte de gravats en gradins (1, 2, 3 blocs) percée de deux tunnels en croix, ouverts des quatre côtés. */
    private void rubbleMound(int ox, int oz, Random rnd) {
        String[] mats = {"minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:andesite", "minecraft:tuff",
                "minecraft:stone", "minecraft:cracked_stone_bricks"};
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                int hh = m == 4 ? 1 : m == 3 ? 2 : 3;
                boolean tunnel = dx == 0 || dz == 0;
                for (int y = 1; y <= hh; y++) {
                    if (tunnel && y <= 2) continue;
                    setData(ox + dx, cy + y, oz + dz, dx == 0 && dz == 0 ? "minecraft:shroomlight" : mats[rnd.nextInt(mats.length)]);
                }
            }
        }
        setData(ox + 2, cy + 4, oz + 2, "minecraft:cobblestone_wall");
        setData(ox + 2, cy + 5, oz + 2, "minecraft:cobblestone_wall");
        setData(ox - 2, cy + 4, oz + 2, "minecraft:tnt");
        setData(ox + 2, cy + 4, oz - 2, "minecraft:barrel[facing=up]");
    }

    /** Dépôt : tour de caisses (dessus à 3 blocs) avec échelle et marches, haies, murets, caisses. */
    private void depot(int ox, int oz) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int y = 1; y <= 3; y++)
                    setData(ox + dx, cy + y, oz + dz, Math.floorMod(dx + dz + y, 2) == 0 ? "minecraft:barrel[facing=up]" : "minecraft:stripped_spruce_wood");
        setData(ox + 2, cy + 1, oz, "minecraft:stripped_spruce_wood");
        setData(ox + 2, cy + 2, oz, "minecraft:barrel[facing=up]");
        setData(ox + 3, cy + 1, oz, "minecraft:barrel[facing=up]");
        for (int y = 1; y <= 3; y++) setData(ox - 2, cy + y, oz, "minecraft:ladder[facing=west]");
        String leaves = "minecraft:oak_leaves[persistent=true]";
        for (int y = 1; y <= 2; y++) {
            for (int i = -4; i <= -2; i++) setData(ox + i, cy + y, oz - 4, leaves);
            for (int i = -3; i <= -2; i++) setData(ox - 4, cy + y, oz + i, leaves);
            for (int i = 2; i <= 4; i++) setData(ox + i, cy + y, oz + 4, leaves);
            for (int i = 2; i <= 3; i++) setData(ox + 4, cy + y, oz + i, leaves);
        }
        for (int i = 1; i <= 4; i++) setData(ox + i, cy + 1, oz - 4, "minecraft:mossy_stone_bricks"); // muret
        for (int i = 1; i <= 3; i++) setData(ox - 4, cy + 1, oz + i, "minecraft:cobblestone");        // muret
        setData(ox + 2, cy + 1, oz + 2, "minecraft:barrel[facing=up]");
        setData(ox - 2, cy + 1, oz + 2, "minecraft:tnt");
        setData(ox - 2, cy + 2, oz + 2, "minecraft:tnt");
    }

    /** Haie en croix (2 blocs de haut) autour d'un arbre : quatre recoins pour se cacher, aucun cul-de-sac. */
    private void hedgeCross(int ox, int oz) {
        for (int i = -3; i <= 3; i++) {
            if (i == 0) continue;
            for (int y = 1; y <= 2; y++) {
                setData(ox + i, cy + y, oz, "minecraft:dark_oak_leaves[persistent=true]");
                setData(ox, cy + y, oz + i, "minecraft:dark_oak_leaves[persistent=true]");
            }
        }
        tree(ox, oz, 5);
    }

    /** Conteneur rouillé 7x3x3, ouvert aux deux bouts : un tunnel d'un bloc de large. */
    private void container(int ox, int oz, boolean alongX, String mat) {
        for (int l = -3; l <= 3; l++)
            for (int w = -1; w <= 1; w++)
                for (int y = 1; y <= 3; y++) {
                    if (w == 0 && y <= 2) continue;
                    setData(alongX ? ox + l : ox + w, cy + y, alongX ? oz + w : oz + l, mat);
                }
        setData(ox, cy + 4, oz, "minecraft:barrel[facing=up]");
    }

    /** Pile de TNT et de caisses, chaudron, petit arbre. */
    private void crates(int ox, int oz) {
        for (int dx = 0; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++) setData(ox + dx, cy + 1, oz + dz, "minecraft:tnt");
        setData(ox, cy + 2, oz, "minecraft:tnt");
        setData(ox + 1, cy + 2, oz + 1, "minecraft:barrel[facing=up]");
        setData(ox - 2, cy + 1, oz + 1, "minecraft:barrel[facing=up]");
        setData(ox + 1, cy + 1, oz - 2, "minecraft:stripped_spruce_wood");
        setData(ox + 1, cy + 2, oz - 2, "minecraft:spruce_slab[type=bottom]");
        setData(ox - 1, cy + 1, oz + 3, "minecraft:cauldron");
        tree(ox - 2, oz - 2, 4);
    }

    /** Lampadaire : muret, poteau, lanterne. */
    private void lamp(int x, int z) {
        setData(x, cy + 1, z, "minecraft:stone_brick_wall");
        for (int y = 2; y <= 4; y++) setData(x, cy + y, z, "minecraft:dark_oak_fence");
        setData(x, cy + 5, z, "minecraft:lantern");
    }

    /** Arbre : tronc de chêne noir, feuillage persistant posé seulement dans l'air (n'écrase rien). */
    private void tree(int x, int z, int h) {
        for (int y = 1; y <= h; y++) setData(x, cy + y, z, "minecraft:dark_oak_log");
        for (int dy = -1; dy <= 1; dy++) {
            int rr = dy == 1 ? 1 : 2;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > rr || (dx == 0 && dz == 0 && dy < 1)) continue;
                    int y = cy + h + dy;
                    if (world.getBlockAt(cx + x + dx, y, cz + z + dz).isEmpty())
                        setData(x + dx, y, z + dz, "minecraft:dark_oak_leaves[persistent=true]");
                }
        }
    }

    /** Case dégagée (elle et ses 8 voisines vides sur 2 blocs de haut, rien au-dessus jusqu'à 3 blocs). */
    private boolean isClear(int x, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int y = 1; y <= 2; y++)
                    if (!world.getBlockAt(cx + x + dx, cy + y, cz + z + dz).isEmpty()) return false;
        return world.getBlockAt(cx + x, cy + 3, cz + z).isEmpty();
    }

    private static double dist(int x, int z) {
        return Math.sqrt(x * x + z * z);
    }

    private static final String[] DIRS = {"east", "south", "west", "north"};

    /** Pose un bloc dans un repère tourné de k quarts de tour (u = axe radial, v = axe tangent), orientation comprise. */
    private void setRot(int k, int u, int v, int y, String data) {
        int x, z;
        switch (k & 3) {
            case 0 -> { x = u; z = v; }
            case 1 -> { x = -v; z = u; }
            case 2 -> { x = -u; z = -v; }
            default -> { x = v; z = -u; }
        }
        for (int i = 0; i < 4; i++) {
            String f = "facing=" + DIRS[i];
            if (data.contains(f + "]") || data.contains(f + ",")) {
                data = data.replace(f, "facing=" + DIRS[(i + k) & 3]);
                break;
            }
        }
        setData(x, y, z, data);
    }

    private void setData(int x, int y, int z, String data) {
        try {
            world.getBlockAt(cx + x, y, cz + z).setBlockData(Bukkit.createBlockData(data), false);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Bloc inconnu : " + data);
        }
    }

    private void set(int x, int y, int z, Material m) {
        world.getBlockAt(cx + x, y, cz + z).setType(m, false);
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageBy(EntityDamageByEntityEvent e) {
        Player victim = e.getEntity() instanceof Player pv ? pv : null;
        Player attacker = e.getDamager() instanceof Player pa ? pa : null;
        Game gv = victim == null ? null : gameOf(victim);
        Game ga = attacker == null ? null : gameOf(attacker);
        if (gv == null && ga == null) return;
        // aucun dégât pour un inscrit : ni au lobby d'attente, ni en partie (la TNT se refile sans dégâts)
        e.setCancelled(true);
        // la TNT ne passe qu'entre deux joueurs de la même partie
        if (gv == null || gv != ga || gv.state != State.ROUND) return;
        Game g = gv;
        UUID a = attacker.getUniqueId(), v = victim.getUniqueId();
        if (!g.alive.contains(a) || !g.alive.contains(v) || !g.taggers.contains(a) || g.taggers.contains(v)) return;
        // celui qui vient de refiler la TNT ne peut pas la reprendre pendant PASS_IMMUNITY ms
        long now = System.currentTimeMillis();
        Long gave = g.passedAt.get(v);
        if (gave != null && now - gave < PASS_IMMUNITY) return;
        g.passedAt.put(a, now);
        g.clearTagger(attacker);
        g.setTagger(victim, attacker);
        attacker.sendActionBar(Component.text("TNT refilée à " + victim.getName() + " !", NamedTextColor.GREEN));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) return;
        if (e.getEntity() instanceof Player p && (gameOf(p) != null || isGameWorld(p.getWorld()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (g == null || !g.inGame.contains(p.getUniqueId()) || g.state == State.ENDING) return;
        Location to = e.getTo();
        if (g.world == null || !to.getWorld().equals(g.world)) return;
        double dx = to.getX() - (cx + 0.5), dz = to.getZ() - (cz + 0.5);
        boolean out = dx * dx + dz * dz > (radius + 1) * (radius + 1) || to.getY() < cy - 3 || to.getY() > cy + CEIL + 1;
        if (out) p.teleport(new Location(g.world, cx + 0.5, cy + 1, cz + 0.5, to.getYaw(), to.getPitch()));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !isTool(it)) return;
        e.setCancelled(true);
        if (it.getType() == Material.RED_BED) {
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté le TNT Tag.");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && gameOf(p) != null
                && p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && (gameOf(p) != null || isGameWorld(p.getWorld()))) e.setCancelled(true);
    }

    /**
     * Un inscrit qui quitte le monde de SA partie (/lobby, /mv tp...) quitte la partie, au lieu d'y être renvoyé.
     * Pendant la préparation d'une copie (il attend au hub), changer de monde annule l'inscription.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = gameOf(p);
        if (g == null) return;
        if (g.state == State.PREPARING) {
            g.leave(p, false);
            p.sendMessage(PREFIX + "§7Inscription au TNT Tag annulée.");
        } else if (g.world != null && e.getFrom().equals(g.world)) g.leave(p, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null) g.leave(e.getPlayer(), g.state != State.PREPARING);
    }

    /** Un spectateur qui quitte le monde de la partie qu'il regarde (/lobby...) redevient un joueur normal. */
    @EventHandler
    public void onSpectatorWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        for (Game g : games.values()) {
            if (!g.spectators.contains(p.getUniqueId()) || (g.world != null && p.getWorld().equals(g.world))) continue;
            g.spectators.remove(p.getUniqueId());
            if (spectatorGameOf(p) == null && p.getGameMode() == GameMode.SPECTATOR && mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onSpectatorQuit(PlayerQuitEvent e) {
        Game g = spectatorGameOf(e.getPlayer());
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

    /**
     * Statut agrégé pour EemiCore (envoyé s'il a changé, ou toutes les 30 s) : waiting si une partie attend ou
     * décompte, sinon playing si une partie joue ; en attente = inscrits hors partie, en jeu = joueurs en vie ;
     * max = max par partie × nombre de parties possibles.
     */
    private void reportStatus() {
        boolean anyWaiting = false, anyPlaying = false;
        int waiting = 0, playing = 0;
        for (Game g : games.values()) {
            switch (g.state) {
                case PREPARING, WAITING, STARTING -> anyWaiting = true;
                case ROUND, PAUSE, ENDING -> anyPlaying = true;
            }
            for (UUID u : g.players) if (!g.inGame.contains(u)) waiting++;
            if (g.state != State.WAITING && g.state != State.STARTING && g.state != State.PREPARING) playing += g.alive.size();
        }
        String st = anyWaiting ? "waiting" : anyPlaying ? "playing" : "waiting";
        String status = "status tnttag " + st + " " + waiting + " " + playing + " " + cfg("max-players", 20) * maxInstances();
        long now = System.currentTimeMillis();
        if (status.equals(lastStatus) && now - lastStatusAt < 30_000) return;
        lastStatus = status;
        lastStatusAt = now;
        eemi(status);
    }

    /** /tnttag spectate [joueur] : la partie de ce joueur, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String name) {
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : /tnttag leave d'abord."); return; }
        Game g = null;
        if (name != null) {
            Player t = Bukkit.getPlayerExact(name);
            if (t == null) { p.sendMessage(PREFIX + "§cJoueur introuvable."); return; }
            g = gameOf(t);
            if (g == null) g = spectatorGameOf(t);
            if (g == null || (g.state != State.ROUND && g.state != State.PAUSE)) { p.sendMessage(PREFIX + "§c" + t.getName() + " n'est dans aucune partie en cours."); return; }
        } else {
            for (Game c : games.values())
                if ((c.state == State.ROUND || c.state == State.PAUSE) && (g == null || c.alive.size() > g.alive.size())) g = c;
            if (g == null) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return; }
        }
        Game old = spectatorGameOf(p);
        if (old != null) old.spectators.remove(p.getUniqueId());
        g.spectate(p);
    }

    // ------------------------------------------------------------------ commandes

    /** Partie visée par une commande admin : numéro donné, sinon celle de l'admin, sinon la partie 1. */
    private Game target(CommandSender sender, String[] args) {
        if (args.length > 1) {
            try {
                return games.get(Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (sender instanceof Player p) {
            Game g = gameOf(p);
            if (g == null) g = spectatorGameOf(p);
            if (g != null) return g;
        }
        return games.get(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemitnttag.admin");
        switch (sub) {
            case "join" -> {
                Player target = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target);
            }
            case "leave" -> {
                Game g = p == null ? null : gameOf(p);
                if (g == null) return true;
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté le TNT Tag.");
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "start" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) { sender.sendMessage(PREFIX + "§cPartie introuvable (/tnttag parties)."); return true; }
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cLa partie " + g.index + " est déjà en cours."); return true; }
                if (g.players.size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs inscrits (partie " + g.index + ")."); return true; }
                g.startGame();
                sender.sendMessage(PREFIX + "§7Partie " + g.index + " lancée.");
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = target(sender, args);
                if (g == null) { sender.sendMessage(PREFIX + "§cPartie introuvable (/tnttag parties)."); return true; }
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie " + g.index + " arrêtée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ") :");
                for (Game g : games.values())
                    sender.sendMessage("§7 #" + g.index + " §f" + (g.world == null ? WorldClone.name(base, g.index) + " (copie en cours)" : g.world.getName())
                            + " §7- " + g.stateName() + " - §f" + g.players.size() + "§7 inscrit(s), §f" + g.alive.size() + "§7 en vie, §f"
                            + g.spectators.size() + "§7 spectateur(s)");
                sender.sendMessage("§7 Modèle : §f" + (model != null ? model.getName() : modelBusy || modelPending ? "en préparation" : "absent"));
            }
            case "build" -> {
                if (!admin) return true;
                Game g1 = games.get(1);
                if (world == null || g1 == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g1.state != State.WAITING) { sender.sendMessage(PREFIX + "§cAttends la fin de la partie 1."); return true; }
                build();
                g1.spawns = null;
                // les copies repartent de la nouvelle arène
                modelPending = true;
                sender.sendMessage(PREFIX + "§aArène et lobby d'attente construits (partie 1). §7Le modèle des autres parties va être refait.");
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.state != State.WAITING && g.state != State.STARTING && g.state != State.PREPARING) {
                        sender.sendMessage(PREFIX + "§cAttends la fin des parties.");
                        return true;
                    }
                loadSettings();
                sender.sendMessage(PREFIX + "§aConfig rechargée.");
            }
            default -> sender.sendMessage(PREFIX + "§7/tnttag <join|leave|spectate [joueur]>" + (admin ? " §8| admin : start [n], stop [n], parties, build, reload" : ""));
        }
        return true;
    }

    // ------------------------------------------------------------------ utilitaires

    private ItemStack tool(Material type, String name, NamedTextColor color) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isTool(ItemStack it) {
        return it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

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
