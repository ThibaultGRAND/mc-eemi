/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  net.kyori.adventure.title.Title
 *  net.kyori.adventure.title.Title$Times
 *  org.bukkit.Bukkit
 *  org.bukkit.Color
 *  org.bukkit.DyeColor
 *  org.bukkit.GameMode
 *  org.bukkit.Keyed
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.Particle$DustOptions
 *  org.bukkit.Sound
 *  org.bukkit.Tag
 *  org.bukkit.World
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.MultipleFacing
 *  org.bukkit.block.data.type.Leaves
 *  org.bukkit.block.data.type.Wall
 *  org.bukkit.block.data.type.Wall$Height
 *  org.bukkit.block.structure.StructureRotation
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.AbstractArrow
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.ExperienceOrb
 *  org.bukkit.entity.FallingBlock
 *  org.bukkit.entity.Fireball
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Mob
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.entity.Sheep
 *  org.bukkit.entity.Snowball
 *  org.bukkit.entity.TNTPrimed
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockExplodeEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.entity.CreatureSpawnEvent
 *  org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityDamageEvent$DamageCause
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.bukkit.event.entity.EntityExplodeEvent
 *  org.bukkit.event.entity.EntityShootBowEvent
 *  org.bukkit.event.entity.FoodLevelChangeEvent
 *  org.bukkit.event.entity.ItemSpawnEvent
 *  org.bukkit.event.entity.PlayerDeathEvent
 *  org.bukkit.event.entity.ProjectileHitEvent
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerRespawnEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.PlayerInventory
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.inventory.meta.LeatherArmorMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.projectiles.ProjectileSource
 *  org.bukkit.scoreboard.Criteria
 *  org.bukkit.scoreboard.DisplaySlot
 *  org.bukkit.scoreboard.Objective
 *  org.bukkit.scoreboard.Scoreboard
 *  org.bukkit.scoreboard.Team
 *  org.bukkit.util.Vector
 */
package fr.eemicraft.sheepwars;

import fr.eemicraft.sheepwars.WorldClone;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Wall;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

