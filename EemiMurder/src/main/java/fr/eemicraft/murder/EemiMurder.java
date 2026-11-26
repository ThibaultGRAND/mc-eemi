/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.bossbar.BossBar
 *  net.kyori.adventure.bossbar.BossBar$Color
 *  net.kyori.adventure.bossbar.BossBar$Overlay
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  net.kyori.adventure.title.Title
 *  net.kyori.adventure.title.Title$Times
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.Keyed
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Sound
 *  org.bukkit.Tag
 *  org.bukkit.World
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.block.Block
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.AbstractArrow
 *  org.bukkit.entity.AbstractArrow$PickupStatus
 *  org.bukkit.entity.Arrow
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.event.Event$Result
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityPickupItemEvent
 *  org.bukkit.event.entity.EntityShootBowEvent
 *  org.bukkit.event.entity.FoodLevelChangeEvent
 *  org.bukkit.event.entity.ProjectileHitEvent
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerSwapHandItemsEvent
 *  org.bukkit.event.player.PlayerTeleportEvent
 *  org.bukkit.event.player.PlayerTeleportEvent$TeleportCause
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.scoreboard.Criteria
 *  org.bukkit.scoreboard.DisplaySlot
 *  org.bukkit.scoreboard.Objective
 *  org.bukkit.scoreboard.Scoreboard
 *  org.bukkit.scoreboard.Team
 *  org.bukkit.scoreboard.Team$Option
 *  org.bukkit.scoreboard.Team$OptionStatus
 */
package fr.eemicraft.murder;

