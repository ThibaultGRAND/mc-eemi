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
 *  org.bukkit.Axis
 *  org.bukkit.Bukkit
 *  org.bukkit.FluidCollisionMode
 *  org.bukkit.GameMode
 *  org.bukkit.GameRules
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.WorldCreator
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.block.data.Bisected
 *  org.bukkit.block.data.Bisected$Half
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.Directional
 *  org.bukkit.block.data.FaceAttachable
 *  org.bukkit.block.data.FaceAttachable$AttachedFace
 *  org.bukkit.block.data.Orientable
 *  org.bukkit.block.data.type.Door
 *  org.bukkit.block.data.type.Slab
 *  org.bukkit.block.data.type.Slab$Type
 *  org.bukkit.block.structure.StructureRotation
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.BlockDisplay
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockFadeEvent
 *  org.bukkit.event.block.BlockFormEvent
 *  org.bukkit.event.block.BlockFromToEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.block.BlockSpreadEvent
 *  org.bukkit.event.block.LeavesDecayEvent
 *  org.bukkit.event.entity.CreatureSpawnEvent
 *  org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.FoodLevelChangeEvent
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.event.player.PlayerAnimationEvent
 *  org.bukkit.event.player.PlayerAnimationType
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerInteractEntityEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerSwapHandItemsEvent
 *  org.bukkit.event.player.PlayerToggleFlightEvent
 *  org.bukkit.generator.ChunkGenerator
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemFlag
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
 *  org.bukkit.util.BoundingBox
 *  org.bukkit.util.RayTraceResult
 *  org.bukkit.util.Transformation
 *  org.bukkit.util.Vector
 *  org.joml.AxisAngle4f
 *  org.joml.Vector3f
 */
package fr.eemicraft.hideseek;

