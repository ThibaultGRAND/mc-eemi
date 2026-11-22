package fr.eemicraft.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coeur d'EEMICRAFT :
 * - registre des jeux et leur statut (reçu des jeux maison par « eemi status », déduit des mondes pour les jeux tiers) ;
 * - appels à la partie au hub, point d'entrée unique « /eemi join <jeu> », « /jouer » ;
 * - groupes (/party) ;
 * - progression (pièces, xp, niveaux, stats par jeu) alimentée par « eemi reward » ;
 * - boutique de cosmétiques (traînées, chapeaux, titres, messages d'arrivée), quêtes du jour, classements ;
 * - placeholders PlaceholderAPI %eemi_...% (si PlaceholderAPI est présent).
 */
public final class EemiCore extends JavaPlugin implements Listener {

    private static final String PREFIX = "&6[EEMICRAFT] &r";
    private static final String PARTY = "&9[Groupe] &r";
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final int PARTY_MAX = 8;
    private static final long INVITE_MS = 60_000L;
    private static final long OFFLINE_KICK_MS = 5 * 60_000L;
    private static final String[] STAT_TYPES = {"wins", "kills", "plays"};

    // registre
    private final Map<String, GameInfo> games = new LinkedHashMap<>();
    private long expireMs = 10 * 60_000L;
    private String lobbyWorld = "lobby";

    // maintenance par jeu (maintenance.yml) : jeu → raison, auteur, depuis (ms)
    private record Maintenance(String reason, String by, long since) {}
    private final Map<String, Maintenance> maintenance = new LinkedHashMap<>();

    // fichier de statut lu par le bot Discord et le site (status.json)
    private record GameSnapshot(String id, String name, Maintenance maintenance, String state, int waiting, int playing, int max) {}
    private final Object statusFileLock = new Object();
    private long statusFileSeq, statusFileWritten;
    private BukkitTask statusFileTask;

    // joueurs
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<String, UUID> nameIndex = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private File playersFolder;

    // appels et entrées dans les jeux
    private record JoinMark(String game, long time) {}

    /** Dernière commande de join tapée par un chef de groupe (« bw join lagoon »...), rejouée par les membres. */
    private record LeaderCmd(String game, String cmd, long time) {}

    private final Map<UUID, LeaderCmd> leaderCmd = new HashMap<>();
    private final Map<UUID, JoinMark> lastJoin = new HashMap<>();
    private int callTimer;

    // groupes
    private static final class Party {
        UUID leader;
        final List<UUID> members = new ArrayList<>(); // chef compris, ordre d'arrivée
    }
    private record Invite(UUID from, long time) {}
    private final Map<UUID, Party> partyOf = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, Long> offlineSince = new HashMap<>();

    // classements : « jeu:type » → lignes (pseudo, valeur)
    private record TopEntry(String name, long value) {}
    private volatile Map<String, List<TopEntry>> tops = new HashMap<>();

    // cosmétiques
    private NamespacedKey hatKey, gadgetKey, tagKey;
    private final Map<UUID, Location> lastTrailLoc = new HashMap<>();
    private float rainbowHue;
    private final TreeMap<Integer, String> levelColors = new TreeMap<>();

    private String lastDay = LocalDate.now().toString();

    // ------------------------------------------------------------------ démarrage

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hatKey = new NamespacedKey(this, "hat");
        gadgetKey = new NamespacedKey(this, "gadget");
        tagKey = new NamespacedKey(this, "tag");
        // titres orphelins d'un arrêt brutal
        for (World w : Bukkit.getWorlds())
            for (org.bukkit.entity.TextDisplay td : w.getEntitiesByClass(org.bukkit.entity.TextDisplay.class))
                if (td.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) td.remove();
        playersFolder = new File(getDataFolder(), "players");
        if (!playersFolder.exists() && !playersFolder.mkdirs()) getLogger().warning("Impossible de creer " + playersFolder);
        loadSettings();
        loadMaintenance();
        loadAllPlayers();
        getServer().getPluginManager().registerEvents(this, this);
        for (String name : List.of("eemi", "party", "pc", "jouer", "profil", "boutique", "quetes", "spectate")) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) onJoinData(p);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new CoreExpansion(this).register();
                getLogger().info("Placeholders %eemi_...% enregistres.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI present mais expansion impossible : " + t);
            }
        } else {
            getLogger().info("PlaceholderAPI absent : pas de placeholders.");
        }
        recomputeTops();
        getServer().getScheduler().runTaskTimer(this, this::second, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::trails, 3L, 3L);
        getServer().getScheduler().runTaskTimer(this, this::hatsCheck, 40L, 40L);
        getServer().getScheduler().runTaskTimer(this, this::gadgetsCheck, 45L, 40L);
        getServer().getScheduler().runTaskTimer(this, this::titleTagsTick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, this::noFlightCheck, 10L, 10L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) updateTitleTag(p);
        }, 20L, 20L);
        long save = Math.max(10, getConfig().getInt("save-interval", 60)) * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> saveDirty(true), save, save);
        startStatusFile();
        // filet de sécurité : objets de l'Abysse / des jeux retirés au lobby (tickets #50, #53)
        new LobbyGuard(this).start();
        getLogger().info(games.size() + " jeux, " + players.size() + " joueurs connus, " + maintenance.size() + " en maintenance.");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeHat(p);
            removeGadget(p);
            removeTitleTag(p);
        }
        saveDirty(false);
    }

    private void loadSettings() {
        reloadConfig();
        lobbyWorld = getConfig().getString("lobby-world", "lobby");
        expireMs = Math.max(1, getConfig().getInt("status-expire-minutes", 10)) * 60_000L;
        ConfigurationSection gs = getConfig().getConfigurationSection("games");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(id);
                if (s == null) continue;
                GameInfo g = games.computeIfAbsent(id.toLowerCase(Locale.ROOT), GameInfo::new);
                g.name = s.getString("name", id);
                g.join = s.getString("join", "");
                g.joinAliases = new ArrayList<>(s.getStringList("join-aliases"));
                g.spectate = s.getString("spectate", "");
                g.worlds = new ArrayList<>(s.getStringList("worlds"));
                g.min = Math.max(1, s.getInt("min", 2));
                g.max = Math.max(g.min, s.getInt("max", 16));
                g.multiplier = s.getDouble("multiplier", 1.0);
                Material m = Material.matchMaterial(s.getString("icon", "PAPER"));
                g.icon = m == null || !m.isItem() ? Material.PAPER : m;
            }
        }
        levelColors.clear();
        ConfigurationSection lc = getConfig().getConfigurationSection("chat.level-colors");
        if (lc != null) for (String k : lc.getKeys(false)) {
            try {
                levelColors.put(Integer.parseInt(k), lc.getString(k, "&7"));
            } catch (NumberFormatException ignored) {
            }
        }
        if (levelColors.isEmpty()) levelColors.put(1, "&7");
    }

    private void loadAllPlayers() {
        File[] files = playersFolder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            try {
                UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                PlayerData d = PlayerData.load(f, id);
                players.put(id, d);
                nameIndex.put(d.name.toLowerCase(Locale.ROOT), id);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Fichier joueur ignore : " + f.getName());
            }
        }
    }

    /** Écrit les joueurs modifiés. YAML construit sur le thread principal, fichiers écrits en asynchrone si async. */
    private void saveDirty(boolean async) {
        if (dirty.isEmpty()) return;
        Map<File, String> out = new HashMap<>();
        for (UUID id : dirty) {
            PlayerData d = players.get(id);
            if (d != null) out.put(new File(playersFolder, id + ".yml"), d.serialize());
        }
        dirty.clear();
        Runnable write = () -> {
            for (Map.Entry<File, String> e : out.entrySet()) {
                try {
                    Files.writeString(e.getKey().toPath(), e.getValue(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    getLogger().warning("Sauvegarde " + e.getKey().getName() + " : " + ex.getMessage());
                }
            }
        };
        if (async) getServer().getScheduler().runTaskAsynchronously(this, write);
        else write.run();
    }

    private PlayerData data(Player p) {
        PlayerData d = players.computeIfAbsent(p.getUniqueId(), PlayerData::new);
        if (!p.getName().equals(d.name)) {
            nameIndex.remove(d.name.toLowerCase(Locale.ROOT));
            d.name = p.getName();
            dirty.add(d.uuid);
        }
        nameIndex.put(d.name.toLowerCase(Locale.ROOT), d.uuid);
        return d;
    }

    /** Données d'un joueur connecté ou connu par son pseudo (null si jamais vu). */
    private PlayerData dataByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return data(online);
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : players.get(id);
    }

    private void markDirty(PlayerData d) {
        dirty.add(d.uuid);
    }

    // ------------------------------------------------------------------ utilitaires

    static Component c(String legacy) {
        return AMP.deserialize(legacy == null ? "" : legacy).decoration(TextDecoration.ITALIC, false);
    }

    static String sec(String legacy) {
        return SECTION.serialize(AMP.deserialize(legacy == null ? "" : legacy));
    }

    private static void msg(CommandSender to, String legacy) {
        to.sendMessage(c(legacy));
    }

    private static Component button(String text, NamedTextColor color, String command, String hover) {
        return Component.text(text, color, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private boolean inLobby(Player p) {
        return p.getWorld().getName().equalsIgnoreCase(lobbyWorld);
    }

    private GameInfo game(String id) {
        return id == null ? null : games.get(id.toLowerCase(Locale.ROOT));
    }

    /** Jeu inconnu (status/reward d'un jeu pas dans la config) : créé à la volée, nom = id. */
    private GameInfo gameOrCreate(String id) {
        return games.computeIfAbsent(id.toLowerCase(Locale.ROOT), GameInfo::new);
    }

    private GameInfo gameForWorld(String world) {
        for (GameInfo g : games.values()) if (g.hasWorld(world)) return g;
        return null;
    }

    private long xpNeeded(int level) {
        return getConfig().getLong("levels.base", 100) + getConfig().getLong("levels.step", 50) * (level - 1L);
    }

    private String levelColor(int level) {
        Map.Entry<Integer, String> e = levelColors.floorEntry(level);
        return e == null ? "&7" : e.getValue();
    }

    private ConfigurationSection cosmetic(String id) {
        return id == null ? null : getConfig().getConfigurationSection("cosmetics." + id);
    }

    private String equippedTitle(PlayerData d) {
        ConfigurationSection s = cosmetic(d.equipped.get("title"));
        return s == null ? "" : s.getString("text", "");
    }

    private static String bar(long value, long max, int width) {
        int filled = max <= 0 ? width : (int) Math.min(width, value * width / max);
        return "&a" + "|".repeat(filled) + "&8" + "|".repeat(width - filled);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ------------------------------------------------------------------ boucle (1 s)

    private void second() {
        long now = System.currentTimeMillis();
        invites.values().removeIf(i -> now - i.time > INVITE_MS);
        lastJoin.values().removeIf(j -> now - j.time > 15 * 60_000L);
        // déconnectés depuis plus de 5 min : retirés de leur groupe
        for (Map.Entry<UUID, Long> e : new ArrayList<>(offlineSince.entrySet())) {
            if (now - e.getValue() < OFFLINE_KICK_MS) continue;
            offlineSince.remove(e.getKey());
            Party party = partyOf.get(e.getKey());
            if (party != null) {
                PlayerData d = players.get(e.getKey());
                removeFromParty(party, e.getKey(), "&7" + (d == null ? "?" : d.name) + " a ete retire du groupe (deconnecte).");
            }
        }
        // appels à la partie
        if (++callTimer >= Math.max(10, getConfig().getInt("call-interval", 60))) {
            callTimer = 0;
            for (GameInfo g : games.values()) {
                if (g.fresh(expireMs) && "waiting".equals(g.state) && g.waiting >= 1 && g.waiting < g.min) {
                    int missing = g.min - g.waiting;
                    sendCall(g, "&eIl manque &c" + missing + " joueur" + (missing > 1 ? "s" : "") + " &e! ");
                }
            }
        }
        // minuit : nouvelles quêtes
        String today = LocalDate.now().toString();
        if (!today.equals(lastDay)) {
            lastDay = today;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (ensureQuests(data(p))) msg(p, PREFIX + "&eNouvelles quetes du jour ! &7/quetes");
            }
        }
        if (now / 1000 % 30 == 0) recomputeTops();
    }

    /** Appel à la partie aux joueurs du hub qui n'ont pas coupé les appels ni déjà rejoint ce jeu. */
    private void sendCall(GameInfo g, String text) {
        if (maintenance.containsKey(g.id)) return;
        Component line = c("&8[" + g.name + "&8] " + text)
                .append(button("[REJOINDRE]", NamedTextColor.GREEN, "/eemi join " + g.id, "Clique pour rejoindre la partie"));
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!inLobby(p) || !data(p).calls) continue;
            JoinMark j = lastJoin.get(p.getUniqueId());
            if (j != null && j.game.equals(g.id) && now - j.time < 10 * 60_000L) continue;
            p.sendMessage(line);
        }
    }

    // ------------------------------------------------------------------ statut des jeux

    String statusText(GameInfo g) {
        ConfigurationSection t = getConfig().getConfigurationSection("status-text");
        String text;
        if (maintenance.containsKey(g.id)) {
            text = t == null ? "&8Maintenance" : t.getString("maintenance", "&8Maintenance");
        } else if (g.fresh(expireMs)) {
            switch (g.state) {
                case "starting" -> text = t == null ? "&eDemarrage..." : t.getString("starting", "&eDemarrage...");
                case "playing" -> text = t == null ? "&cEn partie (%playing%)" : t.getString("playing", "&cEn partie (%playing%)");
                case "ending" -> text = t == null ? "&6Fin de partie" : t.getString("ending", "&6Fin de partie");
                default -> {
                    if (g.waiting > 0) text = t == null ? "&a%waiting%/%max% en attente" : t.getString("waiting", "&a%waiting%/%max% en attente");
                    else if (g.playing > 0) text = t == null ? "&cEn partie (%playing%)" : t.getString("playing", "&cEn partie (%playing%)");
                    else text = t == null ? "&7Personne" : t.getString("nobody", "&7Personne");
                }
            }
        } else {
            int n = g.worldPlayers();
            if (n == 0) text = t == null ? "&7Personne" : t.getString("nobody", "&7Personne");
            else if (n == 1) text = t == null ? "&71 joueur" : t.getString("deduced-one", "&71 joueur");
            else text = t == null ? "&7%players% joueurs" : t.getString("deduced", "&7%players% joueurs");
        }
        return text.replace("%waiting%", String.valueOf(g.waiting))
                .replace("%playing%", String.valueOf(g.playing))
                .replace("%max%", String.valueOf(g.max))
                .replace("%players%", String.valueOf(playerCount(g)));
    }

    int playerCount(GameInfo g) {
        return g.fresh(expireMs) ? g.waiting + g.playing : g.worldPlayers();
    }

    private void updateStatus(String id, String state, int waiting, int playing, int max) {
        GameInfo g = gameOrCreate(id);
        String old = g.fresh(expireMs) ? g.state : null;
        g.state = state;
        g.waiting = Math.max(0, waiting);
        g.playing = Math.max(0, playing);
        if (max > 0) g.max = max;
        g.lastUpdate = System.currentTimeMillis();
        if ("starting".equals(state) && !"starting".equals(old)) sendCall(g, "&eLa partie de " + g.name + " &edemarre ! ");
    }

    // ------------------------------------------------------------------ entrer dans un jeu

    /** Point d'entrée unique : tient compte du groupe. */
    private void joinGame(Player p, GameInfo g) {
        if (blockedByMaintenance(p, g)) return;
        Party party = partyOf.get(p.getUniqueId());
        if (party != null && party.members.size() > 1) {
            if (!party.leader.equals(p.getUniqueId())) {
                msg(p, PARTY + "&cSeul le chef du groupe lance une partie.");
                return;
            }
            boolean menu = isMenuJoin(g);
            for (UUID id : party.members) {
                if (id.equals(p.getUniqueId())) continue;
                Player m = Bukkit.getPlayer(id);
                if (m == null) continue;
                if (menu) {
                    // BedWars, UHC... : le chef choisit le mode, le groupe le suit quand il entre dans la partie
                    msg(m, PARTY + "&7" + p.getName() + " choisit une partie de " + g.name + "&7, tu le suivras automatiquement.");
                    continue;
                }
                msg(m, PARTY + "&7" + p.getName() + " emmene le groupe en " + g.name + "&7.");
                runJoin(m, g);
            }
        }
        runJoin(p, g);
    }

    /** Exécute la commande « join » du jeu pour ce joueur (1 tick plus tard : on sort d'un éventuel menu). */
    private void runJoin(Player p, GameInfo g) {
        if (g.join == null || g.join.isBlank()) {
            msg(p, PREFIX + "&cCe jeu n'a pas de commande pour le rejoindre.");
            return;
        }
        if (blockedByMaintenance(p, g)) return;
        lastJoin.put(p.getUniqueId(), new JoinMark(g.id, System.currentTimeMillis()));
        String cmd = g.join.trim();
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            if (cmd.toLowerCase(Locale.ROOT).startsWith("console:")) {
                String c = cmd.substring(8).trim().replace("%player%", p.getName());
                if (c.startsWith("/")) c = c.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            } else {
                p.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd.replace("%player%", p.getName()));
            }
        });
    }

    private void spectate(Player p, GameInfo g) {
        if (g.spectate == null || g.spectate.isBlank()) {
            msg(p, PREFIX + "&cPas de mode spectateur pour " + g.name + "&c.");
            return;
        }
        String cmd = g.spectate.trim();
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline()) p.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd.replace("%player%", p.getName()));
        });
    }

    /** /jouer : le jeu avec le plus de joueurs en attente (pas plein), sinon un jeu au hasard. */
    private void quickPlay(Player p) {
        GameInfo best = null;
        for (GameInfo g : games.values()) {
            if (maintenance.containsKey(g.id)) continue;
            if (!g.fresh(expireMs) || !"waiting".equals(g.state) && !"starting".equals(g.state)) continue;
            if (g.waiting <= 0 || g.waiting >= g.max) continue;
            if (best == null || g.waiting > best.waiting) best = g;
        }
        if (best == null) {
            List<GameInfo> pool = new ArrayList<>();
            for (String id : getConfig().getStringList("random-games")) {
                GameInfo g = game(id);
                if (g != null && g.join != null && !g.join.isBlank() && !maintenance.containsKey(g.id)) pool.add(g);
            }
            if (pool.isEmpty()) {
                msg(p, PREFIX + "&cAucun jeu disponible.");
                return;
            }
            best = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        msg(p, PREFIX + "&7Direction " + best.name + "&7 !");
        joinGame(p, best);
    }

    // ------------------------------------------------------------------ maintenance

    private File maintenanceFile() {
        return new File(getDataFolder(), "maintenance.yml");
    }

    /** Relu au démarrage : un jeu en maintenance le reste après un redémarrage. */
    private void loadMaintenance() {
        maintenance.clear();
        File f = maintenanceFile();
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            String reason = s.getString("reason", "");
            maintenance.put(id.toLowerCase(Locale.ROOT), new Maintenance(reason == null ? "" : reason,
                    s.getString("by", "?"), s.getLong("since", System.currentTimeMillis())));
        }
    }

    /** YAML construit sur le thread principal, écrit en asynchrone. */
    private void saveMaintenance() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Maintenance> e : maintenance.entrySet()) {
            y.set(e.getKey() + ".reason", e.getValue().reason());
            y.set(e.getKey() + ".by", e.getValue().by());
            y.set(e.getKey() + ".since", e.getValue().since());
        }
        String text = y.saveToString();
        File f = maintenanceFile();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            synchronized (maintenance) {
                try {
                    Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    getLogger().warning("Sauvegarde maintenance.yml : " + ex.getMessage());
                }
            }
        });
    }

    private String maintenanceMessage(GameInfo g) {
        Maintenance m = maintenance.get(g.id);
        String reason = m == null || m.reason().isBlank() ? "" : "&7 : " + m.reason();
        return PREFIX + g.name + " &cest en maintenance" + reason;
    }

    /** Vrai (et message au joueur) si le jeu est en maintenance et que le joueur ne peut pas passer. */
    private boolean blockedByMaintenance(Player p, GameInfo g) {
        if (!maintenance.containsKey(g.id) || p.hasPermission("eemicore.maintenance.bypass")) return false;
        msg(p, maintenanceMessage(g));
        return true;
    }

    /** Commande tapée sans le /, en minuscules, préfixe « plugin: » retiré, espaces simples. */
    private static String normCommand(String cmd) {
        String c = cmd.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("/")) c = c.substring(1);
        c = c.trim().replaceAll("\\s+", " ");
        int space = c.indexOf(' ');
        String first = space < 0 ? c : c.substring(0, space);
        int colon = first.indexOf(':');
        if (colon >= 0) c = c.substring(colon + 1);
        return c;
    }

    /** Vrai si la commande est une commande de join du jeu (join sans « console: » ou join-aliases). */
    private static boolean isJoinCommand(GameInfo g, String typed) {
        List<String> forms = new ArrayList<>(g.joinAliases);
        if (g.join != null && !g.join.isBlank() && !g.join.trim().toLowerCase(Locale.ROOT).startsWith("console:")) forms.add(g.join);
        for (String form : forms) {
            String f = normCommand(form.replace("%player%", ""));
            if (f.isEmpty()) continue;
            // un join d'un seul mot (duel, ffa, island) ne bloque que la commande seule :
            // « /duel spectate », « /ffa leave », « /island go » restent permis
            if (f.indexOf(' ') < 0 ? typed.equals(f) : typed.equals(f) || typed.startsWith(f + " ")) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMaintenanceCommand(PlayerCommandPreprocessEvent e) {
        if (maintenance.isEmpty()) return;
        Player p = e.getPlayer();
        if (p.hasPermission("eemicore.maintenance.bypass")) return;
        String typed = normCommand(e.getMessage());
        for (String id : maintenance.keySet()) {
            GameInfo g = game(id);
            if (g == null || !isJoinCommand(g, typed)) continue;
            e.setCancelled(true);
            msg(p, maintenanceMessage(g));
            return;
        }
    }

    private void setMaintenance(CommandSender sender, GameInfo g, boolean on, String reason) {
        if (on) {
            maintenance.put(g.id, new Maintenance(reason == null ? "" : reason.trim(), sender.getName(), System.currentTimeMillis()));
        } else if (maintenance.remove(g.id) == null) {
            msg(sender, PREFIX + "&7" + g.name + " &7n'etait pas en maintenance.");
            return;
        }
        saveMaintenance();
        writeStatusFile();
        String announce = PREFIX + "&e" + g.name + (on ? " &7est en maintenance" : " &7est de nouveau disponible");
        for (Player p : Bukkit.getOnlinePlayers()) if (inLobby(p)) msg(p, announce);
        if (!(sender instanceof Player sp && inLobby(sp))) msg(sender, announce);
        getLogger().info(g.id + (on ? " en maintenance par " + sender.getName() + (reason == null || reason.isBlank() ? "" : " : " + reason) : " rouvert par " + sender.getName()));
    }

    private void maintenanceCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eemicore.maintenance")) {
            msg(sender, "&cPermission refusee.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            if (maintenance.isEmpty()) {
                msg(sender, PREFIX + "&aAucun jeu en maintenance.");
                return;
            }
            SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
            msg(sender, PREFIX + "&eJeux en maintenance :");
            for (Map.Entry<String, Maintenance> e : maintenance.entrySet()) {
                Maintenance m = e.getValue();
                GameInfo g = game(e.getKey());
                long min = (System.currentTimeMillis() - m.since()) / 60_000L;
                String ago = min < 60 ? min + " min" : min / 60 + " h " + min % 60 + " min";
                msg(sender, " &e" + e.getKey() + " &8- " + (g == null ? e.getKey() : g.name)
                        + " &8: &f" + (m.reason().isBlank() ? "&7(sans raison)" : m.reason())
                        + " &8(par &7" + m.by() + "&8, depuis le &7" + fmt.format(new java.util.Date(m.since())) + "&8, il y a " + ago + ")");
            }
            return;
        }
        if (args.length < 3) {
            msg(sender, PREFIX + "&c/eemi maintenance <jeu> on [raison...] &7| &c/eemi maintenance <jeu> off &7| &c/eemi maintenance list");
            return;
        }
        GameInfo g = game(args[1]);
        if (g == null) {
            msg(sender, PREFIX + "&cJeu inconnu : " + args[1] + ". &7Jeux : " + String.join(", ", games.keySet()));
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on" -> setMaintenance(sender, g, true, String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
            case "off" -> setMaintenance(sender, g, false, null);
            default -> msg(sender, PREFIX + "&c/eemi maintenance <jeu> <on|off> [raison...]");
        }
    }

    // ------------------------------------------------------------------ fichier de statut (status.json)

    /** (Re)lance l'écriture périodique, selon status-file du config. Écrit aussi tout de suite. */
    private void startStatusFile() {
        if (statusFileTask != null) {
            statusFileTask.cancel();
            statusFileTask = null;
        }
        if (!getConfig().getBoolean("status-file.enabled", true)) return;
        long period = Math.max(5, getConfig().getInt("status-file.interval", 30)) * 20L;
        writeStatusFile();
        statusFileTask = getServer().getScheduler().runTaskTimer(this, this::writeStatusFile, period, period);
    }

    /** État publié pour un jeu : celui que connaît EemiCore, la maintenance est un champ à part. */
    private GameSnapshot snapshot(GameInfo g) {
        String state;
        int waiting = 0, playing = 0;
        if (g.fresh(expireMs)) {
            waiting = g.waiting;
            playing = g.playing;
            state = switch (g.state) {
                case "starting", "playing", "ending" -> g.state;
                // « waiting » des jeux en parallèle : même lecture que statusText
                default -> waiting > 0 ? "waiting" : playing > 0 ? "playing" : "nobody";
            };
        } else if (g.state != null) {
            state = "unknown"; // jeu maison sans nouvelle depuis status-expire-minutes
        } else {
            playing = g.worldPlayers(); // jeu tiers : déduit de ses mondes
            state = playing > 0 ? "playing" : "nobody";
        }
        return new GameSnapshot(g.id, g.name, maintenance.get(g.id), state, waiting, playing, g.max);
    }

    /**
     * Copie des données sur le thread principal, puis JSON écrit en asynchrone dans status.json.tmp
     * et déplacé d'un coup sur status.json : le bot ne lit jamais un fichier à moitié écrit.
     */
    private void writeStatusFile() {
        if (!getConfig().getBoolean("status-file.enabled", true)) return;
        List<GameSnapshot> snap = new ArrayList<>();
        // ordre du config.yml, puis les jeux connus seulement par « eemi status »
        ConfigurationSection gs = getConfig().getConfigurationSection("games");
        Set<String> done = new HashSet<>();
        if (gs != null) for (String id : gs.getKeys(false)) {
            GameInfo g = game(id);
            if (g != null && done.add(g.id)) snap.add(snapshot(g));
        }
        for (GameInfo g : games.values()) if (done.add(g.id)) snap.add(snapshot(g));
        long seq = ++statusFileSeq;
        Path dir = getDataFolder().toPath();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            synchronized (statusFileLock) {
                if (seq < statusFileWritten) return; // une copie plus récente est déjà écrite
                Path tmp = dir.resolve("status.json.tmp");
                Path out = dir.resolve("status.json");
                try {
                    Files.writeString(tmp, statusJson(snap, System.currentTimeMillis()), StandardCharsets.UTF_8);
                    try {
                        Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException ex) {
                        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    statusFileWritten = seq;
                } catch (IOException ex) {
                    getLogger().warning("Ecriture status.json : " + ex.getMessage());
                }
            }
        });
    }

    private static String statusJson(List<GameSnapshot> snap, long now) {
        StringBuilder b = new StringBuilder(4096);
        b.append("{\n  \"version\": 1,\n  \"updated_at\": ").append(now).append(",\n  \"games\": [");
        for (int i = 0; i < snap.size(); i++) {
            GameSnapshot s = snap.get(i);
            b.append(i == 0 ? "\n" : ",\n").append("    {")
                    .append("\"id\": ").append(jsonString(s.id()))
                    .append(", \"name\": ").append(jsonString(s.name()))
                    .append(", \"maintenance\": ").append(s.maintenance() != null)
                    .append(", \"maintenance_reason\": ")
                    .append(s.maintenance() == null || s.maintenance().reason().isBlank() ? "null" : jsonString(s.maintenance().reason()))
                    .append(", \"state\": ").append(jsonString(s.state()))
                    .append(", \"waiting\": ").append(s.waiting())
                    .append(", \"playing\": ").append(s.playing())
                    .append(", \"max\": ").append(s.max())
                    .append('}');
        }
        b.append(snap.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return b.toString();
    }

    private static String jsonString(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.append('"').toString();
    }

    // ------------------------------------------------------------------ récompenses et progression

    private void reward(PlayerData d, GameInfo g, String type) {
        String stat = switch (type) {
            case "win" -> "wins";
            case "kill" -> "kills";
            default -> "plays";
        };
        d.addStat(g.id, stat, 1);
        long coins = Math.round(getConfig().getLong("rewards." + type + ".coins", 0) * g.multiplier);
        long xp = Math.round(getConfig().getLong("rewards." + type + ".xp", 0) * g.multiplier);
        d.coins += coins;
        Player p = Bukkit.getPlayer(d.uuid);
        if (p != null && (coins > 0 || xp > 0)) p.sendActionBar(c("&6+" + coins + " pieces  &b+" + xp + " xp"));
        giveXp(d, xp);
        // quêtes
        ensureQuests(d);
        for (PlayerData.Quest q : d.quests) {
            if (q.done || !q.type.equals(type)) continue;
            if (!q.game.isEmpty() && !q.game.equals(g.id)) continue;
            q.progress++;
            if (q.progress >= q.target) {
                q.done = true;
                q.progress = q.target;
                d.coins += q.coins;
                if (p != null) {
                    msg(p, PREFIX + "&aQuete terminee : &f" + q.text + " &7(&6+" + q.coins + " pieces&7, &b+" + q.xp + " xp&7)");
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
                }
                giveXp(d, q.xp);
            }
        }
        markDirty(d);
    }

    private void giveXp(PlayerData d, long amount) {
        if (amount <= 0) return;
        d.xp += amount;
        Player p = Bukkit.getPlayer(d.uuid);
        while (d.xp >= xpNeeded(d.level)) {
            d.xp -= xpNeeded(d.level);
            d.level++;
            long bonus = getConfig().getLong("levels.level-up-coins", 10) * d.level;
            d.coins += bonus;
            if (p != null) {
                String col = levelColor(d.level);
                p.showTitle(Title.title(c(col + "&lNIVEAU " + d.level), c("&e+" + bonus + " pieces"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                msg(p, PREFIX + "&aTu passes au niveau " + col + d.level + "&a ! &6+" + bonus + " pieces");
            }
        }
        markDirty(d);
    }

    // ------------------------------------------------------------------ quêtes du jour

    /** Tire les quêtes du jour si la date a changé. Renvoie vrai si de nouvelles quêtes ont été tirées. */
    private boolean ensureQuests(PlayerData d) {
        String today = LocalDate.now().toString();
        if (today.equals(d.questDate)) return false;
        d.questDate = today;
        d.quests.clear();
        ConfigurationSection pool = getConfig().getConfigurationSection("quests.pool");
        if (pool != null) {
            List<String> ids = new ArrayList<>(pool.getKeys(false));
            Collections.shuffle(ids);
            int count = Math.min(ids.size(), getConfig().getInt("quests.count", 3));
            List<String> random = new ArrayList<>();
            for (String id : getConfig().getStringList("random-games")) if (game(id) != null) random.add(id.toLowerCase(Locale.ROOT));
            for (int i = 0; i < count; i++) {
                ConfigurationSection s = pool.getConfigurationSection(ids.get(i));
                if (s == null) continue;
                PlayerData.Quest q = new PlayerData.Quest();
                q.id = ids.get(i);
                q.type = s.getString("type", "play");
                String gm = s.getString("game", "any");
                if (gm.equalsIgnoreCase("any")) q.game = "";
                else if (gm.equalsIgnoreCase("random")) q.game = random.isEmpty() ? "" : random.get(ThreadLocalRandom.current().nextInt(random.size()));
                else q.game = gm.toLowerCase(Locale.ROOT);
                q.target = Math.max(1, s.getInt("target", 1));
                q.coins = s.getInt("coins", 0);
                q.xp = s.getInt("xp", 0);
                GameInfo g = game(q.game);
                q.text = s.getString("text", q.id)
                        .replace("%target%", String.valueOf(q.target))
                        .replace("%game%", g == null ? "n'importe quel jeu" : g.name + "&f");
                d.quests.add(q);
            }
        }
        markDirty(d);
        return true;
    }

    // ------------------------------------------------------------------ classements

    private void recomputeTops() {
        Map<String, List<TopEntry>> result = new HashMap<>();
        Set<String> ids = new HashSet<>(games.keySet());
        for (PlayerData d : players.values()) ids.addAll(d.stats.keySet());
        for (String id : ids) {
            for (String type : STAT_TYPES) {
                List<TopEntry> list = new ArrayList<>();
                for (PlayerData d : players.values()) {
                    int v = d.stat(id, type);
                    if (v > 0) list.add(new TopEntry(d.name, v));
                }
                list.sort(Comparator.comparingLong(TopEntry::value).reversed());
                result.put(id + ":" + type, list.subList(0, Math.min(10, list.size())));
            }
        }
        List<PlayerData> all = new ArrayList<>(players.values());
        all.sort(Comparator.comparingInt((PlayerData d) -> d.level).thenComparingLong(d -> d.xp).reversed());
        List<TopEntry> lv = new ArrayList<>();
        for (PlayerData d : all) {
            if (lv.size() >= 10) break;
            lv.add(new TopEntry(d.name, d.level));
        }
        result.put("level:level", lv);
        tops = result;
    }

    private static String unit(String type, long v) {
        return switch (type) {
            case "kills" -> v + " kill" + (v > 1 ? "s" : "");
            case "plays" -> v + " partie" + (v > 1 ? "s" : "");
            case "level" -> "niveau " + v;
            default -> v + " victoire" + (v > 1 ? "s" : "");
        };
    }

    private static String normType(String t) {
        if (t == null) return "wins";
        return switch (t.toLowerCase(Locale.ROOT)) {
            case "kills", "kill" -> "kills";
            case "plays", "play", "parties" -> "plays";
            default -> "wins";
        };
    }

    private void showTop(CommandSender to, String id, String type) {
        boolean level = id.equalsIgnoreCase("level") || id.equalsIgnoreCase("niveau");
        String key = level ? "level:level" : id.toLowerCase(Locale.ROOT) + ":" + normType(type);
        List<TopEntry> list = tops.getOrDefault(key, List.of());
        GameInfo g = game(id);
        String title = level ? "&eNiveaux" : (g == null ? id : g.name) + " &7(" + normType(type) + ")";
        msg(to, PREFIX + "&eClassement " + title);
        if (list.isEmpty()) msg(to, "&7 Personne pour l'instant.");
        int rank = 1;
        for (TopEntry e : list) {
            String medal = rank == 1 ? "&6" : rank == 2 ? "&f" : rank == 3 ? "&c" : "&7";
            msg(to, " " + medal + "#" + rank++ + " &f" + e.name + " &7- &e" + unit(level ? "level" : normType(type), e.value));
        }
    }

    // ------------------------------------------------------------------ placeholders

    /** Appelé par CoreExpansion. Renvoie du texte avec couleurs § (lisible par TAB, DecentHolograms, DeluxeHub). */
    String placeholder(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("status_")) {
            GameInfo g = game(p.substring(7));
            return g == null ? "" : sec(statusText(g));
        }
        if (p.startsWith("maintenance_")) {
            GameInfo g = game(p.substring(12));
            return String.valueOf(g != null && maintenance.containsKey(g.id));
        }
        if (p.startsWith("players_")) {
            GameInfo g = game(p.substring(8));
            return g == null ? "0" : String.valueOf(playerCount(g));
        }
        if (p.startsWith("name_")) {
            GameInfo g = game(p.substring(5));
            return g == null ? "" : sec(g.name);
        }
        if (p.startsWith("top_")) {
            // top_<jeu>_<rang> ou top_<jeu>_<wins|kills|plays>_<rang>
            String[] parts = p.substring(4).split("_");
            if (parts.length < 2) return "";
            int rank = parseInt(parts[parts.length - 1], 0);
            String id = parts[0];
            boolean level = id.equals("level");
            String type = level ? "level" : normType(parts.length >= 3 ? parts[1] : "wins");
            List<TopEntry> list = tops.getOrDefault(level ? "level:level" : id + ":" + type, List.of());
            if (rank < 1 || rank > list.size()) return "";
            TopEntry e = list.get(rank - 1);
            return sec(e.name + " &7- &e" + unit(type, e.value));
        }
        if (player == null) return "";
        if (p.equals("team")) {
            // équipe posée par le jeu sur le scoreboard du joueur (« [Rouge] »…), pour la liste des joueurs
            Player online = player.getPlayer();
            if (online == null) return "";
            org.bukkit.scoreboard.Team t = online.getScoreboard().getEntryTeam(online.getName());
            return t == null ? "" : SECTION.serialize(t.prefix());
        }
        PlayerData d = players.get(player.getUniqueId());
        if (d == null) d = new PlayerData(player.getUniqueId());
        return switch (p) {
            case "level" -> String.valueOf(d.level);
            case "level_colored" -> sec(levelColor(d.level) + d.level);
            case "coins" -> String.valueOf(d.coins);
            case "xp" -> String.valueOf(d.xp);
            case "xp_needed" -> String.valueOf(xpNeeded(d.level));
            case "title" -> sec(equippedTitle(d));
            // pour la liste des joueurs (TAB) : « [Titre] » ou rien
            case "title_prefix" -> equippedTitle(d).isEmpty() ? "" : sec("&8[" + equippedTitle(d) + "&8] &r");
            case "wins" -> String.valueOf(d.total("wins"));
            case "kills" -> String.valueOf(d.total("kills"));
            case "plays" -> String.valueOf(d.total("plays"));
            default -> null;
        };
    }

    // ------------------------------------------------------------------ chat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!getConfig().getBoolean("chat.enabled", true)) return;
        Player p = e.getPlayer();
        for (String w : getConfig().getStringList("chat.disabled-worlds")) if (w.equalsIgnoreCase(p.getWorld().getName())) return;
        PlayerData d = players.get(p.getUniqueId());
        int level = d == null ? 1 : d.level;
        String title = d == null ? "" : equippedTitle(d);
        Component prefix = c("&8[" + levelColor(level) + "Niv " + level + "&8] " + (title.isEmpty() ? "" : "&8[" + title + "&8] "));
        e.renderer((source, displayName, message, viewer) -> prefix.append(displayName)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }

    // ------------------------------------------------------------------ connexions

    private void onJoinData(Player p) {
        PlayerData d = data(p);
        offlineSince.remove(p.getUniqueId());
        ensureQuests(d);
        markDirty(d);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        onJoinData(p);
        PlayerData d = data(p);
        Bukkit.getScheduler().runTaskLater(this, () -> applyHat(p), 10L);
        Bukkit.getScheduler().runTaskLater(this, () -> applyGadget(p), 20L);
        ConfigurationSection jm = cosmetic(d.equipped.get("join"));
        if (jm != null) {
            String text = jm.getString("message", "").replace("%player%", p.getName());
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || p.hasMetadata("vanished") || text.isEmpty()) return;
                for (Player o : Bukkit.getOnlinePlayers()) msg(o, text);
            }, 20L);
        }
        Party party = partyOf.get(p.getUniqueId());
        if (party != null) partyBroadcast(party, "&a" + p.getName() + " est de retour.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        removeHat(p);
        removeGadget(p);
        removeTitleTag(p);
        jetpacking.remove(p.getUniqueId());
        lastTrailLoc.remove(p.getUniqueId());
        invites.remove(p.getUniqueId());
        Party party = partyOf.get(p.getUniqueId());
        if (party != null) {
            offlineSince.put(p.getUniqueId(), System.currentTimeMillis());
            partyBroadcast(party, "&7" + p.getName() + " s'est deconnecte (retire du groupe dans 5 min).");
        }
        markDirty(data(p));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (inLobby(p)) {
            Bukkit.getScheduler().runTaskLater(this, () -> applyHat(p), 10L);
            Bukkit.getScheduler().runTaskLater(this, () -> applyGadget(p), 20L);
        } else {
            removeHat(p);
            removeGadget(p);
            removeTitleTag(p);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                removeHat(p);
                removeGadget(p);
            }, 10L);
        }
        // chef de groupe entré dans un jeu sans passer par /eemi join : proposer [SUIVRE]
        Party party = partyOf.get(p.getUniqueId());
        if (party == null || !party.leader.equals(p.getUniqueId()) || party.members.size() < 2) return;
        GameInfo g = gameForWorld(p.getWorld().getName());
        if (g == null || !getConfig().getBoolean("party.auto-follow", true)) return;
        long now = System.currentTimeMillis();
        JoinMark j = lastJoin.get(p.getUniqueId());
        boolean viaEemi = j != null && j.game.equals(g.id) && now - j.time < 60_000L;
        // déjà emmenés par /eemi join (sauf jeux à menu, où le chef vient seulement de choisir)
        if (viaEemi && !isMenuJoin(g)) return;
        if (getConfig().getStringList("party.no-follow").contains(g.id)) return;
        LeaderCmd lc = leaderCmd.get(p.getUniqueId());
        String cmd = lc != null && lc.game.equals(g.id) && now - lc.time < 60_000L ? lc.cmd : null;
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m == null || m.equals(p) || m.getWorld().equals(p.getWorld())) continue;
            GameInfo mg = gameForWorld(m.getWorld().getName());
            if (mg != null && mg.id.equals(g.id)) continue; // déjà dans ce jeu (salle d'attente, autre monde du jeu)
            followLeader(m, p, g, cmd);
        }
    }

    /** Un membre rejoint la partie de son chef : même commande que lui si on la connaît, sinon le join du jeu. */
    private void followLeader(Player m, Player leader, GameInfo g, String cmd) {
        if (cmd != null) {
            msg(m, PARTY + "&7Tu suis " + leader.getName() + " en " + g.name + "&7.");
            lastJoin.put(m.getUniqueId(), new JoinMark(g.id, System.currentTimeMillis()));
            Bukkit.getScheduler().runTask(this, () -> {
                if (m.isOnline()) m.performCommand(cmd);
            });
        } else if (!isMenuJoin(g)) {
            msg(m, PARTY + "&7Tu suis " + leader.getName() + " en " + g.name + "&7.");
            runJoin(m, g);
        } else {
            m.sendMessage(c(PARTY + "&7" + leader.getName() + " est parti jouer a " + g.name + "&7. ")
                    .append(button("[SUIVRE]", NamedTextColor.GREEN, "/eemi suivre " + leader.getName(), "Rejoindre " + leader.getName())));
        }
    }

    /** Le join du jeu ouvre un menu de choix (dm open ...) au lieu d'entrer directement dans une partie. */
    private static boolean isMenuJoin(GameInfo g) {
        return g.join != null && g.join.toLowerCase(Locale.ROOT).contains("dm open");
    }

    /** Commande de join tapée (ou lancée par un menu) par un chef de groupe : mémorisée pour la rejouer aux membres. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeaderCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        Party party = partyOf.get(p.getUniqueId());
        if (party == null || !party.leader.equals(p.getUniqueId()) || party.members.size() < 2) return;
        String typed = normCommand(e.getMessage());
        if (typed.startsWith("eemi ")) return;
        for (GameInfo g : games.values()) {
            if (!isJoinCommand(g, typed)) continue;
            leaderCmd.put(p.getUniqueId(), new LeaderCmd(g.id, typed, System.currentTimeMillis()));
            return;
        }
    }

    /** Avant le changement de monde : retirer le chapeau tout de suite (inventaire sauvegardé par Multiverse-Inventories). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (!e.getFrom().getWorld().equals(e.getTo().getWorld()) && !e.getTo().getWorld().getName().equalsIgnoreCase(lobbyWorld)) {
            removeHat(e.getPlayer());
            removeGadget(e.getPlayer());
            removeTitleTag(e.getPlayer());
        }
    }

    // ------------------------------------------------------------------ cosmétiques : chapeaux

    private boolean isHat(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(hatKey, PersistentDataType.STRING);
    }

    private void applyHat(Player p) {
        if (!p.isOnline()) return;
        ConfigurationSection s = cosmetic(data(p).equipped.get("hat"));
        if (!inLobby(p) || s == null || p.getGameMode() == GameMode.SPECTATOR) {
            removeHat(p);
            return;
        }
        Material m = Material.matchMaterial(s.getString("material", "GLASS"));
        if (m == null || !m.isItem()) return;
        ItemStack current = p.getInventory().getHelmet();
        if (current != null && !current.getType().isAir() && !isHat(current)) return; // vrai casque : on n'y touche pas
        String id = data(p).equipped.get("hat");
        if (isHat(current) && current.getType() == m
                && id.equals(current.getItemMeta().getPersistentDataContainer().get(hatKey, PersistentDataType.STRING))) return;
        ItemStack hat = new ItemStack(m);
        ItemMeta meta = hat.getItemMeta();
        meta.displayName(c(s.getString("name", id)));
        meta.lore(List.of(c("&7Chapeau EEMICRAFT (hub seulement)")));
        meta.getPersistentDataContainer().set(hatKey, PersistentDataType.STRING, id);
        meta.addItemFlags(ItemFlag.values());
        hat.setItemMeta(meta);
        p.getInventory().setHelmet(hat);
    }

    private void removeHat(Player p) {
        if (isHat(p.getInventory().getHelmet())) p.getInventory().setHelmet(null);
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (isHat(contents[i])) p.getInventory().setItem(i, null);
    }

    private void hatsCheck() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (inLobby(p) && data(p).equipped.containsKey("hat")) applyHat(p);
            else if (isHat(p.getInventory().getHelmet())) removeHat(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHatClick(InventoryClickEvent e) {
        if (isHat(e.getCurrentItem()) || isHat(e.getCursor())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHatDrop(PlayerDropItemEvent e) {
        if (isHat(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(it -> isHat(it) || gadgetOf(it) != null);
    }

    // ------------------------------------------------------------------ cosmétiques : traînées

    private void trails() {
        rainbowHue = (rainbowHue + 0.02f) % 1f;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!inLobby(p) || p.getGameMode() == GameMode.SPECTATOR || p.hasMetadata("vanished")) continue;
            PlayerData d = players.get(p.getUniqueId());
            ConfigurationSection s = d == null ? null : cosmetic(d.equipped.get("trail"));
            if (s == null) continue;
            Location loc = p.getLocation();
            Location last = lastTrailLoc.put(p.getUniqueId(), loc);
            if (last == null || !last.getWorld().equals(loc.getWorld()) || last.distanceSquared(loc) < 0.0025) continue; // immobile
            Location at = loc.clone().add(0, 0.15, 0);
            String name = s.getString("particle", "FLAME").toUpperCase(Locale.ROOT);
            try {
                if (name.equals("RAINBOW")) {
                    java.awt.Color rgb = java.awt.Color.getHSBColor(rainbowHue, 1f, 1f);
                    p.getWorld().spawnParticle(Particle.DUST, at, 3, 0.2, 0.05, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue()), 1.2f));
                } else {
                    Particle particle = Particle.valueOf(name);
                    if (particle.getDataType() != Void.class) continue;
                    p.getWorld().spawnParticle(particle, at, 2, 0.15, 0.05, 0.15, 0.01);
                }
            } catch (IllegalArgumentException ex) {
                // particule inconnue dans la config : ignorée
            }
        }
    }

    // ------------------------------------------------------------------ anti-vol hors du hub

    /**
     * Le double saut de DeluxeHub donne le droit de voler au hub, et les jeux ne le retirent pas tous
     * (on volait en FFA). Hors du hub et des mondes de flight-worlds (double saut de TNT Run, Cache-cache),
     * un joueur en survie ou en aventure qui n'est pas en vanish ne peut pas voler.
     */
    private void noFlightCheck() {
        List<String> allowed = getConfig().isSet("flight-worlds") ? getConfig().getStringList("flight-worlds") : List.of("tntrun", "hideseek");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getAllowFlight() || inLobby(p) || p.hasMetadata("vanished")) continue;
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            String w = p.getWorld().getName();
            boolean ok = false;
            for (String a : allowed) if (a.equalsIgnoreCase(w)) ok = true;
            if (ok) continue;
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

    // ------------------------------------------------------------------ titre au-dessus de la tête (hub)

    /**
     * Un TextDisplay par joueur titré, au-dessus de son pseudo, déplacé à chaque tick (pas de passager :
     * un joueur qui porte une entité ne peut plus être téléporté). Invisible pour le joueur lui-même.
     */
    private final Map<UUID, org.bukkit.entity.TextDisplay> titleTags = new HashMap<>();

    private boolean wantsTitleTag(Player p) {
        PlayerData d = players.get(p.getUniqueId());
        return d != null && inLobby(p) && !equippedTitle(d).isEmpty() && !p.hasMetadata("vanished")
                && p.getGameMode() != GameMode.SPECTATOR && !p.isInvisible() && getConfig().getBoolean("title-tag.enabled", true);
    }

    private Location titleTagLocation(Player p) {
        double h = getConfig().getDouble("title-tag.height", 2.55) - (p.isSneaking() ? 0.3 : 0);
        Location l = p.getLocation().add(0, h, 0);
        l.setYaw(0);
        l.setPitch(0);
        return l;
    }

    private void updateTitleTag(Player p) {
        org.bukkit.entity.TextDisplay td = titleTags.get(p.getUniqueId());
        if (!p.isOnline() || !wantsTitleTag(p)) {
            if (td != null) td.remove();
            titleTags.remove(p.getUniqueId());
            return;
        }
        Component text = c(equippedTitle(data(p)));
        if (td == null || !td.isValid() || !td.getWorld().equals(p.getWorld())) {
            if (td != null) td.remove();
            td = p.getWorld().spawn(titleTagLocation(p), org.bukkit.entity.TextDisplay.class, t -> {
                t.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                t.setPersistent(false);
                t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setTeleportDuration(2);
                t.text(text);
            });
            p.hideEntity(this, td);
            titleTags.put(p.getUniqueId(), td);
        } else if (!text.equals(td.text())) td.text(text);
    }

    private void titleTagsTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            org.bukkit.entity.TextDisplay td = titleTags.get(p.getUniqueId());
            if (td != null && td.isValid() && wantsTitleTag(p) && td.getWorld().equals(p.getWorld())) td.teleport(titleTagLocation(p));
        }
    }

    private void removeTitleTag(Player p) {
        org.bukkit.entity.TextDisplay td = titleTags.remove(p.getUniqueId());
        if (td != null) td.remove();
    }

    /** WorldGuard bloque les apparitions de plugin au hub : on rétablit les nôtres (titres, feux d'artifice). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOwnSpawn(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) e.setCancelled(false);
    }

    // ------------------------------------------------------------------ gadgets (hub)

    private final Map<UUID, Long> gadgetCooldown = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    private final Set<UUID> jetpacking = new HashSet<>();

    private int gadgetSlot() {
        return Math.max(0, Math.min(8, getConfig().getInt("gadget-slot", 7)));
    }

    private String gadgetOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(gadgetKey, PersistentDataType.STRING);
    }

    private void applyGadget(Player p) {
        if (!p.isOnline()) return;
        String id = data(p).equipped.get("gadget");
        ConfigurationSection s = cosmetic(id);
        if (!inLobby(p) || s == null || p.getGameMode() == GameMode.SPECTATOR || p.hasMetadata("vanished")) {
            removeGadget(p);
            return;
        }
        ItemStack current = p.getInventory().getItem(gadgetSlot());
        if (id.equals(gadgetOf(current))) return;
        if (current != null && !current.getType().isAir() && gadgetOf(current) == null) return; // case occupée
        Material m = Material.matchMaterial(s.getString("icon", "PAPER"));
        ItemStack it = new ItemStack(m == null || !m.isItem() ? Material.PAPER : m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(s.getString("name", id) + " &7(clic droit)"));
        List<Component> lore = new ArrayList<>();
        for (String line : s.getStringList("description")) lore.add(c("&7" + line));
        lore.add(c("&8Gadget EEMICRAFT (hub seulement)"));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(gadgetKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        removeGadget(p);
        p.getInventory().setItem(gadgetSlot(), it);
    }

    private void removeGadget(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (gadgetOf(contents[i]) != null) p.getInventory().setItem(i, null);
    }

    private void gadgetsCheck() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (inLobby(p) && data(p).equipped.containsKey("gadget")) applyGadget(p);
            else removeGadget(p);
        }
    }

    /** Délai écoulé ? Sinon message avec le temps restant. */
    private boolean gadgetReady(Player p, ConfigurationSection s) {
        long now = System.currentTimeMillis();
        Long until = gadgetCooldown.get(p.getUniqueId());
        if (until != null && until > now) {
            p.sendActionBar(c("&cGadget pret dans " + ((until - now) / 1000 + 1) + " s"));
            return false;
        }
        gadgetCooldown.put(p.getUniqueId(), now + s.getLong("cooldown", 5) * 1000L);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGadgetUse(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        org.bukkit.event.block.Action a = e.getAction();
        if (a != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && a != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String id = gadgetOf(e.getItem());
        if (id == null) return;
        ConfigurationSection s = cosmetic(id);
        if (s == null || !inLobby(p)) return;
        String kind = s.getString("gadget", "");
        if (kind.equals("grappin")) return; // la canne se lance normalement, voir onGrapple
        e.setCancelled(true);
        if (!gadgetReady(p, s)) return;
        switch (kind) {
            case "jetpack" -> jetpack(p, s.getInt("duration", 60));
            case "peinture" -> {
                org.bukkit.entity.Snowball ball = p.launchProjectile(org.bukkit.entity.Snowball.class, p.getLocation().getDirection().multiply(1.8));
                ball.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                p.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.8f, 1.3f);
            }
            case "feux" -> {
                Location at = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5));
                p.getWorld().spawn(at, org.bukkit.entity.Firework.class, fw -> {
                    fw.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                    org.bukkit.inventory.meta.FireworkMeta fm = fw.getFireworkMeta();
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    org.bukkit.FireworkEffect.Type[] types = org.bukkit.FireworkEffect.Type.values();
                    fm.addEffect(org.bukkit.FireworkEffect.builder().with(types[r.nextInt(types.length)])
                            .withColor(Color.fromRGB(r.nextInt(0xFFFFFF)), Color.fromRGB(r.nextInt(0xFFFFFF)))
                            .withFade(Color.WHITE).flicker(r.nextBoolean()).trail(true).build());
                    fm.setPower(1);
                    fw.setFireworkMeta(fm);
                });
            }
            default -> {
            }
        }
    }

    /** Jetpack : poussée vers le haut et dans la direction du regard pendant « duration » ticks. */
    private void jetpack(Player p, int duration) {
        if (!jetpacking.add(p.getUniqueId())) return;
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.8f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || !inLobby(p) || ++t > duration || p.getLocation().getY() > getConfig().getDouble("jetpack-max-y", 120)) {
                    jetpacking.remove(p.getUniqueId());
                    noFallUntil.put(p.getUniqueId(), System.currentTimeMillis() + 8000L);
                    cancel();
                    return;
                }
                org.bukkit.util.Vector v = p.getLocation().getDirection().setY(0).multiply(0.45);
                v.setY(0.42);
                p.setVelocity(v);
                p.setFallDistance(0);
                Location feet = p.getLocation().add(0, 0.2, 0);
                p.getWorld().spawnParticle(Particle.FLAME, feet, 4, 0.12, 0.05, 0.12, 0.02);
                p.getWorld().spawnParticle(Particle.CLOUD, feet, 2, 0.15, 0.05, 0.15, 0.01);
                if (t % 4 == 0) p.getWorld().playSound(feet, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.8f);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    /** Grappin : la canne tire le joueur vers l'hameçon accroché. */
    @EventHandler
    public void onGrapple(org.bukkit.event.player.PlayerFishEvent e) {
        Player p = e.getPlayer();
        ItemStack rod = p.getInventory().getItemInMainHand();
        String id = gadgetOf(rod);
        ConfigurationSection s = cosmetic(id);
        if (s == null || !"grappin".equals(s.getString("gadget")) || !inLobby(p)) return;
        org.bukkit.event.player.PlayerFishEvent.State st = e.getState();
        if (st == org.bukkit.event.player.PlayerFishEvent.State.FISHING || st == org.bukkit.event.player.PlayerFishEvent.State.BITE) return;
        Location hook = e.getHook().getLocation();
        boolean hooked = st == org.bukkit.event.player.PlayerFishEvent.State.IN_GROUND || e.getHook().isOnGround()
                || !hook.clone().subtract(0, 0.3, 0).getBlock().isPassable();
        if (!hooked) return;
        if (!gadgetReady(p, s)) return;
        org.bukkit.util.Vector v = hook.toVector().subtract(p.getLocation().toVector());
        double dist = v.length();
        if (dist < 1.5) return;
        v.normalize().multiply(Math.min(2.4, 0.5 + dist * 0.12));
        v.setY(v.getY() + 0.45);
        p.setVelocity(v);
        noFallUntil.put(p.getUniqueId(), System.currentTimeMillis() + 6000L);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.7f);
    }

    /** Pistolet à peinture : taches de laine colorée (fausses, revient après 3 s) autour de l'impact. */
    @EventHandler
    public void onPaint(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Snowball ball) || !ball.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) return;
        if (e.getHitEntity() != null) e.setCancelled(true);
        Location at = e.getHitBlock() != null ? e.getHitBlock().getLocation() : ball.getLocation();
        Material[] wools = {Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL,
                Material.MAGENTA_WOOL, Material.PINK_WOOL, Material.PURPLE_WOOL};
        Material wool = wools[ThreadLocalRandom.current().nextInt(wools.length)];
        Map<Location, org.bukkit.block.data.BlockData> fake = new HashMap<>();
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz > 5 || ThreadLocalRandom.current().nextInt(4) == 0) continue;
                    org.bukkit.block.Block b = at.clone().add(dx, dy, dz).getBlock();
                    if (!b.getType().isSolid()) continue;
                    boolean exposed = false; // seulement les blocs visibles (une face à l'air)
                    for (org.bukkit.block.BlockFace f : new org.bukkit.block.BlockFace[]{org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN,
                            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST})
                        if (b.getRelative(f).isPassable()) exposed = true;
                    if (exposed) fake.put(b.getLocation(), wool.createBlockData());
                }
        if (fake.isEmpty()) return;
        List<Player> viewers = new ArrayList<>();
        for (Player o : at.getWorld().getPlayers()) if (o.getLocation().distanceSquared(at) < 64 * 64) viewers.add(o);
        for (Player o : viewers) o.sendMultiBlockChange(fake);
        at.getWorld().spawnParticle(Particle.ITEM_SLIME, at.clone().add(0.5, 1, 0.5), 12, 0.6, 0.3, 0.6, 0.05);
        at.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, 0.8f, 1.2f);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Map<Location, org.bukkit.block.data.BlockData> real = new HashMap<>();
            for (Location l : fake.keySet()) real.put(l, l.getBlock().getBlockData());
            for (Player o : viewers) if (o.isOnline() && o.getWorld().equals(at.getWorld())) o.sendMultiBlockChange(real);
        }, 60L);
    }

    /** Projectiles et feux d'artifice des gadgets : aucun dégât ni recul. Pas de dégâts de chute après jetpack/grappin. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGadgetDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by
                && by.getDamager().getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p) {
            Long until = noFallUntil.get(p.getUniqueId());
            if (until != null && until > System.currentTimeMillis()) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGadgetClick(InventoryClickEvent e) {
        if (gadgetOf(e.getCurrentItem()) != null || gadgetOf(e.getCursor()) != null) e.setCancelled(true);
        if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p
                && gadgetOf(p.getInventory().getItem(e.getHotbarButton())) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGadgetDrop(PlayerDropItemEvent e) {
        if (gadgetOf(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGadgetSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        if (gadgetOf(e.getMainHandItem()) != null || gadgetOf(e.getOffHandItem()) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ menus

    private static final class Menu implements InventoryHolder {
        final String kind;
        final Map<Integer, String> actions = new HashMap<>();
        Inventory inventory;

        Menu(String kind) {
            this.kind = kind;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(c(s));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private void openLater(Player p, Inventory inv) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) p.openInventory(inv);
        }, 2L);
    }

    private void openProfile(Player viewer, PlayerData d) {
        Menu holder = new Menu("profile");
        Inventory inv = Bukkit.createInventory(holder, 54, c("Profil de " + d.name));
        holder.inventory = inv;
        long need = xpNeeded(d.level);
        ItemStack head = item(Material.PLAYER_HEAD, levelColor(d.level) + d.name, List.of(
                "&7Niveau : " + levelColor(d.level) + d.level,
                "&7XP : &b" + d.xp + "&7/&b" + need,
                bar(d.xp, need, 30),
                "&7Pieces : &6" + d.coins,
                "",
                "&7Parties : &f" + d.total("plays"),
                "&7Victoires : &a" + d.total("wins"),
                "&7Kills : &c" + d.total("kills"),
                equippedTitle(d).isEmpty() ? "&7Titre : &8aucun" : "&7Titre : " + equippedTitle(d)), false);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer(d.uuid));
        head.setItemMeta(sm);
        inv.setItem(4, head);
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int i = 0;
        for (GameInfo g : games.values()) {
            if (i >= slots.length) break;
            inv.setItem(slots[i++], item(g.icon, g.name, List.of(
                    "&7Parties : &f" + d.stat(g.id, "plays"),
                    "&7Victoires : &a" + d.stat(g.id, "wins"),
                    "&7Kills : &c" + d.stat(g.id, "kills")), false));
        }
        PlayerData own = data(viewer);
        if (!own.uuid.equals(d.uuid)) {
            inv.setItem(45, item(Material.ARROW, "&eMon profil", List.of("&7Niveau " + levelColor(own.level) + own.level), false));
            holder.actions.put(45, "profile:" + own.uuid);
        } else {
            inv.setItem(45, item(Material.EMERALD, "&aBoutique", List.of("&7Tes pieces : &6" + own.coins, "&7Trainees, chapeaux, titres...", "", "&eClic pour ouvrir"), true));
            holder.actions.put(45, "shop");
        }
        inv.setItem(46, item(Material.WRITABLE_BOOK, "&eQuetes du jour", List.of("&7Gagne des pieces et de l'xp", "", "&eClic pour voir"), false));
        holder.actions.put(46, "quests");
        inv.setItem(49, item(Material.BARRIER, "&cFermer", List.of(), false));
        holder.actions.put(49, "close");
        inv.setItem(52, item(Material.PLAYER_HEAD, "&bJoueurs en ligne", List.of("&7Niveau et xp des joueurs connectes", "", "&eClic pour voir"), false));
        holder.actions.put(52, "players");
        inv.setItem(53, item(Material.GOLD_BLOCK, "&6Classement des niveaux", List.of("&7Les 10 meilleurs joueurs", "", "&eClic pour voir"), false));
        holder.actions.put(53, "toplevel");
        openLater(viewer, inv);
    }

    private static ItemStack head(UUID owner, String name, List<String> lore) {
        ItemStack it = item(Material.PLAYER_HEAD, name, lore, false);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        it.setItemMeta(sm);
        return it;
    }

    private List<String> levelLore(PlayerData d) {
        long need = xpNeeded(d.level);
        return new ArrayList<>(List.of(
                "&7Niveau : " + levelColor(d.level) + d.level,
                "&7XP : &b" + d.xp + "&7/&b" + need,
                bar(d.xp, need, 20),
                "&7Victoires : &a" + d.total("wins") + " &7- Kills : &c" + d.total("kills")));
    }

    /** Joueurs connectés (sauf en vanish) : niveau et xp, clic = profil. */
    private void openPlayers(Player viewer) {
        Menu holder = new Menu("players");
        Inventory inv = Bukkit.createInventory(holder, 54, c("Joueurs en ligne"));
        holder.inventory = inv;
        List<Player> online = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) if (!o.hasMetadata("vanished") || o.equals(viewer)) online.add(o);
        online.sort(Comparator.comparingInt((Player o) -> data(o).level).thenComparingLong(o -> data(o).xp).reversed());
        int slot = 0;
        for (Player o : online) {
            if (slot >= 45) break;
            PlayerData d = data(o);
            List<String> lore = levelLore(d);
            GameInfo g = gameOfPlayer(o);
            lore.add(inLobby(o) ? "&7Au hub" : g != null ? "&7En jeu : " + g.name : "&7Monde : " + o.getWorld().getName());
            lore.add("");
            lore.add("&eClic pour voir son profil");
            if (!o.equals(viewer) && g != null && g.spectate != null && !g.spectate.isBlank())
                lore.add("&bShift-clic pour regarder sa partie");
            inv.setItem(slot, head(o.getUniqueId(), levelColor(d.level) + d.name, lore));
            holder.actions.put(slot++, "profile:" + d.uuid);
        }
        inv.setItem(48, item(Material.ARROW, "&eRetour au profil", List.of(), false));
        holder.actions.put(48, "profile:" + viewer.getUniqueId());
        inv.setItem(49, item(Material.BARRIER, "&cFermer", List.of(), false));
        holder.actions.put(49, "close");
        openLater(viewer, inv);
    }

    /** Les 10 meilleurs niveaux (connectés ou non), clic = profil. */
    private void openTopLevel(Player viewer) {
        Menu holder = new Menu("toplevel");
        Inventory inv = Bukkit.createInventory(holder, 36, c("Classement des niveaux"));
        holder.inventory = inv;
        int[] slots = {4, 12, 14, 19, 20, 21, 22, 23, 24, 25};
        String[] medals = {"&6&l", "&f&l", "&c&l"};
        List<TopEntry> list = tops.getOrDefault("level:level", List.of());
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            PlayerData d = dataByName(list.get(i).name());
            if (d == null) continue;
            List<String> lore = levelLore(d);
            lore.add("");
            lore.add("&eClic pour voir son profil");
            inv.setItem(slots[i], head(d.uuid, (i < 3 ? medals[i] : "&7") + "#" + (i + 1) + " " + levelColor(d.level) + d.name, lore));
            holder.actions.put(slots[i], "profile:" + d.uuid);
        }
        PlayerData own = data(viewer);
        int rank = 1;
        for (PlayerData d : players.values()) if (d.level > own.level || d.level == own.level && d.xp > own.xp) rank++;
        inv.setItem(30, item(Material.ARROW, "&eRetour au profil", List.of("&7Ta place : &f#" + rank), false));
        holder.actions.put(30, "profile:" + viewer.getUniqueId());
        inv.setItem(31, item(Material.BARRIER, "&cFermer", List.of(), false));
        holder.actions.put(31, "close");
        openLater(viewer, inv);
    }

    private void profileText(CommandSender to, PlayerData d) {
        long need = xpNeeded(d.level);
        msg(to, PREFIX + "&eProfil de " + d.name);
        msg(to, "&7 Niveau " + levelColor(d.level) + d.level + " &7- XP &b" + d.xp + "&7/&b" + need + " " + bar(d.xp, need, 20));
        msg(to, "&7 Pieces : &6" + d.coins + " &7- Parties &f" + d.total("plays") + " &7- Victoires &a" + d.total("wins") + " &7- Kills &c" + d.total("kills"));
        for (GameInfo g : games.values()) {
            int pl = d.stat(g.id, "plays");
            if (pl == 0 && d.stat(g.id, "wins") == 0 && d.stat(g.id, "kills") == 0) continue;
            msg(to, "&7  " + g.name + "&7 : " + pl + " parties, &a" + d.stat(g.id, "wins") + " V&7, &c" + d.stat(g.id, "kills") + " kills");
        }
    }

    private static final String[] CATEGORIES = {"trail", "hat", "title", "join", "gadget"};
    private static final String[] CATEGORY_NAMES = {"&bTrainees", "&6Chapeaux", "&eTitres", "&aMessages d'arrivee", "&dGadgets"};
    private static final Material[] CATEGORY_ICONS = {Material.BLAZE_POWDER, Material.LEATHER_HELMET, Material.NAME_TAG, Material.OAK_SIGN, Material.FIREWORK_ROCKET};

    private static final String[] CATEGORY_HELP = {"&7Particules derriere toi quand tu marches", "&7Un chapeau sur ta tete",
            "&7Au-dessus de ta tete au hub et dans le chat", "&7Annonce quand tu te connectes", "&7Un objet a utiliser au hub (clic droit)"};

    /** Cosmétique en vente aujourd'hui (champs optionnels available-from / available-until, AAAA-MM-JJ). */
    private boolean onSale(ConfigurationSection s) {
        LocalDate today = LocalDate.now();
        try {
            String from = s.getString("available-from"), until = s.getString("available-until");
            if (from != null && today.isBefore(LocalDate.parse(from))) return false;
            if (until != null && today.isAfter(LocalDate.parse(until))) return false;
        } catch (java.time.format.DateTimeParseException ex) {
            return true;
        }
        return true;
    }

    /** Cosmétiques d'une catégorie, dans l'ordre de la config. */
    private List<String> cosmeticsOf(String cat) {
        List<String> ids = new ArrayList<>();
        ConfigurationSection all = getConfig().getConfigurationSection("cosmetics");
        if (all != null) for (String id : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(id);
            if (s != null && cat.equals(s.getString("type"))) ids.add(id);
        }
        return ids;
    }

    private ItemStack coinsItem(PlayerData d) {
        return item(Material.GOLD_INGOT, "&6" + d.coins + " pieces", List.of("&7Gagne des pieces en jouant", "&7et avec les quetes du jour (/quetes)."), false);
    }

    /** Accueil de la boutique : une entrée par catégorie de cosmétiques. */
    private void openShop(Player p) {
        PlayerData d = data(p);
        Menu holder = new Menu("shop");
        Inventory inv = Bukkit.createInventory(holder, 27, c("Boutique - " + d.coins + " pieces"));
        holder.inventory = inv;
        int[] slots = {11, 12, 13, 14, 15};
        for (int i = 0; i < CATEGORIES.length; i++) {
            String cat = CATEGORIES[i];
            List<String> ids = cosmeticsOf(cat);
            ids.removeIf(id -> !d.owned.contains(id) && !onSale(cosmetic(id)));
            int owned = 0;
            for (String id : ids) if (d.owned.contains(id)) owned++;
            ConfigurationSection eq = cosmetic(d.equipped.get(cat));
            inv.setItem(slots[i], item(CATEGORY_ICONS[i], CATEGORY_NAMES[i], List.of(
                    CATEGORY_HELP[i],
                    cat.equals("title") ? "&8Dans le chat partout, au-dessus de la tete au hub" : "&8Actifs au hub seulement",
                    "",
                    "&7Achetes : &f" + owned + "&7/&f" + ids.size(),
                    "&7Equipe : " + (eq == null ? "&8aucun" : eq.getString("name", "?")),
                    "",
                    "&eClic pour voir"), eq != null));
            holder.actions.put(slots[i], "shopcat:" + cat);
        }
        inv.setItem(4, coinsItem(d));
        inv.setItem(18, item(Material.ARROW, "&eMon profil", List.of(), false));
        holder.actions.put(18, "profile:" + d.uuid);
        inv.setItem(22, item(Material.BARRIER, "&cFermer", List.of(), false));
        holder.actions.put(22, "close");
        openLater(p, inv);
    }

    /** Page d'une catégorie : ses cosmétiques (acheter, équiper, retirer). */
    private void openShopCategory(Player p, String cat) {
        int ci = java.util.Arrays.asList(CATEGORIES).indexOf(cat);
        if (ci < 0) {
            openShop(p);
            return;
        }
        PlayerData d = data(p);
        Menu holder = new Menu("shopcat");
        Inventory inv = Bukkit.createInventory(holder, 54, c(CATEGORY_NAMES[ci].replaceAll("&.", "") + " - " + d.coins + " pieces"));
        holder.inventory = inv;
        inv.setItem(4, item(CATEGORY_ICONS[ci], CATEGORY_NAMES[ci], List.of(CATEGORY_HELP[ci]), false));
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int i = 0;
        for (String id : cosmeticsOf(cat)) {
            if (i >= slots.length) break;
            ConfigurationSection s = cosmetic(id);
            boolean owned = d.owned.contains(id);
            boolean sale = onSale(s);
            if (!owned && !sale) continue; // saisonnier hors saison : caché
            boolean eq = id.equals(d.equipped.get(cat));
            int price = s.getInt("price", 0);
            List<String> lore = new ArrayList<>();
            if (cat.equals("title")) lore.add("&7Apercu : &8[" + s.getString("text", "") + "&8] &f" + p.getName());
            if (cat.equals("join")) lore.add("&7" + s.getString("message", "").replace("%player%", p.getName()));
            if (cat.equals("gadget")) for (String line : s.getStringList("description")) lore.add("&7" + line);
            String until = s.getString("available-until");
            if (until != null) {
                String[] ymd = until.split("-");
                lore.add(sale && ymd.length == 3 ? "&6Saisonnier &7- en vente jusqu'au " + ymd[2] + "/" + ymd[1] : "&8Edition limitee (plus en vente)");
            }
            lore.add("");
            if (eq) lore.add("&aEquipe &7- clic pour retirer");
            else if (owned) lore.add("&eAchete &7- clic pour equiper");
            else lore.add((d.coins >= price ? "&6" : "&c") + "Prix : " + price + " pieces &7- clic pour acheter");
            Material m = Material.matchMaterial(s.getString("icon", "PAPER"));
            inv.setItem(slots[i], item(m == null || !m.isItem() ? Material.PAPER : m, s.getString("name", id), lore, eq));
            holder.actions.put(slots[i++], "cos:" + id);
        }
        inv.setItem(45, item(Material.ARROW, "&eRetour aux categories", List.of(), false));
        holder.actions.put(45, "shop");
        inv.setItem(49, item(Material.BARRIER, "&cFermer", List.of(), false));
        holder.actions.put(49, "close");
        inv.setItem(53, coinsItem(d));
        openLater(p, inv);
    }

    private void shopClick(Player p, String id) {
        ConfigurationSection s = cosmetic(id);
        if (s == null) return;
        PlayerData d = data(p);
        String cat = s.getString("type", "trail");
        if (d.owned.contains(id)) {
            if (id.equals(d.equipped.get(cat))) {
                d.equipped.remove(cat);
                msg(p, PREFIX + "&7Retire : " + s.getString("name", id));
            } else {
                d.equipped.put(cat, id);
                msg(p, PREFIX + "&aEquipe : " + s.getString("name", id));
            }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
        } else {
            int price = s.getInt("price", 0);
            if (!onSale(s)) {
                msg(p, PREFIX + "&cCe cosmetique n'est plus en vente.");
                return;
            }
            if (d.coins < price) {
                msg(p, PREFIX + "&cIl te manque " + (price - d.coins) + " pieces.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                return;
            }
            d.coins -= price;
            d.owned.add(id);
            d.equipped.put(cat, id);
            msg(p, PREFIX + "&aAchete et equipe : " + s.getString("name", id) + " &7(-" + price + " pieces)");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        markDirty(d);
        if (cat.equals("hat")) {
            removeHat(p);
            applyHat(p);
        }
        if (cat.equals("gadget")) {
            removeGadget(p);
            applyGadget(p);
        }
        if (cat.equals("title")) updateTitleTag(p);
        openShopCategory(p, cat);
    }

    private void openQuests(Player p) {
        PlayerData d = data(p);
        ensureQuests(d);
        Menu holder = new Menu("quests");
        Inventory inv = Bukkit.createInventory(holder, 27, c("Quetes du jour"));
        holder.inventory = inv;
        int[] slots = {11, 13, 15, 10, 16, 12, 14};
        int i = 0;
        for (PlayerData.Quest q : d.quests) {
            if (i >= slots.length) break;
            inv.setItem(slots[i++], item(q.done ? Material.LIME_DYE : Material.PAPER, (q.done ? "&a" : "&e") + q.text, List.of(
                    bar(q.progress, q.target, 20) + " &7" + q.progress + "/" + q.target,
                    "&7Recompense : &6" + q.coins + " pieces &7et &b" + q.xp + " xp",
                    q.done ? "&aTerminee !" : "&7En cours"), q.done));
        }
        LocalDateTime now = LocalDateTime.now();
        long minutes = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMinutes();
        inv.setItem(22, item(Material.CLOCK, "&bNouvelles quetes dans " + (minutes / 60) + "h" + String.format("%02d", minutes % 60), List.of("&7Les quetes changent a minuit."), false));
        openLater(p, inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        String action = menu.actions.get(e.getRawSlot());
        if (action == null) return;
        int colon = action.indexOf(':');
        String key = colon < 0 ? action : action.substring(0, colon);
        String arg = colon < 0 ? "" : action.substring(colon + 1);
        switch (key) {
            case "close" -> p.closeInventory();
            case "cos" -> shopClick(p, arg);
            case "shop" -> openShop(p);
            case "shopcat" -> openShopCategory(p, arg);
            case "quests" -> openQuests(p);
            case "players" -> openPlayers(p);
            case "toplevel" -> openTopLevel(p);
            case "profile" -> {
                UUID id = UUID.fromString(arg);
                Player t = Bukkit.getPlayer(id);
                // menu « Joueurs en ligne » : shift-clic = regarder sa partie
                if (menu.kind.equals("players") && e.isShiftClick() && t != null && !t.equals(p) && gameOfPlayer(t) != null) {
                    spectatePlayer(p, t);
                    return;
                }
                PlayerData d = players.get(id);
                if (d != null) openProfile(p, d);
            }
            case "party" -> openPartyMenu(p);
            case "pinvite" -> openInviteMenu(p);
            case "pinv" -> {
                partyCommand(p, new String[]{"invite", nameOf(UUID.fromString(arg))});
                openPartyMenu(p);
            }
            case "pcancel" -> {
                UUID target = UUID.fromString(arg);
                Invite inv = invites.get(target);
                if (inv != null && inv.from.equals(p.getUniqueId())) {
                    invites.remove(target);
                    msg(p, PARTY + "&7Invitation a " + nameOf(target) + " annulee.");
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) msg(t, PARTY + "&7" + p.getName() + " a annule son invitation.");
                }
                openPartyMenu(p);
            }
            case "paccept", "pdeny" -> {
                partyCommand(p, new String[]{e.isRightClick() || key.equals("pdeny") ? "deny" : "accept"});
                openPartyMenu(p);
            }
            case "pleave" -> {
                Party party = partyOf.get(p.getUniqueId());
                if (party != null && party.leader.equals(p.getUniqueId()) && e.isShiftClick()) partyCommand(p, new String[]{"disband"});
                else partyCommand(p, new String[]{"leave"});
                p.closeInventory();
            }
            case "pmember" -> memberClick(p, UUID.fromString(arg), e.isShiftClick());
            case "pspec" -> {
                GameInfo g = game(arg);
                if (g == null) return;
                p.closeInventory();
                spectate(p, g);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ groupes

    private void partyBroadcast(Party party, String legacy) {
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) msg(m, PARTY + legacy);
        }
    }

    private void removeFromParty(Party party, UUID id, String announce) {
        party.members.remove(id);
        partyOf.remove(id);
        offlineSince.remove(id);
        if (announce != null) partyBroadcast(party, announce);
        if (party.members.size() <= 1) {
            for (UUID m : new ArrayList<>(party.members)) partyOf.remove(m);
            partyBroadcast(party, "&7Le groupe est dissous.");
            party.members.clear();
            return;
        }
        if (party.leader.equals(id)) {
            party.leader = party.members.get(0);
            PlayerData d = players.get(party.leader);
            partyBroadcast(party, "&e" + (d == null ? "?" : d.name) + " &7est le nouveau chef du groupe.");
        }
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        PlayerData d = players.get(id);
        return d == null ? "?" : d.name;
    }

    /** Où se trouve un joueur : hub, jeu (avec son statut) ou déconnecté. */
    private String whereText(UUID id) {
        Player m = Bukkit.getPlayer(id);
        if (m == null) return "&8Deconnecte";
        if (inLobby(m)) return "&aAu hub";
        GameInfo g = gameForWorld(m.getWorld().getName());
        return g == null ? "&7Monde : " + m.getWorld().getName() : "&7En jeu : " + g.name;
    }

    /** Jeu dans lequel un membre peut être regardé (null s'il n'est dans aucun jeu avec mode spectateur). */
    private GameInfo spectatableGameOf(UUID id) {
        Player m = Bukkit.getPlayer(id);
        if (m == null) return null;
        GameInfo g = gameOfPlayer(m);
        return g == null || g.spectate == null || g.spectate.isBlank() ? null : g;
    }

    /**
     * Jeu dans lequel se trouve un joueur (null au hub ou hors des mondes du registre).
     * Plusieurs jeux peuvent partager un monde (duels et ffa dans « duels ») : pour ces deux-là
     * on regarde la position (arène FFA d'EemiDuels centrée en z -300, duels en z 0, sumo en z 300),
     * sinon le dernier jeu rejoint par /eemi join.
     */
    private GameInfo gameOfPlayer(Player t) {
        if (inLobby(t)) return null;
        String world = t.getWorld().getName();
        List<GameInfo> found = new ArrayList<>();
        for (GameInfo g : games.values()) if (g.hasWorld(world)) found.add(g);
        if (found.size() <= 1) return found.isEmpty() ? null : found.get(0);
        GameInfo duels = game("duels"), ffa = game("ffa");
        if (duels != null && ffa != null && found.contains(duels) && found.contains(ffa))
            return t.getLocation().getZ() < -150 ? ffa : duels;
        JoinMark j = lastJoin.get(t.getUniqueId());
        if (j != null) for (GameInfo g : found) if (g.id.equals(j.game)) return g;
        return found.get(0);
    }

    /**
     * Regarder la partie d'un joueur précis : lance la commande spectate de son jeu pour p.
     * Pour les duels, /duel spectate <joueur> (EemiDuels 1.1.1+) vise le duel de la cible.
     */
    private void spectatePlayer(Player p, Player target) {
        if (target.equals(p)) {
            msg(p, PREFIX + "&cTu ne peux pas te regarder toi-meme.");
            return;
        }
        GameInfo own = gameOfPlayer(p);
        if (own != null && p.getGameMode() != GameMode.SPECTATOR) {
            msg(p, PREFIX + "&cTu es en partie (" + own.name + "&c) : fais /lobby d'abord.");
            return;
        }
        GameInfo g = gameOfPlayer(target);
        if (g == null || target.hasMetadata("vanished") && !p.hasMetadata("vanished")) {
            msg(p, PREFIX + "&c" + target.getName() + " n'est dans aucune partie" + (inLobby(target) ? " (au hub)." : "."));
            return;
        }
        if (g.spectate == null || g.spectate.isBlank()) {
            msg(p, PREFIX + "&c" + g.name + " &cn'a pas de mode spectateur.");
            return;
        }
        p.closeInventory();
        msg(p, PREFIX + "&7Tu regardes la partie de &f" + target.getName() + " &7(" + g.name + "&7).");
        if (!g.id.equals("duels")) {
            spectate(p, g);
            return;
        }
        String name = target.getName();
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline()) p.performCommand("duel spectate " + name);
        });
    }

    /** Menu du groupe : membres, invitations envoyées et reçue, parties à regarder. */
    private void openPartyMenu(Player p) {
        Menu holder = new Menu("party");
        Inventory inv = Bukkit.createInventory(holder, 54, c("Groupe"));
        holder.inventory = inv;
        Party party = partyOf.get(p.getUniqueId());
        boolean leader = party == null || party.leader.equals(p.getUniqueId());
        long now = System.currentTimeMillis();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // ligne 0 : état du groupe
        inv.setItem(4, item(Material.LEAD, party == null ? "&7Tu n'es dans aucun groupe"
                : "&9Groupe de " + nameOf(party.leader) + " &7(" + party.members.size() + "/" + PARTY_MAX + ")", List.of(
                "&7Le chef lance les parties pour tout le groupe",
                "&7(moustache, PNJ, /jouer).",
                "&f/pc <message> &7: parler au groupe"), false));

        // ligne 1 : membres
        inv.setItem(9, item(Material.BLUE_STAINED_GLASS_PANE, "&9Membres", List.of("&7Clic sur un membre en jeu :", "&7regarder sa partie"), false));
        if (party == null) {
            inv.setItem(10, head(p.getUniqueId(), "&f" + p.getName(), List.of("&7Seul pour l'instant.", "&7Invite des joueurs avec le bouton en bas.")));
        } else {
            int slot = 10;
            for (UUID id : party.members) {
                if (slot > 17) break;
                List<String> lore = new ArrayList<>();
                if (id.equals(party.leader)) lore.add("&6Chef du groupe");
                lore.add(whereText(id));
                PlayerData d = players.get(id);
                if (d != null) lore.add("&7Niveau " + levelColor(d.level) + d.level);
                lore.add("");
                if (spectatableGameOf(id) != null && !id.equals(p.getUniqueId())) lore.add("&eClic : regarder sa partie");
                if (party.leader.equals(p.getUniqueId()) && !id.equals(p.getUniqueId())) lore.add("&cShift-clic : exclure");
                inv.setItem(slot, head(id, (Bukkit.getPlayer(id) != null ? "&f" : "&8") + nameOf(id), lore));
                holder.actions.put(slot++, "pmember:" + id);
            }
        }

        // ligne 2 : invitations envoyées
        inv.setItem(18, item(Material.LIME_STAINED_GLASS_PANE, "&aInvitations envoyees", List.of("&7Clic sur une invitation : l'annuler"), false));
        int slot = 19;
        for (Map.Entry<UUID, Invite> en : invites.entrySet()) {
            if (!en.getValue().from.equals(p.getUniqueId())) continue;
            long left = Math.max(0, (INVITE_MS - (now - en.getValue().time)) / 1000);
            if (slot > 26 || left == 0) continue;
            inv.setItem(slot, head(en.getKey(), "&f" + nameOf(en.getKey()), List.of("&7En attente de reponse", "&7Expire dans &e" + left + " s", "", "&cClic : annuler")));
            holder.actions.put(slot++, "pcancel:" + en.getKey());
        }
        if (slot == 19) inv.setItem(19, item(Material.PAPER, "&7Aucune invitation en attente", List.of(), false));

        // ligne 3 : parties à regarder
        inv.setItem(27, item(Material.ORANGE_STAINED_GLASS_PANE, "&6Regarder une partie", List.of("&7Parties en cours avec", "&7mode spectateur"), false));
        slot = 28;
        for (GameInfo g : games.values()) {
            if (slot > 35) break;
            if (g.spectate == null || g.spectate.isBlank() || !g.fresh(expireMs)) continue;
            if (!"playing".equals(g.state) && !"starting".equals(g.state) && !"ending".equals(g.state)) continue;
            inv.setItem(slot, item(g.icon, g.name, List.of(statusText(g), "", "&eClic : regarder"), false));
            holder.actions.put(slot++, "pspec:" + g.id);
        }
        if (slot == 28) inv.setItem(28, item(Material.ENDER_EYE, "&7Aucune partie en cours", List.of("&7Reviens quand une partie est lancee."), false));

        // barre du bas
        Invite received = invites.get(p.getUniqueId());
        if (received != null && now - received.time <= INVITE_MS) {
            inv.setItem(45, head(received.from, "&eInvitation de " + nameOf(received.from), List.of(
                    "&7Expire dans &e" + (INVITE_MS - (now - received.time)) / 1000 + " s", "",
                    "&aClic gauche : accepter", "&cClic droit : refuser")));
            holder.actions.put(45, "paccept");
        }
        if (leader) {
            inv.setItem(47, item(Material.WRITABLE_BOOK, "&aInviter un joueur", List.of("&7Choisis parmi les joueurs connectes"), false));
            holder.actions.put(47, "pinvite");
        }
        inv.setItem(49, item(Material.BARRIER, "&cFermer", List.of(), false));
        holder.actions.put(49, "close");
        if (party != null) {
            inv.setItem(51, item(Material.RED_DYE, "&cQuitter le groupe", party.leader.equals(p.getUniqueId())
                    ? List.of("&7Clic : quitter (un autre devient chef)", "&4Shift-clic : dissoudre le groupe") : List.of("&7Clic : quitter"), false));
            holder.actions.put(51, "pleave");
        }
        openLater(p, inv);
    }

    /** Choix d'un joueur à inviter : connectés, hors groupe, pas en vanish. */
    private void openInviteMenu(Player p) {
        Menu holder = new Menu("pinvite");
        Inventory inv = Bukkit.createInventory(holder, 54, c("Inviter un joueur"));
        holder.inventory = inv;
        int slot = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            if (o.equals(p) || o.hasMetadata("vanished") || partyOf.containsKey(o.getUniqueId())) continue;
            Invite pending = invites.get(o.getUniqueId());
            boolean mine = pending != null && pending.from.equals(p.getUniqueId());
            PlayerData d = data(o);
            inv.setItem(slot, head(o.getUniqueId(), "&f" + o.getName(), List.of(
                    "&7Niveau " + levelColor(d.level) + d.level, whereText(o.getUniqueId()), "",
                    mine ? "&7Deja invite (clic : renvoyer)" : "&aClic : inviter")));
            holder.actions.put(slot++, "pinv:" + o.getUniqueId());
        }
        if (slot == 0) inv.setItem(22, item(Material.PAPER, "&7Personne a inviter", List.of("&7Tous les joueurs connectes", "&7sont deja dans un groupe."), false));
        inv.setItem(48, item(Material.ARROW, "&eRetour au groupe", List.of(), false));
        holder.actions.put(48, "party");
        inv.setItem(49, item(Material.BARRIER, "&cFermer", List.of(), false));
        holder.actions.put(49, "close");
        openLater(p, inv);
    }

    private void memberClick(Player p, UUID id, boolean shift) {
        Party party = partyOf.get(p.getUniqueId());
        if (party == null || !party.members.contains(id) || id.equals(p.getUniqueId())) return;
        if (shift && party.leader.equals(p.getUniqueId())) {
            partyCommand(p, new String[]{"kick", nameOf(id)});
            openPartyMenu(p);
            return;
        }
        Player t = Bukkit.getPlayer(id);
        if (t == null || spectatableGameOf(id) == null) {
            msg(p, PARTY + "&7" + nameOf(id) + " n'est dans aucune partie a regarder.");
            return;
        }
        spectatePlayer(p, t);
    }

    private void partyCommand(Player p, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        Party party = partyOf.get(p.getUniqueId());
        switch (sub) {
            case "invite", "inviter" -> {
                if (args.length < 2) {
                    msg(p, PARTY + "&c/party invite <joueur>");
                    return;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null || t.equals(p)) {
                    msg(p, PARTY + "&cJoueur introuvable.");
                    return;
                }
                if (party != null && !party.leader.equals(p.getUniqueId())) {
                    msg(p, PARTY + "&cSeul le chef peut inviter.");
                    return;
                }
                if (partyOf.containsKey(t.getUniqueId())) {
                    msg(p, PARTY + "&c" + t.getName() + " est deja dans un groupe.");
                    return;
                }
                if (party != null && party.members.size() >= PARTY_MAX) {
                    msg(p, PARTY + "&cGroupe complet (" + PARTY_MAX + " max).");
                    return;
                }
                invites.put(t.getUniqueId(), new Invite(p.getUniqueId(), System.currentTimeMillis()));
                msg(p, PARTY + "&7Invitation envoyee a &f" + t.getName() + "&7 (60 s).");
                t.sendMessage(c(PARTY + "&f" + p.getName() + " &7t'invite dans son groupe. ")
                        .append(button("[ACCEPTER]", NamedTextColor.GREEN, "/party accept", "Rejoindre le groupe"))
                        .append(Component.text(" "))
                        .append(button("[REFUSER]", NamedTextColor.RED, "/party deny", "Refuser")));
                t.playSound(t.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
            }
            case "accept", "accepter" -> {
                Invite inv = invites.remove(p.getUniqueId());
                if (inv == null || System.currentTimeMillis() - inv.time > INVITE_MS) {
                    msg(p, PARTY + "&cAucune invitation en cours.");
                    return;
                }
                if (party != null) {
                    msg(p, PARTY + "&cQuitte d'abord ton groupe (/party leave).");
                    return;
                }
                Player from = Bukkit.getPlayer(inv.from);
                if (from == null) {
                    msg(p, PARTY + "&cLe joueur qui t'a invite n'est plus connecte.");
                    return;
                }
                Party target = partyOf.get(inv.from);
                if (target == null) {
                    target = new Party();
                    target.leader = inv.from;
                    target.members.add(inv.from);
                    partyOf.put(inv.from, target);
                } else if (!target.leader.equals(inv.from)) {
                    msg(p, PARTY + "&cCette invitation n'est plus valable.");
                    return;
                }
                if (target.members.size() >= PARTY_MAX) {
                    msg(p, PARTY + "&cGroupe complet.");
                    return;
                }
                target.members.add(p.getUniqueId());
                partyOf.put(p.getUniqueId(), target);
                partyBroadcast(target, "&a" + p.getName() + " a rejoint le groupe.");
            }
            case "deny", "refuser" -> {
                Invite inv = invites.remove(p.getUniqueId());
                if (inv == null) {
                    msg(p, PARTY + "&cAucune invitation en cours.");
                    return;
                }
                msg(p, PARTY + "&7Invitation refusee.");
                Player from = Bukkit.getPlayer(inv.from);
                if (from != null) msg(from, PARTY + "&c" + p.getName() + " a refuse ton invitation.");
            }
            case "leave", "quitter" -> {
                if (party == null) {
                    msg(p, PARTY + "&cTu n'es dans aucun groupe.");
                    return;
                }
                msg(p, PARTY + "&7Tu as quitte le groupe.");
                removeFromParty(party, p.getUniqueId(), "&7" + p.getName() + " a quitte le groupe.");
            }
            case "kick", "exclure" -> {
                if (party == null || !party.leader.equals(p.getUniqueId())) {
                    msg(p, PARTY + "&cSeul le chef peut exclure.");
                    return;
                }
                if (args.length < 2) {
                    msg(p, PARTY + "&c/party kick <joueur>");
                    return;
                }
                UUID target = null;
                for (UUID id : party.members) if (nameOf(id).equalsIgnoreCase(args[1])) target = id;
                if (target == null || target.equals(p.getUniqueId())) {
                    msg(p, PARTY + "&cCe joueur n'est pas dans ton groupe.");
                    return;
                }
                Player t = Bukkit.getPlayer(target);
                if (t != null) msg(t, PARTY + "&cTu as ete exclu du groupe.");
                removeFromParty(party, target, "&7" + nameOf(target) + " a ete exclu du groupe.");
            }
            case "disband", "dissoudre" -> {
                if (party == null || !party.leader.equals(p.getUniqueId())) {
                    msg(p, PARTY + "&cSeul le chef peut dissoudre le groupe.");
                    return;
                }
                partyBroadcast(party, "&7Le groupe a ete dissous par " + p.getName() + ".");
                for (UUID id : party.members) {
                    partyOf.remove(id);
                    offlineSince.remove(id);
                }
                party.members.clear();
            }
            case "list", "liste" -> {
                if (party == null) {
                    msg(p, PARTY + "&7Tu n'es dans aucun groupe. &f/party invite <joueur>");
                    return;
                }
                msg(p, PARTY + "&eMembres (" + party.members.size() + "/" + PARTY_MAX + ") :");
                for (UUID id : party.members) {
                    boolean on = Bukkit.getPlayer(id) != null;
                    msg(p, " " + (on ? "&a" : "&8") + nameOf(id) + (id.equals(party.leader) ? " &6(chef)" : "") + (on ? "" : " &8(deconnecte)"));
                }
            }
            case "chat" -> partyChat(p, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            case "menu" -> openPartyMenu(p);
            default -> {
                msg(p, PARTY + "&eCommandes du groupe :");
                msg(p, "&f/party &7- menu du groupe (membres, invitations, regarder une partie)");
                msg(p, "&f/party invite <joueur> &7- inviter (le chef)");
                msg(p, "&f/party accept &7| &f/party deny &7- repondre a une invitation");
                msg(p, "&f/party leave &7- quitter | &f/party list &7- membres");
                msg(p, "&f/party kick <joueur> &7| &f/party disband &7- chef");
                msg(p, "&f/pc <message> &7- parler au groupe");
                msg(p, "&7Le chef lance les parties pour tout le groupe (/eemi join, boussole, /jouer).");
            }
        }
    }

    private void partyChat(Player p, String text) {
        Party party = partyOf.get(p.getUniqueId());
        if (party == null) {
            msg(p, PARTY + "&cTu n'es dans aucun groupe.");
            return;
        }
        if (text.isBlank()) return;
        Component line = c(PARTY + "&b" + p.getName() + "&7: ").append(Component.text(text, NamedTextColor.WHITE));
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) m.sendMessage(line);
        }
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("eemi")) return eemiCommand(sender, args);
        if (!(sender instanceof Player p)) {
            if (name.equals("profil") && args.length > 0) {
                PlayerData d = dataByName(args[0]);
                if (d == null) msg(sender, PREFIX + "&cJoueur inconnu.");
                else profileText(sender, d);
                return true;
            }
            msg(sender, "&cCommande reservee aux joueurs.");
            return true;
        }
        switch (name) {
            case "party" -> partyCommand(p, args);
            case "pc" -> partyChat(p, String.join(" ", args));
            case "jouer" -> quickPlay(p);
            case "profil" -> {
                PlayerData d = args.length > 0 ? dataByName(args[0]) : data(p);
                if (d == null) msg(p, PREFIX + "&cJoueur inconnu.");
                else openProfile(p, d);
            }
            case "boutique" -> openShop(p);
            case "quetes" -> openQuests(p);
            case "spectate" -> {
                if (args.length < 1) {
                    msg(p, PREFIX + "&c/spectate <joueur> &7- regarder la partie d'un joueur");
                    return true;
                }
                Player t = Bukkit.getPlayer(args[0]);
                if (t == null || t.hasMetadata("vanished") && !p.hasMetadata("vanished")) msg(p, PREFIX + "&c" + args[0] + " n'est pas connecte.");
                else spectatePlayer(p, t);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean eemiCommand(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean trusted = sender instanceof ConsoleCommandSender || sender.isOp();
        switch (sub) {
            case "status" -> {
                // eemi status <jeu> <state> <waiting> <playing> <max> : console/op, silencieux
                if (!trusted || args.length < 6) return true;
                String state = args[2].toLowerCase(Locale.ROOT);
                if (!Set.of("waiting", "starting", "playing", "ending", "nobody").contains(state)) return true;
                updateStatus(args[1], state, parseInt(args[3], 0), parseInt(args[4], 0), parseInt(args[5], 0));
                return true;
            }
            case "reward" -> {
                // eemi reward <pseudo> <jeu> <play|win|kill> : console/op, silencieux
                if (!trusted || args.length < 4) return true;
                String type = args[3].toLowerCase(Locale.ROOT);
                if (!Set.of("play", "win", "kill").contains(type)) return true;
                PlayerData d = dataByName(args[1]);
                if (d == null) {
                    OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[1]);
                    if (off == null) return true;
                    d = players.computeIfAbsent(off.getUniqueId(), PlayerData::new);
                    d.name = off.getName() == null ? args[1] : off.getName();
                    nameIndex.put(d.name.toLowerCase(Locale.ROOT), d.uuid);
                }
                reward(d, gameOrCreate(args[2]), type);
                return true;
            }
            case "join", "rejoindre" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "&cCommande reservee aux joueurs.");
                    return true;
                }
                GameInfo g = args.length > 1 ? game(args[1]) : null;
                if (g == null) {
                    msg(p, PREFIX + "&cJeu inconnu. &7Jeux : " + String.join(", ", games.keySet()));
                    return true;
                }
                joinGame(p, g);
                return true;
            }
            case "spectate", "regarder" -> {
                if (!(sender instanceof Player p)) return true;
                GameInfo g = args.length > 1 ? game(args[1]) : null;
                if (g == null) {
                    // /eemi spectate <joueur> marche aussi
                    Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                    if (t != null && (!t.hasMetadata("vanished") || p.hasMetadata("vanished"))) spectatePlayer(p, t);
                    else msg(p, PREFIX + "&cJeu inconnu. &7/eemi spectate <jeu> ou /spectate <joueur>");
                    return true;
                }
                spectate(p, g);
                return true;
            }
            case "suivre" -> {
                // [SUIVRE] d'un membre de groupe : rejoint le jeu où se trouve son chef
                if (!(sender instanceof Player p) || args.length < 2) return true;
                Player leader = Bukkit.getPlayerExact(args[1]);
                Party party = partyOf.get(p.getUniqueId());
                if (leader == null || party == null || !party.members.contains(leader.getUniqueId())) {
                    msg(p, PARTY + "&cCe joueur n'est pas dans ton groupe.");
                    return true;
                }
                GameInfo g = gameForWorld(leader.getWorld().getName());
                JoinMark j = lastJoin.get(leader.getUniqueId());
                if (g == null && j != null) g = game(j.game);
                if (g == null) {
                    msg(p, PARTY + "&c" + leader.getName() + " n'est dans aucun jeu.");
                    return true;
                }
                LeaderCmd lc = leaderCmd.get(leader.getUniqueId());
                if (lc != null && lc.game.equals(g.id) && System.currentTimeMillis() - lc.time < 30 * 60_000L) {
                    String cmd = lc.cmd;
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (p.isOnline()) p.performCommand(cmd);
                    });
                } else runJoin(p, g);
                return true;
            }
            case "appels", "calls" -> {
                if (!(sender instanceof Player p)) return true;
                PlayerData d = data(p);
                d.calls = !d.calls;
                markDirty(d);
                msg(p, PREFIX + (d.calls ? "&aAppels a la partie actives." : "&7Appels a la partie coupes. &f/eemi appels &7pour les remettre."));
                return true;
            }
            case "top", "classement" -> {
                if (args.length < 2) {
                    msg(sender, PREFIX + "&c/eemi top <jeu|level> [wins|kills|plays]");
                    return true;
                }
                showTop(sender, args[1], args.length > 2 ? args[2] : "wins");
                return true;
            }
            case "maintenance" -> {
                maintenanceCommand(sender, args);
                return true;
            }
            case "jeux", "games" -> {
                msg(sender, PREFIX + "&eJeux :");
                for (GameInfo g : games.values()) msg(sender, " &7" + g.id + " &8- " + g.name + " &8: " + statusText(g));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("eemicore.admin")) {
                    msg(sender, "&cPermission refusee.");
                    return true;
                }
                loadSettings();
                recomputeTops();
                startStatusFile();
                msg(sender, PREFIX + "&aConfig rechargee (" + games.size() + " jeux).");
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("eemicore.admin")) {
                    msg(sender, "&cPermission refusee.");
                    return true;
                }
                if (args.length < 4) {
                    msg(sender, PREFIX + "&c/eemi give <joueur> <coins|xp> <n>");
                    return true;
                }
                PlayerData d = dataByName(args[1]);
                if (d == null) {
                    msg(sender, PREFIX + "&cJoueur inconnu.");
                    return true;
                }
                long n;
                try {
                    n = Long.parseLong(args[3]);
                } catch (NumberFormatException e) {
                    msg(sender, PREFIX + "&cNombre invalide.");
                    return true;
                }
                if (args[2].equalsIgnoreCase("xp")) giveXp(d, n);
                else d.coins = Math.max(0, d.coins + n);
                markDirty(d);
                msg(sender, PREFIX + "&a" + d.name + " : " + d.coins + " pieces, niveau " + d.level + ", " + d.xp + " xp.");
                Player t = Bukkit.getPlayer(d.uuid);
                if (t != null && !args[2].equalsIgnoreCase("xp") && n > 0) msg(t, PREFIX + "&6+" + n + " pieces");
                return true;
            }
            default -> {
                msg(sender, PREFIX + "&eCommandes :");
                msg(sender, "&f/eemi join <jeu> &7- rejoindre un jeu (avec ton groupe si tu es chef)");
                msg(sender, "&f/eemi spectate <jeu> &7- regarder une partie");
                msg(sender, "&f/spectate <joueur> &7- regarder la partie d'un joueur");
                msg(sender, "&f/jouer &7- rejoindre la partie la plus remplie");
                msg(sender, "&f/eemi jeux &7- etat des jeux");
                msg(sender, "&f/eemi top <jeu|level> [wins|kills|plays] &7- classements");
                msg(sender, "&f/eemi appels &7- couper/remettre les appels a la partie");
                msg(sender, "&f/party &7- groupes | &f/profil &7| &f/boutique &7| &f/quetes");
                if (sender.hasPermission("eemicore.admin")) {
                    msg(sender, "&c/eemi reload &7| &c/eemi give <joueur> <coins|xp> <n>");
                }
                if (sender.hasPermission("eemicore.maintenance")) {
                    msg(sender, "&c/eemi maintenance <jeu> on [raison...] &7| &c/eemi maintenance <jeu> off &7| &c/eemi maintenance list");
                }
                if (sender.hasPermission("eemicore.admin")) {
                    msg(sender, "&8eemi status <jeu> <state> <waiting> <playing> <max> | eemi reward <pseudo> <jeu> <play|win|kill>");
                }
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (name.equals("eemi")) {
            if (args.length == 1) {
                options.addAll(List.of("join", "spectate", "jeux", "top", "appels", "help"));
                if (sender.hasPermission("eemicore.admin")) options.addAll(List.of("reload", "give"));
                if (sender.hasPermission("eemicore.maintenance")) options.add("maintenance");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("maintenance") && sender.hasPermission("eemicore.maintenance")) {
                options.add("list");
                options.addAll(games.keySet());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("maintenance") && sender.hasPermission("eemicore.maintenance")
                    && game(args[1]) != null) {
                options.addAll(maintenance.containsKey(game(args[1]).id) ? List.of("off", "on") : List.of("on", "off"));
            } else if (args.length == 2 && Set.of("join", "spectate", "top").contains(args[0].toLowerCase(Locale.ROOT))) {
                options.addAll(games.keySet());
                if (args[0].equalsIgnoreCase("top")) options.add("level");
                if (args[0].equalsIgnoreCase("spectate"))
                    for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(sender) && !p.hasMetadata("vanished")) options.add(p.getName());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("top")) {
                options.addAll(List.of(STAT_TYPES));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                options.addAll(List.of("coins", "xp"));
            }
        } else if (name.equals("party")) {
            if (args.length == 1) options.addAll(List.of("menu", "invite", "accept", "deny", "leave", "kick", "disband", "list", "chat", "help"));
            else if (args.length == 2 && Set.of("invite", "kick").contains(args[0].toLowerCase(Locale.ROOT)))
                for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (name.equals("profil") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (name.equals("spectate") && args.length == 1) {
            boolean vanished = sender instanceof Player sp && sp.hasMetadata("vanished");
            for (Player p : Bukkit.getOnlinePlayers())
                if (!p.equals(sender) && (vanished || !p.hasMetadata("vanished"))) options.add(p.getName());
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return options;
    }
}