import fr.eemicraft.murder.GameMap;
import fr.eemicraft.murder.WorldClone;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class EemiMurder
extends JavaPlugin
implements Listener {
    private static final String PREFIX = "\u00a78[\u00a7dMurder\u00a78] \u00a77";
    private static final String TEAM = "eemimurder";
    private static final long RECREATE_DELAY_MS = 10000L;
    private NamespacedKey toolKey;
    /** 1.14.0 (#134) : objet « Pret » du salon d'attente (case READY_SLOT ; 8 = lit « Quitter le Murder »). */
    private NamespacedKey readyKey;
    private static final int READY_SLOT = 4;
    private final Map<String, GameMap> maps = new LinkedHashMap<String, GameMap>();
    private Location lobby;
    private final TreeMap<Integer, Game> games = new TreeMap();
    private final Map<UUID, Game> byPlayer = new HashMap<UUID, Game>();
    private final Map<UUID, Game> bySpectator = new HashMap<UUID, Game>();
    private final Map<String, Long> destroyedAt = new HashMap<String, Long>();
    private final Set<String> modelBusy = new HashSet<String>();
    private final Map<String, Integer> pendingCopies = new HashMap<String, Integer>();
    /** Maps construites par le plugin (Manoir-bibliotheque 1.7.0, Fete foraine et Grand Musee 1.8.0, Paquebot 1.9.0). */
    private final List<BuiltMap> built = new ArrayList<BuiltMap>();
    /** 1.12.0 : derniere petite map tiree (toutes parties confondues) : les petites maps passent en alternance. */
    private String lastSmallMapId;
    /** 1.14.1 : derniere map JOUEE (toutes parties), sauvee dans data.yml (cle last-map) : exclue du tirage suivant, meme apres un redemarrage. */
    private String lastPlayedMapId;

    private java.io.File dataFile() {
        return new java.io.File(this.getDataFolder(), "data.yml");
    }

    private void loadLastMaps() {
        java.io.File f = this.dataFile();
        if (!f.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        this.lastPlayedMapId = y.getString("last-map");
        this.lastSmallMapId = y.getString("last-small-map");
    }

    /** 1.14.1 : appele au lancement d'une partie (map reellement jouee). */
    void saveLastPlayed(GameMap m) {
        this.lastPlayedMapId = m.id;
        if (m.small) {
            this.lastSmallMapId = m.id;
        }
        java.io.File f = this.dataFile();
        org.bukkit.configuration.file.YamlConfiguration y = f.exists() ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f) : new org.bukkit.configuration.file.YamlConfiguration();
        y.set("last-map", this.lastPlayedMapId);
        y.set("last-small-map", this.lastSmallMapId);
        try {
            this.getDataFolder().mkdirs();
            y.save(f);
        }
        catch (java.io.IOException e) {
            this.getLogger().warning("Impossible d'ecrire data.yml : " + e.getMessage());
        }
    }

    public void onEnable() {
        this.toolKey = new NamespacedKey((Plugin)this, "tool");
        this.readyKey = new NamespacedKey((Plugin)this, "ready");
        this.saveDefaultConfig();
        this.built.clear();
        for (MapDef d : MapDef.ALL) {
            this.built.add(new BuiltMap(this, d));
        }
        this.loadSettings();
        this.loadLastMaps();
        this.enableBuiltMaps();
        this.games.put(1, new Game(1));
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            for (String base : this.mapWorlds()) {
                World w = Bukkit.getWorld((String)base);
                if (w == null) continue;
                WorldClone.cleanup((Plugin)this, w, base, this.maxInstances());
                this.refreshModel(base);
            }
        });
        this.reportStatus();
    }

    public void onDisable() {
        for (Game g : new ArrayList<Game>(this.games.values())) {
            g.shutdown();
        }
        for (BuiltMap b : this.built) {
            b.disable();
        }
    }

    /** Map construite par le plugin pour ce monde (de base), ou null. */
    private BuiltMap builtFor(String world) {
        for (BuiltMap b : this.built) {
            if (b.def.world.equals(world)) return b;
        }
        return null;
    }

    /** Map construite occupee (construction, echec, modele pas encore relance) : ni tiree au sort ni copiee. */
    private boolean builtBusy(String world) {
        BuiltMap b = this.builtFor(world);
        return b != null && b.isBusy(world);
    }

    /** Vrai si ce monde est une map construite par le plugin ou l'une de ses copies (mm_fete, mm_fete_2...). */
    private boolean isBuiltWorld(String worldName) {
        for (BuiltMap b : this.built) {
            if (WorldClone.index(b.def.world, worldName) > 0) return true;
        }
        return false;
    }

    /** Mondes des maps construites : crees, et construits si leur version a change (seulement si la map est jouable). */
    private void enableBuiltMaps() {
        for (BuiltMap b : this.built) {
            MapDef d = b.def;
            GameMap m = this.maps.get(d.id);
            if (m == null || !d.world.equals(m.worldName) || b.isBuilding()) {
                continue;
            }
            if (!b.needsBuild()) {
                if (b.needsPregen()) b.pregenOnly();
                continue;
            }
            String why = this.builtBlocked(d.world);
            if (why != null) {
                this.getLogger().warning("Map " + d.name + " a construire mais " + why + " : /murder buildmap " + d.id + " plus tard.");
                continue;
            }
            this.getLogger().info("Map " + d.name + " : construction (premiere fois, nouvelle version ou pose inachevee)...");
            b.build(() -> this.refreshModel(d.world));
        }
    }

    /** Raison d'interdire une construction de cette map maintenant (partie dessus, copie en cours), ou null. */
    private String builtBlocked(String world) {
        for (Game g : this.games.values()) {
            boolean onIt = g.chosen != null && world.equals(g.chosen.worldName) || g.current != null && world.equals(g.current.worldName);
            if (onIt) {
                return g.label() + " utilise cette map";
            }
        }
        if (this.modelBusy.contains(world) || this.pendingCopies.getOrDefault(world, 0) > 0) {
            return "une copie de la map est en cours";
        }
        return null;
    }

    private void loadSettings() {
        this.reloadConfig();
        this.maps.clear();
        ConfigurationSection sec = this.getConfig().getConfigurationSection("maps");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection m = sec.getConfigurationSection(id);
                if (m == null) continue;
                MapDef d = MapDef.byId(id);
                this.maps.put(id, GameMap.load(id, d != null && d.id.equals(id) ? BuiltMap.merged(m, d) : m));
            }
        }
        for (MapDef d : MapDef.ALL) {
            if (!this.maps.containsKey(d.id) && !BuiltMap.retired(this.getConfig(), d)) {
                this.maps.put(d.id, GameMap.load(d.id, BuiltMap.defaultSection(d)));
            }
        }
        this.lobby = EemiMurder.parseLocation(this.getConfig().getString("lobby", ""));
        this.getLogger().info(this.maps.size() + " map(s) chargee(s), lobby " + (this.lobby == null ? "non d\u00e9fini" : "ok") + ".");
    }

    private int cfg(String key, int def) {
        return this.getConfig().getInt(key, def);
    }

    private int maxInstances() {
        return Math.max(1, this.cfg("max-instances", 3));
    }

    /** Duree d'une partie sur cette map : small-map-game-time pour une petite map (1.10.0, defaut = game-time). */
    private int gameTime(GameMap m) {
        int def = this.cfg("game-time", 270);
        return m != null && m.small ? this.cfg("small-map-game-time", def) : def;
    }

    /** 1.10.0 : au plus ce nombre d'inscrits, seules les petites maps sont tirees ; au-dessus, seules les grandes. */
    private int smallMapMaxPlayers() {
        return this.cfg("small-map-max-players", 5);
    }

    private int maxPlayers() {
        return this.cfg("max-players", 16);
    }

    private long knifeCooldownMs() {
        return Math.max(0L, Math.round(this.getConfig().getDouble("kill-cooldown", 3.0) * 1000.0));
    }

    private static String formatSeconds(long ms) {
        return String.format(Locale.ROOT, "%.1f s", (double)ms / 1000.0);
    }

    private Set<String> mapWorlds() {
        LinkedHashSet<String> s = new LinkedHashSet<String>();
        for (GameMap m : this.maps.values()) {
            s.add(m.worldName);
        }
        return s;
    }

    private static String modelName(String base) {
        return base + "_modele";
    }

    private boolean refreshModel(String base) {
        World template = Bukkit.getWorld((String)base);
        if (template == null) {
            return false;
        }
        BuiltMap bm = this.builtFor(base);
        if (bm != null && bm.blocksCopy(base)) {
            return false;
        }
        if (this.modelBusy.contains(base) || this.pendingCopies.getOrDefault(base, 0) > 0) {
            return false;
        }
        for (Game g : this.games.values()) {
            if (g.world == null || !g.world.equals((Object)template) || g.state != State.PLAYING && g.state != State.ENDING) continue;
            return false;
        }
        World old = Bukkit.getWorld((String)EemiMurder.modelName(base));
        if (old != null && !Bukkit.unloadWorld((World)old, (boolean)false)) {
            this.getLogger().warning("Impossible de d\u00e9charger " + old.getName() + " : mod\u00e8le non refait.");
            return false;
        }
        this.modelBusy.add(base);
        WorldClone.create((Plugin)this, template, EemiMurder.modelName(base), w -> {
            this.modelBusy.remove(base);
            if (w == null) {
                this.getLogger().warning("Mod\u00e8le " + EemiMurder.modelName(base) + "non cr\u00e9\u00e9 : pas de partie 2+ sur cette map.");
            }
        });
        return true;
    }

    private void ensureModels() {
        for (String base : this.mapWorlds()) {
            if (Bukkit.getWorld((String)base) == null || Bukkit.getWorld((String)EemiMurder.modelName(base)) != null || this.modelBusy.contains(base)) continue;
            WorldClone.cleanup((Plugin)this, Bukkit.getWorld((String)base), base, this.maxInstances());
            this.refreshModel(base);
        }
    }

    private void requestCopy(String base, int n, Consumer<World> done) {
        String name = WorldClone.name(base, n);
        World model = Bukkit.getWorld((String)EemiMurder.modelName(base));
        long since = System.currentTimeMillis() - this.destroyedAt.getOrDefault(name, 0L);
        if (model == null || this.modelBusy.contains(base) || since < 10000L) {
            if (model == null && !this.modelBusy.contains(base) && !this.refreshModel(base)) {
                done.accept(null);
                return;
            }
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.requestCopy(base, n, done), 40L);
            return;
        }
        this.pendingCopies.merge(base, 1, Integer::sum);
        WorldClone.create((Plugin)this, model, name, w -> {
            this.pendingCopies.merge(base, -1, Integer::sum);
            done.accept((World)w);
        });
    }

    private void destroyCopy(World w, String base) {
        if (w == null || w.getName().equals(base)) {
            return;
        }
        this.destroyedAt.put(w.getName(), System.currentTimeMillis());
        WorldClone.destroy((Plugin)this, w, base, this::sendToHub);
    }

    private void tick() {
        for (Game g : new ArrayList<Game>(this.games.values())) {
            g.tick();
        }
    }

    private void reportStatus() {
        boolean anyWaiting = false;
        boolean anyPlaying = false;
        long waiting = 0L;
        int playing = 0;
        for (Game g : this.games.values()) {
            if (g.state == State.WAITING || g.state == State.STARTING) {
                anyWaiting = true;
            } else {
                anyPlaying = true;
            }
            waiting += g.players.stream().filter(u -> !g.inGame.contains(u)).count();
            if (g.state != State.PLAYING && g.state != State.ENDING) continue;
            playing += g.alive.size();
        }
        String st = anyWaiting ? "waiting" : (anyPlaying ? "playing" : "waiting");
        this.eemi("status murder " + st + " " + waiting + " " + playing + " " + this.maxPlayers() * this.maxInstances());
    }

    private void join(Player p) {
        if (this.byPlayer.containsKey(p.getUniqueId())) {
            p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77Tu es d\u00e9j\u00e0 inscrit.");
            return;
        }
        if (this.lobby == null || this.lobby.getWorld() == null) {
            p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cLe lobby d'attente n'est pas configur\u00e9.");
            return;
        }
        int max = this.maxPlayers();
        Game best = this.games.values().stream().filter(g -> (g.state == State.WAITING || g.state == State.STARTING) && g.players.size() < max).max(Comparator.<Game>comparingInt(g -> g.players.size()).thenComparing(g -> -g.n)).orElse(null);
        if (best == null && this.games.size() < this.maxInstances()) {
            int n = 2;
            while (this.games.containsKey(n)) {
                ++n;
            }
            best = new Game(n);
            this.games.put(n, best);
            this.getLogger().info("Nouvelle partie de Murder : " + n + ".");
        }
        if (best == null) {
            best = this.games.values().stream().filter(g -> g.players.size() < max).min(Comparator.comparingInt(g -> g.players.size())).orElse(null);
        }
        if (best == null) {
            p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cToutes les parties sont pleines.");
            return;
        }
        best.join(p);
    }

    private void removeGame(Game g) {
        if (g.n == 1) {
            return;
        }
        g.releaseWorld();
        this.games.remove(g.n);
        this.getLogger().info("Partie de Murder " + g.n + " ferm\u00e9e.");
        this.reportStatus();
    }

    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        String name = w.getName();
        if (name.equals(this.getConfig().getString("hub-world", "lobby"))) {
            return true;
        }
        if (this.lobby != null && w.equals((Object)this.lobby.getWorld())) {
            return true;
        }
        for (String b : this.mapWorlds()) {
            if (WorldClone.index(b, name) <= 0 && !name.equals(EemiMurder.modelName(b))) continue;
            return true;
        }
        return false;
    }

    private void sendToHub(Player p) {
        if (!this.mayTouch(p)) {
            return;
        }
        this.resetPlayer(p, GameMode.SURVIVAL);
        World hub = Bukkit.getWorld((String)this.getConfig().getString("hub-world", "lobby"));
        if (hub != null) {
            p.teleport(hub.getSpawnLocation());
        }
    }

    private void resetPlayer(Player p, GameMode mode) {
        if (!this.mayTouch(p)) {
            return;
        }
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20.0 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20.0f);
        p.setLevel(0);
        p.setExp(0.0f);
        p.setFireTicks(0);
        p.setGlowing(false);
    }

    private void giveBow(Player p, boolean detectiveBow) {
        p.getInventory().setItem(1, this.tool(Material.BOW, detectiveBow ? "Arc du d\u00e9tective" : "Arc", NamedTextColor.AQUA));
        p.getInventory().setItem(9, new ItemStack(Material.ARROW));
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDamageBy(EntityDamageByEntityEvent e) {
        Arrow arrow;
        Entity entity = e.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player)entity;
        Game g = this.byPlayer.get(victim.getUniqueId());
        if (g == null) {
            return;
        }
        e.setCancelled(true);
        if (g.state != State.PLAYING || !g.inGame.contains(victim.getUniqueId()) || !g.alive.contains(victim.getUniqueId())) {
            return;
        }
        Entity entity2 = e.getDamager();
        if (entity2 instanceof Player) {
            Player attacker = (Player)entity2;
            if (attacker.getUniqueId().equals(g.murderer) && g.alive.contains(g.murderer) && attacker.getInventory().getItemInMainHand().getType() == Material.IRON_SWORD) {
                long left = g.knifeCooldownLeft();
                if (left > 0L) {
                    attacker.sendActionBar((Component)Component.text((String)("Couteau pr\u00eat dans " + EemiMurder.formatSeconds(left)), (TextColor)NamedTextColor.RED));
                    return;
                }
                g.lastKnifeKill = System.currentTimeMillis();
                int ticks = (int)Math.round((double)this.knifeCooldownMs() / 50.0);
                if (ticks > 0) {
                    attacker.setCooldown(Material.IRON_SWORD, ticks);
                }
                g.eliminate(victim, attacker, true);
                g.checkWin();
            }
            return;
        }
        Entity entity3 = e.getDamager();
        if (entity3 instanceof Arrow && (arrow = (Arrow)entity3).getShooter() instanceof Player) {
            Player shooter = (Player)arrow.getShooter();
            arrow.remove();
            if (!g.alive.contains(shooter.getUniqueId()) || shooter.equals((Object)victim)) {
                return;
            }
            g.eliminate(victim, shooter, true);
            if (!victim.getUniqueId().equals(g.murderer) && !shooter.getUniqueId().equals(g.murderer)) {
                shooter.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cTu as tu\u00e9 un innocent !");
                g.eliminate(shooter, null, true);
            }
            g.checkWin();
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        Player p;
        if (e instanceof EntityDamageByEntityEvent) {
            return;
        }
        Entity entity = e.getEntity();
        if (entity instanceof Player && this.byPlayer.containsKey((p = (Player)entity).getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        LivingEntity livingEntity = e.getEntity();
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player p = (Player)livingEntity;
        Game g = this.byPlayer.get(p.getUniqueId());
        if (g == null || !g.inGame.contains(p.getUniqueId())) {
            return;
        }
        g.lastShot.put(p.getUniqueId(), System.currentTimeMillis());
        Entity entity = e.getProjectile();
        if (entity instanceof AbstractArrow) {
            AbstractArrow arrow = (AbstractArrow)entity;
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        if ((entity = e.getProjectile()) instanceof Projectile) {
            Projectile proj = (Projectile)entity;
            g.spawned.add((Entity)proj);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Arrow arrow;
        block4: {
            block3: {
                Projectile projectile = e.getEntity();
                if (!(projectile instanceof Arrow)) break block3;
                arrow = (Arrow)projectile;
                if (e.getHitBlock() != null) break block4;
            }
            return;
        }
        for (Game g : this.games.values()) {
            if (!g.spawned.contains(arrow)) continue;
            arrow.remove();
            return;
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        LivingEntity livingEntity = e.getEntity();
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player p = (Player)livingEntity;
        Game g = this.byPlayer.get(p.getUniqueId());
        if (g == null || !g.inGame.contains(p.getUniqueId())) {
            return;
        }
        Item item = e.getItem();
        e.setCancelled(true);
        if (g.state != State.PLAYING || !g.alive.contains(p.getUniqueId()) || !g.spawned.contains(item)) {
            return;
        }
        Material type = item.getItemStack().getType();
        boolean isMurderer = p.getUniqueId().equals(g.murderer);
        if (type == Material.GOLD_INGOT) {
            item.remove();
            g.spawned.remove(item);
            int count = g.gold.merge(p.getUniqueId(), 1, Integer::sum);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            int needed = this.cfg("gold-for-bow", 10);
            if (count >= needed && !isMurderer) {
                g.gold.put(p.getUniqueId(), count - needed);
                if (!p.getInventory().contains(Material.BOW)) {
                    this.giveBow(p, false);
                    p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a76" + needed + " pi\u00e8ces d'or : tu obtiens un arc !");
                } else {
                    p.getInventory().setItem(9, new ItemStack(Material.ARROW));
                    p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a76" + needed + " pi\u00e8ces d'or : une fl\u00e8che de plus !");
                }
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
            int shown = g.gold.get(p.getUniqueId());
            p.getInventory().setItem(8, shown > 0 ? new ItemStack(Material.GOLD_INGOT, shown) : null);
        } else if (type == Material.BOW && !isMurderer && !p.getInventory().contains(Material.BOW)) {
            item.remove();
            g.spawned.remove(item);
            this.giveBow(p, true);
            g.broadcast("\u00a7bQuelqu'un a ramass\u00e9 l'arc du d\u00e9tective !");
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = this.byPlayer.get(p.getUniqueId());
        if (g == null || g.state != State.PLAYING || g.current == null) {
            return;
        }
        if (!g.inGame.contains(p.getUniqueId())) {
            return;
        }
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY() && to.getBlockZ() == e.getFrom().getBlockZ()) {
            return;
        }
        if (g.alive.contains(p.getUniqueId()) && WorldClone.index(Paquebot.WORLD, to.getWorld().getName()) > 0
                && Paquebot.overboard(to.getBlockX(), to.getBlockZ())) {
            // paquebot : pieds hors de la coque (par-dessus bord, sur une preceinte) = tombe a la mer
            p.sendMessage("§8[§dMurder§8] §7§cTu es tombé à la mer !");
            g.eliminate(p, null, true);
            g.checkWin();
            return;
        }
        if (g.alive.contains(p.getUniqueId()) && g.current.isBelowFloor(to)) {
            p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cTu es tomb\u00e9 dans un pi\u00e8ge !");
            g.eliminate(p, null, true);
            g.checkWin();
            return;
        }
        if (g.current.contains(to)) {
            return;
        }
        if (g.current.contains(e.getFrom())) {
            e.setTo(e.getFrom());
        } else {
            p.teleport(g.current.randomSpawn());
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Game g = this.byPlayer.get(p.getUniqueId());
        if (g == null || g.state != State.PLAYING || g.current == null) {
            return;
        }
        if (!g.inGame.contains(p.getUniqueId()) || e.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        if (!g.current.contains(e.getTo())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOW)
    public void onLobbyBlockUse(PlayerInteractEvent e) {
        if (this.lobby == null || this.lobby.getWorld() == null) {
            return;
        }
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null || !b.getWorld().equals((Object)this.lobby.getWorld())) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) {
            return;
        }
        if (!EemiMurder.isPassageBlock(b.getType())) {
            return;
        }
        Location loc = b.getLocation();
        for (GameMap m : this.maps.values()) {
            if (!m.contains(loc)) continue;
            return;
        }
        e.setUseInteractedBlock(Event.Result.DENY);
    }

    private static boolean isPassageBlock(Material t) {
        return Tag.DOORS.isTagged(t) || Tag.TRAPDOORS.isTagged(t) || Tag.FENCE_GATES.isTagged(t) || Tag.BUTTONS.isTagged(t) || Tag.PRESSURE_PLATES.isTagged(t) || t == Material.LEVER;
    }

    /**
     * Blocs qu'un joueur peut actionner dans une map construite. Fete, Musee (1.8.0) et Paquebot (1.9.0) : portes et
     * portillons seulement (leurs trappes sont du decor : cadres, lustres, chaises longues, nid de pie). Les portes
     * en fer (cabines fermees du paquebot) restent closes. Bibliotheque : comme en 1.7.
     */
    private boolean isBuiltPassage(String worldName, Material t) {
        boolean newMap = WorldClone.index(Fete.WORLD, worldName) > 0 || WorldClone.index(Musee.WORLD, worldName) > 0
                || WorldClone.index(Paquebot.WORLD, worldName) > 0 || WorldClone.index(Hotel.WORLD, worldName) > 0
                || WorldClone.index(Chalet.WORLD, worldName) > 0;
        if (newMap) return Tag.DOORS.isTagged(t) || Tag.FENCE_GATES.isTagged(t);
        return EemiMurder.isPassageBlock(t);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = this.byPlayer.get(p.getUniqueId());
        if (g == null || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack it = e.getItem();
        Block clicked = e.getClickedBlock();
        if (clicked != null && e.getAction() == Action.RIGHT_CLICK_BLOCK && this.isBuiltWorld(clicked.getWorld().getName()) && p.getGameMode() != GameMode.CREATIVE && !this.isBuiltPassage(clicked.getWorld().getName(), clicked.getType())) {
            // maps construites : pots, pupitres, tonneaux, blocs musicaux, coffres... purement decoratifs
            e.setUseInteractedBlock(Event.Result.DENY);
        }
        if (this.isReadyItem(it)) {
            e.setCancelled(true);
            g.toggleReady(p);
            return;
        }
        if (it != null && it.getType() == Material.RED_BED && this.isTool(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77Tu as quitt\u00e9 le Murder.");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (this.byPlayer.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (this.byPlayer.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player && this.byPlayer.containsKey((p = (Player)humanEntity).getUniqueId()) && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        Player p;
        HumanEntity humanEntity = e.getEntity();
        if (humanEntity instanceof Player && this.byPlayer.containsKey((p = (Player)humanEntity).getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = this.byPlayer.get(p.getUniqueId());
        if (g != null && !g.isOwnWorld(p.getWorld())) {
            g.leave(p, false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game g = this.byPlayer.get(p.getUniqueId());
        if (g != null) {
            g.leave(p, true);
        }
    }

    @EventHandler
    public void onSpectatorWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = this.bySpectator.get(p.getUniqueId());
        if (g == null) {
            return;
        }
        if (g.current != null && p.getWorld().getName().equals(g.current.worldName)) {
            return;
        }
        this.bySpectator.remove(p.getUniqueId());
        g.spectators.remove(p.getUniqueId());
        if (this.mayTouch(p)) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onSpectatorQuit(PlayerQuitEvent e) {
        Game g = this.bySpectator.remove(e.getPlayer().getUniqueId());
        if (g != null) {
            g.spectators.remove(e.getPlayer().getUniqueId());
            this.sendToHub(e.getPlayer());
        }
    }

    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore")) {
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)("eemi " + args));
        }
    }

    private void spectate(Player p, String targetName) {
        Game g;
        if (this.byPlayer.containsKey(p.getUniqueId())) {
            p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cTu es inscrit : /murder leave d'abord.");
            return;
        }
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact((String)targetName);
            Game game = g = t == null ? null : this.byPlayer.get(t.getUniqueId());
            if (g == null) {
                p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7c" + targetName + " n'est pas dans une partie de Murder.");
                return;
            }
        } else {
            g = this.games.values().stream().filter(x -> x.state == State.PLAYING).max(Comparator.comparingInt(x -> x.alive.size())).orElse(null);
        }
        if (g == null || g.state != State.PLAYING || g.current == null || g.current.world() == null) {
            p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cAucune partie en cours.");
            return;
        }
        g.spectate(p);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player pl;
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        Player p = sender instanceof Player ? (pl = (Player)sender) : null;
        boolean admin = sender.hasPermission("eemimurder.admin");
        switch (sub) {
            case "help": 
            case "aide": 
            case "?": {
                this.sendHelp(sender, admin);
                break;
            }
            case "join": {
                Player target;
                Player player = target = args.length > 1 && admin ? Bukkit.getPlayerExact((String)args[1]) : p;
                if (target == null) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cJoueur introuvable.");
                    return true;
                }
                this.join(target);
                break;
            }
            case "leave": {
                if (p == null) {
                    return true;
                }
                Game g = this.byPlayer.get(p.getUniqueId());
                if (g == null) {
                    p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77Tu n'es pas inscrit.");
                    return true;
                }
                g.leave(p, true);
                p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77Tu as quitt\u00e9 le Murder.");
                break;
            }
            case "spectate": 
            case "spec": {
                if (p == null) break;
                this.spectate(p, args.length > 1 ? args[1] : null);
                break;
            }
            case "maps": {
                for (GameMap m : this.maps.values()) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7f" + m.id + " \u00a77(" + m.name + (m.small ? ", petite map" : "") + ") : " + m.spawns.size() + " apparitions, " + m.gold.size() + " points d'or, " + (this.builtBusy(m.worldName) ? "\u00a76en construction" : (m.isReady() ? "\u00a7apr\u00eate" : "\u00a7cincompl\u00e8te")) + (Bukkit.getWorld((String)EemiMurder.modelName(m.worldName)) != null ? " \u00a77(mod\u00e8le ok)" : " \u00a77(pas de mod\u00e8le)"));
                }
                break;
            }
            default: {
                if (!admin) {
                    this.sendHelp(sender, false);
                    return true;
                }
                this.adminCommand(sender, p, sub, args);
            }
        }
        return true;
    }

    private void sendHelp(CommandSender s, boolean admin) {
        s.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7fCommandes du Murder :");
        s.sendMessage("\u00a7e/murder join \u00a77: rejoindre une partie");
        s.sendMessage("\u00a7e/murder leave \u00a77: quitter la partie");
        s.sendMessage("\u00a7e/murder maps \u00a77: liste des maps");
        s.sendMessage("\u00a7e/murder spectate [joueur] \u00a77: regarder une partie");
        if (!admin) {
            return;
        }
        s.sendMessage("\u00a7c/murder start [n] \u00a77| \u00a7cstop [n] \u00a77| \u00a7cparties \u00a77| \u00a7cmodeles \u00a77| \u00a7creload \u00a77| \u00a7cbuildmap <" + MapDef.ids() + ">");
        s.sendMessage("\u00a7c/murder setlobby \u00a77| \u00a7cbuildlobby \u00a77| \u00a7ccreatemap \u00a77| \u00a7csetpos \u00a77| \u00a7caddspawn \u00a77| \u00a7caddgold");
    }

    private Game targetGame(CommandSender sender, Player p, String[] args) {
        if (args.length > 1) {
            try {
                Game g = this.games.get(Integer.parseInt(args[1]));
                if (g == null) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cPas de partie " + args[1] + ".");
                }
                return g;
            }
            catch (NumberFormatException e) {
                sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cNum\u00e9ro de partie invalide.");
                return null;
            }
        }
        if (p != null && this.byPlayer.containsKey(p.getUniqueId())) {
            return this.byPlayer.get(p.getUniqueId());
        }
        return this.games.get(1);
    }

    private boolean anyRunning() {
        return this.games.values().stream().anyMatch(g -> g.state == State.PLAYING || g.state == State.ENDING);
    }

    private void adminCommand(CommandSender sender, Player p, String sub, String[] args) {
        switch (sub) {
            case "start": {
                Game g = this.targetGame(sender, p, args);
                if (g == null) {
                    return;
                }
                if (g.state != State.WAITING && g.state != State.STARTING) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cUne partie est d\u00e9j\u00e0 en cours.");
                    return;
                }
                if (g.players.size() < 2) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cIl faut au moins 2 joueurs inscrits.");
                    return;
                }
                g.startGame();
                break;
            }
            case "stop": {
                Game g = this.targetGame(sender, p, args);
                if (g == null) {
                    return;
                }
                g.forceStop();
                sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77" + g.label() + " arr\u00eat\u00e9e.");
                break;
            }
            case "parties": 
            case "games": {
                sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7e" + this.games.size() + "/" + this.maxInstances() + " partie(s) :");
                for (Game g : this.games.values()) {
                    sender.sendMessage(PREFIX + g.describe());
                }
                break;
            }
            case "modeles": {
                for (String base : this.mapWorlds()) {
                    boolean ok = this.refreshModel(base);
                    sender.sendMessage(PREFIX + (ok ? "\u00a7aMod\u00e8le " + EemiMurder.modelName(base) + " en cours de copie." : "\u00a7cMod\u00e8le " + EemiMurder.modelName(base) + " non refait (monde absent, copie en cours ou partie sur la map)."));
                }
                break;
            }
            case "reload": {
                if (this.anyRunning()) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cAttends la fin des parties.");
                    return;
                }
                this.loadSettings();
                this.enableBuiltMaps();
                this.ensureModels();
                sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7aConfig recharg\u00e9e (" + this.maps.size() + " maps).");
                break;
            }
            case "buildmap": {
                MapDef bd = args.length < 2 ? null : MapDef.byId(args[1]);
                if (bd == null) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77/murder buildmap <" + MapDef.ids() + "> \u00a78(maps construites par le plugin)");
                    return;
                }
                if (!this.maps.containsKey(bd.id)) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cLa map " + bd.id + " est retir\u00e9e du config.");
                    return;
                }
                String why = this.builtBlocked(bd.world);
                if (why != null) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7c" + why + " : attends la fin (ou /murder stop).");
                    return;
                }
                String err = this.builtFor(bd.world).build(() -> {
                    boolean ok = this.refreshModel(bd.world);
                    if (ok) {
                        sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7aMap " + bd.name + " reconstruite, mod\u00e8le en cours de copie.");
                    }
                    return ok;
                });
                sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77" + (err == null ? "\u00a7aConstruction de la map " + bd.name + " lanc\u00e9e (quelques secondes)." : "\u00a7c" + err + "."));
                break;
            }
            case "buildlobby": {
                if (this.lobby == null || this.lobby.getWorld() == null) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cLobby non d\u00e9fini.");
                    return;
                }
                Game g1 = this.games.get(1);
                if (g1.state == State.PLAYING || g1.state == State.ENDING) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cLa partie 1 est en cours.");
                    return;
                }
                this.buildLobby(this.lobby);
                sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7aSalon d'attente construit.");
                String lw = this.lobby.getWorld().getName();
                if (!this.mapWorlds().contains(lw)) break;
                this.refreshModel(lw);
                break;
            }
            case "setlobby": {
                if (p == null) {
                    return;
                }
                this.getConfig().set("lobby", (Object)EemiMurder.formatLocation(p.getLocation(), true));
                this.saveConfig();
                this.lobby = p.getLocation();
                p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7aLobby d'attente d\u00e9fini ici.");
                break;
            }
            case "createmap": {
                if (p == null || args.length < 3) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77/murder createmap <id> <nom>");
                    return;
                }
                String path = "maps." + args[1];
                this.getConfig().set(path + ".name", (Object)String.join((CharSequence)" ", List.of(args).subList(2, args.length)));
                this.getConfig().set(path + ".world", (Object)p.getWorld().getName());
                this.saveAndReloadMaps();
                p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7aMap " + args[1] + " cr\u00e9\u00e9e. D\u00e9finis /murder setpos " + args[1] + " 1 et 2, puis addspawn / addgold.");
                break;
            }
            case "setpos": {
                if (p == null || args.length < 3 || !this.maps.containsKey(args[1])) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77/murder setpos <map> <1|2>");
                    return;
                }
                this.getConfig().set("maps." + args[1] + "." + ("1".equals(args[2]) ? "min" : "max"), (Object)EemiMurder.formatLocation(p.getLocation(), false));
                this.saveAndReloadMaps();
                p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7aCoin " + args[2] + " de " + args[1] + " d\u00e9fini.");
                break;
            }
            case "addspawn": 
            case "addgold": {
                if (p == null || args.length < 2 || !this.maps.containsKey(args[1])) {
                    sender.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77/murder " + sub + " <map>");
                    return;
                }
                String key = "maps." + args[1] + "." + (sub.equals("addspawn") ? "spawns" : "gold");
                ArrayList<String> list = new ArrayList<String>(this.getConfig().getStringList(key));
                list.add(EemiMurder.formatLocation(p.getLocation(), false));
                this.getConfig().set(key, list);
                this.saveAndReloadMaps();
                p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7aPoint ajout\u00e9 (" + list.size() + ").");
                break;
            }
            default: {
                this.sendHelp(sender, true);
            }
        }
    }

    private void buildLobby(Location center) {
        int z;
        World w = center.getWorld();
        int cx = center.getBlockX();
        int fy = center.getBlockY() - 1;
        int cz = center.getBlockZ();
        int h = 8;
        int top = 6;
        BiConsumer<int[], String> set = (p, data) -> {
            try {
                w.getBlockAt(cx + p[0], fy + p[1], cz + p[2]).setBlockData(Bukkit.createBlockData((String)data), false);
            }
            catch (IllegalArgumentException e) {
                this.getLogger().warning("Bloc inconnu pour le salon : " + data);
            }
        };
        int var9_10 = -h - 4;
        while (var9_10 <= h + 4) {
            for (z = -h - 4; z <= h + 4; ++z) {
                int y = 0;
                while (y <= top + 6) {
                    set.accept(new int[]{var9_10, y++, z}, "minecraft:air");
                }
            }
            ++var9_10;
        }
        int var9_12 = -h - 1;
        while (var9_12 <= h + 1) {
            for (z = -h - 1; z <= h + 1; ++z) {
                boolean wall = Math.abs((int)var9_12) == h + 1 || Math.abs(z) == h + 1;
                set.accept(new int[]{var9_12, -1, z}, "minecraft:stone_bricks");
                set.accept(new int[]{var9_12, 0, z}, wall ? "minecraft:polished_deepslate" : ((var9_12 + z & 1) == 0 ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks"));
                set.accept(new int[]{var9_12, top + 1, z}, Math.abs((int)var9_12) % 4 == 0 && Math.abs(z) % 4 == 0 && !wall ? "minecraft:shroomlight" : "minecraft:dark_oak_planks");
                if (!wall) continue;
                int along = Math.abs((int)var9_12) == h + 1 ? z : var9_12;
                boolean beam = Math.abs(along) % 4 == 0 || Math.abs(along) == h + 1;
                int y = 1;
                while (y <= top) {
                    String block = beam ? "minecraft:stripped_dark_oak_log" : (y == 1 ? "minecraft:dark_oak_planks" : (y == top ? "minecraft:dark_oak_planks" : "minecraft:bookshelf"));
                    set.accept(new int[]{var9_12, y++, z}, block);
                }
            }
            ++var9_12;
        }
        int var9_14 = -h;
        while (var9_14 <= h) {
            set.accept(new int[]{var9_14, 1, 0}, "minecraft:red_carpet");
            set.accept(new int[]{0, 1, var9_14}, "minecraft:red_carpet");
            if (Math.abs((int)var9_14) > 1) {
                set.accept(new int[]{var9_14, 1, 1}, "minecraft:black_carpet");
                set.accept(new int[]{var9_14, 1, -1}, "minecraft:black_carpet");
                set.accept(new int[]{1, 1, var9_14}, "minecraft:black_carpet");
                set.accept(new int[]{-1, 1, var9_14}, "minecraft:black_carpet");
            }
            ++var9_14;
        }
        int[][] nArrayArray = new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        z = nArrayArray.length;
        for (int i = 0; i < z; ++i) {
            int[] c = nArrayArray[i];
            set.accept(new int[]{c[0], top, c[1]}, "minecraft:iron_chain");
            set.accept(new int[]{c[0], top - 1, c[1]}, "minecraft:lantern[hanging=true]");
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                set.accept(new int[]{c[0] + d[0], top, c[1] + d[1]}, "minecraft:lantern[hanging=true]");
            }
        }
        int var9_17 = -2;
        while (var9_17 <= 2) {
            for (int y = 1; y <= top; ++y) {
                set.accept(new int[]{var9_17, y, -h}, Math.abs((int)var9_17) == 2 || y >= 4 ? "minecraft:bricks" : "minecraft:air");
            }
            ++var9_17;
        }
        set.accept(new int[]{0, 1, -h}, "minecraft:campfire[lit=true]");
        set.accept(new int[]{-1, 1, -h}, "minecraft:campfire[lit=true]");
        set.accept(new int[]{1, 1, -h}, "minecraft:campfire[lit=true]");
        set.accept(new int[]{-2, 4, -h + 1}, "minecraft:dark_oak_trapdoor[facing=south,half=top,open=false]");
        set.accept(new int[]{2, 4, -h + 1}, "minecraft:dark_oak_trapdoor[facing=south,half=top,open=false]");
        for (int s : new int[]{-5, 5}) {
            set.accept(new int[]{s, 1, -h}, "minecraft:dark_oak_log");
            set.accept(new int[]{s, 2, -h}, "minecraft:dark_oak_log");
            set.accept(new int[]{s, 3, -h}, "minecraft:spruce_planks");
            set.accept(new int[]{s, 3, -h + 1}, "minecraft:spruce_button[face=wall,facing=south]");
        }
        for (int s : new int[]{-1, 1}) {
            for (int z2 : new int[]{-5, 5}) {
                set.accept(new int[]{s * 5, 1, z2}, "minecraft:dark_oak_fence");
                set.accept(new int[]{s * 5, 2, z2}, "minecraft:red_carpet");
                set.accept(new int[]{s * 6, 1, z2}, "minecraft:spruce_stairs[facing=" + (s < 0 ? "west" : "east") + "]");
                set.accept(new int[]{s * 4, 1, z2}, "minecraft:spruce_stairs[facing=" + (s < 0 ? "east" : "west") + "]");
            }
        }
        for (int[] c : new int[][]{{-h, -h}, {h, -h}, {-h, h}, {h, h}}) {
            set.accept(new int[]{c[0], 1, c[1]}, "minecraft:potted_fern");
            set.accept(new int[]{c[0], 2, c[1]}, "minecraft:air");
        }
        set.accept(new int[]{-5, 3, -5}, "minecraft:candle[candles=3,lit=true]");
        set.accept(new int[]{5, 3, 5}, "minecraft:candle[candles=3,lit=true]");
        set.accept(new int[]{0, 1, h + 1}, "minecraft:dark_oak_door[facing=north,half=lower]");
        set.accept(new int[]{0, 2, h + 1}, "minecraft:dark_oak_door[facing=north,half=upper]");
        set.accept(new int[]{0, 0, h + 1}, "minecraft:polished_deepslate");
    }

    private void saveAndReloadMaps() {
        this.saveConfig();
        if (!this.anyRunning()) {
            this.loadSettings();
            this.ensureModels();
        }
    }

    private ItemStack tool(Material type, String name, NamedTextColor color) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text((String)name, (TextColor)color).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(this.toolKey, PersistentDataType.BYTE, (byte)1);
        it.setItemMeta(meta);
        return it;
    }

    /** Objet « Pret » (1.14.0, #134) : gris = pas pret, vert = pret ; marque aussi comme outil du Murder. */
    private ItemStack readyItem(boolean ready) {
        ItemStack it = this.tool(ready ? Material.LIME_DYE : Material.GRAY_DYE, ready ? "Pr\u00eat ! (clic droit pour annuler)" : "Pr\u00eat ? (clic droit)", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY);
        ItemMeta meta = it.getItemMeta();
        meta.lore(List.of(Component.text((String)(ready ? "Tu attends les autres joueurs." : "Quand tout le monde est pr\u00eat,"), (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), Component.text((String)(ready ? "Clic droit pour annuler." : "le d\u00e9part est avanc\u00e9."), (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(this.readyKey, PersistentDataType.BYTE, (byte)1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isReadyItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(this.readyKey, PersistentDataType.BYTE);
    }

    private boolean isTool(ItemStack it) {
        return it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(this.toolKey, PersistentDataType.BYTE);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTaskLater((Plugin)this, r, 3L);
    }

    private List<Player> online(Set<UUID> ids) {
        ArrayList<Player> list = new ArrayList<Player>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer((UUID)id);
            if (p == null) continue;
            list.add(p);
        }
        return list;
    }

    private String name(UUID id) {
        if (id == null) {
            return "?";
        }
        String n = Bukkit.getOfflinePlayer((UUID)id).getName();
        return n == null ? "?" : n;
    }

    private static String formatTime(int s) {
        return String.format("%d:%02d", Math.max(0, s) / 60, Math.max(0, s) % 60);
    }

    private static String formatLocation(Location l, boolean full) {
        if (full) {
            return String.format(Locale.ROOT, "%s,%.1f,%.1f,%.1f,%.0f,%.0f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), Float.valueOf(l.getYaw()), Float.valueOf(l.getPitch()));
        }
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static Location parseLocation(String s) {
        String[] p = s.split(",");
        if (p.length < 4) {
            return null;
        }
        World w = Bukkit.getWorld((String)p[0].trim());
        if (w == null) {
            return null;
        }
        Location loc = new Location(w, Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim()));
        if (p.length >= 6) {
            loc.setYaw(Float.parseFloat(p[4].trim()));
            loc.setPitch(Float.parseFloat(p[5].trim()));
        }
        return loc;
    }

    final class Game {
        final int n;
        final String teamName;
        final Set<UUID> players = new LinkedHashSet<UUID>();
        final Set<UUID> inGame = new LinkedHashSet<UUID>();
        final Set<UUID> alive = new HashSet<UUID>();
        final Map<UUID, Role> roles = new HashMap<UUID, Role>();
        final Map<UUID, Integer> gold = new HashMap<UUID, Integer>();
        final Map<UUID, Long> lastShot = new HashMap<UUID, Long>();
        long lastKnifeKill;
        final Set<Entity> spawned = new HashSet<Entity>();
        final Set<Scoreboard> boards = new HashSet<Scoreboard>();
        final Map<UUID, Scoreboard> sidebars = new HashMap<UUID, Scoreboard>();
        final Set<UUID> spectators = new HashSet<UUID>();
        /** 1.14.0 (#134) : inscrits qui ont clique sur « Pret » pendant l'attente de cette partie. */
        final Set<UUID> ready = new HashSet<UUID>();
        State state = State.WAITING;
        GameMap chosen;
        World world;
        boolean preparing;
        int prepToken;
        boolean removed;
        boolean forced;
        long nextPrepare;
        GameMap current;
        String lastMapId;
        /** 1.10.0 : derniere map jouee avant le tirage en cours (remise si le tirage est refait) ; taille voulue au tirage. */
        String lastMapIdBefore;
        /** 1.12.0 : derniere petite map avant le tirage en cours (remise si le tirage est refait ou rate). */
        String lastSmallBefore;
        /** 1.10.0 : heure du dernier nouveau tirage pour changement de taille (au plus un toutes les 20 s). */
        long lastResize;
        int timer;
        int elapsed;
        UUID murderer;
        UUID detective;
        BossBar bar;

        Game(int n) {
            this.n = n;
            this.teamName = n == 1 ? EemiMurder.TEAM : "eemimurder_" + n;
            // 1.14.1 : jamais la derniere map jouee (meme apres un redemarrage)
            this.lastMapId = EemiMurder.this.lastPlayedMapId;
        }

        String label() {
            return "Partie " + this.n;
        }

        long knifeCooldownLeft() {
            if (this.lastKnifeKill == 0L) {
                return 0L;
            }
            return Math.max(0L, this.lastKnifeKill + EemiMurder.this.knifeCooldownMs() - System.currentTimeMillis());
        }

        boolean isOwnWorld(World w) {
            if (w == null) {
                return false;
            }
            if (EemiMurder.this.lobby != null && w.equals((Object)EemiMurder.this.lobby.getWorld())) {
                return true;
            }
            return this.world != null && w.equals((Object)this.world);
        }

        void tick() {
            for (Player p : EemiMurder.this.online(this.players)) {
                this.updateSidebar(p);
            }
            switch (this.state.ordinal()) {
                case 0: {
                    if (this.players.size() >= EemiMurder.this.cfg("min-players", 3)) {
                        this.state = State.STARTING;
                        this.timer = EemiMurder.this.cfg("countdown", 30);
                        this.broadcast("\u00a7eAssez de joueurs : la partie commence dans \u00a76" + this.timer + " s\u00a7e.");
                        if (this.chosen == null) {
                            this.prepareMap();
                        }
                        EemiMurder.this.reportStatus();
                    }
                    for (Player p : EemiMurder.this.online(this.players)) {
                        p.sendActionBar((Component)Component.text((String)("En attente de joueurs (" + this.players.size() + "/" + EemiMurder.this.cfg("min-players", 3) + ")"), (TextColor)NamedTextColor.YELLOW));
                    }
                    break;
                }
                case 1: {
                    if (this.players.size() < (this.forced ? 2 : EemiMurder.this.cfg("min-players", 3))) {
                        this.state = State.WAITING;
                        this.forced = false;
                        this.broadcast("\u00a7cPas assez de joueurs, lancement annul\u00e9.");
                        EemiMurder.this.reportStatus();
                        return;
                    }
                    if (this.players.size() >= EemiMurder.this.maxPlayers()) {
                        this.timer = Math.min(this.timer, EemiMurder.this.cfg("full-countdown", 10));
                    }
                    this.checkReady();
                    this.resizeMapIfNeeded(false);
                    if (this.timer <= 0 && this.world == null) {
                        if (this.chosen == null && !this.preparing && System.currentTimeMillis() >= this.nextPrepare) {
                            this.prepareMap();
                        }
                        for (Player p : EemiMurder.this.online(this.players)) {
                            p.sendActionBar((Component)Component.text((String)"Pr\u00e9paration de la map...", (TextColor)NamedTextColor.GOLD));
                        }
                        return;
                    }
                    for (Player p : EemiMurder.this.online(this.players)) {
                        if (EemiMurder.this.mayTouch(p)) {
                            p.setLevel(this.timer);
                        }
                        p.sendActionBar((Component)Component.text((String)("D\u00e9but dans " + this.timer + " s"), (TextColor)NamedTextColor.GOLD));
                    }
                    if (this.timer <= 5 && this.timer > 0) {
                        for (Player p : EemiMurder.this.online(this.players)) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        }
                    }
                    if (this.timer <= 0) {
                        this.startGame();
                        break;
                    }
                    --this.timer;
                    break;
                }
                case 2: {
                    this.playingTick();
                    break;
                }
                case 3: {
                    if (--this.timer > 0) break;
                    this.backToLobby();
                }
            }
        }

        void playingTick() {
            Player m;
            ++this.elapsed;
            --this.timer;
            int total = EemiMurder.this.gameTime(this.current);
            if (this.bar != null) {
                this.bar.progress(Math.max(0.0f, Math.min(1.0f, (float)this.timer / (float)total)));
                this.bar.name((Component)Component.text((String)(this.current.name + "  -  " + EemiMurder.formatTime(this.timer)), (TextColor)NamedTextColor.WHITE));
            }
            if (this.elapsed == EemiMurder.this.cfg("sword-delay", 10) && (m = Bukkit.getPlayer((UUID)this.murderer)) != null && this.alive.contains(this.murderer)) {
                m.getInventory().setItem(1, EemiMurder.this.tool(Material.IRON_SWORD, "Couteau du meurtrier", NamedTextColor.RED));
                m.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a7cTu as ton couteau. \u00c9limine tout le monde !");
                m.playSound(m.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0.8f);
            }
            if (this.elapsed % Math.max(1, EemiMurder.this.cfg("gold-interval", 3)) == 0) {
                this.spawnGold();
            }
            this.refillArrows();
            for (Player p : EemiMurder.this.online(this.alive)) {
                long left;
                Role r = this.roles.get(p.getUniqueId());
                Component ab = ((TextComponent)Component.text((String)r.label, (TextColor)r.color).decorate(TextDecoration.BOLD)).append((Component)Component.text((String)("   Or : " + String.valueOf(this.gold.getOrDefault(p.getUniqueId(), 0)) + "/" + EemiMurder.this.cfg("gold-for-bow", 10)), (TextColor)NamedTextColor.GOLD));
                long l = left = r == Role.MURDERER ? this.knifeCooldownLeft() : 0L;
                if (left > 0L) {
                    ab = ab.append((Component)Component.text((String)("   Couteau : " + EemiMurder.formatSeconds(left)), (TextColor)NamedTextColor.RED));
                }
                p.sendActionBar(ab);
            }
            if (this.timer <= 0) {
                this.endGame(false, "Le temps est \u00e9coul\u00e9 !");
            }
        }

        void prepareMap() {
            GameMap m2;
            ArrayList<GameMap> ready = new ArrayList<GameMap>();
            for (GameMap m3 : EemiMurder.this.maps.values()) {
                if (!m3.isReady() || EemiMurder.this.builtBusy(m3.worldName)) continue;
                ready.add(m3);
            }
            if (ready.isEmpty()) {
                this.broadcast("\u00a7cAucune map pr\u00eate.");
                return;
            }
            if (this.n != 1) {
                ArrayList<GameMap> withModel = new ArrayList<GameMap>(ready);
                withModel.removeIf(m -> Bukkit.getWorld((String)EemiMurder.modelName(m.worldName)) == null && !EemiMurder.this.modelBusy.contains(m.worldName));
                if (!withModel.isEmpty()) {
                    ready = withModel;
                }
            }
            // 1.10.0 : taille de map selon le nombre d'inscrits (repli sur toutes s'il n'y en a aucune de la bonne taille)
            boolean wantSmall = this.players.size() <= EemiMurder.this.smallMapMaxPlayers();
            ArrayList<GameMap> sized = new ArrayList<GameMap>(ready);
            sized.removeIf(m -> m.small != wantSmall);
            if (!sized.isEmpty()) {
                ready = sized;
            }
            if (ready.size() > 1) {
                ready.removeIf(m -> m.id.equals(this.lastMapId));
            }
            // 1.12.0 : petites maps en alternance (Grand Hotel, Chalet...), meme si une grande map a ete jouee entre deux
            String lastSmall = EemiMurder.this.lastSmallMapId;
            if (wantSmall && lastSmall != null && ready.size() > 1) {
                ready.removeIf(m -> m.small && m.id.equals(lastSmall));
            }
            this.chosen = m2 = (GameMap)ready.get(ThreadLocalRandom.current().nextInt(ready.size()));
            this.lastMapIdBefore = this.lastMapId;
            this.lastSmallBefore = lastSmall;
            this.lastMapId = m2.id;
            if (m2.small) {
                EemiMurder.this.lastSmallMapId = m2.id;
            }
            if (this.n == 1) {
                this.world = m2.world();
                return;
            }
            this.preparing = true;
            int token = ++this.prepToken;
            EemiMurder.this.requestCopy(m2.worldName, this.n, w -> {
                if (this.removed || token != this.prepToken) {
                    if (w != null && !this.isUsedByAnotherGame((World)w)) {
                        EemiMurder.this.destroyCopy((World)w, m2.worldName);
                    }
                    return;
                }
                this.preparing = false;
                if (w == null) {
                    this.chosen = null;
                    this.lastMapId = this.lastMapIdBefore;
                    if (m2.id.equals(EemiMurder.this.lastSmallMapId)) {
                        EemiMurder.this.lastSmallMapId = this.lastSmallBefore;
                    }
                    this.nextPrepare = System.currentTimeMillis() + 10000L;
                    this.broadcast("\u00a7cPr\u00e9paration de la map impossible, nouvel essai dans 10 s...");
                    return;
                }
                this.world = w;
            });
        }

        /**
         * 1.10.0 : la map est tiree au debut du compte a rebours ; si le nombre d'inscrits passe ensuite de l'autre
         * cote de small-map-max-players et qu'une map de la bonne taille est prete, le tirage est refait (copie
         * relachee pour une partie 2+, nouvelle copie demandee ; le compte a rebours attend la copie comme avant).
         */
        void resizeMapIfNeeded(boolean atStart) {
            if (this.chosen == null || this.preparing) {
                return;
            }
            long now = System.currentTimeMillis();
            // tirage fige dans les 10 dernieres secondes, et au plus un nouveau tirage toutes les 20 s (un joueur qui
            // entre et sort autour du seuil ne fait pas copier / detruire un monde a chaque fois) ; pas de limite
            // au lancement (/murder start)
            if (!atStart && (this.timer <= 10 || now - this.lastResize < 20000L)) {
                return;
            }
            boolean wantSmall = this.players.size() <= EemiMurder.this.smallMapMaxPlayers();
            if (this.chosen.small == wantSmall) {
                return;
            }
            boolean any = false;
            for (GameMap m3 : EemiMurder.this.maps.values()) {
                if (m3.small != wantSmall || !m3.isReady() || EemiMurder.this.builtBusy(m3.worldName)) continue;
                // partie 2+ : meme filtre que prepareMap (il faut le modele de la map pour la copier)
                if (this.n != 1 && Bukkit.getWorld((String)EemiMurder.modelName(m3.worldName)) == null && !EemiMurder.this.modelBusy.contains(m3.worldName)) continue;
                any = true;
            }
            if (!any) {
                return;
            }
            this.lastResize = now;
            String before = this.lastMapIdBefore;
            String smallBefore = this.lastSmallBefore;
            String chosenId = this.chosen.id;
            this.releaseWorld();
            this.lastMapId = before;
            // une autre partie a pu tirer une petite map entre-temps : on ne revient en arriere que si c'etait la notre
            if (chosenId.equals(EemiMurder.this.lastSmallMapId)) {
                EemiMurder.this.lastSmallMapId = smallBefore;
            }
            this.prepareMap();
            if (this.chosen != null) {
                this.broadcast("§7Le nombre de joueurs a changé : nouvelle map, §f" + this.chosen.name + "§7.");
            }
        }

        boolean isUsedByAnotherGame(World w) {
            for (Game g : EemiMurder.this.games.values()) {
                if (g == this || !w.equals((Object)g.world)) continue;
                return true;
            }
            return false;
        }

        void releaseWorld() {
            ++this.prepToken;
            this.preparing = false;
            if (this.n != 1 && this.world != null && this.chosen != null) {
                EemiMurder.this.destroyCopy(this.world, this.chosen.worldName);
            }
            this.world = null;
            this.chosen = null;
        }

        void join(Player p) {
            EemiMurder.this.bySpectator.remove(p.getUniqueId());
            for (Game g : EemiMurder.this.games.values()) {
                g.spectators.remove(p.getUniqueId());
            }
            this.players.add(p.getUniqueId());
            EemiMurder.this.byPlayer.put(p.getUniqueId(), this);
            p.teleport(EemiMurder.this.lobby);
            EemiMurder.this.later(() -> this.prepareLobbyPlayer(p));
            this.broadcast("\u00a7a" + p.getName() + " \u00a77a rejoint le Murder (\u00a7f" + this.players.size() + "\u00a77, partie " + this.n + ").");
            if (this.state == State.PLAYING || this.state == State.ENDING) {
                p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77Une partie est en cours, tu joueras \u00e0 la suivante.");
            }
            else if (this.n == 1 && this.state == State.WAITING && this.players.size() == 1 && this.chosen == null && !this.preparing) {
                // 1.14.1 : premier joueur dans une salle vide -> tirage de la map maintenant (refait si la taille change)
                this.prepareMap();
                if (this.chosen != null) {
                    this.broadcast("\u00a77Map tir\u00e9e : \u00a7f" + this.chosen.name + "\u00a77.");
                }
            }
            EemiMurder.this.reportStatus();
        }

        void prepareLobbyPlayer(Player p) {
            if (!(p.isOnline() && this.players.contains(p.getUniqueId()) && EemiMurder.this.mayTouch(p))) {
                return;
            }
            EemiMurder.this.resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, EemiMurder.this.tool(Material.RED_BED, "Quitter le Murder", NamedTextColor.RED));
            this.giveReady(p);
            this.updateSidebar(p);
        }

        // ---------------------------------------------------------------- objet « Pret » (1.14.0, #134)

        int readyCount() {
            int c = 0;
            for (UUID id : this.players) {
                if (!this.ready.contains(id)) continue;
                ++c;
            }
            return c;
        }

        void giveReady(Player p) {
            ItemStack it = EemiMurder.this.readyItem(this.ready.contains(p.getUniqueId()));
            for (int i = 0; i < p.getInventory().getSize(); ++i) {
                if (!EemiMurder.this.isReadyItem(p.getInventory().getItem(i))) continue;
                p.getInventory().setItem(i, it);
                return;
            }
            p.getInventory().setItem(EemiMurder.READY_SLOT, it);
        }

        void toggleReady(Player p) {
            UUID id = p.getUniqueId();
            if (!this.players.contains(id)) {
                return;
            }
            if (this.state != State.WAITING && this.state != State.STARTING) {
                p.sendMessage(EemiMurder.PREFIX + "\u00a77Une partie est en cours : attends la suivante.");
                return;
            }
            boolean now = !this.ready.remove(id);
            if (now) {
                this.ready.add(id);
            }
            this.giveReady(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, now ? 1.6f : 0.9f);
            this.broadcast("\u00a7f" + p.getName() + (now ? " \u00a7aest pr\u00eat" : " \u00a77n'est plus pr\u00eat") + " \u00a77(\u00a7f" + this.readyCount() + "\u00a77/\u00a7f" + this.players.size() + "\u00a77)");
            this.checkReady();
        }

        /** Compte a rebours lance et tous les inscrits prets : temps restant ramene a ready-skip-to (0 = desactive). */
        void checkReady() {
            int skip = Math.max(0, EemiMurder.this.cfg("ready-skip-to", 5));
            if (skip <= 0 || this.state != State.STARTING || this.players.isEmpty()) {
                return;
            }
            int min = this.forced ? 2 : EemiMurder.this.cfg("min-players", 3);
            if (this.players.size() < min || this.readyCount() < this.players.size()) {
                return;
            }
            if (this.timer > skip) {
                this.timer = skip;
                this.broadcast("\u00a7aTout le monde est pr\u00eat : d\u00e9part dans \u00a7e" + skip + " s\u00a7a.");
            }
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!this.players.remove(id)) {
                return;
            }
            this.ready.remove(id);
            EemiMurder.this.byPlayer.remove(id);
            if (this.state == State.PLAYING && this.alive.contains(id)) {
                this.broadcast("\u00a77" + p.getName() + " a quitt\u00e9 la partie.");
                this.eliminate(p, null, false);
            }
            this.inGame.remove(id);
            this.alive.remove(id);
            if (this.bar != null) {
                p.hideBossBar(this.bar);
            }
            this.showName(p);
            this.sidebars.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) {
                EemiMurder.this.sendToHub(p);
            }
            if (this.state == State.PLAYING) {
                this.checkWin();
            }
            if (this.n != 1 && this.players.isEmpty() && (this.state == State.WAITING || this.state == State.STARTING)) {
                this.state = State.WAITING;
                this.removed = true;
                EemiMurder.this.removeGame(this);
                return;
            }
            if (this.n == 1 && this.players.isEmpty() && (this.state == State.WAITING || this.state == State.STARTING)) {
                // 1.14.1 : salle vide -> la map tiree est liberee (nouveau tirage au prochain joueur) ; elle n'a pas
                // ete jouee, on remet donc la derniere map jouee comme map a exclure
                this.state = State.WAITING;
                this.forced = false;
                if (this.chosen != null) {
                    String chosenId = this.chosen.id;
                    this.releaseWorld();
                    this.lastMapId = this.lastMapIdBefore;
                    if (chosenId.equals(EemiMurder.this.lastSmallMapId)) {
                        EemiMurder.this.lastSmallMapId = this.lastSmallBefore;
                    }
                }
            }
            EemiMurder.this.reportStatus();
        }

        Scoreboard sidebar(Player p) {
            Scoreboard b = this.sidebars.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("murder", Criteria.DUMMY, Component.text((String)"MURDER", (TextColor)NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                this.sidebars.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) {
                p.setScoreboard(b);
            }
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = this.sidebar(p);
            Objective o = b.getObjective("murder");
            for (int i = 0; i < 15; ++i) {
                String entry = "\u00a7" + Integer.toHexString(i) + "\u00a7r";
                Team t = b.getTeam("line" + i);
                if (i < lines.size()) {
                    if (t == null) {
                        t = b.registerNewTeam("line" + i);
                        t.addEntry(entry);
                    }
                    t.prefix(lines.get(i));
                    o.getScore(entry).setScore(lines.size() - i);
                    continue;
                }
                if (t == null) continue;
                b.resetScores(entry);
                t.unregister();
            }
        }

        void updateSidebar(Player p) {
            UUID id = p.getUniqueId();
            ArrayList<Component> l = new ArrayList<Component>();
            l.add((Component)Component.text((String)" "));
            if (this.inGame.contains(id) && (this.state == State.PLAYING || this.state == State.ENDING)) {
                Role r = this.roles.get(id);
                long innocents = this.alive.stream().filter(u -> this.roles.get(u) != Role.MURDERER).count();
                l.add(Component.text((String)"Map : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.current == null ? "-" : this.current.name), (TextColor)NamedTextColor.WHITE)));
                l.add(Component.text((String)"R\u00f4le : ", (TextColor)NamedTextColor.GRAY).append((Component)(r == null ? Component.text((String)"-") : (this.alive.contains(id) ? Component.text((String)r.label, (TextColor)r.color) : Component.text((String)"Mort", (TextColor)NamedTextColor.DARK_GRAY)))));
                l.add((Component)Component.text((String)"  "));
                l.add(Component.text((String)"Innocents en vie : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.valueOf(innocents), (TextColor)NamedTextColor.GREEN)));
                l.add(Component.text((String)"Or : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(String.valueOf(this.gold.getOrDefault(id, 0)) + "/" + EemiMurder.this.cfg("gold-for-bow", 10)), (TextColor)NamedTextColor.GOLD)));
                l.add(Component.text((String)"Temps : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)EemiMurder.formatTime(this.state == State.PLAYING ? this.timer : 0), (TextColor)NamedTextColor.WHITE)));
            } else {
                l.add(Component.text((String)"Partie : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.valueOf(this.n), (TextColor)NamedTextColor.WHITE)));
                l.add(Component.text((String)"Joueurs : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.players.size() + "/" + EemiMurder.this.maxPlayers()), (TextColor)NamedTextColor.WHITE)));
                l.add(Component.text((String)"Minimum : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.valueOf(EemiMurder.this.cfg("min-players", 3)), (TextColor)NamedTextColor.WHITE)));
                l.add((Component)Component.text((String)"  "));
                if (this.state == State.STARTING) {
                    l.add(Component.text((String)"D\u00e9but dans ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.timer + " s"), (TextColor)NamedTextColor.GOLD)));
                } else if (this.state == State.PLAYING || this.state == State.ENDING) {
                    l.add((Component)Component.text((String)"Partie en cours", (TextColor)NamedTextColor.YELLOW));
                } else {
                    l.add((Component)Component.text((String)"En attente...", (TextColor)NamedTextColor.YELLOW));
                }
                l.add(this.chosen != null ? Component.text((String)"Map : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)this.chosen.name, (TextColor)NamedTextColor.WHITE)) : Component.text((String)"Maps : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.valueOf(EemiMurder.this.maps.size()), (TextColor)NamedTextColor.WHITE)));
            }
            l.add((Component)Component.text((String)"   "));
            l.add((Component)Component.text((String)"eemicraft", (TextColor)NamedTextColor.DARK_GRAY));
            this.setLines(p, l);
        }

        void startGame() {
            int i;
            if (this.n == 1) {
                // 1.10.0 : /murder start apres des departs (ou un seuil franchi dans les 10 dernieres secondes) :
                // map de la bonne taille ; sans cout pour la partie 1 (monde d'origine, aucune copie)
                this.resizeMapIfNeeded(true);
            }
            if (this.chosen == null || this.world == null) {
                if (this.chosen == null && !this.preparing) {
                    this.prepareMap();
                }
                if (this.world == null) {
                    if (this.state == State.WAITING) {
                        this.state = State.STARTING;
                        this.timer = 0;
                        this.forced = true;
                    }
                    this.broadcast("\u00a77Pr\u00e9paration de la map, la partie commence d\u00e8s qu'elle est pr\u00eate.");
                    EemiMurder.this.reportStatus();
                    return;
                }
            }
            this.forced = false;
            this.current = this.chosen.withWorld(this.world.getName());
            this.lastMapId = this.chosen.id;
            EemiMurder.this.saveLastPlayed(this.chosen);
            List<Player> list = EemiMurder.this.online(this.players);
            if (list.size() < 2) {
                this.state = State.WAITING;
                EemiMurder.this.reportStatus();
                return;
            }
            Collections.shuffle(list);
            this.ready.clear();
            this.inGame.clear();
            this.alive.clear();
            this.roles.clear();
            this.gold.clear();
            this.lastShot.clear();
            this.lastKnifeKill = 0L;
            this.murderer = list.get(0).getUniqueId();
            this.detective = list.get(1).getUniqueId();
            this.bar = BossBar.bossBar((Component)Component.text((String)this.current.name), (float)1.0f, (BossBar.Color)BossBar.Color.RED, (BossBar.Overlay)BossBar.Overlay.PROGRESS);
            this.hideNames(list);
            ArrayList<Integer> order = new ArrayList<Integer>();
            for (i = 0; i < this.current.spawns.size(); ++i) {
                order.add(i);
            }
            Collections.shuffle(order);
            for (i = 0; i < list.size(); ++i) {
                Player p = list.get(i);
                UUID id = p.getUniqueId();
                Role role = i == 0 ? Role.MURDERER : (i == 1 ? Role.DETECTIVE : Role.INNOCENT);
                this.inGame.add(id);
                this.alive.add(id);
                this.roles.put(id, role);
                this.gold.put(id, 0);
                p.teleport(this.current.spawn((Integer)order.get(i % order.size())));
                p.showBossBar(this.bar);
                EemiMurder.this.later(() -> this.equip(p, role));
            }
            this.state = State.PLAYING;
            this.timer = EemiMurder.this.gameTime(this.current);
            this.elapsed = 0;
            this.broadcast("\u00a7eLa partie commence sur \u00a76" + this.current.name + "\u00a7e !");
            EemiMurder.this.reportStatus();
        }

        void equip(Player p, Role role) {
            if (!(p.isOnline() && this.alive.contains(p.getUniqueId()) && EemiMurder.this.mayTouch(p))) {
                return;
            }
            EemiMurder.this.resetPlayer(p, GameMode.ADVENTURE);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, false, false));
            String sub = switch (role.ordinal()) {
                default -> throw new MatchException(null, null);
                case 0 -> "\u00c9limine tout le monde, discr\u00e8tement";
                case 1 -> "Trouve et abats le meurtrier";
                case 2 -> "Survis et ramasse de l'or";
            };
            p.showTitle(Title.title((Component)Component.text((String)role.label, (TextColor)role.color).decorate(TextDecoration.BOLD), (Component)Component.text((String)sub, (TextColor)NamedTextColor.GRAY), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(300L), (Duration)Duration.ofSeconds(3L), (Duration)Duration.ofMillis(700L))));
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.2f);
            if (role == Role.DETECTIVE) {
                EemiMurder.this.giveBow(p, true);
            }
        }

        void spawnGold() {
            if (this.current == null) {
                return;
            }
            long onGround = this.spawned.stream().filter(e -> {
                Item i;
                return e instanceof Item && (i = (Item)e).isValid() && i.getItemStack().getType() == Material.GOLD_INGOT;
            }).count();
            if (onGround >= (long)EemiMurder.this.cfg("gold-max", 12)) {
                return;
            }
            Location loc = this.current.randomGold();
            if (loc == null || loc.getWorld() == null) {
                return;
            }
            Item item = loc.getWorld().dropItem(loc.add(0.0, 0.2, 0.0), new ItemStack(Material.GOLD_INGOT));
            item.setVelocity(item.getVelocity().zero());
            item.setUnlimitedLifetime(true);
            this.spawned.add((Entity)item);
        }

        void refillArrows() {
            long cooldown = (long)EemiMurder.this.cfg("arrow-cooldown", 5) * 1000L;
            long now = System.currentTimeMillis();
            for (Player p : EemiMurder.this.online(this.alive)) {
                if (this.roles.get(p.getUniqueId()) == Role.MURDERER || !p.getInventory().contains(Material.BOW) || p.getInventory().contains(Material.ARROW) || now - this.lastShot.getOrDefault(p.getUniqueId(), 0L) < cooldown) continue;
                p.getInventory().setItem(9, new ItemStack(Material.ARROW));
            }
        }

        void eliminate(Player victim, Player killer, boolean announce) {
            boolean here;
            UUID id = victim.getUniqueId();
            if (!this.alive.remove(id)) {
                return;
            }
            Location at = victim.getLocation();
            boolean bl = here = this.isOwnWorld(victim.getWorld()) && EemiMurder.this.mayTouch(victim);
            if (here && victim.getInventory().contains(Material.BOW)) {
                ItemStack bow = EemiMurder.this.tool(Material.BOW, "Arc du d\u00e9tective", NamedTextColor.AQUA);
                Item drop = at.getWorld().dropItem(at, bow);
                drop.setGlowing(true);
                drop.setUnlimitedLifetime(true);
                this.spawned.add((Entity)drop);
                if (announce) {
                    this.broadcast("\u00a7bL'arc du d\u00e9tective est tomb\u00e9 ! Un innocent peut le ramasser.");
                }
            }
            if (here) {
                victim.getInventory().clear();
                victim.setGameMode(GameMode.SPECTATOR);
                victim.getInventory().setItem(8, EemiMurder.this.tool(Material.RED_BED, "Quitter le Murder", NamedTextColor.RED));
            }
            victim.showTitle(Title.title((Component)Component.text((String)"Tu es mort", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD), (Component)Component.text((String)(killer == null ? "" : "Tu\u00e9 par " + killer.getName()), (TextColor)NamedTextColor.GRAY)));
            at.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
            if (announce) {
                long innocents = this.alive.stream().filter(u -> this.roles.get(u) != Role.MURDERER).count();
                this.broadcast("\u00a77Un joueur est mort. \u00a7f" + innocents + " \u00a77innocent(s) encore en vie.");
            }
            if (killer != null && !killer.equals((Object)victim) && (killer.getUniqueId().equals(this.murderer) || id.equals(this.murderer))) {
                EemiMurder.this.eemi("reward " + killer.getName() + " murder kill");
            }
            EemiMurder.this.reportStatus();
        }

        void checkWin() {
            if (this.state != State.PLAYING) {
                return;
            }
            if (!this.alive.contains(this.murderer)) {
                this.endGame(false, "Le meurtrier a \u00e9t\u00e9 \u00e9limin\u00e9 !");
                return;
            }
            boolean others = this.alive.stream().anyMatch(u -> !u.equals(this.murderer));
            if (!others) {
                this.endGame(true, "Tous les innocents sont morts.");
            }
        }

        void endGame(boolean murdererWins, String reason) {
            this.endGame(murdererWins, reason, true);
        }

        void endGame(boolean murdererWins, String reason, boolean rewards) {
            this.state = State.ENDING;
            if (rewards) {
                for (Player p : EemiMurder.this.online(this.inGame)) {
                    if (!this.players.contains(p.getUniqueId())) continue;
                    EemiMurder.this.eemi("reward " + p.getName() + " murder play");
                    boolean isMurderer = p.getUniqueId().equals(this.murderer);
                    if (murdererWins != isMurderer) continue;
                    EemiMurder.this.eemi("reward " + p.getName() + " murder win");
                }
            }
            this.timer = EemiMurder.this.cfg("end-time", 8);
            String mName = EemiMurder.this.name(this.murderer);
            String dName = EemiMurder.this.name(this.detective);
            NamedTextColor winColor = murdererWins ? NamedTextColor.RED : NamedTextColor.GREEN;
            Component title = Component.text((String)"Victoire !", (TextColor)winColor).decorate(TextDecoration.BOLD);
            TextComponent sub = Component.text((String)(murdererWins ? "Le meurtrier gagne" : "Les innocents gagnent"), (TextColor)winColor);
            for (Player p : EemiMurder.this.online(this.inGame)) {
                p.showTitle(Title.title((Component)title, (Component)sub));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                if (this.bar == null) continue;
                p.hideBossBar(this.bar);
            }
            this.broadcast("\u00a76" + reason + " \u00a77Meurtrier : \u00a7c" + mName + "\u00a77, d\u00e9tective : \u00a7b" + dName + "\u00a77.");
            this.clearSpawned();
            EemiMurder.this.reportStatus();
        }

        void backToLobby() {
            this.removeTeam();
            this.clearSpawned();
            // nouvelle attente : personne n'est pret ; ceux qui attendaient deja au salon recoivent un colorant gris
            this.ready.clear();
            for (Player w : EemiMurder.this.online(this.players)) {
                if (this.inGame.contains(w.getUniqueId()) || EemiMurder.this.lobby == null || !w.getWorld().equals((Object)EemiMurder.this.lobby.getWorld())) continue;
                this.giveReady(w);
            }
            for (UUID id : this.inGame) {
                Player p = Bukkit.getPlayer((UUID)id);
                if (p == null || !this.players.contains(id)) continue;
                p.teleport(EemiMurder.this.lobby);
                EemiMurder.this.later(() -> this.prepareLobbyPlayer(p));
                // 1.11.0 : message commun a tous les jeux (le lit « Quitter le Murder » est deja en case 8)
                p.sendMessage("§8[§dMurder§8] §7Tu restes dans la file du Murder. Clique sur le lit (ou §f/murder leave§7) pour revenir au lobby.");
            }
            this.inGame.clear();
            this.alive.clear();
            this.roles.clear();
            this.gold.clear();
            this.current = null;
            this.bar = null;
            this.murderer = null;
            this.detective = null;
            this.state = State.WAITING;
            this.sendSpectatorsToHub();
            this.releaseWorld();
            if (this.n != 1 && this.players.isEmpty()) {
                this.removed = true;
                EemiMurder.this.removeGame(this);
                return;
            }
            EemiMurder.this.reportStatus();
        }

        void forceStop() {
            if (this.state == State.PLAYING) {
                this.endGame(false, "Partie arr\u00eat\u00e9e par un admin.", false);
            }
            if (this.state == State.ENDING) {
                this.backToLobby();
            }
            if (this.removed) {
                return;
            }
            this.state = State.WAITING;
            EemiMurder.this.reportStatus();
        }

        void clearSpawned() {
            for (Entity e : this.spawned) {
                if (!e.isValid()) continue;
                e.remove();
            }
            this.spawned.clear();
            if (this.current != null && this.current.world() != null) {
                for (Entity e : this.current.world().getEntitiesByClass(AbstractArrow.class)) {
                    if (!this.current.contains(e.getLocation())) continue;
                    e.remove();
                }
            }
        }

        void shutdown() {
            this.sendSpectatorsToHub();
            for (UUID id : new ArrayList<UUID>(this.players)) {
                Player p = Bukkit.getPlayer((UUID)id);
                if (p == null) continue;
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                EemiMurder.this.sendToHub(p);
            }
            this.clearSpawned();
            this.removeTeam();
        }

        void spectate(Player p) {
            Location view = new Location(this.current.world(), (double)(this.current.min[0] + this.current.max[0]) / 2.0 + 0.5, (double)(this.current.min[1] + this.current.max[1]) / 2.0 + 2.0, (double)(this.current.min[2] + this.current.max[2]) / 2.0 + 0.5, 0.0f, 60.0f);
            Game old = EemiMurder.this.bySpectator.remove(p.getUniqueId());
            if (old != null) {
                old.spectators.remove(p.getUniqueId());
            }
            this.spectators.add(p.getUniqueId());
            EemiMurder.this.bySpectator.put(p.getUniqueId(), this);
            p.teleport(view);
            if (EemiMurder.this.mayTouch(p)) {
                p.setGameMode(GameMode.SPECTATOR);
            }
            EemiMurder.this.later(() -> {
                if (p.isOnline() && this.spectators.contains(p.getUniqueId()) && EemiMurder.this.mayTouch(p)) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
            });
            p.sendMessage("\u00a78[\u00a7dMurder\u00a78] \u00a77\u00a77Tu regardes la partie " + this.n + ", /lobby pour revenir.");
        }

        void sendSpectatorsToHub() {
            List<Player> list = EemiMurder.this.online(this.spectators);
            for (UUID id : this.spectators) {
                EemiMurder.this.bySpectator.remove(id);
            }
            this.spectators.clear();
            for (Player s : list) {
                EemiMurder.this.sendToHub(s);
            }
        }

        void hideNames(List<Player> list) {
            for (Player viewer : list) {
                Scoreboard board = viewer.getScoreboard();
                Team team = board.getTeam(this.teamName);
                if (team == null) {
                    team = board.registerNewTeam(this.teamName);
                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                    team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                }
                for (Player p : list) {
                    team.addEntry(p.getName());
                }
                this.boards.add(board);
            }
        }

        void showName(Player p) {
            for (Scoreboard board : this.boards) {
                Team team = board.getTeam(this.teamName);
                if (team == null) continue;
                team.removeEntry(p.getName());
            }
        }

        void removeTeam() {
            this.boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
            for (Scoreboard board : this.boards) {
                Team team = board.getTeam(this.teamName);
                if (team == null) continue;
                team.unregister();
            }
            this.boards.clear();
        }

        void broadcast(String msg) {
            for (Player p : EemiMurder.this.online(this.players)) {
                p.sendMessage(EemiMurder.PREFIX + msg);
            }
        }

        String describe() {
            String w = this.world != null ? this.world.getName() : (this.preparing ? "copie en pr\u00e9paration" : "-");
            return "\u00a7f" + this.label() + " \u00a77: monde \u00a7f" + w + "\u00a77, " + this.state.name().toLowerCase() + ", \u00a7f" + this.players.size() + "\u00a77/" + EemiMurder.this.maxPlayers() + " inscrits" + (String)(this.state == State.PLAYING || this.state == State.ENDING ? ", " + this.alive.size() + " en vie" : "") + (String)(this.chosen != null ? ", map " + this.chosen.name : "");
        }
    }

    static enum State {
        WAITING,
        STARTING,
        PLAYING,
        ENDING;

    }

    static enum Role {
        MURDERER("Meurtrier", NamedTextColor.RED),
        DETECTIVE("D\u00e9tective", NamedTextColor.AQUA),
        INNOCENT("Innocent", NamedTextColor.GREEN);

        final String label;
        final NamedTextColor color;

        private Role(String label, NamedTextColor color) {
            this.label = label;
            this.color = color;
        }
    }
}