import fr.eemicraft.hideseek.MapBuilder;
import fr.eemicraft.hideseek.build.RecordingSink;
import fr.eemicraft.hideseek.WorldClone;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.Input;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class EemiHideSeek
extends JavaPlugin
implements Listener {
    private static final String PREFIX = "\u00a78[\u00a7dCache-cache\u00a78] \u00a77";
    private static final String TAG = "eemihideseek";
    private static final String NAMES_TEAM = "hns_noms";
    private NamespacedKey kitKey;
    private final Map<Material, String> blocks = new LinkedHashMap<Material, String>();
    private String base = "hideseek";
    private final TreeMap<Integer, Game> games = new TreeMap<Integer, Game>();
    private World model;
    private boolean modelPending;
    private boolean modelBusy;
    private int copying;
    private final Set<Integer> releasing = new HashSet<Integer>();
    private String lastStatus;
    private long lastStatusAt;
    private long ticks;
    private final Random random = new Random();
    // Dernier clic droit / lancer d'objet de chaque joueur : le bras bouge aussi, ce n'est pas un coup.
    private final Map<UUID, Long> lastUse = new HashMap<UUID, Long>();
    // Objet de l'onde de choc des chercheurs (1.3.1, remplace l'eclat d'echo de la sonde).
    private static final Material SHOCKWAVE_ITEM = Material.BREEZE_ROD;
    // Joueurs a qui renvoyer l'air de leur propre case (cacheur solide) au tick suivant.
    private final Set<UUID> resyncSoon = new HashSet<UUID>();
    // 1.3.2 : un cacheur solide est un VRAI bloc pose dans le monde (plus de faux bloc envoye par paquet, que
    // n'importe quel clic droit effacait : le serveur renvoie l'etat reel des blocs autour apres chaque clic).
    // Bloc pose -> etat d'origine de la case, journalise dans blocs-cacheurs.yml (restaure au demarrage
    // apres un crash).
    private final Map<Block, BlockData> placed = new HashMap<Block, BlockData>();
    private boolean journalLoaded;
    // Cases a restaurer apres un crash (etat d'origine), gardees jusqu'a la sauvegarde du monde en fin de partie.
    private final Map<Block, BlockData> journal = new LinkedHashMap<Block, BlockData>();
    // 1.4.0 : plusieurs maps posees dans le meme monde (village en 0, port de peche depuis la 1.5.0 : boite x 340..520,
    // zone de jeu x 352..433 ;
    // supermarche en x 800). La map EEMI de la 1.4.0 est abandonnee : sa boite est recouverte par celle du port.
    // Le village garde ses points dans la config ; les autres maps portent les leurs (HsMap).
    static final String VILLAGE = "village";
    private final LinkedHashMap<String, HsMap> builtMaps = new LinkedHashMap<String, HsMap>();
    private final Map<String, LinkedHashMap<Material, String>> mapBlocks = new HashMap<String, LinkedHashMap<Material, String>>();
    private final Map<Material, String> allNames = new HashMap<Material, String>();
    // Etat des constructions (map.yml : version du village, maps.<id> pour les autres).
    private YamlConfiguration mapState = new YamlConfiguration();
    // Maps retirees (id -> boite) : leur entree de map.yml est effacee une fois une map dont la boite les couvre
    // reconstruite (la pose videe toute sa boite, donc aussi l'ancienne map).
    private static final Map<String, int[]> RETIRED_MAPS = Map.of("eemi", new int[]{340, 58, -48, 459, 112, 47});
    // Map imposee par /hideseek map <id> pour la prochaine partie.
    private String forcedNext;
    // Maps presentes dans le modele des copies (cliche pris au debut de la copie du modele).
    private Set<String> modelMaps = new HashSet<String>();

    public void onEnable() {
        this.kitKey = new NamespacedKey((Plugin)this, "kit");
        this.builtMaps.put("supermarche", new SuperMap());
        this.builtMaps.put("port", new PortMap());
        this.saveDefaultConfig();
        this.loadSettings();
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::second, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater((Plugin)this, this::setupWorld, 40L);
        Bukkit.getScheduler().runTask((Plugin)this, this::reportStatus);
    }

    public void onDisable() {
        for (Game g : new ArrayList<Game>(this.games.values())) {
            g.shutdown();
        }
        // Filet de securite : tout bloc de cacheur encore pose est remis a l'etat d'origine. Les mondes sont
        // sauvegardes apres l'arret des plugins : seules les cases non restaurees restent au journal.
        HashMap<Block, BlockData> failed = new HashMap<Block, BlockData>();
        for (Map.Entry<Block, BlockData> en : new ArrayList<Map.Entry<Block, BlockData>>(this.placed.entrySet())) {
            if (!this.restoreCell(en.getKey(), en.getValue())) {
                failed.put(en.getKey(), this.journal.getOrDefault(en.getKey(), en.getValue()));
            }
        }
        this.placed.clear();
        this.journal.clear();
        this.journal.putAll(failed);
        this.saveJournal();
    }

    private File journalFile() {
        return new File(this.getDataFolder(), "blocs-cacheurs.yml");
    }

    // Pose le bloc du cacheur (sans physique) et note l'etat d'origine de la case dans le journal.
    // Le journal garde une case meme apres sa liberation : le disque peut encore contenir le bloc du cacheur
    // (sauvegarde automatique faite pendant qu'il etait pose). Il n'est vide qu'apres une sauvegarde du monde
    // en fin de partie (clearJournal), a l'arret, ou par la restauration au demarrage.
    void placeCell(Block b, BlockData data) {
        if (!this.placed.containsKey(b)) {
            this.placed.put(b, b.getBlockData().clone());
        }
        if (!this.journal.containsKey(b)) {
            this.journal.put(b, this.placed.get(b));
            // Ecrit AVANT la pose : un crash juste apres ne peut pas laisser un bloc non journalise.
            this.saveJournal();
        }
        b.setBlockData(data, false);
    }

    // Remet la case a son etat d'origine (si le monde est encore charge).
    void releaseCell(Block b) {
        BlockData orig = this.placed.remove(b);
        if (orig != null) {
            this.restoreCell(b, orig);
        }
    }

    // Fin de partie : le monde (sans bloc de cacheur) vient d'etre sauvegarde, ses entrees sont retirees.
    void clearJournal(World w) {
        if (w == null) {
            return;
        }
        boolean changed = false;
        for (Block b : new ArrayList<Block>(this.journal.keySet())) {
            if (!b.getWorld().equals((Object)w) || this.placed.containsKey(b)) continue;
            this.journal.remove(b);
            changed = true;
        }
        if (changed) {
            this.saveJournal();
        }
    }

    private boolean restoreCell(Block b, BlockData orig) {
        try {
            if (Bukkit.getWorld(b.getWorld().getUID()) == null) {
                return false;
            }
            b.setBlockData(orig, false);
            return true;
        }
        catch (Exception ex) {
            this.getLogger().warning("Bloc de cacheur non restaure en " + b.getX() + " " + b.getY() + " " + b.getZ() + " : " + ex.getMessage());
            return false;
        }
    }

    private void saveJournal() {
        // Tant que l'ancien journal n'a pas ete relu (monde pas encore charge), on ne l'ecrase pas.
        if (!this.journalLoaded) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        ArrayList<String> l = new ArrayList<String>();
        for (Map.Entry<Block, BlockData> en : this.journal.entrySet()) {
            Block b = en.getKey();
            l.add(b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ() + ";" + en.getValue().getAsString());
        }
        y.set("blocs", l);
        try {
            this.getDataFolder().mkdirs();
            y.save(this.journalFile());
        }
        catch (IOException e) {
            this.getLogger().warning("blocs-cacheurs.yml : " + e.getMessage());
        }
    }

    // Au demarrage (avant toute copie du monde) : remet les cases laissees par un arret brutal.
    // Les entrees des copies (hideseek_2...) sont ignorees : ces mondes sont supprimes au demarrage.
    private void restoreJournal(World world) {
        File f = this.journalFile();
        this.journalLoaded = true;
        if (!f.exists()) {
            return;
        }
        int n = 0;
        for (String s : YamlConfiguration.loadConfiguration((File)f).getStringList("blocs")) {
            String[] p = s.split(";", 5);
            if (p.length < 5 || !p[0].equals(world.getName())) continue;
            try {
                Block b = world.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                b.setBlockData(Bukkit.createBlockData((String)p[4]), false);
                ++n;
            }
            catch (Exception ex) {
                this.getLogger().warning("Journal des blocs de cacheurs : entree illisible (" + s + ") : " + ex.getMessage());
            }
        }
        if (n > 0) {
            this.getLogger().warning(n + " bloc(s) de cacheur laisse(s) par un arret brutal : remis a l'etat d'origine.");
        }
        this.saveJournal();
    }

    // Les vrais blocs des cacheurs ne bougent jamais (pas de mise a jour de voisin, de chute, de piston).
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (!this.placed.isEmpty() && this.placed.containsKey(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!this.placed.isEmpty() && this.placed.containsKey(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (this.placed.isEmpty()) return;
        for (Block b : e.getBlocks()) {
            if (this.placed.containsKey(b)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (this.placed.isEmpty()) return;
        for (Block b : e.getBlocks()) {
            if (this.placed.containsKey(b)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!this.placed.isEmpty()) e.blockList().removeIf(this.placed::containsKey);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!this.placed.isEmpty()) e.blockList().removeIf(this.placed::containsKey);
    }

    private void loadSettings() {
        this.reloadConfig();
        this.blocks.clear();
        ConfigurationSection s = this.getConfig().getConfigurationSection("blocks");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                Material m = Material.matchMaterial((String)k);
                if (m == null || !m.isBlock()) {
                    this.getLogger().warning("Bloc inconnu dans la config : " + k);
                    continue;
                }
                this.blocks.put(m, s.getString(k, k));
            }
        }
        if (this.blocks.isEmpty()) {
            this.blocks.put(Material.BARREL, "Tonneau");
        }
        this.getLogger().info(this.blocks.size() + " blocs de d\u00e9guisement.");
        // Listes de deguisement des maps construites : celle du code, ou map-blocks.<id> si la config en a une.
        // Un bloc deja dans la liste du village garde son nom de la config.
        this.mapBlocks.clear();
        this.allNames.clear();
        this.allNames.putAll(this.blocks);
        for (HsMap hm : this.builtMaps.values()) {
            LinkedHashMap<Material, String> l = new LinkedHashMap<Material, String>();
            ConfigurationSection over = this.getConfig().getConfigurationSection("map-blocks." + hm.id);
            Map<String, String> src = new LinkedHashMap<String, String>();
            if (over != null) {
                for (String k : over.getKeys(false)) src.put(k, over.getString(k, k));
            } else {
                src.putAll(hm.disguises());
            }
            for (Map.Entry<String, String> en : src.entrySet()) {
                Material m = Material.matchMaterial((String)en.getKey());
                if (m == null || !m.isBlock()) {
                    this.getLogger().warning("Bloc de d\u00e9guisement inconnu (map " + hm.id + ") : " + en.getKey());
                    continue;
                }
                l.put(m, en.getValue());
                this.allNames.putIfAbsent(m, en.getValue());
            }
            if (l.isEmpty()) {
                l.putAll(this.blocks);
            }
            this.mapBlocks.put(hm.id, l);
            this.getLogger().info("Map " + hm.id + " : " + l.size() + " blocs de d\u00e9guisement.");
        }
    }

    // ------------------------------------------------------------------ maps (1.4.0)

    String mapName(String id) {
        HsMap m = this.builtMaps.get(id);
        return m != null ? m.name : "Village";
    }

    List<String> allMapIds() {
        ArrayList<String> l = new ArrayList<String>();
        l.add(VILLAGE);
        l.addAll(this.builtMaps.keySet());
        return l;
    }

    /** Map construite et a jour (version notee dans map.yml egale a celle du code). */
    boolean mapReady(String id) {
        if (VILLAGE.equals(id)) {
            return this.mapState.getInt("version", 0) == 1;
        }
        HsMap m = this.builtMaps.get(id);
        return m != null && this.mapState.getInt("maps." + id, 0) == m.version;
    }

    /** Maps jouables : cle maps de la config (vide = toutes), construites, et presentes dans le monde de la partie. */
    List<String> playableMaps(Set<String> inWorld) {
        ArrayList<String> cfg = new ArrayList<String>();
        for (String s : this.getConfig().getStringList("maps")) cfg.add(s.trim().toLowerCase());
        ArrayList<String> res = new ArrayList<String>();
        for (String id : this.allMapIds()) {
            if ((cfg.isEmpty() || cfg.contains(id)) && this.mapReady(id) && (inWorld == null || inWorld.contains(id))) res.add(id);
        }
        if (res.isEmpty()) {
            res.add(VILLAGE);
        }
        return res;
    }

    /** Tire la prochaine map (jamais deux fois de suite), ou celle imposee par /hideseek map. */
    String pickMap(String current, Set<String> inWorld) {
        if (this.forcedNext != null && this.mapReady(this.forcedNext) && (inWorld == null || inWorld.contains(this.forcedNext))) {
            String f = this.forcedNext;
            this.forcedNext = null;
            return f;
        }
        List<String> l = this.playableMaps(inWorld);
        if (l.size() > 1 && current != null) {
            l.remove(current);
        }
        return l.get(this.random.nextInt(l.size()));
    }

    Set<String> readyMaps() {
        HashSet<String> s = new HashSet<String>();
        for (String id : this.allMapIds()) {
            if (this.mapReady(id)) s.add(id);
        }
        return s;
    }

    Map<Material, String> disguiseBlocks(Game g) {
        if (g == null || VILLAGE.equals(g.mapId)) {
            return this.blocks;
        }
        LinkedHashMap<Material, String> l = this.mapBlocks.get(g.mapId);
        return l != null ? l : this.blocks;
    }

    Location mapPoint(World w, String id, String key) {
        HsMap m = this.builtMaps.get(id);
        double[] p = m == null ? null : m.point(key);
        if (p == null) {
            if ("spectate".equals(key)) {
                Location view = this.point(w, "seeker-spawn").add(0.0, 20.0, 18.0);
                view.setYaw(180.0f);
                view.setPitch(45.0f);
                return view;
            }
            return this.point(w, key);
        }
        return new Location(w, p[0], p[1], p[2], (float)p[3], (float)p[4]);
    }

    /** La boite a contient entierement la boite b ({x1, y1, z1, x2, y2, z2}). */
    static boolean covers(int[] a, int[] b) {
        return a[0] <= b[0] && a[1] <= b[1] && a[2] <= b[2] && a[3] >= b[3] && a[4] >= b[4] && a[5] >= b[5];
    }

    private void saveMapState() {
        try {
            this.getDataFolder().mkdirs();
            this.mapState.save(new File(this.getDataFolder(), "map.yml"));
        }
        catch (IOException e) {
            this.getLogger().warning("map.yml : " + e.getMessage());
        }
    }

    private int maxInstances() {
        return Math.max(1, this.getConfig().getInt("max-instances", 3));
    }

    private String modelName() {
        return this.base + "_modele";
    }

    private void setupWorld() {
        this.base = this.getConfig().getString("world", "hideseek");
        World world = Bukkit.getWorld((String)this.base);
        if (world == null) {
            world = new WorldCreator(this.base).generator((ChunkGenerator)new VoidGenerator()).createWorld();
            this.getLogger().info("Monde " + this.base + " cree (vide).");
        }
        if (world == null) {
            this.getLogger().severe("Impossible de charger ou cr\u00e9er le monde " + this.base + " : cache-cache d\u00e9sactiv\u00e9.");
            return;
        }
        Game g1 = new Game(1);
        g1.world = world;
        g1.state = State.WAITING;
        this.games.put(1, g1);
        this.restoreJournal(world);
        this.prepareWorld(world);
        this.removeDisplays(world);
        WorldClone.cleanup((Plugin)this, world, this.base, this.maxInstances());
        this.mapState = YamlConfiguration.loadConfiguration((File)new File(this.getDataFolder(), "map.yml"));
        // 1.5.0 : ancienne map encore notee (EEMI) -> la map qui couvre sa boite est reconstruite, ce qui la vide.
        for (Map.Entry<String, int[]> rm : RETIRED_MAPS.entrySet()) {
            if (!this.mapState.isSet("maps." + rm.getKey())) continue;
            for (HsMap hm : this.builtMaps.values()) {
                if (!covers(hm.box(), rm.getValue())) continue;
                this.getLogger().info("Ancienne map " + rm.getKey() + " encore dans le monde : sa zone sera videe par la construction de la map " + hm.id + ".");
                this.mapState.set("maps." + hm.id, (Object)0);
            }
        }
        // Toutes les maps sont posees dans le monde de la partie 1 (le modele des copies les contient donc toutes) :
        // on reconstruit celles qui manquent ou dont la version a change.
        ArrayList<String> todo = new ArrayList<String>();
        for (String id : this.allMapIds()) {
            // bloc temoin : le sol sous le point d'attente doit exister (monde supprime, crash avant sauvegarde...)
            if (this.mapReady(id) && this.mapPoint(world, id, "waiting-spawn").getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType().isAir()) {
                this.getLogger().warning("Map " + id + " notee construite mais absente du monde : reconstruction.");
                if (VILLAGE.equals(id)) this.mapState.set("version", (Object)0); else this.mapState.set("maps." + id, (Object)0);
            }
            if (!this.mapReady(id)) todo.add(id);
        }
        g1.mapId = this.pickMap(null, null);
        if (!todo.isEmpty()) {
            g1.buildMaps(null, todo);
        } else {
            this.modelPending = true;
        }
    }

    private void prepareWorld(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.DROWNING_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setSpawnLocation(this.point(world, "waiting-spawn"));
    }

    private int cfg(String key, int def) {
        return this.getConfig().getInt(key, def);
    }

    /**
     * Le cacheur (pose au sol en l) est-il sur un perchoir interdit de la map de la partie (1.6.0, carte #135) ?
     * Village : sol plat (bloc y 64) dans x/z -44..44. Maps construites : HsMap.roofGround / perchAllowed.
     */
    boolean perched(Game g, Location l) {
        HsMap m = this.builtMaps.get(g.mapId);
        Perch.Ground ground = m != null ? m::roofGround : (x, z) -> Math.abs(x) <= 44 && Math.abs(z) <= 44 ? MapBuilder.GROUND : Integer.MIN_VALUE;
        int x = l.getBlockX(), z = l.getBlockZ();
        World w = l.getWorld();
        return Perch.forbidden((cx, cy, cz) -> this.roofCover(w, cx, cy, cz), m, x, l.getY(), z, Perch.groundNear(ground, x, z),
                this.cfgD("max-roof-height", 3.0), Math.max(1, this.cfg("roof-cover-scan", 24)));
    }

    /**
     * Bloc qui abrite un cacheur (toit, plafond, plancher d'etage) : tout bloc qui arrete le joueur, sauf feuilles,
     * barrieres (plafond invisible des maps) et bloc de lumiere. Les blocs des cacheurs solides sont ignores (leur case
     * d'origine est toujours vide ou remplacable).
     */
    private boolean roofCover(World w, int x, int y, int z) {
        if (y >= w.getMaxHeight()) return false;
        Block b = w.getBlockAt(x, y, z);
        if (!this.placed.isEmpty() && this.placed.containsKey(b)) return false;
        Material m = b.getType();
        if (m.isAir() || m == Material.BARRIER || m == Material.LIGHT || Tag.LEAVES.isTagged(m)) return false;
        return !b.isPassable();
    }

    private double cfgD(String key, double def) {
        return this.getConfig().getDouble(key, def);
    }

    private Location point(World world, String key) {
        String[] p = this.getConfig().getString(key, "0.5,101,75.5").split(",");
        Location l = new Location(world, Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
        if (p.length >= 5) {
            l.setYaw(Float.parseFloat(p[3].trim()));
            l.setPitch(Float.parseFloat(p[4].trim()));
        }
        return l;
    }

    private String blockName(Game g, BlockData d) {
        String n = this.disguiseBlocks(g).get(d.getMaterial());
        if (n == null) {
            n = this.allNames.get(d.getMaterial());
        }
        if (n != null) {
            return n;
        }
        String raw = d.getMaterial().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private boolean isGameWorld(World w) {
        return w != null && (WorldClone.index(this.base, w.getName()) > 0 || w.getName().equals(this.modelName()));
    }

    private Game gameOf(Player p) {
        for (Game g : this.games.values()) {
            if (!g.players.contains(p.getUniqueId())) continue;
            return g;
        }
        return null;
    }

    private Game spectatorGameOf(Player p) {
        for (Game g : this.games.values()) {
            if (!g.spectators.contains(p.getUniqueId())) continue;
            return g;
        }
        return null;
    }

    private Game gameAt(World w) {
        if (w == null) {
            return null;
        }
        for (Game g : this.games.values()) {
            if (!w.equals((Object)g.world)) continue;
            return g;
        }
        return null;
    }

    private Game joinable(int need, Game except, boolean preparing) {
        Game best = null;
        for (Game g : this.games.values()) {
            if (g == except || g.state != State.WAITING && g.state != State.STARTING && (!preparing || g.state != State.PREPARING) || g.state != State.PREPARING && g.world == null || g.players.size() + need > this.cfg("max-players", 20) || best != null && g.players.size() <= best.players.size()) continue;
            best = g;
        }
        return best;
    }

    private Game newGame() {
        if (this.games.size() >= this.maxInstances()) {
            return null;
        }
        for (int n = 2; n <= this.maxInstances(); ++n) {
            if (this.games.containsKey(n) || this.releasing.contains(n)) continue;
            Game g = new Game(n);
            g.state = State.PREPARING;
            this.games.put(n, g);
            if (this.model != null) {
                this.startCopy(g);
            }
            return g;
        }
        return null;
    }

    private void startCopy(Game g) {
        g.copyStarted = true;
        ++this.copying;
        // La copie ne contient que les maps presentes quand le modele a ete fait.
        g.available = new HashSet<String>(this.modelMaps);
        WorldClone.create((Plugin)this, this.model, WorldClone.name(this.base, g.index), w -> {
            --this.copying;
            if (this.games.get(g.index) != g) {
                if (w != null) {
                    WorldClone.destroy((Plugin)this, w, this.base, this::sendToHub);
                }
                return;
            }
            if (w == null) {
                for (Player p : this.online(g.players)) {
                    p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cImpossible de pr\u00e9parer une nouvelle partie, r\u00e9essaie plus tard.");
                }
                this.games.remove(g.index);
                this.reportStatus();
                return;
            }
            g.world = w;
            g.mapId = this.pickMap(null, g.available);
            this.prepareWorld((World)w);
            this.removeDisplays((World)w);
            g.state = State.WAITING;
            String hub = this.getConfig().getString("hub-world", "lobby");
            for (UUID id : new ArrayList<UUID>(g.players)) {
                Player p = Bukkit.getPlayer((UUID)id);
                if (p == null || !p.getWorld().getName().equals(hub)) {
                    g.players.remove(id);
                    if (p == null) continue;
                    p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu n'es plus au hub : inscription au cache-cache annul\u00e9e.");
                    continue;
                }
                g.toWaiting(p);
                p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7aLa partie " + g.index + " est pr\u00eate !");
            }
            if (g.players.isEmpty()) {
                this.destroyGame(g);
            } else {
                g.broadcast("\u00a77Joueurs : \u00a7f" + g.players.size() + "/" + this.cfg("max-players", 20) + "\u00a77.");
            }
            this.reportStatus();
        });
    }

    private void destroyGame(Game g) {
        if (g.index <= 1) {
            return;
        }
        this.games.remove(g.index);
        for (Player p : this.online(g.spectators)) {
            this.showAll(p);
            this.sendToHub(p);
        }
        g.spectators.clear();
        g.releaseAllCells();
        this.clearJournal(g.world);
        World w = g.world;
        g.world = null;
        if (w == null) {
            return;
        }
        int n = g.index;
        this.releasing.add(n);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.releasing.remove(n), 200L);
        Bukkit.getScheduler().runTask((Plugin)this, () -> WorldClone.destroy((Plugin)this, w, this.base, p -> {
            this.showAll((Player)p);
            this.sendToHub((Player)p);
        }));
    }

    private void maybeRemakeModel() {
        Game g1 = this.games.get(1);
        if (!this.modelPending || this.modelBusy || this.copying > 0 || g1 == null || g1.world == null) {
            return;
        }
        if (g1.state != State.WAITING && g1.state != State.STARTING) {
            return;
        }
        this.modelPending = false;
        World old = Bukkit.getWorld((String)this.modelName());
        this.model = null;
        if (old != null) {
            for (Player p : old.getPlayers()) {
                this.sendToHub(p);
            }
            if (!Bukkit.unloadWorld((World)old, (boolean)false)) {
                this.getLogger().warning("Impossible de d\u00e9charger l'ancien mod\u00e8le " + this.modelName() + ", nouvel essai dans 1 s.");
                this.modelPending = true;
                return;
            }
        }
        this.modelBusy = true;
        Set<String> snapshot = this.readyMaps();
        // Pas de sauvegarde automatique de la partie 1 pendant la copie asynchrone de ses fichiers (une partie
        // peut demarrer entre-temps : le modele ne doit embarquer ni bloc de cacheur ni region a moitie ecrite).
        World src = g1.world;
        boolean autoSave = src.isAutoSave();
        src.setAutoSave(false);
        WorldClone.create((Plugin)this, src, this.modelName(), w -> {
            src.setAutoSave(autoSave);
            this.modelBusy = false;
            this.model = w;
            this.modelMaps = snapshot;
            if (w == null) {
                this.getLogger().warning("Mod\u00e8le " + this.modelName() + " impossible a cr\u00e9er : pas de partie suppl\u00e9mentaire pour l'instant.");
                return;
            }
            this.prepareWorld((World)w);
            this.removeDisplays((World)w);
            this.getLogger().info("Mod\u00e8le " + this.modelName() + " pr\u00eat (parties en parall\u00e8le : " + this.maxInstances() + " max).");
        });
    }

    private void second() {
        for (Game g : new ArrayList<Game>(this.games.values())) {
            if (this.games.get(g.index) != g) continue;
            g.second();
            if (g.index >= 2 && g.players.isEmpty() && (g.state == State.WAITING || g.state == State.STARTING)) {
                this.destroyGame(g);
                continue;
            }
            if (g.index < 2 || g.state != State.PREPARING || !g.players.isEmpty() || g.copyStarted) continue;
            this.games.remove(g.index);
        }
        for (Game g : new ArrayList<Game>(this.games.values())) {
            if (g.state != State.PREPARING || g.copyStarted || this.model == null) continue;
            this.startCopy(g);
        }
        this.maybeRemakeModel();
        this.reportStatus();
    }

    private void tick() {
        ++this.ticks;
        for (Game g : new ArrayList<Game>(this.games.values())) {
            g.tick();
        }
    }

    private void removeDisplays(World world) {
        if (world == null) {
            return;
        }
        for (Entity e : world.getEntities()) {
            if (!(e instanceof BlockDisplay) || !e.getScoreboardTags().contains(TAG)) continue;
            e.remove();
        }
    }

    private BlockData randomBlock(Game g) {
        ArrayList<Material> l = new ArrayList<Material>(this.disguiseBlocks(g).keySet());
        return this.normalized(((Material)l.get(this.random.nextInt(l.size()))).createBlockData());
    }

    private BlockData normalized(BlockData d) {
        if (d instanceof FaceAttachable) {
            FaceAttachable fa = (FaceAttachable)d;
            fa.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        }
        // 1.3.2 : le bloc solide est un vrai bloc pose sans physique ; un etat copie qui depend des voisins
        // de sa source (connexions, escalier en angle, demi-coffre double, eau) jurerait a sa nouvelle place.
        if (d instanceof org.bukkit.block.data.Waterlogged) {
            ((org.bukkit.block.data.Waterlogged)d).setWaterlogged(false);
        }
        if (d instanceof org.bukkit.block.data.MultipleFacing) {
            org.bukkit.block.data.MultipleFacing mf = (org.bukkit.block.data.MultipleFacing)d;
            for (BlockFace f : mf.getAllowedFaces()) {
                if (f == BlockFace.UP || f == BlockFace.DOWN) continue;
                mf.setFace(f, false);
            }
        }
        if (d instanceof org.bukkit.block.data.type.Wall) {
            org.bukkit.block.data.type.Wall w = (org.bukkit.block.data.type.Wall)d;
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                w.setHeight(f, org.bukkit.block.data.type.Wall.Height.NONE);
            }
            w.setUp(true);
        }
        if (d instanceof org.bukkit.block.data.type.Stairs) {
            ((org.bukkit.block.data.type.Stairs)d).setShape(org.bukkit.block.data.type.Stairs.Shape.STRAIGHT);
        }
        if (d instanceof org.bukkit.block.data.type.Chest) {
            ((org.bukkit.block.data.type.Chest)d).setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
        }
        if (d instanceof org.bukkit.block.data.type.Leaves) {
            ((org.bukkit.block.data.type.Leaves)d).setPersistent(true);
        }
        return d;
    }

    // Deguisements refuses : blocs en deux parties (porte, lit, plante double) qu'on ne peut poser qu'a moitie.
    private boolean unusableDisguise(BlockData d) {
        return d instanceof Door || d instanceof org.bukkit.block.data.type.Bed || d instanceof Bisected && !(d instanceof org.bukkit.block.data.type.Stairs) && !(d instanceof org.bukkit.block.data.type.TrapDoor);
    }

    private void openMenu(Game g, Player h) {
        Map<Material, String> list = this.disguiseBlocks(g);
        Menu m = new Menu();
        m.items.addAll(list.keySet());
        int size = Math.min(54, Math.max(9, (m.items.size() + 8) / 9 * 9));
        m.inv = Bukkit.createInventory((InventoryHolder)m, (int)size, (Component)Component.text((String)"Choisis ton bloc", (TextColor)NamedTextColor.DARK_GREEN));
        for (int i = 0; i < m.items.size() && i < size; ++i) {
            Material mat = m.items.get(i);
            ItemStack icon = new ItemStack(mat.isItem() ? mat : Material.FLOWER_POT);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text((String)list.get(mat), (TextColor)NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.values());
            icon.setItemMeta(meta);
            m.inv.setItem(i, icon);
        }
        h.openInventory(m.inv);
    }

    private boolean hiddenHider(Player q) {
        Game g = this.gameOf(q);
        return g != null && (g.state == State.HIDING || g.state == State.SEEKING) && g.roles.get(q.getUniqueId()) == Role.HIDER;
    }

    private void showAll(Player p) {
        for (Player q : Bukkit.getOnlinePlayers()) {
            if (q == p) continue;
            if (!this.hiddenHider(p)) {
                q.showPlayer((Plugin)this, p);
            }
            if (this.hiddenHider(q)) continue;
            p.showPlayer((Plugin)this, q);
        }
    }

    private void join(Player p) {
        if (this.gameOf(p) != null) {
            p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu es d\u00e9j\u00e0 inscrit.");
            return;
        }
        if (this.games.get(1) == null) {
            p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cLa map n'est pas charg\u00e9e.");
            return;
        }
        Game sg = this.spectatorGameOf(p);
        if (sg != null) {
            sg.spectators.remove(p.getUniqueId());
            this.showAll(p);
        }
        Game g = this.joinable(1, null, true);
        boolean created = false;
        if (g == null) {
            g = this.newGame();
            boolean bl = created = g != null;
        }
        if (g == null) {
            p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cToutes les parties sont pleines, r\u00e9essaie dans un instant.");
            return;
        }
        g.players.add(p.getUniqueId());
        if (g.state == State.PREPARING) {
            p.sendMessage(PREFIX + (created ? "\u00a7ePr\u00e9paration d'une nouvelle partie... \u00a77tu y seras t\u00e9l\u00e9port\u00e9." : "\u00a7eUne nouvelle partie se pr\u00e9pare : \u00a77tu y seras t\u00e9l\u00e9port\u00e9."));
            this.reportStatus();
            return;
        }
        g.toWaiting(p);
        g.broadcast("\u00a7a" + p.getName() + " \u00a77a rejoint le cache-cache (\u00a7f" + g.inRoom().size() + "/" + this.cfg("max-players", 20) + "\u00a77).");
        this.reportStatus();
    }

    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        return this.isGameWorld(w) || w.getName().equals(this.getConfig().getString("hub-world", "lobby"));
    }

    private void sendToHub(Player p) {
        if (!this.mayTouch(p)) {
            return;
        }
        this.resetPlayer(p, GameMode.SURVIVAL);
        p.setAllowFlight(false);
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
        p.setCooldown(SHOCKWAVE_ITEM, 0);
        p.setCooldown(Material.RECOVERY_COMPASS, 0);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        this.heal(p);
        p.setLevel(0);
        p.setExp(0.0f);
        p.setFireTicks(0);
        p.setFallDistance(0.0f);
    }

    private void heal(Player p) {
        if (!this.mayTouch(p)) {
            return;
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20.0 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5.0f);
    }

    private void equipSeeker(Player s) {
        if (!this.mayTouch(s)) {
            return;
        }
        s.getInventory().clear();
        ItemStack sword = this.kit(new ItemStack(Material.IRON_SWORD), "sword");
        ItemMeta meta = sword.getItemMeta();
        meta.setUnbreakable(true);
        meta.displayName(Component.text((String)"\u00c9p\u00e9e du chercheur", (TextColor)NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        sword.setItemMeta(meta);
        s.getInventory().setItem(0, sword);
        s.getInventory().setItem(1, this.named(this.kit(new ItemStack(SHOCKWAVE_ITEM), "shockwave"), "Onde de choc", NamedTextColor.DARK_AQUA, "Clic droit : onde de " + this.fmt(this.cfgD("shockwave-radius", 5.0)) + " blocs autour de toi", "D\u00e9loge les cacheurs (m\u00eame derri\u00e8re un mur)", "et les repousse un peu", "Gratuit, recharge " + this.cfg("shockwave-cooldown", 15) + " s"));
        s.getInventory().setItem(2, this.named(this.kit(new ItemStack(Material.RECOVERY_COMPASS), "radar"), "Radar chaud/froid", NamedTextColor.GOLD, "Clic droit : distance du cacheur", "le plus proche (sans direction)", "Recharge " + this.cfg("radar-cooldown", 1) + " s"));
        s.getInventory().setHeldItemSlot(0);
        int speed = this.cfg("seeker-speed", 2);
        if (speed > 0) {
            s.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, speed - 1, false, false, true));
        }
    }

    private void giveHiderItems(Player h) {
        if (!this.mayTouch(h)) {
            return;
        }
        h.getInventory().clear();
        h.getInventory().setItem(0, this.named(this.kit(new ItemStack(Material.AMETHYST_SHARD), "change"), "Changer de bloc", NamedTextColor.LIGHT_PURPLE, "Clic droit sur un bloc : le copier", "Clic droit dans le vide : menu"));
        h.getInventory().setItem(1, this.named(this.kit(new ItemStack(Material.FIREWORK_STAR), "taunt"), "Provoquer", NamedTextColor.GOLD, "Son + particules : +1 point", "et le temps restant baisse"));
        h.getInventory().setItem(2, this.named(this.kit(new ItemStack(Material.BLAZE_ROD), "rotate"), "Tourner le bloc", NamedTextColor.AQUA, "Clic droit : quart de tour", "Clic gauche : retourner (haut/bas, axe)"));
        h.getInventory().setHeldItemSlot(0);
    }

    private static String clock(int s) {
        s = Math.max(0, s);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // Nombre sans ",0" inutile (5.0 -> "5", 2.5 -> "2,5").
    private String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0E-6) {
            return String.valueOf((long)Math.rint(v));
        }
        return String.format("%.1f", v);
    }

    private static Component kv(String k, String v, NamedTextColor c) {
        return Component.text((String)k, (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)v, (TextColor)c));
    }

    private ItemStack named(ItemStack it, String name, NamedTextColor color, String ... lore) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text((String)name, (TextColor)color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            ArrayList<Component> l = new ArrayList<Component>();
            for (String s : lore) {
                l.add(Component.text((String)s, (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack kit(ItemStack it, String type) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(this.kitKey, PersistentDataType.STRING, type);
        it.setItemMeta(meta);
        return it;
    }

    private String kitType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return (String)it.getItemMeta().getPersistentDataContainer().get(this.kitKey, PersistentDataType.STRING);
    }

    private boolean inWorld(Block b) {
        return this.isGameWorld(b.getWorld());
    }

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemihideseek.admin");
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (this.inWorld(e.getBlock()) && !this.admin(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (this.inWorld(e.getBlock()) && !this.admin(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onFlow(BlockFromToEvent e) {
        if (this.inWorld(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onFade(BlockFadeEvent e) {
        if (this.inWorld(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onForm(BlockFormEvent e) {
        if (this.inWorld(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onSpread(BlockSpreadEvent e) {
        if (this.inWorld(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onDecay(LeavesDecayEvent e) {
        if (this.inWorld(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (this.isGameWorld(e.getLocation().getWorld()) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        Player p;
        Entity entity = e.getEntity();
        if (entity instanceof Player && (this.gameOf(p = (Player)entity) != null || this.isGameWorld(p.getWorld()))) {
            e.setCancelled(true);
        }
    }

    // Frapper un joueur fait aussi bouger le bras : ce n'est pas un coup dans le decor.
    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerAttack(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Player) {
            this.lastUse.put(d.getUniqueId(), System.currentTimeMillis());
        }
    }

    // Les coeurs du chercheur ne remontent pas tout seuls pendant la partie (seulement en trouvant un cacheur).
    @EventHandler(ignoreCancelled=true)
    public void onRegain(EntityRegainHealthEvent e) {
        Entity entity = e.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player p = (Player)entity;
        Game g = this.gameOf(p);
        if (g != null && (g.state == State.HIDING || g.state == State.SEEKING) && g.roles.get(p.getUniqueId()) == Role.SEEKER) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        Player p;
        HumanEntity humanEntity = e.getEntity();
        if (humanEntity instanceof Player && (this.gameOf(p = (Player)humanEntity) != null || this.isGameWorld(p.getWorld()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        if (this.isGameWorld(e.getPlayer().getWorld())) {
            this.scheduleResync(e.getPlayer());
        }
        Game g = this.gameOf(e.getPlayer());
        if (g != null) {
            g.swing(e.getPlayer());
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Game g = this.gameOf(e.getPlayer());
        if (g == null || g.roles.get(e.getPlayer().getUniqueId()) != Role.SEEKER) {
            return;
        }
        boolean frozen = g.state == State.HIDING || g.state == State.SEEKING && g.respawn.containsKey(e.getPlayer().getUniqueId());
        if (!frozen) {
            return;
        }
        Location f = e.getFrom();
        Location t = e.getTo();
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            Location back = f.clone();
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            e.setTo(back);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        Game g = this.gameOf(p);
        if (!e.isFlying() || g == null || !g.roles.containsKey(p.getUniqueId())) {
            return;
        }
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        e.setCancelled(true);
        p.setFlying(false);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        boolean here = this.isGameWorld(p.getWorld());
        Game g = this.gameOf(p);
        if (!here && g == null) {
            return;
        }
        if (here && !this.admin(p) && (e.getAction() == Action.PHYSICAL || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            this.lastUse.put(p.getUniqueId(), System.currentTimeMillis());
        }
        // 1.3.2 : les cacheurs solides sont de vrais blocs, un clic ne revele plus rien. Seul le cacheur
        // lui-meme voit de l'air dans sa propre case : un clic pres de lui peut la lui renvoyer, on la recorrige.
        if (here && e.getAction() != Action.PHYSICAL) {
            this.scheduleResync(p);
        }
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String type = this.kitType(e.getItem());
        if (type == null) {
            return;
        }
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean hider = g != null && g.roles.get(p.getUniqueId()) == Role.HIDER;
        switch (type) {
            case "quit": {
                if (!right) {
                    return;
                }
                e.setCancelled(true);
                if (g != null) {
                    g.leave(p, true);
                } else {
                    this.sendToHub(p);
                }
                p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu as quitt\u00e9 le cache-cache.");
                break;
            }
            case "change": {
                e.setCancelled(true);
                if (!hider) {
                    return;
                }
                if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    Block b = e.getClickedBlock();
                    if (b == null) {
                        return;
                    }
                    if (!this.disguiseBlocks(g).containsKey(b.getType()) && !this.getConfig().getBoolean("allow-any-target", false)) {
                        p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cCe bloc n'est pas autoris\u00e9. \u00a77Clic droit dans le vide pour voir la liste.");
                        return;
                    }
                    g.changeBlock(p, b.getBlockData().clone());
                    break;
                }
                if (!right) break;
                this.openMenu(g, p);
                break;
            }
            case "rotate": {
                e.setCancelled(true);
                if (e.getAction() == Action.PHYSICAL || !hider) break;
                g.rotateBlock(p, !right);
                break;
            }
            case "taunt": {
                e.setCancelled(true);
                if (!right || !hider) break;
                g.taunt(p);
                break;
            }
            case "shockwave": {
                e.setCancelled(true);
                if (!right || g == null) break;
                g.shockwave(p);
                break;
            }
            case "radar": {
                e.setCancelled(true);
                if (!right || g == null) break;
                g.radar(p);
                break;
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        this.lastUse.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        if (this.isGameWorld(e.getPlayer().getWorld())) {
            this.scheduleResync(e.getPlayer());
            if (!this.admin(e.getPlayer())) {
                e.setCancelled(true);
            }
        }
    }

    // Teleportation ou chunk (re)envoye : un cacheur solide recoit le vrai bloc de sa propre case.
    @EventHandler(priority=EventPriority.MONITOR)
    public void onTeleportResync(PlayerTeleportEvent e) {
        if (this.isGameWorld(e.getTo() == null ? null : e.getTo().getWorld())) {
            this.scheduleResync(e.getPlayer());
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent e) {
        if (this.isGameWorld(e.getWorld())) {
            this.scheduleResync(e.getPlayer());
        }
    }

    // Cacheur solide : lui renvoie l'air de sa propre case au tick suivant puis 4 ticks plus tard (apres les
    // corrections que le serveur envoie au client a la suite d'un clic, d'une teleportation, d'un chunk).
    private void scheduleResync(Player p) {
        if (p == null || !this.resyncSoon.add(p.getUniqueId())) {
            return;
        }
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            this.resyncSoon.remove(id);
            this.resyncNow(id);
        }, 1L);
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.resyncNow(id), 4L);
    }

    private void resyncNow(UUID id) {
        Player v = Bukkit.getPlayer((UUID)id);
        if (v == null) {
            return;
        }
        Game g = this.gameAt(v.getWorld());
        if (g != null) {
            g.resendOwnAirTo(v);
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        HumanEntity humanEntity = e.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player p = (Player)humanEntity;
        Game g = this.gameOf(p);
        InventoryHolder inventoryHolder = e.getInventory().getHolder();
        if (inventoryHolder instanceof Menu) {
            Menu m = (Menu)inventoryHolder;
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory()) {
                return;
            }
            int slot = e.getRawSlot();
            if (slot < 0 || slot >= m.items.size() || g == null || g.roles.get(p.getUniqueId()) != Role.HIDER) {
                return;
            }
            Material mat = m.items.get(slot);
            p.closeInventory();
            g.changeBlock(p, this.normalized(mat.createBlockData()));
            return;
        }
        if (g != null && (g.roles.containsKey(p.getUniqueId()) || g.world != null && p.getWorld().equals((Object)g.world)) && !this.admin(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        this.lastUse.put(p.getUniqueId(), System.currentTimeMillis());
        Game g = this.gameOf(p);
        if (g != null && g.world != null && p.getWorld().equals((Object)g.world) && !this.admin(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = this.gameOf(e.getPlayer());
        if (g != null && g.roles.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player sp = e.getPlayer();
        for (Game g : new ArrayList<Game>(this.games.values())) {
            if (!g.spectators.contains(sp.getUniqueId()) || g.world != null && sp.getWorld().equals((Object)g.world)) continue;
            g.spectators.remove(sp.getUniqueId());
            this.showAll(sp);
            if (sp.getGameMode() != GameMode.SPECTATOR || !this.mayTouch(sp)) continue;
            sp.setGameMode(GameMode.SURVIVAL);
        }
        Game g = this.gameOf(sp);
        if (g == null) {
            return;
        }
        if (g.state == State.PREPARING) {
            g.leave(sp, false);
            sp.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Inscription au cache-cache annul\u00e9e.");
        } else if (g.world != null && e.getFrom().equals((Object)g.world)) {
            g.leave(sp, false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game g;
        this.lastUse.remove(e.getPlayer().getUniqueId());
        Game sg = this.spectatorGameOf(e.getPlayer());
        if (sg != null) {
            sg.spectators.remove(e.getPlayer().getUniqueId());
            this.showAll(e.getPlayer());
            this.sendToHub(e.getPlayer());
        }
        if ((g = this.gameOf(e.getPlayer())) != null) {
            g.leave(e.getPlayer(), g.state != State.PREPARING);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        this.later(() -> {
            if (!p.isOnline()) {
                return;
            }
            if (this.isGameWorld(p.getWorld()) && this.gameOf(p) == null && !p.hasPermission("eemihideseek.admin")) {
                this.sendToHub(p);
            }
            for (Game g : this.games.values()) {
                if (g.state != State.HIDING && g.state != State.SEEKING) continue;
                g.refreshVisibility();
            }
        });
    }

    private Game target(CommandSender sender, String[] args) {
        if (args.length > 1) {
            try {
                return this.games.get(Integer.parseInt(args[1]));
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (sender instanceof Player) {
            Player p = (Player)sender;
            Game g = this.gameOf(p);
            if (g == null) {
                g = this.spectatorGameOf(p);
            }
            if (g != null) {
                return g;
            }
        }
        return this.games.get(1);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player pl;
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player ? (pl = (Player)sender) : null;
        boolean admin = sender.hasPermission("eemihideseek.admin");
        switch (sub) {
            case "join": {
                Player target;
                Player player = target = args.length > 1 && admin ? Bukkit.getPlayerExact((String)args[1]) : p;
                if (target == null) {
                    sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cJoueur introuvable.");
                    return true;
                }
                this.join(target);
                break;
            }
            case "spectate": 
            case "spec": {
                if (p == null) break;
                this.spectate(p, args.length > 1 ? args[1] : null);
                break;
            }
            case "leave": {
                Game g;
                if (p != null && this.spectatorGameOf(p) != null) {
                    this.sendToHub(p);
                    return true;
                }
                Game game = g = p == null ? null : this.gameOf(p);
                if (g == null) {
                    return true;
                }
                g.leave(p, true);
                p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu as quitt\u00e9 le cache-cache.");
                break;
            }
            case "start": {
                if (!admin) {
                    return true;
                }
                Game g = this.target(sender, args);
                if (g == null) {
                    sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cPartie introuvable (\u00a7f/hideseek parties\u00a7c).");
                    return true;
                }
                if (g.state != State.WAITING && g.state != State.STARTING) {
                    sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cLa partie " + g.index + " est d\u00e9j\u00e0 en cours.");
                    return true;
                }
                if (g.inRoom().size() < 2) {
                    sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cIl faut au moins 2 joueurs en salle d'attente (partie " + g.index + ").");
                    return true;
                }
                g.startGame();
                sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Partie " + g.index + " lanc\u00e9e.");
                break;
            }
            case "stop": {
                if (!admin) {
                    return true;
                }
                Game g = this.target(sender, args);
                if (g == null) {
                    sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cPartie introuvable (\u00a7f/hideseek parties\u00a7c).");
                    return true;
                }
                g.forceStop();
                sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Partie " + g.index + " arr\u00eat\u00e9e.");
                break;
            }
            case "parties": 
            case "games": {
                if (!admin) {
                    return true;
                }
                sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Parties (" + this.games.size() + "/" + this.maxInstances() + ") :");
                for (Game g : this.games.values()) {
                    sender.sendMessage("\u00a77 #" + g.index + " \u00a7f" + (String)(g.world == null ? WorldClone.name(this.base, g.index) + " (copie en cours)" : g.world.getName()) + " \u00a77- " + g.stateName() + " - \u00a7f" + g.players.size() + "\u00a77 inscrit(s), \u00a7f" + g.spectators.size() + "\u00a77 spectateur(s)");
                }
                sender.sendMessage("\u00a77 Mod\u00e8le : \u00a7f" + (this.model != null ? this.model.getName() : (this.modelBusy || this.modelPending ? "en pr\u00e9paration" : "absent")));
                break;
            }
            case "build": {
                if (!admin) {
                    return true;
                }
                Game g1 = this.games.get(1);
                if (g1 == null) {
                    return true;
                }
                // /hideseek build [id|tout] : toutes les maps par defaut
                ArrayList<String> ids = new ArrayList<String>();
                if (args.length > 1 && !args[1].equalsIgnoreCase("tout") && !args[1].equalsIgnoreCase("all")) {
                    String id = args[1].toLowerCase();
                    if (!this.allMapIds().contains(id)) {
                        sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cMap inconnue. \u00a77Maps : \u00a7f" + String.join(", ", this.allMapIds()));
                        return true;
                    }
                    ids.add(id);
                } else {
                    ids.addAll(this.allMapIds());
                }
                if (this.modelBusy || this.copying > 0) {
                    sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cUne copie de monde est en cours, r\u00e9essaie dans quelques secondes.");
                    return true;
                }
                if (g1.state == State.STARTING) {
                    g1.broadcast("\u00a7cD\u00e9compte annul\u00e9 : reconstruction des maps par un admin.");
                }
                g1.buildMaps(sender, ids);
                break;
            }
            case "map":
            case "maps": {
                if (!admin) {
                    return true;
                }
                this.mapCommand(sender, args);
                break;
            }
            case "reload": {
                if (!admin) {
                    return true;
                }
                for (Game g : this.games.values()) {
                    if (g.state == State.WAITING || g.state == State.PREPARING) continue;
                    sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cAucune partie ne doit \u00eatre en cours.");
                    return true;
                }
                this.loadSettings();
                for (Game g : this.games.values()) {
                    if (g.world == null) continue;
                    this.prepareWorld(g.world);
                }
                if (this.model != null) {
                    this.prepareWorld(this.model);
                }
                sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7aConfig recharg\u00e9e.");
                break;
            }
            default: {
                sender.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77/hideseek <join|leave|spectate [joueur]>" + (admin ? " \u00a78| admin : start [n], stop [n], parties, map [id], build [id], reload" : ""));
            }
        }
        return true;
    }

    /**
     * /hideseek map : liste des maps et map de chaque partie. /hideseek map <id> : impose la prochaine map ; une
     * partie en attente (celle du joueur, sinon la partie 1) y passe tout de suite.
     */
    private void mapCommand(CommandSender sender, String[] args) {
        String P = "\u00a78[\u00a7dCache-cache\u00a78] \u00a77";
        if (args.length < 2) {
            sender.sendMessage(P + "\u00a77Maps (\u00a7f/hideseek map <id>\u00a77 pour imposer la prochaine) :");
            List<String> play = this.playableMaps(null);
            for (String id : this.allMapIds()) {
                sender.sendMessage("\u00a77 - \u00a7f" + id + " \u00a77(" + this.mapName(id) + ") : " + (this.mapReady(id) ? "\u00a7aconstruite" : "\u00a7cnon construite") + (play.contains(id) ? "\u00a77, tirable" : "\u00a77, hors tirage"));
            }
            for (Game g : this.games.values()) {
                sender.sendMessage("\u00a77 Partie #" + g.index + " : \u00a7f" + this.mapName(g.mapId) + " \u00a77(" + g.stateName() + ")");
            }
            if (this.forcedNext != null) {
                sender.sendMessage("\u00a77 Prochaine map imposee : \u00a7f" + this.mapName(this.forcedNext));
            }
            return;
        }
        String id = args[1].toLowerCase();
        if (!this.allMapIds().contains(id)) {
            sender.sendMessage(P + "\u00a7cMap inconnue. \u00a77Maps : \u00a7f" + String.join(", ", this.allMapIds()));
            return;
        }
        if (!this.mapReady(id)) {
            sender.sendMessage(P + "\u00a7cLa map " + id + " n'est pas construite (\u00a7f/hideseek build " + id + "\u00a7c).");
            return;
        }
        Game g = sender instanceof Player ? this.gameOf((Player)sender) : null;
        if (g == null) {
            g = this.games.get(1);
        }
        if (g != null && g.world != null && (g.state == State.WAITING || g.state == State.STARTING) && (g.available == null || g.available.contains(id))) {
            g.mapId = id;
            for (Player p : this.online(g.players)) {
                g.toWaiting(p);
            }
            g.broadcast("\u00a77Map de la prochaine partie : \u00a7f" + this.mapName(id) + "\u00a77.");
            sender.sendMessage(P + "\u00a7aPartie " + g.index + " : la prochaine map sera \u00a7f" + this.mapName(id) + "\u00a7a.");
            return;
        }
        this.forcedNext = id;
        sender.sendMessage(P + "\u00a7aLa prochaine partie qui tire une map jouera \u00a7f" + this.mapName(id) + "\u00a7a.");
    }
    private void spectate(Player p, String name) {
        Game old;
        if (this.gameOf(p) != null) {
            p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cTu es inscrit : \u00a7f/hideseek leave \u00a7cd'abord.");
            return;
        }
        Game g = null;
        if (name != null) {
            Player t = Bukkit.getPlayerExact((String)name);
            if (t == null) {
                p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cJoueur introuvable.");
                return;
            }
            g = this.gameOf(t);
            if (g == null) {
                g = this.spectatorGameOf(t);
            }
            if (g == null || g.state != State.HIDING && g.state != State.SEEKING) {
                p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7c" + t.getName() + " n'est dans aucune partie en cours.");
                return;
            }
        } else {
            for (Game c : this.games.values()) {
                if (c.state != State.HIDING && c.state != State.SEEKING || g != null && c.roles.size() <= g.roles.size()) continue;
                g = c;
            }
            if (g == null) {
                p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cAucune partie en cours.");
                return;
            }
        }
        if ((old = this.spectatorGameOf(p)) != null) {
            old.spectators.remove(p.getUniqueId());
        }
        g.spectate(p);
    }

    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore")) {
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)("eemi " + args));
        }
    }

    private void reportStatus() {
        boolean anyWaiting = false;
        boolean anyPlaying = false;
        int waiting = 0;
        int playing = 0;
        for (Game g : this.games.values()) {
            switch (g.state.ordinal()) {
                case 0: 
                case 1: 
                case 2: 
                case 6: {
                    anyWaiting = true;
                    break;
                }
                case 3: 
                case 4: 
                case 5: {
                    anyPlaying = true;
                    break;
                }
            }
            boolean inGame = g.state == State.HIDING || g.state == State.SEEKING || g.state == State.ENDING;
            for (Player p : this.online(g.players)) {
                Role r = g.roles.get(p.getUniqueId());
                if (inGame && r != null) {
                    if (r == Role.SPECTATOR) continue;
                    ++playing;
                    continue;
                }
                ++waiting;
            }
        }
        String st = anyWaiting ? "waiting" : (anyPlaying ? "playing" : "waiting");
        String status = "status hideseek " + st + " " + waiting + " " + playing + " " + this.cfg("max-players", 20) * this.maxInstances();
        long now = System.currentTimeMillis();
        if (status.equals(this.lastStatus) && now - this.lastStatusAt < 30000L) {
            return;
        }
        this.lastStatus = status;
        this.lastStatusAt = now;
        this.eemi(status);
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

    private final class Game {
        final int index;
        World world;
        boolean copyStarted;
        final Set<UUID> players = new LinkedHashSet<UUID>();
        final Map<UUID, Role> roles = new HashMap<UUID, Role>();
        final Map<UUID, Disguise> disguises = new HashMap<UUID, Disguise>();
        final Map<UUID, Integer> foundBy = new HashMap<UUID, Integer>();
        final Map<UUID, Integer> taunts = new HashMap<UUID, Integer>();
        final Map<UUID, Long> lastSwing = new HashMap<UUID, Long>();
        final Map<UUID, Scoreboard> boards = new HashMap<UUID, Scoreboard>();
        final Set<UUID> spectators = new LinkedHashSet<UUID>();
        // Chercheurs a 0 coeur : secondes avant leur retour (ils attendent dans la salle noire).
        final Map<UUID, Integer> respawn = new HashMap<UUID, Integer>();
        // Cacheurs : secondes avant leur prochain bruit force.
        final Map<UUID, Integer> noiseIn = new HashMap<UUID, Integer>();
        final Map<UUID, Long> lastShock = new HashMap<UUID, Long>();
        final Map<UUID, Long> lastRadar = new HashMap<UUID, Long>();
        final Map<UUID, Long> lastHiderHit = new HashMap<UUID, Long>();
        State state = State.WAITING;
        int timer;
        int total;
        int hidersAtStart;
        // Map de la partie (tiree a chaque partie) et maps presentes dans son monde (null = partie 1 : toutes).
        String mapId = VILLAGE;
        Set<String> available;

        Game(int index) {
            this.index = index;
        }

        Location point(String key) {
            return EemiHideSeek.this.mapPoint(this.world, this.mapId, key);
        }

        boolean running() {
            return this.state == State.HIDING || this.state == State.SEEKING || this.state == State.ENDING || this.state == State.BUILDING;
        }

        // Remet a l'etat d'origine les vrais blocs de tous les cacheurs solides (meme deconnectes).
        void releaseAllCells() {
            for (Disguise d : this.disguises.values()) {
                if (d.solid && d.block != null) {
                    EemiHideSeek.this.releaseCell(d.block);
                }
                d.solid = false;
                d.block = null;
            }
        }

        void shutdown() {
            for (Player p : EemiHideSeek.this.online(this.disguises.keySet())) {
                this.undisguise(p);
            }
            this.releaseAllCells();
            this.disguises.clear();
            EemiHideSeek.this.removeDisplays(this.world);
            for (Player p : EemiHideSeek.this.online(this.players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                EemiHideSeek.this.showAll(p);
                if (this.world == null || !p.getWorld().equals((Object)this.world)) continue;
                EemiHideSeek.this.sendToHub(p);
            }
            for (Player p : EemiHideSeek.this.online(this.spectators)) {
                EemiHideSeek.this.showAll(p);
                if (this.world == null || !p.getWorld().equals((Object)this.world)) continue;
                EemiHideSeek.this.sendToHub(p);
            }
        }

        /**
         * Construit (ou reconstruit) les maps donnees l'une apres l'autre dans le monde de la partie 1 (1.4.0).
         * Village : plan de MapBuilder, comme avant. Autres maps : dessinees hors du fil principal dans un
         * RecordingSink, puis posees boite entiere (air compris, chaque case une seule fois a son etat final, donc
         * jamais de trou intermediaire) par tranches de 20 000 cases par tick.
         */
        void buildMaps(CommandSender who, List<String> ids) {
            if (this.world == null || ids.isEmpty()) {
                return;
            }
            if (this.state != State.WAITING && this.state != State.STARTING) {
                if (who != null) {
                    who.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cImpossible pendant une partie.");
                }
                return;
            }
            this.state = State.BUILDING;
            this.nextBuild(who, new ArrayList<String>(ids), 0);
        }

        private void nextBuild(CommandSender who, List<String> ids, int i) {
            if (!EemiHideSeek.this.isEnabled()) {
                return;
            }
            if (this.world == null) {
                this.state = State.WAITING;
                return;
            }
            if (i >= ids.size()) {
                this.buildDone(who);
                return;
            }
            String id = ids.get(i);
            long t0 = System.currentTimeMillis();
            if (VILLAGE.equals(id)) {
                ArrayList<Map.Entry<Long, BlockData>> todo = new ArrayList<Map.Entry<Long, BlockData>>(new MapBuilder().plan().entrySet());
                EemiHideSeek.this.getLogger().info("Construction de la map Village : " + todo.size() + " positions...");
                if (who != null) {
                    who.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Construction de la map Village (" + todo.size() + " positions)...");
                }
                this.applyPlan(todo, 0, () -> {
                    this.world.save();
                    EemiHideSeek.this.mapState.set("version", (Object)1);
                    EemiHideSeek.this.saveMapState();
                    this.built(who, "Map Village construite (" + todo.size() + " positions, " + (System.currentTimeMillis() - t0) + " ms).");
                    this.nextBuild(who, ids, i + 1);
                });
                return;
            }
            HsMap hm = EemiHideSeek.this.builtMaps.get(id);
            if (hm == null) {
                this.nextBuild(who, ids, i + 1);
                return;
            }
            EemiHideSeek.this.getLogger().info("Dessin de la map " + hm.name + " (" + hm.id + ")...");
            if (who != null) {
                who.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Construction de la map " + hm.name + "...");
            }
            Bukkit.getScheduler().runTaskAsynchronously((Plugin)EemiHideSeek.this, () -> {
                RecordingSink rec = new RecordingSink();
                Throwable err = null;
                try {
                    hm.build(rec);
                }
                catch (Throwable ex) {
                    err = ex;
                }
                Throwable fail = err;
                if (!EemiHideSeek.this.isEnabled()) {
                    return;
                }
                Bukkit.getScheduler().runTask((Plugin)EemiHideSeek.this, () -> {
                    if (this.state != State.BUILDING) {
                        return;
                    }
                    if (fail != null) {
                        EemiHideSeek.this.getLogger().log(java.util.logging.Level.SEVERE, "Dessin de la map " + hm.id + " impossible", fail);
                        if (who != null) {
                            who.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cDessin de la map " + hm.name + " impossible (voir la console).");
                        }
                        this.nextBuild(who, ids, i + 1);
                        return;
                    }
                    // etats -> BlockData ; un etat invalide devient de l'air (signale une fois par etat)
                    BlockData[] pal = new BlockData[rec.paletteSize()];
                    int bad = 0;
                    for (int k = 0; k < pal.length; ++k) {
                        try {
                            pal[k] = Bukkit.createBlockData((String)rec.state(k));
                        }
                        catch (IllegalArgumentException ex) {
                            pal[k] = Material.AIR.createBlockData();
                            ++bad;
                            EemiHideSeek.this.getLogger().warning("Bloc invalide ignore (map " + hm.id + ") : " + rec.state(k));
                        }
                    }
                    int[] b = hm.box();
                    long n = (long)(b[3] - b[0] + 1) * (long)(b[4] - b[1] + 1) * (long)(b[5] - b[2] + 1);
                    int invalid = bad;
                    long blocks = rec.count();
                    this.chunkTickets(b, true);
                    this.applyBox(rec, pal, b, 0L, n, () -> {
                        this.chunkTickets(b, false);
                        // monde sauvegarde AVANT de noter la map construite (un crash ne laisse pas une map notee mais absente)
                        this.world.save();
                        EemiHideSeek.this.mapState.set("maps." + hm.id, (Object)hm.version);
                        for (Map.Entry<String, int[]> rm : RETIRED_MAPS.entrySet()) {
                            if (EemiHideSeek.this.mapState.isSet("maps." + rm.getKey()) && covers(b, rm.getValue())) {
                                EemiHideSeek.this.mapState.set("maps." + rm.getKey(), null);
                                EemiHideSeek.this.getLogger().info("Ancienne map " + rm.getKey() + " effacee (zone videe par la map " + hm.id + ").");
                            }
                        }
                        EemiHideSeek.this.saveMapState();
                        this.built(who, "Map " + hm.name + " construite (version " + hm.version + ") : " + blocks + " blocs, " + n + " cases, " + invalid + " etat(s) invalide(s), " + (System.currentTimeMillis() - t0) + " ms.");
                        this.nextBuild(who, ids, i + 1);
                    });
                });
            });
        }

        private void built(CommandSender who, String msg) {
            EemiHideSeek.this.getLogger().info(msg);
            if (who != null) {
                who.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7a" + msg);
            }
        }

        private void buildFailed(CommandSender who, String what, Throwable ex) {
            EemiHideSeek.this.getLogger().log(java.util.logging.Level.SEVERE, "Construction interrompue (" + what + ")", ex);
            if (who != null) {
                who.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cConstruction interrompue (" + what + "), voir la console.");
            }
            this.buildDone(who);
        }

        private void applyPlan(List<Map.Entry<Long, BlockData>> todo, int from, Runnable done) {
            if (!EemiHideSeek.this.isEnabled() || this.world == null || this.state != State.BUILDING) {
                return;
            }
            int end = Math.min(todo.size(), from + 20000);
            try {
            for (int i = from; i < end; ++i) {
                long k = todo.get(i).getKey();
                Block b = this.world.getBlockAt(MapBuilder.kx(k), MapBuilder.ky(k), MapBuilder.kz(k));
                BlockData d = todo.get(i).getValue();
                if (b.getBlockData().equals((Object)d)) continue;
                b.setBlockData(d, false);
            }
            }
            catch (RuntimeException ex) {
                this.buildFailed(null, "village", ex);
                return;
            }
            if (end < todo.size()) {
                Bukkit.getScheduler().runTaskLater((Plugin)EemiHideSeek.this, () -> this.applyPlan(todo, end, done), 1L);
                return;
            }
            done.run();
        }

        private void applyBox(RecordingSink rec, BlockData[] pal, int[] b, long from, long n, Runnable done) {
            if (!EemiHideSeek.this.isEnabled() || this.world == null || this.state != State.BUILDING) {
                return;
            }
            int sx = b[3] - b[0] + 1;
            int sz = b[5] - b[2] + 1;
            long end = Math.min(n, from + 20000L);
            try {
            for (long i = from; i < end; ++i) {
                int x = b[0] + (int)(i % sx);
                int z = b[2] + (int)(i / sx % sz);
                int y = b[1] + (int)(i / ((long)sx * sz));
                BlockData d = pal[rec.id(x, y, z)];
                Block blk = this.world.getBlockAt(x, y, z);
                if (blk.getBlockData().equals((Object)d)) continue;
                blk.setBlockData(d, false);
            }
            }
            catch (RuntimeException ex) {
                this.buildFailed(null, "pose", ex);
                return;
            }
            if (end < n) {
                long next = end;
                Bukkit.getScheduler().runTaskLater((Plugin)EemiHideSeek.this, () -> this.applyBox(rec, pal, b, next, n, done), 1L);
                return;
            }
            done.run();
        }

        private void chunkTickets(int[] b, boolean add) {
            if (this.world == null) {
                return;
            }
            for (int cx = b[0] >> 4; cx <= b[3] >> 4; ++cx) {
                for (int cz = b[2] >> 4; cz <= b[5] >> 4; ++cz) {
                    if (add) {
                        this.world.addPluginChunkTicket(cx, cz, (Plugin)EemiHideSeek.this);
                    } else {
                        this.world.removePluginChunkTicket(cx, cz, (Plugin)EemiHideSeek.this);
                    }
                }
            }
        }

        private void buildDone(CommandSender who) {
            if (this.world != null) {
                this.world.removePluginChunkTickets((Plugin)EemiHideSeek.this);
            }
            this.state = State.WAITING;
            if (!EemiHideSeek.this.mapReady(this.mapId)) {
                this.mapId = EemiHideSeek.this.pickMap(null, null);
            }
            if (who != null) {
                who.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7aConstruction terminee. \u00a77Le mod\u00e8le des autres parties va \u00eatre refait.");
            }
            EemiHideSeek.this.modelPending = true;
            for (Player p : EemiHideSeek.this.online(this.players)) {
                this.toWaiting(p);
            }
        }
        void second() {
            if (this.world == null) {
                return;
            }
            if (this.world.getTime() < 5000L || this.world.getTime() > 7000L) {
                this.world.setTime(6000L);
            }
            switch (this.state.ordinal()) {
                case 1: {
                    if (this.inRoom().size() < EemiHideSeek.this.cfg("min-players", 3)) break;
                    this.state = State.STARTING;
                    this.timer = EemiHideSeek.this.cfg("countdown", 20);
                    this.broadcast("\u00a7eAssez de joueurs : la partie commence dans \u00a76" + this.timer + " s\u00a7e.");
                    break;
                }
                case 2: {
                    if (this.inRoom().size() < EemiHideSeek.this.cfg("min-players", 3)) {
                        this.state = State.WAITING;
                        this.broadcast("\u00a7cPas assez de joueurs, lancement annul\u00e9.");
                        break;
                    }
                    if (this.timer <= 5 && this.timer > 0) {
                        for (Player player : this.inRoom()) {
                            player.showTitle(Title.title((Component)Component.text((String)String.valueOf(this.timer), (TextColor)NamedTextColor.GREEN).decorate(TextDecoration.BOLD), (Component)Component.empty(), (Title.Times)Title.Times.times((Duration)Duration.ZERO, (Duration)Duration.ofMillis(900L), (Duration)Duration.ofMillis(100L))));
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        }
                    }
                    if (this.timer <= 0) {
                        this.startGame();
                        break;
                    }
                    --this.timer;
                    break;
                }
                case 3: {
                    for (Player player : EemiHideSeek.this.online(this.role(Role.SEEKER))) {
                        player.sendActionBar((Component)Component.text((String)("Les cacheurs se cachent... " + this.timer + " s"), (TextColor)NamedTextColor.RED));
                    }
                    if (this.timer <= 0) {
                        this.release();
                        break;
                    }
                    --this.timer;
                    break;
                }
                case 4: {
                    this.respawnSecond();
                    this.forcedNoiseSecond();
                    int reveal = EemiHideSeek.this.cfg("final-reveal-time", 60);
                    if (this.timer == reveal) {
                        this.broadcastGame("\u00a76Plus que " + reveal + " secondes ! \u00a77Les cacheurs se font entendre...");
                        for (Player p : EemiHideSeek.this.online(this.roles.keySet())) {
                            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
                        }
                    }
                    if (this.timer <= reveal && this.timer > 0 && this.timer % Math.max(1, EemiHideSeek.this.cfg("reveal-interval", 10)) == 0) {
                        this.revealHiders();
                    }
                    if (this.timer <= 0) {
                        this.endGame(true);
                        break;
                    }
                    --this.timer;
                    break;
                }
                case 5: {
                    if (--this.timer > 0) break;
                    this.finishGame();
                    break;
                }
            }
            if (EemiHideSeek.this.games.get(this.index) != this) {
                return;
            }
            for (Player player : EemiHideSeek.this.online(this.players)) {
                if (this.world == null || !player.getWorld().equals((Object)this.world)) continue;
                this.updateSidebar(player);
                this.hideNames(player);
            }
        }

        void tick() {
            if (this.world == null || this.state != State.HIDING && this.state != State.SEEKING) {
                return;
            }
            int solidTicks = (int)Math.round(EemiHideSeek.this.cfgD("solid-delay", 3.5) * 20.0);
            for (Map.Entry<UUID, Disguise> en : new ArrayList<Map.Entry<UUID, Disguise>>(this.disguises.entrySet())) {
                Player h = Bukkit.getPlayer((UUID)en.getKey());
                Disguise d = en.getValue();
                if (h == null || !h.getWorld().equals((Object)this.world)) continue;
                // Pas de particules d'atterrissage (envoyees a tous, meme a ceux qui ne voient pas le cacheur).
                h.setFallDistance(0.0f);
                Location l = h.getLocation();
                if (d.solid) {
                    // 1.3.2 : le cacheur ne quitte son bloc que s'il appuie sur une touche de deplacement (ou s'il
                    // a ete deplace de loin). Un petit glissement sans touche = son client l'a pousse hors du vrai
                    // bloc le temps de recevoir l'air de sa case : on le remet au centre.
                    Input in = h.getCurrentInput();
                    boolean wants = in != null && (in.isForward() || in.isBackward() || in.isLeft() || in.isRight() || in.isJump());
                    double dist2 = d.anchor == null || !d.anchor.getWorld().equals((Object)l.getWorld()) ? 99.0 : l.distanceSquared(d.anchor);
                    if (wants || dist2 > 2.25) {
                        this.unsolidify(h, d);
                        d.anchor = l.clone();
                        d.still = 0;
                    } else if (dist2 > 0.0025) {
                        Location back = d.anchor.clone();
                        back.setYaw(l.getYaw());
                        back.setPitch(l.getPitch());
                        h.setVelocity(new Vector());
                        h.teleport(back);
                        this.sendOwnAir(h, d);
                    }
                } else {
                    // Touche de deplacement enfoncee (meme bloque contre un mur) : pas immobile, sinon le bloc
                    // apparaitrait puis disparaitrait aussitot (clignotement visible de tous).
                    Input in2 = h.getCurrentInput();
                    boolean pressing = in2 != null && (in2.isForward() || in2.isBackward() || in2.isLeft() || in2.isRight() || in2.isJump());
                    if (pressing || d.anchor == null || !d.anchor.getWorld().equals((Object)l.getWorld()) || l.distanceSquared(d.anchor) > 0.0025) {
                        d.anchor = l.clone();
                        d.still = 0;
                        d.blocked = false;
                    } else {
                        ++d.still;
                    }
                    this.follow(h, d);
                    if (this.checkPerch(h, d, l)) continue;
                    if (d.still >= solidTicks && !d.blocked && !d.perched) {
                        this.solidify(h, d);
                    }
                }
                if (EemiHideSeek.this.ticks % 5L != 0L) continue;
                this.actionBar(h, d, solidTicks);
            }
            if (EemiHideSeek.this.ticks % 5L == 0L) {
                this.resendOwnAir();
            }
            if (EemiHideSeek.this.ticks % 10L == 0L) {
                this.syncDisplays();
            }
        }

        /**
         * Regle anti-perchoir (1.6.0, carte #135) pour un cacheur non solide. Pose au sol : perchoir interdit (voir
         * {@link Perch}) ou non ; au sol hors perchoir, la position devient son dernier point valide. Sur un perchoir
         * plus de roof-return-delay secondes : ramene a ce point (sans degats). Vrai si le cacheur vient d'etre ramene.
         */
        private boolean checkPerch(Player h, Disguise d, Location l) {
            if (!EemiHideSeek.this.getConfig().getBoolean("roof-block", true) || this.roles.get(h.getUniqueId()) != Role.HIDER) {
                d.perched = false;
                d.perchTicks = 0;
                return false;
            }
            if (((Entity)h).isOnGround()) {
                d.perched = EemiHideSeek.this.perched(this, l);
                if (!d.perched) {
                    d.safe = l.clone();
                }
            }
            if (!d.perched) {
                d.perchTicks = 0;
                return false;
            }
            if (++d.perchTicks < (int)Math.round(EemiHideSeek.this.cfgD("roof-return-delay", 2.0) * 20.0)) {
                return false;
            }
            Location back = d.safe;
            if (back == null || !this.world.equals((Object)back.getWorld()) || EemiHideSeek.this.placed.containsKey(back.getBlock()) || EemiHideSeek.this.placed.containsKey(back.getBlock().getRelative(BlockFace.UP))) {
                back = this.point("hider-spawn");
            }
            back = back.clone();
            back.setYaw(l.getYaw());
            back.setPitch(l.getPitch());
            h.setVelocity(new Vector());
            h.teleport(back);
            h.setFallDistance(0.0f);
            d.anchor = back.clone();
            d.still = 0;
            d.perched = false;
            d.perchTicks = 0;
            this.follow(h, d);
            h.sendMessage("§8[§dCache-cache§8] §7§cPas de cachette sur les toits ni en hauteur ! §7Te revoilà en bas.");
            h.playSound(h.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
            return true;
        }

        private void actionBar(Player h, Disguise d, int solidTicks) {
            Object c = !d.solid && d.perched ? Component.text((String)"Trop haut : pas de cachette sur les toits, redescends !", (TextColor)NamedTextColor.RED) : d.solid ? ((TextComponent)Component.text((String)"SOLIDE", (TextColor)NamedTextColor.GREEN).decorate(TextDecoration.BOLD)).append((Component)Component.text((String)(" - ne bouge pas (" + EemiHideSeek.this.blockName(this, d.data) + ")"), (TextColor)NamedTextColor.GRAY)) : (d.blocked ? Component.text((String)"Impossible de devenir solide ici", (TextColor)NamedTextColor.RED) : (d.still > 0 ? Component.text((String)String.format("Solide dans %.1f s", (double)Math.max(0, solidTicks - d.still) / 20.0), (TextColor)NamedTextColor.YELLOW) : Component.text((String)("En mouvement - " + EemiHideSeek.this.blockName(this, d.data)), (TextColor)NamedTextColor.GRAY)));
            h.sendActionBar((Component)c);
        }

        private void follow(Player h, Disguise d) {
            Location l = h.getLocation();
            Location at = new Location(this.world, l.getX(), l.getY(), l.getZ(), 0.0f, 0.0f);
            if (d.display == null || !d.display.isValid()) {
                d.display = this.spawnDisplay(at, d.data);
                this.syncDisplay(h, d);
            } else {
                d.display.teleport(at);
            }
        }

        private BlockDisplay spawnDisplay(Location at, BlockData data) {
            return (BlockDisplay)this.world.spawn(at, BlockDisplay.class, e -> {
                e.setVisibleByDefault(false);
                e.setBlock(data);
                e.setTransformation(new Transformation(new Vector3f(-0.5f, 0.0f, -0.5f), new AxisAngle4f(), new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
                e.setTeleportDuration(2);
                e.setPersistent(false);
                e.addScoreboardTag(EemiHideSeek.TAG);
            });
        }

        private boolean seesHiders(Player v) {
            return this.roles.get(v.getUniqueId()) != Role.HIDER;
        }

        private void syncDisplay(Player h, Disguise d) {
            if (d.display == null || !d.display.isValid() || this.world == null) {
                return;
            }
            for (Player v : this.world.getPlayers()) {
                if (v == h || !d.solid && this.seesHiders(v)) {
                    v.showEntity((Plugin)EemiHideSeek.this, (Entity)d.display);
                    continue;
                }
                v.hideEntity((Plugin)EemiHideSeek.this, (Entity)d.display);
            }
        }

        private void syncDisplays() {
            for (Map.Entry<UUID, Disguise> en : this.disguises.entrySet()) {
                Player h = Bukkit.getPlayer((UUID)en.getKey());
                if (h == null) continue;
                this.syncDisplay(h, en.getValue());
            }
        }

        private void solidify(Player h, Disguise d) {
            Location l = h.getLocation();
            int by = (int)Math.floor(l.getY() + 0.1);
            Block b = this.world.getBlockAt(l.getBlockX(), by, l.getBlockZ());
            boolean replaceable = b.isEmpty() || b.isReplaceable() || b.getType().name().endsWith("_CARPET");
            boolean taken = false;
            for (Disguise o : this.disguises.values()) {
                if (o == d || !o.solid || !b.equals((Object)o.block)) continue;
                taken = true;
            }
            // 1.3.2 : la case devient un vrai bloc. Refusee si elle contient un liquide, un bloc de lumiere,
            // une moitie de plante double (l'autre moitie resterait en l'air) ou si une plante double la surmonte.
            Material om = b.getType();
            Block above = b.getRelative(BlockFace.UP);
            boolean badCell = b.isLiquid() || om == Material.LIGHT || om == Material.BUBBLE_COLUMN || om == Material.WATER || om == Material.LAVA || b.getBlockData() instanceof Bisected || above.getBlockData() instanceof Bisected && !(above.getBlockData() instanceof org.bukkit.block.data.type.Stairs) && !(above.getBlockData() instanceof org.bukkit.block.data.type.TrapDoor) && !(above.getBlockData() instanceof Door) || b.getBlockData() instanceof org.bukkit.block.data.Waterlogged && ((org.bukkit.block.data.Waterlogged)b.getBlockData()).isWaterlogged();
            if (!replaceable || badCell || taken || Math.abs(l.getY() - (double)by) > 0.1 || EemiHideSeek.this.placed.containsKey(b)) {
                d.blocked = true;
                return;
            }
            // Un autre joueur deborde sur la case (ou s'en approche : son client est en avance de son ping) :
            // on ne l'enferme pas dedans. Marge de 0,3 bloc + 3 ticks de sa vitesse pour les non-cacheurs.
            BoundingBox cell = BoundingBox.of((Block)b);
            for (Player o : this.world.getPlayers()) {
                if (o == h || o.getGameMode() == GameMode.SPECTATOR) continue;
                BoundingBox ob = o.getBoundingBox();
                if (this.roles.get(o.getUniqueId()) != Role.HIDER) {
                    Vector v = o.getVelocity();
                    ob = ob.clone().expand(0.3 + Math.abs(v.getX()) * 3.0, 0.3, 0.3 + Math.abs(v.getZ()) * 3.0);
                }
                if (!ob.overlaps(cell)) continue;
                d.blocked = true;
                return;
            }
            Location snap = new Location(this.world, (double)b.getX() + 0.5, l.getY(), (double)b.getZ() + 0.5, l.getYaw(), l.getPitch());
            h.teleport(snap);
            d.anchor = snap.clone();
            d.solid = true;
            d.block = b;
            if (d.display == null || !d.display.isValid()) {
                d.display = this.spawnDisplay(new Location(this.world, snap.getX(), snap.getY(), snap.getZ()), d.data);
            } else {
                d.display.teleport(new Location(this.world, snap.getX(), snap.getY(), snap.getZ()));
            }
            // 1.3.2 : vrai bloc pour tout le monde (meme comportement qu'un bloc du decor au clic, a la
            // collision, aux projectiles, a la lumiere). Le cacheur garde son BlockDisplay.
            EemiHideSeek.this.placeCell(b, d.data);
            this.syncDisplay(h, d);
            this.sendOwnAirSoon(h);
            h.playSound(h.getLocation(), Sound.BLOCK_STONE_PLACE, 0.6f, 1.4f);
        }

        private void unsolidify(Player h, Disguise d) {
            if (!d.solid) {
                return;
            }
            d.solid = false;
            if (d.block != null) {
                EemiHideSeek.this.releaseCell(d.block);
            }
            d.block = null;
            this.follow(h, d);
            this.syncDisplay(h, d);
        }

        // Le cacheur solide voit de l'air dans sa propre case (son BlockDisplay a la place) : le vrai bloc a ses
        // pieds ferait reagir son client (il le pousserait dehors).
        private void sendOwnAir(Player h, Disguise d) {
            if (!d.solid || d.block == null || !h.getWorld().equals((Object)d.block.getWorld())) {
                return;
            }
            BlockData orig = EemiHideSeek.this.placed.get(d.block);
            h.sendBlockChange(d.block.getLocation(), orig != null ? orig : Material.AIR.createBlockData());
        }

        // Au tick suivant (apres la diffusion des blocs modifies, faite en fin de tick) puis 3 ticks plus tard.
        private void sendOwnAirSoon(Player h) {
            UUID id = h.getUniqueId();
            for (long delay : new long[]{1L, 3L}) {
                Bukkit.getScheduler().runTaskLater((Plugin)EemiHideSeek.this, () -> {
                    Player p = Bukkit.getPlayer((UUID)id);
                    Disguise d = this.disguises.get(id);
                    if (p != null && d != null) {
                        this.sendOwnAir(p, d);
                    }
                }, delay);
            }
        }

        private void resendOwnAir() {
            for (Map.Entry<UUID, Disguise> en : this.disguises.entrySet()) {
                Disguise d = en.getValue();
                if (!d.solid) continue;
                Player h = Bukkit.getPlayer((UUID)en.getKey());
                if (h != null) {
                    this.sendOwnAir(h, d);
                }
            }
        }

        // Air de sa propre case pour un cacheur solide (apres un clic, une teleportation, un chunk).
        void resendOwnAirTo(Player v) {
            if (this.world == null || this.state != State.HIDING && this.state != State.SEEKING || !v.getWorld().equals((Object)this.world)) {
                return;
            }
            Disguise d = this.disguises.get(v.getUniqueId());
            if (d != null) {
                this.sendOwnAir(v, d);
            }
        }

        private void disguise(Player h, BlockData data) {
            Disguise d = this.disguises.computeIfAbsent(h.getUniqueId(), k -> new Disguise());
            boolean wasSolid = d.solid;
            if (wasSolid) {
                this.unsolidify(h, d);
            }
            d.data = data;
            d.still = 0;
            d.blocked = false;
            d.anchor = null;
            if (d.display != null && d.display.isValid()) {
                d.display.setBlock(data);
            } else {
                this.follow(h, d);
            }
            h.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, -1, 0, false, false, false));
        }

        private void undisguise(Player h) {
            Disguise d = this.disguises.remove(h.getUniqueId());
            if (d != null) {
                if (d.solid && d.block != null) {
                    EemiHideSeek.this.releaseCell(d.block);
                }
                d.solid = false;
                d.block = null;
                if (d.display != null) {
                    d.display.remove();
                }
            }
            if (EemiHideSeek.this.mayTouch(h)) {
                h.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
            for (Player v : Bukkit.getOnlinePlayers()) {
                if (v == h) continue;
                v.showPlayer((Plugin)EemiHideSeek.this, h);
            }
        }

        void changeBlock(Player h, BlockData data) {
            long cd;
            Disguise d = this.disguises.get(h.getUniqueId());
            if (d == null) {
                return;
            }
            if (EemiHideSeek.this.unusableDisguise(data)) {
                h.sendMessage("§8[§dCache-cache§8] §7§cCe bloc n'est pas autorisé.");
                return;
            }
            data = EemiHideSeek.this.normalized(data);
            long now = System.currentTimeMillis();
            long l = cd = this.state == State.SEEKING ? (long)EemiHideSeek.this.cfg("change-cooldown", 3) * 1000L : 500L;
            if (now - d.lastChange < cd) {
                h.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cAttends encore " + String.format("%.1f", (double)(cd - (now - d.lastChange)) / 1000.0) + " s avant de changer de bloc.");
                return;
            }
            d.lastChange = now;
            this.disguise(h, data);
            h.playSound(h.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            h.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu es maintenant : \u00a7a" + EemiHideSeek.this.blockName(this, data));
        }

        void rotateBlock(Player h, boolean flip) {
            Disguise d = this.disguises.get(h.getUniqueId());
            if (d == null || d.data == null) {
                return;
            }
            BlockData n = d.data.clone();
            if (!flip) {
                n.rotate(StructureRotation.CLOCKWISE_90);
            } else if (n instanceof Slab && ((Slab)n).getType() != Slab.Type.DOUBLE) {
                Slab sl = (Slab)n;
                sl.setType(sl.getType() == Slab.Type.TOP ? Slab.Type.BOTTOM : Slab.Type.TOP);
            } else if (n instanceof Bisected && !(n instanceof Door)) {
                Bisected bi = (Bisected)n;
                bi.setHalf(bi.getHalf() == Bisected.Half.TOP ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            } else if (n instanceof Orientable) {
                Orientable o = (Orientable)n;
                ArrayList<Axis> axes = new ArrayList<Axis>(List.of(Axis.Y, Axis.X, Axis.Z));
                axes.retainAll(o.getAxes());
                if (!axes.isEmpty()) {
                    o.setAxis(axes.get((axes.indexOf(o.getAxis()) + 1) % axes.size()));
                }
            } else if (n instanceof Directional) {
                Directional di = (Directional)n;
                ArrayList<BlockFace> faces = new ArrayList<BlockFace>(List.of(BlockFace.NORTH, BlockFace.UP, BlockFace.DOWN));
                faces.retainAll(di.getFaces());
                BlockFace cur = di.getFacing();
                if (faces.size() > 1) {
                    di.setFacing(faces.get((faces.indexOf(cur) + 1) % faces.size()));
                }
            }
            if (n.equals((Object)d.data)) {
                h.sendActionBar((Component)Component.text((String)("Ce bloc ne peut pas " + (flip != false ? "se retourner" : "tourner")), (TextColor)NamedTextColor.RED));
                return;
            }
            d.data = n;
            if (d.display != null && d.display.isValid()) {
                d.display.setBlock(n);
            }
            if (d.solid && d.block != null) {
                EemiHideSeek.this.placeCell(d.block, n);
                this.sendOwnAirSoon(h);
            }
            h.playSound(h.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        }

        void taunt(Player h) {
            long cd;
            Disguise d = this.disguises.get(h.getUniqueId());
            if (d == null) {
                return;
            }
            if (this.state != State.SEEKING) {
                h.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu pourras provoquer quand les chercheurs seront l\u00e2ches.");
                return;
            }
            long now = System.currentTimeMillis();
            if (now - d.lastTaunt < (cd = (long)EemiHideSeek.this.cfg("taunt-cooldown", 15) * 1000L)) {
                h.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cProvocation disponible dans " + (cd - (now - d.lastTaunt) + 999L) / 1000L + " s.");
                return;
            }
            d.lastTaunt = now;
            Location at = (d.solid && d.block != null ? d.block.getLocation() : h.getLocation().getBlock().getLocation()).add(0.5, 0.6, 0.5);
            Sound[] sounds = new Sound[]{Sound.ENTITY_CAT_AMBIENT, Sound.ENTITY_VILLAGER_YES, Sound.ENTITY_CHICKEN_AMBIENT, Sound.ENTITY_GOAT_SCREAMING_AMBIENT, Sound.BLOCK_NOTE_BLOCK_BELL};
            this.world.playSound(at, sounds[EemiHideSeek.this.random.nextInt(sounds.length)], 1.5f, 1.0f);
            this.world.spawnParticle(Particle.NOTE, at.clone().add(0.0, 0.8, 0.0), 6, 0.4, 0.3, 0.4, 1.0);
            this.world.spawnParticle(Particle.FIREWORK, at, 20, 0.3, 0.4, 0.3, 0.05);
            this.taunts.merge(h.getUniqueId(), 1, Integer::sum);
            int bonus = EemiHideSeek.this.cfg("taunt-time-bonus", 5);
            if (this.timer > bonus + 5) {
                this.timer -= bonus;
            }
            this.broadcastGame("\u00a7dUn cacheur provoque les chercheurs ! \u00a77(-" + bonus + " s)");
            h.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7d+1 point de provocation \u00a77(total " + String.valueOf(this.taunts.get(h.getUniqueId())) + ").");
        }

        private void revealHiders() {
            for (Player h : EemiHideSeek.this.online(this.role(Role.HIDER))) {
                Disguise d = this.disguises.get(h.getUniqueId());
                Location at = (d != null && d.solid && d.block != null ? d.block.getLocation() : h.getLocation().getBlock().getLocation()).add(0.5, 0.6, 0.5);
                this.world.spawnParticle(Particle.FIREWORK, at, 25, 0.3, 0.5, 0.3, 0.05);
                this.world.spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.4, 0.4, 0.4, 0.0);
                this.world.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, 1.6f);
            }
        }

        void swing(Player s) {
            if (this.state != State.SEEKING || this.roles.get(s.getUniqueId()) != Role.SEEKER || !s.getWorld().equals((Object)this.world)) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = this.lastSwing.get(s.getUniqueId());
            if (last != null && now - last < (long)EemiHideSeek.this.cfg("hit-cooldown-ms", 400)) {
                return;
            }
            if (this.respawn.containsKey(s.getUniqueId())) {
                return;
            }
            // 1.3.1 : seule l'epee du chercheur touche un cacheur (ou coute un coeur). Main nue, radar,
            // onde de choc... : le bras bouge mais rien ne se passe.
            if (!"sword".equals(EemiHideSeek.this.kitType(s.getInventory().getItemInMainHand()))) {
                return;
            }
            // Bras qui bouge a cause d'un clic droit ou d'un objet lache (touche Q) : ce n'est pas un coup.
            Long usedAt = EemiHideSeek.this.lastUse.get(s.getUniqueId());
            if (usedAt != null && now - usedAt < 250L) {
                return;
            }
            double range = EemiHideSeek.this.cfgD("hit-range", 4.0);
            Location eye = s.getEyeLocation();
            Vector start = eye.toVector();
            Vector dir = eye.getDirection();
            RayTraceResult wall = this.world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
            double max = wall == null ? range : wall.getHitPosition().distance(start);
            Player best = this.aimedHider(start, dir, range, max);
            if (best == null) {
                if (this.isWrongHit(s, eye, dir, wall, max, now)) {
                    this.lastSwing.put(s.getUniqueId(), now);
                    this.wrongHit(s);
                }
                return;
            }
            this.lastSwing.put(s.getUniqueId(), now);
            this.lastHiderHit.put(s.getUniqueId(), now);
            this.hit(s, best);
        }

        // Cacheur vise le plus proche (bloc solide ou deguisement en mouvement), avant le premier vrai bloc (max).
        private Player aimedHider(Vector start, Vector dir, double range, double max) {
            Player best = null;
            double bestDist = max + 0.01;
            for (Player h : EemiHideSeek.this.online(this.role(Role.HIDER))) {
                double dist;
                RayTraceResult r;
                BoundingBox box;
                Disguise d = this.disguises.get(h.getUniqueId());
                if (d == null || !h.getWorld().equals((Object)this.world)) continue;
                if (d.solid && d.block != null) {
                    box = BoundingBox.of((Block)d.block);
                } else {
                    Location l = h.getLocation();
                    box = new BoundingBox(l.getX() - 0.55, l.getY(), l.getZ() - 0.55, l.getX() + 0.55, l.getY() + 1.05, l.getZ() + 0.55);
                }
                if ((r = box.rayTrace(start, dir, range)) == null || !((dist = r.getHitPosition().distance(start)) < bestDist)) continue;
                bestDist = dist;
                best = h;
            }
            return best;
        }

        // Coup dans le decor : vrai bloc de la map vise a portee, epee en main, pas un clic droit ni un objet lache
        // (le bras bouge aussi), pas un joueur visible devant le bloc, pas une barriere invisible.
        private boolean isWrongHit(Player s, Location eye, Vector dir, RayTraceResult wall, double max, long now) {
            if (wall == null || wall.getHitBlock() == null) {
                return false;
            }
            if (EemiHideSeek.this.cfgD("wrong-hit-cost", 1.0) <= 0.0) {
                return false;
            }
            if (!"sword".equals(EemiHideSeek.this.kitType(s.getInventory().getItemInMainHand()))) {
                return false;
            }
            Long used = EemiHideSeek.this.lastUse.get(s.getUniqueId());
            if (used != null && now - used < 250L) {
                return false;
            }
            Block b = wall.getHitBlock();
            Material m = b.getType();
            if (m == Material.BARRIER || m == Material.LIGHT || m == Material.STRUCTURE_VOID || b.isEmpty()) {
                return false;
            }
            for (Disguise d : this.disguises.values()) {
                if (!d.solid || d.block == null || !d.block.equals((Object)b)) continue;
                return false;
            }
            // Juste apres avoir touche un cacheur (il s'enfuit) : pas de cout.
            Long hitAt = this.lastHiderHit.get(s.getUniqueId());
            if (hitAt != null && now - hitAt < 1500L) {
                return false;
            }
            // Tolerance : le chercheur voit les cacheurs avec du retard (ping, deplacement du display).
            // Un rayon qui passe pres d'un cacheur (boite elargie) ne coute rien.
            Vector start = eye.toVector();
            double range = EemiHideSeek.this.cfgD("hit-range", 4.0) + 1.5;
            for (Player h : EemiHideSeek.this.online(this.role(Role.HIDER))) {
                Disguise d = this.disguises.get(h.getUniqueId());
                if (d == null || !h.getWorld().equals((Object)this.world)) continue;
                BoundingBox box;
                if (d.solid && d.block != null) {
                    box = BoundingBox.of((Block)d.block).expand(0.15);
                } else {
                    Location l = h.getLocation();
                    box = new BoundingBox(l.getX() - 0.55, l.getY(), l.getZ() - 0.55, l.getX() + 0.55, l.getY() + 1.05, l.getZ() + 0.55).expand(1.5);
                }
                if (box.rayTrace(start, dir, range) != null) {
                    return false;
                }
            }
            RayTraceResult ent = this.world.rayTraceEntities(eye, dir, max, 0.3, en -> en instanceof Player && en != s && ((Player)en).getGameMode() != GameMode.SPECTATOR && s.canSee((Player)en));
            return ent == null;
        }

        private double heartsHp(Player s) {
            AttributeInstance a = s.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = a == null ? 20.0 : a.getValue();
            return Math.max(1.0, Math.min(maxHp, (double)Math.max(1, EemiHideSeek.this.cfg("seeker-hearts", 10)) * 2.0));
        }

        private void setSeekerHearts(Player s) {
            if (!EemiHideSeek.this.mayTouch(s)) {
                return;
            }
            s.setHealth(this.heartsHp(s));
        }

        private String heartsText(double hp) {
            return EemiHideSeek.this.fmt(Math.max(0.0, hp) / 2.0);
        }

        private void wrongHit(Player s) {
            double cost = EemiHideSeek.this.cfgD("wrong-hit-cost", 1.0) * 2.0;
            double hp = s.getHealth() - cost;
            s.playSound(s.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.8f);
            if (hp <= 0.01) {
                this.startRespawn(s);
                return;
            }
            s.setHealth(hp);
            s.playHurtAnimation(0.0f);
            s.sendActionBar(Component.text((String)"Mauvais bloc ! ", (TextColor)NamedTextColor.RED).append((Component)Component.text((String)("-" + EemiHideSeek.this.fmt(cost / 2.0) + " cœur, " + this.heartsText(hp) + " restant(s)"), (TextColor)NamedTextColor.GRAY)));
        }

        private void startRespawn(Player s) {
            int secs = Math.max(1, EemiHideSeek.this.cfg("seeker-respawn", 15));
            this.respawn.put(s.getUniqueId(), secs);
            if (!EemiHideSeek.this.mayTouch(s)) {
                return;
            }
            s.setHealth(Math.min(1.0, s.getHealth()));
            s.getInventory().clear();
            s.removePotionEffect(PotionEffectType.SPEED);
            s.teleport(this.point("seeker-room"));
            s.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, -1, 0, false, false, false));
            s.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, -1, 6, false, false, false));
            s.showTitle(Title.title((Component)Component.text((String)"Plus de cœurs !", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD), (Component)Component.text((String)("Retour dans " + secs + " s"), (TextColor)NamedTextColor.GRAY)));
            s.playSound(s.getLocation(), Sound.ENTITY_PLAYER_DEATH, 0.8f, 1.0f);
            this.broadcastGame("§e" + s.getName() + " §7a tapé trop de blocs : retour dans §f" + secs + " s§7.");
        }

        private void endRespawn(Player s) {
            this.respawn.remove(s.getUniqueId());
            if (!EemiHideSeek.this.mayTouch(s) || !s.getWorld().equals((Object)this.world)) {
                return;
            }
            s.removePotionEffect(PotionEffectType.BLINDNESS);
            s.removePotionEffect(PotionEffectType.SLOWNESS);
            s.teleport(this.point("seeker-spawn"));
            EemiHideSeek.this.equipSeeker(s);
            this.setSeekerHearts(s);
            s.showTitle(Title.title((Component)Component.text((String)"De retour !", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD), (Component)Component.text((String)(EemiHideSeek.this.fmt(this.heartsHp(s) / 2.0) + " cœurs"), (TextColor)NamedTextColor.GRAY)));
            s.playSound(s.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.2f);
        }

        private void respawnSecond() {
            for (UUID id : new ArrayList<UUID>(this.respawn.keySet())) {
                Player s = Bukkit.getPlayer((UUID)id);
                if (s == null || this.roles.get(id) != Role.SEEKER) {
                    this.respawn.remove(id);
                    continue;
                }
                int left = this.respawn.get(id) - 1;
                if (left <= 0) {
                    this.endRespawn(s);
                    continue;
                }
                this.respawn.put(id, left);
                s.sendActionBar((Component)Component.text((String)("Retour en jeu dans " + left + " s"), (TextColor)NamedTextColor.RED));
            }
        }

        private int nextNoise() {
            int min = EemiHideSeek.this.cfg("forced-sound-min", 45);
            int max = Math.max(min, EemiHideSeek.this.cfg("forced-sound-max", 60));
            return min + EemiHideSeek.this.random.nextInt(max - min + 1);
        }

        private void forcedNoiseSecond() {
            if (EemiHideSeek.this.cfg("forced-sound-min", 45) <= 0) {
                return;
            }
            for (Player h : EemiHideSeek.this.online(this.role(Role.HIDER))) {
                if (!h.getWorld().equals((Object)this.world)) continue;
                Integer left = this.noiseIn.get(h.getUniqueId());
                if (left == null) {
                    this.noiseIn.put(h.getUniqueId(), this.nextNoise());
                    continue;
                }
                if (left - 1 > 0) {
                    this.noiseIn.put(h.getUniqueId(), left - 1);
                    continue;
                }
                this.noiseIn.put(h.getUniqueId(), this.nextNoise());
                Location at = this.hiderSpot(h);
                this.world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 0.8f + EemiHideSeek.this.random.nextFloat() * 0.6f);
                this.world.spawnParticle(Particle.NOTE, at.clone().add(0.0, 0.7, 0.0), 3, 0.3, 0.2, 0.3, 1.0);
                h.sendMessage("§8[§dCache-cache§8] §7§8Tu as fait un petit bruit...");
            }
        }

        // Centre du bloc du cacheur (bloc solide) ou sa position (en mouvement).
        private Location hiderSpot(Player h) {
            Disguise d = this.disguises.get(h.getUniqueId());
            if (d != null && d.solid && d.block != null) {
                return d.block.getLocation().add(0.5, 0.5, 0.5);
            }
            return h.getLocation().add(0.0, 0.5, 0.0);
        }

        private boolean seekerReady(Player s) {
            return this.state == State.SEEKING && this.world != null && this.roles.get(s.getUniqueId()) == Role.SEEKER && s.getWorld().equals((Object)this.world) && !this.respawn.containsKey(s.getUniqueId());
        }

        // Onde de choc (1.3.1, remplace la sonde) : anneau autour du chercheur ; chaque cacheur dans le rayon
        // (meme derriere un mur) perd son bloc solide, est un peu repousse et doit de nouveau rester immobile.
        void shockwave(Player s) {
            if (!this.seekerReady(s)) {
                return;
            }
            long now = System.currentTimeMillis();
            int cdS = Math.max(0, EemiHideSeek.this.cfg("shockwave-cooldown", 15));
            Long last = this.lastShock.get(s.getUniqueId());
            if (last != null && now - last < (long)cdS * 1000L) {
                s.sendActionBar((Component)Component.text((String)("Onde de choc disponible dans " + ((long)cdS * 1000L - (now - last) + 999L) / 1000L + " s"), (TextColor)NamedTextColor.GRAY));
                return;
            }
            this.lastShock.put(s.getUniqueId(), now);
            s.setCooldown(SHOCKWAVE_ITEM, cdS * 20);
            double radius = Math.max(1.0, EemiHideSeek.this.cfgD("shockwave-radius", 5.0));
            Location c = s.getLocation();
            // Anneau qui s'elargit en 5 etapes (2 ticks chacune) + son pour tous les joueurs proches.
            this.world.playSound(c, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.8f);
            this.world.playSound(c, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 1.6f);
            World w = this.world;
            for (int step = 1; step <= 5; ++step) {
                double r = radius * (double)step / 5.0;
                Bukkit.getScheduler().runTaskLater((Plugin)EemiHideSeek.this, () -> {
                    if (this.world != w) {
                        return;
                    }
                    int n = Math.max(16, (int)Math.round(r * 9.0));
                    for (int i = 0; i < n; ++i) {
                        double a = Math.PI * 2.0 * (double)i / (double)n;
                        Location pt = new Location(w, c.getX() + Math.cos(a) * r, c.getY() + 0.3, c.getZ() + Math.sin(a) * r);
                        w.spawnParticle(Particle.CLOUD, pt, 1, 0.0, 0.05, 0.0, 0.0);
                        if (i % 3 == 0) {
                            w.spawnParticle(Particle.ELECTRIC_SPARK, pt.add(0.0, 0.4, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }
                }, (long)((step - 1) * 2));
            }
            int touched = 0;
            for (Player h : EemiHideSeek.this.online(this.role(Role.HIDER))) {
                Disguise d = this.disguises.get(h.getUniqueId());
                if (d == null || !h.getWorld().equals((Object)this.world)) continue;
                Location spot = this.hiderSpot(h);
                if (spot.distanceSquared(c) > radius * radius) continue;
                ++touched;
                // Son bloc tremble (vu par tous) avant d'etre deloge.
                if (d.data != null) {
                    this.world.spawnParticle(Particle.BLOCK, spot, 20, 0.35, 0.35, 0.35, 0.0, (Object)d.data);
                }
                if (d.solid) {
                    this.unsolidify(h, d);
                }
                d.still = 0;
                d.anchor = null;
                d.blocked = false;
                this.push(h, c);
                h.sendActionBar((Component)Component.text((String)"Une onde de choc t'a délogé !", (TextColor)NamedTextColor.RED));
                h.sendMessage("§8[§dCache-cache§8] §7§cUne onde de choc t'a délogé ! §7Reste immobile pour redevenir solide.");
                h.playSound(h.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.2f);
            }
            if (touched == 0) {
                s.sendActionBar((Component)Component.text((String)"Rien...", (TextColor)NamedTextColor.GRAY));
            } else {
                s.sendActionBar((Component)Component.text((String)(touched + (touched > 1 ? " cacheurs" : " cacheur") + " dans l'onde !"), (TextColor)NamedTextColor.GOLD));
                s.playSound(s.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
            }
        }

        // Repousse un cacheur vers l'exterieur de l'onde (0,6 a l'horizontale, 0,25 vers le haut), moins fort
        // s'il n'y a pas de sol de ce cote (pas de chute dans le vide ou hors de la map).
        private void push(Player h, Location from) {
            Location l = h.getLocation();
            Vector dir = new Vector(l.getX() - from.getX(), 0.0, l.getZ() - from.getZ());
            if (dir.lengthSquared() < 1.0E-4) {
                double a = EemiHideSeek.this.random.nextDouble() * Math.PI * 2.0;
                dir = new Vector(Math.cos(a), 0.0, Math.sin(a));
            }
            dir.normalize();
            double horiz = 0.6;
            if (!(this.groundAhead(l, dir, 1.0) && this.groundAhead(l, dir, 1.5) && this.groundAhead(l, dir, 2.0) && this.groundAhead(l, dir, 2.5))) {
                horiz = this.groundAhead(l, dir, 1.0) ? 0.25 : 0.0;
            }
            h.setFallDistance(0.0f);
            h.setVelocity(dir.multiply(horiz).setY(0.25));
        }

        // Sol plein (pas une barriere) a moins de 4 blocs sous le point situe a dist blocs dans cette direction.
        private boolean groundAhead(Location l, Vector dir, double dist) {
            int x = (int)Math.floor(l.getX() + dir.getX() * dist);
            int z = (int)Math.floor(l.getZ() + dir.getZ() * dist);
            int y0 = (int)Math.floor(l.getY());
            // Mur plein au niveau des pieds : pas de passage de ce cote (ce n'est pas du sol).
            if (this.world.getBlockAt(x, y0, z).getType().isSolid()) {
                return false;
            }
            for (int y = y0 - 1; y >= y0 - 4; --y) {
                Block b = this.world.getBlockAt(x, y, z);
                Material m = b.getType();
                if (m == Material.BARRIER) {
                    return false;
                }
                if (m.isSolid()) {
                    return true;
                }
            }
            return false;
        }

        void radar(Player s) {
            if (!this.seekerReady(s)) {
                return;
            }
            long now = System.currentTimeMillis();
            int cdS = Math.max(0, EemiHideSeek.this.cfg("radar-cooldown", 1));
            Long last = this.lastRadar.get(s.getUniqueId());
            if (last != null && now - last < (long)cdS * 1000L) {
                s.sendActionBar((Component)Component.text((String)("Radar disponible dans " + ((long)cdS * 1000L - (now - last) + 999L) / 1000L + " s"), (TextColor)NamedTextColor.GRAY));
                return;
            }
            double best = Double.MAX_VALUE;
            Location me = s.getLocation();
            for (Player h : EemiHideSeek.this.online(this.role(Role.HIDER))) {
                if (!h.getWorld().equals((Object)this.world)) continue;
                best = Math.min(best, this.hiderSpot(h).distance(me));
            }
            this.lastRadar.put(s.getUniqueId(), now);
            s.setCooldown(Material.RECOVERY_COMPASS, cdS * 20);
            if (best == Double.MAX_VALUE) {
                s.sendActionBar((Component)Component.text((String)"Radar : aucun cacheur", (TextColor)NamedTextColor.GRAY));
                return;
            }
            String word;
            NamedTextColor color;
            float pitch;
            if (best < EemiHideSeek.this.cfgD("radar-burning", 10.0)) {
                word = "Brûlant";
                color = NamedTextColor.RED;
                pitch = 2.0f;
            } else if (best < EemiHideSeek.this.cfgD("radar-hot", 20.0)) {
                word = "Chaud";
                color = NamedTextColor.GOLD;
                pitch = 1.5f;
            } else if (best < EemiHideSeek.this.cfgD("radar-warm", 35.0)) {
                word = "Tiède";
                color = NamedTextColor.YELLOW;
                pitch = 1.0f;
            } else {
                word = "Froid";
                color = NamedTextColor.AQUA;
                pitch = 0.6f;
            }
            s.sendActionBar(Component.text((String)"Radar : ", (TextColor)NamedTextColor.GRAY).append(Component.text((String)word, (TextColor)color).decorate(TextDecoration.BOLD)));
            s.playSound(me, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
        }

        private void hit(Player seeker, Player h) {
            Location at;
            Disguise d = this.disguises.get(h.getUniqueId());
            if (d == null) {
                return;
            }
            Location location = at = d.solid && d.block != null ? d.block.getLocation().add(0.5, 0.5, 0.5) : h.getLocation().add(0.0, 0.5, 0.0);
            if (d.solid) {
                this.unsolidify(h, d);
            }
            d.still = 0;
            d.anchor = null;
            this.world.spawnParticle(Particle.BLOCK, at, 25, 0.3, 0.3, 0.3, (Object)d.data);
            this.world.playSound(at, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            double hp = h.getHealth() - EemiHideSeek.this.cfgD("hit-damage", 5.0);
            if (hp <= 0.0) {
                this.found(h, seeker);
                return;
            }
            h.setHealth(hp);
            h.playHurtAnimation(0.0f);
            seeker.sendActionBar(Component.text((String)"Touch\u00e9 ! ", (TextColor)NamedTextColor.GOLD).append((Component)Component.text((String)(String.format("%.1f", hp / 2.0) + " c\u0153urs restants"), (TextColor)NamedTextColor.GRAY)));
        }

        private void found(Player h, Player seeker) {
            this.undisguise(h);
            this.noiseIn.remove(h.getUniqueId());
            this.foundBy.merge(seeker.getUniqueId(), 1, Integer::sum);
            double heal = EemiHideSeek.this.cfgD("found-heal", 2.0) * 2.0;
            if (heal > 0.0 && seeker.isOnline() && !this.respawn.containsKey(seeker.getUniqueId()) && EemiHideSeek.this.mayTouch(seeker)) {
                double hp = Math.min(this.heartsHp(seeker), seeker.getHealth() + heal);
                if (hp > seeker.getHealth()) {
                    seeker.setHealth(hp);
                    seeker.sendActionBar((Component)Component.text((String)("Trouvé ! +" + EemiHideSeek.this.fmt(heal / 2.0) + " cœurs (" + this.heartsText(hp) + ")"), (TextColor)NamedTextColor.GREEN));
                }
            }
            EemiHideSeek.this.eemi("reward " + seeker.getName() + " hideseek kill");
            this.world.strikeLightningEffect(h.getLocation());
            this.broadcastGame("\u00a7c" + h.getName() + " \u00a77a \u00e9t\u00e9 trouv\u00e9 par \u00a7e" + seeker.getName() + "\u00a77 !");
            if ("spectator".equalsIgnoreCase(EemiHideSeek.this.getConfig().getString("found-becomes", "seeker"))) {
                this.roles.put(h.getUniqueId(), Role.SPECTATOR);
                if (EemiHideSeek.this.mayTouch(h)) {
                    h.getInventory().clear();
                    h.setGameMode(GameMode.SPECTATOR);
                }
                h.showTitle(Title.title((Component)Component.text((String)"Trouv\u00e9 !", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD), (Component)Component.text((String)"Tu regardes la fin de la partie", (TextColor)NamedTextColor.GRAY)));
            } else {
                this.roles.put(h.getUniqueId(), Role.SEEKER);
                EemiHideSeek.this.heal(h);
                EemiHideSeek.this.equipSeeker(h);
                this.setSeekerHearts(h);
                h.showTitle(Title.title((Component)Component.text((String)"Trouv\u00e9 !", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD), (Component)Component.text((String)"Tu deviens chercheur !", (TextColor)NamedTextColor.YELLOW)));
                h.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7eTu deviens chercheur : \u00a7ftrouve les autres cacheurs !");
            }
            this.refreshVisibility();
            EemiHideSeek.this.reportStatus();
            this.checkEnd();
        }

        List<Player> inRoom() {
            ArrayList<Player> l = new ArrayList<Player>();
            if (this.world == null) {
                return l;
            }
            for (Player p : EemiHideSeek.this.online(this.players)) {
                if (!p.getWorld().equals((Object)this.world)) continue;
                l.add(p);
            }
            return l;
        }

        Set<UUID> role(Role r) {
            LinkedHashSet<UUID> s = new LinkedHashSet<UUID>();
            for (Map.Entry<UUID, Role> e : this.roles.entrySet()) {
                if (e.getValue() != r) continue;
                s.add(e.getKey());
            }
            return s;
        }

        void startGame() {
            List<Player> list = this.inRoom();
            if (list.size() < 2) {
                this.state = State.WAITING;
                return;
            }
            Collections.shuffle(list, EemiHideSeek.this.random);
            int seekers = Math.max(1, list.size() / Math.max(1, EemiHideSeek.this.cfg("players-per-seeker", 5)));
            seekers = Math.min(seekers, list.size() - 1);
            this.roles.clear();
            this.releaseAllCells();
            this.disguises.clear();
            this.foundBy.clear();
            this.taunts.clear();
            this.lastSwing.clear();
            this.respawn.clear();
            this.noiseIn.clear();
            this.lastShock.clear();
            this.lastRadar.clear();
            this.lastHiderHit.clear();
            EemiHideSeek.this.removeDisplays(this.world);
            this.world.setTime(6000L);
            this.total = list.size();
            for (int i = 0; i < list.size(); ++i) {
                Player p = list.get(i);
                EemiHideSeek.this.resetPlayer(p, GameMode.ADVENTURE);
                p.setAllowFlight(true);
                if (i < seekers) {
                    this.roles.put(p.getUniqueId(), Role.SEEKER);
                    p.teleport(this.point("seeker-room"));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, -1, 0, false, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, -1, 6, false, false, false));
                    p.showTitle(Title.title((Component)Component.text((String)"CHERCHEUR", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD), (Component)Component.text((String)("Attends " + EemiHideSeek.this.cfg("hide-time", 30) + " s, puis cherche !"), (TextColor)NamedTextColor.GRAY)));
                    p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cTu es chercheur : \u00a77attends " + EemiHideSeek.this.cfg("hide-time", 30) + " s, puis trouve les cacheurs.");
                    p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu as \u00a7c" + this.heartsText(this.heartsHp(p)) + " c\u0153urs\u00a77 : taper un vrai bloc du d\u00e9cor \u00e0 l'\u00e9p\u00e9e en co\u00fbte " + EemiHideSeek.this.fmt(EemiHideSeek.this.cfgD("wrong-hit-cost", 1.0)) + ", trouver un cacheur en rend " + EemiHideSeek.this.fmt(EemiHideSeek.this.cfgD("found-heal", 2.0)) + ". \u00c0 0, retour apr\u00e8s " + EemiHideSeek.this.cfg("seeker-respawn", 15) + " s.");
                    p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Seule l'\u00a7c\u00e9p\u00e9e \u00a77touche les cacheurs. \u00a73Onde de choc \u00a77(d\u00e9loge les cacheurs autour de toi, gratuite) et \u00a76radar \u00a77(Br\u00fblant/Chaud/Ti\u00e8de/Froid) pour chercher sans taper.");
                    continue;
                }
                this.roles.put(p.getUniqueId(), Role.HIDER);
                p.teleport(this.point("hider-spawn"));
                this.disguise(p, EemiHideSeek.this.randomBlock(this));
                EemiHideSeek.this.giveHiderItems(p);
                p.showTitle(Title.title((Component)Component.text((String)"CACHEUR", (TextColor)NamedTextColor.GREEN).decorate(TextDecoration.BOLD), (Component)Component.text((String)"Choisis ton bloc et cache-toi !", (TextColor)NamedTextColor.GRAY)));
                Bukkit.getScheduler().runTaskLater((Plugin)EemiHideSeek.this, () -> {
                    if (p.isOnline() && this.roles.get(p.getUniqueId()) == Role.HIDER) {
                        EemiHideSeek.this.openMenu(this, p);
                    }
                }, 10L);
            }
            this.hidersAtStart = list.size() - seekers;
            this.state = State.HIDING;
            this.refreshVisibility();
            this.timer = EemiHideSeek.this.cfg("hide-time", 30);
            EemiHideSeek.this.reportStatus();
            this.broadcastGame("\u00a7eMap : \u00a7f" + EemiHideSeek.this.mapName(this.mapId) + "\u00a7e.");
            this.broadcastGame("\u00a7eLa partie commence ! \u00a77" + seekers + " chercheur(s), " + (list.size() - seekers) + " cacheur(s). Les chercheurs sont l\u00e2ches dans \u00a7f" + this.timer + " s\u00a77.");
            this.broadcastGame("\u00a77Cacheurs : \u00a7fclic droit avec l'am\u00e9thyste \u00a77sur un bloc pour le copier, dans le vide pour le menu. B\u00e2ton de blaze pour tourner le bloc. Immobile = bloc solide.");
            this.broadcastGame("\u00a77Attention cacheurs : vous faites un petit bruit toutes les " + EemiHideSeek.this.cfg("forced-sound-min", 45) + " \u00e0 " + Math.max(EemiHideSeek.this.cfg("forced-sound-min", 45), EemiHideSeek.this.cfg("forced-sound-max", 60)) + " s, et les chercheurs ont une onde de choc qui vous d\u00e9loge et un radar.");
        }

        private void release() {
            this.state = State.SEEKING;
            this.timer = EemiHideSeek.this.cfg("game-time", 420);
            for (Player s : EemiHideSeek.this.online(this.role(Role.SEEKER))) {
                s.removePotionEffect(PotionEffectType.BLINDNESS);
                s.removePotionEffect(PotionEffectType.SLOWNESS);
                s.teleport(this.point("seeker-spawn"));
                EemiHideSeek.this.equipSeeker(s);
                this.setSeekerHearts(s);
                s.showTitle(Title.title((Component)Component.text((String)"CHERCHEZ !", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD), (Component)Component.text((String)("Mauvais bloc = -1 cœur, onde et radar gratuits"), (TextColor)NamedTextColor.GRAY)));
                s.playSound(s.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);
            }
            this.broadcastGame("\u00a7cLes chercheurs sont l\u00e2ches ! \u00a77Temps : \u00a7f" + EemiHideSeek.clock(this.timer) + "\u00a77.");
        }

        void refreshVisibility() {
            if (this.world == null) {
                return;
            }
            Set<UUID> hiders = this.role(Role.HIDER);
            for (Player v : this.world.getPlayers()) {
                for (Player q : this.world.getPlayers()) {
                    if (v == q) continue;
                    if (hiders.contains(q.getUniqueId())) {
                        v.hidePlayer((Plugin)EemiHideSeek.this, q);
                        continue;
                    }
                    v.showPlayer((Plugin)EemiHideSeek.this, q);
                }
            }
            this.syncDisplays();
            this.resendOwnAir();
        }

        void checkEnd() {
            if (this.state != State.HIDING && this.state != State.SEEKING) {
                return;
            }
            int hiders = EemiHideSeek.this.online(this.role(Role.HIDER)).size();
            int seekers = EemiHideSeek.this.online(this.role(Role.SEEKER)).size();
            if (hiders == 0) {
                this.endGame(false);
            } else if (seekers == 0) {
                this.endGame(true);
            }
        }

        private void endGame(boolean hidersWin) {
            this.endGame(hidersWin, true);
        }

        void endGame(boolean hidersWin, boolean rewards) {
            Component component;
            if (this.state != State.HIDING && this.state != State.SEEKING) {
                return;
            }
            List<Player> survivors = EemiHideSeek.this.online(this.role(Role.HIDER));
            if (rewards) {
                Role winners = hidersWin ? Role.HIDER : Role.SEEKER;
                for (Map.Entry<UUID, Role> en : this.roles.entrySet()) {
                    Player p = Bukkit.getPlayer((UUID)en.getKey());
                    if (p == null) continue;
                    EemiHideSeek.this.eemi("reward " + p.getName() + " hideseek play");
                    if (en.getValue() != winners) continue;
                    EemiHideSeek.this.eemi("reward " + p.getName() + " hideseek win");
                }
            }
            this.state = State.ENDING;
            for (Player s : EemiHideSeek.this.online(this.respawn.keySet())) {
                if (this.world == null || !s.getWorld().equals((Object)this.world) || !EemiHideSeek.this.mayTouch(s)) continue;
                s.teleport(this.point("seeker-spawn"));
            }
            this.respawn.clear();
            this.noiseIn.clear();
            EemiHideSeek.this.reportStatus();
            this.timer = EemiHideSeek.this.cfg("end-time", 8);
            for (Player player : EemiHideSeek.this.online(new LinkedHashSet<UUID>(this.disguises.keySet()))) {
                this.undisguise(player);
            }
            this.releaseAllCells();
            this.disguises.clear();
            EemiHideSeek.this.removeDisplays(this.world);
            for (Player player : EemiHideSeek.this.online(this.roles.keySet())) {
                EemiHideSeek.this.showAll(player);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.removePotionEffect(PotionEffectType.SPEED);
            }
            StringBuilder names = new StringBuilder();
            for (Player h : survivors) {
                names.append(names.length() == 0 ? "" : ", ").append(h.getName());
            }
            Component component2 = component = hidersWin ? Component.text((String)"Les cacheurs", (TextColor)NamedTextColor.GREEN).decorate(TextDecoration.BOLD) : Component.text((String)"Les chercheurs", (TextColor)NamedTextColor.RED).decorate(TextDecoration.BOLD);
            TextComponent sub = Component.text((String)("gagnent ! " + (String)(hidersWin ? (survivors.isEmpty() ? "Plus aucun chercheur" : survivors.size() + " cacheur(s) en vie") : "Tous trouv\u00e9s")), (TextColor)NamedTextColor.GRAY);
            for (Player p : EemiHideSeek.this.online(this.roles.keySet())) {
                p.showTitle(Title.title((Component)component, (Component)sub));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            this.broadcastGame((String)(hidersWin ? "\u00a7aLes cacheurs gagnent !" + (String)(survivors.isEmpty() ? "" : " \u00a77Survivants : \u00a7f" + String.valueOf(names)) : "\u00a7cLes chercheurs gagnent ! \u00a77Tous les cacheurs ont \u00e9t\u00e9 trouv\u00e9s."));
            UUID best = null;
            int bestFound = 0;
            for (Map.Entry<UUID, Integer> e : this.foundBy.entrySet()) {
                if (e.getValue() <= bestFound) continue;
                bestFound = e.getValue();
                best = e.getKey();
            }
            if (best != null && Bukkit.getPlayer(best) != null) {
                this.broadcastGame("\u00a77Meilleur chercheur : \u00a7e" + Bukkit.getPlayer(best).getName() + " \u00a77(" + bestFound + "trouv\u00e9(s))");
            }
            UUID taunter = null;
            int bestTaunts = 0;
            for (Map.Entry<UUID, Integer> e : this.taunts.entrySet()) {
                if (e.getValue() <= bestTaunts) continue;
                bestTaunts = e.getValue();
                taunter = e.getKey();
            }
            if (taunter != null && Bukkit.getPlayer(taunter) != null) {
                this.broadcastGame("\u00a77Roi de la provocation : \u00a7d" + Bukkit.getPlayer(taunter).getName() + " \u00a77(" + bestTaunts + " point(s))");
            }
        }

        void finishGame() {
            this.roles.clear();
            this.releaseAllCells();
            this.disguises.clear();
            // Partie 1 (monde sauvegarde) : sauvegarde sans aucun bloc de cacheur, puis journal vide pour ce monde.
            // Les copies ne sont pas sauvegardees (et sont supprimees au demarrage) : journal vide aussi.
            if (this.world != null) {
                boolean saved = true;
                if (this.index == 1 && EemiHideSeek.this.modelBusy) {
                    saved = false;      // copie du modele en cours : pas d'ecriture des regions maintenant
                } else if (this.index == 1) {
                    try {
                        this.world.save();
                    }
                    catch (Exception ex) {
                        saved = false;
                        EemiHideSeek.this.getLogger().warning("Sauvegarde de " + this.world.getName() + " : " + ex.getMessage());
                    }
                }
                if (saved) {
                    EemiHideSeek.this.clearJournal(this.world);
                }
            }
            EemiHideSeek.this.removeDisplays(this.world);
            this.state = State.WAITING;
            // 1.4.0 : nouvelle map tiree pour la partie suivante (jamais deux fois de suite)
            this.mapId = EemiHideSeek.this.pickMap(this.mapId, this.available);
            for (Player p : EemiHideSeek.this.online(this.spectators)) {
                EemiHideSeek.this.showAll(p);
                EemiHideSeek.this.sendToHub(p);
                p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Partie termin\u00e9e, retour au hub.");
            }
            this.spectators.clear();
            if (this.index >= 2) {
                Game other;
                Game game = other = this.players.isEmpty() ? null : EemiHideSeek.this.joinable(this.players.size(), this, false);
                if (other != null || this.players.isEmpty()) {
                    ArrayList<UUID> moved = new ArrayList<UUID>(this.players);
                    this.players.clear();
                    for (UUID id : moved) {
                        this.boards.remove(id);
                    }
                    EemiHideSeek.this.destroyGame(this);
                    if (other != null) {
                        for (UUID id : moved) {
                            Player p = Bukkit.getPlayer((UUID)id);
                            if (p == null) continue;
                            other.players.add(id);
                            other.toWaiting(p);
                            p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu rejoins la salle d'attente de la partie " + other.index + ".");
                        }
                    }
                    EemiHideSeek.this.reportStatus();
                    return;
                }
            }
            for (Player p : EemiHideSeek.this.online(this.players)) {
                this.toWaiting(p);
            }
            EemiHideSeek.this.reportStatus();
        }

        void forceStop() {
            if (this.state == State.BUILDING) {
                // les tranches de pose s'arretent d'elles-memes (etat different de BUILDING) ; la map n'est pas notee construite
                if (this.world != null) {
                    this.world.removePluginChunkTickets((Plugin)EemiHideSeek.this);
                }
                this.state = State.WAITING;
                EemiHideSeek.this.getLogger().warning("Construction des maps arretee par un admin : relancer /hideseek build.");
                return;
            }
            if (this.state == State.HIDING || this.state == State.SEEKING) {
                this.endGame(false, false);
            }
            if (this.state == State.ENDING) {
                this.finishGame();
            }
            if (this.state == State.STARTING) {
                this.state = State.WAITING;
            }
        }

        void toWaiting(Player p) {
            Location l = this.point("waiting-spawn");
            EemiHideSeek.this.showAll(p);
            p.teleport(l);
            EemiHideSeek.this.later(() -> {
                if (!(p.isOnline() && this.players.contains(p.getUniqueId()) && this.world != null && EemiHideSeek.this.mayTouch(p))) {
                    return;
                }
                if (!p.getWorld().equals((Object)this.world)) {
                    p.teleport(l);
                }
                if (!p.getWorld().equals((Object)this.world)) {
                    return;
                }
                EemiHideSeek.this.resetPlayer(p, GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.getInventory().setItem(8, EemiHideSeek.this.named(EemiHideSeek.this.kit(new ItemStack(Material.RED_BED), "quit"), "Quitter le cache-cache", NamedTextColor.RED, new String[0]));
                this.updateSidebar(p);
            });
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!this.players.remove(id)) {
                return;
            }
            if (this.state == State.PREPARING) {
                if (!this.copyStarted && this.players.isEmpty()) {
                    EemiHideSeek.this.games.remove(this.index);
                }
                EemiHideSeek.this.reportStatus();
                return;
            }
            if (this.disguises.containsKey(id)) {
                this.undisguise(p);
            }
            Role r = this.roles.remove(id);
            this.respawn.remove(id);
            this.noiseIn.remove(id);
            this.lastShock.remove(id);
            this.lastRadar.remove(id);
            this.lastHiderHit.remove(id);
            this.boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            EemiHideSeek.this.showAll(p);
            if (!teleport && r != null && p.isOnline()) {
                // Parti par /lobby ou changement de monde : retirer ce que le jeu a pose (aveuglement, lenteur,
                // vitesse, invisibilite, recharges) et rendre la vie, sinon il garde tout au hub.
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.SPEED);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                p.setCooldown(SHOCKWAVE_ITEM, 0);
                p.setCooldown(Material.RECOVERY_COMPASS, 0);
                AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
                if (!p.isDead()) {
                    p.setHealth(hp == null ? 20.0 : hp.getValue());
                }
            }
            if (teleport) {
                EemiHideSeek.this.sendToHub(p);
            }
            if (r != null && r != Role.SPECTATOR) {
                this.broadcastGame("\u00a7c" + p.getName() + " \u00a77a quitt\u00e9 la partie.");
                this.checkEnd();
            }
            if (this.index >= 2 && this.players.isEmpty() && (this.state == State.WAITING || this.state == State.STARTING) && EemiHideSeek.this.games.get(this.index) == this) {
                EemiHideSeek.this.destroyGame(this);
            }
            EemiHideSeek.this.reportStatus();
        }

        void spectate(Player p) {
            Location view = this.point("spectate");
            this.spectators.add(p.getUniqueId());
            p.teleport(view);
            if (EemiHideSeek.this.mayTouch(p)) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
            }
            EemiHideSeek.this.later(this::refreshVisibility);
            p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a77Tu regardes la partie " + this.index + ", \u00a7f/lobby \u00a77pour revenir.");
            p.sendMessage("\u00a78[\u00a7dCache-cache\u00a78] \u00a77\u00a7cNe r\u00e9v\u00e8le pas les cachettes dans le chat !");
        }

        private Scoreboard board(Player p) {
            Scoreboard b = this.boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("hns", Criteria.DUMMY, ((TextComponent)Component.text((String)"CACHE", (TextColor)NamedTextColor.GREEN).decorate(TextDecoration.BOLD)).append(Component.text((String)"-CACHE", (TextColor)NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                Team t = b.registerNewTeam(EemiHideSeek.NAMES_TEAM);
                t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                this.boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) {
                p.setScoreboard(b);
            }
            return b;
        }

        private void hideNames(Player viewer) {
            Team t = this.board(viewer).getTeam(EemiHideSeek.NAMES_TEAM);
            if (t == null) {
                return;
            }
            for (Player q : EemiHideSeek.this.online(this.players)) {
                if (t.hasEntry(q.getName())) continue;
                t.addEntry(q.getName());
            }
            for (String entry : new ArrayList<String>(t.getEntries())) {
                Player q = Bukkit.getPlayerExact((String)entry);
                if (q != null && this.players.contains(q.getUniqueId())) continue;
                t.removeEntry(entry);
            }
        }

        private void setLines(Player p, List<Component> lines) {
            Scoreboard b = this.board(p);
            Objective o = b.getObjective("hns");
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
            boolean playing;
            ArrayList<Component> l = new ArrayList<Component>();
            l.add((Component)Component.text((String)" "));
            Role r = this.roles.get(p.getUniqueId());
            boolean bl = playing = r != null && (this.state == State.HIDING || this.state == State.SEEKING || this.state == State.ENDING);
            if (!playing) {
                l.add(EemiHideSeek.kv("Joueurs : ", this.inRoom().size() + "/" + EemiHideSeek.this.cfg("max-players", 20), NamedTextColor.WHITE));
                l.add(EemiHideSeek.kv("Minimum : ", String.valueOf(EemiHideSeek.this.cfg("min-players", 3)), NamedTextColor.WHITE));
                l.add(EemiHideSeek.kv("Map : ", EemiHideSeek.this.mapName(this.mapId), NamedTextColor.GREEN));
                if (EemiHideSeek.this.maxInstances() > 1) {
                    l.add(EemiHideSeek.kv("Partie : ", "#" + this.index, NamedTextColor.WHITE));
                }
                l.add((Component)Component.text((String)"  "));
                if (this.state == State.STARTING) {
                    l.add(EemiHideSeek.kv("D\u00e9but dans ", this.timer + " s", NamedTextColor.GREEN));
                } else if (this.state == State.WAITING) {
                    l.add((Component)Component.text((String)"En attente...", (TextColor)NamedTextColor.YELLOW));
                } else if (this.state == State.BUILDING) {
                    l.add((Component)Component.text((String)"Construction de la map...", (TextColor)NamedTextColor.GOLD));
                } else {
                    l.add((Component)Component.text((String)"Partie en cours...", (TextColor)NamedTextColor.YELLOW));
                }
            } else {
                l.add(switch (r.ordinal()) {
                    default -> throw new MatchException(null, null);
                    case 0 -> EemiHideSeek.kv("R\u00f4le : ", "Cacheur", NamedTextColor.GREEN);
                    case 1 -> EemiHideSeek.kv("R\u00f4le : ", "Chercheur", NamedTextColor.RED);
                    case 2 -> EemiHideSeek.kv("R\u00f4le : ", "Spectateur", NamedTextColor.DARK_GRAY);
                });
                l.add((Component)Component.text((String)"  "));
                l.add(EemiHideSeek.kv("Cacheurs restants : ", EemiHideSeek.this.online(this.role(Role.HIDER)).size() + "/" + this.hidersAtStart, NamedTextColor.GREEN));
                l.add(EemiHideSeek.kv("Chercheurs : ", String.valueOf(EemiHideSeek.this.online(this.role(Role.SEEKER)).size()), NamedTextColor.RED));
                if (this.state == State.HIDING) {
                    l.add(EemiHideSeek.kv("Cachette : ", this.timer + " s", NamedTextColor.YELLOW));
                } else if (this.state == State.SEEKING) {
                    l.add(EemiHideSeek.kv("Temps : ", EemiHideSeek.clock(this.timer), this.timer <= EemiHideSeek.this.cfg("final-reveal-time", 60) ? NamedTextColor.GOLD : NamedTextColor.WHITE));
                } else {
                    l.add((Component)Component.text((String)"Fin de la partie", (TextColor)NamedTextColor.GOLD));
                }
                Disguise d = this.disguises.get(p.getUniqueId());
                if (d != null) {
                    l.add(EemiHideSeek.kv("Bloc : ", EemiHideSeek.this.blockName(this, d.data), NamedTextColor.AQUA));
                    if (d.solid) {
                        l.add((Component)Component.text((String)"Solide", (TextColor)NamedTextColor.GREEN));
                    }
                    l.add(EemiHideSeek.kv("Provocations : ", String.valueOf(this.taunts.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.LIGHT_PURPLE));
                } else if (r == Role.SEEKER) {
                    l.add(EemiHideSeek.kv("Trouv\u00e9s : ", String.valueOf(this.foundBy.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.YELLOW));
                    if (this.state == State.SEEKING) {
                        Integer back = this.respawn.get(p.getUniqueId());
                        if (back != null) {
                            l.add(EemiHideSeek.kv("Retour dans ", back + " s", NamedTextColor.RED));
                        } else {
                            l.add(EemiHideSeek.kv("C\u0153urs : ", this.heartsText(p.getHealth()) + "/" + this.heartsText(this.heartsHp(p)), NamedTextColor.RED));
                        }
                    }
                }
            }
            l.add((Component)Component.text((String)"   "));
            l.add((Component)Component.text((String)"eemicraft", (TextColor)NamedTextColor.DARK_GRAY));
            this.setLines(p, l);
        }

        void broadcast(String msg) {
            for (Player p : EemiHideSeek.this.online(this.players)) {
                p.sendMessage(EemiHideSeek.PREFIX + msg);
            }
        }

        void broadcastGame(String msg) {
            for (Player p : EemiHideSeek.this.online(this.roles.keySet())) {
                p.sendMessage(EemiHideSeek.PREFIX + msg);
            }
        }

        String stateName() {
            return switch (this.state.ordinal()) {
                default -> throw new MatchException(null, null);
                case 0 -> "preparation";
                case 1 -> "attente";
                case 2 -> "d\u00e9compte " + this.timer + " s";
                case 3 -> "cachette " + this.timer + " s";
                case 4 -> "recherche " + EemiHideSeek.clock(this.timer);
                case 5 -> "fin";
                case 6 -> "construction";
            };
        }
    }

    private static final class VoidGenerator
    extends ChunkGenerator {
        private VoidGenerator() {
        }

        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 101.0, 75.5);
        }
    }

    static enum State {
        PREPARING,
        WAITING,
        STARTING,
        HIDING,
        SEEKING,
        ENDING,
        BUILDING;

    }

    private static final class Menu
    implements InventoryHolder {
        Inventory inv;
        final List<Material> items = new ArrayList<Material>();

        private Menu() {
        }

        public Inventory getInventory() {
            return this.inv;
        }
    }

    static enum Role {
        HIDER,
        SEEKER,
        SPECTATOR;

    }

    private static final class Disguise {
        BlockData data;
        BlockDisplay display;
        Location anchor;
        int still;
        boolean solid;
        boolean blocked;
        // 1.6.0 (#135) : sur un toit / perchoir interdit, ticks passes dessus, dernier point valide au sol.
        boolean perched;
        int perchTicks;
        Location safe;
        Block block;
        long lastChange;
        long lastTaunt;

        private Disguise() {
        }
    }
}

