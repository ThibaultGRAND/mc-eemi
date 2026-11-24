package fr.eemicraft.speedrun;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Une partie : monde neuf, plusieurs manches de roulette, le premier qui obtient la cible marque. */
final class Game {

    enum State { STARTING, SPINNING, HUNTING, PAUSE, ENDING }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final EemiSpeedrun plugin;
    private final Names names;
    final Mode mode;
    private final World world;
    private final Location spawn;
    private final int startCount;

    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Map<Material, Integer>> snapshot = new HashMap<>();
    private final Set<Material> usedItems = new HashSet<>();
    private final Set<Character> usedLetters = new HashSet<>();
    /** Joueurs qui ont clique sur « Rejouer » pendant la fin de partie : remis en file au retour au lobby. */
    private final Map<UUID, Mode> replay = new LinkedHashMap<>();

    private State state = State.STARTING;
    /** Vrai pendant close() : les teleportations vers le hub ne doivent pas retirer les joueurs de la remise en file. */
    private boolean closing;
    private int round;
    private Material targetItem;
    private char targetLetter;
    private long roundStart;
    private final BossBar bar;
    private final Scoreboard board;
    private final Objective sidebar;
    private BukkitTask ticker;
    private final List<BukkitTask> pending = new ArrayList<>();

    Game(EemiSpeedrun plugin, Mode mode, List<Player> joined) {
        this.plugin = plugin;
        this.names = plugin.names();
        this.mode = mode;
        this.startCount = joined.size();

        String worldName = plugin.getConfig().getString("world-name", "speedrun");
        plugin.deleteWorld(worldName);
        WorldCreator creator = new WorldCreator(worldName)
                .seed(ThreadLocalRandom.current().nextLong())
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL);
        this.world = creator.createWorld();
        if (world == null) throw new IllegalStateException("Le monde " + worldName + " n'a pas pu etre cree");
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(0);
        world.setGameRule(GameRule.KEEP_INVENTORY, plugin.getConfig().getBoolean("keep-inventory", true));
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setPVP(plugin.getConfig().getBoolean("pvp", false));
        Location s = world.getSpawnLocation();
        int y = world.getHighestBlockYAt(s.getBlockX(), s.getBlockZ()) + 1;
        this.spawn = new Location(world, s.getBlockX() + 0.5, y, s.getBlockZ() + 0.5);
        world.setSpawnLocation(spawn);
        world.getWorldBorder().setCenter(spawn);
        world.getWorldBorder().setSize(plugin.getConfig().getDouble("border-size", 1000));

        this.bar = BossBar.bossBar(Component.text("Speedrun"), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.sidebar = board.registerNewObjective("speedrun", Criteria.DUMMY, LEGACY.deserialize("§6§lSPEEDRUN"));
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (Player p : joined) {
            players.add(p.getUniqueId());
            playerNames.put(p.getUniqueId(), p.getName());
            points.put(p.getUniqueId(), 0);
            p.teleport(spawn);
        }
        // Inventaire vide APRES la teleportation : Multiverse-Inventories a deja enregistre celui du lobby.
        later(2, () -> {
            for (Player p : online(players)) prepare(p);
            broadcast("§eMode : §f" + mode.display + "§e, " + (rounds() == 1
                    ? "une seule manche : le premier qui trouve gagne !"
                    : rounds() + " manches.") + " Monde de survie neuf : bonne chance !");
            if (mode == Mode.LETTRE) {
                broadcast("§7Il faut obtenir un objet dont le nom §ffrancais §7commence par la lettre tiree.");
            }
            broadcast("§7Seuls comptent les objets obtenus apres l'arret de la roulette.");
            plugin.status("playing", 0, players.size());
        });
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        later(60, this::startRound);
    }

    // ------------------------------------------------------------------ manches

    private int rounds() {
        return Math.max(1, plugin.getConfig().getInt("rounds", 5));
    }

