/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.event.player.AsyncChatEvent
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.event.ClickEvent
 *  net.kyori.adventure.text.event.HoverEvent
 *  net.kyori.adventure.text.event.HoverEventSource
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  net.kyori.adventure.title.Title
 *  net.kyori.adventure.title.Title$Times
 *  org.bukkit.Bukkit
 *  org.bukkit.Color
 *  org.bukkit.FireworkEffect
 *  org.bukkit.FireworkEffect$Type
 *  org.bukkit.GameMode
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Particle
 *  org.bukkit.Particle$DustOptions
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Display$Billboard
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Firework
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.entity.Snowball
 *  org.bukkit.entity.TextDisplay
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityDamageEvent$DamageCause
 *  org.bukkit.event.entity.EntitySpawnEvent
 *  org.bukkit.event.entity.PlayerDeathEvent
 *  org.bukkit.event.entity.ProjectileHitEvent
 *  org.bukkit.event.inventory.ClickType
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerFishEvent
 *  org.bukkit.event.player.PlayerFishEvent$State
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerSwapHandItemsEvent
 *  org.bukkit.event.player.PlayerTeleportEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.FireworkMeta
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.inventory.meta.SkullMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.scheduler.BukkitTask
 *  org.bukkit.scoreboard.Team
 *  org.bukkit.util.Vector
 */
package fr.eemicraft.core;

import fr.eemicraft.core.CoreExpansion;
import fr.eemicraft.core.GameInfo;
import fr.eemicraft.core.PlayerData;
import fr.eemicraft.core.ui.NavFonds;
import fr.eemicraft.core.ui.NavUi;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

