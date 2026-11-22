/*
 * LGPlus - complément du plugin LoupGarou (Gh0st-cmd). Source reconstituée depuis la 1.0.6 (CFR) puis corrigée.
 */
package fr.fallen.lgplus;

import org.bukkit.GameMode;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.gh0st.loupGarou.utils.extern.WorldGuardIntegration;
import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import fr.fallen.lgplus.Menu;
import fr.fallen.lgplus.Tool;
import fr.fallen.lgplus.Turn;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.gh0st.loupGarou.LoupGarouPlugin;
import org.gh0st.loupGarou.game.GameManager;

public final class LGPlus
extends JavaPlugin
implements Listener {
    private static final String PREFIX = "\u00a76[LG+] \u00a7r";
    private NamespacedKey toolKey;
    private GameManager game;
    private LoupGarouPlugin lgPlugin;
    private String lgWorld = "lg";
    private final List<Location> seats = new ArrayList<Location>();
    private final Map<UUID, Location> assigned = new HashMap<UUID, Location>();
    private final Map<UUID, UUID> cupidFirst = new HashMap<UUID, UUID>();
    private final Map<UUID, Integer> witchDone = new HashMap<UUID, Integer>();
    private final Set<UUID> hunterNotified = new HashSet<UUID>();
    private final Set<UUID> blinded = new HashSet<UUID>();
    private final Map<UUID, Set<UUID>> hidden = new HashMap<UUID, Set<UUID>>();
    private final Map<UUID, Long> lastUse = new HashMap<UUID, Long>();
    private boolean wasActive;
    private final BossBar phaseBar = BossBar.bossBar((Component)Component.empty(), (float)1.0f, (BossBar.Color)BossBar.Color.BLUE, (BossBar.Overlay)BossBar.Overlay.PROGRESS);
    private final Set<UUID> barViewers = new HashSet<UUID>();
    private GameManager.GameState barState;
    private long phaseStart;
    private final Map<UUID, TextDisplay> voteLabels = new HashMap<UUID, TextDisplay>();
    private final Random random = new Random();
    private Runnable endNight;
    private List<Turn> nightTurns;
    private int turnIndex;
    private long turnStart;
    private long turnEnd;
    private boolean turnFake;
    private int seqNight = -1;
    private int handledNight = -1;
    private int firstNight = -1;
    private int firstDay = -1;
    private boolean electionOpen;
    private boolean provisionalNotified;
    private final Map<UUID, UUID> mayorVotes = new HashMap<UUID, UUID>();
    private ChatFilter chatFilter;
    private WorldGuardIntegration originalWg;
    private final Set<UUID> skipVotes = new HashSet<UUID>();
    private String skipPhase = "";
    private final Set<UUID> allowGameMode = new HashSet<UUID>();
    private static final Set<String> ACTION_SUBCOMMANDS = Set.of("tuer", "kill", "voir", "see", "proteger", "guard", "soigner", "heal", "empoisonner", "poison", "lier", "link", "espionner", "spy", "assassiner", "assassinate");

    public void onLoad() {
        Path file = this.getDataFolder().toPath().getParent().resolve("LoupGarou").resolve("config.yml");
        if (!Files.exists(file, new LinkOption[0])) {
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            StringBuilder clean = new StringBuilder();
            for (String line : text.split("\n", -1)) {
                if (line.stripLeading().startsWith("#")) continue;
                clean.append(line.replaceAll("[\\u0080-\\u009F]", "")).append('\n');
            }
            String result = clean.toString().stripTrailing() + "\n";
            if (!result.equals(text)) {
                Files.writeString(file, (CharSequence)result, StandardCharsets.UTF_8, new OpenOption[0]);
                this.getLogger().info("Config de LoupGarou nettoy\u00e9e (en-t\u00eate corrompu retir\u00e9).");
            }
        }
        catch (IOException | RuntimeException e) {
            this.getLogger().warning("Impossible de nettoyer la config de LoupGarou : " + e.getMessage());
        }
    }

    public void onEnable() {
        this.toolKey = new NamespacedKey((Plugin)this, "tool");
        LoupGarouPlugin lg = (LoupGarouPlugin)this.getServer().getPluginManager().getPlugin("LoupGarou");
        if (lg == null || lg.getGameManager() == null) {
            this.getLogger().severe("LoupGarou introuvable : LGPlus d\u00e9sactiv\u00e9.");
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        this.game = lg.getGameManager();
        this.lgPlugin = lg;
        this.lgWorld = lg.getConfig().getString("worldguard.world-name", "lg");
        this.saveDefaultConfig();
        this.loadSeats();
        // « [DEBUG] Monde incorrect ... » plusieurs fois par seconde dans la console
        ChatFilter.muteDebug(lg.getLogger());
        this.installVanishFilter();
        this.chatFilter = new ChatFilter(this.getLogger());
        this.chatFilter.repairEnabled = this.getConfig().getBoolean("chat.reparer-accents", true);
        this.chatFilter.directEnabled = this.getConfig().getBoolean("chat.filtre-messages-directs", true);
        if (this.getConfig().getBoolean("chat.filtre-paquets", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) this.chatFilter.inject(p);
        }
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::fixScoreboards, 20L, 5L);
    }

    /**
     * Remplace l'intégration WorldGuard de LoupGarou par {@link VanishAwareWorldGuard} : c'est elle que
     * LoupGarou consulte pour choisir les participants (/lg start, démarrage auto). Les joueurs en vanish
     * ne reçoivent donc jamais de rôle.
     */
    private void installVanishFilter() {
        try {
            Field f = LoupGarouPlugin.class.getDeclaredField("worldGuardIntegration");
            f.setAccessible(true);
            Object current = f.get(this.lgPlugin);
            if (current == null) {
                this.getLogger().warning("WorldGuard absent de LoupGarou : impossible d'exclure les joueurs en vanish des parties.");
                return;
            }
            if (current.getClass().getName().equals(VanishAwareWorldGuard.class.getName())) return;
            this.originalWg = (WorldGuardIntegration)current;
            f.set(this.lgPlugin, new VanishAwareWorldGuard(this.lgPlugin));
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            this.getLogger().warning("Exclusion des joueurs en vanish impossible : " + e);
        }
    }

    private void restoreWorldGuard() {
        if (this.originalWg == null) return;
        try {
            Field f = LoupGarouPlugin.class.getDeclaredField("worldGuardIntegration");
            f.setAccessible(true);
            f.set(this.lgPlugin, this.originalWg);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            this.getLogger().warning("Restauration de l'intégration WorldGuard de LoupGarou impossible : " + e);
        }
        this.originalWg = null;
    }

    /**
     * Retouche les scoreboards de LoupGarou (objectif « loupgarou ») :
     * <ul>
     * <li>texte double encodé réparé (jar 1.0.0) - lignes, titre et préfixes d'équipes ;</li>
     * <li>hors partie, « Joueurs : » compte tous les connectés du serveur (Bukkit.getOnlinePlayers), hub
     * compris : on le remplace par les joueurs qui peuvent jouer (bon monde + région WorldGuard, même test
     * que LoupGarou pour lancer la partie), sans les joueurs en vanish.</li>
     * </ul>
     */
    private void fixScoreboards() {
        boolean running = this.game.isGameRunning();
        int n = 0;
        if (!running) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // test de région seulement dans le bon monde : canPlayerPlay écrit un message de debug sinon
                if (!p.getWorld().getName().equals(this.lgWorld) || VanishAwareWorldGuard.isVanished(p)) continue;
                if (this.lgPlugin.getWorldGuardIntegration() == null || this.lgPlugin.getWorldGuardIntegration().canPlayerPlay(p)) n++;
            }
        }
        Set<Scoreboard> seen = new HashSet<Scoreboard>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            if (!seen.add(board)) continue;
            Objective obj = board.getObjective("loupgarou");
            if (obj == null) continue;
            String title = obj.getDisplayName();
            String fixedTitle = Mojibake.repair(title);
            if (!fixedTitle.equals(title)) obj.setDisplayName(fixedTitle);
            for (Team team : board.getTeams()) {
                String prefix = team.getPrefix();
                String fixedPrefix = Mojibake.repair(prefix);
                if (!fixedPrefix.equals(prefix)) team.setPrefix(fixedPrefix);
            }
            for (String entry : new ArrayList<String>(board.getEntries())) {
                if (!obj.getScore(entry).isScoreSet()) continue;
                String fixed = Mojibake.repair(entry);
                if (!running) {
                    fixed = fixed.replaceFirst("(Joueurs: (?:§.)*)\\d+", "$1" + n);
                }
                if (fixed.equals(entry)) continue;
                int score = obj.getScore(entry).getScore();
                board.resetScores(entry);
                obj.getScore(fixed).setScore(score);
            }
        }
    }

    public void onDisable() {
        if (this.chatFilter != null) {
            for (Player p : Bukkit.getOnlinePlayers()) this.chatFilter.uninject(p);
        }
        if (this.lgPlugin != null) this.restoreWorldGuard();
        if (this.game != null) this.cleanup();
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (this.chatFilter != null && this.getConfig().getBoolean("chat.filtre-paquets", true)) {
            this.chatFilter.inject(event.getPlayer());
        }
    }

    private boolean isActive(GameManager.GameState state) {
        return state != GameManager.GameState.WAITING && state != GameManager.GameState.FINISHED;
    }

    private void tick() {
        GameManager.GameState state = this.game.getCurrentState();
        boolean active = this.isActive(state);
        this.updateFilterState(active);
        if (active && !this.wasActive) {
            this.wasActive = true;
            this.warnVanishedParticipants();
            Bukkit.getScheduler().runTaskLater((Plugin)this, this::assignSeats, 10L);
        } else if (!active && this.wasActive) {
            this.wasActive = false;
            this.cleanup();
            return;
        }
        // objets hors participants : livre des rôles dans le monde lg, rien ailleurs
        Map<UUID, ?> participants = active ? this.game.getPlayers() : Map.of();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (participants.containsKey(p.getUniqueId())) continue;
            this.syncTools(p, this.desiredTools(p, state));
        }
        if (!active) {
            return;
        }
        for (UUID uuid : new ArrayList<UUID>(this.game.getPlayers().keySet())) {
            Player p = Bukkit.getPlayer((UUID)uuid);
            if (p == null) continue;
            try {
                this.tickPlayer(p, uuid, state);
            }
            catch (LinkageError | RuntimeException e) {
                this.getLogger().warning("Erreur pour " + p.getName() + " : " + String.valueOf(e));
            }
        }
        this.updateVisibility(state);
        this.updatePhaseBar(state);
        this.updateVoteLabels(state);
        this.refreshOpenMenus();
        if (state == GameManager.GameState.NIGHT) {
            if (this.endNight == null && this.handledNight != this.game.getDayNumber()) {
                this.startNightTurns();
            } else if (this.endNight != null) {
                this.stepNight();
            }
        } else if (this.endNight != null) {
            this.resetNight();
        }
        this.tickElection(state);
        this.tickSkipVotes(state);
    }

    /** Copie de l'état utile au filtre de chat (lu depuis les threads réseau). */
    private void updateFilterState(boolean active) {
        if (this.chatFilter == null) return;
        Set<UUID> now = new HashSet<UUID>();
        Set<String> roles = new HashSet<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.concerned(p)) now.add(p.getUniqueId());
        }
        if (active) {
            for (UUID uuid : this.game.getPlayers().keySet()) {
                String r = this.roleName(uuid);
                if (r != null) roles.add(r);
            }
        }
        this.chatFilter.concerned.retainAll(now);
        this.chatFilter.concerned.addAll(now);
        this.chatFilter.presentRoles = Set.copyOf(roles);
        this.chatFilter.gameRunning = active;
    }

    /** Filet de sécurité si l'exclusion des joueurs en vanish n'a pas pu être installée. */
    private void warnVanishedParticipants() {
        for (UUID uuid : this.game.getPlayers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && VanishAwareWorldGuard.isVanished(p)) {
                this.broadcastAdmins(p.getName() + " est en vanish mais participe a la partie (/lg stop puis relancer).");
            }
        }
    }

    // ---------------------------------------------------------------- vote pour passer à la suite

    private boolean skippable(GameManager.GameState state) {
        return state == GameManager.GameState.NIGHT || state == GameManager.GameState.DAY
                || state == GameManager.GameState.VOTE || state == GameManager.GameState.MAYOR_VOTE;
    }

    private String phaseKey(GameManager.GameState state) {
        int turn = state == GameManager.GameState.NIGHT && this.currentTurn() != null ? this.turnIndex : -1;
        return state.name() + ":" + this.game.getDayNumber() + ":" + turn;
    }

    private List<Player> alivePlayers() {
        ArrayList<Player> list = new ArrayList<Player>();
        for (UUID uuid : this.game.getPlayers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && this.game.isPlayerAlive(p)) list.add(p);
        }
        return list;
    }

    private int skipNeeded(int alive) {
        double ratio = Math.max(0.01, Math.min(1.0, this.getConfig().getDouble("passer.ratio", 1.0)));
        return Math.max(1, (int)Math.ceil(ratio * alive - 1e-9));
    }

    private void tickSkipVotes(GameManager.GameState state) {
        String key = this.phaseKey(state);
        if (!key.equals(this.skipPhase)) {
            this.skipPhase = key;
            this.skipVotes.clear();
            return;
        }
        if (!this.skipVotes.isEmpty()) this.checkSkip(state);
    }

    private void voteSkip(Player p) {
        GameManager.GameState state = this.game.getCurrentState();
        if (!this.isActive(state) || !this.skippable(state) || !this.game.isPlayerAlive(p)) {
            p.sendActionBar(Component.text("Rien à passer pour l'instant", NamedTextColor.GRAY));
            return;
        }
        String key = this.phaseKey(state);
        if (!key.equals(this.skipPhase)) {
            this.skipPhase = key;
            this.skipVotes.clear();
        }
        boolean added = this.skipVotes.add(p.getUniqueId());
        if (!added) this.skipVotes.remove(p.getUniqueId());
        List<Player> alive = this.alivePlayers();
        this.skipVotes.removeIf(u -> alive.stream().noneMatch(a -> a.getUniqueId().equals(u)));
        String text = p.getName() + (added ? " veut passer" : " ne veut plus passer") + " (" + this.skipVotes.size() + "/" + this.skipNeeded(alive.size()) + ")";
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (this.concerned(o)) o.sendActionBar(Component.text(text, added ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        this.checkSkip(state);
    }

    private void checkSkip(GameManager.GameState state) {
        List<Player> alive = this.alivePlayers();
        this.skipVotes.removeIf(u -> alive.stream().noneMatch(a -> a.getUniqueId().equals(u)));
        if (this.skipVotes.isEmpty() || this.skipVotes.size() < this.skipNeeded(alive.size()) || !this.skippable(state)) return;
        this.skipVotes.clear();
        this.gameBroadcast(PREFIX + "§7Tout le monde est d'accord : on passe à la suite.");
        this.next(Bukkit.getConsoleSender(), false);
    }

    /** Annonce du réveil d'un rôle : barre d'action (la barre de boss affiche déjà le tour), ou chat si configuré. */
    private void announceTurn(String wake) {
        if (this.getConfig().getBoolean("chat.annonces-tours", false)) {
            this.gameBroadcast(PREFIX + "§9" + wake);
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.concerned(p)) p.sendActionBar(Component.text(wake, NamedTextColor.BLUE));
        }
    }

    private boolean mayorEnabled() {
        return this.lgPlugin.getConfig().getBoolean("game.enable-mayor", true);
    }

    private void startNightTurns() {
        BukkitTask task;
        Runnable action;
        this.handledNight = this.game.getDayNumber();
        if (this.firstNight < 0) {
            this.firstNight = this.handledNight;
        }
        Runnable runnable = action = (task = this.phaseTask()) == null ? null : this.phaseAction(task);
        if (action == null) {
            this.getLogger().warning("Nuit " + this.handledNight + " : minuteur de LoupGarou introuvable, tours d\u00e9sactiv\u00e9s pour cette nuit.");
            return;
        }
        task.cancel();
        this.endNight = action;
        this.seqNight = this.handledNight;
        this.nightTurns = new ArrayList<Turn>();
        HashSet<String> present = new HashSet<String>();
        for (UUID uuid : this.game.getPlayers().keySet()) {
            String r = this.roleName(uuid);
            if (r == null) continue;
            present.add(r);
        }
        for (Turn t : Turn.values()) {
            if (t == Turn.CUPID && this.handledNight != this.firstNight) continue;
            if (t != Turn.WOLVES) {
                if (!t.roles.stream().anyMatch(present::contains)) continue;
            }
            this.nightTurns.add(t);
        }
        this.turnIndex = -1;
        this.nextTurn();
    }

    private Turn currentTurn() {
        return this.endNight == null || this.nightTurns == null || this.turnIndex < 0 || this.turnIndex >= this.nightTurns.size() ? null : this.nightTurns.get(this.turnIndex);
    }

    private List<Player> actors(Turn t) {
        ArrayList<Player> list = new ArrayList<Player>();
        Map votes = Map.of();
        try {
            Field f = GameManager.class.getDeclaredField("votes");
            f.setAccessible(true);
            votes = (Map)f.get(this.game);
        }
        catch (ClassCastException | ReflectiveOperationException exception) {
            // empty catch block
        }
        for (UUID uuid : this.game.getPlayers().keySet()) {
            Player p = Bukkit.getPlayer((UUID)uuid);
            String role = this.roleName(uuid);
            if (p == null || role == null || !t.roles.contains(role) || !this.game.isPlayerAlive(p)) continue;
            if ("WEREWOLF".equals(role)) {
                if (votes.containsKey(uuid)) continue;
                list.add(p);
                continue;
            }
            if (!this.nightActions(p).stream().anyMatch(t.tools::contains)) continue;
            list.add(p);
        }
        return list;
    }

    private void nextTurn() {
        long now;
        this.closeMenus();
        ++this.turnIndex;
        if (this.turnIndex >= this.nightTurns.size()) {
            Runnable end = this.endNight;
            int night = this.seqNight;
            this.resetNight();
            if (this.game.getCurrentState() == GameManager.GameState.NIGHT && this.game.getDayNumber() == night) {
                end.run();
            }
            return;
        }
        Turn t = this.nightTurns.get(this.turnIndex);
        List<Player> actors = this.actors(t);
        this.turnFake = actors.isEmpty();
        this.turnStart = now = System.currentTimeMillis();
        this.turnEnd = now + (this.turnFake ? (long)(5000 + this.random.nextInt(3001)) : (long)t.seconds * 1000L);
        this.announceTurn(t.wake);
        for (Player a : actors) {
            Bukkit.getScheduler().runTask((Plugin)this, () -> this.openNight(a));
        }
    }

    private void stepNight() {
        boolean done;
        if (this.game.getDayNumber() != this.seqNight) {
            this.resetNight();
            return;
        }
        Turn t = this.currentTurn();
        if (t == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean bl = done = !this.turnFake && now - this.turnStart >= 5000L && this.actors(t).isEmpty();
        if (now >= this.turnEnd || done) {
            this.nextTurn();
        }
    }

    private void resetNight() {
        this.endNight = null;
        this.nightTurns = null;
        this.turnIndex = -1;
    }

    private void closeMenus() {
        for (UUID uuid : this.game.getPlayers().keySet()) {
            Player p = Bukkit.getPlayer((UUID)uuid);
            if (p == null || !(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)) continue;
            p.closeInventory();
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String cmd;
        if (!this.isActive(this.game.getCurrentState())) {
            return;
        }
        String[] parts = event.getMessage().substring(1).toLowerCase().split(" +");
        if (parts.length < 2) {
            return;
        }
        String string = cmd = parts[0].contains(":") ? parts[0].substring(parts[0].indexOf(58) + 1) : parts[0];
        if (!(cmd.equals("lg") || cmd.equals("loupgarou") || cmd.equals("werewolf"))) {
            return;
        }
        if (!ACTION_SUBCOMMANDS.contains(parts[1])) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("\u00a76[LG+] \u00a7r\u00a77Utilise l'objet \u00a79Nuit\u00a77 (clic droit) quand c'est ton tour.");
    }

    private void tickElection(GameManager.GameState state) {
        if (!this.mayorEnabled()) {
            return;
        }
        if (!this.provisionalNotified && this.firstDay < 0 && this.game.getMayor() != null) {
            this.provisionalNotified = true;
            Player m = Bukkit.getPlayer((UUID)this.game.getMayor());
            if (m != null) {
                m.sendMessage("\u00a76[LG+] \u00a7r\u00a77Tu n'es que maire provisoire : le vrai maire sera \u00e9lu pendant le premier jour.");
            }
        }
        if (state == GameManager.GameState.DAY && this.firstDay < 0) {
            this.firstDay = this.game.getDayNumber();
            this.electionOpen = true;
            this.mayorVotes.clear();
            this.gameBroadcast("\u00a76[LG+] \u00a7r\u00a76\u00c9lection du maire : vote avec le \u00a7lcasque dor\u00e9\u00a76 avant la fin du jour !");
        } else if (this.electionOpen && state != GameManager.GameState.DAY) {
            this.closeElection();
        }
    }

    private void closeElection() {
        this.electionOpen = false;
        HashMap<UUID, Integer> count = new HashMap<UUID, Integer>();
        for (Map.Entry<UUID, UUID> e2 : this.mayorVotes.entrySet()) {
            Player voter = Bukkit.getPlayer((UUID)e2.getKey());
            Player target = Bukkit.getPlayer((UUID)e2.getValue());
            if (voter == null || target == null || !this.game.isPlayerAlive(voter) || !this.game.isPlayerAlive(target)) continue;
            count.merge(e2.getValue(), 1, Integer::sum);
        }
        this.mayorVotes.clear();
        if (count.isEmpty()) {
            Player current;
            Player player = current = this.game.getMayor() == null ? null : Bukkit.getPlayer((UUID)this.game.getMayor());
            if (current != null) {
                this.gameBroadcast("\u00a76[LG+] \u00a7r\u00a76Personne n'a vot\u00e9 : \u00a7e" + current.getName() + "\u00a76 reste maire.");
            }
            return;
        }
        int max = (Integer)Collections.max(count.values());
        List<UUID> best = count.entrySet().stream().filter(e -> (Integer)e.getValue() == max).map(Map.Entry::getKey).toList();
        Player winner = Bukkit.getPlayer((UUID)best.get(this.random.nextInt(best.size())));
        if (winner == null) {
            return;
        }
        if (best.size() > 1) {
            this.gameBroadcast("\u00a76[LG+] \u00a7r\u00a76\u00c9galit\u00e9 : le sort d\u00e9signe le maire.");
        }
        this.game.setNewMayor(winner, true);
    }

    private void tickPlayer(Player p, UUID uuid, GameManager.GameState state) {
        Location seat = this.assigned.get(uuid);
        if (seat != null && (!p.getWorld().equals((Object)seat.getWorld()) || p.getLocation().distanceSquared(seat) > 1.0)) {
            Location back = seat.clone();
            back.setYaw(p.getLocation().getYaw());
            back.setPitch(p.getLocation().getPitch());
            p.teleport(back);
        }
        this.syncTools(p, this.desiredTools(p, state));
        this.applyNightBlindness(p, uuid, state);
        if (this.power("isHunterAwaitingRevenge", p) && this.hunterNotified.add(uuid)) {
            this.sendHunterChoices(p);
        }
    }

    private void applyNightBlindness(Player p, UUID uuid, GameManager.GameState state) {
        boolean blind;
        boolean bl = blind = state == GameManager.GameState.NIGHT && this.game.isPlayerAlive(p) && !"WEREWOLF".equals(this.roleName(uuid));
        if (blind) {
            this.blinded.add(uuid);
            PotionEffect current = p.getPotionEffect(PotionEffectType.BLINDNESS);
            if (current == null || !current.isInfinite()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, -1, 0, false, false, false));
            }
        } else if (this.blinded.remove(uuid)) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
    }

    private void updateVisibility(GameManager.GameState state) {
        for (UUID viewerId : this.game.getPlayers().keySet()) {
            Player viewer = Bukkit.getPlayer((UUID)viewerId);
            if (viewer == null) continue;
            boolean night = state == GameManager.GameState.NIGHT && this.game.isPlayerAlive(viewer);
            Set set = this.hidden.computeIfAbsent(viewerId, k -> new HashSet());
            for (UUID targetId : this.game.getPlayers().keySet()) {
                Player target;
                if (targetId.equals(viewerId) || (target = Bukkit.getPlayer((UUID)targetId)) == null) continue;
                if (night) {
                    if (!set.add(targetId)) continue;
                    viewer.hidePlayer((Plugin)this, target);
                    continue;
                }
                if (!set.remove(targetId)) continue;
                viewer.showPlayer((Plugin)this, target);
            }
        }
    }

    private void showAll() {
        for (Map.Entry<UUID, Set<UUID>> e : this.hidden.entrySet()) {
            Player viewer = Bukkit.getPlayer((UUID)e.getKey());
            if (viewer == null) continue;
            for (UUID t : e.getValue()) {
                Player target = Bukkit.getPlayer((UUID)t);
                if (target == null) continue;
                viewer.showPlayer((Plugin)this, target);
            }
        }
        this.hidden.clear();
    }

    private void updatePhaseBar(GameManager.GameState state) {
        Turn turn;
        Object label;
        int total;
        if (state != this.barState) {
            this.barState = state;
            this.phaseStart = System.currentTimeMillis();
        }
        BossBar.Color color = switch (state) {
            case GameManager.GameState.NIGHT -> {
                total = this.lgPlugin.getConfigManager().getNightDuration();
                label = "Nuit " + this.game.getDayNumber();
                yield BossBar.Color.BLUE;
            }
            case GameManager.GameState.DAY -> {
                total = this.lgPlugin.getConfigManager().getDayDuration();
                label = "Jour " + this.game.getDayNumber() + (this.electionOpen ? " - \u00e9lection du maire" : "");
                yield BossBar.Color.YELLOW;
            }
            case GameManager.GameState.VOTE -> {
                total = this.lgPlugin.getConfigManager().getVoteDuration();
                label = "Vote du village";
                yield BossBar.Color.RED;
            }
            default -> {
                total = 0;
                label = "";
                yield BossBar.Color.WHITE;
            }
        };
        if (total <= 0) {
            this.hideBar();
            return;
        }
        long left = Math.max(0L, (long)total * 1000L - (System.currentTimeMillis() - this.phaseStart));
        Turn turn2 = turn = state == GameManager.GameState.NIGHT ? this.currentTurn() : null;
        if (turn != null) {
            label = (String)label + " - " + turn.label;
            total = (int)Math.max(1L, (this.turnEnd - this.turnStart) / 1000L);
            left = Math.max(0L, this.turnEnd - System.currentTimeMillis());
        }
        long sec = (left + 999L) / 1000L;
        this.phaseBar.name((Component)Component.text((String)((String)label + " - " + sec / 60L + ":" + String.format("%02d", sec % 60L))));
        this.phaseBar.progress(Math.min(1.0f, (float)left / ((float)total * 1000.0f)));
        this.phaseBar.color(color);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.concerned(p)) {
                if (!this.barViewers.add(p.getUniqueId())) continue;
                p.showBossBar(this.phaseBar);
                continue;
            }
            if (!this.barViewers.remove(p.getUniqueId())) continue;
            p.hideBossBar(this.phaseBar);
        }
    }

    private void hideBar() {
        for (UUID uuid : this.barViewers) {
            Player p = Bukkit.getPlayer((UUID)uuid);
            if (p == null) continue;
            p.hideBossBar(this.phaseBar);
        }
        this.barViewers.clear();
    }

    private Map<UUID, List<String>> mayorTally() {
        HashMap<UUID, List<String>> tally = new HashMap<UUID, List<String>>();
        for (Map.Entry<UUID, UUID> e : this.mayorVotes.entrySet()) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            tally.computeIfAbsent(e.getValue(), k -> new ArrayList<String>()).add(name == null ? "?" : name);
        }
        return tally;
    }

    private Map<UUID, List<String>> voteTally(boolean wolvesOnly) {
        HashMap<UUID, List<String>> tally = new HashMap<UUID, List<String>>();
        try {
            Field f = GameManager.class.getDeclaredField("votes");
            f.setAccessible(true);
            Map votes = (Map)f.get(this.game);
            for (Object o : votes.entrySet()) { Map.Entry e = (Map.Entry) o;
                String name;
                if (wolvesOnly && !"WEREWOLF".equals(this.roleName((UUID)e.getKey()))) continue;
                tally.computeIfAbsent((UUID)e.getValue(), k -> new ArrayList()).add((name = Bukkit.getOfflinePlayer((UUID)((UUID)e.getKey())).getName()) == null ? "?" : name);
            }
        }
        catch (ClassCastException | ReflectiveOperationException exception) {
            // empty catch block
        }
        return tally;
    }

    private void updateVoteLabels(GameManager.GameState state) {
        boolean show;
        boolean bl = show = state == GameManager.GameState.VOTE || state == GameManager.GameState.MAYOR_VOTE;
        Map<UUID, List<String>> tally = show ? this.voteTally(false) : (this.electionOpen ? this.mayorTally() : Map.of());
        this.voteLabels.entrySet().removeIf(e -> {
            Player t = Bukkit.getPlayer((UUID)((UUID)e.getKey()));
            if (tally.containsKey(e.getKey()) && t != null && ((TextDisplay)e.getValue()).isValid()) {
                return false;
            }
            ((TextDisplay)e.getValue()).remove();
            return true;
        });
        for (Map.Entry<UUID, List<String>> e2 : tally.entrySet()) {
            Player t = Bukkit.getPlayer((UUID)e2.getKey());
            if (t == null) continue;
            Location loc = t.getLocation().add(0.0, 2.45, 0.0);
            TextDisplay d = this.voteLabels.get(e2.getKey());
            if (d == null) {
                d = (TextDisplay)t.getWorld().spawn(loc, TextDisplay.class, td -> {
                    td.setPersistent(false);
                    td.setBillboard(Display.Billboard.CENTER);
                });
                this.voteLabels.put(e2.getKey(), d);
            } else if (!d.getWorld().equals((Object)loc.getWorld()) || d.getLocation().distanceSquared(loc) > 0.01) {
                d.teleport(loc);
            }
            int n = e2.getValue().size();
            d.text((Component)Component.text((String)(n + (n > 1 ? " votes" : " vote")), (TextColor)NamedTextColor.RED));
        }
    }

    private void removeVoteLabels() {
        for (TextDisplay d : this.voteLabels.values()) {
            d.remove();
        }
        this.voteLabels.clear();
    }

    private void fillVoteLore(Menu menu) {
        if (menu.action != Tool.CLAW && menu.action != Tool.BALLOT && menu.action != Tool.MAYOR) {
            return;
        }
        Map<UUID, List<String>> tally = menu.action == Tool.MAYOR ? this.mayorTally() : this.voteTally(menu.action == Tool.CLAW);
        for (Map.Entry<Integer, UUID> e : menu.targets.entrySet()) {
            ItemStack head = menu.inventory.getItem(e.getKey().intValue());
            if (head == null) continue;
            ItemMeta meta = head.getItemMeta();
            List voters = tally.getOrDefault(e.getValue(), List.of());
            meta.setLore(voters.isEmpty() ? List.of("\u00a770 vote") : List.of("\u00a7c" + voters.size() + (voters.size() > 1 ? " votes" : " vote") + " : \u00a7f" + String.join((CharSequence)", ", voters)));
            head.setItemMeta(meta);
        }
    }

    private void refreshOpenMenus() {
        for (UUID uuid : this.game.getPlayers().keySet()) {
            InventoryHolder inventoryHolder;
            Player p = Bukkit.getPlayer((UUID)uuid);
            if (p == null || !((inventoryHolder = p.getOpenInventory().getTopInventory().getHolder()) instanceof Menu)) continue;
            Menu menu = (Menu)inventoryHolder;
            this.fillVoteLore(menu);
        }
    }

    private void assignSeats() {
        this.assigned.clear();
        if (!this.isActive(this.game.getCurrentState())) {
            return;
        }
        ArrayList<UUID> participants = new ArrayList<UUID>(this.game.getPlayers().keySet());
        if (this.seats.isEmpty()) {
            this.getLogger().warning("Aucune dalle enregistr\u00e9e (/lgdalle add) : joueurs non plac\u00e9s.");
            return;
        }
        if (this.seats.size() < participants.size()) {
            this.broadcastAdmins("Pas assez de dalles : " + this.seats.size() + " pour " + participants.size() + " joueurs. Les joueurs en trop ne sont pas plac\u00e9s.");
        }
        ArrayList<Location> free = new ArrayList<Location>(this.seats);
        Collections.shuffle(free);
        for (UUID uuid : participants) {
            if (free.isEmpty()) break;
            Player p = Bukkit.getPlayer((UUID)uuid);
            if (p == null) continue;
            Location seat = (Location)free.remove(0);
            this.assigned.put(uuid, seat);
            p.teleport(seat);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onMove(PlayerMoveEvent event) {
        Location seat = this.assigned.get(event.getPlayer().getUniqueId());
        if (seat == null) {
            return;
        }
        Location to = event.getTo();
        if (to.getWorld().equals((Object)seat.getWorld()) && Math.abs(to.getX() - seat.getX()) < 0.05 && Math.abs(to.getY() - seat.getY()) < 0.05 && Math.abs(to.getZ() - seat.getZ()) < 0.05) {
            return;
        }
        Location fixed = seat.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());
        event.setTo(fixed);
    }

    @EventHandler(ignoreCancelled=true)
    public void onSpectate(PlayerStartSpectatingEntityEvent event) {
        if (this.assigned.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean concerned(Player p) {
        return this.game.getPlayers().containsKey(p.getUniqueId()) || p.getWorld().getName().equals(this.lgWorld);
    }

    private static boolean fromLoupGarou() {
        return StackWalker.getInstance().walk(s -> s.anyMatch(f -> f.getClassName().startsWith("org.gh0st.")));
    }

    private void gameBroadcast(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!this.concerned(p)) continue;
            p.sendMessage(message);
        }
    }

    @EventHandler
    public void onBroadcast(BroadcastMessageEvent event) {
        if (!LGPlus.fromLoupGarou()) {
            return;
        }
        if (this.mayorEnabled() && StackWalker.getInstance().walk(st -> st.anyMatch(f -> f.getMethodName().equals("electMayor"))).booleanValue()) {
            event.setCancelled(true);
            return;
        }
        if (this.getConfig().getBoolean("chat.filtre", true) || this.getConfig().getBoolean("chat.reparer-accents", true)) {
            Component original = event.message();
            Component fixed = this.getConfig().getBoolean("chat.reparer-accents", true) ? ChatFilter.repair(original) : original;
            if (this.getConfig().getBoolean("chat.filtre", true)
                    && this.chatFilter.dropBroadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(fixed))) {
                event.setCancelled(true);
                return;
            }
            if (fixed != original) event.message(fixed);
        }
        event.getRecipients().removeIf(r -> {
            Player p;
            return r instanceof Player && !this.concerned(p = (Player)r);
        });
    }

    @EventHandler(ignoreCancelled=true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!this.game.getPlayers().containsKey(event.getPlayer().getUniqueId()) && LGPlus.fromLoupGarou()) {
            event.setCancelled(true);
        }
    }

    private Set<Tool> desiredTools(Player p, GameManager.GameState state) {
        EnumSet<Tool> tools = EnumSet.noneOf(Tool.class);
        UUID uuid = p.getUniqueId();
        if (p.getWorld().getName().equals(this.lgWorld)) {
            tools.add(Tool.BOOK);
        }
        if (!this.isActive(state) || this.roleName(uuid) == null || !this.game.isPlayerAlive(p)) {
            return tools;
        }
        if (this.skippable(state) && this.getConfig().getBoolean("passer.actif", true)) {
            tools.add(Tool.SKIP);
        }
        if (state == GameManager.GameState.NIGHT) {
            tools.add(Tool.NIGHT);
        } else if (state == GameManager.GameState.DAY && this.electionOpen) {
            tools.add(Tool.MAYOR);
        } else if (state == GameManager.GameState.VOTE) {
            tools.add(Tool.BALLOT);
        } else if (state == GameManager.GameState.MAYOR_VOTE && uuid.equals(this.game.getMayor())) {
            tools.add(Tool.BALLOT);
        }
        return tools;
    }

    private List<Tool> nightActions(Player p) {
        ArrayList<Tool> tools = new ArrayList<Tool>();
        UUID uuid = p.getUniqueId();
        String role = this.roleName(uuid);
        if (role == null || !this.game.isPlayerAlive(p)) {
            return tools;
        }
        switch (role) {
            case "WEREWOLF": {
                tools.add(Tool.CLAW);
                break;
            }
            case "SEER": {
                if (this.power("hasSeerUsedPower", p)) break;
                tools.add(Tool.CRYSTAL);
                break;
            }
            case "GUARD": {
                if (this.power("hasGuardUsedPower", p)) break;
                tools.add(Tool.PROTECT);
                break;
            }
            case "CUPID": {
                if (this.power("hasCupidUsedPower", p)) break;
                tools.add(Tool.ARROW);
                break;
            }
            case "LITTLE_GIRL": {
                if (this.power("hasLittleGirlUsedPower", p)) break;
                tools.add(Tool.SPY);
                break;
            }
            case "ASSASSIN": {
                if (this.power("hasAssassinUsedPower", p)) break;
                tools.add(Tool.DAGGER);
                break;
            }
            case "WITCH": {
                Integer done = this.witchDone.get(uuid);
                if (done != null && done.intValue() == this.game.getDayNumber()) break;
                if (!this.power("hasWitchUsedHealPotion", p)) {
                    tools.add(Tool.LIFE);
                }
                if (!this.power("hasWitchUsedPoisonPotion", p)) {
                    tools.add(Tool.DEATH);
                }
                if (tools.isEmpty()) break;
                tools.add(Tool.NOTHING);
                break;
            }
        }
        return tools;
    }

    private ItemStack createTool(Tool tool) {
        ItemStack item = new ItemStack(tool.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(tool.name);
        meta.setLore(List.of(switch (tool) {
            case NIGHT -> "\u00a77Clic droit : tes actions de la nuit";
            case MAYOR -> "\u00a77Clic droit : \u00e9lire le maire";
            case BOOK -> "\u00a77Clic droit : liste des r\u00f4les";
            case SKIP -> "\u00a77Clic droit : voter pour passer \u00e0 la suite";
            default -> "\u00a77Clic droit : choisir pour qui voter";
        }));
        if (meta instanceof org.bukkit.inventory.meta.BookMeta book) {
            book.setTitle("Roles");
            book.setAuthor("LG+");
            book.addPage(" ");
        }
        meta.getPersistentDataContainer().set(this.toolKey, PersistentDataType.STRING, tool.name());
        item.setItemMeta(meta);
        return item;
    }

    private Tool toolOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = (String)item.getItemMeta().getPersistentDataContainer().get(this.toolKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return Tool.valueOf(id);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void syncTools(Player p, Set<Tool> wanted) {
        PlayerInventory inv = p.getInventory();
        EnumSet<Tool> present = EnumSet.noneOf(Tool.class);
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; ++i) {
            Tool t = this.toolOf(contents[i]);
            if (t == null || wanted.contains((Object)t) && present.add(t)) continue;
            inv.setItem(i, null);
        }
        for (Tool t : wanted) {
            if (present.contains((Object)t)) continue;
            ItemStack fixed = t.slot >= 0 ? inv.getItem(t.slot) : null;
            int slot = t.slot >= 0 && (fixed == null || fixed.getType().isAir()) ? t.slot : this.firstEmptyHotbar(inv);
            if (slot >= 0) {
                inv.setItem(slot, this.createTool(t));
                continue;
            }
            inv.addItem(new ItemStack[]{this.createTool(t)});
        }
    }

    private int firstEmptyHotbar(PlayerInventory inv) {
        // 7 et 8 sont réservés au livre des rôles et au papier « Passer à la suite »
        for (int i = 0; i < 7; ++i) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) continue;
            return i;
        }
        return -1;
    }

    private void removeAllTools(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; ++i) {
            if (this.toolOf(contents[i]) == null) continue;
            p.getInventory().setItem(i, null);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Tool tool = this.toolOf(event.getItem());
        if (tool == null) {
            return;
        }
        event.setCancelled(true);
        this.openFor(event.getPlayer(), tool);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Tool tool = this.toolOf(event.getPlayer().getInventory().getItemInMainHand());
        if (tool == null) {
            return;
        }
        event.setCancelled(true);
        this.openFor(event.getPlayer(), tool);
    }

    @EventHandler(ignoreCancelled=true)
    public void onDrop(PlayerDropItemEvent event) {
        if (this.toolOf(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    /** Les objets LGPlus ne se déplacent pas (inventaire, touches 1-9, main secondaire). */
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onToolClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            return;
        }
        PlayerInventory inv = event.getWhoClicked().getInventory();
        ItemStack hotbar = event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0 ? inv.getItem(event.getHotbarButton()) : null;
        ItemStack offhand = event.getClick() == ClickType.SWAP_OFFHAND ? inv.getItemInOffHand() : null;
        if (this.toolOf(event.getCurrentItem()) != null || this.toolOf(event.getCursor()) != null
                || this.toolOf(hotbar) != null || this.toolOf(offhand) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onToolDrag(InventoryDragEvent event) {
        if (this.toolOf(event.getOldCursor()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (this.toolOf(event.getMainHandItem()) != null || this.toolOf(event.getOffHandItem()) != null) {
            event.setCancelled(true);
        }
    }

    private void openFor(Player p, Tool item) {
        long now = System.currentTimeMillis();
        Long last = this.lastUse.get(p.getUniqueId());
        if (last != null && now - last < 400L) {
            return;
        }
        this.lastUse.put(p.getUniqueId(), now);
        if (item == Tool.BOOK) {
            boolean running = this.isActive(this.game.getCurrentState());
            Set<String> present = new HashSet<String>();
            if (running) {
                for (UUID uuid : this.game.getPlayers().keySet()) {
                    String r = this.roleName(uuid);
                    if (r != null) present.add(r);
                }
            }
            p.openBook(RoleBook.build(running ? present : null, this.mayorEnabled()));
            return;
        }
        if (item == Tool.SKIP) {
            this.voteSkip(p);
            return;
        }
        if (item == Tool.BALLOT) {
            this.openTargets(p, Tool.BALLOT, null);
            return;
        }
        if (item == Tool.MAYOR) {
            if (this.electionOpen) {
                this.openTargets(p, Tool.MAYOR, "\u00c9lection du maire");
            }
            return;
        }
        if (item == Tool.NIGHT && this.game.getCurrentState() == GameManager.GameState.NIGHT) {
            this.openNight(p);
        }
    }

    private List<Tool> turnActions(Player p) {
        List<Tool> actions = this.nightActions(p);
        Turn t = this.currentTurn();
        if (this.endNight != null) {
            if (t == null) {
                actions.clear();
            } else {
                actions.retainAll(t.tools);
            }
        }
        return actions;
    }

    private void openNight(Player p) {
        if (!p.isOnline() || this.game.getCurrentState() != GameManager.GameState.NIGHT) {
            return;
        }
        List<Tool> actions = this.turnActions(p);
        if (actions.isEmpty()) {
            p.sendActionBar((Component)Component.text((String)"Rien \u00e0 faire pour l'instant", (TextColor)NamedTextColor.GRAY));
        } else if (actions.size() == 1) {
            this.openTargets(p, actions.get(0), null);
        } else {
            this.openChoice(p, actions);
        }
    }

    private void openChoice(Player p, List<Tool> actions) {
        Inventory inv;
        Menu menu = new Menu(null);
        menu.inventory = inv = Bukkit.createInventory((InventoryHolder)menu, (int)9, (Component)Component.text((String)"Que fais-tu cette nuit ?"));
        int slot = 2;
        for (Tool t : actions) {
            ItemStack icon = new ItemStack(t.material);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(t.name);
            if (t == Tool.LIFE) {
                Player victim = this.wolfVictim();
                meta.setLore(List.of(victim == null ? "\u00a77Les loups n'ont pas encore choisi" : "\u00a7cVictime des loups : \u00a7f" + victim.getName()));
            }
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            menu.actions.put(slot, t);
            slot += 2;
        }
        p.openInventory(inv);
    }

    private void openTargets(Player p, Tool action, String title) {
        Inventory inv;
        Menu menu = new Menu(action);
        ArrayList<Player> list = new ArrayList<Player>();
        boolean selfAllowed = action == Tool.PROTECT || action == Tool.ARROW || action == Tool.MAYOR;
        for (UUID uuid : this.game.getPlayers().keySet()) {
            Player t = Bukkit.getPlayer((UUID)uuid);
            if (t == null || !this.game.isPlayerAlive(t) || t.equals((Object)p) && !selfAllowed || action == Tool.CLAW && "WEREWOLF".equals(this.roleName(uuid)) || action == Tool.ARROW && uuid.equals(this.cupidFirst.get(p.getUniqueId()))) continue;
            list.add(t);
        }
        int size = Math.min(54, Math.max(9, (list.size() + 8) / 9 * 9));
        menu.inventory = inv = Bukkit.createInventory((InventoryHolder)menu, (int)size, (Component)Component.text((String)(title != null ? title : LGPlus.stripColors(action.name))));
        for (int i = 0; i < list.size() && i < size; ++i) {
            Player t = (Player)list.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta)head.getItemMeta();
            meta.setOwningPlayer((OfflinePlayer)t);
            meta.setDisplayName("\u00a7f" + t.getName());
            head.setItemMeta((ItemMeta)meta);
            inv.setItem(i, head);
            menu.targets.put(i, t.getUniqueId());
        }
        this.fillVoteLore(menu);
        p.openInventory(inv);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        Player target;
        Tool clicked;
        InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
        if (!(inventoryHolder instanceof Menu)) {
            return;
        }
        Menu menu = (Menu)inventoryHolder;
        event.setCancelled(true);
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player p = (Player)humanEntity;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Tool tool = clicked = menu.action != null ? menu.action : menu.actions.get(slot);
        if (clicked != null && clicked != Tool.BALLOT && clicked != Tool.MAYOR && !this.turnActions(p).contains((Object)clicked)) {
            p.closeInventory();
            p.sendActionBar((Component)Component.text((String)"Ce n'est plus le moment", (TextColor)NamedTextColor.GRAY));
            return;
        }
        if (menu.action == null) {
            Tool t = menu.actions.get(slot);
            if (t == null) {
                return;
            }
            switch (t) {
                case NOTHING: {
                    this.witchDone.put(p.getUniqueId(), this.game.getDayNumber());
                    p.closeInventory();
                    p.sendMessage("\u00a76[LG+] \u00a7rTu termines ton tour.");
                    break;
                }
                case LIFE: {
                    Player victim = this.wolfVictim();
                    if (victim == null) {
                        p.sendActionBar((Component)Component.text((String)"Les loups n'ont pas encore choisi leur victime", (TextColor)NamedTextColor.RED));
                        return;
                    }
                    p.closeInventory();
                    p.performCommand(String.format(Tool.LIFE.command, victim.getName()));
                    break;
                }
                default: {
                    Bukkit.getScheduler().runTask((Plugin)this, () -> this.openTargets(p, t, null));
                }
            }
            return;
        }
        UUID targetId = menu.targets.get(slot);
        Player player = target = targetId == null ? null : Bukkit.getPlayer((UUID)targetId);
        if (target == null) {
            return;
        }
        if (menu.action == Tool.MAYOR) {
            if (!this.electionOpen || !this.game.isPlayerAlive(p)) {
                p.closeInventory();
                return;
            }
            this.mayorVotes.put(p.getUniqueId(), target.getUniqueId());
            p.closeInventory();
            p.sendMessage("\u00a76[LG+] \u00a7r\u00a76Tu votes pour \u00a7e" + target.getName() + "\u00a76 comme maire.");
            return;
        }
        if (menu.action == Tool.ARROW) {
            Player firstPlayer;
            UUID first = this.cupidFirst.get(p.getUniqueId());
            Player player2 = firstPlayer = first == null ? null : Bukkit.getPlayer((UUID)first);
            if (firstPlayer == null || firstPlayer.equals((Object)target)) {
                this.cupidFirst.put(p.getUniqueId(), target.getUniqueId());
                Bukkit.getScheduler().runTask((Plugin)this, () -> this.openTargets(p, Tool.ARROW, "Second amoureux (1er : " + target.getName() + ")"));
            } else {
                this.cupidFirst.remove(p.getUniqueId());
                p.closeInventory();
                p.performCommand("lg lier " + firstPlayer.getName() + " " + target.getName());
            }
            return;
        }
        p.closeInventory();
        p.performCommand(String.format(menu.action.command, target.getName()));
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    private static String stripColors(String s) {
        return s.replaceAll("\u00a7.", "");
    }

    private String roleName(UUID uuid) {
        String string;
        Object role = this.game.getPlayers().get(uuid);
        if (role instanceof Enum) {
            Enum e = (Enum)role;
            string = e.name();
        } else {
            string = null;
        }
        return string;
    }

    private boolean power(String method, Player p) {
        try {
            return (Boolean)GameManager.class.getMethod(method, Player.class).invoke((Object)this.game, p);
        }
        catch (ClassCastException | ReflectiveOperationException e) {
            return false;
        }
    }

    private Player wolfVictim() {
        try {
            Field f = GameManager.class.getDeclaredField("votes");
            f.setAccessible(true);
            Map votes = (Map)f.get(this.game);
            HashMap<UUID, Integer> count = new HashMap<UUID, Integer>();
            for (Object o : votes.entrySet()) { Map.Entry e = (Map.Entry) o;
                if (!"WEREWOLF".equals(this.roleName((UUID)e.getKey()))) continue;
                count.merge((UUID)e.getValue(), 1, Integer::sum);
            }
            UUID best = count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            return best == null ? null : Bukkit.getPlayer((UUID)best);
        }
        catch (ClassCastException | ReflectiveOperationException e) {
            return null;
        }
    }

    private void sendHunterChoices(Player hunter) {
        hunter.sendMessage("\u00a76[LG+] \u00a7r\u00a7cTu es mort : clique sur un nom pour emporter quelqu'un avec toi.");
        TextComponent line = Component.empty();
        for (UUID uuid : this.game.getPlayers().keySet()) {
            Player t = Bukkit.getPlayer((UUID)uuid);
            if (t == null || t.equals((Object)hunter) || !this.game.isPlayerAlive(t)) continue;
            line = line.append(Component.text((String)("[" + t.getName() + "] "), (TextColor)NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand((String)("/lg vengeance " + t.getName()))));
        }
        hunter.sendMessage((Component)line);
    }

    private void next(CommandSender sender, boolean byAdmin) {
        GameManager.GameState state = this.game.getCurrentState();
        if (!this.isActive(state)) {
            LGPlus.msg(sender, "Aucune partie en cours.");
            return;
        }
        if (state == GameManager.GameState.STARTING) {
            LGPlus.msg(sender, "La premi\u00e8re nuit commence dans quelques secondes.");
            return;
        }
        if (state == GameManager.GameState.NIGHT && this.currentTurn() != null) {
            this.turnEnd = 0L;
            this.turnStart = 0L;
            this.stepNight();
            if (byAdmin) {
                this.gameBroadcast("\u00a76[LG+] \u00a7r\u00a7eTour pass\u00e9 par un admin.");
            }
            return;
        }
        try {
            Field f = GameManager.class.getDeclaredField("phaseTimerTask");
            f.setAccessible(true);
            BukkitTask task = (BukkitTask)f.get(this.game);
            if (task == null || task.isCancelled() || !Bukkit.getScheduler().isQueued(task.getTaskId())) {
                LGPlus.msg(sender, "Pas de minuteur de phase en cours (" + state.name() + ").");
                return;
            }
            Runnable action = this.phaseAction(task);
            if (action == null) {
                LGPlus.msg(sender, "Impossible de r\u00e9cup\u00e9rer la fin de phase.");
                return;
            }
            task.cancel();
            action.run();
            if (byAdmin) {
                this.gameBroadcast("\u00a76[LG+] \u00a7r\u00a7ePhase pass\u00e9e par un admin.");
            }
        }
        catch (ClassCastException | ReflectiveOperationException e) {
            LGPlus.msg(sender, "\u00c9chec : " + e.getMessage());
            this.getLogger().warning("lgnext : " + String.valueOf(e));
        }
    }

    private BukkitTask phaseTask() {
        try {
            Field f = GameManager.class.getDeclaredField("phaseTimerTask");
            f.setAccessible(true);
            BukkitTask task = (BukkitTask)f.get(this.game);
            if (task == null || task.isCancelled() || !Bukkit.getScheduler().isQueued(task.getTaskId())) {
                return null;
            }
            return task;
        }
        catch (ClassCastException | ReflectiveOperationException e) {
            return null;
        }
    }

    private Runnable phaseAction(BukkitTask task) {
        Runnable run;
        try {
            Field r = task.getClass().getField("rTask");
            Object o = r.get(task);
            if (o instanceof Runnable) {
                Runnable run2 = (Runnable)o;
                return run2;
            }
        }
        catch (ReflectiveOperationException r) {
            // empty catch block
        }
        return task instanceof Runnable ? (run = (Runnable)task) : null;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub;
        if (command.getName().equalsIgnoreCase("lgnext")) {
            this.next(sender, true);
            return true;
        }
        if (command.getName().equalsIgnoreCase("lgdebug")) {
            this.debug(sender, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("lglobby")) {
            this.toLobby(sender, args);
            return true;
        }
        switch (sub = args.length == 0 ? "" : args[0].toLowerCase()) {
            case "add": {
                if (!(sender instanceof Player)) {
                    LGPlus.msg(sender, "Commande joueur uniquement.");
                    return true;
                }
                Player p = (Player)sender;
                Location l = p.getLocation();
                Location seat = new Location(l.getWorld(), (double)l.getBlockX() + 0.5, l.getY(), (double)l.getBlockZ() + 0.5, l.getYaw(), l.getPitch());
                this.seats.add(seat);
                this.saveSeats();
                LGPlus.msg(sender, "Dalle \u00a7e" + this.seats.size() + "\u00a7r ajout\u00e9e (regard enregistr\u00e9).");
                break;
            }
            case "list": {
                LGPlus.msg(sender, this.seats.size() + " dalle(s).");
                for (int i = 0; i < this.seats.size(); ++i) {
                    Location s = this.seats.get(i);
                    LGPlus.msg(sender, "\u00a7e" + (i + 1) + "\u00a7r : " + s.getWorld().getName() + " " + s.getBlockX() + " " + s.getBlockY() + " " + s.getBlockZ());
                }
                break;
            }
            case "remove": {
                try {
                    int n = Integer.parseInt(args[1]);
                    this.seats.remove(n - 1);
                    this.saveSeats();
                    LGPlus.msg(sender, "Dalle " + n + " supprim\u00e9e.");
                }
                catch (RuntimeException e) {
                    LGPlus.msg(sender, "Usage : /lgdalle remove <num\u00e9ro> (voir /lgdalle list)");
                }
                break;
            }
            case "clear": {
                this.seats.clear();
                this.saveSeats();
                LGPlus.msg(sender, "Toutes les dalles ont \u00e9t\u00e9 supprim\u00e9es.");
                break;
            }
            default: {
                LGPlus.msg(sender, "Usage : /lgdalle <add|list|remove <n>|clear>");
            }
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("lgdalle") && args.length == 1) {
            return List.of("add", "list", "remove", "clear").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (command.getName().equalsIgnoreCase("lglobby") && args.length == 1 && sender.hasPermission("lgplus.admin")) {
            return null; // noms des joueurs
        }
        return List.of();
    }

    /**
     * /lglobby [joueur] : renvoie au spawn du hub (config « hub-world »). Pensé pour le PNJ « retour » :
     * action player_command « lglobby », ou console_command « lglobby {player} ».
     * Refusé pour un joueur vivant d'une partie en cours.
     */
    private void toLobby(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0) {
            if (sender instanceof Player && !sender.hasPermission("lgplus.admin")) {
                LGPlus.msg(sender, "Usage : /lglobby");
                return;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                LGPlus.msg(sender, "Joueur introuvable : " + args[0]);
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player)sender;
        } else {
            LGPlus.msg(sender, "Usage : lglobby <joueur>");
            return;
        }
        UUID uuid = target.getUniqueId();
        boolean participant = this.isActive(this.game.getCurrentState()) && this.game.getPlayers().containsKey(uuid);
        if (participant && this.game.isPlayerAlive(target)) {
            LGPlus.msg(target, "§cTu es en pleine partie : impossible de retourner au lobby maintenant.");
            if (sender != target) LGPlus.msg(sender, target.getName() + " joue une partie en cours : refusé.");
            return;
        }
        String hubName = this.getConfig().getString("hub-world", "lobby");
        World hub = Bukkit.getWorld(hubName);
        if (hub == null) {
            LGPlus.msg(sender, "§cMonde du hub introuvable : " + hubName + " (hub-world dans config.yml).");
            this.getLogger().warning("lglobby : monde " + hubName + " introuvable.");
            return;
        }
        // mort d'une partie en cours : on le libère de sa dalle, sinon il serait ramené
        this.assigned.remove(uuid);
        target.teleport(hub.getSpawnLocation());
        if (target.getGameMode() == GameMode.SPECTATOR) {
            this.allowGameMode.add(uuid);
            try {
                target.setGameMode(GameMode.SURVIVAL);
            } finally {
                this.allowGameMode.remove(uuid);
            }
        }
    }

    /** LoupGarou interdit aux morts de quitter le mode spectateur : on laisse passer le retour au lobby. */
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onLobbyGameMode(PlayerGameModeChangeEvent event) {
        if (this.allowGameMode.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(false);
        }
    }

    private void debug(CommandSender sender, String[] args) {
        Player sp;
        Player p = args.length > 0 ? Bukkit.getPlayer((String)args[0]) : (sender instanceof Player ? (sp = (Player)sender) : null);
        GameManager.GameState state = this.game.getCurrentState();
        LGPlus.msg(sender, "Etat : " + String.valueOf(state) + " | actif : " + this.isActive(state) + " | nuit n\u00b0" + this.game.getDayNumber() + " | joueurs : " + this.game.getPlayers().size() + " | dalles : " + this.seats.size() + " (plac\u00e9s : " + this.assigned.size() + ")");
        if (p == null) {
            return;
        }
        LGPlus.msg(sender, p.getName() + " : r\u00f4le " + String.valueOf(this.game.getPlayers().get(p.getUniqueId())) + " | vivant : " + this.game.isPlayerAlive(p) + " | mode : " + String.valueOf(p.getGameMode()) + " | dalle : " + (this.assigned.containsKey(p.getUniqueId()) ? "oui" : "non"));
        LGPlus.msg(sender, "Objets voulus : " + String.valueOf(this.desiredTools(p, state)) + " | actions de nuit : " + String.valueOf(this.nightActions(p)));
        EnumSet<Tool> present = EnumSet.noneOf(Tool.class);
        for (ItemStack it : p.getInventory().getContents()) {
            Tool t = this.toolOf(it);
            if (t == null) continue;
            present.add(t);
        }
        LGPlus.msg(sender, "Objets en inventaire : " + String.valueOf(present));
    }

    private void loadSeats() {
        this.seats.clear();
        ConfigurationSection sec = this.getConfig().getConfigurationSection("dalles");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            World w = Bukkit.getWorld((String)s.getString("world", ""));
            if (w == null) {
                this.getLogger().warning("Dalle " + key + " : monde " + s.getString("world") + " introuvable.");
                continue;
            }
            this.seats.add(new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch")));
        }
    }

    private void saveSeats() {
        this.getConfig().set("dalles", null);
        for (int i = 0; i < this.seats.size(); ++i) {
            Location s = this.seats.get(i);
            String p = "dalles." + (i + 1) + ".";
            this.getConfig().set(p + "world", (Object)s.getWorld().getName());
            this.getConfig().set(p + "x", (Object)s.getX());
            this.getConfig().set(p + "y", (Object)s.getY());
            this.getConfig().set(p + "z", (Object)s.getZ());
            this.getConfig().set(p + "yaw", (Object)Float.valueOf(s.getYaw()));
            this.getConfig().set(p + "pitch", (Object)Float.valueOf(s.getPitch()));
        }
        this.saveConfig();
    }

    private void cleanup() {
        Player p;
        for (UUID uuid : new ArrayList<UUID>(this.assigned.keySet())) {
            p = Bukkit.getPlayer((UUID)uuid);
            if (p == null) continue;
            this.removeAllTools(p);
        }
        for (Player p2 : Bukkit.getOnlinePlayers()) {
            this.removeAllTools(p2);
        }
        for (UUID uuid : this.blinded) {
            p = Bukkit.getPlayer((UUID)uuid);
            if (p == null) continue;
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        this.blinded.clear();
        this.showAll();
        this.hideBar();
        this.barState = null;
        this.removeVoteLabels();
        this.assigned.clear();
        this.cupidFirst.clear();
        this.witchDone.clear();
        this.hunterNotified.clear();
        this.lastUse.clear();
        this.resetNight();
        this.handledNight = -1;
        this.firstNight = -1;
        this.firstDay = -1;
        this.electionOpen = false;
        this.provisionalNotified = false;
        this.mayorVotes.clear();
    }

    private void broadcastAdmins(String text) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("lgplus.admin")) continue;
            p.sendMessage(PREFIX + text);
        }
        this.getLogger().warning(text);
    }

    private static void msg(CommandSender sender, String text) {
        sender.sendMessage(PREFIX + text);
    }
}