    private void startRound() {
        if (state == State.ENDING) return;
        round++;
        if (round > rounds()) {
            end(false);
            return;
        }
        state = State.SPINNING;
        targetItem = null;
        targetLetter = 0;
        List<Object> pool = new ArrayList<>(candidates());
        if (pool.isEmpty()) {
            broadcast("§cAucune cible disponible pour cette manche, fin de la partie.");
            end(false);
            return;
        }
        Object chosen = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        if (chosen instanceof Material m) {
            targetItem = m;
            usedItems.add(m);
        } else {
            targetLetter = (Character) chosen;
            usedLetters.add(targetLetter);
        }
        bar.name(LEGACY.deserialize("§eManche " + round + "/" + rounds() + " §7- la roulette tourne..."));
        bar.progress(1f);
        spin(pool, 0, 2);
    }

    /** Cibles possibles de la manche (sans repetition dans la partie si possible). */
    private List<?> candidates() {
        if (mode == Mode.LETTRE) {
            List<Character> letters = names.playableLetters(plugin.getConfig().getInt("letter-min-items", 3));
            List<Character> fresh = new ArrayList<>(letters);
            fresh.removeAll(usedLetters);
            return fresh.isEmpty() ? letters : fresh;
        }
        List<String> difficulties = plugin.getConfig().getStringList("round-difficulty");
        String tier = difficulties.isEmpty() ? "facile" : difficulties.get(Math.min(round, difficulties.size()) - 1);
        List<Material> items = new ArrayList<>(names.tier(tier));
        if (items.isEmpty()) items = new ArrayList<>(names.tier("facile"));
        List<Material> fresh = new ArrayList<>(items);
        fresh.removeAll(usedItems);
        return fresh.isEmpty() ? items : fresh;
    }

    private String label(Object o) {
        return o instanceof Material m ? names.name(m) : String.valueOf(o);
    }