public final class EemiCore
extends JavaPlugin
implements Listener {
    private static final String PREFIX = "&6[EEMICRAFT] &r";
    private static final String PARTY = "&9[Groupe] &r";
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final int PARTY_MAX = 8;
    private static final long INVITE_MS = 60000L;
    private static final long OFFLINE_KICK_MS = 300000L;
    private static final String[] STAT_TYPES = new String[]{"wins", "kills", "plays"};
    private final Map<String, GameInfo> games = new LinkedHashMap<String, GameInfo>();
    private long expireMs = 600000L;
    private LobbyLive live;

    long expireMs() {
        return this.expireMs;
    }

    java.util.Collection<GameInfo> allGames() {
        return this.games.values();
    }

    List<TopEntry> topList(String key) {
        return this.tops.getOrDefault(key, List.of());
    }

    static String unitOf(String type, long v) {
        return EemiCore.unit(type, v);
    }

    static String normTypeOf(String t) {
        return EemiCore.normType(t);
    }
    private String lobbyWorld = "lobby";
    private final Map<String, Maintenance> maintenance = new LinkedHashMap<String, Maintenance>();
    private final Object statusFileLock = new Object();
    private long statusFileSeq;
    private long statusFileWritten;
    private BukkitTask statusFileTask;
    private final Map<UUID, PlayerData> players = new HashMap<UUID, PlayerData>();
    /** 1.9.4 : joueurs qui regardent la page « Saison » de la boutique (pour y revenir apres un achat). */
    private final Set<UUID> saisonView = new HashSet<UUID>();
    private final Map<String, UUID> nameIndex = new HashMap<String, UUID>();
    private final Set<UUID> dirty = new HashSet<UUID>();
    private File playersFolder;
    private final Map<UUID, LeaderCmd> leaderCmd = new HashMap<UUID, LeaderCmd>();
    private final Map<UUID, JoinMark> lastJoin = new HashMap<UUID, JoinMark>();
    private int callTimer;
    private final Map<UUID, Party> partyOf = new HashMap<UUID, Party>();
    private final Map<UUID, Invite> invites = new HashMap<UUID, Invite>();
    private final Map<UUID, Long> offlineSince = new HashMap<UUID, Long>();
    private volatile Map<String, List<TopEntry>> tops = new HashMap<String, List<TopEntry>>();
    private int topTimer;
    private NamespacedKey hatKey;
    private NamespacedKey gadgetKey;
    private NamespacedKey tagKey;
    private final Map<UUID, Location> lastTrailLoc = new HashMap<UUID, Location>();
    private float rainbowHue;
    private final TreeMap<Integer, String> levelColors = new TreeMap();
    private String lastDay = LocalDate.now().toString();
    private final Map<UUID, TextDisplay> titleTags = new HashMap<UUID, TextDisplay>();
    private final Map<UUID, Long> gadgetCooldown = new HashMap<UUID, Long>();
    private final Map<UUID, Long> noFallUntil = new HashMap<UUID, Long>();
    private final Set<UUID> jetpacking = new HashSet<UUID>();
    /* 1.6.0 : jour de Paris pour la recompense quotidienne et les bonus du jour. */
    private String lastParisDay = "";
    private static final String[] CATEGORIES = new String[]{"trail", "hat", "title", "join", "gadget"};
    private static final String[] CATEGORY_NAMES = new String[]{"&bTraînées", "&6Chapeaux", "&eTitres", "&aMessages d'arrivée", "&dGadgets"};
    private static final Material[] CATEGORY_ICONS = new Material[]{Material.BLAZE_POWDER, Material.LEATHER_HELMET, Material.NAME_TAG, Material.OAK_SIGN, Material.FIREWORK_ROCKET};
    private static final String[] CATEGORY_HELP = new String[]{"&7Des particules qui te suivent quand tu marches", "&7Un chapeau sur ta tête", "&7Un titre devant ton pseudo", "&7Annoncé à tous quand tu te connectes", "&7Un objet à utiliser au hub (clic droit)"};

    public void onEnable() {
        this.saveDefaultConfig();
        this.hatKey = new NamespacedKey((Plugin)this, "hat");
        this.gadgetKey = new NamespacedKey((Plugin)this, "gadget");
        this.tagKey = new NamespacedKey((Plugin)this, "tag");
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (!td.getPersistentDataContainer().has(this.tagKey, PersistentDataType.BYTE)) continue;
                td.remove();
            }
        }
        this.playersFolder = new File(this.getDataFolder(), "players");
        if (!this.playersFolder.exists() && !this.playersFolder.mkdirs()) {
            this.getLogger().warning("Impossible de creer " + String.valueOf(this.playersFolder));
        }
        this.loadSettings();
        this.loadMaintenance();
        this.loadAllPlayers();
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        for (String name : List.of("eemi", "party", "pc", "jouer", "profil", "boutique", "quetes", "spectate", "jeux")) {
            PluginCommand cmd = this.getCommand(name);
            if (cmd == null) continue;
            cmd.setExecutor((CommandExecutor)this);
            cmd.setTabCompleter((TabCompleter)this);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.onJoinData(p);
        }
        if (this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new CoreExpansion(this).register();
                this.getLogger().info("Placeholders %eemi_...% enregistres.");
            }
            catch (Throwable t) {
                this.getLogger().warning("PlaceholderAPI present mais expansion impossible : " + String.valueOf(t));
            }
        } else {
            this.getLogger().info("PlaceholderAPI absent : pas de placeholders.");
        }
        this.recomputeTops();
        this.live = new LobbyLive(this);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, () -> this.live.saveIfDirty(), 200L, 200L);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this::second, 20L, 20L);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this::trails, 3L, 3L);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this::hatsCheck, 40L, 40L);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this::gadgetsCheck, 45L, 40L);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this::titleTagsTick, 1L, 1L);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this::noFlightCheck, 10L, 10L);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                this.updateTitleTag(p);
            }
        }, 20L, 20L);
        long save = (long)Math.max(10, this.getConfig().getInt("save-interval", 60)) * 20L;
        this.getServer().getScheduler().runTaskTimer((Plugin)this, () -> this.saveDirty(true), save, save);
        this.startStatusFile();
        new LobbyGuard(this).start();
        this.lastParisDay = this.parisDay();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.checkDaily(p), 40L);
        }
        this.getLogger().info(this.games.size() + " jeux, " + this.players.size() + " joueurs connus, " + this.maintenance.size() + " en maintenance.");
    }

    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.removeHat(p);
            this.removeGadget(p);
            this.removeTitleTag(p);
        }
        this.saveDirty(false);
        if (this.live != null) {
            this.live.saveIfDirty();
        }
    }

    private void loadSettings() {
        this.reloadConfig();
        // DA des menus du lobby (fonds dessines + icones du pack) : seulement si le pack qui les contient est en ligne
        UI = this.getConfig().getBoolean("lobby-ui.enabled", false);
        // 1.8.0 : menus au style Refuge (police eemicraft:nav du pack), "classique" = anciens menus
        NAV = !"classique".equalsIgnoreCase(this.getConfig().getString("lobby-ui.style", "refuge"));
        NavUi.log = this.getLogger();
        this.lobbyWorld = this.getConfig().getString("lobby-world", "lobby");
        this.expireMs = (long)Math.max(1, this.getConfig().getInt("status-expire-minutes", 10)) * 60000L;
        ConfigurationSection gs = this.getConfig().getConfigurationSection("games");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(id);
                if (s == null) continue;
                GameInfo g = this.games.computeIfAbsent(id.toLowerCase(Locale.ROOT), GameInfo::new);
                g.name = s.getString("name", id);
                g.join = s.getString("join", "");
                g.joinAliases = new ArrayList<String>(s.getStringList("join-aliases"));
                g.spectate = s.getString("spectate", "");
                g.worlds = new ArrayList<String>(s.getStringList("worlds"));
                g.min = Math.max(1, s.getInt("min", 2));
                g.max = Math.max(g.min, s.getInt("max", 16));
                g.multiplier = s.getDouble("multiplier", 1.0);
                Material m = Material.matchMaterial((String)s.getString("icon", "PAPER"));
                g.icon = m == null || !m.isItem() ? Material.PAPER : m;
            }
        }
        this.levelColors.clear();
        ConfigurationSection lc = this.getConfig().getConfigurationSection("chat.level-colors");
        if (lc != null) {
            for (String k : lc.getKeys(false)) {
                try {
                    this.levelColors.put(Integer.parseInt(k), lc.getString(k, "&7"));
                }
                catch (NumberFormatException numberFormatException) {}
            }
        }
        if (this.levelColors.isEmpty()) {
            this.levelColors.put(1, "&7");
        }
    }

    private void loadAllPlayers() {
        File[] files = this.playersFolder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            try {
                UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                PlayerData d = PlayerData.load(f, id);
                this.players.put(id, d);
                this.nameIndex.put(d.name.toLowerCase(Locale.ROOT), id);
            }
            catch (IllegalArgumentException e) {
                this.getLogger().warning("Fichier joueur ignore : " + f.getName());
            }
        }
    }

    private void saveDirty(boolean async) {
        if (this.dirty.isEmpty()) {
            return;
        }
        HashMap<File, String> out = new HashMap<File, String>();
        for (UUID id : this.dirty) {
            PlayerData d = this.players.get(id);
            if (d == null) continue;
            out.put(new File(this.playersFolder, String.valueOf(id) + ".yml"), d.serialize());
        }
        this.dirty.clear();
        Runnable write = () -> {
            for (Map.Entry e : out.entrySet()) {
                try {
                    Files.writeString(((File)e.getKey()).toPath(), (CharSequence)e.getValue(), StandardCharsets.UTF_8, new OpenOption[0]);
                }
                catch (IOException ex) {
                    this.getLogger().warning("Sauvegarde " + ((File)e.getKey()).getName() + " : " + ex.getMessage());
                }
            }
        };
        if (async) {
            this.getServer().getScheduler().runTaskAsynchronously((Plugin)this, write);
        } else {
            write.run();
        }
    }

    private PlayerData data(Player p) {
        PlayerData d = this.players.computeIfAbsent(p.getUniqueId(), PlayerData::new);
        if (!p.getName().equals(d.name)) {
            this.nameIndex.remove(d.name.toLowerCase(Locale.ROOT));
            d.name = p.getName();
            this.dirty.add(d.uuid);
        }
        this.nameIndex.put(d.name.toLowerCase(Locale.ROOT), d.uuid);
        return d;
    }

    private PlayerData dataByName(String name) {
        Player online = Bukkit.getPlayerExact((String)name);
        if (online != null) {
            return this.data(online);
        }
        UUID id = this.nameIndex.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : this.players.get(id);
    }

    private void markDirty(PlayerData d) {
        this.dirty.add(d.uuid);
    }

    static Component c(String legacy) {
        return AMP.deserialize(legacy == null ? "" : legacy).decoration(TextDecoration.ITALIC, false);
    }

    static String sec(String legacy) {
        return SECTION.serialize((Component)AMP.deserialize(legacy == null ? "" : legacy));
    }

    private static void msg(CommandSender to, String legacy) {
        to.sendMessage(EemiCore.c(legacy));
    }

    private static Component button(String text, NamedTextColor color, String command, String hover) {
        return ((TextComponent)Component.text((String)text, (TextColor)color, (TextDecoration[])new TextDecoration[]{TextDecoration.BOLD}).clickEvent(ClickEvent.runCommand((String)command))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Component.text((String)hover, (TextColor)NamedTextColor.GRAY)));
    }

    private boolean inLobby(Player p) {
        return p.getWorld().getName().equalsIgnoreCase(this.lobbyWorld);
    }

    /*
     * 1.5.1 : mondes a inventaire propre (SkyBlock, OneBlock, Abysse). Un jeu qui vide l'inventaire
     * avant de teleporter (BedWars) faisait enregistrer par Multiverse-Inventories un inventaire vide
     * pour ces mondes : on ne fait jamais entrer un joueur dans un jeu directement depuis la.
     */
    private boolean inProtectedWorld(Player p) {
        List<String> list = this.getConfig().isSet("party.protected-worlds") ? this.getConfig().getStringList("party.protected-worlds") : List.of("bskyblock_world*", "oneblock_world*", "abysse*", "arene_boss");
        String w = p.getWorld().getName().toLowerCase(Locale.ROOT);
        for (String s : list) {
            String m = s.toLowerCase(Locale.ROOT).trim();
            if (m.endsWith("*") ? w.startsWith(m.substring(0, m.length() - 1)) : w.equals(m)) {
                return true;
            }
        }
        return false;
    }

    /* Renvoie le joueur au lobby (Multiverse-Inventories range son inventaire), puis lance l'action. */
    private void viaLobby(Player p, Runnable then) {
        EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&7Passage par le lobby pour mettre ton inventaire a l'abri...");
        p.performCommand(this.getConfig().getString("party.lobby-command", "lobby"));
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (this.inProtectedWorld(p)) {
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cImpossible de passer par le lobby : partie annulee. Fais /lobby puis rejoins le jeu.");
                return;
            }
            then.run();
        }, (long)Math.max(10, this.getConfig().getInt("party.lobby-delay-ticks", 30)));
    }

    private void sendFollowButton(Player m, Player leader, GameInfo g) {
        m.sendMessage(EemiCore.c("&9[Groupe] &r&7" + leader.getName() + " est parti jouer a " + g.name + "&7. Ton inventaire reste ici. ").append(EemiCore.button("[SUIVRE]", NamedTextColor.GREEN, "/eemi suivre " + leader.getName(), "Passer par le lobby et rejoindre " + leader.getName())));
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onJoinFromProtectedWorld(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!this.inProtectedWorld(p)) {
            return;
        }
        String typed = EemiCore.normCommand(e.getMessage());
        for (GameInfo g : this.games.values()) {
            if (!EemiCore.isJoinCommand(g, typed)) continue;
            e.setCancelled(true);
            String original = e.getMessage();
            this.viaLobby(p, () -> p.chat(original));
            return;
        }
    }

    GameInfo game(String id) {
        return id == null ? null : this.games.get(id.toLowerCase(Locale.ROOT));
    }

    private GameInfo gameOrCreate(String id) {
        return this.games.computeIfAbsent(id.toLowerCase(Locale.ROOT), GameInfo::new);
    }

    private GameInfo gameForWorld(String world) {
        for (GameInfo g : this.games.values()) {
            if (!g.hasWorld(world)) continue;
            return g;
        }
        return null;
    }

    // ================================================================ 1.6.0 : jour de Paris, sons du pack

    private ZoneId zone() {
        try {
            return ZoneId.of(this.getConfig().getString("timezone", "Europe/Paris"));
        }
        catch (Exception ex) {
            return ZoneId.of("Europe/Paris");
        }
    }

    private String parisDay() {
        return LocalDate.now(this.zone()).toString();
    }

    /** Son d'interface : cle texte du pack (sounds.<nom> du config, defaut eemicraft:ui.<nom>). Vide = aucun son. */
    void uiSound(Player p, String name) {
        if (p == null || !p.isOnline()) {
            return;
        }
        String key = this.getConfig().getString("sounds." + name, "eemicraft:ui." + name);
        if (key == null || key.isBlank()) {
            return;
        }
        p.playSound(p.getLocation(), key.trim(), 1.0f, 1.0f);
    }

    /** Recompense de la premiere connexion du jour (heure de Paris), selon la serie de jours consecutifs. */
    private void checkDaily(Player p) {
        if (p == null || !p.isOnline() || !this.getConfig().getBoolean("daily-reward.enabled", true)) {
            return;
        }
        PlayerData d = this.data(p);
        LocalDate today = LocalDate.now(this.zone());
        if (today.toString().equals(d.dailyDate)) {
            return;
        }
        d.streak = today.minusDays(1L).toString().equals(d.dailyDate) ? d.streak + 1 : 1;
        d.dailyDate = today.toString();
        List<Integer> table = this.getConfig().getIntegerList("daily-reward.coins");
        if (table.isEmpty()) {
            table = List.of(20, 25, 30, 35, 40, 45, 50);
        }
        int max = table.size();
        int coins = Math.max(0, table.get(Math.min(d.streak, max) - 1));
        d.coins += coins;
        this.markDirty(d);
        String serie = d.streak >= max ? "&6Série max (" + d.streak + " jours) !" : "&7Série : &e" + d.streak + "&7/&e" + max + " jours";
        EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&aRécompense du jour : &6+" + coins + " pièces &8| " + serie);
        if (d.streak < max) {
            int next = table.get(Math.min(d.streak + 1, max) - 1);
            EemiCore.msg((CommandSender)p, "&7Reviens demain pour &6" + next + " pièces&7. Un jour manqué remet la série à 1.");
        }
        p.showTitle(Title.title((Component)EemiCore.c("&6&lRécompense du jour"), (Component)EemiCore.c("&e+" + coins + " pièces &7- jour " + Math.min(d.streak, max) + "/" + max), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(300L), (Duration)Duration.ofMillis(2500L), (Duration)Duration.ofMillis(700L))));
        this.uiSound(p, "recompense");
    }

    /** Ligne cliquable a la connexion : [Quetes x/3] [Jouer] [Boutique]. */
    private void sendJoinLinks(Player p) {
        if (p == null || !p.isOnline() || !this.getConfig().getBoolean("join-links.enabled", true)) {
            return;
        }
        PlayerData d = this.data(p);
        this.ensureQuests(d);
        int done = 0;
        for (PlayerData.Quest q : d.quests) {
            if (q.done) ++done;
        }
        int total = d.quests.size();
        NamedTextColor qc = total > 0 && done >= total ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        Component line = EemiCore.c("&6[EEMICRAFT] &r")
                .append(EemiCore.button("[Quêtes " + done + "/" + total + "]", qc, "/quetes", "Voir les quêtes du jour"))
                .append(Component.text(" "))
                .append(EemiCore.button("[Jouer]", NamedTextColor.GREEN, "/jouer", "Rejoindre la partie la plus remplie"))
                .append(Component.text(" "))
                .append(EemiCore.button("[Boutique]", NamedTextColor.GOLD, "/boutique", "Ouvrir la boutique (" + EemiCore.num(d.coins) + " pièces)"));
        p.sendMessage(line);
    }

    private long xpNeeded(int level) {
        return this.getConfig().getLong("levels.base", 100L) + this.getConfig().getLong("levels.step", 50L) * ((long)level - 1L);
    }

    String levelColor(int level) {
        Map.Entry<Integer, String> e = this.levelColors.floorEntry(level);
        return e == null ? "&7" : e.getValue();
    }

    private ConfigurationSection cosmetic(String id) {
        return id == null ? null : this.getConfig().getConfigurationSection("cosmetics." + id);
    }

    /** 1.9.0 (#88) : cosmetique saisonnier masque hors saison (hide-off-season: true et hors des dates
     *  available-from / available-until) : ni vendu, ni visible dans la boutique pour ceux qui ne l'ont pas.
     *  1.9.3 (Esteban, 26/11) : ceux qui l'ont achete le voient toujours et peuvent l'equiper et le porter. */
    private boolean offSeason(ConfigurationSection s) {
        return s != null && s.getBoolean("hide-off-season", false) && !this.onSale(s);
    }

    /** Cosmetique equipe a AFFICHER (null si rien). 1.9.3 : un cosmetique equipe est forcement achete, donc
     *  toujours porte, meme hors saison. */
    private ConfigurationSection worn(String id) {
        return this.cosmetic(id);
    }

    /** 1.9.0 : visible dans la boutique pour ce joueur (achete, ou en vente et pas reserve au casino ; jamais hors saison masque). */
    private boolean shopVisible(PlayerData d, String id) {
        ConfigurationSection s = this.cosmetic(id);
        if (s == null) {
            return false;
        }
        // 1.9.2 : hors saison, il reste visible (grise, non equipable) pour ceux qui l'ont achete
        if (this.offSeason(s)) {
            return d.owned.contains(id);
        }
        return d.owned.contains(id) || (this.onSale(s) && !this.casinoOnly(s));
    }

    /** 1.9.4 (Esteban, 26/11) : cosmetique de saison EN VENTE et PAS ENCORE ACHETE : il est dans la page « Saison »
     *  de la boutique (bouton a la place de Quetes), plus dans sa categorie. Une fois achete, il va dans sa categorie. */
    private boolean saisonPick(PlayerData d, String id) {
        ConfigurationSection s = this.cosmetic(id);
        if (s == null || d.owned.contains(id) || this.casinoOnly(s) || !this.onSale(s)) {
            return false;
        }
        String season = s.getString("season");
        return season != null && !season.isBlank();
    }

    private List<String> saisonIds(PlayerData d) {
        ArrayList<String> ids = new ArrayList<String>();
        for (String cat : CATEGORIES) {
            for (String id : this.cosmeticsOf(cat)) {
                if (this.saisonPick(d, id)) ids.add(id);
            }
        }
        return ids;
    }

    /** Page ou revenir apres un clic sur un cosmetique : la page Saison si on y etait. */
    private String backCat(Player p, String cat) {
        return this.saisonView.contains(p.getUniqueId()) ? "saison" : cat;
    }

    private String equippedTitle(PlayerData d) {
        ConfigurationSection s = this.worn(d.equipped.get("title"));
        return s == null ? "" : s.getString("text", "");
    }

    private static String bar(long value, long max, int width) {
        int filled = max <= 0L ? width : (int)Math.min((long)width, value * (long)width / max);
        return "&a" + "|".repeat(filled) + "&8" + "|".repeat(width - filled);
    }

    static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException e) {
            return def;
        }
    }

    private void second() {
        String today;
        long now = System.currentTimeMillis();
        this.invites.values().removeIf(i -> now - i.time > 60000L);
        this.lastJoin.values().removeIf(j -> now - j.time > 900000L);
        for (Map.Entry<UUID, Long> e : new ArrayList<Map.Entry<UUID, Long>>(this.offlineSince.entrySet())) {
            PlayerData d;
            if (now - e.getValue() < 300000L) continue;
            this.offlineSince.remove(e.getKey());
            Party party = this.partyOf.get(e.getKey());
            if (party == null) continue;
            this.removeFromParty(party, e.getKey(), "&7" + ((d = this.players.get(e.getKey())) == null ? "?" : d.name) + " a ete retire du groupe (deconnecte).");
        }
        if (++this.callTimer >= Math.max(10, this.getConfig().getInt("call-interval", 60))) {
            this.callTimer = 0;
            for (GameInfo g : this.games.values()) {
                if (!g.fresh(this.expireMs) || !"waiting".equals(g.state) || g.waiting < 1 || g.waiting >= g.min) continue;
                int missing = g.min - g.waiting;
                this.sendCall(g, "&eIl manque &c" + missing + " joueur" + (missing > 1 ? "s" : "") + " &e! ");
            }
        }
        if (!(today = LocalDate.now().toString()).equals(this.lastDay)) {
            this.lastDay = today;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!this.ensureQuests(this.data(p))) continue;
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&eNouvelles quêtes du jour ! &7/quetes");
            }
        }
        String paris = this.parisDay();
        if (!paris.equals(this.lastParisDay)) {
            this.lastParisDay = paris;
            for (Player p : Bukkit.getOnlinePlayers()) {
                this.checkDaily(p);
            }
        }
        // 1.9.0 (#114) : compteur au lieu de "heure % 30 == 0", qui sautait des recalculs quand le serveur ralentissait
        if (++this.topTimer >= 30) {
            this.topTimer = 0;
            this.recomputeTops();
        }
    }

    private void sendCall(GameInfo g, String text) {
        if (this.maintenance.containsKey(g.id)) {
            return;
        }
        Component line = EemiCore.c("&8[" + g.name + "&8] " + text).append(EemiCore.button("[REJOINDRE]", NamedTextColor.GREEN, "/eemi join " + g.id, "Clique pour rejoindre la partie"));
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JoinMark j;
            if (!this.inLobby(p) || !this.data((Player)p).calls || (j = this.lastJoin.get(p.getUniqueId())) != null && j.game.equals(g.id) && now - j.time < 600000L) continue;
            p.sendMessage(line);
        }
    }

    String statusText(GameInfo g) {
        String text;
        ConfigurationSection t = this.getConfig().getConfigurationSection("status-text");
        if (this.maintenance.containsKey(g.id)) {
            text = t == null ? "&8Maintenance" : t.getString("maintenance", "&8Maintenance");
        } else if (g.fresh(this.expireMs)) {
            switch (g.state) {
                case "starting": {
                    text = t == null ? "&eDemarrage..." : t.getString("starting", "&eDemarrage...");
                    break;
                }
                case "playing": {
                    text = t == null ? "&cEn partie (%playing%)" : t.getString("playing", "&cEn partie (%playing%)");
                    break;
                }
                case "ending": {
                    text = t == null ? "&6Fin de partie" : t.getString("ending", "&6Fin de partie");
                    break;
                }
                default: {
                    if (g.waiting > 0) {
                        text = t == null ? "&a%waiting%/%max% en attente" : t.getString("waiting", "&a%waiting%/%max% en attente");
                        break;
                    }
                    if (g.playing > 0) {
                        text = t == null ? "&cEn partie (%playing%)" : t.getString("playing", "&cEn partie (%playing%)");
                        break;
                    }
                    text = t == null ? "&7Personne" : t.getString("nobody", "&7Personne");
                    break;
                }
            }
        } else {
            int n = g.worldPlayers();
            text = n == 0 ? (t == null ? "&7Personne" : t.getString("nobody", "&7Personne")) : (n == 1 ? (t == null ? "&71 joueur" : t.getString("deduced-one", "&71 joueur")) : (t == null ? "&7%players% joueurs" : t.getString("deduced", "&7%players% joueurs")));
        }
        return text.replace("%waiting%", String.valueOf(g.waiting)).replace("%playing%", String.valueOf(g.playing)).replace("%max%", String.valueOf(g.max)).replace("%players%", String.valueOf(this.playerCount(g)));
    }

    int playerCount(GameInfo g) {
        return g.fresh(this.expireMs) ? g.waiting + g.playing : g.worldPlayers();
    }

    private void updateStatus(String id, String state, int waiting, int playing, int max) {
        GameInfo g = this.gameOrCreate(id);
        String old = g.fresh(this.expireMs) ? g.state : null;
        int oldPlaying = g.fresh(this.expireMs) ? g.playing : 0;
        g.state = state;
        g.waiting = Math.max(0, waiting);
        g.playing = Math.max(0, playing);
        if (max > 0) {
            g.max = max;
        }
        g.lastUpdate = System.currentTimeMillis();
        if (this.live != null) {
            this.live.status(g, oldPlaying, g.playing);
        }
        if ("starting".equals(state) && !"starting".equals(old)) {
            this.sendCall(g, "&eLa partie de " + g.name + " &edemarre ! ");
        }
    }

    private void joinGame(Player p, GameInfo g) {
        if (this.blockedByMaintenance(p, g)) {
            return;
        }
        Party party = this.partyOf.get(p.getUniqueId());
        if (party != null && party.members.size() > 1) {
            if (!party.leader.equals(p.getUniqueId())) {
                EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cSeul le chef du groupe lance une partie.");
                return;
            }
            boolean menu = EemiCore.isMenuJoin(g);
            for (UUID id : party.members) {
                Player m;
                if (id.equals(p.getUniqueId()) || (m = Bukkit.getPlayer((UUID)id)) == null) continue;
                if (this.inProtectedWorld(m)) {
                    this.sendFollowButton(m, p, g);
                    continue;
                }
                if (menu) {
                    EemiCore.msg((CommandSender)m, "&9[Groupe] &r&7" + p.getName() + " choisit une partie de " + g.name + "&7, tu le suivras automatiquement.");
                    continue;
                }
                EemiCore.msg((CommandSender)m, "&9[Groupe] &r&7" + p.getName() + " emmene le groupe en " + g.name + "&7.");
                this.runJoin(m, g);
            }
        }
        this.runJoin(p, g);
    }

    private void runJoin(Player p, GameInfo g) {
        if (g.join == null || g.join.isBlank()) {
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cCe jeu n'a pas de commande pour le rejoindre.");
            return;
        }
        if (this.blockedByMaintenance(p, g)) {
            return;
        }
        if (this.inProtectedWorld(p)) {
            this.viaLobby(p, () -> this.runJoin(p, g));
            return;
        }
        this.lastJoin.put(p.getUniqueId(), new JoinMark(g.id, System.currentTimeMillis()));
        String cmd = g.join.trim();
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (cmd.toLowerCase(Locale.ROOT).startsWith("console:")) {
                String c = cmd.substring(8).trim().replace("%player%", p.getName());
                if (c.startsWith("/")) {
                    c = c.substring(1);
                }
                Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)c);
            } else {
                p.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd.replace("%player%", p.getName()));
            }
        });
    }

    private void spectate(Player p, GameInfo g) {
        if (g.spectate == null || g.spectate.isBlank()) {
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cPas de mode spectateur pour " + g.name + "&c.");
            return;
        }
        String cmd = g.spectate.trim();
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            if (p.isOnline()) {
                p.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd.replace("%player%", p.getName()));
            }
        });
    }

    private void quickPlay(Player p) {
        GameInfo best = null;
        for (GameInfo g : this.games.values()) {
            if (this.maintenance.containsKey(g.id) || !g.fresh(this.expireMs) || !"waiting".equals(g.state) && !"starting".equals(g.state) || g.waiting <= 0 || g.waiting >= g.max || best != null && g.waiting <= best.waiting) continue;
            best = g;
        }
        if (best == null) {
            ArrayList<GameInfo> pool = new ArrayList<GameInfo>();
            for (String id : this.getConfig().getStringList("random-games")) {
                GameInfo g = this.game(id);
                if (g == null || g.join == null || g.join.isBlank() || this.maintenance.containsKey(g.id)) continue;
                pool.add(g);
            }
            if (pool.isEmpty()) {
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cAucun jeu disponible.");
                return;
            }
            best = (GameInfo)pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&7Direction " + best.name + "&7 !");
        this.joinGame(p, best);
    }

    private File maintenanceFile() {
        return new File(this.getDataFolder(), "maintenance.yml");
    }

    private void loadMaintenance() {
        this.maintenance.clear();
        File f = this.maintenanceFile();
        if (!f.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration((File)f);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            String reason = s.getString("reason", "");
            this.maintenance.put(id.toLowerCase(Locale.ROOT), new Maintenance(reason == null ? "" : reason, s.getString("by", "?"), s.getLong("since", System.currentTimeMillis())));
        }
    }

    private void saveMaintenance() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Maintenance> e : this.maintenance.entrySet()) {
            y.set(e.getKey() + ".reason", e.getValue().reason());
            y.set(e.getKey() + ".by", e.getValue().by());
            y.set(e.getKey() + ".since", e.getValue().since());
        }
        String text = y.saveToString();
        File f = this.maintenanceFile();
        this.getServer().getScheduler().runTaskAsynchronously((Plugin)this, () -> {
            Map<String, Maintenance> map = this.maintenance;
            synchronized (map) {
                try {
                    Files.writeString(f.toPath(), (CharSequence)text, StandardCharsets.UTF_8, new OpenOption[0]);
                }
                catch (IOException ex) {
                    this.getLogger().warning("Sauvegarde maintenance.yml : " + ex.getMessage());
                }
            }
        });
    }

    private String maintenanceMessage(GameInfo g) {
        Maintenance m = this.maintenance.get(g.id);
        String reason = m == null || m.reason().isBlank() ? "" : "&7 : " + m.reason();
        return PREFIX + g.name + " &cest en maintenance" + reason;
    }

    private boolean blockedByMaintenance(Player p, GameInfo g) {
        if (!this.maintenance.containsKey(g.id) || p.hasPermission("eemicore.maintenance.bypass")) {
            return false;
        }
        EemiCore.msg((CommandSender)p, this.maintenanceMessage(g));
        return true;
    }

    private static String normCommand(String cmd) {
        int space;
        String first;
        int colon;
        String c = cmd.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("/")) {
            c = c.substring(1);
        }
        if ((colon = (first = (space = (c = c.trim().replaceAll("\\s+", " ")).indexOf(32)) < 0 ? c : c.substring(0, space)).indexOf(58)) >= 0) {
            c = c.substring(colon + 1);
        }
        return c;
    }

    private static boolean isJoinCommand(GameInfo g, String typed) {
        ArrayList<String> forms = new ArrayList<String>(g.joinAliases);
        if (g.join != null && !g.join.isBlank() && !g.join.trim().toLowerCase(Locale.ROOT).startsWith("console:")) {
            forms.add(g.join);
        }
        for (String form : forms) {
            String f = EemiCore.normCommand(form.replace("%player%", ""));
            if (f.isEmpty() || !(f.indexOf(32) < 0 ? typed.equals(f) : typed.equals(f) || typed.startsWith(f + " "))) continue;
            return true;
        }
        return false;
    }

    @EventHandler(priority=EventPriority.LOW, ignoreCancelled=true)
    public void onMaintenanceCommand(PlayerCommandPreprocessEvent e) {
        if (this.maintenance.isEmpty()) {
            return;
        }
        Player p = e.getPlayer();
        if (p.hasPermission("eemicore.maintenance.bypass")) {
            return;
        }
        String typed = EemiCore.normCommand(e.getMessage());
        for (String id : this.maintenance.keySet()) {
            GameInfo g = this.game(id);
            if (g == null || !EemiCore.isJoinCommand(g, typed)) continue;
            e.setCancelled(true);
            EemiCore.msg((CommandSender)p, this.maintenanceMessage(g));
            return;
        }
    }

    private void setMaintenance(CommandSender sender, GameInfo g, boolean on, String reason) {
        Player sp;
        if (on) {
            this.maintenance.put(g.id, new Maintenance(reason == null ? "" : reason.trim(), sender.getName(), System.currentTimeMillis()));
        } else if (this.maintenance.remove(g.id) == null) {
            EemiCore.msg(sender, "&6[EEMICRAFT] &r&7" + g.name + " &7n'etait pas en maintenance.");
            return;
        }
        this.saveMaintenance();
        this.writeStatusFile();
        String announce = "&6[EEMICRAFT] &r&e" + g.name + (on ? " &7est en maintenance" : " &7est de nouveau disponible");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!this.inLobby(p)) continue;
            EemiCore.msg((CommandSender)p, announce);
        }
        if (!(sender instanceof Player) || !this.inLobby(sp = (Player)sender)) {
            EemiCore.msg(sender, announce);
        }
        this.getLogger().info(g.id + (on ? " en maintenance par " + sender.getName() + (String)(reason == null || reason.isBlank() ? "" : " : " + reason) : " rouvert par " + sender.getName()));
    }

    private void maintenanceCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eemicore.maintenance")) {
            EemiCore.msg(sender, "&cPermission refusee.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            if (this.maintenance.isEmpty()) {
                EemiCore.msg(sender, "&6[EEMICRAFT] &r&aAucun jeu en maintenance.");
                return;
            }
            SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
            EemiCore.msg(sender, "&6[EEMICRAFT] &r&eJeux en maintenance :");
            for (Map.Entry<String, Maintenance> e : this.maintenance.entrySet()) {
                Maintenance m = e.getValue();
                GameInfo g = this.game(e.getKey());
                long min = (System.currentTimeMillis() - m.since()) / 60000L;
                String ago = min < 60L ? min + " min" : min / 60L + " h " + min % 60L + " min";
                EemiCore.msg(sender, " &e" + e.getKey() + " &8- " + (g == null ? e.getKey() : g.name) + " &8: &f" + (m.reason().isBlank() ? "&7(sans raison)" : m.reason()) + " &8(par &7" + m.by() + "&8, depuis le &7" + fmt.format(new Date(m.since())) + "&8, il y a " + ago + ")");
            }
            return;
        }
        if (args.length < 3) {
            EemiCore.msg(sender, "&6[EEMICRAFT] &r&c/eemi maintenance <jeu> on [raison...] &7| &c/eemi maintenance <jeu> off &7| &c/eemi maintenance list");
            return;
        }
        GameInfo g = this.game(args[1]);
        if (g == null) {
            EemiCore.msg(sender, "&6[EEMICRAFT] &r&cJeu inconnu : " + args[1] + ". &7Jeux : " + String.join((CharSequence)", ", this.games.keySet()));
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on": {
                this.setMaintenance(sender, g, true, String.join((CharSequence)" ", Arrays.copyOfRange(args, 3, args.length)));
                break;
            }
            case "off": {
                this.setMaintenance(sender, g, false, null);
                break;
            }
            default: {
                EemiCore.msg(sender, "&6[EEMICRAFT] &r&c/eemi maintenance <jeu> <on|off> [raison...]");
            }
        }
    }

    private void startStatusFile() {
        if (this.statusFileTask != null) {
            this.statusFileTask.cancel();
            this.statusFileTask = null;
        }
        if (!this.getConfig().getBoolean("status-file.enabled", true)) {
            return;
        }
        long period = (long)Math.max(5, this.getConfig().getInt("status-file.interval", 30)) * 20L;
        this.writeStatusFile();
        this.statusFileTask = this.getServer().getScheduler().runTaskTimer((Plugin)this, this::writeStatusFile, period, period);
    }

    private GameSnapshot snapshot(GameInfo g) {
        String state;
        int waiting = 0;
        int playing = 0;
        if (g.fresh(this.expireMs)) {
            waiting = g.waiting;
            playing = g.playing;
            state = switch (g.state) {
                case "starting", "playing", "ending" -> g.state;
                default -> waiting > 0 ? "waiting" : (playing > 0 ? "playing" : "nobody");
            };
        } else {
            state = g.state != null ? "unknown" : ((playing = g.worldPlayers()) > 0 ? "playing" : "nobody");
        }
        return new GameSnapshot(g.id, g.name, this.maintenance.get(g.id), state, waiting, playing, g.max);
    }

    private void writeStatusFile() {
        if (!this.getConfig().getBoolean("status-file.enabled", true)) {
            return;
        }
        ArrayList<GameSnapshot> snap = new ArrayList<GameSnapshot>();
        ConfigurationSection gs = this.getConfig().getConfigurationSection("games");
        HashSet<String> done = new HashSet<String>();
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                GameInfo g = this.game(id);
                if (g == null || !done.add(g.id)) continue;
                snap.add(this.snapshot(g));
            }
        }
        for (GameInfo g : this.games.values()) {
            if (!done.add(g.id)) continue;
            snap.add(this.snapshot(g));
        }
        long seq = ++this.statusFileSeq;
        Path dir = this.getDataFolder().toPath();
        this.getServer().getScheduler().runTaskAsynchronously((Plugin)this, () -> {
            Object object = this.statusFileLock;
            synchronized (object) {
                if (seq < this.statusFileWritten) {
                    return;
                }
                Path tmp = dir.resolve("status.json.tmp");
                Path out = dir.resolve("status.json");
                try {
                    Files.writeString(tmp, (CharSequence)EemiCore.statusJson(snap, System.currentTimeMillis()), StandardCharsets.UTF_8, new OpenOption[0]);
                    try {
                        Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (AtomicMoveNotSupportedException ex) {
                        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    this.statusFileWritten = seq;
                }
                catch (IOException ex) {
                    this.getLogger().warning("Ecriture status.json : " + ex.getMessage());
                }
            }
        });
    }

    private static String statusJson(List<GameSnapshot> snap, long now) {
        StringBuilder b = new StringBuilder(4096);
        b.append("{\n  \"version\": 1,\n  \"updated_at\": ").append(now).append(",\n  \"games\": [");
        for (int i = 0; i < snap.size(); ++i) {
            GameSnapshot s = snap.get(i);
            b.append(i == 0 ? "\n" : ",\n").append("    {").append("\"id\": ").append(EemiCore.jsonString(s.id())).append(", \"name\": ").append(EemiCore.jsonString(s.name())).append(", \"maintenance\": ").append(s.maintenance() != null).append(", \"maintenance_reason\": ").append(s.maintenance() == null || s.maintenance().reason().isBlank() ? "null" : EemiCore.jsonString(s.maintenance().reason())).append(", \"state\": ").append(EemiCore.jsonString(s.state())).append(", \"waiting\": ").append(s.waiting()).append(", \"playing\": ").append(s.playing()).append(", \"max\": ").append(s.max()).append('}');
        }
        b.append(snap.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return b.toString();
    }

    private static String jsonString(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('\"');
        block7: for (int i = 0; i < s.length(); ++i) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\"': {
                    b.append("\\\"");
                    continue block7;
                }
                case '\\': {
                    b.append("\\\\");
                    continue block7;
                }
                case '\n': {
                    b.append("\\n");
                    continue block7;
                }
                case '\r': {
                    b.append("\\r");
                    continue block7;
                }
                case '\t': {
                    b.append("\\t");
                    continue block7;
                }
                default: {
                    if (ch < ' ') {
                        b.append(String.format("\\u%04x", ch));
                        continue block7;
                    }
                    b.append(ch);
                }
            }
        }
        return b.append('\"').toString();
    }

    private void reward(PlayerData d, GameInfo g, String type) {
        String stat = switch (type) {
            case "win" -> "wins";
            case "kill" -> "kills";
            default -> "plays";
        };
        d.addStat(g.id, stat, 1);
        long coins = Math.round((double)this.getConfig().getLong("rewards." + type + ".coins", 0L) * g.multiplier);
        long xp = Math.round((double)this.getConfig().getLong("rewards." + type + ".xp", 0L) * g.multiplier);
        d.coins += coins;
        Player p = Bukkit.getPlayer((UUID)d.uuid);
        if (p != null && (coins > 0L || xp > 0L)) {
            p.sendActionBar(EemiCore.c("&6+" + coins + " pièces  &b+" + xp + " xp"));
        }
        // 1.6.0 : premiere partie / premiere victoire du jour (heure de Paris)
        String day = this.parisDay();
        if (type.equals("play") && !day.equals(d.firstPlayDate)) {
            d.firstPlayDate = day;
            long bonus = this.getConfig().getLong("daily-bonus.first-play", 15L);
            if (bonus > 0L) {
                d.coins += bonus;
                if (p != null) {
                    EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&aPremière partie du jour : &6+" + bonus + " pièces");
                    this.uiSound(p, "recompense");
                }
            }
        }
        if (type.equals("win") && !day.equals(d.firstWinDate)) {
            d.firstWinDate = day;
            long bonus = this.getConfig().getLong("daily-bonus.first-win", 50L);
            if (bonus > 0L) {
                d.coins += bonus;
                if (p != null) {
                    EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&aPremière victoire du jour : &6+" + bonus + " pièces");
                    this.uiSound(p, "recompense");
                }
            }
        }
        this.giveXp(d, xp);
        this.ensureQuests(d);
        for (PlayerData.Quest q : d.quests) {
            if (q.done || !q.type.equals(type) || !q.game.isEmpty() && !q.game.equals(g.id)) continue;
            ++q.progress;
            if (q.progress < q.target) continue;
            q.done = true;
            q.progress = q.target;
            d.coins += (long)q.coins;
            if (p != null) {
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&aQuête terminée : &f" + q.text + " &7(&6+" + q.coins + " pièces&7, &b+" + q.xp + " xp&7)");
                this.uiSound(p, "quete");
            }
            this.giveXp(d, q.xp);
        }
        this.markDirty(d);
    }

    private void giveXp(PlayerData d, long amount) {
        if (amount <= 0L) {
            return;
        }
        d.xp += amount;
        Player p = Bukkit.getPlayer((UUID)d.uuid);
        while (d.xp >= this.xpNeeded(d.level)) {
            d.xp -= this.xpNeeded(d.level);
            ++d.level;
            long bonus = this.getConfig().getLong("levels.level-up-coins", 10L) * (long)d.level;
            d.coins += bonus;
            if (this.live != null) {
                this.live.levelUp(d.name, d.level);
            }
            if (p == null) continue;
            String col = this.levelColor(d.level);
            p.showTitle(Title.title((Component)EemiCore.c(col + "&lNIVEAU " + d.level), (Component)EemiCore.c("&e+" + bonus + " pièces"), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(300L), (Duration)Duration.ofSeconds(3L), (Duration)Duration.ofMillis(800L))));
            this.uiSound(p, "niveau");
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&aTu passes au niveau " + col + d.level + "&a ! &6+" + bonus + " pièces");
        }
        this.markDirty(d);
    }

    private boolean ensureQuests(PlayerData d) {
        String today = LocalDate.now().toString();
        if (today.equals(d.questDate)) {
            return false;
        }
        d.questDate = today;
        d.quests.clear();
        ConfigurationSection pool = this.getConfig().getConfigurationSection("quests.pool");
        if (pool != null) {
            ArrayList ids = new ArrayList(pool.getKeys(false));
            Collections.shuffle(ids);
            int count = Math.min(ids.size(), this.getConfig().getInt("quests.count", 3));
            ArrayList<String> random = new ArrayList<String>();
            for (String id : this.getConfig().getStringList("random-games")) {
                if (this.game(id) == null) continue;
                random.add(id.toLowerCase(Locale.ROOT));
            }
            for (int i = 0; i < count; ++i) {
                ConfigurationSection s = pool.getConfigurationSection((String)ids.get(i));
                if (s == null) continue;
                PlayerData.Quest q = new PlayerData.Quest();
                q.id = (String)ids.get(i);
                q.type = s.getString("type", "play");
                String gm = s.getString("game", "any");
                q.game = gm.equalsIgnoreCase("any") ? "" : (gm.equalsIgnoreCase("random") ? (random.isEmpty() ? "" : (String)random.get(ThreadLocalRandom.current().nextInt(random.size()))) : gm.toLowerCase(Locale.ROOT));
                q.target = Math.max(1, s.getInt("target", 1));
                q.coins = s.getInt("coins", 0);
                q.xp = s.getInt("xp", 0);
                GameInfo g = this.game(q.game);
                q.text = s.getString("text", q.id).replace("%target%", String.valueOf(q.target)).replace("%game%", (CharSequence)(g == null ? "n'importe quel jeu" : g.name + "&f"));
                d.quests.add(q);
            }
        }
        this.markDirty(d);
        return true;
    }

    private void recomputeTops() {
        HashMap<String, List<TopEntry>> result = new HashMap<String, List<TopEntry>>();
        HashSet<String> ids = new HashSet<String>(this.games.keySet());
        for (PlayerData d2 : this.players.values()) {
            ids.addAll(d2.stats.keySet());
        }
        for (String id : ids) {
            for (String type : STAT_TYPES) {
                ArrayList<TopEntry> list = new ArrayList<TopEntry>();
                for (PlayerData d3 : this.players.values()) {
                    int v = d3.stat(id, type);
                    if (v <= 0) continue;
                    list.add(new TopEntry(d3.name, v));
                }
                list.sort(Comparator.comparingLong(TopEntry::value).reversed());
                result.put(id + ":" + type, list.subList(0, Math.min(10, list.size())));
            }
        }
        List<TopEntry> lv = this.levelTop();
        result.put("level:level", new ArrayList<TopEntry>(lv.subList(0, Math.min(10, lv.size()))));
        this.tops = result;
    }

    /** 1.9.0 (#114) : classement COMPLET des niveaux, calcule a la demande (menus, /eemi top level, ta place).
     *  Niveau puis xp dans le niveau, puis pseudo (ordre stable entre deux calculs) ; sans les comptes sans
     *  pseudo connu ("?") ni ceux qui n'ont jamais gagne d'xp (niveau 1, 0 xp). */
    private List<TopEntry> levelTop() {
        ArrayList<PlayerData> all = new ArrayList<PlayerData>();
        for (PlayerData d : this.players.values()) {
            if (d.name == null || d.name.equals("?") || (d.level <= 1 && d.xp <= 0L)) continue;
            all.add(d);
        }
        Comparator<PlayerData> cmp = Comparator.<PlayerData>comparingInt(d -> d.level).thenComparingLong(d -> d.xp).reversed();
        all.sort(cmp.thenComparing(d -> d.name.toLowerCase(Locale.ROOT)));
        ArrayList<TopEntry> lv = new ArrayList<TopEntry>(all.size());
        for (PlayerData d : all) {
            lv.add(new TopEntry(d.name, d.level, d.uuid, d.xp));
        }
        return lv;
    }

    /** 1.9.0 : place d'un joueur dans le classement des niveaux (meme ordre que la liste affichee). */
    private int levelRank(List<TopEntry> list, PlayerData own) {
        for (int i = 0; i < list.size(); ++i) {
            if (own.uuid.equals(list.get(i).uuid())) return i + 1;
        }
        int rank = 1;
        for (TopEntry e : list) {
            if (e.value() > own.level || (e.value() == own.level && e.xp() > own.xp)) ++rank;
        }
        return rank;
    }

    /** 1.9.0 : texte d'une entree de classement ; pour les niveaux, niveau ET xp. */
    static String entryText(String type, TopEntry e) {
        if ("level".equals(type) && e.xp() >= 0L) {
            return "niveau " + e.value() + " &7(" + EemiCore.num(e.xp()) + " xp)";
        }
        return EemiCore.unit(type, e.value());
    }

    private static String unit(String type, long v) {
        return switch (type) {
            case "kills" -> v + " kill" + (v > 1L ? "s" : "");
            case "plays" -> v + " partie" + (v > 1L ? "s" : "");
            case "level" -> "niveau " + v;
            default -> v + " victoire" + (v > 1L ? "s" : "");
        };
    }

    private static String normType(String t) {
        if (t == null) {
            return "wins";
        }
        return switch (t.toLowerCase(Locale.ROOT)) {
            case "kills", "kill" -> "kills";
            case "plays", "play", "parties" -> "plays";
            default -> "wins";
        };
    }

    private void showTop(CommandSender to, String id, String type) {
        boolean level = id.equalsIgnoreCase("level") || id.equalsIgnoreCase("niveau");
        String key = level ? "level:level" : id.toLowerCase(Locale.ROOT) + ":" + EemiCore.normType(type);
        List<TopEntry> list = level ? this.levelTop() : this.tops.getOrDefault(key, List.of());
        if (level && list.size() > 10) {
            list = list.subList(0, 10);
        }
        GameInfo g = this.game(id);
        String title = level ? "&eNiveaux" : (g == null ? id : g.name) + " &7(" + EemiCore.normType(type) + ")";
        EemiCore.msg(to, "&6[EEMICRAFT] &r&eClassement " + title);
        if (list.isEmpty()) {
            EemiCore.msg(to, "&7 Personne pour l'instant.");
        }
        int rank = 1;
        for (TopEntry e : list) {
            String medal = rank == 1 ? "&6" : (rank == 2 ? "&f" : (rank == 3 ? "&c" : "&7"));
            EemiCore.msg(to, " " + medal + "#" + rank++ + " &f" + e.name + " &7- &e" + EemiCore.entryText(level ? "level" : EemiCore.normType(type), e));
        }
        if (level && to instanceof Player tp) {
            List<TopEntry> full = this.levelTop();
            EemiCore.msg(to, "&7 Ta place : &f#" + this.levelRank(full, this.data(tp)) + " &7sur " + full.size());
        }
    }

    String placeholder(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (this.live != null) {
            String lv = this.live.placeholder(p);
            if (lv != null) {
                return lv;
            }
        }
        if (p.startsWith("status_")) {
            GameInfo g = this.game(p.substring(7));
            return g == null ? "" : EemiCore.sec(this.statusText(g));
        }
        if (p.startsWith("maintenance_")) {
            GameInfo g = this.game(p.substring(12));
            return String.valueOf(g != null && this.maintenance.containsKey(g.id));
        }
        if (p.startsWith("players_")) {
            GameInfo g = this.game(p.substring(8));
            return g == null ? "0" : String.valueOf(this.playerCount(g));
        }
        if (p.startsWith("name_")) {
            GameInfo g = this.game(p.substring(5));
            return g == null ? "" : EemiCore.sec(g.name);
        }
        if (p.startsWith("top_")) {
            String[] parts = p.substring(4).split("_");
            if (parts.length < 2) {
                return "";
            }
            int rank = EemiCore.parseInt(parts[parts.length - 1], 0);
            String id = parts[0];
            boolean level = id.equals("level");
            String type = level ? "level" : EemiCore.normType(parts.length >= 3 ? parts[1] : "wins");
            List list = this.tops.getOrDefault(level ? "level:level" : id + ":" + type, List.of());
            if (rank < 1 || rank > list.size()) {
                return "";
            }
            TopEntry e = (TopEntry)list.get(rank - 1);
            return EemiCore.sec(e.name + " &7- &e" + EemiCore.entryText(type, e));
        }
        if (player == null) {
            return "";
        }
        if (p.equals("team")) {
            Player online = player.getPlayer();
            if (online == null) {
                return "";
            }
            Team t = online.getScoreboard().getEntryTeam(online.getName());
            return t == null ? "" : SECTION.serialize(t.prefix());
        }
        PlayerData d = this.players.get(player.getUniqueId());
        if (d == null) {
            d = new PlayerData(player.getUniqueId());
        }
        return switch (p) {
            case "level" -> String.valueOf(d.level);
            case "level_colored" -> EemiCore.sec(this.levelColor(d.level) + d.level);
            case "coins" -> String.valueOf(d.coins);
            case "xp" -> String.valueOf(d.xp);
            case "xp_needed" -> String.valueOf(this.xpNeeded(d.level));
            case "title" -> EemiCore.sec(this.equippedTitle(d));
            case "title_prefix" -> {
                if (this.equippedTitle(d).isEmpty()) {
                    yield "";
                }
                yield EemiCore.sec("&8[" + this.equippedTitle(d) + "&8] &r");
            }
            case "wins" -> String.valueOf(d.total("wins"));
            case "kills" -> String.valueOf(d.total("kills"));
            case "plays" -> String.valueOf(d.total("plays"));
            default -> null;
        };
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onChat(AsyncChatEvent e) {
        if (!this.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }
        Player p = e.getPlayer();
        for (String w : this.getConfig().getStringList("chat.disabled-worlds")) {
            if (!w.equalsIgnoreCase(p.getWorld().getName())) continue;
            return;
        }
        PlayerData d = this.players.get(p.getUniqueId());
        int level = d == null ? 1 : d.level;
        String title = d == null ? "" : this.equippedTitle(d);
        Component prefix = EemiCore.c("&8[" + this.levelColor(level) + "Niv " + level + "&8] " + (String)(title.isEmpty() ? "" : "&8[" + title + "&8] "));
        e.renderer((source, displayName, message, viewer) -> prefix.append(displayName).append((Component)Component.text((String)": ", (TextColor)NamedTextColor.GRAY)).append(message.colorIfAbsent((TextColor)NamedTextColor.WHITE)));
    }

    private void onJoinData(Player p) {
        PlayerData d = this.data(p);
        this.offlineSince.remove(p.getUniqueId());
        this.ensureQuests(d);
        this.markDirty(d);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Party party;
        Player p = e.getPlayer();
        this.onJoinData(p);
        PlayerData d = this.data(p);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.applyHat(p), 10L);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.applyGadget(p), 20L);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.checkDaily(p), 40L);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.sendJoinLinks(p), 60L);
        ConfigurationSection jm = this.worn(d.equipped.get("join"));
        if (jm != null) {
            String text = jm.getString("message", "").replace("%player%", p.getName());
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
                if (!p.isOnline() || p.hasMetadata("vanished") || text.isEmpty()) {
                    return;
                }
                for (Player o : Bukkit.getOnlinePlayers()) {
                    EemiCore.msg((CommandSender)o, text);
                }
            }, 20L);
        }
        if ((party = this.partyOf.get(p.getUniqueId())) != null) {
            this.partyBroadcast(party, "&a" + p.getName() + " est de retour.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        this.removeHat(p);
        this.removeGadget(p);
        this.removeTitleTag(p);
        this.jetpacking.remove(p.getUniqueId());
        this.lastTrailLoc.remove(p.getUniqueId());
        this.invites.remove(p.getUniqueId());
        Party party = this.partyOf.get(p.getUniqueId());
        if (party != null) {
            this.offlineSince.put(p.getUniqueId(), System.currentTimeMillis());
            this.partyBroadcast(party, "&7" + p.getName() + " s'est deconnecte (retire du groupe dans 5 min).");
        }
        this.markDirty(this.data(p));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        boolean viaEemi;
        Player p = e.getPlayer();
        if (this.inLobby(p)) {
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.applyHat(p), 10L);
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.applyGadget(p), 20L);
        } else {
            this.removeHat(p);
            this.removeGadget(p);
            this.removeTitleTag(p);
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
                this.removeHat(p);
                this.removeGadget(p);
            }, 10L);
        }
        Party party = this.partyOf.get(p.getUniqueId());
        if (party == null || !party.leader.equals(p.getUniqueId()) || party.members.size() < 2) {
            return;
        }
        GameInfo g = this.gameForWorld(p.getWorld().getName());
        if (g == null || !this.getConfig().getBoolean("party.auto-follow", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        JoinMark j = this.lastJoin.get(p.getUniqueId());
        viaEemi = j != null && j.game.equals(g.id) && now - j.time < 60000L;
        if (viaEemi && !EemiCore.isMenuJoin(g)) {
            return;
        }
        if (this.getConfig().getStringList("party.no-follow").contains(g.id)) {
            return;
        }
        LeaderCmd lc = this.leaderCmd.get(p.getUniqueId());
        String cmd = lc != null && lc.game.equals(g.id) && now - lc.time < 60000L ? lc.cmd : null;
        for (UUID id : party.members) {
            GameInfo mg;
            Player m = Bukkit.getPlayer((UUID)id);
            if (m == null || m.equals(p) || m.getWorld().equals(p.getWorld()) || (mg = this.gameForWorld(m.getWorld().getName())) != null && mg.id.equals(g.id)) continue;
            if (this.inProtectedWorld(m)) {
                this.sendFollowButton(m, p, g);
                continue;
            }
            this.followLeader(m, p, g, cmd);
        }
    }

    private void followLeader(Player m, Player leader, GameInfo g, String cmd) {
        if (cmd != null) {
            EemiCore.msg((CommandSender)m, "&9[Groupe] &r&7Tu suis " + leader.getName() + " en " + g.name + "&7.");
            this.lastJoin.put(m.getUniqueId(), new JoinMark(g.id, System.currentTimeMillis()));
            Bukkit.getScheduler().runTask((Plugin)this, () -> {
                if (m.isOnline()) {
                    m.performCommand(cmd);
                }
            });
        } else if (!EemiCore.isMenuJoin(g)) {
            EemiCore.msg((CommandSender)m, "&9[Groupe] &r&7Tu suis " + leader.getName() + " en " + g.name + "&7.");
            this.runJoin(m, g);
        } else {
            m.sendMessage(EemiCore.c("&9[Groupe] &r&7" + leader.getName() + " est parti jouer a " + g.name + "&7. ").append(EemiCore.button("[SUIVRE]", NamedTextColor.GREEN, "/eemi suivre " + leader.getName(), "Rejoindre " + leader.getName())));
        }
    }

    private static boolean isMenuJoin(GameInfo g) {
        return g.join != null && g.join.toLowerCase(Locale.ROOT).contains("dm open");
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onLeaderCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        Party party = this.partyOf.get(p.getUniqueId());
        if (party == null || !party.leader.equals(p.getUniqueId()) || party.members.size() < 2) {
            return;
        }
        String typed = EemiCore.normCommand(e.getMessage());
        if (typed.startsWith("eemi ")) {
            return;
        }
        for (GameInfo g : this.games.values()) {
            if (!EemiCore.isJoinCommand(g, typed)) continue;
            this.leaderCmd.put(p.getUniqueId(), new LeaderCmd(g.id, typed, System.currentTimeMillis()));
            return;
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) {
            return;
        }
        if (!e.getFrom().getWorld().equals(e.getTo().getWorld()) && !e.getTo().getWorld().getName().equalsIgnoreCase(this.lobbyWorld)) {
            this.removeHat(e.getPlayer());
            this.removeGadget(e.getPlayer());
            this.removeTitleTag(e.getPlayer());
        }
    }

    private boolean isHat(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(this.hatKey, PersistentDataType.STRING);
    }

    private void applyHat(Player p) {
        if (!p.isOnline()) {
            return;
        }
        ConfigurationSection s = this.worn(this.data((Player)p).equipped.get("hat"));
        if (!this.inLobby(p) || s == null || p.getGameMode() == GameMode.SPECTATOR) {
            this.removeHat(p);
            return;
        }
        Material m = Material.matchMaterial((String)s.getString("material", "GLASS"));
        if (m == null || !m.isItem()) {
            return;
        }
        ItemStack current = p.getInventory().getHelmet();
        if (current != null && !current.getType().isAir() && !this.isHat(current)) {
            return;
        }
        String id = this.data((Player)p).equipped.get("hat");
        if (this.isHat(current) && current.getType() == m && id.equals(current.getItemMeta().getPersistentDataContainer().get(this.hatKey, PersistentDataType.STRING))) {
            return;
        }
        ItemStack hat = new ItemStack(m);
        ItemMeta meta = hat.getItemMeta();
        meta.displayName(EemiCore.c(s.getString("name", id)));
        meta.lore(List.of(EemiCore.c("&7Chapeau EEMICRAFT (hub seulement)")));
        meta.getPersistentDataContainer().set(this.hatKey, PersistentDataType.STRING, id);
        meta.addItemFlags(ItemFlag.values());
        hat.setItemMeta(meta);
        p.getInventory().setHelmet(hat);
    }

    private void removeHat(Player p) {
        if (this.isHat(p.getInventory().getHelmet())) {
            p.getInventory().setHelmet(null);
        }
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; ++i) {
            if (!this.isHat(contents[i])) continue;
            p.getInventory().setItem(i, null);
        }
    }

    private void hatsCheck() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.inLobby(p) && this.data((Player)p).equipped.containsKey("hat")) {
                this.applyHat(p);
                continue;
            }
            if (!this.isHat(p.getInventory().getHelmet())) continue;
            this.removeHat(p);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onHatClick(InventoryClickEvent e) {
        if (this.isHat(e.getCurrentItem()) || this.isHat(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onHatDrop(PlayerDropItemEvent e) {
        if (this.isHat(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(it -> this.isHat((ItemStack)it) || this.gadgetOf((ItemStack)it) != null);
    }

    private void trails() {
        this.rainbowHue = (this.rainbowHue + 0.02f) % 1.0f;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ConfigurationSection s;
            if (!this.inLobby(p) || p.getGameMode() == GameMode.SPECTATOR || p.hasMetadata("vanished")) continue;
            PlayerData d = this.players.get(p.getUniqueId());
            ConfigurationSection configurationSection = s = d == null ? null : this.worn(d.equipped.get("trail"));
            if (s == null) continue;
            Location loc = p.getLocation();
            Location last = this.lastTrailLoc.put(p.getUniqueId(), loc);
            if (last == null || !last.getWorld().equals(loc.getWorld()) || last.distanceSquared(loc) < 0.0025) continue;
            Location at = loc.clone().add(0.0, 0.15, 0.0);
            String name = s.getString("particle", "FLAME").toUpperCase(Locale.ROOT);
            try {
                if (name.equals("RAINBOW")) {
                    Color rgb = Color.getHSBColor(this.rainbowHue, 1.0f, 1.0f);
                    p.getWorld().spawnParticle(Particle.DUST, at, 3, 0.2, 0.05, 0.2, 0.0, new Particle.DustOptions(org.bukkit.Color.fromRGB((int)rgb.getRed(), (int)rgb.getGreen(), (int)rgb.getBlue()), 1.2f));
                    continue;
                }
                Particle particle = Particle.valueOf((String)name);
                if (particle.getDataType() != Void.class) continue;
                p.getWorld().spawnParticle(particle, at, 2, 0.15, 0.05, 0.15, 0.01);
            }
            catch (IllegalArgumentException illegalArgumentException) {}
        }
    }

    private void noFlightCheck() {
        List<String> allowed = this.getConfig().isSet("flight-worlds") ? this.getConfig().getStringList("flight-worlds") : List.of("tntrun", "hideseek");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getAllowFlight() || this.inLobby(p) || p.hasMetadata("vanished") || p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            String w = p.getWorld().getName();
            boolean ok = false;
            for (String a : allowed) {
                if (!a.equalsIgnoreCase(w)) continue;
                ok = true;
            }
            if (ok) continue;
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

    private boolean wantsTitleTag(Player p) {
        PlayerData d = this.players.get(p.getUniqueId());
        return d != null && this.inLobby(p) && !this.equippedTitle(d).isEmpty() && !p.hasMetadata("vanished") && p.getGameMode() != GameMode.SPECTATOR && !p.isInvisible() && this.getConfig().getBoolean("title-tag.enabled", true);
    }

    private Location titleTagLocation(Player p) {
        double h = this.getConfig().getDouble("title-tag.height", 2.55) - (p.isSneaking() ? 0.3 : 0.0);
        Location l = p.getLocation().add(0.0, h, 0.0);
        l.setYaw(0.0f);
        l.setPitch(0.0f);
        return l;
    }

    private void updateTitleTag(Player p) {
        TextDisplay td = this.titleTags.get(p.getUniqueId());
        if (!p.isOnline() || !this.wantsTitleTag(p)) {
            if (td != null) {
                td.remove();
            }
            this.titleTags.remove(p.getUniqueId());
            return;
        }
        Component text = EemiCore.c(this.equippedTitle(this.data(p)));
        if (td == null || !td.isValid() || !td.getWorld().equals(p.getWorld())) {
            if (td != null) {
                td.remove();
            }
            td = (TextDisplay)p.getWorld().spawn(this.titleTagLocation(p), TextDisplay.class, t -> {
                t.getPersistentDataContainer().set(this.tagKey, PersistentDataType.BYTE, (byte)1);
                t.setPersistent(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setTeleportDuration(2);
                t.text(text);
            });
            p.hideEntity((Plugin)this, (Entity)td);
            this.titleTags.put(p.getUniqueId(), td);
        } else if (!text.equals(td.text())) {
            td.text(text);
        }
    }

    private void titleTagsTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextDisplay td = this.titleTags.get(p.getUniqueId());
            if (td == null || !td.isValid() || !this.wantsTitleTag(p) || !td.getWorld().equals(p.getWorld())) continue;
            td.teleport(this.titleTagLocation(p));
        }
    }

    private void removeTitleTag(Player p) {
        TextDisplay td = this.titleTags.remove(p.getUniqueId());
        if (td != null) {
            td.remove();
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onOwnSpawn(EntitySpawnEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(this.tagKey, PersistentDataType.BYTE)) {
            e.setCancelled(false);
        }
    }

    private int gadgetSlot() {
        return Math.max(0, Math.min(8, this.getConfig().getInt("gadget-slot", 7)));
    }

    private String gadgetOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return (String)item.getItemMeta().getPersistentDataContainer().get(this.gadgetKey, PersistentDataType.STRING);
    }

    private void applyGadget(Player p) {
        if (!p.isOnline()) {
            return;
        }
        String id = this.data((Player)p).equipped.get("gadget");
        ConfigurationSection s = this.worn(id);
        if (!this.inLobby(p) || s == null || p.getGameMode() == GameMode.SPECTATOR || p.hasMetadata("vanished")) {
            this.removeGadget(p);
            return;
        }
        ItemStack current = p.getInventory().getItem(this.gadgetSlot());
        if (id.equals(this.gadgetOf(current))) {
            return;
        }
        if (current != null && !current.getType().isAir() && this.gadgetOf(current) == null) {
            return;
        }
        Material m = Material.matchMaterial((String)s.getString("icon", "PAPER"));
        ItemStack it = new ItemStack(m == null || !m.isItem() ? Material.PAPER : m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(EemiCore.c(s.getString("name", id) + " &7(clic droit)"));
        ArrayList<Component> lore = new ArrayList<Component>();
        for (String line : s.getStringList("description")) {
            lore.add(EemiCore.c("&7" + line));
        }
        lore.add(EemiCore.c("&8Gadget EEMICRAFT (hub seulement)"));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.values());
        String kindModel = s.getString("gadget", "");
        if (UI && this.getConfig().getBoolean("lobby-ui.gadget-models", true) && List.of("feux", "peinture", "grappin", "jetpack").contains(kindModel)) {
            meta.setItemModel(new NamespacedKey("eemicraft", "gadget/" + kindModel));
        }
        meta.getPersistentDataContainer().set(this.gadgetKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        this.removeGadget(p);
        p.getInventory().setItem(this.gadgetSlot(), it);
    }

    private void removeGadget(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; ++i) {
            if (this.gadgetOf(contents[i]) == null) continue;
            p.getInventory().setItem(i, null);
        }
    }

    private void gadgetsCheck() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.inLobby(p) && this.data((Player)p).equipped.containsKey("gadget")) {
                this.applyGadget(p);
                continue;
            }
            this.removeGadget(p);
        }
    }

    private boolean gadgetReady(Player p, ConfigurationSection s) {
        long now = System.currentTimeMillis();
        Long until = this.gadgetCooldown.get(p.getUniqueId());
        if (until != null && until > now) {
            p.sendActionBar(EemiCore.c("&cGadget pret dans " + ((until - now) / 1000L + 1L) + " s"));
            return false;
        }
        this.gadgetCooldown.put(p.getUniqueId(), now + s.getLong("cooldown", 5L) * 1000L);
        return true;
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onGadgetUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        String id = this.gadgetOf(e.getItem());
        if (id == null) {
            return;
        }
        ConfigurationSection s = this.cosmetic(id);
        if (s == null || !this.inLobby(p)) {
            return;
        }
        String kind = s.getString("gadget", "");
        if (kind.equals("grappin")) {
            return;
        }
        e.setCancelled(true);
        if (!this.gadgetReady(p, s)) {
            return;
        }
        switch (kind) {
            case "jetpack": {
                this.jetpack(p, s.getInt("duration", 60));
                break;
            }
            case "peinture": {
                Snowball ball = (Snowball)p.launchProjectile(Snowball.class, p.getLocation().getDirection().multiply(1.8));
                ball.getPersistentDataContainer().set(this.tagKey, PersistentDataType.BYTE, (byte)1);
                p.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.8f, 1.3f);
                break;
            }
            case "feux": {
                Location at = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5));
                p.getWorld().spawn(at, Firework.class, fw -> {
                    fw.getPersistentDataContainer().set(this.tagKey, PersistentDataType.BYTE, (byte)1);
                    FireworkMeta fm = fw.getFireworkMeta();
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    FireworkEffect.Type[] types = FireworkEffect.Type.values();
                    fm.addEffect(FireworkEffect.builder().with(types[r.nextInt(types.length)]).withColor(new org.bukkit.Color[]{org.bukkit.Color.fromRGB((int)r.nextInt(0xFFFFFF)), org.bukkit.Color.fromRGB((int)r.nextInt(0xFFFFFF))}).withFade(org.bukkit.Color.WHITE).flicker(r.nextBoolean()).trail(true).build());
                    fm.setPower(1);
                    fw.setFireworkMeta(fm);
                });
                break;
            }
        }
    }

    private void jetpack(final Player p, final int duration) {
        if (!this.jetpacking.add(p.getUniqueId())) {
            return;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);
        final EemiCore plugin = this;
        new BukkitRunnable(){
            int t;
            final EemiCore this$0 = plugin;

            public void run() {
                if (!p.isOnline() || !this.this$0.inLobby(p) || ++this.t > duration || p.getLocation().getY() > this.this$0.getConfig().getDouble("jetpack-max-y", 120.0)) {
                    this.this$0.jetpacking.remove(p.getUniqueId());
                    this.this$0.noFallUntil.put(p.getUniqueId(), System.currentTimeMillis() + 8000L);
                    this.cancel();
                    return;
                }
                Vector v = p.getLocation().getDirection().setY(0).multiply(0.45);
                v.setY(0.42);
                p.setVelocity(v);
                p.setFallDistance(0.0f);
                Location feet = p.getLocation().add(0.0, 0.2, 0.0);
                p.getWorld().spawnParticle(Particle.FLAME, feet, 4, 0.12, 0.05, 0.12, 0.02);
                p.getWorld().spawnParticle(Particle.CLOUD, feet, 2, 0.15, 0.05, 0.15, 0.01);
                if (this.t % 4 == 0) {
                    p.getWorld().playSound(feet, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.8f);
                }
            }
        }.runTaskTimer((Plugin)this, 0L, 1L);
    }

    @EventHandler
    public void onGrapple(PlayerFishEvent e) {
        boolean hooked;
        Player p = e.getPlayer();
        ItemStack rod = p.getInventory().getItemInMainHand();
        String id = this.gadgetOf(rod);
        ConfigurationSection s = this.cosmetic(id);
        if (s == null || !"grappin".equals(s.getString("gadget")) || !this.inLobby(p)) {
            return;
        }
        PlayerFishEvent.State st = e.getState();
        if (st == PlayerFishEvent.State.FISHING || st == PlayerFishEvent.State.BITE) {
            return;
        }
        Location hook = e.getHook().getLocation();
        boolean bl = hooked = st == PlayerFishEvent.State.IN_GROUND || e.getHook().isOnGround() || !hook.clone().subtract(0.0, 0.3, 0.0).getBlock().isPassable();
        if (!hooked) {
            return;
        }
        if (!this.gadgetReady(p, s)) {
            return;
        }
        Vector v = hook.toVector().subtract(p.getLocation().toVector());
        double dist = v.length();
        if (dist < 1.5) {
            return;
        }
        v.normalize().multiply(Math.min(2.4, 0.5 + dist * 0.12));
        v.setY(v.getY() + 0.45);
        p.setVelocity(v);
        this.noFallUntil.put(p.getUniqueId(), System.currentTimeMillis() + 6000L);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.7f);
    }

    @EventHandler
    public void onPaint(ProjectileHitEvent e) {
        Snowball ball;
        Projectile projectile = e.getEntity();
        if (!(projectile instanceof Snowball) || !(ball = (Snowball)projectile).getPersistentDataContainer().has(this.tagKey, PersistentDataType.BYTE)) {
            return;
        }
        if (e.getHitEntity() != null) {
            e.setCancelled(true);
        }
        Location at = e.getHitBlock() != null ? e.getHitBlock().getLocation() : ball.getLocation();
        Material[] wools = new Material[]{Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL, Material.MAGENTA_WOOL, Material.PINK_WOOL, Material.PURPLE_WOOL};
        Material wool = wools[ThreadLocalRandom.current().nextInt(wools.length)];
        HashMap<Location, BlockData> fake = new HashMap<Location, BlockData>();
        for (int dx = -2; dx <= 2; ++dx) {
            for (int dy = -2; dy <= 2; ++dy) {
                for (int dz = -2; dz <= 2; ++dz) {
                    Block b;
                    if (dx * dx + dy * dy + dz * dz > 5 || ThreadLocalRandom.current().nextInt(4) == 0 || !(b = at.clone().add((double)dx, (double)dy, (double)dz).getBlock()).getType().isSolid()) continue;
                    boolean exposed = false;
                    for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                        if (!b.getRelative(f).isPassable()) continue;
                        exposed = true;
                    }
                    if (!exposed) continue;
                    fake.put(b.getLocation(), wool.createBlockData());
                }
            }
        }
        if (fake.isEmpty()) {
            return;
        }
        ArrayList<Player> viewers = new ArrayList<Player>();
        for (Player o : at.getWorld().getPlayers()) {
            if (!(o.getLocation().distanceSquared(at) < 4096.0)) continue;
            viewers.add(o);
        }
        for (Player o : viewers) {
            o.sendMultiBlockChange(fake);
        }
        at.getWorld().spawnParticle(Particle.ITEM_SLIME, at.clone().add(0.5, 1.0, 0.5), 12, 0.6, 0.3, 0.6, 0.05);
        at.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, 0.8f, 1.2f);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            HashMap<Location, BlockData> real = new HashMap<Location, BlockData>();
            for (Location l : fake.keySet()) {
                real.put(l, l.getBlock().getBlockData());
            }
            for (Player o : viewers) {
                if (!o.isOnline() || !o.getWorld().equals(at.getWorld())) continue;
                o.sendMultiBlockChange(real);
            }
        }, 60L);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onGadgetDamage(EntityDamageEvent e) {
        Player p;
        Long until;
        Entity entity;
        EntityDamageByEntityEvent by;
        if (e instanceof EntityDamageByEntityEvent && (by = (EntityDamageByEntityEvent)e).getDamager().getPersistentDataContainer().has(this.tagKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && (entity = e.getEntity()) instanceof Player && (until = this.noFallUntil.get((p = (Player)entity).getUniqueId())) != null && until > System.currentTimeMillis()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onGadgetClick(InventoryClickEvent e) {
        Player p;
        HumanEntity humanEntity;
        if (this.gadgetOf(e.getCurrentItem()) != null || this.gadgetOf(e.getCursor()) != null) {
            e.setCancelled(true);
        }
        if (e.getClick() == ClickType.NUMBER_KEY && (humanEntity = e.getWhoClicked()) instanceof Player && this.gadgetOf((p = (Player)humanEntity).getInventory().getItem(e.getHotbarButton())) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onGadgetDrop(PlayerDropItemEvent e) {
        if (this.gadgetOf(e.getItemDrop().getItemStack()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onGadgetSwap(PlayerSwapHandItemsEvent e) {
        if (this.gadgetOf(e.getMainHandItem()) != null || this.gadgetOf(e.getOffHandItem()) != null) {
            e.setCancelled(true);
        }
    }

    // ================================================================ DA des menus du lobby (pack : EemicraftPack/art_lobby.py)

    /** Fonds dessines actifs (config lobby-ui.enabled). */
    static boolean UI;
    /** Meme ordre que BACKGROUNDS de EemicraftPack/art_lobby.py : caractere U+E310 + index (confirm = U+E31F, 1.6.1). */
    private static final List<String> UI_BACKGROUNDS = List.of("selector", "iles", "survie", "equipes", "combat", "tnt", "ambiance",
            "profile", "players", "toplevel", "shop", "shopcat", "quests", "party", "pinvite", "confirm");
    /** Icones de jeux du pack (eemicraft:lobby/<nom>). */
    private static final java.util.Set<String> UI_GAME_ICONS = java.util.Set.of("skyblock", "abysse", "oneblock", "jump", "uhc", "speedrun", "bingo",
            "towers", "thebridge", "bedwars", "ballsofsteel", "sheepwars", "paintball", "skywars", "hungergames", "pvp", "pitchout", "jumpleague",
            "tntrun", "tnttag", "bowspleef", "pvprun", "tntwars", "tntwizards", "horscours", "murder", "loupgarou", "hideseek", "blockparty",
            "buildbattle", "boatrace", "deacoudre", "elytra", "soleil", "infection", "dropper");
    private static final String[] UI_CATEGORY_ICONS = new String[]{"trainee", "chapeau", "titre", "arrivee", "gadget"};
    /** Boutique, page de categorie (1.6.1) : cartes en quinconce, case d'etat juste en dessous (+9). Meme liste que SHOP_CARDS de art_lobby.py. */
    private static final int[] SHOP_CARD_SLOTS = new int[]{9, 11, 13, 15, 17, 28, 30, 32, 34};

    /** 12345 -> "12 345" (espace) : prix lisibles dans les menus. */
    static String num(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    /** Titre de menu : fond dessine (glyphe de la police par defaut) puis le texte en blanc. */
    static Component uiTitle(String kind, String text) {
        int idx = UI_BACKGROUNDS.indexOf(kind);
        if (!UI || idx < 0) {
            return EemiCore.c(text);
        }
        String t = text.startsWith("&") && text.length() > 1 ? text.substring(2) : text;
        // Police a part (eemicraft:lobby) : insensible a l'option "police Unicode" du client.
        // Fond et texte en freres (pas parent/enfant) pour que le texte garde la police normale.
        Component bg = Component.text("\ue300" + (char) (0xE310 + idx) + "\ue301")
                .font(net.kyori.adventure.key.Key.key("eemicraft", "lobby"))
                .color(net.kyori.adventure.text.format.NamedTextColor.WHITE);
        return Component.textOfChildren(bg, EemiCore.c("&f" + t));
    }

    /** Glyphes des fonds de menus (U+E300..U+E3FF) : retires de tout ce que tapent les joueurs. */
    static String stripUi(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder b = null;
        for (int i = 0; i < s.length(); ++i) {
            char ch = s.charAt(i);
            if (ch >= '\ue300' && ch <= '\ue3ff') {
                if (b == null) {
                    b = new StringBuilder(s.length());
                    b.append(s, 0, i);
                }
                continue;
            }
            if (b != null) {
                b.append(ch);
            }
        }
        return b == null ? s : b.toString();
    }

    static Component stripUi(Component c) {
        return c == null ? null : c.replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                .match("[\\x{E300}-\\x{E3FF}]+").replacement("").build());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUiChat(AsyncChatEvent e) {
        e.message(stripUi(e.message()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUiCommand(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage();
        String s = stripUi(m);
        if (s != m) {
            e.setMessage(s);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUiSign(org.bukkit.event.block.SignChangeEvent e) {
        for (int i = 0; i < e.lines().size(); ++i) {
            Component l = e.line(i);
            if (l != null) {
                e.line(i, stripUi(l));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUiAnvil(org.bukkit.event.inventory.PrepareAnvilEvent e) {
        ItemStack r = e.getResult();
        if (r == null || !r.hasItemMeta() || !r.getItemMeta().hasDisplayName()) {
            return;
        }
        ItemMeta m = r.getItemMeta();
        m.displayName(stripUi(m.displayName()));
        r.setItemMeta(m);
        e.setResult(r);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUiBook(org.bukkit.event.player.PlayerEditBookEvent e) {
        org.bukkit.inventory.meta.BookMeta m = e.getNewBookMeta();
        java.util.List<Component> pages = new java.util.ArrayList<>();
        for (Component pg : m.pages()) {
            pages.add(stripUi(pg));
        }
        m.pages(pages);
        if (m.hasTitle()) {
            m.setTitle(stripUi(m.getTitle()));
        }
        e.setNewBookMeta(m);
    }

    /** Pose l'icone dessinee du pack sur un objet de menu (sans effet si la DA est coupee). */
    static ItemStack ic(ItemStack it, String icon) {
        if (!UI || it == null || icon == null) {
            return it;
        }
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("eemicraft", "lobby/" + icon));
        it.setItemMeta(meta);
        return it;
    }

    /** 1.6.2 : objet reel d'un cosmetique dans les menus (boutique, confirmation) : chapeau = le bloc porte
     *  (material, sinon icon), autres = icon (sinon material) ; jamais d'icone eemicraft:lobby. Les gadgets gardent
     *  leur modele eemicraft:gadget/* si lobby-ui.gadget-models. */
    private ItemStack cosmeticItem(ConfigurationSection s, String name, List<String> lore, boolean glow) {
        boolean hat = "hat".equals(s.getString("type"));
        String first = s.getString(hat ? "material" : "icon");
        String second = s.getString(hat ? "icon" : "material");
        Material m = first == null ? null : Material.matchMaterial(first);
        if ((m == null || !m.isItem()) && second != null) {
            m = Material.matchMaterial(second);
        }
        ItemStack it = EemiCore.item(m == null || !m.isItem() ? Material.PAPER : m, name, lore, glow);
        String kind = s.getString("gadget", "");
        if (UI && "gadget".equals(s.getString("type")) && this.getConfig().getBoolean("lobby-ui.gadget-models", true) && List.of("feux", "peinture", "grappin", "jetpack").contains(kind)) {
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("eemicraft", "gadget/" + kind));
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Icone d'un jeu (nom de l'icone du pack, ou null : on garde l'objet du registre). */
    static String gameIcon(String id) {
        if (id == null) {
            return null;
        }
        String k = id.toLowerCase(Locale.ROOT);
        if (k.equals("duels") || k.equals("ffa")) {
            return "pvp";
        }
        if (k.equals("casino")) {
            return "cat_casino";
        }
        return UI_GAME_ICONS.contains(k) ? k : null;
    }

    private static ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(EemiCore.c(name));
        ArrayList<Component> l = new ArrayList<Component>();
        for (String s : lore) {
            l.add(EemiCore.c(s));
        }
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        if (glow) {
            meta.setEnchantmentGlintOverride(Boolean.valueOf(true));
        }
        it.setItemMeta(meta);
        return it;
    }

    private void openLater(Player p, Inventory inv) {
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            if (p.isOnline()) {
                p.openInventory(inv);
            }
        }, 2L);
    }

    // ================================================================ 1.8.0 : menus au style Refuge (pack : EemiCore/pack/gen_nav.py)

    /** Menus dessinés façon Refuge (config lobby-ui.style: refuge, défaut). Sinon les anciens menus. */
    static boolean NAV;

    private static final Map<String, String> NAV_NAMES = Map.of("oneblock", "&6OneBlock", "abysse", "&5L'Abysse",
            "horscours", "&fHors Cours", "duels", "&cDuels et FFA", "casino", "&6Casino");
    private static final Map<String, List<String>> NAV_DESC = new HashMap<String, List<String>>();
    static {
        NAV_DESC.put("skyblock", List.of("Ton île, tes minions, tes compétences", "Bazar, hôtel des ventes, banque, coop"));
        NAV_DESC.put("abysse", List.of("Descends strate par strate dans le gouffre", "Mine, monte ton usine, vends à la bourse"));
        NAV_DESC.put("oneblock", List.of("Un seul bloc qui se régénère sans fin", "Casse-le pour passer les phases"));
        NAV_DESC.put("jump", List.of("250 niveaux de parkour en forêt", "Tu reprends au dernier niveau atteint"));
        NAV_DESC.put("uhc", List.of("Monde neuf à chaque partie", "Pas de régénération naturelle", "Solo, duo ou trio"));
        NAV_DESC.put("speedrun", List.of("Survie dans un monde neuf", "Le premier qui trouve l'objet gagne"));
        NAV_DESC.put("bingo", List.of("Trouve les items de ta grille", "Un item trouvé est bloqué pour les autres", "Solo, duo ou quatuor"));
        NAV_DESC.put("bedwars", List.of("Solo, duo ou équipes de 4", "Protège ton lit, détruis les autres !"));
        NAV_DESC.put("thebridge", List.of("Rouge contre Bleu, de 1v1 à 4v4", "Traverse le pont, saute dans le but adverse"));
        NAV_DESC.put("towers", List.of("Rouge contre Bleu", "Saute dans le bassin adverse !"));
        NAV_DESC.put("sheepwars", List.of("Rouge contre Bleu, une île chacun", "Lance des moutons sur l'île adverse"));
        NAV_DESC.put("ballsofsteel", List.of("Jusqu'à 4 équipes", "Mine les boules de diamant, dépose-les au centre !"));
        NAV_DESC.put("paintball", List.of("Rouge contre Bleu sur un terrain speedball", "Une bille = éliminé, première équipe à 30"));
        NAV_DESC.put("skywars", List.of("Chacun sur son île, jusqu'à 16 joueurs", "Pille les coffres, pousse les autres dans le vide"));
        NAV_DESC.put("hungergames", List.of("Chacun pour soi, une seule vie", "Pille les coffres, survis à la bordure"));
        NAV_DESC.put("duels", List.of("Duels 1v1 classés ou non", "Classic, NoDebuff, Sumo, Boxing, BuildUHC", "Arène FFA, stats et classements"));
        NAV_DESC.put("pitchout", List.of("Chacun pour soi, 5 vies", "Pousse les autres dans le vide !"));
        NAV_DESC.put("jumpleague", List.of("10 modules de parcours, un coffre par module", "Puis deathmatch : 3 vies"));
        NAV_DESC.put("tntrun", List.of("Le sol disparaît sous tes pieds", "Dernier debout gagne !"));
        NAV_DESC.put("tnttag", List.of("Refile la TNT avant qu'elle explose", "Dernier survivant gagne !"));
        NAV_DESC.put("bowspleef", List.of("Tire sur le sol de TNT pour le faire disparaître", "Dernier debout gagne !"));
        NAV_DESC.put("pvprun", List.of("TNT Run avec une épée", "Un kill rend 3 cœurs"));
        NAV_DESC.put("tntwars", List.of("Rouge contre Bleu, de part et d'autre d'un canal", "Construis des canons à TNT !"));
        NAV_DESC.put("tntwizards", List.of("Rouge contre Bleu, baguettes magiques", "Capture les points, 1000 points pour gagner"));
        NAV_DESC.put("murder", List.of("Un meurtrier, un détective, des innocents", "Ramasse l'or et survis !"));
        NAV_DESC.put("loupgarou", List.of("Village contre loups-garous", "La partie se lance quand assez de joueurs sont là"));
        NAV_DESC.put("hideseek", List.of("Déguise-toi en bloc dans le village", "ou trouve les cacheurs avant la fin !"));
        NAV_DESC.put("blockparty", List.of("La musique s'arrête, une couleur s'affiche", "Cours dessus avant que le sol disparaisse !"));
        NAV_DESC.put("buildbattle", List.of("Un thème, 5 minutes pour construire", "Puis tout le monde vote !"));
        NAV_DESC.put("boatrace", List.of("Course de bateaux façon Mario Kart", "Boîtes ? : encre, éclair, banane, turbo..."));
        NAV_DESC.put("deacoudre", List.of("Chacun son tour, saute du plongeoir", "dans la piscine sans toucher les blocs"));
        NAV_DESC.put("soleil", List.of("Avance quand la poupée chante", "Ne bouge plus quand elle te regarde"));
        NAV_DESC.put("infection", List.of("Survivants contre zombies, de nuit en ville", "Un coup de zombie et tu es infecté"));
        NAV_DESC.put("elytra", List.of("3 parcours : canyon, îles flottantes, grotte", "Passe les anneaux dans l'ordre"));
        NAV_DESC.put("dropper", List.of("5 niveaux tirés au hasard", "Tombe dans le puits sans toucher les obstacles"));
        NAV_DESC.put("horscours", List.of("Campagne horreur dans l'EEMI", "Seul ou jusqu'à 4, ne quitte pas Loïc des yeux..."));
        NAV_DESC.put("casino", List.of("Machines à sous, blackjack, roulette,", "roue de la fortune, plinko, poker"));
    }

    /** État d'un jeu pour les menus : texte court (petites capitales), couleur, nombres, drapeaux. */
    private record NavStatus(String text, String color, int waiting, int playing, boolean closed, boolean beta, String reason) {
    }

    private Inventory navInv(Menu holder, String menu, List<String> ov, List<NavUi.Ligne> lines) {
        NavFonds.Disposition d = NavFonds.MENUS.get(menu);
        Inventory inv = Bukkit.createInventory((InventoryHolder)holder, d.lignes() * 9, NavUi.title(menu, ov, lines));
        holder.inventory = inv;
        return inv;
    }

    private static ItemStack navItem(String name, List<String> lore) {
        ItemStack it = EemiCore.item(Material.PAPER, name, lore, false);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(NavUi.MODEL_VIDE);
        it.setItemMeta(m);
        return it;
    }

    /** Pose l'objet invisible d'un bouton sur toutes ses cases. */
    private void navBtn(Menu h, Inventory inv, String menu, String id, String name, List<String> lore, String action) {
        int[] slots = NavFonds.MENUS.get(menu).boutons().get(id);
        if (slots == null) {
            return;
        }
        ItemStack it = EemiCore.navItem(name, lore);
        for (int s : slots) {
            inv.setItem(s, it);
            if (action != null) {
                h.actions.put(s, action);
            }
        }
    }

    private static int[] navCells(String menu, String id) {
        int[] c = NavFonds.MENUS.get(menu).cases().get(id);
        return c == null ? new int[0] : c;
    }

    /** ✕ et i de l'en-tête. */
    private void navHead(Menu h, Inventory inv, String menu, List<String> info) {
        this.navBtn(h, inv, menu, "fermer", "&cFermer", List.of(), "close");
        if (info != null) {
            this.navBtn(h, inv, menu, "info", "&9Aide", info, null);
        }
    }

    private void navPager(Menu h, Inventory inv, String menu, String back, String backName, int page, int pages, String action) {
        this.navBtn(h, inv, menu, "retour", backName == null ? "&eRetour" : backName, List.of(), back);
        if (page > 0) {
            this.navBtn(h, inv, menu, "prec", "&ePage précédente", List.of("&7Page " + page + "/" + pages), action + ":" + (page - 1));
        }
        if (page < pages - 1) {
            this.navBtn(h, inv, menu, "suiv", "&ePage suivante", List.of("&7Page " + (page + 2) + "/" + pages), action + ":" + (page + 1));
        }
    }

    private String placeholder(Player p, String ph) {
        if (!this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return "";
        }
        try {
            String s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((OfflinePlayer)p, ph);
            return s == null || s.contains("%") ? "" : s;
        }
        catch (Throwable t) {
            return "";
        }
    }

    private int worldPrefixCount(String prefix) {
        int n = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.hasMetadata("vanished") || !o.getWorld().getName().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            ++n;
        }
        return n;
    }

    private NavStatus navStatus(Player p, String id) {
        if (id.equals("horscours") && !p.isOp() && !p.hasPermission("eemihorscours.play")) {
            return new NavStatus("EN TEST", "&8", 0, 0, true, false, "Réservé aux admins pendant les tests");
        }
        // EemiDroits (bêta / maintenance par jeu), s'il est installé
        String etat = this.placeholder(p, "%eemidroits_etat_" + id + "%").toLowerCase(Locale.ROOT);
        boolean beta = etat.contains("bêta") || etat.contains("beta");
        boolean droitsMaint = etat.contains("maintenance");
        boolean denied = "false".equals(this.placeholder(p, "%eemidroits_acces_" + id + "%"));
        GameInfo g = this.game(id);
        Maintenance mt = g == null ? null : this.maintenance.get(g.id);
        if (mt != null || droitsMaint) {
            String reason = mt != null && mt.reason() != null && !mt.reason().isBlank() ? mt.reason() : null;
            return new NavStatus("MAINTENANCE", "&8", 0, 0, true, false, reason);
        }
        int waiting = 0;
        int playing;
        String text;
        String color;
        if (g == null) {
            playing = this.worldPrefixCount(id);
            text = playing == 0 ? "PERSONNE" : playing + " EN JEU";
            color = playing == 0 ? "&7" : "&3";
        } else if (g.fresh(this.expireMs)) {
            playing = g.playing;
            switch (g.state) {
                case "starting": {
                    waiting = g.waiting;
                    text = "DÉMARRAGE";
                    color = "&e";
                    break;
                }
                case "ending": {
                    text = "FIN DE PARTIE";
                    color = "&6";
                    break;
                }
                default: {
                    waiting = g.waiting;
                    if (waiting > 0) {
                        text = waiting + "/" + g.max + " ATTENTE";
                        color = "&a";
                    } else if (playing > 0) {
                        text = playing + " EN JEU";
                        color = "&c";
                    } else {
                        text = "PERSONNE";
                        color = "&7";
                    }
                }
            }
        } else {
            playing = g.worldPlayers();
            text = playing == 0 ? "PERSONNE" : playing + " EN JEU";
            color = playing == 0 ? "&7" : "&3";
        }
        if (beta && waiting == 0 && playing == 0) {
            text = "BÊTA";
            color = "&d";
        }
        return new NavStatus(text, color, waiting, playing, beta && denied, beta, beta && denied ? "Réservé aux bêta-testeurs" : null);
    }

    private String navName(String id) {
        String n = NAV_NAMES.get(id);
        if (n != null) {
            return n;
        }
        GameInfo g = this.game(id);
        return g == null ? "&f" + id : g.name;
    }

    private List<String> navGameLore(Player p, String id, NavStatus st) {
        ArrayList<String> lore = new ArrayList<String>();
        for (String l : NAV_DESC.getOrDefault(id, List.of())) {
            lore.add("&7" + l);
        }
        lore.add("");
        GameInfo g = this.game(id);
        if (st.closed()) {
            lore.add("&8" + (st.text().equals("MAINTENANCE") ? "En maintenance" : "Fermé"));
            if (st.reason() != null) {
                lore.add("&7" + st.reason());
            }
            return lore;
        }
        lore.add(g != null ? this.statusText(g) : (st.playing() == 0 ? "&7Aucun joueur" : "&3" + st.playing() + " joueur" + (st.playing() > 1 ? "s" : "")));
        if (st.beta()) {
            lore.add("&dEn bêta");
        }
        lore.add("");
        boolean menu = g != null && EemiCore.isMenuJoin(g);
        lore.add(menu ? "&eClic : choisir un mode" : "&eClic : jouer");
        if (g != null && g.spectate != null && !g.spectate.isBlank() && st.playing() > 0) {
            lore.add("&bClic droit : regarder une partie");
        }
        Party party = this.partyOf.get(p.getUniqueId());
        if (party != null && party.members.size() > 1) {
            lore.add(party.leader.equals(p.getUniqueId()) ? "&9Ton groupe te suivra" : "&cSeul le chef du groupe lance une partie");
        }
        return lore;
    }

    /** Accueil du navigateur (/jeux, moustache du lobby). */
    void openNav(Player p) {
        Menu h = new Menu("jeux");
        ArrayList<String> ov = new ArrayList<String>();
        ArrayList<NavUi.Ligne> lines = new ArrayList<NavUi.Ligne>();
        int online = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.hasMetadata("vanished")) ++online;
        }
        int inGame = 0;
        Map<String, List<String>> lore = new HashMap<String, List<String>>();
        for (String cat : NavFonds.CATEGORIES) {
            int w = 0;
            int pl = 0;
            ArrayList<String> names = new ArrayList<String>();
            for (String page : NavFonds.PAGES.getOrDefault(cat, List.of())) {
                for (String id : NavFonds.JEUX.getOrDefault(page, List.of())) {
                    NavStatus st = this.navStatus(p, id);
                    w += st.waiting();
                    pl += st.playing();
                    names.add(this.navName(id) + (st.waiting() > 0 ? " &a(" + st.waiting() + " en attente)" : st.playing() > 0 ? " &7(" + st.playing() + " en jeu)" : ""));
                }
            }
            inGame += pl + w;
            if (w > 0) {
                ov.add("on:" + cat);
                lines.add(new NavUi.Ligne("s_" + cat, w + " EN ATTENTE", NavUi.colorOf("&a")));
            } else {
                lines.add(new NavUi.Ligne("s_" + cat, pl == 0 ? "PERSONNE" : pl + " EN JEU", NavUi.colorOf(pl == 0 ? "&7" : "&3")));
            }
            ArrayList<String> l = new ArrayList<String>();
            for (String n : names) l.add("&8- " + n);
            l.add("");
            l.add("&eClic : voir les jeux");
            lore.put(cat, l);
        }
        NavStatus casino = this.navStatus(p, "casino");
        if (casino.playing() > 0) {
            ov.add("on:casino");
        }
        lines.add(0, new NavUi.Ligne("haut", online + " EN LIGNE · " + inGame + " EN JEU"));
        Inventory inv = this.navInv(h, "jeux", ov, lines);
        this.navHead(h, inv, "jeux", List.of("&7Choisis une catégorie puis un jeu.", "&7Le chiffre sous chaque catégorie :", "&7joueurs en attente ou en partie.", "", "&9Groupe : &7le chef lance, les autres suivent."));
        String[] catNames = {"&b&lÎles et aventure", "&a&lSurvie", "&9&lÉquipes", "&c&lCombat", "&6&lJeux TNT", "&d&lAmbiance"};
        for (int i = 0; i < NavFonds.CATEGORIES.size(); ++i) {
            String cat = NavFonds.CATEGORIES.get(i);
            this.navBtn(h, inv, "jeux", cat, catNames[i], lore.get(cat), "navcat:" + cat + ":0");
        }
        this.navBtn(h, inv, "jeux", "hasard", "&e&lPartie au hasard", List.of("&7Rejoint le jeu où il y a", "&7le plus de monde en attente", "", "&eClic : jouer"), "random");
        this.navBtn(h, inv, "jeux", "casino", "&6&lCasino", List.of("&7Machines à sous, blackjack, roulette,", "&7roue de la fortune, plinko, poker", "", casino.playing() > 0 ? "&3" + casino.playing() + " joueur(s) au casino" : "&7Personne au casino", "", "&eClic : entrer"), "play:casino");
        Party party = this.partyOf.get(p.getUniqueId());
        this.navBtn(h, inv, "jeux", "groupe", "&9&lGroupe", List.of(party == null ? "&7Tu n'es dans aucun groupe" : "&7Groupe de " + this.nameOf(party.leader) + " (" + party.members.size() + "/8)", "&7Joue avec tes amis", "", "&eClic : ouvrir"), "party");
        this.openLater(p, inv);
    }

    /** Page d'une catégorie du navigateur. */
    void openNavCat(Player p, String cat, int page) {
        List<String> pages = NavFonds.PAGES.get(cat);
        if (pages == null || pages.isEmpty()) {
            this.openNav(p);
            return;
        }
        page = Math.max(0, Math.min(page, pages.size() - 1));
        String menu = pages.get(page);
        List<String> ids = NavFonds.JEUX.getOrDefault(menu, List.of());
        Menu h = new Menu("navcat");
        ArrayList<String> ov = new ArrayList<String>();
        ArrayList<NavUi.Ligne> lines = new ArrayList<NavUi.Ligne>();
        ArrayList<NavStatus> sts = new ArrayList<NavStatus>();
        int w = 0;
        int pl = 0;
        for (int k = 0; k < ids.size(); ++k) {
            NavStatus st = this.navStatus(p, ids.get(k));
            sts.add(st);
            w += st.waiting();
            pl += st.playing();
            if (st.closed()) {
                ov.add("off:g" + k);
            } else if (st.waiting() > 0) {
                ov.add("on:g" + k);
            } else if (st.beta()) {
                ov.add("sel:g" + k);
            }
            lines.add(new NavUi.Ligne("s_g" + k, st.text(), NavUi.colorOf(st.color())));
        }
        String haut = w > 0 ? w + " EN ATTENTE" + (pl > 0 ? " · " + pl + " EN JEU" : "") : (pl > 0 ? pl + " EN JEU" : "PERSONNE POUR L'INSTANT");
        lines.add(new NavUi.Ligne("haut", haut));
        if (pages.size() > 1) {
            lines.add(new NavUi.Ligne("page", (page + 1) + "/" + pages.size()));
        }
        Inventory inv = this.navInv(h, menu, ov, lines);
        this.navHead(h, inv, menu, List.of("&7Cadre &avert &7: des joueurs attendent,", "&7rejoins-les !", "&7Cadre &6doré &7: jeu en bêta", "&7Grisé : maintenance ou fermé"));
        for (int k = 0; k < ids.size(); ++k) {
            String id = ids.get(k);
            this.navBtn(h, inv, menu, "g" + k, this.navName(id), this.navGameLore(p, id, sts.get(k)), "play:" + id);
        }
        this.navPager(h, inv, menu, "nav", "&eRetour aux catégories", page, pages.size(), "navcat:" + cat);
        this.openLater(p, inv);
    }

    /** Lance un jeu depuis le navigateur. Passe par le chat (/commande) pour que les autres plugins (EemiDroits,
     *  suivi du groupe) voient la commande comme si le joueur l'avait tapée. */
    private void navPlay(Player p, String id, boolean right) {
        NavStatus st = this.navStatus(p, id);
        GameInfo g = this.game(id);
        if (right && g != null && g.spectate != null && !g.spectate.isBlank() && st.playing() > 0) {
            p.closeInventory();
            this.spectate(p, g);
            return;
        }
        if (st.closed()) {
            this.uiSound(p, "erreur");
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&c" + EemiCore.strip(this.navName(id)) + " est fermé" + (st.reason() != null ? " : " + st.reason() : " pour l'instant."));
            return;
        }
        String cmd;
        switch (id) {
            case "duels": cmd = "duel"; break;
            case "oneblock": cmd = "ob"; break;
            case "abysse": cmd = "abysse"; break;
            case "horscours": cmd = "horscours join"; break;
            default: cmd = g != null ? "eemi join " + id : null;
        }
        if (cmd == null) {
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cCe jeu n'est pas disponible.");
            return;
        }
        this.uiSound(p, "clic");
        p.closeInventory();
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            if (p.isOnline()) {
                p.chat("/" + cmd);
            }
        });
    }

    private static String strip(String legacy) {
        return legacy == null ? "" : legacy.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    // ---------------------------------------------------------------- profil

    private List<String> navStatLore(PlayerData d, String type, String unit) {
        ArrayList<GameInfo> list = new ArrayList<GameInfo>(this.games.values());
        list.removeIf(g -> d.stat(g.id, type) == 0);
        list.sort(Comparator.comparingInt((GameInfo g) -> d.stat(g.id, type)).reversed());
        ArrayList<String> lore = new ArrayList<String>();
        if (list.isEmpty()) {
            lore.add("&7Rien pour l'instant");
        }
        for (int i = 0; i < list.size() && i < 10; ++i) {
            lore.add("&8- " + list.get(i).name + " &7: &f" + d.stat(list.get(i).id, type) + " " + unit);
        }
        return lore;
    }

    private void navProfile(Player viewer, PlayerData d) {
        Menu h = new Menu("profile");
        long need = this.xpNeeded(d.level);
        int bar = need <= 0 ? 0 : (int)Math.max(0L, Math.min(20L, d.xp * 20L / need));
        boolean own = viewer.getUniqueId().equals(d.uuid);
        List<NavUi.Ligne> lines = List.of(
                new NavUi.Ligne("haut", d.name + " · " + EemiCore.num(d.coins) + " ⛃", NavUi.colorOf(this.levelColor(d.level))),
                new NavUi.Ligne("parties", EemiCore.num(d.total("plays"))), new NavUi.Ligne("victoires", EemiCore.num(d.total("wins"))),
                new NavUi.Ligne("kills", EemiCore.num(d.total("kills"))), new NavUi.Ligne("serie", d.streak + " J"),
                new NavUi.Ligne("niveau", "NIV " + d.level, NavUi.colorOf(this.levelColor(d.level))),
                new NavUi.Ligne("xp", "XP " + EemiCore.num(d.xp) + " / " + EemiCore.num(need)));
        Inventory inv = this.navInv(h, "profil", List.of("bar:xp:" + bar), lines);
        this.navHead(h, inv, "profil", List.of("&7Gagne de l'xp et des pièces en jouant :", "&7partie jouée, victoire, kill,", "&7quêtes du jour et connexion quotidienne."));
        this.navBtn(h, inv, "profil", "parties", "&fParties jouées : " + d.total("plays"), this.navStatLore(d, "plays", "parties"), null);
        this.navBtn(h, inv, "profil", "victoires", "&aVictoires : " + d.total("wins"), this.navStatLore(d, "wins", "victoires"), null);
        this.navBtn(h, inv, "profil", "kills", "&cKills : " + d.total("kills"), this.navStatLore(d, "kills", "kills"), null);
        this.navBtn(h, inv, "profil", "serie", "&eSérie de connexion : " + d.streak + " jour" + (d.streak > 1 ? "s" : ""), List.of("&7Connecte-toi chaque jour pour", "&7une récompense de plus en plus grosse."), null);
        int[] head = EemiCore.navCells("profil", "tete");
        if (head.length > 0) {
            ArrayList<String> lore = new ArrayList<String>(this.levelLore(d));
            String t = this.equippedTitle(d);
            lore.add(t.isEmpty() ? "&7Titre : &8aucun" : "&7Titre : " + t);
            inv.setItem(head[0], EemiCore.head(d.uuid, this.levelColor(d.level) + d.name, lore));
        }
        PlayerData me = this.data(viewer);
        this.navBtn(h, inv, "profil", "boutique", "&aBoutique", List.of("&7Tes pièces : &6" + EemiCore.num(me.coins), "&7Traînées, chapeaux, titres...", "", "&eClic : ouvrir"), "shop");
        this.navBtn(h, inv, "profil", "quetes", "&eQuêtes du jour", List.of("&7Gagne des pièces et de l'xp", "", "&eClic : voir"), "quests");
        this.navBtn(h, inv, "profil", "joueurs", "&bJoueurs en ligne", List.of("&7Niveau et xp des joueurs connectés", "", "&eClic : voir"), "players");
        this.navBtn(h, inv, "profil", "classement", "&6Top 10 des niveaux", List.of("&7Les meilleurs joueurs du serveur", "", "&eClic : voir"), "toplevel");
        if (!own) {
            // le profil d'un autre : ✕ reste, Aide remplacée par le retour à son propre profil
            this.navBtn(h, inv, "profil", "info", "&eRetour à mon profil", List.of("&7Niveau " + this.levelColor(me.level) + me.level), "profile:" + me.uuid);
        }
        this.openLater(viewer, inv);
    }

    // ---------------------------------------------------------------- boutique

    private void navShop(Player p) {
        PlayerData d = this.data(p);
        this.ensureQuests(d);
        Menu h = new Menu("shop");
        ArrayList<String> ov = new ArrayList<String>();
        ArrayList<NavUi.Ligne> lines = new ArrayList<NavUi.Ligne>();
        lines.add(new NavUi.Ligne("haut", EemiCore.num(d.coins) + " ⛃ PIÈCES", NavUi.colorOf("&6")));
        Map<String, List<String>> lore = new HashMap<String, List<String>>();
        this.saisonView.remove(p.getUniqueId());
        for (int i = 0; i < CATEGORIES.length; ++i) {
            String cat = CATEGORIES[i];
            List<String> ids = this.cosmeticsOf(cat);
            ids.removeIf(id -> !this.shopVisible(d, id) || this.saisonPick(d, id));
            int owned = 0;
            for (String id : ids) {
                if (d.owned.contains(id)) ++owned;
            }
            ConfigurationSection eq = this.worn(d.equipped.get(cat));
            if (eq != null) {
                ov.add("sel:" + cat);
            }
            lines.add(new NavUi.Ligne("s_" + cat, owned + " / " + ids.size(), NavUi.colorOf(owned > 0 ? "&a" : "&7")));
            lore.put(cat, this.categoryLore(i, owned, ids.size(), eq));
        }
        // 1.9.4 : la case « quetes » de l'accueil de la boutique devient « Saison » (les quetes restent dans le profil)
        List<String> saison = this.saisonIds(d);
        lines.add(new NavUi.Ligne("s_quetes", saison.isEmpty() ? "SAISON : RIEN" : "✦ SAISON : " + saison.size(), NavUi.colorOf(saison.isEmpty() ? "&7" : "&6")));
        lines.add(new NavUi.Ligne("bas", "GAGNE DES PIÈCES EN JOUANT"));
        Inventory inv = this.navInv(h, "boutique", ov, lines);
        this.navHead(h, inv, "boutique", List.of("&7Cadre &6doré &7: un cosmétique de la", "&7catégorie est équipé.", "", "&7Pièces : parties, victoires, kills,", "&7quêtes du jour et connexion quotidienne."));
        for (int i = 0; i < CATEGORIES.length; ++i) {
            this.navBtn(h, inv, "boutique", CATEGORIES[i], CATEGORY_NAMES[i], lore.get(CATEGORIES[i]), "shopcat:" + CATEGORIES[i]);
        }
        String seasonName = saison.isEmpty() ? null : this.cosmetic(saison.get(0)).getString("season");
        this.navBtn(h, inv, "boutique", "quetes", "&6&l✦ Saison" + (seasonName == null ? "" : " &e" + seasonName),
                saison.isEmpty() ? List.of("&7Aucun cosmétique de saison en vente", "&7pour le moment.")
                        : List.of("&7Les cosmétiques de la saison en cours,", "&7en vente pour un temps limité.", "", "&7À acheter : &f" + saison.size(),
                        "&8Une fois achetés, ils vont dans leur catégorie.", "", "&eClic : voir"),
                saison.isEmpty() ? null : "shopcat:saison");
        this.navBtn(h, inv, "boutique", "retour", "&eMon profil", List.of(), "profile:" + d.uuid);
        this.openLater(p, inv);
    }

    /** 1.9.0 (#102) : description d'une categorie, identique dans les deux styles de menus. */
    private List<String> categoryLore(int i, int owned, int total, ConfigurationSection eq) {
        return List.of(CATEGORY_HELP[i], CATEGORIES[i].equals("title") ? "&8Partout dans le chat, au-dessus de ta tête au hub" : "&8Visible au hub seulement",
                "", "&7Achetés : &f" + owned + "&7/&f" + total, "&7Équipé : " + (eq == null ? "&8aucun" : eq.getString("name", "?")), "", "&eClic : voir");
    }

    /** 1.9.0 (#122) : etat d'un cosmetique pour un joueur, le MEME pour le cadre, la description et l'achat.
     *  EQUIPE / ACHETE / ACHETABLE (pieces >= prix, egalite comprise) / TROP_CHER ; BLOQUE = ni achete ni achetable
     *  autrement (hors vente, casino) : cadre rouge aussi. */
    private enum ShopState { EQUIPE, ACHETE, ACHETABLE, TROP_CHER, BLOQUE, HORS_SAISON }

    private ShopState shopState(PlayerData d, String id, ConfigurationSection s) {
        String cat = s.getString("type", "trail");
        if (d.owned.contains(id)) {
            return id.equals(d.equipped.get(cat)) ? ShopState.EQUIPE : ShopState.ACHETE;
        }
        if (!this.onSale(s) || this.casinoOnly(s) || this.offSeason(s)) {
            return ShopState.BLOQUE;
        }
        return d.coins >= (long)s.getInt("price", 0) ? ShopState.ACHETABLE : ShopState.TROP_CHER;
    }

    /** 1.9.0 (#102) : description d'un cosmetique (sans la ligne d'etat), la meme dans les deux styles. */
    private List<String> cosmeticLore(Player p, ConfigurationSection s, String cat) {
        ArrayList<String> lore = new ArrayList<String>();
        if (cat.equals("title")) {
            lore.add("&7Aperçu : &8[" + s.getString("text", "") + "&8] &f" + p.getName());
        }
        if (cat.equals("join")) {
            lore.add("&7Aperçu : " + s.getString("message", "").replace("%player%", p.getName()));
        }
        for (String line : s.getStringList("description")) {
            lore.add("&7" + line);
        }
        if (this.casinoOnly(s)) {
            lore.add("&6Exclusivité du casino");
        }
        // 1.9.3 : saison du cosmetique (cle « season » du config, ex. « Noël 2026 ») pour qu'on voie qu'il est temporaire
        String season = s.getString("season");
        if (season != null && !season.isBlank()) {
            lore.add(this.onSale(s) ? "&6✦ Édition " + season + " &7(temporaire)" : "&8✦ Édition " + season);
        }
        String until = s.getString("available-until");
        if (until != null) {
            String[] ymd = until.split("-");
            lore.add(this.onSale(s) && ymd.length == 3 ? "&6Saisonnier &7: en vente jusqu'au " + ymd[2] + "/" + ymd[1] : "&8Édition limitée : plus en vente");
        }
        return lore;
    }

    /** 1.9.0 : lignes d'etat (titre + aide) communes aux deux styles. */
    private List<String> stateLore(PlayerData d, ShopState st, int price) {
        return switch (st) {
            case EQUIPE -> List.of("&a&l✔ Équipé", "&7Clic : retirer");
            case ACHETE -> List.of("&b&lAcheté", "&7Clic : équiper");
            case ACHETABLE -> List.of("&6&l" + EemiCore.num(price) + " pièces", "&eClic : acheter");
            case TROP_CHER -> List.of("&c&l" + EemiCore.num(price) + " pièces", "&cIl te manque " + EemiCore.num((long)price - d.coins) + " pièces", "&7Gagnes-en en jouant et avec les quêtes du jour");
            case BLOQUE -> List.of("&c&lPas en vente");
            case HORS_SAISON -> List.of("&8&lHors saison", "&7Tu l'as : il revient avec sa saison");
        };
    }

    private void navShopCategory(Player p, String cat, int page) {
        boolean saisonPage = "saison".equals(cat);
        int ci = Arrays.asList(CATEGORIES).indexOf(cat);
        if (ci < 0 && !saisonPage) {
            this.navShop(p);
            return;
        }
        PlayerData d = this.data(p);
        Menu h = new Menu("shopcat");
        int[] cells = EemiCore.navCells("boutique_cat", "articles");
        ArrayList<String> visible = new ArrayList<String>();
        if (saisonPage) {
            visible.addAll(this.saisonIds(d));
            if (visible.isEmpty()) {
                this.navShop(p);
                return;
            }
            this.saisonView.add(p.getUniqueId());
        } else {
            this.saisonView.remove(p.getUniqueId());
            for (String id : this.cosmeticsOf(cat)) {
                if (!this.shopVisible(d, id) || this.saisonPick(d, id)) continue;
                visible.add(id);
            }
        }
        int pages = EemiCore.pageCount(visible.size(), cells.length);
        page = Math.max(0, Math.min(page, pages - 1));
        ArrayList<String> ov = new ArrayList<String>();
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        ArrayList<String> acts = new ArrayList<String>();
        int i = 0;
        for (int k = page * cells.length; k < visible.size() && i < cells.length; ++k, ++i) {
            String id = visible.get(k);
            ConfigurationSection s = this.cosmetic(id);
            ShopState st = this.shopState(d, id, s);
            ArrayList<String> lore = new ArrayList<String>(this.cosmeticLore(p, s, s.getString("type", cat)));
            lore.add("");
            lore.addAll(this.stateLore(d, st, s.getInt("price", 0)));
            switch (st) {
                case EQUIPE -> ov.add("chef:articles:" + i);
                case ACHETE -> ov.add("ok:articles:" + i);
                case TROP_CHER, BLOQUE -> ov.add("non:articles:" + i);
                default -> { }
            }
            items.add(this.cosmeticItem(s, s.getString("name", id), lore, st == ShopState.EQUIPE));
            acts.add("cos:" + id + ":" + page);
        }
        String seasonName = saisonPage ? this.cosmetic(visible.get(0)).getString("season", "") : "";
        List<NavUi.Ligne> lines = List.of(new NavUi.Ligne("haut", saisonPage ? "✦ " + seasonName.toUpperCase() : EemiCore.strip(CATEGORY_NAMES[ci])),
                new NavUi.Ligne("page", (page + 1) + "/" + pages), new NavUi.Ligne("solde", EemiCore.num(d.coins) + " ⛃", NavUi.colorOf("&6")));
        Inventory inv = this.navInv(h, "boutique_cat", ov, lines);
        this.navHead(h, inv, "boutique_cat", List.of("&7Cadre &6doré &7: équipé", "&7Cadre &avert &7: acheté", "&7Cadre &crouge &7: pas assez de pièces", "&7Sans cadre : à acheter", "",
                saisonPage ? "&6Cosmétiques de saison, en vente pour un temps limité" : CATEGORY_HELP[ci]));
        for (int k = 0; k < items.size(); ++k) {
            inv.setItem(cells[k], items.get(k));
            h.actions.put(cells[k], acts.get(k));
        }
        this.navPager(h, inv, "boutique_cat", "shop", "&eRetour aux catégories", page, pages, "shopcat:" + cat);
        this.openLater(p, inv);
    }

    private void navConfirm(Player p, String id, int page) {
        ConfigurationSection s = this.cosmetic(id);
        if (s == null) {
            return;
        }
        PlayerData d = this.data(p);
        String cat = s.getString("type", "trail");
        int price = s.getInt("price", 0);
        Menu h = new Menu("confirm");
        List<NavUi.Ligne> lines = List.of(new NavUi.Ligne("nom", s.getString("name", id), NavUi.colorOf(s.getString("name", ""))),
                new NavUi.Ligne("prix", EemiCore.num(price) + " ⛃ · IL TE RESTERA " + EemiCore.num(d.coins - (long)price) + " ⛃"));
        Inventory inv = this.navInv(h, "confirmer", List.of(), lines);
        this.navBtn(h, inv, "confirmer", "fermer", "&cFermer", List.of(), "close");
        int[] c = EemiCore.navCells("confirmer", "objet");
        inv.setItem(c[0], this.cosmeticItem(s, s.getString("name", id), this.confirmLore(d, price), false));
        this.navBtn(h, inv, "confirmer", "annuler", "&cAnnuler", List.of("&7Retour à la boutique"), "shopcat:" + this.backCat(p, cat) + ":" + page);
        this.navBtn(h, inv, "confirmer", "acheter", "&a&lOui, acheter", List.of("&7" + EemiCore.strip(s.getString("name", id)) + " pour &6" + EemiCore.num(price) + " pièces"), "buy:" + id + ":" + page);
        this.openLater(p, inv);
    }

    private List<String> confirmLore(PlayerData d, int price) {
        return List.of("&7Prix : &6&l" + EemiCore.num(price) + " pièces", "&7Ton solde : &6" + EemiCore.num(d.coins) + " pièces", "&7Il te restera : &6" + EemiCore.num(d.coins - (long)price) + " pièces");
    }

    private void navQuests(Player p) {
        PlayerData d = this.data(p);
        this.ensureQuests(d);
        Menu h = new Menu("quests");
        LocalDateTime now = LocalDateTime.now();
        long minutes = Duration.between(now, now.toLocalDate().plusDays(1L).atStartOfDay()).toMinutes();
        ArrayList<String> ov = new ArrayList<String>();
        ArrayList<NavUi.Ligne> lines = new ArrayList<NavUi.Ligne>();
        lines.add(new NavUi.Ligne("haut", "NOUVELLES DANS " + minutes / 60L + "H" + String.format("%02d", minutes % 60L)));
        long coins = 0;
        long xp = 0;
        int i = 0;
        for (PlayerData.Quest q : d.quests) {
            if (i >= 3) break;
            if (q.done) {
                ov.add("on:q" + i);
            }
            lines.add(new NavUi.Ligne("q" + i, q.text, NavUi.colorOf(q.done ? "&a" : "&r")));
            lines.add(new NavUi.Ligne("p" + i, Math.min(q.progress, q.target) + "/" + q.target, NavUi.colorOf(q.done ? "&a" : "&7")));
            if (!q.done) {
                coins += q.coins;
                xp += q.xp;
            }
            ++i;
        }
        lines.add(new NavUi.Ligne("bas", coins == 0 ? "TOUT EST FAIT, BRAVO !" : "ENCORE " + coins + " ⛃ ET " + xp + " XP À GAGNER", NavUi.colorOf(coins == 0 ? "&a" : "&6")));
        Inventory inv = this.navInv(h, "quetes", ov, lines);
        this.navHead(h, inv, "quetes", List.of("&73 quêtes tirées chaque jour.", "&7Elles changent à minuit."));
        i = 0;
        for (PlayerData.Quest q : d.quests) {
            if (i >= 3) break;
            List<String> lore = List.of(EemiCore.bar(q.progress, q.target, 20) + " &7" + q.progress + "/" + q.target, "&7Récompense : &6" + q.coins + " pièces &7et &b" + q.xp + " xp", q.done ? "&aTerminée !" : "&7En cours");
            this.navBtn(h, inv, "quetes", "q" + i, (q.done ? "&a" : "&e") + q.text, lore, null);
            int[] c = EemiCore.navCells("quetes", "ic" + i);
            if (c.length > 0) {
                inv.setItem(c[0], EemiCore.ic(EemiCore.item(q.done ? Material.LIME_DYE : Material.PAPER, (q.done ? "&a" : "&e") + q.text, lore, q.done), q.done ? "quete_ok" : "quete"));
            }
            ++i;
        }
        this.navBtn(h, inv, "quetes", "retour", "&eRetour au profil", List.of(), "profile:" + p.getUniqueId());
        this.openLater(p, inv);
    }

    // ---------------------------------------------------------------- groupe

    private List<GameInfo> watchableGames() {
        ArrayList<GameInfo> out = new ArrayList<GameInfo>();
        for (GameInfo g : this.games.values()) {
            if (g.spectate == null || g.spectate.isBlank() || !g.fresh(this.expireMs)) continue;
            if (!"playing".equals(g.state) && !"starting".equals(g.state) && !"ending".equals(g.state) && g.playing <= 0) continue;
            out.add(g);
        }
        return out;
    }

    private void navParty(Player p) {
        Menu h = new Menu("party");
        Party party = this.partyOf.get(p.getUniqueId());
        boolean leader = party == null || party.leader.equals(p.getUniqueId());
        long now = System.currentTimeMillis();
        Invite received = this.invites.get(p.getUniqueId());
        if (received != null && now - received.time > 60000L) {
            received = null;
        }
        ArrayList<String> ov = new ArrayList<String>();
        List<UUID> members = party == null ? List.of(p.getUniqueId()) : party.members;
        if (party != null) {
            int li = party.members.indexOf(party.leader);
            if (li >= 0 && li < 8) ov.add("chef:membres:" + li);
        }
        if (!leader) ov.add("off:inviter");
        if (this.watchableGames().isEmpty()) ov.add("off:regarder");
        if (received != null) ov.add("on:accepter");
        else ov.add("off:accepter");
        if (party == null) ov.add("off:quitter");
        List<NavUi.Ligne> lines = List.of(
                new NavUi.Ligne("haut", party == null ? "AUCUN GROUPE" : "GROUPE DE " + this.nameOf(party.leader) + " · " + party.members.size() + "/8"),
                new NavUi.Ligne("info", party == null ? "INVITE TES AMIS POUR JOUER ENSEMBLE" : "CLIQUE UN MEMBRE EN JEU POUR LE REGARDER"));
        Inventory inv = this.navInv(h, "groupe", ov, lines);
        this.navHead(h, inv, "groupe", List.of("&7Le chef lance les parties pour tout", "&7le groupe (navigateur, PNJ, /jouer).", "&f/pc <message> &7: parler au groupe"));
        int[] cells = EemiCore.navCells("groupe", "membres");
        int k = 0;
        for (UUID id : members) {
            if (k >= cells.length) break;
            ArrayList<String> lore = new ArrayList<String>();
            if (party != null && id.equals(party.leader)) lore.add("&6Chef du groupe");
            lore.add(this.whereText(id));
            PlayerData md = this.players.get(id);
            if (md != null) lore.add("&7Niveau " + this.levelColor(md.level) + md.level);
            if (party == null) {
                lore.add("");
                lore.add("&7Seul pour l'instant.");
            }
            if (party != null && this.spectatableGameOf(id) != null && !id.equals(p.getUniqueId())) {
                lore.add("");
                lore.add("&eClic : regarder sa partie");
            }
            if (party != null && party.leader.equals(p.getUniqueId()) && !id.equals(p.getUniqueId())) {
                lore.add("&cShift-clic : exclure");
            }
            inv.setItem(cells[k], EemiCore.head(id, (Bukkit.getPlayer(id) != null ? "&f" : "&8") + this.nameOf(id), lore));
            if (party != null) h.actions.put(cells[k], "pmember:" + id);
            ++k;
        }
        int[] sent = EemiCore.navCells("groupe", "envoyees");
        k = 0;
        for (Map.Entry<UUID, Invite> en : this.invites.entrySet()) {
            if (!en.getValue().from.equals(p.getUniqueId()) || k >= sent.length) continue;
            long left = Math.max(0L, (60000L - (now - en.getValue().time)) / 1000L);
            if (left == 0L) continue;
            inv.setItem(sent[k], EemiCore.head(en.getKey(), "&f" + this.nameOf(en.getKey()), List.of("&7Invitation en attente", "&7Expire dans &e" + left + " s", "", "&cClic : annuler")));
            h.actions.put(sent[k], "pcancel:" + en.getKey());
            ++k;
        }
        this.navBtn(h, inv, "groupe", "inviter", leader ? "&aInviter un joueur" : "&8Inviter un joueur", leader ? List.of("&7Choisis parmi les joueurs connectés", "", "&eClic : choisir") : List.of("&7Seul le chef invite"), leader ? "pinvite" : null);
        this.navBtn(h, inv, "groupe", "regarder", "&6Regarder une partie", List.of("&7Parties en cours avec mode spectateur", "", "&eClic : voir la liste"), "pwatch:0");
        if (received != null) {
            this.navBtn(h, inv, "groupe", "accepter", "&eInvitation de " + this.nameOf(received.from), List.of("&7Expire dans &e" + (60000L - (now - received.time)) / 1000L + " s", "", "&aClic gauche : accepter", "&cClic droit : refuser"), "paccept");
        } else {
            this.navBtn(h, inv, "groupe", "accepter", "&8Aucune invitation reçue", List.of(), null);
        }
        if (party != null) {
            this.navBtn(h, inv, "groupe", "quitter", "&cQuitter le groupe", party.leader.equals(p.getUniqueId()) ? List.of("&7Clic : quitter (un autre devient chef)", "&4Shift-clic : dissoudre le groupe") : List.of("&7Clic : quitter"), "pleave");
        } else {
            this.navBtn(h, inv, "groupe", "quitter", "&8Pas de groupe à quitter", List.of(), null);
        }
        this.navBtn(h, inv, "groupe", "retour", "&eRetour aux mini-jeux", List.of(), "nav");
        this.openLater(p, inv);
    }

    private void navInvite(Player p, int page) {
        Menu h = new Menu("pinvite");
        ArrayList<Player> list = new ArrayList<Player>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || o.hasMetadata("vanished") || this.partyOf.containsKey(o.getUniqueId())) continue;
            list.add(o);
        }
        int[] cells = EemiCore.navCells("liste_inviter", "liste");
        int pages = EemiCore.pageCount(list.size(), cells.length);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = this.navInv(h, "liste_inviter", List.of(), List.of(
                new NavUi.Ligne("haut", list.isEmpty() ? "PERSONNE À INVITER" : list.size() + " JOUEUR" + (list.size() > 1 ? "S" : "") + " À INVITER"),
                new NavUi.Ligne("page", (page + 1) + "/" + pages)));
        this.navHead(h, inv, "liste_inviter", List.of("&7Les joueurs déjà dans un groupe", "&7n'apparaissent pas ici."));
        int k = 0;
        for (int i = page * cells.length; i < list.size() && k < cells.length; ++i, ++k) {
            Player o = list.get(i);
            Invite pending = this.invites.get(o.getUniqueId());
            boolean mine = pending != null && pending.from.equals(p.getUniqueId());
            PlayerData od = this.data(o);
            inv.setItem(cells[k], EemiCore.head(o.getUniqueId(), "&f" + o.getName(), List.of("&7Niveau " + this.levelColor(od.level) + od.level, this.whereText(o.getUniqueId()), "", mine ? "&7Déjà invité (clic : renvoyer)" : "&aClic : inviter")));
            h.actions.put(cells[k], "pinv:" + o.getUniqueId());
        }
        this.navPager(h, inv, "liste_inviter", "party", "&eRetour au groupe", page, pages, "pinvite");
        this.openLater(p, inv);
    }

    private void navWatch(Player p, int page) {
        Menu h = new Menu("pwatch");
        List<GameInfo> list = this.watchableGames();
        int[] cells = EemiCore.navCells("liste_regarder", "liste");
        int pages = EemiCore.pageCount(list.size(), cells.length);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = this.navInv(h, "liste_regarder", List.of(), List.of(
                new NavUi.Ligne("haut", list.isEmpty() ? "AUCUNE PARTIE EN COURS" : list.size() + " PARTIE" + (list.size() > 1 ? "S" : "") + " EN COURS"),
                new NavUi.Ligne("page", (page + 1) + "/" + pages)));
        this.navHead(h, inv, "liste_regarder", List.of("&7Clic sur un jeu : regarder", "&7sa partie en spectateur."));
        int k = 0;
        for (int i = page * cells.length; i < list.size() && k < cells.length; ++i, ++k) {
            GameInfo g = list.get(i);
            inv.setItem(cells[k], EemiCore.ic(EemiCore.item(g.icon, g.name, List.of(this.statusText(g), "", "&eClic : regarder"), false), EemiCore.gameIcon(g.id)));
            h.actions.put(cells[k], "pspec:" + g.id);
        }
        this.navPager(h, inv, "liste_regarder", "party", "&eRetour au groupe", page, pages, "pwatch");
        this.openLater(p, inv);
    }

    private void navPlayers(Player viewer, int page) {
        Menu h = new Menu("players");
        ArrayList<Player> online = new ArrayList<Player>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.hasMetadata("vanished") && !o.equals(viewer)) continue;
            online.add(o);
        }
        online.sort(Comparator.comparingInt((Player o) -> this.data(o).level).thenComparingLong(o -> this.data(o).xp).reversed());
        int[] cells = EemiCore.navCells("liste_joueurs", "liste");
        int pages = EemiCore.pageCount(online.size(), cells.length);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = this.navInv(h, "liste_joueurs", List.of(), List.of(
                new NavUi.Ligne("haut", online.size() + " JOUEUR" + (online.size() > 1 ? "S" : "") + " EN LIGNE"),
                new NavUi.Ligne("page", (page + 1) + "/" + pages)));
        this.navHead(h, inv, "liste_joueurs", List.of("&7Clic : voir son profil", "&7Shift-clic : regarder sa partie"));
        int k = 0;
        for (int i = page * cells.length; i < online.size() && k < cells.length; ++i, ++k) {
            Player o = online.get(i);
            PlayerData d = this.data(o);
            List<String> lore = this.levelLore(d);
            GameInfo g = this.gameOfPlayer(o);
            lore.add(this.inLobby(o) ? "&7Au hub" : (g != null ? "&7En jeu : " + g.name : "&7Monde : " + o.getWorld().getName()));
            lore.add("");
            lore.add("&eClic : voir son profil");
            if (!o.equals(viewer) && g != null && g.spectate != null && !g.spectate.isBlank()) {
                lore.add("&bShift-clic : regarder sa partie");
            }
            inv.setItem(cells[k], EemiCore.head(o.getUniqueId(), this.levelColor(d.level) + d.name, lore));
            h.actions.put(cells[k], "profile:" + d.uuid);
        }
        this.navPager(h, inv, "liste_joueurs", "profile:" + viewer.getUniqueId(), "&eRetour au profil", page, pages, "players");
        this.openLater(viewer, inv);
    }

    private void navTopLevel(Player viewer) {
        Menu h = new Menu("toplevel");
        PlayerData own = this.data(viewer);
        List<TopEntry> full = this.levelTop();
        int rank = this.levelRank(full, own);
        Inventory inv = this.navInv(h, "classement", List.of(), List.of(new NavUi.Ligne("haut", "TA PLACE : #" + rank + " · NIV " + own.level), new NavUi.Ligne("page", "1/1")));
        this.navHead(h, inv, "classement", List.of("&7Les 10 joueurs de plus haut niveau.", "&7Clic : voir son profil"));
        int[] cells = EemiCore.navCells("classement", "liste");
        String[] medals = {"&6&l", "&f&l", "&c&l"};
        List<TopEntry> list = full;
        // 1.9.1 (#114) : pyramide, lue de haut en bas et de gauche a droite.
        // Grille 4 x 7 : #1 au centre de la 1re ligne, #2-#3 sur la 2e, #4-#6 sur la 3e, #7-#10 sur la 4e.
        int[] order = {3, 9, 11, 15, 17, 19, 21, 23, 25, 27};
        for (int i = 0; i < list.size() && i < 10; ++i) {
            PlayerData d = this.players.get(list.get(i).uuid());
            if (d == null || order[i] >= cells.length) continue;
            int slot = cells[order[i]];
            List<String> lore = this.levelLore(d);
            lore.add("");
            lore.add("&eClic : voir son profil");
            inv.setItem(slot, EemiCore.head(d.uuid, (i < 3 ? medals[i] : "&7") + "#" + (i + 1) + " " + this.levelColor(d.level) + d.name + " &8· " + this.levelColor(d.level) + "Niv " + d.level + " &8· &b" + EemiCore.num(d.xp) + " xp", lore));
            h.actions.put(slot, "profile:" + d.uuid);
        }
        this.navPager(h, inv, "classement", "profile:" + viewer.getUniqueId(), "&eRetour au profil", 0, 1, "toplevel");
        this.openLater(viewer, inv);
    }

    // ================================================================ 1.6.0 : boutons de bas de menu (convention unique)

    /** Derniere ligne : Retour juste a gauche du centre (si backAction), Fermer au centre. */
    private void bottomButtons(Menu holder, Inventory inv, String backAction, String backName, List<String> backLore) {
        int center = inv.getSize() - 5;
        if (backAction != null) {
            inv.setItem(center - 1, EemiCore.ic(EemiCore.item(Material.ARROW, backName == null ? "&eRetour" : backName, backLore == null ? List.of() : backLore, false), "retour"));
            holder.actions.put(center - 1, backAction);
        }
        inv.setItem(center, EemiCore.ic(EemiCore.item(Material.BARRIER, "&cFermer", List.of(), false), "fermer"));
        holder.actions.put(center, "close");
    }

    /** Pages : Precedent en bas a gauche, Suivant en bas a droite. action recoit ":<page>". */
    private void pageButtons(Menu holder, Inventory inv, int page, int pages, String action) {
        int first = inv.getSize() - 9;
        int last = inv.getSize() - 1;
        if (page > 0) {
            inv.setItem(first, EemiCore.ic(EemiCore.item(Material.SPECTRAL_ARROW, "&ePage précédente", List.of("&7Page " + page + "/" + pages), false), "precedent"));
            holder.actions.put(first, action + ":" + (page - 1));
        }
        if (page < pages - 1) {
            inv.setItem(last, EemiCore.ic(EemiCore.item(Material.SPECTRAL_ARROW, "&ePage suivante", List.of("&7Page " + (page + 2) + "/" + pages), false), "suivant"));
            holder.actions.put(last, action + ":" + (page + 1));
        }
    }

    private static int pageCount(int items, int perPage) {
        return Math.max(1, (items + perPage - 1) / perPage);
    }

    private void openProfile(Player viewer, PlayerData d) {
        if (NAV) {
            this.navProfile(viewer, d);
            return;
        }
        Inventory inv;
        Menu holder = new Menu("profile");
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)54, EemiCore.uiTitle("profile", "Profil de " + d.name));
        long need = this.xpNeeded(d.level);
        ItemStack head = EemiCore.item(Material.PLAYER_HEAD, this.levelColor(d.level) + d.name, List.of("&7Niveau : " + this.levelColor(d.level) + d.level, "&7XP : &b" + d.xp + "&7/&b" + need, EemiCore.bar(d.xp, need, 30), "&7Pièces : &6" + d.coins, "&7Série de connexion : &e" + d.streak + " jour" + (d.streak > 1 ? "s" : ""), "", "&7Parties : &f" + d.total("plays"), "&7Victoires : &a" + d.total("wins"), "&7Kills : &c" + d.total("kills"), this.equippedTitle(d).isEmpty() ? "&7Titre : &8aucun" : "&7Titre : " + this.equippedTitle(d)), false);
        SkullMeta sm = (SkullMeta)head.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer((UUID)d.uuid));
        head.setItemMeta((ItemMeta)sm);
        inv.setItem(4, head);
        int[] slots = new int[]{19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int i = 0;
        for (GameInfo g : this.games.values()) {
            if (i >= slots.length) break;
            inv.setItem(slots[i++], EemiCore.ic(EemiCore.item(g.icon, g.name, List.of("&7Parties : &f" + d.stat(g.id, "plays"), "&7Victoires : &a" + d.stat(g.id, "wins"), "&7Kills : &c" + d.stat(g.id, "kills")), false), EemiCore.gameIcon(g.id)));
        }
        PlayerData own = this.data(viewer);
        inv.setItem(45, EemiCore.ic(EemiCore.item(Material.EMERALD, "&aBoutique", List.of("&7Tes pièces : &6" + EemiCore.num(own.coins), "&7Traînées, chapeaux, titres...", "", "&eClic : ouvrir"), true), "boutique"));
        holder.actions.put(45, "shop");
        inv.setItem(46, EemiCore.ic(EemiCore.item(Material.WRITABLE_BOOK, "&eQuêtes du jour", List.of("&7Gagne des pièces et de l'xp", "", "&eClic pour voir"), false), "quetes"));
        holder.actions.put(46, "quests");
        inv.setItem(47, EemiCore.ic(EemiCore.item(Material.LEAD, "&9Groupe", List.of("&7Joue avec tes amis", "", "&eClic pour ouvrir"), false), "groupe"));
        holder.actions.put(47, "party");
        if (!own.uuid.equals(d.uuid)) {
            this.bottomButtons(holder, inv, "profile:" + String.valueOf(own.uuid), "&eRetour à mon profil", List.of("&7Niveau " + this.levelColor(own.level) + own.level));
        } else {
            this.bottomButtons(holder, inv, null, null, null);
        }
        inv.setItem(52, EemiCore.ic(EemiCore.item(Material.PLAYER_HEAD, "&bJoueurs en ligne", List.of("&7Niveau et xp des joueurs connectés", "", "&eClic pour voir"), false), "joueurs"));
        holder.actions.put(52, "players");
        inv.setItem(53, EemiCore.ic(EemiCore.item(Material.GOLD_BLOCK, "&6Classement des niveaux", List.of("&7Les 10 meilleurs joueurs", "", "&eClic pour voir"), false), "classement"));
        holder.actions.put(53, "toplevel");
        this.openLater(viewer, inv);
    }

    private static ItemStack head(UUID owner, String name, List<String> lore) {
        ItemStack it = EemiCore.item(Material.PLAYER_HEAD, name, lore, false);
        SkullMeta sm = (SkullMeta)it.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer((UUID)owner));
        it.setItemMeta((ItemMeta)sm);
        return it;
    }

    private List<String> levelLore(PlayerData d) {
        long need = this.xpNeeded(d.level);
        return new ArrayList<String>(List.of("&7Niveau : " + this.levelColor(d.level) + d.level, "&7XP : &b" + d.xp + "&7/&b" + need, EemiCore.bar(d.xp, need, 20), "&7Victoires : &a" + d.total("wins") + " &7- Kills : &c" + d.total("kills")));
    }

    private void openPlayers(Player viewer, int page) {
        if (NAV) {
            this.navPlayers(viewer, page);
            return;
        }
        Inventory inv;
        Menu holder = new Menu("players");
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)54, EemiCore.uiTitle("players", "Joueurs en ligne"));
        ArrayList<Player> online = new ArrayList<Player>();
        for (Player o2 : Bukkit.getOnlinePlayers()) {
            if (o2.hasMetadata("vanished") && !o2.equals(viewer)) continue;
            online.add(o2);
        }
        online.sort(Comparator.comparingInt(o -> this.data((Player)o).level).thenComparingLong(o -> this.data((Player)o).xp).reversed());
        int pages = EemiCore.pageCount(online.size(), 45);
        page = Math.max(0, Math.min(page, pages - 1));
        int slot = 0;
        for (int k = page * 45; k < online.size() && slot < 45; ++k) {
            Player o3 = online.get(k);
            PlayerData d = this.data(o3);
            List<String> lore = this.levelLore(d);
            GameInfo g = this.gameOfPlayer(o3);
            lore.add((String)(this.inLobby(o3) ? "&7Au hub" : (g != null ? "&7En jeu : " + g.name : "&7Monde : " + o3.getWorld().getName())));
            lore.add("");
            lore.add("&eClic pour voir son profil");
            if (!o3.equals(viewer) && g != null && g.spectate != null && !g.spectate.isBlank()) {
                lore.add("&bShift-clic pour regarder sa partie");
            }
            inv.setItem(slot, EemiCore.head(o3.getUniqueId(), this.levelColor(d.level) + d.name, lore));
            holder.actions.put(slot++, "profile:" + String.valueOf(d.uuid));
        }
        this.pageButtons(holder, inv, page, pages, "players");
        this.bottomButtons(holder, inv, "profile:" + String.valueOf(viewer.getUniqueId()), "&eRetour au profil", null);
        this.openLater(viewer, inv);
    }

    private void openTopLevel(Player viewer) {
        if (NAV) {
            this.navTopLevel(viewer);
            return;
        }
        Inventory inv;
        Menu holder = new Menu("toplevel");
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)36, EemiCore.uiTitle("toplevel", "Classement des niveaux"));
        int[] slots = new int[]{4, 12, 14, 19, 20, 21, 22, 23, 24, 25};
        String[] medals = new String[]{"&6&l", "&f&l", "&c&l"};
        List<TopEntry> list = this.levelTop();
        for (int i = 0; i < list.size() && i < slots.length; ++i) {
            PlayerData d = this.players.get(list.get(i).uuid());
            if (d == null) continue;
            List<String> lore = this.levelLore(d);
            lore.add("");
            lore.add("&eClic : voir son profil");
            inv.setItem(slots[i], EemiCore.head(d.uuid, (i < 3 ? medals[i] : "&7") + "#" + (i + 1) + " " + this.levelColor(d.level) + d.name + " &8· " + this.levelColor(d.level) + "Niv " + d.level + " &8· &b" + EemiCore.num(d.xp) + " xp", lore));
            holder.actions.put(slots[i], "profile:" + String.valueOf(d.uuid));
        }
        PlayerData own = this.data(viewer);
        int rank = this.levelRank(list, own);
        this.bottomButtons(holder, inv, "profile:" + String.valueOf(viewer.getUniqueId()), "&eRetour au profil", List.of("&7Ta place : &f#" + rank));
        this.openLater(viewer, inv);
    }

    private void profileText(CommandSender to, PlayerData d) {
        long need = this.xpNeeded(d.level);
        EemiCore.msg(to, "&6[EEMICRAFT] &r&eProfil de " + d.name);
        EemiCore.msg(to, "&7 Niveau " + this.levelColor(d.level) + d.level + " &7- XP &b" + d.xp + "&7/&b" + need + " " + EemiCore.bar(d.xp, need, 20));
        EemiCore.msg(to, "&7 Pièces : &6" + d.coins + " &7- Parties &f" + d.total("plays") + " &7- Victoires &a" + d.total("wins") + " &7- Kills &c" + d.total("kills") + " &7- Série &e" + d.streak);
        for (GameInfo g : this.games.values()) {
            int pl = d.stat(g.id, "plays");
            if (pl == 0 && d.stat(g.id, "wins") == 0 && d.stat(g.id, "kills") == 0) continue;
            EemiCore.msg(to, "&7  " + g.name + "&7 : " + pl + " parties, &a" + d.stat(g.id, "wins") + " V&7, &c" + d.stat(g.id, "kills") + " kills");
        }
    }

    private boolean onSale(ConfigurationSection s) {
        LocalDate today = LocalDate.now();
        try {
            String from = s.getString("available-from");
            String until = s.getString("available-until");
            if (from != null && today.isBefore(LocalDate.parse(from))) {
                return false;
            }
            if (until != null && today.isAfter(LocalDate.parse(until))) {
                return false;
            }
        }
        catch (DateTimeParseException ex) {
            return true;
        }
        return true;
    }

    /** Cosmetique reserve au casino : jamais vendu en pieces, donne par /eemi cosmetic. */
    private boolean casinoOnly(ConfigurationSection s) {
        return s != null && s.getBoolean("casino", false);
    }

    private List<String> cosmeticsOf(String cat) {
        ArrayList<String> ids = new ArrayList<String>();
        ConfigurationSection all = this.getConfig().getConfigurationSection("cosmetics");
        if (all != null) {
            for (String id : all.getKeys(false)) {
                ConfigurationSection s = all.getConfigurationSection(id);
                if (s == null || !cat.equals(s.getString("type"))) continue;
                ids.add(id);
            }
        }
        return ids;
    }

    private ItemStack coinsItem(PlayerData d) {
        return EemiCore.ic(EemiCore.item(Material.GOLD_INGOT, "&6" + EemiCore.num(d.coins) + " pièces", List.of("&7Gagne des pièces en jouant, avec les", "&7quêtes du jour (/quetes) et la", "&7récompense de connexion quotidienne."), false), "pieces");
    }

    private void openShop(Player p) {
        if (NAV) {
            this.navShop(p);
            return;
        }
        Inventory inv;
        PlayerData d = this.data(p);
        Menu holder = new Menu("shop");
        // 1.6.3 : 4 lignes (fond bg_shop) : categories des la 1re ligne sous le titre, en quinconce (2, 4, 6 puis 12, 14),
        // pieces en haut a droite (8), une ligne vide, Retour 30 / Fermer 31
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)36, EemiCore.uiTitle("shop", "Boutique · " + EemiCore.num(d.coins) + " pièces"));
        int[] slots = new int[]{2, 4, 6, 12, 14};
        for (int i = 0; i < CATEGORIES.length; ++i) {
            String cat = CATEGORIES[i];
            List<String> ids = this.cosmeticsOf(cat);
            ids.removeIf(id -> !this.shopVisible(d, (String)id));
            int owned = 0;
            for (String id2 : ids) {
                if (!d.owned.contains(id2)) continue;
                ++owned;
            }
            ConfigurationSection eq = this.worn(d.equipped.get(cat));
            inv.setItem(slots[i], EemiCore.ic(EemiCore.item(CATEGORY_ICONS[i], CATEGORY_NAMES[i], this.categoryLore(i, owned, ids.size(), eq), eq != null), UI_CATEGORY_ICONS[i]));
            holder.actions.put(slots[i], "shopcat:" + cat);
        }
        inv.setItem(8, this.coinsItem(d));
        this.bottomButtons(holder, inv, "profile:" + String.valueOf(d.uuid), "&eMon profil", null);
        this.openLater(p, inv);
    }

    private void openShopCategory(Player p, String cat, int page) {
        if (NAV) {
            this.navShopCategory(p, cat, page);
            return;
        }
        Inventory inv;
        int ci = Arrays.asList(CATEGORIES).indexOf(cat);
        if (ci < 0) {
            this.openShop(p);
            return;
        }
        PlayerData d = this.data(p);
        Menu holder = new Menu("shopcat");
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)54, EemiCore.uiTitle("shopcat", CATEGORY_NAMES[ci].replaceAll("&.", "") + " · " + EemiCore.num(d.coins) + " pièces"));
        inv.setItem(4, EemiCore.ic(EemiCore.item(CATEGORY_ICONS[ci], CATEGORY_NAMES[ci], List.of(CATEGORY_HELP[ci]), false), UI_CATEGORY_ICONS[ci]));
        // 1.6.1 : cartes en quinconce (fond bg_shopcat) : le cosmetique, et juste en dessous (+9) sa case d'etat
        // (equipe / achete / prix / trop cher), une case vide entre chaque carte. 9 par page.
        int[] slots = SHOP_CARD_SLOTS;
        ArrayList<String> visible = new ArrayList<String>();
        for (String id : this.cosmeticsOf(cat)) {
            if (!this.shopVisible(d, id)) continue;
            visible.add(id);
        }
        int pages = EemiCore.pageCount(visible.size(), slots.length);
        page = Math.max(0, Math.min(page, pages - 1));
        int i = 0;
        for (int k = page * slots.length; k < visible.size() && i < slots.length; ++k) {
            String id = visible.get(k);
            ConfigurationSection s = this.cosmetic(id);
            ShopState st = this.shopState(d, id, s);
            int price = s.getInt("price", 0);
            ArrayList<String> lore = new ArrayList<String>(this.cosmeticLore(p, s, cat));
            lore.add("");
            List<String> state = this.stateLore(d, st, price);
            // etat lisible aussi dans la description du cosmetique
            lore.addAll(state);
            String stateName = state.get(0);
            List<String> stateHelp = state.subList(1, state.size());
            ItemStack stateItem = switch (st) {
                case EQUIPE -> EemiCore.ic(EemiCore.item(Material.LIME_DYE, stateName, stateHelp, false), "etat_equipe");
                case ACHETE -> EemiCore.ic(EemiCore.item(Material.LIGHT_BLUE_DYE, stateName, stateHelp, false), "etat_achete");
                case ACHETABLE -> EemiCore.ic(EemiCore.item(Material.GOLD_NUGGET, stateName, List.of("&7Ton solde : &6" + EemiCore.num(d.coins) + " pièces", "&eClic : acheter"), false), "etat_prix");
                // 1.9.0 (#122) : ce style n'a pas de cadre dessine ; la case sous la carte devient une vitre ROUGE
                // (sans icone du pack, qui etait un cadenas dore) pour marquer ce qu'on ne peut pas acheter.
                case TROP_CHER, BLOQUE -> EemiCore.item(Material.RED_STAINED_GLASS_PANE, stateName, stateHelp, false);
                case HORS_SAISON -> EemiCore.item(Material.GRAY_STAINED_GLASS_PANE, stateName, stateHelp, false);
            };
            // 1.6.2 : le cosmetique garde son VRAI objet (tete de creeper, bloc de diamant...), jamais l'icone de sa categorie
            inv.setItem(slots[i], this.cosmeticItem(s, s.getString("name", id), lore, st == ShopState.EQUIPE));
            holder.actions.put(slots[i], "cos:" + id + ":" + page);
            inv.setItem(slots[i] + 9, stateItem);
            holder.actions.put(slots[i] + 9, "cos:" + id + ":" + page);
            ++i;
        }
        inv.setItem(8, this.coinsItem(d));
        this.pageButtons(holder, inv, page, pages, "shopcat:" + cat);
        this.bottomButtons(holder, inv, "shop", "&eRetour aux catégories", null);
        this.openLater(p, inv);
    }

    /** Ecran de confirmation d'un achat (au-dessus de shop.confirm-above pieces). */
    private void openConfirm(Player p, String id, int page) {
        if (NAV) {
            this.navConfirm(p, id, page);
            return;
        }
        ConfigurationSection s = this.cosmetic(id);
        if (s == null) {
            return;
        }
        PlayerData d = this.data(p);
        String cat = s.getString("type", "trail");
        int price = s.getInt("price", 0);
        Inventory inv;
        Menu holder = new Menu("confirm");
        // 1.6.1 : fond dessine bg_confirm (4 lignes : Oui / objet / Annuler, ligne vide, boutons)
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)36, EemiCore.uiTitle("confirm", "Confirmer l'achat"));
        inv.setItem(13, this.cosmeticItem(s, s.getString("name", id), this.confirmLore(d, price), false));
        inv.setItem(11, EemiCore.ic(EemiCore.item(Material.LIME_CONCRETE, "&a&lOui, acheter", List.of("&7" + s.getString("name", id).replaceAll("&.", "") + " pour &6" + EemiCore.num(price) + " pièces"), false), "confirmer"));
        holder.actions.put(11, "buy:" + id + ":" + page);
        inv.setItem(15, EemiCore.ic(EemiCore.item(Material.RED_CONCRETE, "&cAnnuler", List.of("&7Retour à la boutique"), false), "annuler"));
        holder.actions.put(15, "shopcat:" + this.backCat(p, cat) + ":" + page);
        this.bottomButtons(holder, inv, "shopcat:" + this.backCat(p, cat) + ":" + page, "&eRetour", null);
        this.openLater(p, inv);
    }

    private void shopClick(Player p, String id, int page, boolean confirmed) {
        ConfigurationSection s = this.cosmetic(id);
        if (s == null) {
            return;
        }
        PlayerData d = this.data(p);
        String cat = s.getString("type", "trail");
        String name = s.getString("name", id);
        if (d.owned.contains(id)) {
            if (confirmed) {
                // deja achete (double clic sur Oui) : on revient a la categorie
                this.openShopCategory(p, this.backCat(p, cat), page);
                return;
            }
            if (id.equals(d.equipped.get(cat))) {
                d.equipped.remove(cat);
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&7Retiré : " + name);
            } else {
                d.equipped.put(cat, id);
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&aÉquipé : " + name);
            }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        } else {
            int price = s.getInt("price", 0);
            if (this.casinoOnly(s)) {
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cCe cosmétique s'achète en jetons, à la boutique du casino.");
                this.uiSound(p, "erreur");
                return;
            }
            if (!this.onSale(s) || this.offSeason(s)) {
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cCe cosmétique n'est plus en vente.");
                this.uiSound(p, "erreur");
                return;
            }
            if (d.coins < (long)price) {
                EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cIl te manque " + EemiCore.num((long)price - d.coins) + " pièces pour " + name + "&c.");
                this.uiSound(p, "erreur");
                if (confirmed) {
                    this.openShopCategory(p, this.backCat(p, cat), page);
                }
                return;
            }
            if (!confirmed && price > this.getConfig().getInt("shop.confirm-above", 500)) {
                this.openConfirm(p, id, page);
                return;
            }
            d.coins -= (long)price;
            d.owned.add(id);
            d.equipped.put(cat, id);
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&aAcheté et équipé : " + name + " &7(-" + EemiCore.num(price) + " pièces)");
            this.uiSound(p, "achat");
        }
        this.markDirty(d);
        if (cat.equals("hat")) {
            this.removeHat(p);
            this.applyHat(p);
        }
        if (cat.equals("gadget")) {
            this.removeGadget(p);
            this.applyGadget(p);
        }
        if (cat.equals("title")) {
            this.updateTitleTag(p);
        }
        this.openShopCategory(p, this.backCat(p, cat), page);
    }

    private void openQuests(Player p) {
        if (NAV) {
            this.navQuests(p);
            return;
        }
        Inventory inv;
        PlayerData d = this.data(p);
        this.ensureQuests(d);
        Menu holder = new Menu("quests");
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)27, EemiCore.uiTitle("quests", "Quêtes du jour"));
        int[] slots = new int[]{11, 13, 15, 10, 16, 12, 14};
        int i = 0;
        for (PlayerData.Quest q : d.quests) {
            if (i >= slots.length) break;
            inv.setItem(slots[i++], EemiCore.ic(EemiCore.item(q.done ? Material.LIME_DYE : Material.PAPER, (q.done ? "&a" : "&e") + q.text, List.of(EemiCore.bar(q.progress, q.target, 20) + " &7" + q.progress + "/" + q.target, "&7Récompense : &6" + q.coins + " pièces &7et &b" + q.xp + " xp", q.done ? "&aTerminée !" : "&7En cours"), q.done), q.done ? "quete_ok" : "quete"));
        }
        LocalDateTime now = LocalDateTime.now();
        long minutes = Duration.between(now, now.toLocalDate().plusDays(1L).atStartOfDay()).toMinutes();
        inv.setItem(4, EemiCore.ic(EemiCore.item(Material.CLOCK, "&bNouvelles quêtes dans " + minutes / 60L + "h" + String.format("%02d", minutes % 60L), List.of("&7Les quêtes changent à minuit."), false), "horloge"));
        this.bottomButtons(holder, inv, "profile:" + String.valueOf(p.getUniqueId()), "&eRetour au profil", null);
        this.openLater(p, inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        Player p;
        Menu menu;
        block49: {
            block48: {
                InventoryHolder inventoryHolder = e.getInventory().getHolder();
                if (!(inventoryHolder instanceof Menu)) {
                    return;
                }
                menu = (Menu)inventoryHolder;
                e.setCancelled(true);
                HumanEntity humanEntity = e.getWhoClicked();
                if (!(humanEntity instanceof Player)) break block48;
                p = (Player)humanEntity;
                if (e.getClickedInventory() == e.getInventory()) break block49;
            }
            return;
        }
        String action = menu.actions.get(e.getRawSlot());
        if (action == null) {
            return;
        }
        int colon = action.indexOf(58);
        String key = colon < 0 ? action : action.substring(0, colon);
        String arg = colon < 0 ? "" : action.substring(colon + 1);
        switch (key) {
            case "close": {
                p.closeInventory();
                break;
            }
            case "cos":
            case "buy": {
                String[] cp = arg.split(":", 2);
                this.shopClick(p, cp[0], cp.length > 1 ? EemiCore.parseInt(cp[1], 0) : 0, key.equals("buy"));
                break;
            }
            case "shop": {
                this.openShop(p);
                break;
            }
            case "shopcat": {
                String[] sp = arg.split(":", 2);
                this.openShopCategory(p, sp[0], sp.length > 1 ? EemiCore.parseInt(sp[1], 0) : 0);
                break;
            }
            case "quests": {
                this.openQuests(p);
                break;
            }
            case "players": {
                this.openPlayers(p, EemiCore.parseInt(arg, 0));
                break;
            }
            case "toplevel": {
                this.openTopLevel(p);
                break;
            }
            case "profile": {
                UUID id = UUID.fromString(arg);
                Player t = Bukkit.getPlayer((UUID)id);
                if (menu.kind.equals("players") && e.isShiftClick() && t != null && !t.equals(p) && this.gameOfPlayer(t) != null) {
                    this.spectatePlayer(p, t);
                    return;
                }
                PlayerData d = this.players.get(id);
                if (d == null) break;
                this.openProfile(p, d);
                break;
            }
            case "party": {
                this.openPartyMenu(p);
                break;
            }
            case "pinvite": {
                this.openInviteMenu(p, EemiCore.parseInt(arg, 0));
                break;
            }
            case "pinv": {
                this.partyCommand(p, new String[]{"invite", this.nameOf(UUID.fromString(arg))});
                this.openPartyMenu(p);
                break;
            }
            case "pcancel": {
                UUID target = UUID.fromString(arg);
                Invite inv = this.invites.get(target);
                if (inv != null && inv.from.equals(p.getUniqueId())) {
                    this.invites.remove(target);
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&7Invitation a " + this.nameOf(target) + " annulee.");
                    Player t = Bukkit.getPlayer((UUID)target);
                    if (t != null) {
                        EemiCore.msg((CommandSender)t, "&9[Groupe] &r&7" + p.getName() + " a annule son invitation.");
                    }
                }
                this.openPartyMenu(p);
                break;
            }
            case "paccept": 
            case "pdeny": {
                this.partyCommand(p, new String[]{e.isRightClick() || key.equals("pdeny") ? "deny" : "accept"});
                this.openPartyMenu(p);
                break;
            }
            case "pleave": {
                Party party = this.partyOf.get(p.getUniqueId());
                if (party != null && party.leader.equals(p.getUniqueId()) && e.isShiftClick()) {
                    this.partyCommand(p, new String[]{"disband"});
                } else {
                    this.partyCommand(p, new String[]{"leave"});
                }
                p.closeInventory();
                break;
            }
            case "pmember": {
                this.memberClick(p, UUID.fromString(arg), e.isShiftClick());
                break;
            }
            case "nav": {
                this.openNav(p);
                break;
            }
            case "navcat": {
                String[] np = arg.split(":", 2);
                this.openNavCat(p, np[0], np.length > 1 ? EemiCore.parseInt(np[1], 0) : 0);
                break;
            }
            case "play": {
                this.navPlay(p, arg, e.isRightClick());
                break;
            }
            case "random": {
                p.closeInventory();
                this.quickPlay(p);
                break;
            }
            case "pwatch": {
                this.navWatch(p, EemiCore.parseInt(arg, 0));
                break;
            }
            case "pspec": {
                GameInfo g = this.game(arg);
                if (g == null) {
                    return;
                }
                p.closeInventory();
                this.spectate(p, g);
                break;
            }
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    private void partyBroadcast(Party party, String legacy) {
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer((UUID)id);
            if (m == null) continue;
            EemiCore.msg((CommandSender)m, PARTY + legacy);
        }
    }

    private void removeFromParty(Party party, UUID id, String announce) {
        party.members.remove(id);
        this.partyOf.remove(id);
        this.offlineSince.remove(id);
        if (announce != null) {
            this.partyBroadcast(party, announce);
        }
        if (party.members.size() <= 1) {
            for (UUID m : new ArrayList<UUID>(party.members)) {
                this.partyOf.remove(m);
            }
            this.partyBroadcast(party, "&7Le groupe est dissous.");
            party.members.clear();
            return;
        }
        if (party.leader.equals(id)) {
            party.leader = party.members.get(0);
            PlayerData d = this.players.get(party.leader);
            this.partyBroadcast(party, "&e" + (d == null ? "?" : d.name) + " &7est le nouveau chef du groupe.");
        }
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer((UUID)id);
        if (p != null) {
            return p.getName();
        }
        PlayerData d = this.players.get(id);
        return d == null ? "?" : d.name;
    }

    private String whereText(UUID id) {
        Player m = Bukkit.getPlayer((UUID)id);
        if (m == null) {
            return "&8Deconnecte";
        }
        if (this.inLobby(m)) {
            return "&aAu hub";
        }
        GameInfo g = this.gameForWorld(m.getWorld().getName());
        return g == null ? "&7Monde : " + m.getWorld().getName() : "&7En jeu : " + g.name;
    }

    private GameInfo spectatableGameOf(UUID id) {
        Player m = Bukkit.getPlayer((UUID)id);
        if (m == null) {
            return null;
        }
        GameInfo g = this.gameOfPlayer(m);
        return g == null || g.spectate == null || g.spectate.isBlank() ? null : g;
    }

    private GameInfo gameOfPlayer(Player t) {
        if (this.inLobby(t)) {
            return null;
        }
        String world = t.getWorld().getName();
        ArrayList<GameInfo> found = new ArrayList<GameInfo>();
        for (GameInfo g : this.games.values()) {
            if (!g.hasWorld(world)) continue;
            found.add(g);
        }
        if (found.size() <= 1) {
            return found.isEmpty() ? null : (GameInfo)found.get(0);
        }
        GameInfo duels = this.game("duels");
        GameInfo ffa = this.game("ffa");
        if (duels != null && ffa != null && found.contains(duels) && found.contains(ffa)) {
            return t.getLocation().getZ() < -150.0 ? ffa : duels;
        }
        JoinMark j = this.lastJoin.get(t.getUniqueId());
        if (j != null) {
            for (GameInfo g : found) {
                if (!g.id.equals(j.game)) continue;
                return g;
            }
        }
        return (GameInfo)found.get(0);
    }

    private void spectatePlayer(Player p, Player target) {
        if (target.equals(p)) {
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cTu ne peux pas te regarder toi-meme.");
            return;
        }
        GameInfo own = this.gameOfPlayer(p);
        if (own != null && p.getGameMode() != GameMode.SPECTATOR) {
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cTu es en partie (" + own.name + "&c) : fais /lobby d'abord.");
            return;
        }
        GameInfo g = this.gameOfPlayer(target);
        if (g == null || target.hasMetadata("vanished") && !p.hasMetadata("vanished")) {
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&c" + target.getName() + " n'est dans aucune partie" + (this.inLobby(target) ? " (au hub)." : "."));
            return;
        }
        if (g.spectate == null || g.spectate.isBlank()) {
            EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&c" + g.name + " &cn'a pas de mode spectateur.");
            return;
        }
        p.closeInventory();
        EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&7Tu regardes la partie de &f" + target.getName() + " &7(" + g.name + "&7).");
        if (!g.id.equals("duels")) {
            this.spectate(p, g);
            return;
        }
        String name = target.getName();
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            if (p.isOnline()) {
                p.performCommand("duel spectate " + name);
            }
        });
    }

    private void openPartyMenu(Player p) {
        if (NAV) {
            this.navParty(p);
            return;
        }
        Invite received;
        int slot;
        Inventory inv;
        Menu holder = new Menu("party");
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)54, EemiCore.uiTitle("party", "Groupe"));
        Party party = this.partyOf.get(p.getUniqueId());
        boolean leader = party == null || party.leader.equals(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (!UI) {
            // avec la DA, le fond dessine remplace la ligne de vitres
            ItemStack filler = EemiCore.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
            for (int i = 45; i < 54; ++i) {
                inv.setItem(i, filler);
            }
        }
        inv.setItem(4, EemiCore.item(Material.LEAD, (String)(party == null ? "&7Tu n'es dans aucun groupe" : "&9Groupe de " + this.nameOf(party.leader) + " &7(" + party.members.size() + "/8)"), List.of("&7Le chef lance les parties pour tout le groupe", "&7(moustache, PNJ, /jouer).", "&f/pc <message> &7: parler au groupe"), false));
        inv.setItem(4, EemiCore.ic(inv.getItem(4), "groupe"));
        inv.setItem(9, EemiCore.ic(EemiCore.item(Material.BLUE_STAINED_GLASS_PANE, "&9Membres", List.of("&7Clic sur un membre en jeu :", "&7regarder sa partie"), false), "joueurs"));
        if (party == null) {
            inv.setItem(10, EemiCore.head(p.getUniqueId(), "&f" + p.getName(), List.of("&7Seul pour l'instant.", "&7Invite des joueurs avec le bouton en bas.")));
        } else {
            slot = 10;
            for (UUID uUID : party.members) {
                if (slot > 17) break;
                ArrayList<String> lore = new ArrayList<String>();
                if (uUID.equals(party.leader)) {
                    lore.add("&6Chef du groupe");
                }
                lore.add(this.whereText(uUID));
                PlayerData d = this.players.get(uUID);
                if (d != null) {
                    lore.add("&7Niveau " + this.levelColor(d.level) + d.level);
                }
                lore.add("");
                if (this.spectatableGameOf(uUID) != null && !uUID.equals(p.getUniqueId())) {
                    lore.add("&eClic : regarder sa partie");
                }
                if (party.leader.equals(p.getUniqueId()) && !uUID.equals(p.getUniqueId())) {
                    lore.add("&cShift-clic : exclure");
                }
                inv.setItem(slot, EemiCore.head(uUID, (Bukkit.getPlayer((UUID)uUID) != null ? "&f" : "&8") + this.nameOf(uUID), lore));
                holder.actions.put(slot++, "pmember:" + String.valueOf(uUID));
            }
        }
        inv.setItem(18, EemiCore.ic(EemiCore.item(Material.LIME_STAINED_GLASS_PANE, "&aInvitations envoyees", List.of("&7Clic sur une invitation : l'annuler"), false), "inviter"));
        slot = 19;
        for (Map.Entry entry : this.invites.entrySet()) {
            if (!((Invite)entry.getValue()).from.equals(p.getUniqueId())) continue;
            long left = Math.max(0L, (60000L - (now - ((Invite)entry.getValue()).time)) / 1000L);
            if (slot > 26 || left == 0L) continue;
            inv.setItem(slot, EemiCore.head((UUID)entry.getKey(), "&f" + this.nameOf((UUID)entry.getKey()), List.of("&7En attente de reponse", "&7Expire dans &e" + left + " s", "", "&cClic : annuler")));
            holder.actions.put(slot++, "pcancel:" + String.valueOf(entry.getKey()));
        }
        if (slot == 19) {
            inv.setItem(19, EemiCore.ic(EemiCore.item(Material.PAPER, "&7Aucune invitation en attente", List.of(), false), "aucun"));
        }
        inv.setItem(27, EemiCore.ic(EemiCore.item(Material.ORANGE_STAINED_GLASS_PANE, "&6Regarder une partie", List.of("&7Parties en cours avec", "&7mode spectateur"), false), "regarder"));
        slot = 28;
        for (GameInfo gameInfo : this.games.values()) {
            if (slot > 35) break;
            if (gameInfo.spectate == null || gameInfo.spectate.isBlank() || !gameInfo.fresh(this.expireMs) || !"playing".equals(gameInfo.state) && !"starting".equals(gameInfo.state) && !"ending".equals(gameInfo.state)) continue;
            inv.setItem(slot, EemiCore.ic(EemiCore.item(gameInfo.icon, gameInfo.name, List.of(this.statusText(gameInfo), "", "&eClic : regarder"), false), EemiCore.gameIcon(gameInfo.id)));
            holder.actions.put(slot++, "pspec:" + gameInfo.id);
        }
        if (slot == 28) {
            inv.setItem(28, EemiCore.ic(EemiCore.item(Material.ENDER_EYE, "&7Aucune partie en cours", List.of("&7Reviens quand une partie est lancee."), false), "aucun"));
        }
        if ((received = this.invites.get(p.getUniqueId())) != null && now - received.time <= 60000L) {
            inv.setItem(45, EemiCore.head(received.from, "&eInvitation de " + this.nameOf(received.from), List.of("&7Expire dans &e" + (60000L - (now - received.time)) / 1000L + " s", "", "&aClic gauche : accepter", "&cClic droit : refuser")));
            holder.actions.put(45, "paccept");
        }
        if (leader) {
            inv.setItem(47, EemiCore.ic(EemiCore.item(Material.WRITABLE_BOOK, "&aInviter un joueur", List.of("&7Choisis parmi les joueurs connectes"), false), "inviter"));
            holder.actions.put(47, "pinvite");
        }
        this.bottomButtons(holder, inv, "profile:" + String.valueOf(p.getUniqueId()), "&eRetour au profil", null);
        if (party != null) {
            inv.setItem(51, EemiCore.ic(EemiCore.item(Material.RED_DYE, "&cQuitter le groupe", party.leader.equals(p.getUniqueId()) ? List.of("&7Clic : quitter (un autre devient chef)", "&4Shift-clic : dissoudre le groupe") : List.of("&7Clic : quitter"), false), "quitter"));
            holder.actions.put(51, "pleave");
        }
        this.openLater(p, inv);
    }

    private void openInviteMenu(Player p, int page) {
        if (NAV) {
            this.navInvite(p, page);
            return;
        }
        Inventory inv;
        Menu holder = new Menu("pinvite");
        holder.inventory = inv = Bukkit.createInventory((InventoryHolder)holder, (int)54, EemiCore.uiTitle("pinvite", "Inviter un joueur"));
        ArrayList<Player> list = new ArrayList<Player>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || o.hasMetadata("vanished") || this.partyOf.containsKey(o.getUniqueId())) continue;
            list.add(o);
        }
        int pages = EemiCore.pageCount(list.size(), 45);
        page = Math.max(0, Math.min(page, pages - 1));
        int slot = 0;
        for (int k = page * 45; k < list.size() && slot < 45; ++k) {
            Player o = list.get(k);
            Invite pending = this.invites.get(o.getUniqueId());
            boolean mine = pending != null && pending.from.equals(p.getUniqueId());
            PlayerData d = this.data(o);
            inv.setItem(slot, EemiCore.head(o.getUniqueId(), "&f" + o.getName(), List.of("&7Niveau " + this.levelColor(d.level) + d.level, this.whereText(o.getUniqueId()), "", mine ? "&7Déjà invité (clic : renvoyer)" : "&aClic : inviter")));
            holder.actions.put(slot++, "pinv:" + String.valueOf(o.getUniqueId()));
        }
        if (list.isEmpty()) {
            inv.setItem(22, EemiCore.ic(EemiCore.item(Material.PAPER, "&7Personne à inviter", List.of("&7Tous les joueurs connectés", "&7sont déjà dans un groupe."), false), "aucun"));
        }
        this.pageButtons(holder, inv, page, pages, "pinvite");
        this.bottomButtons(holder, inv, "party", "&eRetour au groupe", null);
        this.openLater(p, inv);
    }

    private void memberClick(Player p, UUID id, boolean shift) {
        Party party = this.partyOf.get(p.getUniqueId());
        if (party == null || !party.members.contains(id) || id.equals(p.getUniqueId())) {
            return;
        }
        if (shift && party.leader.equals(p.getUniqueId())) {
            this.partyCommand(p, new String[]{"kick", this.nameOf(id)});
            this.openPartyMenu(p);
            return;
        }
        Player t = Bukkit.getPlayer((UUID)id);
        if (t == null || this.spectatableGameOf(id) == null) {
            EemiCore.msg((CommandSender)p, "&9[Groupe] &r&7" + this.nameOf(id) + " n'est dans aucune partie a regarder.");
            return;
        }
        this.spectatePlayer(p, t);
    }

    private void partyCommand(Player p, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        Party party = this.partyOf.get(p.getUniqueId());
        switch (sub) {
            case "invite": 
            case "inviter": {
                if (args.length < 2) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&c/party invite <joueur>");
                    return;
                }
                Player t = Bukkit.getPlayerExact((String)args[1]);
                if (t == null || t.equals(p)) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cJoueur introuvable.");
                    return;
                }
                if (party != null && !party.leader.equals(p.getUniqueId())) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cSeul le chef peut inviter.");
                    return;
                }
                if (this.partyOf.containsKey(t.getUniqueId())) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&c" + t.getName() + " est deja dans un groupe.");
                    return;
                }
                if (party != null && party.members.size() >= 8) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cGroupe complet (8 max).");
                    return;
                }
                this.invites.put(t.getUniqueId(), new Invite(p.getUniqueId(), System.currentTimeMillis()));
                EemiCore.msg((CommandSender)p, "&9[Groupe] &r&7Invitation envoyee a &f" + t.getName() + "&7 (60 s).");
                t.sendMessage(EemiCore.c("&9[Groupe] &r&f" + p.getName() + " &7t'invite dans son groupe. ").append(EemiCore.button("[ACCEPTER]", NamedTextColor.GREEN, "/party accept", "Rejoindre le groupe")).append((Component)Component.text((String)" ")).append(EemiCore.button("[REFUSER]", NamedTextColor.RED, "/party deny", "Refuser")));
                t.playSound(t.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
                break;
            }
            case "accept": 
            case "accepter": {
                Invite inv = this.invites.remove(p.getUniqueId());
                if (inv == null || System.currentTimeMillis() - inv.time > 60000L) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cAucune invitation en cours.");
                    return;
                }
                if (party != null) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cQuitte d'abord ton groupe (/party leave).");
                    return;
                }
                Player from = Bukkit.getPlayer((UUID)inv.from);
                if (from == null) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cLe joueur qui t'a invite n'est plus connecte.");
                    return;
                }
                Party target = this.partyOf.get(inv.from);
                if (target == null) {
                    target = new Party();
                    target.leader = inv.from;
                    target.members.add(inv.from);
                    this.partyOf.put(inv.from, target);
                } else if (!target.leader.equals(inv.from)) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cCette invitation n'est plus valable.");
                    return;
                }
                if (target.members.size() >= 8) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cGroupe complet.");
                    return;
                }
                target.members.add(p.getUniqueId());
                this.partyOf.put(p.getUniqueId(), target);
                this.partyBroadcast(target, "&a" + p.getName() + " a rejoint le groupe.");
                break;
            }
            case "deny": 
            case "refuser": {
                Invite inv = this.invites.remove(p.getUniqueId());
                if (inv == null) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cAucune invitation en cours.");
                    return;
                }
                EemiCore.msg((CommandSender)p, "&9[Groupe] &r&7Invitation refusee.");
                Player from = Bukkit.getPlayer((UUID)inv.from);
                if (from == null) break;
                EemiCore.msg((CommandSender)from, "&9[Groupe] &r&c" + p.getName() + " a refuse ton invitation.");
                break;
            }
            case "leave": 
            case "quitter": {
                if (party == null) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cTu n'es dans aucun groupe.");
                    return;
                }
                EemiCore.msg((CommandSender)p, "&9[Groupe] &r&7Tu as quitte le groupe.");
                this.removeFromParty(party, p.getUniqueId(), "&7" + p.getName() + " a quitte le groupe.");
                break;
            }
            case "kick": 
            case "exclure": {
                if (party == null || !party.leader.equals(p.getUniqueId())) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cSeul le chef peut exclure.");
                    return;
                }
                if (args.length < 2) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&c/party kick <joueur>");
                    return;
                }
                UUID target = null;
                for (UUID id : party.members) {
                    if (!this.nameOf(id).equalsIgnoreCase(args[1])) continue;
                    target = id;
                }
                if (target == null || target.equals(p.getUniqueId())) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cCe joueur n'est pas dans ton groupe.");
                    return;
                }
                Player t = Bukkit.getPlayer((UUID)target);
                if (t != null) {
                    EemiCore.msg((CommandSender)t, "&9[Groupe] &r&cTu as ete exclu du groupe.");
                }
                this.removeFromParty(party, target, "&7" + this.nameOf(target) + " a ete exclu du groupe.");
                break;
            }
            case "disband": 
            case "dissoudre": {
                if (party == null || !party.leader.equals(p.getUniqueId())) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cSeul le chef peut dissoudre le groupe.");
                    return;
                }
                this.partyBroadcast(party, "&7Le groupe a ete dissous par " + p.getName() + ".");
                for (UUID id : party.members) {
                    this.partyOf.remove(id);
                    this.offlineSince.remove(id);
                }
                party.members.clear();
                break;
            }
            case "list": 
            case "liste": {
                if (party == null) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&7Tu n'es dans aucun groupe. &f/party invite <joueur>");
                    return;
                }
                EemiCore.msg((CommandSender)p, "&9[Groupe] &r&eMembres (" + party.members.size() + "/8) :");
                for (UUID id : party.members) {
                    boolean on = Bukkit.getPlayer((UUID)id) != null;
                    EemiCore.msg((CommandSender)p, " " + (on ? "&a" : "&8") + this.nameOf(id) + (id.equals(party.leader) ? " &6(chef)" : "") + (on ? "" : " &8(deconnecte)"));
                }
                break;
            }
            case "chat": {
                this.partyChat(p, String.join((CharSequence)" ", Arrays.copyOfRange(args, 1, args.length)));
                break;
            }
            case "menu": {
                this.openPartyMenu(p);
                break;
            }
            default: {
                EemiCore.msg((CommandSender)p, "&9[Groupe] &r&eCommandes du groupe :");
                EemiCore.msg((CommandSender)p, "&f/party &7- menu du groupe (membres, invitations, regarder une partie)");
                EemiCore.msg((CommandSender)p, "&f/party invite <joueur> &7- inviter (le chef)");
                EemiCore.msg((CommandSender)p, "&f/party accept &7| &f/party deny &7- repondre a une invitation");
                EemiCore.msg((CommandSender)p, "&f/party leave &7- quitter | &f/party list &7- membres");
                EemiCore.msg((CommandSender)p, "&f/party kick <joueur> &7| &f/party disband &7- chef");
                EemiCore.msg((CommandSender)p, "&f/pc <message> &7- parler au groupe");
                EemiCore.msg((CommandSender)p, "&7Le chef lance les parties pour tout le groupe (/eemi join, boussole, /jouer).");
            }
        }
    }

    private void partyChat(Player p, String text) {
        Party party = this.partyOf.get(p.getUniqueId());
        if (party == null) {
            EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cTu n'es dans aucun groupe.");
            return;
        }
        if (text.isBlank()) {
            return;
        }
        Component line = EemiCore.c("&9[Groupe] &r&b" + p.getName() + "&7: ").append((Component)Component.text((String)text, (TextColor)NamedTextColor.WHITE));
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer((UUID)id);
            if (m == null) continue;
            m.sendMessage(line);
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("eemi")) {
            return this.eemiCommand(sender, args);
        }
        if (!(sender instanceof Player)) {
            if (name.equals("profil") && args.length > 0) {
                PlayerData d = this.dataByName(args[0]);
                if (d == null) {
                    EemiCore.msg(sender, "&6[EEMICRAFT] &r&cJoueur inconnu.");
                } else {
                    this.profileText(sender, d);
                }
                return true;
            }
            EemiCore.msg(sender, "&cCommande reservee aux joueurs.");
            return true;
        }
        Player p = (Player)sender;
        switch (name) {
            case "party": {
                this.partyCommand(p, args);
                break;
            }
            case "pc": {
                this.partyChat(p, String.join((CharSequence)" ", args));
                break;
            }
            case "jouer": {
                this.quickPlay(p);
                break;
            }
            case "profil": {
                PlayerData d;
                PlayerData playerData = d = args.length > 0 ? this.dataByName(args[0]) : this.data(p);
                if (d == null) {
                    EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cJoueur inconnu.");
                    break;
                }
                this.openProfile(p, d);
                break;
            }
            case "boutique": {
                this.openShop(p);
                break;
            }
            case "jeux": {
                if (args.length > 0 && NavFonds.PAGES.containsKey(args[0].toLowerCase(Locale.ROOT))) {
                    this.openNavCat(p, args[0].toLowerCase(Locale.ROOT), 0);
                } else {
                    this.openNav(p);
                }
                break;
            }
            case "quetes": {
                this.openQuests(p);
                break;
            }
            case "spectate": {
                if (args.length < 1) {
                    EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&c/spectate <joueur> &7- regarder la partie d'un joueur");
                    return true;
                }
                Player t = Bukkit.getPlayer((String)args[0]);
                if (t == null || t.hasMetadata("vanished") && !p.hasMetadata("vanished")) {
                    EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&c" + args[0] + " n'est pas connecte.");
                    break;
                }
                this.spectatePlayer(p, t);
                break;
            }
            default: {
                return false;
            }
        }
        return true;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private boolean eemiCommand(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean trusted = sender instanceof ConsoleCommandSender || sender.isOp();
        switch (sub) {
            case "status": {
                if (!trusted || args.length < 6) {
                    return true;
                }
                String state = args[2].toLowerCase(Locale.ROOT);
                if (!Set.of("waiting", "starting", "playing", "ending", "nobody").contains(state)) {
                    return true;
                }
                this.updateStatus(args[1], state, EemiCore.parseInt(args[3], 0), EemiCore.parseInt(args[4], 0), EemiCore.parseInt(args[5], 0));
                return true;
            }
            case "reward": {
                if (!trusted || args.length < 4) {
                    return true;
                }
                String type = args[3].toLowerCase(Locale.ROOT);
                if (!Set.of("play", "win", "kill").contains(type)) {
                    return true;
                }
                PlayerData d = this.dataByName(args[1]);
                if (d == null) {
                    OfflinePlayer off = Bukkit.getOfflinePlayerIfCached((String)args[1]);
                    if (off == null) {
                        return true;
                    }
                    d = this.players.computeIfAbsent(off.getUniqueId(), PlayerData::new);
                    d.name = off.getName() == null ? args[1] : off.getName();
                    this.nameIndex.put(d.name.toLowerCase(Locale.ROOT), d.uuid);
                }
                GameInfo rg = this.gameOrCreate(args[2]);
                this.reward(d, rg, type);
                if (type.equals("win") && this.live != null) {
                    this.live.win(d.name, rg);
                }
                return true;
            }
            case "join": 
            case "rejoindre": {
                GameInfo g;
                if (!(sender instanceof Player)) {
                    EemiCore.msg(sender, "&cCommande reservee aux joueurs.");
                    return true;
                }
                Player p = (Player)sender;
                GameInfo gameInfo = g = args.length > 1 ? this.game(args[1]) : null;
                if (g == null) {
                    EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cJeu inconnu. &7Jeux : " + String.join((CharSequence)", ", this.games.keySet()));
                    return true;
                }
                this.joinGame(p, g);
                return true;
            }
            case "spectate": 
            case "regarder": {
                GameInfo g;
                if (!(sender instanceof Player)) {
                    return true;
                }
                Player p = (Player)sender;
                GameInfo gameInfo = g = args.length > 1 ? this.game(args[1]) : null;
                if (g == null) {
                    Player t;
                    Player player = t = args.length > 1 ? Bukkit.getPlayerExact((String)args[1]) : null;
                    if (t != null && (!t.hasMetadata("vanished") || p.hasMetadata("vanished"))) {
                        this.spectatePlayer(p, t);
                        return true;
                    } else {
                        EemiCore.msg((CommandSender)p, "&6[EEMICRAFT] &r&cJeu inconnu. &7/eemi spectate <jeu> ou /spectate <joueur>");
                    }
                    return true;
                }
                this.spectate(p, g);
                return true;
            }
            case "suivre": {
                if (!(sender instanceof Player)) return true;
                Player p = (Player)sender;
                if (args.length < 2) {
                    return true;
                }
                Player leader = Bukkit.getPlayerExact((String)args[1]);
                Party party = this.partyOf.get(p.getUniqueId());
                if (leader == null || party == null || !party.members.contains(leader.getUniqueId())) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&cCe joueur n'est pas dans ton groupe.");
                    return true;
                }
                GameInfo g = this.gameForWorld(leader.getWorld().getName());
                JoinMark j = this.lastJoin.get(leader.getUniqueId());
                if (g == null && j != null) {
                    g = this.game(j.game);
                }
                if (g == null) {
                    EemiCore.msg((CommandSender)p, "&9[Groupe] &r&c" + leader.getName() + " n'est dans aucun jeu.");
                    return true;
                }
                LeaderCmd lc = this.leaderCmd.get(leader.getUniqueId());
                if (lc != null && lc.game.equals(g.id) && System.currentTimeMillis() - lc.time < 1800000L) {
                    String cmd = lc.cmd;
                    if (this.inProtectedWorld(p)) {
                        this.viaLobby(p, () -> p.performCommand(cmd));
                        return true;
                    }
                    Bukkit.getScheduler().runTask((Plugin)this, () -> {
                        if (p.isOnline()) {
                            p.performCommand(cmd);
                        }
                    });
                    return true;
                } else {
                    this.runJoin(p, g);
                }
                return true;
            }
            case "appels": 
            case "calls": {
                if (!(sender instanceof Player)) {
                    return true;
                }
                Player p = (Player)sender;
                PlayerData d = this.data(p);
                d.calls = !d.calls;
                this.markDirty(d);
                EemiCore.msg((CommandSender)p, PREFIX + (d.calls ? "&aAppels a la partie actives." : "&7Appels a la partie coupes. &f/eemi appels &7pour les remettre."));
                return true;
            }
            case "top": 
            case "classement": {
                if (args.length < 2) {
                    EemiCore.msg(sender, "&6[EEMICRAFT] &r&c/eemi top <jeu|level> [wins|kills|plays]");
                    return true;
                }
                this.showTop(sender, args[1], args.length > 2 ? args[2] : "wins");
                return true;
            }
            case "maintenance": {
                this.maintenanceCommand(sender, args);
                return true;
            }
            case "jeux": 
            case "games": {
                EemiCore.msg(sender, "&6[EEMICRAFT] &r&eJeux :");
                for (GameInfo g : this.games.values()) {
                    EemiCore.msg(sender, " &7" + g.id + " &8- " + g.name + " &8: " + this.statusText(g));
                }
                return true;
            }
            case "reload": {
                if (!sender.hasPermission("eemicore.admin")) {
                    EemiCore.msg(sender, "&cPermission refusee.");
                    return true;
                }
                this.loadSettings();
                this.recomputeTops();
                this.startStatusFile();
                EemiCore.msg(sender, "&6[EEMICRAFT] &r&aConfig rechargee (" + this.games.size() + " jeux).");
                return true;
            }
            case "cosmetic": {
                // /eemi cosmetic <joueur> <id> : donne et equipe un cosmetique (boutique du casino)
                if (!sender.hasPermission("eemicore.admin")) {
                    EemiCore.msg(sender, "&cPermission refusee.");
                    return true;
                }
                if (args.length < 3) {
                    EemiCore.msg(sender, "&6[EEMICRAFT] &r&c/eemi cosmetic <joueur> <id>");
                    return true;
                }
                PlayerData cd = this.dataByName(args[1]);
                ConfigurationSection cs = this.cosmetic(args[2]);
                if (cd == null || cs == null) {
                    EemiCore.msg(sender, "&6[EEMICRAFT] &r&cJoueur ou cosmetique inconnu.");
                    return true;
                }
                String cat = cs.getString("type", "trail");
                cd.owned.add(args[2]);
                cd.equipped.put(cat, args[2]);
                this.markDirty(cd);
                Player ct = Bukkit.getPlayer((UUID)cd.uuid);
                if (ct != null) {
                    if (cat.equals("hat")) {
                        this.removeHat(ct);
                        this.applyHat(ct);
                    }
                    if (cat.equals("gadget")) {
                        this.removeGadget(ct);
                        this.applyGadget(ct);
                    }
                    if (cat.equals("title")) {
                        this.updateTitleTag(ct);
                    }
                }
                EemiCore.msg(sender, "&6[EEMICRAFT] &r&a" + cd.name + " recoit " + args[2] + ".");
                return true;
            }
            case "give": {
                long n;
                if (!sender.hasPermission("eemicore.admin")) {
                    EemiCore.msg(sender, "&cPermission refusee.");
                    return true;
                }
                if (args.length < 4) {
                    EemiCore.msg(sender, "&6[EEMICRAFT] &r&c/eemi give <joueur> <coins|xp> <n>");
                    return true;
                }
                PlayerData d = this.dataByName(args[1]);
                if (d == null) {
                    EemiCore.msg(sender, "&6[EEMICRAFT] &r&cJoueur inconnu.");
                    return true;
                }
                try {
                    n = Long.parseLong(args[3]);
                }
                catch (NumberFormatException e) {
                    EemiCore.msg(sender, "&6[EEMICRAFT] &r&cNombre invalide.");
                    return true;
                }
                if (args[2].equalsIgnoreCase("xp")) {
                    this.giveXp(d, n);
                } else {
                    d.coins = Math.max(0L, d.coins + n);
                }
                this.markDirty(d);
                EemiCore.msg(sender, "&6[EEMICRAFT] &r&a" + d.name + " : " + d.coins + " pieces, niveau " + d.level + ", " + d.xp + " xp.");
                Player t = Bukkit.getPlayer((UUID)d.uuid);
                if (t == null || args[2].equalsIgnoreCase("xp") || n <= 0L) return true;
                EemiCore.msg((CommandSender)t, "&6[EEMICRAFT] &r&6+" + n + " pièces");
                return true;
            }
        }
        EemiCore.msg(sender, "&6[EEMICRAFT] &r&eCommandes :");
        EemiCore.msg(sender, "&f/eemi join <jeu> &7- rejoindre un jeu (avec ton groupe si tu es chef)");
        EemiCore.msg(sender, "&f/eemi spectate <jeu> &7- regarder une partie");
        EemiCore.msg(sender, "&f/spectate <joueur> &7- regarder la partie d'un joueur");
        EemiCore.msg(sender, "&f/jouer &7- rejoindre la partie la plus remplie");
        EemiCore.msg(sender, "&f/eemi jeux &7- etat des jeux");
        EemiCore.msg(sender, "&f/eemi top <jeu|level> [wins|kills|plays] &7- classements");
        EemiCore.msg(sender, "&f/eemi appels &7- couper/remettre les appels a la partie");
        EemiCore.msg(sender, "&f/party &7- groupes | &f/profil &7| &f/boutique &7| &f/quetes");
        if (sender.hasPermission("eemicore.admin")) {
            EemiCore.msg(sender, "&c/eemi reload &7| &c/eemi give <joueur> <coins|xp> <n>");
        }
        if (sender.hasPermission("eemicore.maintenance")) {
            EemiCore.msg(sender, "&c/eemi maintenance <jeu> on [raison...] &7| &c/eemi maintenance <jeu> off &7| &c/eemi maintenance list");
        }
        if (!sender.hasPermission("eemicore.admin")) return true;
        EemiCore.msg(sender, "&8eemi status <jeu> <state> <waiting> <playing> <max> | eemi reward <pseudo> <jeu> <play|win|kill>");
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        ArrayList<String> options = new ArrayList<String>();
        if (name.equals("eemi")) {
            if (args.length == 1) {
                options.addAll(List.of("join", "spectate", "jeux", "top", "appels", "help"));
                if (sender.hasPermission("eemicore.admin")) {
                    options.addAll(List.of("reload", "give"));
                }
                if (sender.hasPermission("eemicore.maintenance")) {
                    options.add("maintenance");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("maintenance") && sender.hasPermission("eemicore.maintenance")) {
                options.add("list");
                options.addAll(this.games.keySet());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("maintenance") && sender.hasPermission("eemicore.maintenance") && this.game(args[1]) != null) {
                options.addAll(this.maintenance.containsKey(this.game((String)args[1]).id) ? List.of("off", "on") : List.of("on", "off"));
            } else if (args.length == 2 && Set.of("join", "spectate", "top").contains(args[0].toLowerCase(Locale.ROOT))) {
                options.addAll(this.games.keySet());
                if (args[0].equalsIgnoreCase("top")) {
                    options.add("level");
                }
                if (args[0].equalsIgnoreCase("spectate")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.equals(sender) || p.hasMetadata("vanished")) continue;
                        options.add(p.getName());
                    }
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("top")) {
                options.addAll(List.of(STAT_TYPES));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    options.add(p.getName());
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                options.addAll(List.of("coins", "xp"));
            }
        } else if (name.equals("party")) {
            if (args.length == 1) {
                options.addAll(List.of("menu", "invite", "accept", "deny", "leave", "kick", "disband", "list", "chat", "help"));
            } else if (args.length == 2 && Set.of("invite", "kick").contains(args[0].toLowerCase(Locale.ROOT))) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    options.add(p.getName());
                }
            }
        } else if (name.equals("profil") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (name.equals("spectate") && args.length == 1) {
            Player sp;
            boolean vanished = sender instanceof Player && (sp = (Player)sender).hasMetadata("vanished");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(sender) || !vanished && p.hasMetadata("vanished")) continue;
                options.add(p.getName());
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return options;
    }

    private static final class Party {
        UUID leader;
        final List<UUID> members = new ArrayList<UUID>();

        private Party() {
        }
    }

    private record JoinMark(String game, long time) {
    }

    private record Maintenance(String reason, String by, long since) {
    }

    private record GameSnapshot(String id, String name, Maintenance maintenance, String state, int waiting, int playing, int max) {
    }

    /** 1.9.0 : uuid (null pour les anciens appels) et xp dans le niveau (-1 = pas un classement de niveaux). */
    record TopEntry(String name, long value, UUID uuid, long xp) {
        TopEntry(String name, long value) {
            this(name, value, null, -1L);
        }
    }

    private record LeaderCmd(String game, String cmd, long time) {
    }

    private static final class Menu
    implements InventoryHolder {
        final String kind;
        final Map<Integer, String> actions = new HashMap<Integer, String>();
        Inventory inventory;

        Menu(String kind) {
            this.kind = kind;
        }

        public Inventory getInventory() {
            return this.inventory;
        }
    }

    private record Invite(UUID from, long time) {
    }
}

