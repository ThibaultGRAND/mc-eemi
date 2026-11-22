package fr.eemicraft.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mode PvP d'EEMICRAFT façon serveurs « practice » (Hypixel Duels, Minemen, Lunar) :
 * duels 1v1 par kit en file non classée ou classée (Elo), défis entre joueurs, arène FFA (kit FFA),
 * statistiques et classements. Le combat lui-même (règles 1.8) est fait par EemiCombat :
 * le monde des duels doit être dans ses « worlds ».
 * Une mort n'est jamais réelle : le coup mortel est annulé et le joueur est éliminé (pas d'écran de mort).
 */
public final class EemiDuels extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§cDuels§8] §7";
    private static final String FFA_PREFIX = "§8[§cFFA§8] §7";
    /** Version des arènes : changer ce nombre fait tout reconstruire au démarrage. */
    private static final int ARENA_VERSION = 1;

    private World world;
    private Stats stats;
    private final List<Arena> duelArenas = new ArrayList<>();
    private final List<Arena> sumoArenas = new ArrayList<>();
    private Arena ffa;
    private List<Location> ffaSpawns = List.of();

    private final Map<UUID, Match> matches = new HashMap<>();
    /** Files d'attente : clé « kit:ranked » → joueur → instant d'entrée (ms). */
    private final Map<String, LinkedHashMap<UUID, Long>> queues = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Set<UUID> ffaPlayers = new HashSet<>();
    private final Map<UUID, Integer> ffaStreak = new HashMap<>();
    private final Map<UUID, Long> ffaProtection = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** Spectateurs d'un duel (/duel spectate) et de l'arène FFA (/ffa spectate). */
    private final Set<UUID> spectators = new HashSet<>();
    private final Set<UUID> ffaSpectators = new HashSet<>();

    private record Invite(UUID from, Kit kit, long time) {}

    /** Façon dont un duel se termine, pour les récompenses EemiCore. */
    private enum End { NORMAL, KILL, FORFEIT, CANCEL }

    // ------------------------------------------------------------------ démarrage

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stats = new Stats(getDataFolder(), getLogger(), getConfig().getInt("start-elo", 1000));
        getServer().getPluginManager().registerEvents(this, this);
        setupWorld();
        getServer().getScheduler().runTaskTimer(this, this::second, 20L, 20L);
        reportStatus();
        reportFfa();
    }

    @Override
    public void onDisable() {
        for (UUID id : new HashSet<>(spectators)) stopSpectating(id, true);
        for (UUID id : new HashSet<>(ffaSpectators)) stopSpectating(id, true);
        for (Match m : new HashSet<>(matches.values())) endMatch(m, null, "Serveur arrêté.", End.CANCEL);
        for (UUID id : new HashSet<>(ffaPlayers)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) leaveFfa(p, true);
        }
        stats.save();
    }

    private void setupWorld() {
        String name = getConfig().getString("world", "duels");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new VoidGenerator()).createWorld();
            getLogger().info("Monde " + name + " cree (vide).");
        }
        if (world == null) {
            getLogger().severe("Impossible de charger ou creer le monde " + name + " : duels desactives.");
            return;
        }
        world.setSpawnLocation(0, Arena.FLOOR + 1, -300);
        world.setTime(6000);
        duelArenas.clear();
        sumoArenas.clear();
        for (int i = 0; i < getConfig().getInt("duel-arenas", 6); i++) duelArenas.add(new Arena(Arena.Type.DUEL, i, world));
        for (int i = 0; i < getConfig().getInt("sumo-arenas", 3); i++) sumoArenas.add(new Arena(Arena.Type.SUMO, i, world));
        ffa = new Arena(Arena.Type.FFA, 0, world);
        ffaSpawns = ffa.ffaSpawns();
        File state = new File(getDataFolder(), "arenas.yml");
        YamlConfiguration s = YamlConfiguration.loadConfiguration(state);
        int built = s.getInt("version", 0);
        int duels = s.getInt("duel-arenas", 0), sumos = s.getInt("sumo-arenas", 0);
        if (built != ARENA_VERSION || duels < duelArenas.size() || sumos < sumoArenas.size()) {
            buildArenas();
            s.set("version", ARENA_VERSION);
            s.set("duel-arenas", duelArenas.size());
            s.set("sumo-arenas", sumoArenas.size());
            try {
                s.save(state);
            } catch (IOException e) {
                getLogger().warning("arenas.yml : " + e.getMessage());
            }
        }
    }

    private void buildArenas() {
        List<Arena> all = new ArrayList<>(duelArenas);
        all.addAll(sumoArenas);
        all.add(ffa);
        for (Arena a : all) {
            int r = a.radius() + 2;
            for (int x = (a.cx - r) >> 4; x <= (a.cx + r) >> 4; x++)
                for (int z = (a.cz - r) >> 4; z <= (a.cz + r) >> 4; z++) world.getChunkAt(x, z).load(true);
            a.build();
        }
        getLogger().info("Arenes construites : " + duelArenas.size() + " duel, " + sumoArenas.size() + " sumo, 1 FFA.");
    }

    /** Générateur vide : le monde n'a que ce que le plugin construit. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return new Location(world, 0.5, Arena.FLOOR + 1, -299.5);
        }
    }

    // ------------------------------------------------------------------ boucle (1 s)

    private void second() {
        if (world == null) return;
        if (world.getTime() < 5000 || world.getTime() > 7000) world.setTime(6000);
        matchmaking();
        long now = System.currentTimeMillis();
        invites.values().removeIf(i -> now - i.time > 30_000);
        for (Match m : new HashSet<>(matches.values())) tickMatch(m);
        for (Map.Entry<String, LinkedHashMap<UUID, Long>> e : queues.entrySet()) {
            String[] key = e.getKey().split(":");
            Kit kit = Kit.valueOf(key[0]);
            boolean ranked = Boolean.parseBoolean(key[1]);
            for (Map.Entry<UUID, Long> q : e.getValue().entrySet()) {
                Player p = Bukkit.getPlayer(q.getKey());
                if (p == null) continue;
                long waited = (now - q.getValue()) / 1000;
                p.sendActionBar(Component.text("File " + (ranked ? "classée " : "") + kit.label + " · " + waited + " s · /duel leave pour quitter", NamedTextColor.YELLOW));
            }
        }
        for (UUID id : ffaPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) ffaSidebar(p);
        }
    }

    private void matchmaking() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, LinkedHashMap<UUID, Long>> e : queues.entrySet()) {
            String[] key = e.getKey().split(":");
            Kit kit = Kit.valueOf(key[0]);
            boolean ranked = Boolean.parseBoolean(key[1]);
            LinkedHashMap<UUID, Long> q = e.getValue();
            q.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
            boolean paired = true;
            while (paired && q.size() >= 2) {
                paired = false;
                List<UUID> ids = new ArrayList<>(q.keySet());
                outer:
                for (int i = 0; i < ids.size(); i++) {
                    for (int j = i + 1; j < ids.size(); j++) {
                        UUID a = ids.get(i), b = ids.get(j);
                        if (ranked) {
                            long wait = Math.max(now - q.get(a), now - q.get(b)) / 1000;
                            int range = getConfig().getInt("elo-range", 100) + (int) wait * getConfig().getInt("elo-range-step", 20);
                            if (Math.abs(stats.elo(a, kit) - stats.elo(b, kit)) > range) continue;
                        }
                        Arena arena = freeArena(kit.arena);
                        if (arena == null) return;
                        q.remove(a);
                        q.remove(b);
                        startMatch(kit, ranked, arena, Bukkit.getPlayer(a), Bukkit.getPlayer(b));
                        paired = true;
                        break outer;
                    }
                }
            }
        }
    }

    private Arena freeArena(Arena.Type type) {
        for (Arena a : type == Arena.Type.SUMO ? sumoArenas : duelArenas) if (a.match == null) return a;
        return null;
    }

    // ------------------------------------------------------------------ files et défis

    private String queueKey(Kit kit, boolean ranked) {
        return kit.name() + ":" + ranked;
    }

    private boolean busy(Player p, boolean tell) {
        String why = matches.containsKey(p.getUniqueId()) ? "Tu es déjà en duel."
                : ffaPlayers.contains(p.getUniqueId()) ? "Quitte d'abord le FFA (/ffa leave)."
                : null;
        if (why != null && tell) p.sendMessage(PREFIX + "§c" + why);
        return why != null;
    }

    private void joinQueue(Player p, Kit kit, boolean ranked) {
        if (world == null || busy(p, true)) return;
        leaveQueues(p.getUniqueId());
        queues.computeIfAbsent(queueKey(kit, ranked), k -> new LinkedHashMap<>()).put(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(PREFIX + "§aFile " + (ranked ? "§6classée §a" : "") + kit.label + " rejointe"
                + (ranked ? " §7(Elo " + stats.elo(p.getUniqueId(), kit) + ")" : "") + "§a. §7/duel leave pour quitter.");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        reportStatus();
    }

    private boolean leaveQueues(UUID id) {
        boolean was = false;
        for (LinkedHashMap<UUID, Long> q : queues.values()) was |= q.remove(id) != null;
        if (was) reportStatus();
        return was;
    }

    private void challenge(Player from, Player to, Kit kit) {
        if (to.equals(from)) {
            from.sendMessage(PREFIX + "§cTu ne peux pas te défier toi-même.");
            return;
        }
        if (busy(from, true)) return;
        if (busy(to, false)) {
            from.sendMessage(PREFIX + "§c" + to.getName() + " est déjà en partie.");
            return;
        }
        invites.put(to.getUniqueId(), new Invite(from.getUniqueId(), kit, System.currentTimeMillis()));
        from.sendMessage(PREFIX + "§aDéfi " + kit.label + " envoyé à " + to.getName() + " §7(30 s).");
        to.sendMessage(Component.text("[", NamedTextColor.DARK_GRAY).append(Component.text("Duels", NamedTextColor.RED)).append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(from.getName() + " te défie en " + kit.label + "  ", NamedTextColor.YELLOW))
                .append(Component.text("[ACCEPTER]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/duel accept " + from.getName())))
                .append(Component.text(" "))
                .append(Component.text("[REFUSER]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/duel deny"))));
        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
    }

    private void accept(Player p, String fromName) {
        Invite inv = invites.get(p.getUniqueId());
        Player from = inv == null ? null : Bukkit.getPlayer(inv.from);
        if (inv == null || from == null || (fromName != null && !from.getName().equalsIgnoreCase(fromName))) {
            p.sendMessage(PREFIX + "§cAucun défi en attente de ce joueur.");
            return;
        }
        invites.remove(p.getUniqueId());
        if (busy(p, true) || busy(from, false)) return;
        Arena arena = freeArena(inv.kit.arena);
        if (arena == null) {
            p.sendMessage(PREFIX + "§cToutes les arènes sont occupées, réessaie dans un instant.");
            return;
        }
        leaveQueues(p.getUniqueId());
        leaveQueues(from.getUniqueId());
        startMatch(inv.kit, false, arena, from, p);
    }

    // ------------------------------------------------------------------ duel

    private void startMatch(Kit kit, boolean ranked, Arena arena, Player a, Player b) {
        if (a == null || b == null) return;
        Match m = new Match(kit, ranked, arena, a.getUniqueId(), b.getUniqueId());
        arena.match = m;
        stopSpectating(a.getUniqueId(), false);
        stopSpectating(b.getUniqueId(), false);
        m.timer = getConfig().getInt("countdown", 5);
        matches.put(a.getUniqueId(), m);
        matches.put(b.getUniqueId(), m);
        stats.setName(a.getUniqueId(), a.getName());
        stats.setName(b.getUniqueId(), b.getName());
        Player[] ps = {a, b};
        for (int i = 0; i < 2; i++) {
            Player p = ps[i];
            Player o = ps[1 - i];
            p.teleport(arena.spawn(i));
            int side = i;
            // Un tick plus tard : Multiverse-Inventories change l'inventaire au changement de monde.
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || matches.get(p.getUniqueId()) != m) return;
                if (!p.getWorld().equals(world)) return; // kit seulement une fois arrivé dans le monde des duels
                prepare(p, GameMode.SURVIVAL);
                kit.give(p);
                p.teleport(arena.spawn(side));
                matchSidebar(p, m);
            }, 2L);
            p.sendMessage(PREFIX + "§e" + kit.label + (ranked ? " §6classée" : "") + " §7contre §f" + o.getName()
                    + (ranked ? " §7(Elo " + stats.elo(o.getUniqueId(), kit) + ")" : ""));
        }
        reportStatus();
    }

    private void tickMatch(Match m) {
        List<Player> ps = online(m);
        if (m.state == Match.State.COUNTDOWN) {
            if (m.timer > 0) {
                for (Player p : ps) {
                    p.showTitle(Title.title(Component.text(String.valueOf(m.timer), NamedTextColor.GOLD), Component.text(m.kit.label, NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
                m.timer--;
                return;
            }
            m.state = Match.State.FIGHT;
            m.timer = getConfig().getInt("max-duration", 600);
            for (Player p : ps) {
                p.showTitle(Title.title(Component.text("Combat !", NamedTextColor.RED), Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(300))));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
            }
        } else if (m.state == Match.State.FIGHT) {
            if (--m.timer <= 0) {
                endMatch(m, null, "Temps écoulé : égalité.");
                return;
            }
        }
        for (Player p : ps) matchSidebar(p, m);
    }

    /** Termine le duel. winner null = égalité ou annulation. */
    private void endMatch(Match m, UUID winner, String reason) {
        endMatch(m, winner, reason, End.NORMAL);
    }

    private void endMatch(Match m, UUID winner, String reason, End how) {
        if (m.state == Match.State.ENDED) return;
        m.state = Match.State.ENDED;
        if (how != End.CANCEL) {
            // abandon : seul le gagnant est récompensé
            for (UUID id : m.players) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || (how == End.FORFEIT && !id.equals(winner))) continue;
                eemi("reward " + p.getName() + " duels play");
                if (id.equals(winner)) eemi("reward " + p.getName() + " duels win");
                if (id.equals(winner) && how == End.KILL) eemi("reward " + p.getName() + " duels kill");
            }
        }
        reportStatus();
        UUID loser = winner == null ? null : m.opponent(winner);
        Player w = winner == null ? null : Bukkit.getPlayer(winner);
        Player l = loser == null ? null : Bukkit.getPlayer(loser);
        String eloText = "";
        if (winner != null) {
            stats.add(winner, m.kit.key() + ".wins", 1);
            stats.add(loser, m.kit.key() + ".losses", 1);
            if (m.ranked) {
                int delta = stats.applyElo(winner, loser, m.kit, getConfig().getInt("elo-k", 32));
                eloText = " §7(Elo §a+" + delta + "§7 / §c-" + delta + "§7)";
            }
            stats.save();
        }
        String winnerName = w != null ? w.getName() : winner != null ? String.valueOf(Bukkit.getOfflinePlayer(winner).getName()) : null;
        String summary = winner == null ? "§7" + reason
                : "§a" + winnerName + " §7gagne" + (w != null && !m.kit.noDamage
                ? " avec §c" + String.format("%.1f", w.getHealth() / 2) + " ❤" : "")
                + (w != null && m.kit == Kit.NODEBUFF ? " §7et §d" + healPots(w) + " potions" : "")
                + (m.kit == Kit.BOXING ? " §7(" + m.hits[0] + " - " + m.hits[1] + " coups)" : "")
                + eloText + (reason == null ? "" : " §8· " + reason);
        for (Player p : online(m)) {
            p.sendMessage(PREFIX + summary);
            boolean won = p.getUniqueId().equals(winner);
            Component endTitle = winner == null
                    ? (how == End.CANCEL ? Component.text("Duel annulé", NamedTextColor.GRAY) : Component.text("Égalité !", NamedTextColor.YELLOW))
                    : Component.text(won ? "Victoire !" : "Défaite", won ? NamedTextColor.GOLD : NamedTextColor.RED);
            Component endSub = Component.empty();
            if (winner == null && how != End.CANCEL) {
                List<String> names = new ArrayList<>();
                for (Player q : online(m)) names.add(q.getName());
                String joined = String.join(", ", names);
                endSub = Component.text(joined.length() <= 38 ? joined : names.size() + " gagnants", NamedTextColor.YELLOW);
            }
            p.showTitle(Title.title(endTitle,
                    endSub, Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            p.setInvulnerable(true);
        }
        // le perdant peut être parti du monde (abandon par /lobby, /is...) : on ne touche alors pas à son mode de jeu
        if (l != null && l.isOnline() && l.getWorld().equals(world)) l.setGameMode(GameMode.SPECTATOR);
        // 3 s de célébration, puis retour au hub et remise à zéro de l'arène.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (UUID id : m.players) {
                if (matches.get(id) == m) matches.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getWorld().equals(world)) sendToHub(p);
                if (p != null) {
                    p.setInvulnerable(false);
                    clearBoard(p);
                }
            }
            for (Block b : m.placed) b.setType(Material.AIR, false);
            m.placed.clear();
            m.arena.match = null;
            // les spectateurs de ce duel rentrent au hub
            for (UUID id : new ArrayList<>(m.spectators)) if (spectators.contains(id)) stopSpectating(id, true);
            m.spectators.clear();
        }, 60L);
    }

    private static int healPots(Player p) {
        int n = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SPLASH_POTION && item.getItemMeta() instanceof PotionMeta pm
                    && pm.getBasePotionType() == PotionType.STRONG_HEALING) n += item.getAmount();
        }
        return n;
    }

    private List<Player> online(Match m) {
        List<Player> list = new ArrayList<>();
        for (UUID id : m.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && matches.get(id) == m) list.add(p);
        }
        return list;
    }

    /** Abandon (déconnexion, /lobby, /duel leave) : l'adversaire gagne. */
    private void forfeit(Player p, String why) {
        Match m = matches.get(p.getUniqueId());
        if (m == null) return;
        if (m.state == Match.State.ENDED) {
            matches.remove(p.getUniqueId());
            return;
        }
        if (m.state == Match.State.COUNTDOWN) {
            endMatch(m, null, p.getName() + " " + why + " : duel annulé.", End.CANCEL);
            return;
        }
        endMatch(m, m.opponent(p.getUniqueId()), p.getName() + " " + why + ".", End.FORFEIT);
    }

    // ------------------------------------------------------------------ FFA

    private void joinFfa(Player p) {
        if (world == null || busy(p, true)) return;
        leaveQueues(p.getUniqueId());
        stopSpectating(p.getUniqueId(), false);
        ffaPlayers.add(p.getUniqueId());
        stats.setName(p.getUniqueId(), p.getName());
        Location spawn = ffaSpawn();
        p.teleport(spawn);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || !ffaPlayers.contains(p.getUniqueId())) return;
            if (!p.getWorld().equals(world)) return; // kit seulement une fois arrivé dans l'arène
            ffaRespawn(p);
        }, 2L);
        p.sendMessage(FFA_PREFIX + "§aArène FFA §7(fer, épée en diamant, arc, pommes d'or ; un kill te soigne). §7/ffa leave ou /lobby pour sortir.");
        reportFfa();
    }

    private void ffaRespawn(Player p) {
        if (!p.getWorld().equals(world)) return; // jamais hors du monde des duels
        prepare(p, GameMode.SURVIVAL);
        Kit.giveFfa(p);
        p.teleport(ffaSpawn());
        ffaProtection.put(p.getUniqueId(), System.currentTimeMillis() + getConfig().getInt("ffa-protection", 3) * 1000L);
        ffaSidebar(p);
    }

    private Location ffaSpawn() {
        return ffaSpawns.get(ThreadLocalRandom.current().nextInt(ffaSpawns.size())).clone();
    }

    private void leaveFfa(Player p, boolean teleport) {
        if (!ffaPlayers.remove(p.getUniqueId())) return;
        ffaStreak.remove(p.getUniqueId());
        ffaProtection.remove(p.getUniqueId());
        clearBoard(p);
        if (teleport) sendToHub(p);
        reportFfa();
    }

    private void ffaKill(Player victim, Player killer) {
        UUID v = victim.getUniqueId();
        stats.add(v, "ffa.deaths", 1);
        int lost = ffaStreak.getOrDefault(v, 0);
        ffaStreak.put(v, 0);
        String msg;
        if (killer != null && !killer.equals(victim)) {
            UUID k = killer.getUniqueId();
            stats.add(k, "ffa.kills", 1);
            int streak = ffaStreak.merge(k, 1, Integer::sum);
            if (streak > stats.get(k, "ffa.best-streak")) stats.set(k, "ffa.best-streak", streak);
            msg = "§c" + victim.getName() + " §7a été tué par §a" + killer.getName() + " §7(§c"
                    + String.format("%.1f", killer.getHealth() / 2) + " ❤§7)";
            Kit.ffaKillReward(killer);
            if (streak % 5 == 0) msg += "\n" + FFA_PREFIX + "§6" + killer.getName() + " enchaîne " + streak + " kills !";
            eemi("reward " + killer.getName() + " ffa kill");
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        } else {
            msg = "§c" + victim.getName() + " §7est mort.";
        }
        if (lost >= 5) msg += "\n" + FFA_PREFIX + "§7Série de " + lost + " kills de " + victim.getName() + " stoppée.";
        stats.save();
        for (UUID id : ffaPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(FFA_PREFIX + msg);
        }
        ffaRespawn(victim);
    }

    // ------------------------------------------------------------------ joueurs

    /** Monde du hub (config « hub-world »). */
    private boolean isHub(World w) {
        return w != null && w.getName().equals(getConfig().getString("hub-world", "lobby"));
    }

    /**
     * On ne touche à l'inventaire / l'état d'un joueur que s'il est dans le monde des duels (duels et FFA) ou au
     * hub : ailleurs (skyblock...), Multiverse-Inventories a déjà chargé l'inventaire de ce monde-là.
     */
    private boolean mayTouch(Player p) {
        return p != null && p.isOnline() && ((world != null && p.getWorld().equals(world)) || isHub(p.getWorld()));
    }

    private void prepare(Player p, GameMode mode) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setAbsorptionAmount(0);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setLevel(0);
        p.setExp(0);
        p.setVelocity(new org.bukkit.util.Vector());
    }

    /**
     * Renvoi au hub : seulement depuis le monde des duels ou le hub (jamais depuis le skyblock ou un autre jeu).
     * Téléportation d'abord (Multiverse-Inventories garde l'inventaire du monde quitté), puis remise à zéro au hub.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
        if (hub != null) p.teleport(hub.getSpawnLocation());
        prepare(p, GameMode.SURVIVAL);
    }

    // ------------------------------------------------------------------ scoreboards

    private Scoreboard board(Player p) {
        Scoreboard b = boards.get(p.getUniqueId());
        if (b == null) {
            b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = b.registerNewObjective("pvp", Criteria.DUMMY,
                    Component.text("DUELS", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            boards.put(p.getUniqueId(), b);
        }
        if (p.getScoreboard() != b) p.setScoreboard(b);
        return b;
    }

    /** Titre du tableau latéral : « DUELS » en duel, « FFA » dans l'arène FFA. */
    private void sidebarTitle(Player p, String title) {
        Objective o = board(p).getObjective("pvp");
        if (o != null) o.displayName(Component.text(title, NamedTextColor.RED).decorate(TextDecoration.BOLD));
    }

    private void clearBoard(Player p) {
        if (boards.remove(p.getUniqueId()) != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void setLines(Player p, List<String> lines) {
        Scoreboard b = board(p);
        Objective o = b.getObjective("pvp");
        for (int i = 0; i < 15; i++) {
            String entry = "§" + Integer.toHexString(i) + "§r";
            Team t = b.getTeam("line" + i);
            if (i < lines.size()) {
                if (t == null) {
                    t = b.registerNewTeam("line" + i);
                    t.addEntry(entry);
                }
                t.prefix(Component.text(lines.get(i)));
                o.getScore(entry).setScore(lines.size() - i);
            } else if (t != null) {
                b.resetScores(entry);
                t.unregister();
            }
        }
    }

    private void matchSidebar(Player p, Match m) {
        sidebarTitle(p, "DUELS");
        Player o = Bukkit.getPlayer(m.opponent(p.getUniqueId()));
        List<String> l = new ArrayList<>();
        l.add(" ");
        l.add("§7Mode : §f" + m.kit.label + (m.ranked ? " §6classée" : ""));
        l.add("§7Adversaire : §f" + (o == null ? "?" : o.getName()));
        if (o != null && !m.kit.noDamage) l.add("§7Sa vie : §c" + String.format("%.1f", o.getHealth() / 2) + " ❤");
        if (m.kit == Kit.BOXING) {
            int me = m.side(p.getUniqueId());
            l.add("§7Coups : §a" + m.hits[me] + " §7- §c" + m.hits[1 - me] + " §8/" + getConfig().getInt("boxing-hits", 100));
        }
        if (m.kit == Kit.NODEBUFF) l.add("§7Tes potions : §d" + healPots(p));
        if (m.state == Match.State.FIGHT) l.add("§7Temps : §f" + String.format("%d:%02d", m.timer / 60, m.timer % 60));
        if (m.ranked) l.add("§7Ton Elo : §6" + stats.elo(p.getUniqueId(), m.kit));
        l.add("  ");
        l.add("§8eemicraft");
        setLines(p, l);
    }

    private void ffaSidebar(Player p) {
        sidebarTitle(p, "FFA");
        UUID id = p.getUniqueId();
        List<String> l = new ArrayList<>();
        l.add(" ");
        l.add("§7Arène : §fFFA");
        l.add("§7Joueurs : §f" + ffaPlayers.size());
        l.add("  ");
        l.add("§7Série : §a" + ffaStreak.getOrDefault(id, 0));
        l.add("§7Kills : §f" + stats.get(id, "ffa.kills"));
        l.add("§7Morts : §f" + stats.get(id, "ffa.deaths"));
        l.add("§7Potions : §d" + healPots(p));
        l.add("   ");
        l.add("§8eemicraft");
        setLines(p, l);
    }

    // ------------------------------------------------------------------ menus

    private static final class Menu implements InventoryHolder {
        final String kind;
        final UUID target;
        Inventory inventory;

        Menu(String kind, UUID target) {
            this.kind = kind;
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static ItemStack named(Material m, String name, String... lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Component.text(s).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        item.setItemMeta(meta);
        return item;
    }

    private void openMain(Player p) {
        Menu holder = new Menu("main", null);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Duels"));
        holder.inventory = inv;
        inv.setItem(10, named(Material.IRON_SWORD, "§aDuel non classé", "§7Un adversaire au hasard", "§7dans le kit de ton choix."));
        inv.setItem(12, named(Material.DIAMOND_SWORD, "§6Duel classé", "§7Fais monter ton Elo.", "§7Adversaire de ton niveau."));
        inv.setItem(14, named(Material.SPLASH_POTION, "§cArène FFA", "§7Kit FFA, tout le monde", "§7contre tout le monde.", "§8" + ffaPlayers.size() + " joueur(s)"));
        inv.setItem(16, named(Material.BOOK, "§eMes statistiques", "§7Elo, victoires, kills."));
        inv.setItem(22, named(Material.PLAYER_HEAD, "§bDéfier un joueur", "§7/duel <joueur>"));
        openLater(p, inv);
    }

    /**
     * Ouvre le menu 2 ticks plus tard. Lancé depuis un autre menu (boussole DeluxeHub, ou nos propres menus),
     * un inventaire ouvert pendant le clic est refermé aussitôt : vu en 1.0.0, « /duel » partait mais
     * le menu ne s'affichait pas depuis la boussole (27/10/2026).
     */
    private void openLater(Player p, Inventory inv) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) p.openInventory(inv);
        }, 2L);
    }

    private void openKits(Player p, String kind, UUID target) {
        Menu holder = new Menu(kind, target);
        String title = switch (kind) {
            case "ranked" -> "Duel classé";
            case "challenge" -> "Défi : choisis le kit";
            default -> "Duel non classé";
        };
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text(title));
        holder.inventory = inv;
        int slot = 11;
        for (Kit k : Kit.values()) {
            ItemStack icon = k.icon();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            if (!kind.equals("challenge")) {
                LinkedHashMap<UUID, Long> q = queues.get(queueKey(k, kind.equals("ranked")));
                lore.add(Component.text((q == null ? 0 : q.size()) + " en file", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            }
            if (kind.equals("ranked")) lore.add(Component.text("Ton Elo : " + stats.elo(p.getUniqueId(), k), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot++, icon);
        }
        openLater(p, inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getRawSlot();
        if (menu.kind.equals("main")) {
            switch (slot) {
                case 10 -> openKits(p, "unranked", null);
                case 12 -> openKits(p, "ranked", null);
                case 14 -> {
                    p.closeInventory();
                    joinFfa(p);
                }
                case 16 -> {
                    p.closeInventory();
                    showStats(p, p.getUniqueId(), p.getName());
                }
                case 22 -> {
                    p.closeInventory();
                    p.sendMessage(PREFIX + "§7Tape §f/duel <joueur>§7 pour défier quelqu'un.");
                }
                default -> { }
            }
            return;
        }
        int index = slot - 11;
        if (index < 0 || index >= Kit.values().length) return;
        Kit kit = Kit.values()[index];
        p.closeInventory();
        if (menu.kind.equals("challenge")) {
            Player target = Bukkit.getPlayer(menu.target);
            if (target == null) p.sendMessage(PREFIX + "§cCe joueur n'est plus connecté.");
            else challenge(p, target, kit);
        } else {
            joinQueue(p, kit, menu.kind.equals("ranked"));
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    private void showStats(CommandSender to, UUID id, String name) {
        to.sendMessage(PREFIX + "§eStatistiques de " + name);
        for (Kit k : Kit.values()) {
            int w = stats.get(id, k.key() + ".wins"), l = stats.get(id, k.key() + ".losses");
            to.sendMessage("§7 " + k.label + " : §a" + w + " V §7/ §c" + l + " D §7· Elo §6" + stats.elo(id, k));
        }
        to.sendMessage("§7 FFA : §a" + stats.get(id, "ffa.kills") + " kills §7/ §c" + stats.get(id, "ffa.deaths")
                + " morts §7· meilleure série §6" + stats.get(id, "ffa.best-streak"));
    }

    // ------------------------------------------------------------------ dégâts et morts

    /** Après EemiCombat (HIGH) : Sumo/Boxing sans dégâts, protection FFA, coup mortel transformé en élimination. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || world == null || !p.getWorld().equals(world)) return;
        Match m = matches.get(p.getUniqueId());
        Player attacker = attacker(e);
        if (m != null) {
            if (m.state != Match.State.FIGHT) {
                e.setCancelled(true);
                return;
            }
            if (attacker != null && !attacker.getUniqueId().equals(m.opponent(p.getUniqueId()))) {
                e.setCancelled(true);
                return;
            }
            if (m.kit.noDamage) {
                if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return;
                if (m.kit == Kit.BOXING && attacker != null && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                    int side = m.side(attacker.getUniqueId());
                    m.hits[side]++;
                    if (m.hits[side] >= getConfig().getInt("boxing-hits", 100)) {
                        e.setDamage(0);
                        endMatch(m, attacker.getUniqueId(), null);
                        return;
                    }
                }
                e.setDamage(0);
                return;
            }
            if (e.getFinalDamage() >= p.getHealth()) {
                e.setCancelled(true);
                endMatch(m, m.opponent(p.getUniqueId()), null, End.KILL);
            }
            return;
        }
        if (ffaPlayers.contains(p.getUniqueId())) {
            Long until = ffaProtection.get(p.getUniqueId());
            if (until != null && until > System.currentTimeMillis() && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
                e.setCancelled(true);
                return;
            }
            if (attacker != null) ffaProtection.remove(attacker.getUniqueId());
            if (e.getFinalDamage() >= p.getHealth()) {
                e.setCancelled(true);
                Player killer = attacker != null ? attacker : (p.getKiller());
                ffaKill(p, killer);
            }
            return;
        }
        // Dans le monde des duels sans partie : aucun dégât.
        e.setCancelled(true);
    }

    private static Player attacker(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent by)) return null;
        Entity d = by.getDamager();
        if (d instanceof Player p) return p;
        if (d instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (world == null || !p.getWorld().equals(world)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        Match m = matches.get(p.getUniqueId());
        if (m != null && m.state != Match.State.ENDED) endMatch(m, m.opponent(p.getUniqueId()), null, End.KILL);
        if (ffaPlayers.contains(p.getUniqueId())) {
            stats.add(p.getUniqueId(), "ffa.deaths", 1);
            ffaStreak.put(p.getUniqueId(), 0);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (ffaPlayers.contains(p.getUniqueId())) {
            e.setRespawnLocation(ffaSpawn());
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && ffaPlayers.contains(p.getUniqueId()) && p.getWorld().equals(world)) ffaRespawn(p);
            }, 2L);
        } else if (matches.containsKey(p.getUniqueId())) {
            e.setRespawnLocation(matches.get(p.getUniqueId()).arena.center());
        }
    }

    /** Chute : hors de la plateforme en Sumo, dans le vide ailleurs. Hors des murs : renvoyé à son départ. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Match m = matches.get(p.getUniqueId());
        Location to = e.getTo();
        if (m != null) {
            if (m.state == Match.State.COUNTDOWN && (to.getX() != e.getFrom().getX() || to.getZ() != e.getFrom().getZ())) {
                Location back = e.getFrom().clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                e.setTo(back);
                return;
            }
            if (m.state == Match.State.FIGHT && to.getY() < m.arena.fallY() && p.getGameMode() == GameMode.SURVIVAL) {
                endMatch(m, m.opponent(p.getUniqueId()), p.getName() + " est tombé", End.KILL);
                return;
            }
            if (m.state != Match.State.ENDED && !m.arena.contains(to)) e.setTo(m.arena.spawn(Math.max(0, m.side(p.getUniqueId()))));
            return;
        }
        if (ffaPlayers.contains(p.getUniqueId()) && (to.getY() < ffa.fallY() || !ffa.contains(to))) {
            ffaKill(p, p.getKiller());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Match m = matches.get(p.getUniqueId());
        // UHC : pas de régénération naturelle (seulement les pommes d'or et les potions).
        if (m != null && m.kit == Kit.BUILDUHC && (e.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || e.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Match m = matches.get(p.getUniqueId());
        if (m != null && !m.kit.hunger && e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ blocs et objets

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        if (world == null || !b.getWorld().equals(world)) return;
        if (admin(e.getPlayer())) return;
        Match m = matches.get(e.getPlayer().getUniqueId());
        if (m == null || !m.kit.building || m.state != Match.State.FIGHT || !inside(m.arena, b) || b.getY() <= Arena.FLOOR) {
            e.setCancelled(true);
            return;
        }
        m.placed.add(b);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (world == null || !b.getWorld().equals(world)) return;
        if (admin(e.getPlayer())) return;
        Match m = matches.get(e.getPlayer().getUniqueId());
        if (m == null || !m.placed.contains(b)) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        m.placed.remove(b);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Block b = e.getBlock();
        if (world == null || !b.getWorld().equals(world)) return;
        Match m = matches.get(e.getPlayer().getUniqueId());
        if (m == null || !m.kit.building || m.state != Match.State.FIGHT || !inside(m.arena, b) || b.getY() <= Arena.FLOOR) {
            if (!admin(e.getPlayer())) e.setCancelled(true);
            return;
        }
        m.placed.add(b);
    }

    /** Eau et lave qui coulent, pierre ou obsidienne formées : retirées à la fin du duel. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (world == null || !to.getWorld().equals(world)) return;
        Arena a = arenaAt(to);
        if (a == null || a.match == null || !a.match.kit.building || to.getY() <= Arena.FLOOR) {
            e.setCancelled(true);
            return;
        }
        a.match.placed.add(to);
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        Block b = e.getBlock();
        if (world == null || !b.getWorld().equals(world)) return;
        Arena a = arenaAt(b);
        if (a == null || a.match == null || b.getY() <= Arena.FLOOR) {
            e.setCancelled(true);
            return;
        }
        a.match.placed.add(b);
    }

    private Arena arenaAt(Block b) {
        for (Arena a : duelArenas) if (inside(a, b)) return a;
        for (Arena a : sumoArenas) if (inside(a, b)) return a;
        return null;
    }

    /** Intérieur de l'arène (murs exclus), sous le plafond. */
    private static boolean inside(Arena a, Block b) {
        int r = a.radius();
        return Math.abs(b.getX() - a.cx) <= r && Math.abs(b.getZ() - a.cz) <= r && b.getY() <= Arena.FLOOR + Arena.WALL_HEIGHT;
    }

    private static boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemiduels.admin");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (world != null && e.getPlayer().getWorld().equals(world) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (world != null && e.getLocation().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (world != null && e.getLocation().getWorld().equals(world)
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ connexions

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Déconnecté en plein duel ou en FFA : on ne reste pas coincé dans le monde des duels.
        if (world != null && p.getWorld().equals(world)) Bukkit.getScheduler().runTaskLater(this, () -> sendToHub(p), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        leaveQueues(p.getUniqueId());
        invites.remove(p.getUniqueId());
        forfeit(p, "s'est déconnecté");
        leaveFfa(p, false);
        boards.remove(p.getUniqueId());
        // spectateur : il sera renvoyé au hub à la reconnexion (onJoin)
        stopSpectating(p.getUniqueId(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (world == null || !e.getFrom().equals(world)) return;
        Player p = e.getPlayer();
        forfeit(p, "a quitté l'arène");
        matches.remove(p.getUniqueId());
        leaveFfa(p, false);
        clearBoard(p);
        if (spectators.contains(p.getUniqueId()) || ffaSpectators.contains(p.getUniqueId())) {
            stopSpectating(p.getUniqueId(), false);
            // seulement au hub : dans un autre monde (skyblock...), on ne touche pas au mode de jeu
            if (isHub(p.getWorld())) p.setGameMode(GameMode.SURVIVAL);
        }
    }

    // ------------------------------------------------------------------ EemiCore et spectateurs

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /** Statut des duels : en attente = joueurs dans les files, en jeu = joueurs en duel (toutes arènes). */
    private void reportStatus() {
        int waiting = 0;
        for (LinkedHashMap<UUID, Long> q : queues.values()) waiting += q.size();
        int playing = 0;
        for (Match m : new HashSet<>(matches.values())) if (m.state != Match.State.ENDED) playing += 2;
        int max = (duelArenas.size() + sumoArenas.size()) * 2;
        eemi("status duels " + (playing > 0 ? "playing" : "waiting") + " " + waiting + " " + playing + " " + max);
    }

    /** Statut de l'arène FFA : en jeu = joueurs présents. */
    private void reportFfa() {
        int n = ffaPlayers.size();
        eemi("status ffa " + (n > 0 ? "playing" : "waiting") + " 0 " + n + " " + Bukkit.getMaxPlayers());
    }

    /** /duel spectate [joueur] : regarder le duel de ce joueur, sinon un duel en cours tiré au hasard. */
    private void spectateDuel(Player p, String targetName) {
        if (busy(p, true)) return;
        if (queues.values().stream().anyMatch(q -> q.containsKey(p.getUniqueId()))) {
            p.sendMessage(PREFIX + "§cTu es en file : /duel leave d'abord.");
            return;
        }
        List<Match> running = new ArrayList<>();
        for (Match m : new HashSet<>(matches.values())) if (m.state != Match.State.ENDED) running.add(m);
        if (running.isEmpty()) {
            p.sendMessage(PREFIX + "§cAucun duel en cours.");
            return;
        }
        Match m;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            m = t == null ? null : matches.get(t.getUniqueId());
            if (m == null || m.state == Match.State.ENDED) {
                p.sendMessage(PREFIX + "§c" + targetName + " n'est pas en duel.");
                return;
            }
        } else m = running.get(ThreadLocalRandom.current().nextInt(running.size()));
        stopSpectating(p.getUniqueId(), false);
        // au-dessus du centre de l'arène, sous le plafond de barrières, regard vers le bas
        Location view = m.arena.center().add(0, 12, 0);
        view.setPitch(60f);
        p.teleport(view);
        spectators.add(p.getUniqueId());
        m.spectators.add(p.getUniqueId());
        becomeSpectator(p);
        Player a = Bukkit.getPlayer(m.players[0]), b = Bukkit.getPlayer(m.players[1]);
        p.sendMessage(PREFIX + "§7Duel " + m.kit.label + " : §f" + (a == null ? "?" : a.getName()) + " §7contre §f" + (b == null ? "?" : b.getName()) + "§7.");
    }

    /** /ffa spectate : regarder l'arène FFA s'il y a du monde. */
    private void spectateFfa(Player p) {
        if (busy(p, true)) return;
        if (ffaPlayers.isEmpty() || ffa == null) {
            p.sendMessage(FFA_PREFIX + "§cPersonne dans l'arène FFA.");
            return;
        }
        stopSpectating(p.getUniqueId(), false);
        Location view = ffa.center().add(0, 15, 0);
        view.setPitch(60f);
        p.teleport(view);
        ffaSpectators.add(p.getUniqueId());
        becomeSpectator(p);
    }

    private void becomeSpectator(Player p) {
        if (p.getWorld().equals(world)) p.setGameMode(GameMode.SPECTATOR); // seulement une fois arrivé
        clearBoard(p);
        // après Multiverse (mode de jeu imposé au changement de monde)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && p.getWorld().equals(world) && (spectators.contains(p.getUniqueId()) || ffaSpectators.contains(p.getUniqueId())))
                p.setGameMode(GameMode.SPECTATOR);
        }, 3L);
        p.sendMessage(PREFIX + "§7Tu regardes la partie, /lobby pour revenir.");
    }

    /** Retire le joueur des spectateurs (duels et FFA) ; toHub = le renvoyer au hub. */
    private void stopSpectating(UUID id, boolean toHub) {
        boolean was = spectators.remove(id) | ffaSpectators.remove(id);
        for (Match m : matches.values()) m.spectators.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (was && toHub && p != null) sendToHub(p);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ffa")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
                if (ffaPlayers.contains(p.getUniqueId())) leaveFfa(p, true);
                else p.sendMessage(FFA_PREFIX + "§7Tu n'es pas dans le FFA.");
            } else if (args.length > 0 && (args[0].equalsIgnoreCase("spectate") || args[0].equalsIgnoreCase("spec"))) {
                spectateFfa(p);
            } else {
                joinFfa(p);
            }
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        // Console (menus DeluxeHub) : /duel open <joueur>
        if (sub.equals("open") && args.length > 1 && sender.hasPermission("eemiduels.admin")) {
            Player t = Bukkit.getPlayer(args[1]);
            if (t != null) openMain(t);
            return true;
        }
        if (sub.equals("build") && sender.hasPermission("eemiduels.admin")) {
            if (matches.isEmpty()) {
                buildArenas();
                sender.sendMessage(PREFIX + "§aArènes reconstruites.");
            } else {
                sender.sendMessage(PREFIX + "§cDes duels sont en cours, réessaie plus tard.");
            }
            return true;
        }
        if (sub.equals("reload") && sender.hasPermission("eemiduels.admin")) {
            reloadConfig();
            sender.sendMessage(PREFIX + "§aConfig rechargée (les nombres d'arènes comptent au prochain démarrage).");
            return true;
        }
        if (sub.equals("top")) {
            Kit kit = args.length > 1 ? Kit.byName(args[1]) : null;
            if (args.length > 1 && args[1].equalsIgnoreCase("ffa")) {
                sender.sendMessage(PREFIX + "§eTop FFA (kills)");
                int i = 1;
                for (Stats.Entry en : stats.top("ffa.kills", 0, 10)) sender.sendMessage("§7" + i++ + ". §f" + en.name() + " §7- §a" + en.value());
                return true;
            }
            if (kit == null) {
                sender.sendMessage(PREFIX + "§7/duel top <" + kitList() + "|ffa>");
                return true;
            }
            sender.sendMessage(PREFIX + "§eTop Elo " + kit.label);
            int i = 1;
            for (Stats.Entry en : stats.top(kit.key() + ".elo", getConfig().getInt("start-elo", 1000), 10))
                sender.sendMessage("§7" + i++ + ". §f" + en.name() + " §7- §6" + en.value());
            return true;
        }
        if (sub.equals("stats")) {
            if (args.length > 1) {
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                showStats(sender, t.getUniqueId(), args[1]);
            } else if (sender instanceof Player p) {
                showStats(sender, p.getUniqueId(), p.getName());
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs (sauf open, build, reload, top, stats).");
            return true;
        }
        switch (sub) {
            case "" -> openMain(p);
            case "accept" -> accept(p, args.length > 1 ? args[1] : null);
            case "spectate", "spec" -> spectateDuel(p, args.length > 1 ? args[1] : null);
            case "deny" -> {
                Invite inv = invites.remove(p.getUniqueId());
                Player from = inv == null ? null : Bukkit.getPlayer(inv.from);
                if (from != null) from.sendMessage(PREFIX + "§c" + p.getName() + " a refusé ton défi.");
                p.sendMessage(PREFIX + "§7Défi refusé.");
            }
            case "queue" -> {
                Kit kit = args.length > 1 ? Kit.byName(args[1]) : null;
                if (kit == null) p.sendMessage(PREFIX + "§7/duel queue <" + kitList() + "> [ranked]");
                else joinQueue(p, kit, args.length > 2 && args[2].equalsIgnoreCase("ranked"));
            }
            case "leave" -> {
                if (leaveQueues(p.getUniqueId())) p.sendMessage(PREFIX + "§7File quittée.");
                else if (matches.containsKey(p.getUniqueId())) {
                    forfeit(p, "a abandonné");
                } else p.sendMessage(PREFIX + "§7Tu n'es dans aucune file.");
            }
            default -> {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    p.sendMessage(PREFIX + "§cJoueur introuvable. §7/duel, /duel <joueur> [kit], /duel queue <kit> [ranked], /duel stats, /duel top <kit>");
                    return true;
                }
                Kit kit = args.length > 1 ? Kit.byName(args[1]) : null;
                if (kit == null) openKits(p, "challenge", target.getUniqueId());
                else challenge(p, target, kit);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("ffa")) {
            if (args.length == 1) out.addAll(List.of("join", "leave", "spectate"));
        } else if (args.length == 1) {
            out.addAll(List.of("queue", "leave", "accept", "deny", "stats", "top", "spectate"));
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("queue") || args[0].equalsIgnoreCase("top") || Bukkit.getPlayer(args[0]) != null)) {
            for (Kit k : Kit.values()) out.add(k.label);
            if (args[0].equalsIgnoreCase("top")) out.add("ffa");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("queue")) {
            out.add("ranked");
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }

    private static String kitList() {
        StringBuilder sb = new StringBuilder();
        for (Kit k : Kit.values()) sb.append(sb.length() == 0 ? "" : "|").append(k.label);
        return sb.toString();
    }
}