    /** Animation : des cibles defilent de plus en plus lentement, puis la vraie cible apparait. */
    private void spin(List<Object> pool, int elapsed, int delay) {
        if (state != State.SPINNING) return;
        int total = Math.max(20, plugin.getConfig().getInt("spin-ticks", 70));
        if (elapsed >= total) {
            reveal();
            return;
        }
        Object shown = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        String title = mode == Mode.LETTRE ? "§e§l" + label(shown) : "§e§l?";
        String subtitle = mode == Mode.LETTRE ? "§7Roulette de lettre" : "§f" + label(shown);
        for (Player p : audience()) {
            p.showTitle(Title.title(LEGACY.deserialize(title), LEGACY.deserialize(subtitle),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(delay * 50L + 200), Duration.ZERO)));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
        }
        int next = Math.min(10, delay + (elapsed > total / 2 ? 2 : 1));
        later(delay, () -> spin(pool, elapsed + delay, next));
    }

    private void reveal() {
        String target = targetText();
        for (Player p : audience()) {
            p.showTitle(Title.title(LEGACY.deserialize(mode == Mode.LETTRE ? "§a§lLettre " + targetLetter : "§a§lA trouver"),
                    LEGACY.deserialize(mode == Mode.LETTRE ? "§fUn objet en " + targetLetter : "§f" + target),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
        broadcast("§eManche " + round + "/" + rounds() + " : " + (mode == Mode.LETTRE
                ? "trouve un objet dont le nom commence par §f§l" + targetLetter + "§e !"
                : "trouve §f§l" + target + "§e !"));
        snapshot.clear();
        for (Player p : online(players)) snapshot.put(p.getUniqueId(), count(p));
        roundStart = System.currentTimeMillis();
        state = State.HUNTING;
        updateDisplay();
    }

    private String targetText() {
        return mode == Mode.LETTRE ? "Lettre " + targetLetter : names.name(targetItem);
    }

    // ------------------------------------------------------------------ detection

    private static Map<Material, Integer> count(Player p) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack != null && !stack.getType().isAir()) counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        ItemStack cursor = p.getItemOnCursor();
        if (!cursor.getType().isAir()) counts.merge(cursor.getType(), cursor.getAmount(), Integer::sum);
        return counts;
    }

    /** Regarde si le joueur vient d'obtenir la cible ; appele toutes les 10 ticks et apres chaque ramassage/craft. */
    void check(Player p) {
        if (state != State.HUNTING || !players.contains(p.getUniqueId()) || p.getWorld() != world) return;
        Map<Material, Integer> before = snapshot.computeIfAbsent(p.getUniqueId(), k -> count(p));
        Map<Material, Integer> now = count(p);
        if (mode == Mode.OBJET) {
            if (now.getOrDefault(targetItem, 0) > before.getOrDefault(targetItem, 0)) roundWon(p, targetItem);
            return;
        }
        for (Map.Entry<Material, Integer> e : now.entrySet()) {
            if (names.initial(e.getKey()) == targetLetter && e.getValue() > before.getOrDefault(e.getKey(), 0)) {
                roundWon(p, e.getKey());
                return;
            }
        }
    }

    private void roundWon(Player winner, Material item) {
        state = State.PAUSE;
        int pts = points.merge(winner.getUniqueId(), 1, Integer::sum);
        String time = clock((System.currentTimeMillis() - roundStart) / 1000);
        broadcast("§a" + winner.getName() + " §ea trouve §f" + names.name(item) + " §een §f" + time
                + " §e! (§f" + pts + " point" + (pts > 1 ? "s" : "") + "§e)");
        for (Player p : audience()) {
            p.showTitle(Title.title(LEGACY.deserialize("§a§l" + winner.getName()),
                    LEGACY.deserialize("§f" + names.name(item) + " §7en " + time),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), p == winner ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        nextRoundLater();
    }

    private void roundTimeout() {
        state = State.PAUSE;
        String hint = "";
        if (mode == Mode.LETTRE) {
            List<String> ex = new ArrayList<>();
            for (Material m : names.examples(targetLetter, 3)) ex.add(names.name(m));
            if (!ex.isEmpty()) hint = " §7(ex. : " + String.join(", ", ex) + ")";
        }
        broadcast("§cTemps ecoule ! Personne n'a trouve §f" + targetText() + "§c." + hint);
        nextRoundLater();
    }

    private void nextRoundLater() {
        updateDisplay();
        long pause = Math.max(1, plugin.getConfig().getInt("pause-between-rounds", 6)) * 20L;
        if (round >= rounds()) later(pause, () -> end(false));
        else later(pause, this::startRound);
    }

    // ------------------------------------------------------------------ boucle et affichage

    private void tick() {
        if (state == State.ENDING) return;
        if (state == State.HUNTING) {
            for (Player p : online(players)) {
                check(p);
                if (state != State.HUNTING) break;
            }
            if (state == State.HUNTING && remaining() <= 0) roundTimeout();
        }
        updateDisplay();
    }

    private long remaining() {
        long limit = Math.max(30, plugin.getConfig().getInt("round-time", 600));
        return limit - (System.currentTimeMillis() - roundStart) / 1000;
    }

    private void updateDisplay() {
        if (state == State.HUNTING) {
            long limit = Math.max(30, plugin.getConfig().getInt("round-time", 600));
            long left = Math.max(0, remaining());
            bar.name(LEGACY.deserialize("§eManche " + round + "/" + rounds() + " §7| §f"
                    + (mode == Mode.LETTRE ? "Un objet en §l" + targetLetter : names.name(targetItem))
                    + " §7| §e" + clock(left)));
            bar.progress(Math.max(0f, Math.min(1f, left / (float) limit)));
        }
        List<String> lines = new ArrayList<>();
        lines.add("§7" + mode.display);
        lines.add("§fManche : §e" + Math.max(1, Math.min(round, rounds())) + "/" + rounds());
        if (targetItem != null || targetLetter != 0) {
            lines.add("§fCible : §a" + (mode == Mode.LETTRE ? "lettre " + targetLetter : names.name(targetItem)));
        }
        lines.add(" ");
        List<UUID> ranking = new ArrayList<>(points.keySet());
        ranking.sort(Comparator.comparingInt((UUID u) -> points.get(u)).reversed());
        int shown = 0;
        for (UUID u : ranking) {
            if (shown++ >= 10) break;
            boolean in = players.contains(u);
            lines.add((in ? "§f" : "§8") + playerNames.get(u) + "§7: §e" + points.get(u));
        }
        lines.add("§8eemicraft.mine.fun");
        for (String entry : new ArrayList<>(board.getEntries())) board.resetScores(entry);
        int score = lines.size();
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            String entry = line;
            while (!seen.add(entry)) entry = entry + "§r";
            sidebar.getScore(entry).setScore(score--);
        }
    }

    private static String clock(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // ------------------------------------------------------------------ joueurs

    private void prepare(Player p) {
        p.getInventory().clear();
        p.setItemOnCursor(null);
        p.getEnderChest().clear();
        p.setGameMode(GameMode.SURVIVAL);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20);
        p.setFoodLevel(20);
        p.setSaturation(5f);
        p.setExhaustion(0f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setScoreboard(board);
        p.showBossBar(bar);
    }

    boolean isPlayer(Player p) {
        return players.contains(p.getUniqueId());
    }

    boolean involves(Player p) {
        return players.contains(p.getUniqueId()) || spectators.contains(p.getUniqueId());
    }

    World world() {
        return world;
    }

    Location spawn() {
        return spawn;
    }

    int playerCount() {
        return players.size();
    }

    State state() {
        return state;
    }

    void addSpectator(Player p) {
        spectators.add(p.getUniqueId());
        p.teleport(spawn);
        later(2, () -> {
            if (!p.isOnline() || p.getWorld() != world) return;
            p.setGameMode(GameMode.SPECTATOR);
            p.setScoreboard(board);
            p.showBossBar(bar);
        });
        p.sendMessage(EemiSpeedrun.PREFIX + "Tu regardes la partie. §7/lobby pour partir.");
    }

    /** Le joueur quitte la partie (commande, changement de monde, deconnexion). */
    void leave(Player p, boolean teleportHub) {
        UUID id = p.getUniqueId();
        boolean wasPlayer = players.remove(id);
        spectators.remove(id);
        snapshot.remove(id);
        // parti pendant la partie ou la fin (/lobby, deconnexion, /speedrun leave) : pas remis en file
        if (!closing) {
            replay.remove(id);
            removeReplayItem(p);
        }
        p.hideBossBar(bar);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (teleportHub && p.getWorld() == world) p.teleport(plugin.hub());
        if (!wasPlayer || state == State.ENDING) return;
        broadcast("§7" + p.getName() + " a quitte la partie.");
        plugin.status("playing", 0, players.size());
        if (players.isEmpty()) end(true);
        else if (startCount > 1 && players.size() == 1 && state != State.STARTING) end(false);
    }

    // ------------------------------------------------------------------ fin

    void end(boolean aborted) {
        if (state == State.ENDING) return;
        state = State.ENDING;
        for (BukkitTask task : pending) task.cancel();
        pending.clear();
        updateDisplay();
        int best = points.isEmpty() ? 0 : points.values().stream().max(Integer::compare).orElse(0);
        List<String> winners = new ArrayList<>();
        Map<UUID, Integer> ordered = new LinkedHashMap<>(points);
        if (!aborted && best > 0) {
            for (Map.Entry<UUID, Integer> e : ordered.entrySet()) {
                if (e.getValue() == best) winners.add(playerNames.get(e.getKey()));
            }
        }
        if (aborted) {
            broadcast("§cPartie arretee.");
        } else if (winners.isEmpty()) {
            broadcast("§eFin de la partie : personne n'a marque.");
        } else {
            String list = String.join(", ", winners);
            broadcast("§6§lFin de la partie ! §e" + (winners.size() > 1 ? "Egalite : " : "Victoire de ") + "§f" + list
                    + " §eavec §f" + best + " point" + (best > 1 ? "s" : "") + "§e.");
            for (Player p : audience()) {
                p.showTitle(Title.title(LEGACY.deserialize("§6§lVictoire"),
                        LEGACY.deserialize("§f" + (list.length() > 34 ? list.substring(0, 34) + "..." : list)),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ofSeconds(1))));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        if (!aborted) {
            for (Player p : online(spectators)) giveReplay(p);
            for (Player p : online(players)) {
                replay.put(p.getUniqueId(), mode);
                p.sendMessage(EemiSpeedrun.PREFIX + "§7Retour au lobby dans quelques secondes : tu restes dans la file §e" + mode.display + "§7.");
            }
            for (UUID u : points.keySet()) {
                String name = playerNames.get(u);
                plugin.reward(name, "play");
                if (winners.contains(name)) plugin.reward(name, "win");
            }
        }
        plugin.status("ending", 0, players.size());
        Bukkit.getScheduler().runTaskLater(plugin, this::close, aborted ? 20L : 160L);
    }

    /** Renvoie tout le monde au lobby, supprime le monde et libere la place pour la partie suivante. */
    void close() {
        closing = true;
        if (ticker != null) ticker.cancel();
        for (BukkitTask task : pending) task.cancel();
        pending.clear();
        for (Player p : new ArrayList<>(world.getPlayers())) {
            p.hideBossBar(bar);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            removeReplayItem(p);
            p.teleport(plugin.hub());
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        players.clear();
        spectators.clear();
        plugin.gameClosed(this);
        plugin.requeueLater(new LinkedHashMap<>(replay));
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.deleteWorld(world.getName()), 40L);
    }

    // ------------------------------------------------------------------ rejouer

    /** Papier « Rejouer » mis dans la main, plus un bouton cliquable dans le chat (meme commande). */
    private void giveReplay(Player p) {
        ItemStack paper = new ItemStack(Material.PAPER);
        var meta = paper.getItemMeta();
        meta.displayName(LEGACY.deserialize("§a§lRejouer §7(clic droit)"));
        meta.lore(List.of(LEGACY.deserialize("§7Relance une partie §f" + mode.display),
                LEGACY.deserialize("§7des ton retour au lobby.")));
        meta.getPersistentDataContainer().set(plugin.replayKey(), org.bukkit.persistence.PersistentDataType.STRING, mode.id);
        paper.setItemMeta(meta);
        p.getInventory().setItem(p.getInventory().getHeldItemSlot(), paper);
        p.sendMessage(Component.text(EemiSpeedrun.PREFIX)
                .append(LEGACY.deserialize("§a§l[REJOUER]")
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/speedrun " + mode.id))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                LEGACY.deserialize("§fRelancer une partie " + mode.display))))
                .append(LEGACY.deserialize(" §7ou clic droit avec le papier.")));
    }

    private void removeReplayItem(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (plugin.isReplayItem(contents[i])) p.getInventory().setItem(i, null);
        }
    }

    /** Clic sur « Rejouer » pendant la fin de partie : le joueur sera remis en file au retour au lobby. */
    void replay(Player p, Mode m) {
        replay.put(p.getUniqueId(), m);
        p.sendMessage(EemiSpeedrun.PREFIX + "§aC'est note : tu rejoins la file §e" + m.display + " §aau retour au lobby.");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    /** Arret du serveur : rien ne peut plus etre planifie, tout se fait tout de suite. */
    void shutdown() {
        state = State.ENDING;
        if (ticker != null) ticker.cancel();
        for (BukkitTask task : pending) task.cancel();
        pending.clear();
        for (Player p : new ArrayList<>(world.getPlayers())) {
            p.hideBossBar(bar);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.teleport(plugin.hub());
        }
        plugin.deleteWorld(world.getName());
    }

    // ------------------------------------------------------------------ outils

    private List<Player> online(Set<UUID> ids) {
        List<Player> list = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && p.getWorld() == world) list.add(p);
        }
        return list;
    }

    private List<Player> audience() {
        List<Player> list = online(players);
        list.addAll(online(spectators));
        return list;
    }

    private void broadcast(String message) {
        for (Player p : audience()) p.sendMessage(EemiSpeedrun.PREFIX + message);
    }

    private void later(long ticks, Runnable r) {
        pending.removeIf(t -> t.isCancelled() || !Bukkit.getScheduler().isQueued(t.getTaskId()));
        pending.add(Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(1, ticks)));
    }
}