public final class EemiSheepWars
extends JavaPlugin
implements Listener {
    private static final String PREFIX = "\u00a78[\u00a79Sheep Wars\u00a78] \u00a77";
    private static final DyeColor[] MAGIC_ORDER = new DyeColor[]{DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE, DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK};
    private NamespacedKey kitKey;
    private NamespacedKey sheepKey;
    private NamespacedKey ballKey;
    private String base;
    private int cy;
    private final List<Game> games = new ArrayList<Game>();
    private final Map<UUID, Game> byPlayer = new HashMap<UUID, Game>();
    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();
    private final Map<String, BlockData> dataCache = new HashMap<String, BlockData>();
    private final Map<String, int[]> areaCache = new HashMap<String, int[]>();
    private final Random random = new Random();
    private World modele;
    private String modeleMap;
    private boolean modeleBusy;
    private final List<Runnable> modeleWaiters = new ArrayList<Runnable>();
    private final Map<String, Long> destroyedAt = new HashMap<String, Long>();
    private String nextMap;
    private static final Map<String, Imported> IMPORTED = new LinkedHashMap<String, Imported>();
    private static final int CLEAR_X = 60;
    private static final int CLEAR_Z = 58;
    private static final int CLEAR_DOWN = 35;
    private static final int CLEAR_UP = 38;
    private static final BlockData NONE;
    private final Map<Integer, BlockData> legacyCache = new HashMap<Integer, BlockData>();
    private Map<Integer, Material> legacyIds;

    public void onEnable() {
        this.kitKey = new NamespacedKey((Plugin)this, "kit");
        this.sheepKey = new NamespacedKey((Plugin)this, "sheep");
        this.ballKey = new NamespacedKey((Plugin)this, "ball");
        this.saveDefaultConfig();
        this.reloadConfig();
        this.base = this.getConfig().getString("world", "sheepwars");
        this.cy = this.cfg("center-y", 100);
        Game first = new Game(1);
        this.games.add(first);
        first.attach(Bukkit.getWorld((String)this.base));
        if (first.world == null) {
            this.getLogger().warning("Monde " + this.base + " introuvable.");
        }
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::sheepTick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            if (first.world == null) {
                first.attach(Bukkit.getWorld((String)this.base));
            }
            if (first.world == null) {
                return;
            }
            WorldClone.cleanup((Plugin)this, first.world, this.base, this.maxInstances());
            first.remakeModele = true;
            first.rebuild(null, this.pickMap(first, false));
        }, 100L);
        this.reportStatus();
    }

    public void onDisable() {
        for (Game g : new ArrayList<Game>(this.games)) {
            g.shutdown();
        }
        for (Game g : new ArrayList<Game>(this.games)) {
            if (g.n <= 1 || g.world == null) continue;
            Bukkit.unloadWorld((World)g.world, (boolean)false);
        }
        if (this.modele != null) {
            Bukkit.unloadWorld((World)this.modele, (boolean)false);
        }
    }

    private int cfg(String key, int def) {
        return this.getConfig().getInt(key, def);
    }

    private int maxInstances() {
        return Math.max(1, this.cfg("max-instances", 3));
    }

    private String modeleName() {
        return this.base + "_modele";
    }

    private boolean isProtected(World w) {
        return w != null && (WorldClone.index(this.base, w.getName()) > 0 || w.getName().equals(this.modeleName()));
    }

    private Game gameOf(World w) {
        if (w == null) {
            return null;
        }
        for (Game g : this.games) {
            if (g.destroyed || !w.equals((Object)g.world)) continue;
            return g;
        }
        return null;
    }

    private Game game(int n) {
        for (Game g : this.games) {
            if (g.n != n || g.destroyed) continue;
            return g;
        }
        return null;
    }

    private Game playing(Player p) {
        Game g = this.byPlayer.get(p.getUniqueId());
        return g != null && g.players.contains(p.getUniqueId()) ? g : null;
    }

    private static boolean isFixed(Material m) {
        return m == Material.BARRIER || m == Material.BEDROCK;
    }

    private void tick() {
        for (Game g : new ArrayList<Game>(this.games)) {
            if (g.destroyed) continue;
            g.tick();
            if (g.destroyed || g.n <= 1 || g.preparing || g.building || g.state != State.WAITING || g.size() != 0) continue;
            this.destroyGame(g);
        }
    }

    private void sheepTick() {
        for (Game g : new ArrayList<Game>(this.games)) {
            if (g.destroyed || g.world == null) continue;
            g.sheepTick();
        }
    }

    private void remakeModele() {
        Game first = this.game(1);
        if (first == null || first.world == null) {
            return;
        }
        this.modeleBusy = true;
        if (this.modele != null) {
            World old = this.modele;
            this.modele = null;
            for (Player p : old.getPlayers()) {
                this.sendToHub(p);
            }
            if (!Bukkit.unloadWorld((World)old, (boolean)false)) {
                this.getLogger().warning("Impossible de d\u00e9charger " + old.getName() + ".");
            }
        }
        String map = first.mapId;
        WorldClone.create((Plugin)this, first.world, this.modeleName(), w -> {
            this.modele = w;
            this.modeleMap = map;
            this.modeleBusy = false;
            if (w == null) {
                this.getLogger().warning("Mod\u00e8le " + this.modeleName() + " indisponible : pas de partie en plus.");
            } else {
                this.getLogger().info("Mod\u00e8le " + this.modeleName() + " pr\u00eat (map " + map + ").");
            }
            ArrayList<Runnable> todo = new ArrayList<Runnable>(this.modeleWaiters);
            this.modeleWaiters.clear();
            todo.forEach(Runnable::run);
        });
    }

    private void withModele(Runnable r) {
        if (this.modele != null && !this.modeleBusy) {
            r.run();
        } else {
            this.modeleWaiters.add(r);
        }
    }

    private int freeIndex() {
        boolean cooling = false;
        for (int n = 2; n <= this.maxInstances(); ++n) {
            if (this.game(n) != null) continue;
            Long t = this.destroyedAt.get(WorldClone.name(this.base, n));
            if (t != null && System.currentTimeMillis() - t < 15000L) {
                cooling = true;
                continue;
            }
            return n;
        }
        return cooling ? -1 : 0;
    }

    private void newCopy(Player p, int n) {
        Game g = new Game(n);
        g.preparing = true;
        g.pending.add(p.getUniqueId());
        this.byPlayer.put(p.getUniqueId(), g);
        this.games.add(g);
        p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Pr\u00e9paration d'une nouvelle partie...");
        this.reportStatus();
        this.withModele(() -> {
            if (g.destroyed) {
                return;
            }
            if (this.modele == null) {
                this.failCopy(g);
                return;
            }
            String map = this.modeleMap;
            WorldClone.create((Plugin)this, this.modele, WorldClone.name(this.base, n), w -> {
                if (g.destroyed) {
                    if (w != null) {
                        WorldClone.destroy((Plugin)this, w, this.base, this::sendToHub);
                    }
                    return;
                }
                if (w == null) {
                    this.failCopy(g);
                    return;
                }
                g.attach((World)w);
                g.mapId = map;
                g.restoreArea();
                g.preparing = false;
                World hub = this.hubWorld();
                for (UUID id : new ArrayList<UUID>(g.pending)) {
                    g.pending.remove(id);
                    Player pl = Bukkit.getPlayer((UUID)id);
                    if (pl == null || hub != null && !pl.getWorld().equals((Object)hub)) {
                        this.byPlayer.remove(id);
                        if (pl == null) continue;
                        pl.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Tu n'es plus dans le hub : inscription annul\u00e9e.");
                        continue;
                    }
                    g.addPlayer(pl);
                }
                if (g.players.isEmpty()) {
                    this.destroyGame(g);
                    return;
                }
                String pick = this.pickMap(g, true);
                if (!pick.equals(g.mapId) || !this.areaCache.containsKey(g.mapId)) {
                    g.rebuild(null, pick);
                }
                this.reportStatus();
            });
        });
    }

    private void failCopy(Game g) {
        for (UUID id : g.pending) {
            this.byPlayer.remove(id);
            Player pl = Bukkit.getPlayer((UUID)id);
            if (pl == null) continue;
            pl.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cImpossible de pr\u00e9parer une nouvelle partie.");
        }
        g.pending.clear();
        g.destroyed = true;
        this.games.remove(g);
        this.reportStatus();
    }

    private void destroyGame(Game g) {
        if (g.n <= 1) {
            return;
        }
        g.shutdown();
        g.destroyed = true;
        this.games.remove(g);
        if (g.world != null) {
            this.destroyedAt.put(g.world.getName(), System.currentTimeMillis());
            WorldClone.destroy((Plugin)this, g.world, this.base, this::sendToHub);
            this.getLogger().info("Partie " + g.n + " termin\u00e9e : copie " + g.world.getName() + " supprim\u00e9e.");
        }
        this.reportStatus();
    }

    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (this.byPlayer.containsKey(id)) {
            p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Tu es d\u00e9j\u00e0 inscrit.");
            return;
        }
        boolean spec = false;
        for (Game g : this.games) {
            spec |= g.spectators.remove(id);
        }
        if (spec) {
            this.sendToHub(p);
        }
        int max = this.cfg("max-players", 12);
        Game best = null;
        for (Game g : this.games) {
            if (g.destroyed || g.size() >= max || !g.preparing && (g.world == null || g.state != State.WAITING && g.state != State.STARTING) || best != null && g.size() <= best.size()) continue;
            best = g;
        }
        if (best != null) {
            for (Game g : this.games) {
                g.spectators.remove(id);
            }
            if (best.preparing) {
                best.pending.add(id);
                this.byPlayer.put(id, best);
                p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Pr\u00e9paration d'une nouvelle partie...");
                this.reportStatus();
            } else {
                best.addPlayer(p);
            }
            return;
        }
        Game first = this.game(1);
        if (first == null || first.world == null) {
            p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cLa map n'est pas charg\u00e9e.");
            return;
        }
        if (this.games.size() < this.maxInstances()) {
            int n = this.freeIndex();
            if (n > 0) {
                for (Game g : this.games) {
                    g.spectators.remove(id);
                }
                this.newCopy(p, n);
                return;
            }
            if (n < 0) {
                p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cUne partie se lib\u00e8re, r\u00e9essaie dans quelques secondes.");
                return;
            }
        }
        p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cToutes les parties sont pleines.");
    }

    private World hubWorld() {
        return Bukkit.getWorld((String)this.getConfig().getString("hub-world", "lobby"));
    }

    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        return this.isProtected(w) || w.equals((Object)this.hubWorld());
    }

    private void sendToHub(Player p) {
        if (!this.mayTouch(p)) {
            return;
        }
        this.resetPlayer(p, GameMode.SURVIVAL);
        World hub = this.hubWorld();
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
        p.setFallDistance(0.0f);
    }

    private ItemStack unbreakable(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack armor(Material type, Side s) {
        ItemStack it = this.unbreakable(this.kit(new ItemStack(type), null));
        LeatherArmorMeta meta = (LeatherArmorMeta)it.getItemMeta();
        meta.setColor(s.armor);
        it.setItemMeta((ItemMeta)meta);
        return it;
    }

    private ItemStack kit(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        if (name != null) {
            meta.displayName(Component.text((String)name, (TextColor)NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        meta.getPersistentDataContainer().set(this.kitKey, PersistentDataType.BYTE, (byte)1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(this.kitKey, PersistentDataType.BYTE);
    }

    private SheepType sheepOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String k = (String)it.getItemMeta().getPersistentDataContainer().get(this.sheepKey, PersistentDataType.STRING);
        return k == null ? null : SheepType.of(k);
    }

    private ItemStack sheepItem(SheepType t) {
        ItemStack it = new ItemStack(t.item);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(((TextComponent)Component.text((String)t.label, (TextColor)TextColor.color((int)t.rgb)).decorate(TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((TextComponent)Component.text((String)t.lore, (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)"Clic gauche : tir droit - clic droit : tir en cloche", (TextColor)NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setItemModel(new NamespacedKey("eemicraft", "sheep_" + t.key.replace('-', '_')));
        meta.getPersistentDataContainer().set(this.sheepKey, PersistentDataType.STRING, t.key);
        meta.getPersistentDataContainer().set(this.kitKey, PersistentDataType.BYTE, (byte)1);
        it.setItemMeta(meta);
        return it;
    }

    private void giveSheep(Player p, int n) {
        if (!this.mayTouch(p)) {
            return;
        }
        int held = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (this.sheepOf(it) == null) continue;
            held += it.getAmount();
        }
        n = Math.min(n, this.cfg("max-sheep", 6) - held);
        for (int i = 0; i < n; ++i) {
            SheepType t = this.randomSheep();
            if (t == null) {
                return;
            }
            p.getInventory().addItem(new ItemStack[]{this.sheepItem(t)});
            p.sendActionBar(Component.text((String)"+ ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)t.label, (TextColor)TextColor.color((int)t.rgb))));
            p.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1.0f, 1.2f);
        }
    }

    private SheepType randomSheep() {
        int total = 0;
        for (SheepType t : SheepType.values()) {
            total += Math.max(0, this.getConfig().getInt("weights." + t.key, 10));
        }
        if (total <= 0) {
            return null;
        }
        int r = this.random.nextInt(total);
        for (SheepType t : SheepType.values()) {
            if ((r -= Math.max(0, this.getConfig().getInt("weights." + t.key, 10))) >= 0) continue;
            return t;
        }
        return SheepType.EXPLOSIF;
    }

    private void top(Player p, Material m, int add, int max) {
        if (!this.mayTouch(p)) {
            return;
        }
        int have = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType() != m) continue;
            have += it.getAmount();
        }
        int n = Math.min(add, max - have);
        if (n > 0) {
            p.getInventory().addItem(new ItemStack[]{this.kit(new ItemStack(m, n), null)});
        }
    }

    private int sheepDelay(int teamAlive) {
        if (teamAlive >= 5) {
            return 12;
        }
        if (teamAlive >= 3) {
            return 10;
        }
        if (teamAlive == 2) {
            return 8;
        }
        return 6;
    }

    private BlockData bd(String s) {
        return this.dataCache.computeIfAbsent(s, k -> {
            try {
                return Bukkit.createBlockData((String)(k.contains(":") ? k : "minecraft:" + k));
            }
            catch (IllegalArgumentException ex) {
                this.getLogger().warning("Bloc invalide ignore : " + k);
                return Material.AIR.createBlockData();
            }
        });
    }

    private String pickMap(Game g, boolean sameOk) {
        if (g.n == 1 && this.nextMap != null) {
            String m = this.nextMap;
            this.nextMap = null;
            return m;
        }
        ArrayList<String> pool = new ArrayList<String>();
        for (String id : this.getConfig().getStringList("maps")) {
            if (!IMPORTED.containsKey(id.toLowerCase())) continue;
            pool.add(id.toLowerCase());
        }
        if (pool.isEmpty()) {
            pool.addAll(IMPORTED.keySet());
        }
        if (!sameOk && pool.size() > 1) {
            pool.remove(g.mapId);
        }
        return (String)pool.get(this.random.nextInt(pool.size()));
    }

    private int baseY() {
        return this.cy - 30;
    }

    private int[] toWorld(Imported m, int x, int y, int z) {
        int dx = x - m.mid();
        int dz = z - m.zc();
        if (m.rotate()) {
            dx = -dx;
            dz = -dz;
        }
        return new int[]{dx, this.baseY() + y - m.oy(), dz};
    }

    private BlockData legacy(int id, int data) {
        BlockData r = this.legacyCache.computeIfAbsent(id << 4 | data, k -> {
            Material m;
            if (this.legacyIds == null) {
                this.legacyIds = new HashMap<Integer, Material>();
                for (Material m2 : (Material[])Material.class.getEnumConstants()) {
                    if (!m2.isLegacy()) continue;
                    this.legacyIds.put(m2.getId(), m2);
                }
                this.getLogger().info(this.legacyIds.size() + " ids de blocs 1.8 connus.");
            }
            if ((m = this.legacyIds.get(id)) == null) {
                this.getLogger().warning("Bloc 1.8 inconnu ignore : " + id + ":" + data);
                return NONE;
            }
            try {
                BlockData bd = Bukkit.getUnsafe().fromLegacy(m, (byte)data);
                if (bd == null || bd.getMaterial().isAir()) {
                    return NONE;
                }
                if (bd instanceof Leaves) {
                    Leaves l = (Leaves)bd;
                    l.setPersistent(true);
                }
                return bd;
            }
            catch (RuntimeException ex) {
                this.getLogger().warning("Conversion impossible du bloc 1.8 " + id + ":" + data + " : " + ex.getMessage());
                return NONE;
            }
        });
        return r == NONE ? null : r;
    }

    /** Etat moderne d'une map SWM2 ("minecraft:oak_stairs[facing=east,...]") ; air ou invalide = null. */
    private BlockData modernState(String s) {
        if (s == null || s.isEmpty() || s.equals("minecraft:air")) {
            return null;
        }
        try {
            BlockData bd = Bukkit.createBlockData(s);
            return bd.getMaterial().isAir() ? null : bd;
        }
        catch (IllegalArgumentException ex) {
            this.getLogger().warning("Bloc invalide ignore : " + s);
            return null;
        }
    }

    private static boolean pane(Material m) {
        return m == Material.IRON_BARS || m.name().endsWith("_PANE");
    }

    private static boolean connects(BlockData self, BlockData n) {
        if (n == null) {
            return false;
        }
        Material a = self.getMaterial();
        Material b = n.getMaterial();
        if (b.isOccluding()) {
            return true;
        }
        if (Tag.FENCES.isTagged(a)) {
            return Tag.FENCES.isTagged(b) && a == Material.NETHER_BRICK_FENCE == (b == Material.NETHER_BRICK_FENCE) || Tag.FENCE_GATES.isTagged(b);
        }
        if (EemiSheepWars.pane(a)) {
            return EemiSheepWars.pane(b) || Tag.WALLS.isTagged(b);
        }
        if (Tag.WALLS.isTagged(a)) {
            return Tag.WALLS.isTagged(b) || Tag.FENCE_GATES.isTagged(b) || EemiSheepWars.pane(b);
        }
        return false;
    }

    private void lobbyRoom(Builder b) {
        int y = this.cfg("lobby-y", 140);
        for (int x = -7; x <= 7; ++x) {
            for (int z = -7; z <= 7; ++z) {
                boolean edge;
                boolean bl = edge = Math.abs(x) == 7 || Math.abs(z) == 7;
                b.put(x, y, z, edge ? "white_wool" : ((x + z) % 2 == 0 ? "spruce_planks" : "stripped_spruce_wood"));
                for (int dy = 1; dy <= 4; ++dy) {
                    b.put(x, y + dy, z, edge ? "barrier" : null);
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                b.put(sx * 6, y + 1, sz * 6, "hay_block");
                b.put(sx * 6, y + 2, sz * 6, "lantern");
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (this.isProtected(e.getLocation().getWorld()) && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        if (this.isProtected(e.getLocation().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        World w = e.getLocation().getWorld();
        if (!this.isProtected(w)) {
            return;
        }
        Game g = this.gameOf(w);
        if (g == null) {
            e.blockList().clear();
        } else {
            e.blockList().removeIf(b -> b.equals((Object)g.magicWool) || !g.inArea(b.getX(), b.getY(), b.getZ()));
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        World w = e.getBlock().getWorld();
        if (!this.isProtected(w)) {
            return;
        }
        Game g = this.gameOf(w);
        if (g == null) {
            e.blockList().clear();
        } else {
            e.blockList().removeIf(b -> b.equals((Object)g.magicWool) || !g.inArea(b.getX(), b.getY(), b.getZ()));
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onBallHit(ProjectileHitEvent e) {
        Snowball ball;
        Projectile projectile = e.getEntity();
        if (!(projectile instanceof Snowball) || !(ball = (Snowball)projectile).getPersistentDataContainer().has(this.ballKey, PersistentDataType.BYTE)) {
            return;
        }
        Game g = this.gameOf(ball.getWorld());
        Entity hit = e.getHitEntity();
        if (hit != null) {
            for (Entity passenger : ball.getPassengers()) {
                if (!passenger.equals((Object)hit) && !passenger.getPassengers().contains(hit)) continue;
                e.setCancelled(true);
                return;
            }
        }
        if (g == null) {
            return;
        }
        for (Entity passenger : new ArrayList<Entity>(ball.getPassengers())) {
            ball.removePassenger(passenger);
            Launched l = g.sheep.get(passenger.getUniqueId());
            if (l == null) continue;
            Location at = ball.getLocation();
            if (e.getHitBlock() != null && e.getHitBlockFace() != null) {
                at = e.getHitBlock().getRelative(e.getHitBlockFace()).getLocation().add(0.5, 0.0, 0.5);
            }
            ArrayList<Entity> riders = new ArrayList<Entity>(passenger.getPassengers());
            for (Entity r : riders) {
                passenger.removePassenger(r);
            }
            passenger.teleport(at);
            for (Entity r : riders) {
                r.teleport(at.clone().add(0.0, 1.0, 0.0));
                r.setFallDistance(0.0f);
            }
            g.arm(l, passenger);
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        Vector push;
        Player victim;
        Entity entity;
        AbstractArrow arrow;
        Projectile projectile = e.getEntity();
        if (!(projectile instanceof AbstractArrow) || !((arrow = (AbstractArrow)projectile).getShooter() instanceof Player)) {
            return;
        }
        Player shooter = (Player)arrow.getShooter();
        Game g = this.playing(shooter);
        if (g == null || g.state != State.PLAYING || !g.alive.contains(shooter.getUniqueId()) || !arrow.getWorld().equals((Object)g.world)) {
            return;
        }
        Side s = g.teams.get(shooter.getUniqueId());
        Block hb = e.getHitBlock();
        if (hb != null && g.magicWool != null && hb.equals((Object)g.magicWool)) {
            g.magicHit(shooter, MAGIC_ORDER[g.magicColor]);
            arrow.remove();
            return;
        }
        if (g.buff(s, "arrows")) {
            g.world.createExplosion(arrow.getLocation(), 1.6f, false, true, (Entity)shooter);
            arrow.remove();
        }
        if (g.buff(s, "punch") && (entity = e.getHitEntity()) instanceof Player && g.teams.get((victim = (Player)entity).getUniqueId()) != s && (push = arrow.getVelocity().setY(0)).lengthSquared() > 0.0) {
            victim.setVelocity(victim.getVelocity().add(push.normalize().multiply(1.2).setY(0.35)));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onShoot(EntityShootBowEvent e) {
        LivingEntity livingEntity = e.getEntity();
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player p = (Player)livingEntity;
        Game g = this.playing(p);
        if (g == null || !g.alive.contains(p.getUniqueId())) {
            return;
        }
        if (g.cageTimer > 0 || g.buff(g.teams.get(p.getUniqueId()), "nobow")) {
            e.setCancelled(true);
            p.sendActionBar((Component)Component.text((String)"Ton arc est bloqu\u00e9 !", (TextColor)NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onSheepDeath(EntityDeathEvent e) {
        boolean direct;
        Game g = this.gameOf(e.getEntity().getWorld());
        if (g == null) {
            return;
        }
        Launched l = g.sheep.remove(e.getEntity().getUniqueId());
        if (l == null) {
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player killer = e.getEntity().getKiller();
        EntityDamageEvent last = e.getEntity().getLastDamageCause();
        boolean bl = direct = last != null && (last.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK || last.getCause() == EntityDamageEvent.DamageCause.PROJECTILE);
        if (killer != null && direct && g.alive.contains(killer.getUniqueId()) && g.state == State.PLAYING) {
            killer.getInventory().addItem(new ItemStack[]{this.sheepItem(l.type)});
            killer.sendActionBar(Component.text((String)"Mouton r\u00e9cup\u00e9r\u00e9 : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)l.type.label, (TextColor)TextColor.color((int)l.type.rgb))));
            killer.playSound(killer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        Entity entity = e.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player)entity;
        Game g = this.playing(victim);
        if (g == null) {
            return;
        }
        UUID id = victim.getUniqueId();
        if (g.state != State.PLAYING || g.cageTimer > 0 || !g.alive.contains(id) || victim.getGameMode() != GameMode.SURVIVAL) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent) {
            Player pl;
            Projectile proj;
            Player attacker;
            Player pl2;
            EntityDamageByEntityEvent by = (EntityDamageByEntityEvent)e;
            Entity entity2 = by.getDamager();
            attacker = entity2 instanceof Player ? (Player)entity2 : (entity2 instanceof Projectile && ((Projectile)entity2).getShooter() instanceof Player ? (Player)((Projectile)entity2).getShooter() : null);
            if (attacker == null) {
                return;
            }
            if (g.teams.get(attacker.getUniqueId()) == g.teams.get(id) || !g.teams.containsKey(attacker.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            g.lastHit.put(id, attacker.getUniqueId());
            g.lastHitTime.put(id, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        String msg;
        Player p = e.getEntity();
        Game g = this.playing(p);
        if (g == null) {
            return;
        }
        Side s = g.teams.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        Player killer = p.getKiller();
        Long t = g.lastHitTime.get(p.getUniqueId());
        if (killer == null && t != null && System.currentTimeMillis() - t < 10000L) {
            killer = Bukkit.getPlayer((UUID)g.lastHit.get(p.getUniqueId()));
        }
        if (killer != null && g.teams.containsKey(killer.getUniqueId()) && killer != p) {
            g.kills.merge(killer.getUniqueId(), 1, Integer::sum);
            if (g.state == State.PLAYING) {
                this.eemi("reward " + killer.getName() + " sheepwars kill");
            }
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            msg = g.teams.get(killer.getUniqueId()).code() + killer.getName() + " \u00a77a \u00e9limin\u00e9 " + s.code() + p.getName();
        } else {
            msg = s.code() + p.getName() + " \u00a77est \u00e9limin\u00e9.";
        }
        if (g.state == State.PLAYING) {
            g.eliminate(p, msg);
        }
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            if (p.isOnline() && p.isDead()) {
                p.spigot().respawn();
            }
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = this.playing(p);
        if (g == null || g.lobby == null) {
            return;
        }
        if (g.teams.containsKey(p.getUniqueId()) && (g.state == State.PLAYING || g.state == State.ENDING)) {
            e.setRespawnLocation(g.spectatorPoint());
            this.later(() -> {
                if (!p.isOnline() || !this.mayTouch(p)) {
                    return;
                }
                this.resetPlayer(p, GameMode.SPECTATOR);
                p.getInventory().clear();
            });
            return;
        }
        e.setRespawnLocation(g.lobby);
        this.later(() -> g.prepareLobbyPlayer(p));
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = this.playing(p);
        if (g == null) {
            return;
        }
        Location to = e.getTo();
        if (g.world == null || !to.getWorld().equals((Object)g.world)) {
            return;
        }
        if (to.getBlockY() == e.getFrom().getBlockY() && to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ()) {
            return;
        }
        if (g.state == State.PLAYING && g.alive.contains(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL) {
            // 4.5 : on ne passe pas la ligne x = 0 vers le camp adverse a pied (tour de laine, laine magique,
            // projection) ; seul le mouton d'abordage (joueur en passager) fait traverser. Le retour est libre.
            // Les maps 1.8 d'origine (rose, vert, vaisseau) gardent leur traversee libre (demande d'Esteban, 16/11/2026).
            Side side = g.teams.get(p.getUniqueId());
            if (side != null && !p.isInsideVehicle() && !(this.getConfig().isList("free-crossing-maps") ? this.getConfig().getStringList("free-crossing-maps") : List.of("rose", "vert", "vaisseau")).contains(g.mapId) && e.getFrom().getX() * (double)side.dir() > 0.0 && to.getX() * (double)side.dir() <= 0.0) {
                e.setCancelled(true);
                p.setVelocity(new Vector(0.15 * (double)side.dir(), Math.min(0.0, p.getVelocity().getY()), 0.0));
                return;
            }
            if (to.getY() < (double)g.mapVoidY()) {
                p.setHealth(0.0);
            }
            return;
        }
        if (to.getY() < (double)(g.mapVoidY() - 20) && g.lobby != null) {
            p.teleport(g.state == State.WAITING || g.state == State.STARTING || !g.teams.containsKey(p.getUniqueId()) ? g.lobby : g.spectatorPoint());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Game g = this.playing(e.getPlayer());
        if (g != null && g.world != null && e.getFrom().equals((Object)g.world) && !e.getPlayer().getWorld().equals((Object)g.world)) {
            g.leave(e.getPlayer(), false);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (this.sheepOf(e.getItemInHand()) != null || !this.canEdit(e.getPlayer(), e.getBlock(), true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        boolean admin = e.getPlayer().getGameMode() == GameMode.CREATIVE && e.getPlayer().hasPermission("eemisheepwars.admin");
        boolean ours = this.isProtected(e.getBlock().getWorld());
        if (!admin && ours && EemiSheepWars.isFixed(e.getBlock().getType())) {
            e.setCancelled(true);
            return;
        }
        if (!this.canEdit(e.getPlayer(), e.getBlock(), false)) {
            e.setCancelled(true);
        } else if (ours) {
            e.setDropItems(false);
        }
    }

    private boolean canEdit(Player p, Block b, boolean place) {
        if (!this.isProtected(b.getWorld())) {
            return true;
        }
        if (p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemisheepwars.admin")) {
            return true;
        }
        Game g = this.gameOf(b.getWorld());
        if (g == null || this.playing(p) != g) {
            return false;
        }
        if (g.state != State.PLAYING || g.cageTimer > 0 || !g.alive.contains(p.getUniqueId())) {
            return false;
        }
        if (!g.inArea(b.getX(), b.getY(), b.getZ())) {
            return false;
        }
        if (!place) {
            return true;
        }
        Side s = g.teams.get(p.getUniqueId());
        return s != null && b.getX() * s.dir() >= 2;
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        boolean left;
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean bl = left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) {
            return;
        }
        ItemStack it = e.getItem();
        if (it == null) {
            return;
        }
        Game g = this.playing(p);
        if (g == null) {
            SheepType t = this.sheepOf(it);
            Game here = this.gameOf(p.getWorld());
            if (t != null && here != null && p.hasPermission("eemisheepwars.admin")) {
                e.setCancelled(true);
                here.launch(p, t, right);
            }
            return;
        }
        if (right && it.getType() == Material.RED_BED && this.isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Tu as quitt\u00e9 Sheep Wars.");
            return;
        }
        SheepType t = this.sheepOf(it);
        if (t == null) {
            return;
        }
        e.setCancelled(true);
        if (g.state != State.PLAYING || g.cageTimer > 0 || !g.alive.contains(p.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.cooldown.getOrDefault(p.getUniqueId(), 0L) < 750L) {
            return;
        }
        this.cooldown.put(p.getUniqueId(), now);
        it.setAmount(it.getAmount() - 1);
        g.launch(p, t, right);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (this.playing(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        Player p;
        HumanEntity humanEntity = e.getEntity();
        if (humanEntity instanceof Player && this.playing(p = (Player)humanEntity) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game g = this.byPlayer.get(p.getUniqueId());
        if (g != null) {
            g.leave(p, g.players.contains(p.getUniqueId()));
        }
        this.cooldown.remove(p.getUniqueId());
    }

    @EventHandler
    public void onSpectatorWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        for (Game g : this.games) {
            if (!g.spectators.contains(p.getUniqueId()) || g.world != null && p.getWorld().equals((Object)g.world)) continue;
            g.spectators.remove(p.getUniqueId());
            if (!this.mayTouch(p)) continue;
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onSpectatorQuit(PlayerQuitEvent e) {
        boolean was = false;
        for (Game g : this.games) {
            was |= g.spectators.remove(e.getPlayer().getUniqueId());
        }
        if (was) {
            this.sendToHub(e.getPlayer());
        }
    }

    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore")) {
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)("eemi " + args));
        }
    }

    private void reportStatus() {
        boolean anyWait = false;
        boolean anyPlay = false;
        long waiting = 0L;
        int inGame = 0;
        for (Game g : this.games) {
            boolean play;
            if (g.destroyed) continue;
            boolean bl = play = g.state == State.PLAYING || g.state == State.ENDING;
            if (g.preparing || !play) {
                anyWait = true;
            } else {
                anyPlay = true;
            }
            waiting += (long)g.pending.size() + g.players.stream().filter(u -> !g.teams.containsKey(u)).count();
            if (!play) continue;
            inGame += g.alive.size();
        }
        String st = anyWait ? "waiting" : (anyPlay ? "playing" : "waiting");
        this.eemi("status sheepwars " + st + " " + waiting + " " + inGame + " " + this.cfg("max-players", 12) * this.maxInstances());
    }

    private Game target(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) {
            try {
                return this.game(Integer.parseInt(args[idx]));
            }
            catch (NumberFormatException ex) {
                return null;
            }
        }
        if (sender instanceof Player) {
            Player p = (Player)sender;
            Game g = this.playing(p);
            if (g != null) {
                return g;
            }
            g = this.gameOf(p.getWorld());
            if (g != null) {
                return g;
            }
        }
        return this.game(1);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player pl;
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player ? (pl = (Player)sender) : null;
        boolean admin = sender.hasPermission("eemisheepwars.admin");
        switch (sub) {
            case "join": {
                Player target;
                Player player = target = args.length > 1 && admin ? Bukkit.getPlayerExact((String)args[1]) : p;
                if (target == null) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cJoueur introuvable.");
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
                    return true;
                }
                g.leave(p, g.players.contains(p.getUniqueId()));
                p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Tu as quitt\u00e9 Sheep Wars.");
                break;
            }
            case "spectate": 
            case "spec": {
                if (p == null) {
                    return true;
                }
                if (this.byPlayer.containsKey(p.getUniqueId())) {
                    p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cTu es inscrit : /sheepwars leave d'abord.");
                    return true;
                }
                Game g = null;
                if (args.length > 1) {
                    Player who = Bukkit.getPlayerExact((String)args[1]);
                    Game game = g = who == null ? null : this.playing(who);
                    if (g == null || g.state != State.PLAYING) {
                        p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cCe joueur n'est pas dans une partie en cours.");
                        return true;
                    }
                } else {
                    for (Game c : this.games) {
                        if (c.destroyed || c.state != State.PLAYING || c.world == null || g != null && c.alive.size() <= g.alive.size()) continue;
                        g = c;
                    }
                    if (g == null) {
                        p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cAucune partie en cours.");
                        return true;
                    }
                }
                for (Game c : this.games) {
                    c.spectators.remove(p.getUniqueId());
                }
                g.spectate(p);
                break;
            }
            case "start": {
                if (!admin) {
                    return true;
                }
                Game g = this.target(sender, args, 1);
                if (g == null || g.world == null) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cPartie introuvable.");
                    return true;
                }
                if (g.state != State.WAITING && g.state != State.STARTING) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cLa partie " + g.n + " est d\u00e9j\u00e0 en cours.");
                    return true;
                }
                if (g.players.size() < 2) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cIl faut au moins 2 joueurs inscrits dans la partie " + g.n + ".");
                    return true;
                }
                if (g.busy()) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cLa map est en construction.");
                    return true;
                }
                g.startGame();
                break;
            }
            case "stop": {
                if (!admin) {
                    return true;
                }
                Game g = this.target(sender, args, 1);
                if (g == null) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cPartie introuvable.");
                    return true;
                }
                g.forceStop();
                sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Partie " + g.n + " arr\u00eat\u00e9e.");
                break;
            }
            case "parties": 
            case "games": {
                if (!admin) {
                    return true;
                }
                sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Parties (" + this.games.size() + "/" + this.maxInstances() + ") :");
                for (Game g : this.games) {
                    sender.sendMessage("\u00a77 #" + g.n + " \u00a7f" + (g.world == null ? WorldClone.name(this.base, g.n) : g.world.getName()) + " \u00a77- " + g.stateLabel() + " - map \u00a7e" + g.mapName() + " \u00a77- joueurs \u00a7f" + g.players.size() + (String)(g.pending.isEmpty() ? "" : " \u00a77(+" + g.pending.size() + " en attente)") + (String)(g.state == State.PLAYING ? " \u00a77- en vie \u00a7f" + g.alive.size() : ""));
                }
                sender.sendMessage("\u00a77 Mod\u00e8le : \u00a7f" + this.modeleName() + " \u00a77- " + (String)(this.modeleBusy ? "en copie" : (this.modele == null ? "absent" : "pr\u00eat (map " + this.modeleMap + ")")));
                break;
            }
            case "build": {
                if (!admin) {
                    return true;
                }
                Game g = this.game(1);
                if (g == null || g.world == null) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cMonde introuvable.");
                    return true;
                }
                if (g.state == State.PLAYING || g.state == State.ENDING) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cAttends la fin de la partie 1.");
                    return true;
                }
                if (g.building) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cLa map est d\u00e9j\u00e0 en construction.");
                    return true;
                }
                if (this.preparingCopies()) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cUne copie est en pr\u00e9paration, r\u00e9essaie dans quelques secondes.");
                    return true;
                }
                sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Construction de la map (partie 1), puis copie du mod\u00e8le...");
                g.remakeModele = true;
                g.rebuild(sender, this.pickMap(g, false));
                break;
            }
            case "map": 
            case "maps": {
                if (!admin) {
                    return true;
                }
                Game g = this.game(1);
                if (args.length < 2) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Map de la partie 1 : \u00a7e" + (g == null ? "?" : g.mapName()) + " \u00a77- maps : \u00a7f" + String.join((CharSequence)", ", IMPORTED.keySet()));
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77/sheepwars map <id> : impose la prochaine map de la partie 1 (reconstruite tout de suite si aucune partie).");
                    return true;
                }
                String id = args[1].toLowerCase();
                if (!IMPORTED.containsKey(id)) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cMap inconnue. \u00a77" + String.join((CharSequence)", ", IMPORTED.keySet()));
                    return true;
                }
                this.nextMap = id;
                if (!(g == null || g.state != State.WAITING && g.state != State.STARTING || g.building || g.world == null || this.preparingCopies())) {
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Construction de \u00a7e" + IMPORTED.get(id).name() + "\u00a77 (partie 1)...");
                    g.remakeModele = true;
                    g.rebuild(sender, this.pickMap(g, false));
                    break;
                }
                sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Prochaine map de la partie 1 : \u00a7e" + IMPORTED.get(id).name());
                break;
            }
            case "give": {
                if (!admin || p == null || args.length < 2) {
                    return true;
                }
                SheepType t = SheepType.of(args[1].toLowerCase());
                if (t == null) break;
                p.getInventory().addItem(new ItemStack[]{this.sheepItem(t)});
                break;
            }
            case "reload": {
                if (!admin) {
                    return true;
                }
                for (Game g : this.games) {
                    if (g.state != State.PLAYING && g.state != State.ENDING) continue;
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cAttends la fin des parties en cours.");
                    return true;
                }
                this.reloadConfig();
                this.cy = this.cfg("center-y", 100);
                for (Game g : this.games) {
                    if (g.world == null) continue;
                    g.attach(g.world);
                }
                Game first = this.game(1);
                if (first != null && first.world == null) {
                    first.attach(Bukkit.getWorld((String)this.base));
                }
                sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7aConfig recharg\u00e9e.");
                break;
            }
            default: {
                sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77/sheepwars <join|leave|spectate [joueur]>" + (admin ? " \u00a78- admin : start|stop [n], parties, build, map, give, reload" : ""));
            }
        }
        return true;
    }

    private boolean preparingCopies() {
        if (this.modeleBusy) {
            return true;
        }
        for (Game g : this.games) {
            if (!g.preparing || g.destroyed) continue;
            return true;
        }
        return false;
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

    static {
        IMPORTED.put("rose", new Imported("D\u00e9sert rose", -113, 29, 999, -81, 1050, true, -3, 29, new int[][]{{-57, 51, 1050}, {-57, 51, 1052}, {-58, 50, 1054}, {-59, 50, 1056}, {-55, 50, 1048}, {-58, 51, 1041}}, new int[][]{{-102, 50, 1049}, {-104, 51, 1052}, {-104, 51, 1054}, {-106, 50, 1056}, {-103, 51, 1063}}));
        IMPORTED.put("vert", new Imported("\u00celes vertes", -46, 23, 999, -17, 1050, false, -3, 27, new int[][]{{-35, 42, 1050}, {-35, 42, 1048}, {-35, 42, 1052}, {-33, 42, 1050}, {-37, 42, 1050}, {-33, 42, 1048}, {-33, 42, 1052}, {-35, 42, 1046}, {-35, 42, 1054}, {-37, 42, 1047}, {-33, 42, 1054}, {-38, 43, 1052}}, new int[][]{{1, 42, 1048}, {1, 42, 1052}, {0, 42, 1050}, {2, 42, 1046}, {2, 42, 1054}, {-1, 42, 1048}, {-1, 42, 1052}, {0, 42, 1046}, {0, 42, 1054}, {-2, 42, 1050}, {2, 42, 1044}, {2, 42, 1056}}));
        IMPORTED.put("vaisseau", new Imported("Vaisseau", 16, 18, 999, 58, 1050, false, 7, 37, new int[][]{{31, 48, 1050}, {33, 48, 1050}, {32, 48, 1052}, {31, 48, 1048}, {34, 48, 1052}, {31, 48, 1046}, {35, 48, 1050}, {34, 46, 1071}, {32, 46, 1072}, {36, 46, 1071}, {34, 46, 1073}, {36, 46, 1073}}, new int[][]{{83, 42, 1050}, {83, 42, 1052}, {81, 42, 1049}, {81, 42, 1051}, {85, 42, 1051}, {82, 42, 1054}, {81, 42, 1047}, {84, 42, 1054}, {80, 42, 1053}, {86, 42, 1053}, {82, 42, 1056}, {84, 42, 1056}}));
        IMPORTED.put("glacier", new Imported("Pics gel\u00e9s", 0, 0, 0, 46, 56, false, -3, 30, new int[][]{{20, 22, 55}, {20, 22, 57}, {20, 22, 53}, {18, 22, 55}, {22, 22, 55}, {18, 22, 57}, {22, 22, 57}, {20, 22, 59}, {18, 22, 53}, {22, 22, 53}, {18, 22, 59}, {22, 22, 59}}, new int[][]{{72, 22, 55}, {72, 22, 57}, {72, 22, 53}, {74, 22, 55}, {70, 22, 55}, {74, 22, 57}, {70, 22, 57}, {72, 22, 59}, {74, 22, 53}, {70, 22, 53}, {74, 22, 59}, {70, 22, 59}}));
        IMPORTED.put("jade", new Imported("Temple de jade", 0, 0, 0, 46, 56, false, -3, 30, new int[][]{{20, 22, 55}, {20, 22, 57}, {20, 22, 53}, {18, 22, 55}, {22, 22, 55}, {18, 22, 57}, {22, 22, 57}, {20, 22, 59}, {18, 22, 53}, {22, 22, 53}, {18, 22, 59}, {22, 22, 59}}, new int[][]{{72, 22, 55}, {72, 22, 57}, {72, 22, 53}, {74, 22, 55}, {70, 22, 55}, {74, 22, 57}, {70, 22, 57}, {72, 22, 59}, {74, 22, 53}, {70, 22, 53}, {74, 22, 59}, {70, 22, 59}}));
        IMPORTED.put("engrenages", new Imported("Cit\u00e9 des engrenages", 0, 0, 0, 56, 56, false, -3, 34, new int[][]{{30, 26, 55}, {30, 26, 57}, {30, 26, 53}, {28, 26, 55}, {32, 26, 55}, {28, 26, 57}, {32, 26, 57}, {30, 26, 59}, {28, 26, 53}, {32, 26, 53}, {28, 26, 59}, {32, 26, 59}}, new int[][]{{82, 26, 55}, {82, 26, 57}, {82, 26, 53}, {84, 26, 55}, {80, 26, 55}, {84, 26, 57}, {80, 26, 57}, {82, 26, 59}, {84, 26, 53}, {80, 26, 53}, {84, 26, 59}, {80, 26, 59}}));
        // 4.5.0 : maps faites par code (format SWM2, generateur hors serveur dans outil-test/)
        IMPORTED.put("pirates", new Imported("Port pirate", 0, 0, 0, 41, 43, false, 8, 16, new int[][]{{21, 11, 40}, {21, 11, 42}, {21, 11, 44}, {21, 11, 46}, {19, 11, 40}, {19, 11, 42}, {19, 11, 44}, {19, 11, 46}, {17, 11, 40}, {17, 11, 42}, {17, 11, 44}, {17, 11, 46}}, new int[][]{{61, 11, 40}, {61, 11, 42}, {61, 11, 44}, {61, 11, 46}, {63, 11, 40}, {63, 11, 42}, {63, 11, 44}, {63, 11, 46}, {65, 11, 40}, {65, 11, 42}, {65, 11, 44}, {65, 11, 46}}));
        IMPORTED.put("ruines", new Imported("Ruines antiques", 0, 0, 0, 33, 43, false, -3, 24, new int[][]{{13, 18, 40}, {13, 18, 42}, {13, 18, 44}, {13, 18, 46}, {11, 18, 40}, {11, 18, 42}, {11, 18, 44}, {11, 18, 46}, {9, 18, 40}, {9, 18, 42}, {9, 18, 44}, {9, 18, 46}}, new int[][]{{53, 18, 40}, {53, 18, 42}, {53, 18, 44}, {53, 18, 46}, {55, 18, 40}, {55, 18, 42}, {55, 18, 44}, {55, 18, 46}, {57, 18, 40}, {57, 18, 42}, {57, 18, 44}, {57, 18, 46}}));
        IMPORTED.put("spatiale", new Imported("Base spatiale", 0, 0, 0, 37, 39, false, -3, 24, new int[][]{{17, 18, 36}, {17, 18, 38}, {17, 18, 40}, {17, 18, 42}, {15, 18, 36}, {15, 18, 38}, {15, 18, 40}, {15, 18, 42}, {13, 18, 36}, {13, 18, 38}, {13, 18, 40}, {13, 18, 42}}, new int[][]{{57, 18, 36}, {57, 18, 38}, {57, 18, 40}, {57, 18, 42}, {59, 18, 36}, {59, 18, 38}, {59, 18, 40}, {59, 18, 42}, {61, 18, 36}, {61, 18, 38}, {61, 18, 40}, {61, 18, 42}}));
        NONE = Material.AIR.createBlockData();
    }

    final class Game {
        final int n;
        World world;
        Location lobby;
        boolean preparing;
        boolean destroyed;
        boolean remakeModele;
        final Set<UUID> pending = new LinkedHashSet<UUID>();
        final Set<UUID> players = new LinkedHashSet<UUID>();
        final Map<UUID, Side> teams = new HashMap<UUID, Side>();
        final Set<UUID> alive = new LinkedHashSet<UUID>();
        final Map<UUID, Integer> kills = new HashMap<UUID, Integer>();
        final Map<UUID, UUID> lastHit = new HashMap<UUID, UUID>();
        final Map<UUID, Long> lastHitTime = new HashMap<UUID, Long>();
        final Map<UUID, Launched> sheep = new HashMap<UUID, Launched>();
        final Map<UUID, Scoreboard> boards = new HashMap<UUID, Scoreboard>();
        final Set<UUID> spectators = new HashSet<UUID>();
        final Map<Side, Integer> sheepTimer = new EnumMap<Side, Integer>(Side.class);
        final Map<Side, Map<String, Long>> buffs = new EnumMap<Side, Map<String, Long>>(Side.class);
        State state = State.WAITING;
        int timer;
        int cageTimer;
        int magicTimer;
        Block magicWool;
        int magicColor;
        int magicLife;
        boolean building;
        final List<Block> cages = new ArrayList<Block>();
        String mapId = "rose";
        int aMinX = -60;
        int aMaxX = 60;
        int aMinY;
        int aMaxY;
        int aMinZ = -58;
        int aMaxZ = 58;

        Game(int n) {
            this.n = n;
        }

        void attach(World w) {
            this.world = w;
            this.lobby = w == null ? null : new Location(w, 0.5, (double)(EemiSheepWars.this.cfg("lobby-y", 140) + 1), 0.5);
        }

        int size() {
            return this.players.size() + this.pending.size();
        }

        boolean busy() {
            return this.building || this.preparing || this.n == 1 && EemiSheepWars.this.modeleBusy;
        }

        void restoreArea() {
            int[] a = EemiSheepWars.this.areaCache.get(this.mapId);
            if (a == null) {
                return;
            }
            this.aMinX = a[0];
            this.aMaxX = a[1];
            this.aMinY = a[2];
            this.aMaxY = a[3];
            this.aMinZ = a[4];
            this.aMaxZ = a[5];
        }

        Location spawnSlot(Side s, int i) {
            Imported m = this.cur();
            int[][] pts = s == Side.ROUGE ? m.red() : m.blue();
            int[] o = pts[i % pts.length];
            int[] p = EemiSheepWars.this.toWorld(m, o[0], o[1], o[2]);
            Location l = new Location(this.world, (double)p[0] + 0.5, (double)(p[1] + 1), (double)p[2] + 0.5);
            l.setYaw(s == Side.ROUGE ? -90.0f : 90.0f);
            return l;
        }

        boolean inArea(int x, int y, int z) {
            return x >= this.aMinX && x <= this.aMaxX && z >= this.aMinZ && z <= this.aMaxZ && y >= this.aMinY && y <= this.aMaxY;
        }

        boolean buff(Side s, String key) {
            if (s == null) {
                return false;
            }
            Long until = (Long)this.buffs.computeIfAbsent(s, k -> new HashMap()).get(key);
            return until != null && until > System.currentTimeMillis();
        }

        void setBuff(Side s, String key, int seconds) {
            this.buffs.computeIfAbsent(s, k -> new HashMap()).put(key, System.currentTimeMillis() + (long)seconds * 1000L);
        }

        void tick() {
            if (this.preparing || this.world == null) {
                return;
            }
            switch (this.state.ordinal()) {
                case 0: {
                    if (this.busy() || this.players.size() < EemiSheepWars.this.cfg("min-players", 2)) break;
                    this.state = State.STARTING;
                    this.timer = EemiSheepWars.this.cfg("countdown", 30);
                    this.broadcast("\u00a7eAssez de joueurs : la partie commence dans \u00a76" + this.timer + " s\u00a7e.");
                    EemiSheepWars.this.reportStatus();
                    break;
                }
                case 1: {
                    if (this.players.size() < EemiSheepWars.this.cfg("min-players", 2)) {
                        this.state = State.WAITING;
                        this.broadcast("\u00a7cPas assez de joueurs, lancement annul\u00e9.");
                        EemiSheepWars.this.reportStatus();
                        break;
                    }
                    if (this.players.size() >= EemiSheepWars.this.cfg("max-players", 12)) {
                        this.timer = Math.min(this.timer, EemiSheepWars.this.cfg("full-countdown", 10));
                    }
                    if (this.timer <= 5 && this.timer > 0) {
                        for (Player p : EemiSheepWars.this.online(this.players)) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        }
                    }
                    if (this.timer <= 0) {
                        if (this.busy()) break;
                        this.startGame();
                        break;
                    }
                    --this.timer;
                    break;
                }
                case 2: {
                    int b;
                    if (this.cageTimer > 0) {
                        if (--this.cageTimer == 0) {
                            this.openCages();
                            break;
                        }
                        for (Player p : EemiSheepWars.this.online(this.alive)) {
                            p.sendActionBar(Component.text((String)"Les cages s'ouvrent dans ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.cageTimer + " s"), (TextColor)NamedTextColor.YELLOW)));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.4f);
                        }
                        break;
                    }
                    --this.timer;
                    for (Side s : Side.values()) {
                        int t = this.sheepTimer.getOrDefault((Object)s, 1) - 1;
                        if (t <= 0) {
                            for (Player p : EemiSheepWars.this.online(this.alive)) {
                                if (this.teams.get(p.getUniqueId()) != s) continue;
                                EemiSheepWars.this.giveSheep(p, 1);
                                this.refill(p);
                            }
                            t = EemiSheepWars.this.sheepDelay(this.count(s));
                        }
                        this.sheepTimer.put(s, t);
                    }
                    if (--this.magicTimer <= 0) {
                        this.magicTimer = EemiSheepWars.this.cfg("magic-wool-interval", 30);
                        this.spawnMagicWool();
                    }
                    if (this.magicWool != null && --this.magicLife <= 0) {
                        this.removeMagicWool();
                    }
                    if (this.timer > 0) break;
                    int r = this.count(Side.ROUGE);
                    this.endGame(r == (b = this.count(Side.BLEU)) ? null : (r > b ? Side.ROUGE : Side.BLEU), "Temps \u00e9coul\u00e9 !");
                    break;
                }
                case 3: {
                    if (--this.timer > 0) break;
                    this.backToLobby();
                }
            }
            if (!this.destroyed) {
                for (Player p : EemiSheepWars.this.online(this.players)) {
                    this.updateSidebar(p);
                }
            }
        }

        int count(Side s) {
            int c = 0;
            for (UUID id : this.alive) {
                if (this.teams.get(id) != s) continue;
                ++c;
            }
            return c;
        }

        Scoreboard board(Player p) {
            Scoreboard b = this.boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("sheepwars", Criteria.DUMMY, ((TextComponent)Component.text((String)"SHEEP ", (TextColor)NamedTextColor.WHITE).decorate(TextDecoration.BOLD)).append(Component.text((String)"WARS", (TextColor)NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                this.boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) {
                p.setScoreboard(b);
            }
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = this.board(p);
            Objective o = b.getObjective("sheepwars");
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
            ArrayList<Component> l = new ArrayList<Component>();
            l.add((Component)Component.text((String)" "));
            l.add(Component.text((String)"Partie : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)("#" + this.n), (TextColor)NamedTextColor.WHITE)));
            l.add(Component.text((String)"Map : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.building ? "..." : this.mapName()), (TextColor)NamedTextColor.YELLOW)));
            switch (this.state.ordinal()) {
                case 0: 
                case 1: {
                    l.add(Component.text((String)"Joueurs : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.players.size() + "/" + EemiSheepWars.this.cfg("max-players", 12)), (TextColor)NamedTextColor.WHITE)));
                    l.add(Component.text((String)"Minimum : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.valueOf(EemiSheepWars.this.cfg("min-players", 2)), (TextColor)NamedTextColor.WHITE)));
                    l.add((Component)Component.text((String)"  "));
                    if (this.building) {
                        l.add((Component)Component.text((String)"Construction de la map...", (TextColor)NamedTextColor.GOLD));
                        break;
                    }
                    if (this.state == State.STARTING) {
                        l.add(Component.text((String)"D\u00e9but dans ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(this.timer + " s"), (TextColor)NamedTextColor.GOLD)));
                        break;
                    }
                    l.add((Component)Component.text((String)"En attente...", (TextColor)NamedTextColor.YELLOW));
                    break;
                }
                case 2: 
                case 3: {
                    l.add(Component.text((String)"Rouge : ", (TextColor)NamedTextColor.RED).append((Component)Component.text((String)(this.count(Side.ROUGE) + " en vie"), (TextColor)NamedTextColor.WHITE)));
                    l.add(Component.text((String)"Bleu : ", (TextColor)NamedTextColor.BLUE).append((Component)Component.text((String)(this.count(Side.BLEU) + " en vie"), (TextColor)NamedTextColor.WHITE)));
                    l.add((Component)Component.text((String)"  "));
                    Side s = this.teams.get(p.getUniqueId());
                    if (s != null) {
                        l.add(Component.text((String)"\u00c9quipe : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)s.fem(), (TextColor)s.color)));
                    }
                    l.add(Component.text((String)"Kills : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.valueOf(this.kills.getOrDefault(p.getUniqueId(), 0)), (TextColor)NamedTextColor.WHITE)));
                    if (this.state == State.PLAYING && s != null && this.cageTimer == 0) {
                        l.add(Component.text((String)"Mouton dans : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(Math.max(0, this.sheepTimer.getOrDefault((Object)s, 0)) + " s"), (TextColor)NamedTextColor.YELLOW)));
                    }
                    l.add(Component.text((String)"Temps : ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)String.format("%d:%02d", Math.max(0, this.timer) / 60, Math.max(0, this.timer) % 60), (TextColor)NamedTextColor.WHITE)));
                    if (s == null) break;
                    ArrayList<String> active = new ArrayList<String>();
                    if (this.buff(s, "big")) {
                        active.add("explosions+");
                    }
                    if (this.buff(s, "arrows")) {
                        active.add("fl\u00e8ches explosives");
                    }
                    if (this.buff(s, "punch")) {
                        active.add("recul");
                    }
                    if (this.buff(s, "fast")) {
                        active.add("moutons rapides");
                    }
                    if (this.buff(s, "slow")) {
                        active.add("moutons lents");
                    }
                    if (this.buff(s, "nobow")) {
                        active.add("arcs bloqu\u00e9s");
                    }
                    if (active.isEmpty()) break;
                    l.add((Component)Component.text((String)String.join((CharSequence)", ", active), (TextColor)NamedTextColor.LIGHT_PURPLE));
                }
            }
            l.add((Component)Component.text((String)"   "));
            l.add((Component)Component.text((String)"eemicraft", (TextColor)NamedTextColor.DARK_GRAY));
            this.setLines(p, l);
        }

        void colorNames() {
            for (Player viewer : EemiSheepWars.this.online(this.players)) {
                Scoreboard b = this.board(viewer);
                for (Side s : Side.values()) {
                    Team t = b.getTeam("sw_" + s.key());
                    if (t == null) {
                        t = b.registerNewTeam("sw_" + s.key());
                        t.color(s.color);
                        t.prefix((Component)Component.text((String)("[" + s.label + "] "), (TextColor)s.color));
                        t.setAllowFriendlyFire(false);
                    }
                    for (String e : new ArrayList<String>(t.getEntries())) {
                        t.removeEntry(e);
                    }
                }
                for (Map.Entry entry : this.teams.entrySet()) {
                    Player p = Bukkit.getPlayer((UUID)((UUID)entry.getKey()));
                    if (p == null) continue;
                    b.getTeam("sw_" + ((Side)((Object)entry.getValue())).key()).addEntry(p.getName());
                }
            }
        }

        void clearNames() {
            for (Scoreboard b : this.boards.values()) {
                for (Side s : Side.values()) {
                    Team t = b.getTeam("sw_" + s.key());
                    if (t == null) continue;
                    t.unregister();
                }
            }
        }

        void addPlayer(Player p) {
            UUID id = p.getUniqueId();
            this.spectators.remove(id);
            this.players.add(id);
            EemiSheepWars.this.byPlayer.put(id, this);
            p.teleport(this.lobby);
            EemiSheepWars.this.later(() -> this.prepareLobbyPlayer(p));
            this.broadcast("\u00a7a" + p.getName() + " \u00a77a rejoint Sheep Wars (\u00a7f" + this.players.size() + "\u00a77).");
            if (this.state == State.PLAYING || this.state == State.ENDING) {
                p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Une partie est en cours, tu joueras \u00e0 la suivante.");
            }
            EemiSheepWars.this.reportStatus();
        }

        void prepareLobbyPlayer(Player p) {
            if (!(p.isOnline() && this.players.contains(p.getUniqueId()) && EemiSheepWars.this.mayTouch(p))) {
                return;
            }
            EemiSheepWars.this.resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, EemiSheepWars.this.kit(new ItemStack(Material.RED_BED), "Quitter Sheep Wars"));
            this.updateSidebar(p);
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (this.pending.remove(id)) {
                EemiSheepWars.this.byPlayer.remove(id);
                EemiSheepWars.this.reportStatus();
                return;
            }
            if (!this.players.remove(id)) {
                return;
            }
            EemiSheepWars.this.byPlayer.remove(id);
            Side side = this.teams.remove(id);
            boolean wasAlive = this.alive.remove(id);
            this.boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) {
                EemiSheepWars.this.sendToHub(p);
            }
            this.colorNames();
            if (this.state == State.PLAYING && side != null && wasAlive) {
                this.broadcast("\u00a77" + p.getName() + " a quitt\u00e9 la partie.");
                this.checkWin();
            }
            EemiSheepWars.this.reportStatus();
        }

        void startGame() {
            List<Player> list = EemiSheepWars.this.online(this.players);
            if (list.size() < 2 || this.busy()) {
                this.state = State.WAITING;
                EemiSheepWars.this.reportStatus();
                return;
            }
            Collections.shuffle(list);
            this.teams.clear();
            this.alive.clear();
            this.kills.clear();
            this.lastHit.clear();
            this.lastHitTime.clear();
            this.buffs.clear();
            for (int i = 0; i < list.size(); ++i) {
                this.teams.put(list.get(i).getUniqueId(), i % 2 == 0 ? Side.ROUGE : Side.BLEU);
                this.alive.add(list.get(i).getUniqueId());
            }
            this.colorNames();
            this.state = State.PLAYING;
            this.timer = EemiSheepWars.this.cfg("game-time", 600);
            this.cageTimer = EemiSheepWars.this.cfg("cage-time", 5);
            this.magicTimer = EemiSheepWars.this.cfg("magic-wool-interval", 30);
            for (Side s : Side.values()) {
                this.sheepTimer.put(s, EemiSheepWars.this.sheepDelay(this.count(s)));
            }
            EnumMap<Side, Integer> slot = new EnumMap<Side, Integer>(Side.class);
            for (Player p : list) {
                Side s;
                s = this.teams.get(p.getUniqueId());
                int i = slot.merge(s, 1, Integer::sum) - 1;
                Location at = this.spawnSlot(s, i);
                this.buildCage(at);
                p.teleport(at);
                EemiSheepWars.this.later(() -> this.equip(p));
                p.showTitle(Title.title((Component)Component.text((String)("\u00c9quipe " + s.fem()), (TextColor)s.color).decorate(TextDecoration.BOLD), (Component)Component.text((String)"\u00c9limine l'\u00e9quipe adverse", (TextColor)NamedTextColor.GRAY), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(300L), (Duration)Duration.ofSeconds(Math.max(2, EemiSheepWars.this.cfg("cage-time", 5))), (Duration)Duration.ofMillis(500L))));
            }
            this.broadcast("\u00a77Map : \u00a7e" + this.mapName());
            this.broadcast("\u00a7eLa partie commence ! \u00a77Clic gauche = tir droit, clic droit = tir en cloche. Tire sur la \u00a7dlaine magique \u00a77\u00e0 l'arc pour des bonus. Une seule vie !");
            EemiSheepWars.this.reportStatus();
        }

        void buildCage(Location at) {
            Block b0 = at.getBlock();
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dy = 0; dy <= 3; ++dy) {
                        Block b;
                        boolean wall;
                        boolean bl = wall = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == 3;
                        if (!wall || !(b = b0.getRelative(dx, dy, dz)).getType().isAir()) continue;
                        b.setType(Material.GLASS, false);
                        this.cages.add(b);
                    }
                }
            }
        }

        void openCages() {
            for (Block b : this.cages) {
                if (b.getType() != Material.GLASS) continue;
                b.setType(Material.AIR, false);
            }
            this.cages.clear();
            for (Player p : EemiSheepWars.this.online(this.alive)) {
                p.showTitle(Title.title((Component)Component.text((String)"GO !", (TextColor)NamedTextColor.GREEN).decorate(TextDecoration.BOLD), (Component)Component.text((String)"Lance tes moutons sur le camp adverse", (TextColor)NamedTextColor.GRAY), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofSeconds(2L), (Duration)Duration.ofMillis(500L))));
                p.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1.0f, 1.0f);
            }
        }

        void equip(Player p) {
            Side s = this.teams.get(p.getUniqueId());
            if (!p.isOnline() || s == null || this.state != State.PLAYING || !EemiSheepWars.this.mayTouch(p)) {
                return;
            }
            EemiSheepWars.this.resetPlayer(p, GameMode.SURVIVAL);
            PlayerInventory inv = p.getInventory();
            inv.setHelmet(EemiSheepWars.this.armor(Material.LEATHER_HELMET, s));
            inv.setChestplate(EemiSheepWars.this.armor(Material.LEATHER_CHESTPLATE, s));
            inv.setLeggings(EemiSheepWars.this.armor(Material.LEATHER_LEGGINGS, s));
            inv.setBoots(EemiSheepWars.this.armor(Material.LEATHER_BOOTS, s));
            inv.setItem(0, EemiSheepWars.this.unbreakable(EemiSheepWars.this.kit(new ItemStack(Material.STONE_SWORD), null)));
            inv.setItem(1, EemiSheepWars.this.unbreakable(EemiSheepWars.this.kit(new ItemStack(Material.BOW), null)));
            inv.setItem(2, EemiSheepWars.this.kit(new ItemStack(s.wool, 32), null));
            inv.setItem(8, EemiSheepWars.this.kit(new ItemStack(Material.ARROW, 16), null));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, -1, 2, false, false, false));
            EemiSheepWars.this.giveSheep(p, EemiSheepWars.this.cfg("start-sheep", 2));
        }

        void refill(Player p) {
            Side s = this.teams.get(p.getUniqueId());
            if (s == null) {
                return;
            }
            EemiSheepWars.this.top(p, s.wool, 4, 64);
            EemiSheepWars.this.top(p, Material.ARROW, 2, 32);
        }

        void eliminate(Player p, String msg) {
            UUID id = p.getUniqueId();
            if (!this.alive.remove(id)) {
                return;
            }
            this.broadcast(msg);
            p.showTitle(Title.title((Component)Component.text((String)"\u00c9limin\u00e9", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD), (Component)Component.text((String)"Tu regardes la fin de la partie", (TextColor)NamedTextColor.GRAY)));
            this.checkWin();
            EemiSheepWars.this.reportStatus();
        }

        void checkWin() {
            if (this.state != State.PLAYING) {
                return;
            }
            int r = this.count(Side.ROUGE);
            int b = this.count(Side.BLEU);
            if (r == 0 && b == 0) {
                this.endGame(null, "Plus personne en vie !");
            } else if (r == 0) {
                this.endGame(Side.BLEU, "L'\u00e9quipe Rouge est \u00e9limin\u00e9e !");
            } else if (b == 0) {
                this.endGame(Side.ROUGE, "L'\u00e9quipe Bleue est \u00e9limin\u00e9e !");
            }
        }

        void endGame(Side winner, String reason) {
            this.endGame(winner, reason, true);
        }

        void endGame(Side winner, String reason, boolean rewards) {
            this.state = State.ENDING;
            if (rewards) {
                for (Map.Entry<UUID, Side> en : this.teams.entrySet()) {
                    Player p = Bukkit.getPlayer((UUID)en.getKey());
                    if (p == null) continue;
                    EemiSheepWars.this.eemi("reward " + p.getName() + " sheepwars play");
                    if (en.getValue() != winner) continue;
                    EemiSheepWars.this.eemi("reward " + p.getName() + " sheepwars win");
                }
            }
            this.timer = EemiSheepWars.this.cfg("end-time", 8);
            this.removeMagicWool();
            Component title = winner == null ? Component.text((String)"\u00c9galit\u00e9 !", (TextColor)NamedTextColor.YELLOW).decorate(TextDecoration.BOLD) : Component.text((String)("Victoire " + winner.label + " !"), (TextColor)winner.color).decorate(TextDecoration.BOLD);
            for (Player p : EemiSheepWars.this.online(this.players)) {
                if (!this.teams.containsKey(p.getUniqueId())) continue;
                p.showTitle(Title.title((Component)title, (Component)Component.text((String)reason, (TextColor)NamedTextColor.GRAY)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                if (p.getGameMode() != GameMode.SURVIVAL || !EemiSheepWars.this.mayTouch(p)) continue;
                p.setGameMode(GameMode.ADVENTURE);
            }
            this.broadcast("\u00a76" + reason);
            EemiSheepWars.this.reportStatus();
        }

        void backToLobby() {
            this.clearNames();
            for (UUID id : new ArrayList<UUID>(this.teams.keySet())) {
                Player p = Bukkit.getPlayer((UUID)id);
                if (p == null || !this.players.contains(id)) continue;
                p.teleport(this.lobby);
                EemiSheepWars.this.later(() -> this.prepareLobbyPlayer(p));
            }
            this.teams.clear();
            this.alive.clear();
            this.cages.clear();
            this.state = State.WAITING;
            this.sendSpectatorsToHub();
            EemiSheepWars.this.reportStatus();
            if (this.n > 1 && EemiSheepWars.this.online(this.players).isEmpty()) {
                EemiSheepWars.this.destroyGame(this);
                return;
            }
            Bukkit.getScheduler().runTaskLater((Plugin)EemiSheepWars.this, () -> {
                if (!this.destroyed) {
                    this.rebuild(null, EemiSheepWars.this.pickMap(this, false));
                }
            }, 20L);
        }

        void forceStop() {
            if (this.state == State.PLAYING) {
                this.endGame(null, "Partie arr\u00eat\u00e9e par un admin.", false);
            }
            if (this.state == State.ENDING) {
                this.backToLobby();
            }
            if (!this.destroyed) {
                this.state = State.WAITING;
            }
            EemiSheepWars.this.reportStatus();
        }

        void shutdown() {
            this.sendSpectatorsToHub();
            for (UUID id : new ArrayList<UUID>(this.sheep.keySet())) {
                Entity e = Bukkit.getEntity((UUID)id);
                if (e == null) continue;
                e.remove();
            }
            this.sheep.clear();
            this.removeMagicWool();
            for (Player p : EemiSheepWars.this.online(this.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                EemiSheepWars.this.sendToHub(p);
            }
            for (UUID id : this.players) {
                EemiSheepWars.this.byPlayer.remove(id);
            }
            for (UUID id : this.pending) {
                EemiSheepWars.this.byPlayer.remove(id);
            }
            this.players.clear();
            this.pending.clear();
        }

        void spawnMagicWool() {
            this.removeMagicWool();
            this.magicWool = this.world.getBlockAt(0, EemiSheepWars.this.baseY() + this.cur().magicRel() + EemiSheepWars.this.random.nextInt(3), (EemiSheepWars.this.random.nextBoolean() ? 1 : -1) * (10 + EemiSheepWars.this.random.nextInt(5)));
            this.magicColor = 0;
            this.magicLife = EemiSheepWars.this.cfg("magic-wool-life", 20);
            this.magicWool.setType(Material.valueOf((String)(MAGIC_ORDER[0].name() + "_WOOL")), false);
            this.world.playSound(this.magicWool.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.5f);
            this.broadcast("\u00a7dLa laine magique est apparue ! \u00a77Tire dessus \u00e0 l'arc : la couleur donne le bonus.");
        }

        void removeMagicWool() {
            if (this.magicWool != null && this.magicWool.getType().name().endsWith("_WOOL")) {
                this.magicWool.setType(Material.AIR, false);
            }
            this.magicWool = null;
        }

        void cycleMagicWool() {
            if (this.magicWool == null) {
                return;
            }
            this.magicColor = (this.magicColor + 1) % MAGIC_ORDER.length;
            this.magicWool.setType(Material.valueOf((String)(MAGIC_ORDER[this.magicColor].name() + "_WOOL")), false);
            this.world.spawnParticle(Particle.FLAME, this.magicWool.getLocation().add(0.5, 0.5, 0.5), 6, 0.4, 0.4, 0.4, 0.01);
        }

        void magicHit(Player shooter, DyeColor color) {
            Side s = this.teams.get(shooter.getUniqueId());
            if (s == null) {
                return;
            }
            this.removeMagicWool();
            this.broadcast(s.code() + shooter.getName() + " \u00a77a touch\u00e9 la laine magique : \u00a7d" + (switch (color) {
                case DyeColor.ORANGE -> {
                    this.setBuff(s, "big", 15);
                    yield "explosions plus grosses (15 s)";
                }
                case DyeColor.LIGHT_BLUE -> {
                    this.setBuff(s.other(), "slow", 10);
                    yield "moutons ennemis ralentis (10 s)";
                }
                case DyeColor.PINK -> {
                    for (Player p : this.teamAlive(s)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
                    }
                    yield "r\u00e9g\u00e9n\u00e9ration (10 s)";
                }
                case DyeColor.GRAY -> {
                    for (Player p : this.teamAlive(s.other())) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                    }
                    yield "aveuglement des ennemis (5 s)";
                }
                case DyeColor.LIGHT_GRAY -> {
                    this.setBuff(s, "punch", 20);
                    yield "fl\u00e8ches \u00e0 recul (20 s)";
                }
                case DyeColor.CYAN -> {
                    this.setBuff(s, "fast", 10);
                    yield "moutons plus rapides (10 s)";
                }
                case DyeColor.BLUE -> {
                    this.setBuff(s.other(), "nobow", 10);
                    yield "arcs ennemis bloqu\u00e9s (10 s)";
                }
                case DyeColor.GREEN -> {
                    for (Player p : this.teamAlive(s.other())) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                    }
                    yield "poison sur les ennemis (3 s)";
                }
                case DyeColor.RED -> {
                    this.setBuff(s, "arrows", 15);
                    yield "fl\u00e8ches explosives (15 s)";
                }
                default -> {
                    for (Player p : this.teamAlive(s)) {
                        EemiSheepWars.this.giveSheep(p, 1);
                    }
                    yield "+1 mouton pour l'\u00e9quipe";
                }
            }) + "\u00a77 pour " + s.code() + "l'\u00e9quipe " + s.fem() + "\u00a77.");
            for (Player p : EemiSheepWars.this.online(this.players)) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
            }
        }

        List<Player> teamAlive(Side s) {
            ArrayList<Player> list = new ArrayList<Player>();
            for (Player p : EemiSheepWars.this.online(this.alive)) {
                if (this.teams.get(p.getUniqueId()) != s) continue;
                list.add(p);
            }
            return list;
        }

        void launch(Player p, SheepType t, boolean gravity) {
            double speed;
            Side s = this.teams.get(p.getUniqueId());
            double d = speed = gravity ? EemiSheepWars.this.getConfig().getDouble("gravity-speed", 2.0) : EemiSheepWars.this.getConfig().getDouble("straight-speed", 1.3);
            if (this.buff(s, "fast")) {
                speed *= 1.3;
            }
            if (this.buff(s, "slow")) {
                speed *= 0.7;
            }
            Vector v = p.getLocation().getDirection().multiply(speed);
            Snowball ball = (Snowball)p.launchProjectile(Snowball.class, v);
            ball.setItem(new ItemStack(t.item));
            if (!gravity) {
                ball.setGravity(false);
            }
            ball.getPersistentDataContainer().set(EemiSheepWars.this.ballKey, PersistentDataType.BYTE, (byte)1);
            Sheep sh = (Sheep)this.world.spawn(ball.getLocation(), Sheep.class, e -> {
                e.setColor(t.dye);
                e.customName((Component)Component.text((String)t.label, (TextColor)TextColor.color((int)t.rgb)));
                e.setCustomNameVisible(true);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
                double health = Math.max(1.0, EemiSheepWars.this.getConfig().getDouble("sheep-health", 20.0));
                AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) {
                    hp.setBaseValue(health);
                }
                e.setHealth(health);
            });
            ball.addPassenger((Entity)sh);
            if (t == SheepType.ABORDAGE) {
                sh.addPassenger((Entity)p);
                p.setFallDistance(0.0f);
            }
            this.sheep.put(sh.getUniqueId(), new Launched(t, s, p.getUniqueId(), gravity));
            this.world.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1.0f, gravity ? 0.7f : 1.1f);
        }

        void arm(Launched l, Entity e) {
            if (l.armed) {
                return;
            }
            l.armed = true;
            for (Entity r : new ArrayList<Entity>(e.getPassengers())) {
                e.removePassenger(r);
                r.setFallDistance(0.0f);
            }
            e.setVelocity(new Vector());
            this.world.playSound(e.getLocation(), Sound.ENTITY_SHEEP_HURT, 1.0f, 1.0f);
            if (l.type.instant || l.type == SheepType.SOIN) {
                l.fuse = 0;
            }
        }

        void sheepTick() {
            if (this.magicWool != null && Bukkit.getCurrentTick() % 10 == 0) {
                this.cycleMagicWool();
            }
            if (this.sheep.isEmpty()) {
                return;
            }
            for (UUID id : new ArrayList<UUID>(this.sheep.keySet())) {
                Launched l = this.sheep.get(id);
                if (l == null) continue;
                Entity e = Bukkit.getEntity((UUID)id);
                ++l.life;
                if (e == null || !e.isValid() || e.getLocation().getY() < (double)this.mapVoidY() || l.life > 600 || this.state != State.PLAYING && l.side != null) {
                    if (e != null) {
                        if (e.getVehicle() != null) {
                            e.getVehicle().remove();
                        }
                        e.remove();
                    }
                    this.sheep.remove(id);
                    continue;
                }
                if (!l.armed) {
                    Entity ball = e.getVehicle();
                    if (ball != null && l.type == SheepType.CHERCHEUR) {
                        this.home(l, ball);
                    }
                    if (ball != null && l.life <= (l.gravityShot ? 200 : 100)) continue;
                    if (ball != null) {
                        ball.remove();
                    }
                    this.arm(l, e);
                    continue;
                }
                Location at = e.getLocation();
                this.world.spawnParticle(Particle.DUST, at.clone().add(0.0, 1.0, 0.0), 3, 0.3, 0.3, 0.3, 0.0, (Object)new Particle.DustOptions(Color.fromRGB((int)l.type.rgb), 1.2f));
                if (!l.type.instant && !e.isOnGround() && ++l.airborne < 100) continue;
                switch (l.type.ordinal()) {
                    case 6: {
                        this.pull(l, at);
                        break;
                    }
                    case 11: {
                        this.poison(l, at);
                        break;
                    }
                }
                if (--l.fuse > 0) continue;
                this.sheep.remove(id);
                this.trigger(l, e);
                e.remove();
            }
        }

        void home(Launched l, Entity ball) {
            Player target = null;
            double best = 1600.0;
            for (Player p : this.enemiesNear(l, ball.getLocation(), 40.0)) {
                double d = p.getLocation().distanceSquared(ball.getLocation());
                if (!(d < best)) continue;
                best = d;
                target = p;
            }
            if (target == null) {
                return;
            }
            Vector to = target.getLocation().add(0.0, 1.0, 0.0).toVector().subtract(ball.getLocation().toVector());
            if (to.lengthSquared() < 0.01) {
                return;
            }
            Vector v = ball.getVelocity();
            double speed = Math.max(1.1, v.length());
            Vector nv = v.normalize().multiply(0.8).add(to.normalize().multiply(0.35)).normalize().multiply(speed);
            ball.setGravity(false);
            ball.setVelocity(nv);
        }

        List<Player> enemiesNear(Launched l, Location at, double r) {
            ArrayList<Player> list = new ArrayList<Player>();
            for (Player p : at.getNearbyPlayers(r)) {
                if (l.side == null) {
                    if (p.getUniqueId().equals(l.owner) || p.getGameMode() != GameMode.SURVIVAL) continue;
                    list.add(p);
                    continue;
                }
                Side s = this.teams.get(p.getUniqueId());
                if (s == null || s == l.side || !this.alive.contains(p.getUniqueId()) || p.getGameMode() != GameMode.SURVIVAL) continue;
                list.add(p);
            }
            return list;
        }

        void pull(Launched l, Location at) {
            Block b;
            this.world.spawnParticle(Particle.PORTAL, at, 20, 2.0, 1.0, 2.0, 0.5);
            for (Player p : this.enemiesNear(l, at, 8.0)) {
                Vector v = at.toVector().subtract(p.getLocation().toVector());
                if (v.lengthSquared() < 1.0) continue;
                p.setVelocity(p.getVelocity().add(v.normalize().multiply(0.12)));
                if (l.fuse % 20 != 0) continue;
                p.showTitle(Title.title((Component)Component.empty(), (Component)Component.text((String)"Tu es aspir\u00e9 par un trou noir !", (TextColor)NamedTextColor.DARK_PURPLE), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofMillis(900L), (Duration)Duration.ZERO)));
            }
            if (l.fuse % 10 == 0 && (b = at.getBlock().getRelative(EemiSheepWars.this.random.nextInt(7) - 3, -1 - EemiSheepWars.this.random.nextInt(2), EemiSheepWars.this.random.nextInt(7) - 3)).getType().isSolid() && !EemiSheepWars.isFixed(b.getType()) && this.inArea(b.getX(), b.getY(), b.getZ())) {
                BlockData data = b.getBlockData();
                b.setType(Material.AIR);
                FallingBlock fb = this.world.spawnFallingBlock(b.getLocation().add(0.5, 0.2, 0.5), data);
                fb.setDropItem(false);
                fb.setVelocity(at.toVector().subtract(b.getLocation().toVector()).normalize().multiply(0.4).setY(0.5));
            }
        }

        void poison(Launched l, Location at) {
            this.world.spawnParticle(Particle.DUST, at.clone().add(0.0, 0.5, 0.0), 25, 3.0, 0.6, 3.0, 0.0, (Object)new Particle.DustOptions(Color.fromRGB((int)3050286), 1.6f));
            if (l.fuse % 20 != 0) {
                return;
            }
            for (Player p : this.enemiesNear(l, at, 4.0)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, 1));
            }
        }

        float power(Launched l, float b) {
            float p = b;
            if (l.gravityShot && (l.type == SheepType.EXPLOSIF || l.type == SheepType.EXPLOSIF_FORT)) {
                p *= 1.25f;
            }
            if (this.buff(l.side, "big")) {
                p *= 1.4f;
            }
            return p;
        }

        void trigger(Launched l, Entity e) {
            Location at = e.getLocation();
            Player owner = Bukkit.getPlayer((UUID)l.owner);
            switch (l.type.ordinal()) {
                case 0: {
                    this.world.createExplosion(at, this.power(l, 3.0f), false, true, (Entity)owner);
                    break;
                }
                case 1: {
                    this.world.createExplosion(at, this.power(l, 4.4f), false, true, (Entity)owner);
                    break;
                }
                case 2: {
                    this.world.createExplosion(at, this.power(l, 2.6f), false, true, (Entity)owner);
                    break;
                }
                case 3: {
                    this.world.createExplosion(at, this.power(l, 2.4f), true, true, (Entity)owner);
                    break;
                }
                case 6: {
                    this.world.createExplosion(at, this.power(l, 1.5f), false, true, (Entity)owner);
                    break;
                }
                case 4: {
                    this.forSphere(at, 3, b -> {
                        if (b.getType().isSolid() && !EemiSheepWars.isFixed(b.getType()) && b.getRelative(0, 1, 0).getType().isAir()) {
                            b.setType(Material.PACKED_ICE);
                        }
                    });
                    this.world.spawnParticle(Particle.SNOWFLAKE, at, 80, 3.0, 1.0, 3.0, 0.05);
                    this.world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
                    for (Player p : this.enemiesNear(l, at, 5.0)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 2));
                    }
                    break;
                }
                case 5: {
                    this.world.spawnParticle(Particle.LARGE_SMOKE, at, 80, 3.0, 1.0, 3.0, 0.02);
                    this.world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.5f);
                    for (Player p : this.enemiesNear(l, at, 6.0)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0));
                    }
                    break;
                }
                case 7: {
                    int bx = at.getBlockX();
                    int by = at.getBlockY();
                    int bz = at.getBlockZ();
                    for (int dx = -3; dx <= 3; ++dx) {
                        for (int dz = -3; dz <= 3; ++dz) {
                            if (dx * dx + dz * dz > 9) continue;
                            for (int dy = -3; dy <= -1; ++dy) {
                                Block b2 = this.world.getBlockAt(bx + dx, by + dy, bz + dz);
                                if (!b2.getType().isSolid() || EemiSheepWars.isFixed(b2.getType()) || !this.inArea(b2.getX(), b2.getY(), b2.getZ())) continue;
                                BlockData data = b2.getBlockData();
                                b2.setType(Material.AIR);
                                FallingBlock fb = this.world.spawnFallingBlock(b2.getLocation().add(0.5, 0.0, 0.5), data);
                                fb.setDropItem(false);
                                fb.setVelocity(new Vector((EemiSheepWars.this.random.nextDouble() - 0.5) * 0.4, 0.4 + EemiSheepWars.this.random.nextDouble() * 0.4, (EemiSheepWars.this.random.nextDouble() - 0.5) * 0.4));
                            }
                        }
                    }
                    this.world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                    for (Player p : this.enemiesNear(l, at, 5.0)) {
                        p.setVelocity(p.getVelocity().setY(1.0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
                        p.showTitle(Title.title((Component)Component.empty(), (Component)Component.text((String)"Tremblement de terre !", (TextColor)NamedTextColor.GOLD), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofSeconds(1L), (Duration)Duration.ofMillis(300L))));
                    }
                    break;
                }
                case 8: {
                    this.world.spawnParticle(Particle.HEART, at.clone().add(0.0, 1.0, 0.0), 15, 2.0, 1.0, 2.0, 0.0);
                    this.world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    for (Player p : at.getNearbyPlayers(6.0)) {
                        if (this.teams.get(p.getUniqueId()) != l.side || !this.alive.contains(p.getUniqueId())) continue;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0));
                    }
                    break;
                }
                case 11: 
                case 13: {
                    break;
                }
                case 10: {
                    this.world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.8f);
                    for (int i = 0; i < 8; ++i) {
                        Location from = at.clone().add((double)(EemiSheepWars.this.random.nextInt(13) - 6), (double)(28 + EemiSheepWars.this.random.nextInt(6)), (double)(EemiSheepWars.this.random.nextInt(13) - 6));
                        Bukkit.getScheduler().runTaskLater((Plugin)EemiSheepWars.this, () -> {
                            if (this.destroyed || this.state != State.PLAYING && l.side != null) {
                                return;
                            }
                            Fireball fb = (Fireball)this.world.spawn(from, Fireball.class, f -> {
                                f.setDirection(new Vector(0, -1, 0));
                                f.setYield(1.8f);
                                f.setIsIncendiary(false);
                                if (owner != null) {
                                    f.setShooter((ProjectileSource)owner);
                                }
                            });
                            fb.setVelocity(new Vector(0.0, -1.2, 0.0));
                        }, 5L * (long)i);
                    }
                    break;
                }
                case 12: {
                    this.world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    this.world.spawnParticle(Particle.PORTAL, at, 120, 3.0, 1.0, 3.0, 1.0);
                    for (Player p : this.enemiesNear(l, at, 5.0)) {
                        Location to = this.safeSpotNear(at, 8);
                        if (to == null) continue;
                        to.setYaw(EemiSheepWars.this.random.nextFloat() * 360.0f);
                        p.teleport(to);
                        p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                    break;
                }
                case 14: {
                    Player target = null;
                    double best = 64.0;
                    for (Player p : this.enemiesNear(l, at, 8.0)) {
                        double d = p.getLocation().distanceSquared(at);
                        if (!(d < best)) continue;
                        best = d;
                        target = p;
                    }
                    if (owner == null || target == null || owner.getGameMode() != GameMode.SURVIVAL || !this.world.equals((Object)owner.getWorld())) {
                        this.world.spawnParticle(Particle.SMOKE, at, 20, 0.5, 0.5, 0.5, 0.02);
                        break;
                    }
                    Location a = owner.getLocation();
                    Location b3 = target.getLocation();
                    owner.teleport(b3);
                    target.teleport(a);
                    owner.setFallDistance(0.0f);
                    target.setFallDistance(0.0f);
                    for (Player p : List.of(owner, target)) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
                        this.world.spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0.0, 1.0, 0.0), 40, 0.4, 0.8, 0.4, 0.05);
                    }
                    break;
                }
                case 9: {
                    this.world.strikeLightning(at);
                    for (int i = 0; i < 2; ++i) {
                        this.world.strikeLightning(at.clone().add((double)(EemiSheepWars.this.random.nextInt(7) - 3), 0.0, (double)(EemiSheepWars.this.random.nextInt(7) - 3)));
                    }
                    break;
                }
            }
        }

        Location safeSpotNear(Location c, int r) {
            for (int i = 0; i < 20; ++i) {
                int x = c.getBlockX() + EemiSheepWars.this.random.nextInt(2 * r + 1) - r;
                int z = c.getBlockZ() + EemiSheepWars.this.random.nextInt(2 * r + 1) - r;
                for (int y = c.getBlockY() + 3; y >= c.getBlockY() - 4; --y) {
                    Block b = this.world.getBlockAt(x, y, z);
                    if (!b.getType().isSolid() || !b.getRelative(0, 1, 0).isPassable() || !b.getRelative(0, 2, 0).isPassable() || !this.inArea(x, y, z)) continue;
                    // 4.5 : jamais dans l'eau ni sous la hauteur de mort (mer du Port pirate)
                    if (b.getRelative(0, 1, 0).isLiquid() || b.getRelative(0, 2, 0).isLiquid() || y + 1 < this.mapVoidY()) continue;
                    return b.getLocation().add(0.5, 1.0, 0.5);
                }
            }
            return null;
        }

        void forSphere(Location c, int r, Consumer<Block> f) {
            for (int dx = -r; dx <= r; ++dx) {
                for (int dy = -r; dy <= r; ++dy) {
                    for (int dz = -r; dz <= r; ++dz) {
                        Block b;
                        if (dx * dx + dy * dy + dz * dz > r * r || !this.inArea((b = c.getBlock().getRelative(dx, dy, dz)).getX(), b.getY(), b.getZ())) continue;
                        f.accept(b);
                    }
                }
            }
        }

        String mapName() {
            Imported m = IMPORTED.get(this.mapId);
            return m == null ? this.mapId : m.name();
        }

        Imported cur() {
            Imported m = IMPORTED.get(this.mapId);
            return m != null ? m : IMPORTED.values().iterator().next();
        }

        int mapVoidY() {
            return EemiSheepWars.this.baseY() + this.cur().voidRel();
        }

        Location spectatorPoint() {
            return new Location(this.world, 0.5, (double)(EemiSheepWars.this.baseY() + this.cur().magicRel() + 6), 0.5, 0.0f, 50.0f);
        }

        void rebuild(CommandSender sender, String id) {
            block7: {
                if (this.world == null || this.building) {
                    return;
                }
                this.building = true;
                try {
                    this.clearEntities();
                    this.removeMagicWool();
                    this.mapId = id;
                    Builder b = new Builder();
                    for (int x = -60; x <= 60; ++x) {
                        for (int z = -58; z <= 58; ++z) {
                            for (int y = EemiSheepWars.this.cy - 35; y <= EemiSheepWars.this.cy + 38; ++y) {
                                b.put(x, y, z, null);
                            }
                        }
                    }
                    this.importedMap(b, this.cur());
                    EemiSheepWars.this.lobbyRoom(b);
                    this.apply(b.ops, 0, sender);
                }
                catch (IOException | RuntimeException ex) {
                    this.building = false;
                    if (this.remakeModele) {
                        this.remakeModele = false;
                        ArrayList<Runnable> todo = new ArrayList<Runnable>(EemiSheepWars.this.modeleWaiters);
                        EemiSheepWars.this.modeleWaiters.clear();
                        todo.forEach(Runnable::run);
                    }
                    EemiSheepWars.this.getLogger().log(Level.SEVERE, "Construction de la map " + this.mapId + " impossible", ex);
                    if (sender == null) break block7;
                    sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7cErreur de construction (voir la console).");
                }
            }
        }

        void apply(List<Op> ops, int from, CommandSender sender) {
            if (this.destroyed) {
                return;
            }
            int end = Math.min(ops.size(), from + 40000);
            for (int i = from; i < end; ++i) {
                Op o = ops.get(i);
                Block b = this.world.getBlockAt(o.x, o.y, o.z);
                if (o.data == null) {
                    if (b.getType().isAir()) continue;
                    b.setType(Material.AIR, false);
                    continue;
                }
                if (b.getBlockData().equals((Object)o.data)) continue;
                b.setBlockData(o.data, false);
            }
            if (end < ops.size()) {
                Bukkit.getScheduler().runTaskLater((Plugin)EemiSheepWars.this, () -> this.apply(ops, end, sender), 1L);
                return;
            }
            this.building = false;
            this.clearEntities();
            EemiSheepWars.this.getLogger().info("Map Sheep Wars construite (" + this.world.getName() + ") : " + this.mapName() + " (" + this.mapId + ", " + ops.size() + " op\u00e9rations).");
            if (sender != null) {
                sender.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a7aMap construite : \u00a7e" + this.mapName());
            }
            if (this.remakeModele) {
                this.remakeModele = false;
                EemiSheepWars.this.remakeModele();
            }
        }

        void importedMap(Builder b, Imported m) throws IOException {
            byte[] raw;
            int l;
            int h;
            int w;
            InputStream res = EemiSheepWars.this.getResource("maps/" + this.mapId + ".swm");
            if (res == null) {
                throw new IOException("maps/" + this.mapId + ".swm absent du jar");
            }
            boolean modern;
            BlockData[] grid;
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(res));){
                String magic = new String(in.readNBytes(4), StandardCharsets.US_ASCII);
                if (magic.equals("SWM1")) {
                    modern = false;
                } else if (magic.equals("SWM2")) {
                    modern = true;
                } else {
                    throw new IOException("format inconnu");
                }
                w = in.readShort();
                h = in.readShort();
                l = in.readShort();
                if (modern) {
                    // 4.5 : palette d'etats modernes (maps faites par code), puis un index (short) par case
                    int n = in.readInt();
                    BlockData[] pal = new BlockData[n];
                    for (int k = 0; k < n; ++k) {
                        pal[k] = EemiSheepWars.this.modernState(in.readUTF());
                    }
                    grid = new BlockData[w * h * l];
                    for (int i = 0; i < grid.length; ++i) {
                        int k = in.readUnsignedShort();
                        grid[i] = k < n ? pal[k] : null;
                    }
                    raw = null;
                } else {
                    raw = in.readAllBytes();
                    grid = new BlockData[w * h * l];
                }
            }
            if (!modern) {
                for (int i = 0; i < grid.length; ++i) {
                    int id = raw[2 * i] & 0xFF;
                    if (id == 0 || id == 166) continue;
                    grid[i] = EemiSheepWars.this.legacy(id, raw[2 * i + 1] & 0xF);
                }
            }
            BlockFace[] sides = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (int y = 0; y < (modern ? 0 : h); ++y) {
                for (int z = 0; z < l; ++z) {
                    for (int x = 0; x < w; ++x) {
                        boolean wall;
                        int i = (y * l + z) * w + x;
                        BlockData bd = grid[i];
                        if (bd == null) continue;
                        Material mat = bd.getMaterial();
                        boolean fenceLike = Tag.FENCES.isTagged(mat) || EemiSheepWars.pane(mat);
                        boolean bl = wall = Tag.WALLS.isTagged(mat) && bd instanceof Wall;
                        if (!fenceLike && !wall) continue;
                        BlockData c = bd.clone();
                        boolean ns = false;
                        boolean ew = false;
                        for (BlockFace f : sides) {
                            MultipleFacing mf;
                            int nx = x + f.getModX();
                            int nz = z + f.getModZ();
                            BlockData nb = nx < 0 || nz < 0 || nx >= w || nz >= l ? null : grid[(y * l + nz) * w + nx];
                            boolean on = EemiSheepWars.connects(bd, nb);
                            if (on && f.getModZ() != 0) {
                                ns = true;
                            }
                            if (on && f.getModX() != 0) {
                                ew = true;
                            }
                            if (c instanceof MultipleFacing && (mf = (MultipleFacing)c).getAllowedFaces().contains(f)) {
                                mf.setFace(f, on);
                            }
                            if (!(c instanceof Wall)) continue;
                            Wall wl = (Wall)c;
                            wl.setHeight(f, on ? Wall.Height.LOW : Wall.Height.NONE);
                        }
                        if (c instanceof Wall) {
                            Wall wl = (Wall)c;
                            wl.setUp(ns == ew);
                        }
                        grid[i] = c;
                    }
                }
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int y = 0; y < h; ++y) {
                for (int z = 0; z < l; ++z) {
                    for (int x = 0; x < w; ++x) {
                        BlockData bd = grid[(y * l + z) * w + x];
                        if (bd == null) continue;
                        if (m.rotate()) {
                            bd = bd.clone();
                            bd.rotate(StructureRotation.CLOCKWISE_180);
                        }
                        int[] p = EemiSheepWars.this.toWorld(m, m.ox() + x, m.oy() + y, m.oz() + z);
                        b.ops.add(new Op(p[0], p[1], p[2], bd));
                        minX = Math.min(minX, p[0]);
                        maxX = Math.max(maxX, p[0]);
                        minZ = Math.min(minZ, p[2]);
                        maxZ = Math.max(maxZ, p[2]);
                    }
                }
            }
            this.aMinX = minX - 1;
            this.aMaxX = maxX + 1;
            this.aMinZ = minZ - 1;
            this.aMaxZ = maxZ + 1;
            this.aMinY = EemiSheepWars.this.baseY() - 3;
            this.aMaxY = EemiSheepWars.this.baseY() + h + 1;
            EemiSheepWars.this.areaCache.put(this.mapId, new int[]{this.aMinX, this.aMaxX, this.aMinY, this.aMaxY, this.aMinZ, this.aMaxZ});
        }

        void clearEntities() {
            if (this.world == null) {
                return;
            }
            for (Entity e : this.world.getEntities()) {
                if (e instanceof Player || !(e instanceof Sheep) && !(e instanceof Item) && !(e instanceof Projectile) && !(e instanceof FallingBlock) && !(e instanceof Mob) && !(e instanceof ExperienceOrb) && !(e instanceof TNTPrimed)) continue;
                e.remove();
            }
            this.sheep.clear();
        }

        void spectate(Player p) {
            p.teleport(this.spectatorPoint());
            this.spectators.add(p.getUniqueId());
            if (EemiSheepWars.this.mayTouch(p)) {
                p.setGameMode(GameMode.SPECTATOR);
            }
            EemiSheepWars.this.later(() -> {
                if (p.isOnline() && this.spectators.contains(p.getUniqueId()) && EemiSheepWars.this.mayTouch(p)) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
            });
            p.sendMessage("\u00a78[\u00a79Sheep Wars\u00a78] \u00a77\u00a77Tu regardes la partie " + this.n + ", /lobby pour revenir.");
        }

        void sendSpectatorsToHub() {
            for (Player sp : EemiSheepWars.this.online(this.spectators)) {
                EemiSheepWars.this.sendToHub(sp);
            }
            this.spectators.clear();
        }

        void broadcast(String msg) {
            for (Player p : EemiSheepWars.this.online(this.players)) {
                p.sendMessage(EemiSheepWars.PREFIX + msg);
            }
        }

        String stateLabel() {
            if (this.preparing) {
                return "preparation";
            }
            if (this.building) {
                return "construction";
            }
            return switch (this.state.ordinal()) {
                default -> throw new MatchException(null, null);
                case 0 -> "attente";
                case 1 -> "d\u00e9compte " + this.timer + " s";
                case 2 -> "en jeu";
                case 3 -> "fin";
            };
        }
    }

    static enum State {
        WAITING,
        STARTING,
        PLAYING,
        ENDING;

    }

    static enum Side {
        ROUGE("Rouge", NamedTextColor.RED, Color.RED, Material.RED_WOOL, Material.RED_TERRACOTTA),
        BLEU("Bleu", NamedTextColor.BLUE, Color.fromRGB((int)3949738), Material.BLUE_WOOL, Material.BLUE_TERRACOTTA);

        final String label;
        final NamedTextColor color;
        final Color armor;
        final Material wool;
        final Material skeleton;

        private Side(String label, NamedTextColor color, Color armor, Material wool, Material skeleton) {
            this.label = label;
            this.color = color;
            this.armor = armor;
            this.wool = wool;
            this.skeleton = skeleton;
        }

        Side other() {
            return this == ROUGE ? BLEU : ROUGE;
        }

        String fem() {
            return this == BLEU ? "Bleue" : this.label;
        }

        String code() {
            return this == ROUGE ? "\u00a7c" : "\u00a79";
        }

        String key() {
            return this.name().toLowerCase();
        }

        int dir() {
            return this == ROUGE ? -1 : 1;
        }

        String front() {
            return this == ROUGE ? "east" : "west";
        }

        String back() {
            return this == ROUGE ? "west" : "east";
        }
    }

    static enum SheepType {
        EXPLOSIF("explosif", "Mouton explosif", DyeColor.RED, Material.RED_WOOL, 0xFF5555, 0, true, "Explose \u00e0 l'impact"),
        EXPLOSIF_FORT("explosif-fort", "Mouton tr\u00e8s explosif", DyeColor.GRAY, Material.GRAY_WOOL, 0xAAAAAA, 0, true, "Grosse explosion \u00e0 l'impact"),
        CHERCHEUR("chercheur", "Mouton chercheur", DyeColor.LIME, Material.LIME_WOOL, 0x55FF55, 0, true, "Se dirige en vol vers l'ennemi le plus proche"),
        INCENDIAIRE("incendiaire", "Mouton incendiaire", DyeColor.ORANGE, Material.ORANGE_WOOL, 0xFFAA00, 30, false, "Explose et met le feu"),
        GLACE("glace", "Mouton de glace", DyeColor.LIGHT_BLUE, Material.LIGHT_BLUE_WOOL, 0x55FFFF, 10, false, "Glace le sol, ralentit les ennemis"),
        AVEUGLANT("aveuglant", "Mouton aveuglant", DyeColor.BLACK, Material.BLACK_WOOL, 0x555555, 10, false, "Aveugle les ennemis proches"),
        TROU_NOIR("trou-noir", "Mouton trou noir", DyeColor.PURPLE, Material.PURPLE_WOOL, 0xAA00AA, 100, false, "Aspire joueurs et blocs pendant 5 s"),
        SEISME("seisme", "Mouton s\u00e9isme", DyeColor.BROWN, Material.BROWN_WOOL, 0xAA7744, 20, false, "Fait sauter le sol et les ennemis"),
        SOIN("soin", "Mouton soigneur", DyeColor.PINK, Material.PINK_WOOL, 0xFF77CC, 5, false, "R\u00e9g\u00e9n\u00e9ration et vitesse pour ton \u00e9quipe"),
        FOUDRE("foudre", "Mouton foudroyant", DyeColor.YELLOW, Material.YELLOW_WOOL, 0xFFFF55, 20, false, "Appelle la foudre"),
        INTERGALACTIQUE("intergalactique", "Mouton intergalactique", DyeColor.BLUE, Material.BLUE_WOOL, 0x5555FF, 20, false, "Fait pleuvoir des m\u00e9t\u00e9ores"),
        POISON("poison", "Mouton empoisonn\u00e9", DyeColor.GREEN, Material.GREEN_WOOL, 43520, 120, false, "Nuage de poison pendant 6 s"),
        ENDER("ender", "Mouton de l'Ender", DyeColor.CYAN, Material.CYAN_WOOL, 43690, 20, false, "T\u00e9l\u00e9porte les ennemis proches au hasard"),
        ABORDAGE("abordage", "Mouton d'abordage", DyeColor.WHITE, Material.WHITE_WOOL, 0xFFFFFF, 0, false, "Tu voyages dessus (accroupis-toi pour descendre)"),
        ECHANGEUR("echangeur", "Mouton \u00e9changeur", DyeColor.MAGENTA, Material.MAGENTA_WOOL, 0xFF55FF, 10, false, "\u00c9change ta place avec l'ennemi le plus proche");

        final String key;
        final String label;
        final DyeColor dye;
        final Material item;
        final int rgb;
        final int fuse;
        final boolean instant;
        final String lore;

        private SheepType(String key, String label, DyeColor dye, Material item, int rgb, int fuse, boolean instant, String lore) {
            this.key = key;
            this.label = label;
            this.dye = dye;
            this.item = item;
            this.rgb = rgb;
            this.fuse = fuse;
            this.instant = instant;
            this.lore = lore;
        }

        static SheepType of(String key) {
            for (SheepType t : SheepType.values()) {
                if (!t.key.equals(key)) continue;
                return t;
            }
            return null;
        }
    }

    private record Imported(String name, int ox, int oy, int oz, int mid, int zc, boolean rotate, int voidRel, int magicRel, int[][] red, int[][] blue) {
    }

    private final class Builder {
        final List<Op> ops = new ArrayList<Op>();

        private Builder() {
        }

        void put(int x, int y, int z, String data) {
            this.ops.add(new Op(x, y, z, data == null ? null : EemiSheepWars.this.bd(data)));
        }
    }

    private static final class Launched {
        final SheepType type;
        final Side side;
        final UUID owner;
        final boolean gravityShot;
        boolean armed;
        int fuse;
        int life;
        int airborne;

        Launched(SheepType type, Side side, UUID owner, boolean gravityShot) {
            this.type = type;
            this.side = side;
            this.owner = owner;
            this.gravityShot = gravityShot;
            this.fuse = type.fuse;
        }
    }

    private record Op(int x, int y, int z, BlockData data) {
    }
}

