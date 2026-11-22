package fr.eemicraft.refuge.expe;

import fr.eemicraft.refuge.EemiRefuge;
import fr.eemicraft.refuge.Txt;
import fr.eemicraft.refuge.api.Bilan;
import fr.eemicraft.refuge.api.ExpeditionApi;
import fr.eemicraft.refuge.api.Profile;
import fr.eemicraft.refuge.api.RefugeApi;
import fr.eemicraft.refuge.api.ZoneInfo;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Module Expéditions (étape 1) : zones et leurs instances (partie 1 = monde d'origine, parties 2..n = copies du
 * modèle « <monde>_modele » faites par WorldClone), butin, zombies en zone, Marée, extraction, mise à terre et
 * relève, sac à la mort, assurance, bilan. Voir CONCEPTION.md (section 0 et 5) et ETAPE1_CONTRAT.md.
 */
public class Expeditions implements ExpeditionApi, Listener {

    static final String TAG = "eemirefuge_expe";

    /** Une zone chargée et ses instances. */
    static final class ZoneRt {
        final Zone zone;
        World origin, model;
        boolean modelBusy;
        int modelGen;
        final Map<Integer, Expedition> running = new HashMap<>();

        ZoneRt(Zone zone) {
            this.zone = zone;
        }
    }

    final EemiRefuge plugin;
    final Loot loot;
    final Sacs sacs;
    private final Map<String, ZoneRt> zones = new LinkedHashMap<>();
    /** Joueur → expédition (préparation comprise). */
    private final Map<UUID, Expedition> byPlayer = new HashMap<>();
    /** Bilans à montrer au retour au Refuge (mort par déconnexion, mort vanilla). */
    private final Map<UUID, Bilan> pendingBilan = new HashMap<>();
    /** Morts vanilla (filet de sécurité) : réapparition au Refuge. */
    private final Set<UUID> respawnToRefuge = new HashSet<>();
    private boolean warnedNoRefuge;
    private String lastStatus = "";
    private long lastStatusAt;

    public Expeditions(EemiRefuge plugin) {
        this.plugin = plugin;
        this.loot = new Loot(plugin.getLogger());
        this.sacs = new Sacs(plugin.getDataFolder(), plugin.getLogger());
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void enable() {
        plugin.getDataFolder().mkdirs();
        if (!new File(plugin.getDataFolder(), "loot.yml").exists()) plugin.saveResource("loot.yml", false);
        copyZonesFromJar();
        loot.load(new File(plugin.getDataFolder(), "loot.yml"));
        sacs.load();
        if (fr.eemicraft.refuge.Catalogue.def("seconde_chance") == null)
            plugin.getLogger().info("Pas d'objet « seconde_chance » au Catalogue : en solo, un coup fatal tue directement.");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Les mondes importés (Multiverse) sont chargés après les plugins : on attend un tick.
        Bukkit.getScheduler().runTask(plugin, this::loadZones);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> reportStatus(), 100L, 600L);
    }

    @Override
    public void disable() {
        for (ZoneRt zr : zones.values())
            for (Expedition ex : new ArrayList<>(zr.running.values())) {
                ex.adminStop = true;
                ex.end("arrêt du serveur", false);
            }
    }

    /** Copie les fichiers zones/* du jar dans plugins/EemiRefuge/zones s'ils n'y sont pas. */
    private void copyZonesFromJar() {
        File dir = new File(plugin.getDataFolder(), "zones");
        dir.mkdirs();
        File jar = plugin.jarFile();
        if (jar == null || !jar.isFile()) return;
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.startsWith("zones/") || n.substring(6).contains("/")) continue;
                File out = new File(dir, n.substring(6));
                if (out.exists()) continue;
                try (InputStream in = jf.getInputStream(e)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Copie des zones du jar impossible : " + ex.getMessage());
        }
    }

    private int maxInstances() {
        return Math.max(1, plugin.getConfig().getInt("expeditions.max-instances", 3));
    }

    private void loadZones() {
        File dir = new File(plugin.getDataFolder(), "zones");
        File[] files = dir.listFiles((d, n) -> (n.endsWith(".yml") || n.endsWith(".yaml")) && !n.contains("_containers"));
        if (files == null) return;
        for (File f : files) {
            org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            if (!y.getBoolean("actif", y.getBoolean("enabled", true))) continue;
            Zone z = Zone.load(f, plugin.getLogger());
            if (zones.containsKey(z.id)) continue;
            ZoneRt zr = new ZoneRt(z);
            zones.put(z.id, zr);
            zr.origin = loadOrigin(z.world);
            if (zr.origin == null) {
                plugin.getLogger().warning("Zone " + z.id + " : monde " + z.world + " absent, zone non jouable.");
                continue;
            }
            rules(zr.origin);
            restoreOrigin(z, zr.origin);
            WorldClone.cleanup(plugin, zr.origin, z.world, maxInstances());
            makeModel(zr);
            plugin.getLogger().info("Zone " + z.id + " (" + z.name + ") : monde " + z.world + ", " + z.arrivals.size() + " arrivee(s), "
                    + z.extractions.size() + " extraction(s), " + z.hordes.size() + " point(s) de horde, " + z.containers.size() + " conteneur(s).");
        }
    }

    /** Monde d'origine d'une zone : déjà chargé (Multiverse), sinon chargé s'il existe sur le disque. Jamais créé vide. */
    private World loadOrigin(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        World main = Bukkit.getWorlds().get(0);
        Path sib = main.getWorldFolder().toPath().resolveSibling(name);
        Path root = Bukkit.getWorldContainer().toPath().resolve(name);
        if (!Files.isDirectory(sib) && !Files.isDirectory(root)) return null;
        return new WorldCreator(name).createWorld();
    }

    /** (Re)fait le modèle depuis le monde d'origine (qui doit être propre). */
    void makeModel(ZoneRt zr) {
        if (zr.origin == null) return;
        String name = zr.zone.world + "_modele";
        int gen = ++zr.modelGen;
        zr.modelBusy = true;
        zr.model = null;
        World old = Bukkit.getWorld(name);
        if (old != null) {
            for (Player p : old.getPlayers()) toRefuge(p);
            if (!Bukkit.unloadWorld(old, false)) plugin.getLogger().warning("Impossible de decharger l'ancien modele " + name + ".");
        }
        WorldClone.create(plugin, zr.origin, name, w -> {
            if (gen != zr.modelGen) return;
            zr.modelBusy = false;
            if (w == null) {
                plugin.getLogger().warning("Modele " + name + " impossible a creer : une seule expedition a la fois dans " + zr.zone.id + ".");
                return;
            }
            rules(w);
            zr.model = w;
            plugin.getLogger().info("Modele " + name + " pret.");
        });
    }

    /** Règles des mondes de zone : pas de cycle, pas d'apparition naturelle, pas de griefing. */
    static void rules(World w) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.SPAWN_WARDENS, false);
        w.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.TNT_EXPLOSION_DROP_DECAY, false);
        w.setDifficulty(org.bukkit.Difficulty.NORMAL);
        w.setStorm(false);
        w.setThundering(false);
    }

    /** Remet les blocs du monde d'origine modifiés pendant une expédition (sacs, portes). */
    void restoreOrigin(Zone z, World w) {
        List<String> lines = sacs.takeRestore(z.id);
        int n = 0;
        for (String line : lines) {
            String[] p = line.split("\\|", 2);
            String[] c = p[0].split(",");
            try {
                BlockData data = Bukkit.createBlockData(p[1]);
                w.getBlockAt(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])).setBlockData(data, false);
                n++;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Restauration impossible : " + line + " (" + e.getMessage() + ")");
            }
        }
        if (n > 0) plugin.getLogger().info("Zone " + z.id + " : " + n + " bloc(s) du monde d'origine remis.");
    }

    // ------------------------------------------------------------------ accès

    /** Monde d'une expédition en cours (combat, bruit, zombies actifs). */
    public boolean isZoneWorld(World w) {
        if (w == null) return false;
        Expedition ex = byWorld(w);
        return ex != null && ex.state == Expedition.State.RUNNING;
    }

    /** Monde d'origine, modèle ou copie d'une zone (en expédition ou non). */
    public boolean isAnyZoneWorld(World w) {
        if (w == null) return false;
        for (ZoneRt zr : zones.values()) {
            String base = zr.zone.world;
            if (w.getName().equals(base + "_modele") || WorldClone.index(base, w.getName()) > 0) return true;
        }
        return false;
    }

    /** Joueurs présents dans les mondes d'expéditions en cours. */
    public List<Player> zonePlayers() {
        List<Player> out = new ArrayList<>();
        for (ZoneRt zr : zones.values())
            for (Expedition ex : zr.running.values())
                if (ex.state == Expedition.State.RUNNING && ex.world != null) out.addAll(ex.world.getPlayers());
        return out;
    }

    Expedition byWorld(World w) {
        for (ZoneRt zr : zones.values())
            for (Expedition ex : zr.running.values()) if (ex.world != null && ex.world.equals(w)) return ex;
        return null;
    }

    private Expedition.Member member(Player p) {
        Expedition ex = byPlayer.get(p.getUniqueId());
        if (ex == null || ex.state != Expedition.State.RUNNING) return null;
        Expedition.Member m = ex.members.get(p.getUniqueId());
        return m != null && m.active() ? m : null;
    }

    void unregister(UUID id) {
        byPlayer.remove(id);
    }

    Profile profile(UUID id) {
        RefugeApi r = plugin.refuge();
        if (r == null) return null;
        try {
            return r.profile(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ contrat ExpeditionApi

    @Override
    public List<ZoneInfo> zones() {
        List<ZoneInfo> out = new ArrayList<>();
        for (ZoneRt zr : zones.values()) {
            if (zr.origin == null) continue;
            int free = zr.running.containsKey(1) ? 0 : 1;
            if (zr.model != null && !zr.modelBusy)
                for (int n = 2; n <= maxInstances(); n++) if (!zr.running.containsKey(n)) free++;
            Zone z = zr.zone;
            out.add(new ZoneInfo(z.id, z.name, z.act, z.minPlayers, z.maxPlayers, free, z.icon));
        }
        return out;
    }

    @Override
    public String start(Player leader, List<Player> squad, String zoneId, boolean night) {
        ZoneRt zr = zoneId == null ? null : zones.get(zoneId.toLowerCase(Locale.ROOT));
        if (zr == null) return "Zone inconnue : " + zoneId + ".";
        if (zr.origin == null) return "La zone " + zr.zone.name + " n'est pas prête (monde absent).";
        if (zr.zone.arrivals.isEmpty() || zr.zone.extractions.isEmpty())
            return "La zone " + zr.zone.name + " n'a pas encore d'arrivée ou d'extraction.";
        List<Player> list = new ArrayList<>();
        if (leader != null) list.add(leader);
        for (Player p : squad) if (p != null && !list.contains(p)) list.add(p);
        if (list.size() < zr.zone.minPlayers) return "Il faut au moins " + zr.zone.minPlayers + " joueur(s) pour cette zone.";
        if (list.size() > zr.zone.maxPlayers) return "Pas plus de " + zr.zone.maxPlayers + " joueurs dans cette zone.";
        for (Player p : list) {
            if (!p.isOnline()) return p.getName() + " n'est plus connecté.";
            if (byPlayer.containsKey(p.getUniqueId())) return p.getName() + " est déjà en expédition.";
            Profile pr = profile(p.getUniqueId());
            if (pr != null && pr.act() < zr.zone.act) return p.getName() + " n'a pas encore débloqué l'acte " + zr.zone.act + ".";
        }
        int idx = -1;
        if (!zr.running.containsKey(1)) idx = 1;
        else if (zr.model != null && !zr.modelBusy)
            for (int n = 2; n <= maxInstances(); n++)
                if (!zr.running.containsKey(n)) {
                    idx = n;
                    break;
                }
        if (idx < 0) return "Toutes les instances de " + zr.zone.name + " sont occupées, réessaie dans quelques minutes.";
        Expedition ex = new Expedition(this, zr, idx, night, list.get(0), list);
        zr.running.put(idx, ex);
        for (Player p : list) byPlayer.put(p.getUniqueId(), ex);
        ex.squadMsg("Préparation de §f" + zr.zone.name + "§7 (" + (night ? "nuit" : "jour") + ")...");
        if (idx == 1) {
            World w = zr.origin;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (ex.state == Expedition.State.PREPARING) ex.launch(w);
            }, 20L);
        } else {
            String name = WorldClone.name(zr.zone.world, idx);
            WorldClone.create(plugin, zr.model, name, w -> {
                if (ex.state != Expedition.State.PREPARING) {
                    if (w != null) WorldClone.destroy(plugin, w, zr.zone.world, this::toRefuge);
                    return;
                }
                if (w == null) {
                    ex.squadMsg("§cImpossible de préparer la zone, réessaie plus tard.");
                    ex.state = Expedition.State.ENDED;
                    for (UUID id : ex.members.keySet()) byPlayer.remove(id);
                    zr.running.remove(ex.index);
                    return;
                }
                ex.launch(w);
            });
        }
        reportStatus();
        return null;
    }

    @Override
    public boolean inExpedition(Player p) {
        Expedition ex = byPlayer.get(p.getUniqueId());
        if (ex == null) return false;
        if (ex.state == Expedition.State.PREPARING) return true;
        Expedition.Member m = ex.members.get(p.getUniqueId());
        return m != null && m.active();
    }

    /** Fin d'une expédition : l'instance est libérée (copie détruite, partie 1 rendue propre). */
    void release(Expedition ex) {
        ZoneRt zr = ex.zr;
        if (ex.index == 1 || ex.world == null) {
            zr.running.remove(ex.index);
        } else {
            World w = ex.world;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.isEnabled()) WorldClone.destroy(plugin, w, zr.zone.world, this::toRefuge);
                else Bukkit.unloadWorld(w, false);
                zr.running.remove(ex.index);
                reportStatus();
            }, plugin.isEnabled() ? 40L : 0L);
        }
        reportStatus();
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        for (ZoneRt zr : zones.values())
            for (Expedition ex : new ArrayList<>(zr.running.values())) {
                try {
                    ex.tick();
                } catch (RuntimeException e) {
                    plugin.getLogger().severe("Expedition " + ex.label() + " : " + e);
                    e.printStackTrace();
                }
            }
    }

    // ------------------------------------------------------------------ retour au Refuge

    void toRefuge(Player p) {
        RefugeApi r = plugin.refuge();
        World rw = null;
        try {
            rw = r == null ? null : r.world();
        } catch (RuntimeException ignored) {
        }
        if (rw != null) {
            r.sendToRefuge(p);
            World fw = rw;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (isAnyZoneWorld(p.getWorld())) {
                    Location s = r.spawn();
                    p.teleport(s != null ? s : fw.getSpawnLocation());
                }
            }, 5L);
        } else {
            if (!warnedNoRefuge) {
                warnedNoRefuge = true;
                plugin.getLogger().warning("Module Refuge sans monde : retour des expeditions au spawn du lobby.");
            }
            World lobby = Bukkit.getWorld("lobby");
            p.teleport((lobby != null ? lobby : Bukkit.getWorlds().get(0)).getSpawnLocation());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> afterReturn(p), 10L);
    }

    private boolean inRefugeWorld(Player p) {
        RefugeApi r = plugin.refuge();
        World rw = null;
        try {
            rw = r == null ? null : r.world();
        } catch (RuntimeException ignored) {
        }
        return rw != null ? p.getWorld().equals(rw) : p.getWorld().getName().equals("refuge");
    }

    /** Arrivé au Refuge : objets assurés rendus, bilan affiché. */
    void afterReturn(Player p) {
        if (!p.isOnline()) return;
        if (inRefugeWorld(p) && sacs.hasWaiting(p.getUniqueId())) {
            ItemStack[] items = sacs.takeWaiting(p.getUniqueId());
            int n = 0;
            for (ItemStack it : items) {
                if (it == null || it.getType().isAir()) continue;
                n += it.getAmount();
                for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), rest);
            }
            if (n > 0) Txt.msg(p, "§aAssurance : §f" + n + "§a objet(s) assuré(s) te sont rendus.");
        }
        Bilan b = pendingBilan.remove(p.getUniqueId());
        if (b != null) {
            RefugeApi r = plugin.refuge();
            try {
                if (r != null) r.showBilan(p, b);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("showBilan : " + e);
            }
        }
    }

    // ------------------------------------------------------------------ extraction, mort, abandon

    /** Nettoyage du joueur en quittant l'expédition (il est encore dans la zone). */
    private void cleanup(Expedition ex, Expedition.Member m, Player p) {
        p.hideBossBar(ex.bar);
        for (Expedition.Extraction e : ex.extractions.values()) p.hideBossBar(e.bar);
        if (m.board != null && p.getScoreboard() == m.board) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        m.board = null;
        m.obj = null;
        m.searchBlock = null;
        if (p.getWorld().equals(ex.world) || isAnyZoneWorld(p.getWorld())) {
            p.setPose(Pose.STANDING, false);
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
            p.setFireTicks(0);
            p.setFallDistance(0);
        }
        plugin.armes().stopBleed(p);
    }

    void extract(Expedition ex, Expedition.Member m, Player p, Zone.Point point) {
        if (!m.active()) return;
        if (m.state == Expedition.MState.DOWNED) ex.revive(p, m, null);
        m.state = Expedition.MState.EXTRACTED;
        List<ItemStack> gained = ex.gained(p, m);
        Bilan b = ex.bilan(m, true, gained, List.of("Extrait par " + point.name() + "."));
        Profile pr = profile(m.id);
        if (pr != null) {
            pr.setInsured(List.of());
            pr.save();
        }
        cleanup(ex, m, p);
        p.closeInventory();
        eemi("reward " + m.name + " refuge win");
        ex.squadMsg("§a" + m.name + " est extrait.");
        Txt.msg(p, "§aExtraction réussie ! §7Tout ce que tu portes est à toi.");
        pendingBilan.put(m.id, b);
        unregister(m.id);
        toRefuge(p);
    }

    /** Coup fatal (pas de mise à terre possible) : le joueur meurt, son sac reste dans la zone. */
    void die(Expedition ex, Expedition.Member m, Player p, String why) {
        if (!m.active()) return;
        m.state = Expedition.MState.DEAD;
        List<String> notes = new ArrayList<>();
        notes.add(why);
        dropToSac(ex, m, p, notes);
        cleanup(ex, m, p);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5);
        Bilan b = ex.bilan(m, false, List.of(), notes);
        pendingBilan.put(m.id, b);
        p.showTitle(net.kyori.adventure.title.Title.title(Txt.c("§4§lMORT"), Txt.c("§7" + why)));
        ex.squadMsg("§4" + m.name + " est mort. §7Son sac est resté sur place.");
        unregister(m.id);
        toRefuge(p);
        ex.checkEnd();
    }

    /**
     * Tout ce que porte le joueur va dans son sac (posé dans la zone, enregistré pour l'expédition suivante),
     * sauf les objets assurés, mis de côté pour son retour au Refuge. Le joueur doit être dans la zone.
     */
    private void dropToSac(Expedition ex, Expedition.Member m, Player p, List<String> notes) {
        p.closeInventory();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) if (it != null && !it.getType().isAir()) items.add(it.clone());
        ItemStack cursor = p.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) items.add(cursor.clone());
        p.setItemOnCursor(null);
        p.getInventory().clear();
        // Assurance
        List<ItemStack> back = new ArrayList<>();
        Profile pr = profile(m.id);
        if (pr != null) {
            for (ItemStack ins : pr.insured()) {
                if (ins == null) continue;
                int need = ins.getAmount();
                for (ItemStack it : items) {
                    if (need <= 0) break;
                    if (it.getAmount() <= 0 || !it.isSimilar(ins)) continue;
                    int take = Math.min(need, it.getAmount());
                    ItemStack c = it.clone();
                    c.setAmount(take);
                    back.add(c);
                    it.setAmount(it.getAmount() - take);
                    need -= take;
                }
            }
            pr.setInsured(List.of());
            pr.save();
        }
        items.removeIf(i -> i.getAmount() <= 0);
        if (!back.isEmpty()) {
            sacs.addWaiting(m.id, back);
            notes.add("Assurance : " + back.stream().mapToInt(ItemStack::getAmount).sum() + " objet(s) rendu(s) au Refuge.");
        }
        if (items.isEmpty()) return;
        Block at = p.getLocation().getBlock();
        if ((p.getLocation().getY() < ex.zone.minY || !ex.zone.inBounds(at.getX(), at.getZ())) && m.lastSafe != null)
            at = m.lastSafe.getBlock();
        Expedition.Sac sac = ex.placeSac(m.id, m.name, at, items.toArray(new ItemStack[0]), null);
        if (sac != null) {
            Sacs.Saved s = new Sacs.Saved(ex.zone.id, m.id, m.name, sac.block.getX(), sac.block.getY(), sac.block.getZ(),
                    items.toArray(new ItemStack[0]));
            sac.current = s;
            sacs.put(s);
            notes.add("Ton sac est resté en " + s.x + " " + s.y + " " + s.z + " : ton escouade peut le récupérer, et toi à ta prochaine expédition ici.");
        }
    }

    /** Le joueur quitte la zone sans extraction (commande, déconnexion) : compté comme mort. */
    private void abandon(Expedition ex, Expedition.Member m, Player p, boolean stillInZone, String why) {
        if (!m.active()) return;
        m.state = Expedition.MState.LEFT;
        List<String> notes = new ArrayList<>();
        notes.add(why);
        if (stillInZone) dropToSac(ex, m, p, notes);
        cleanup(ex, m, p);
        pendingBilan.put(m.id, ex.bilan(m, false, List.of(), notes));
        ex.squadMsg("§c" + m.name + " a quitté l'expédition.");
        unregister(m.id);
        ex.checkEnd();
    }

    /** Fin forcée (arrêt admin, arrêt du serveur, brume) pour un joueur encore dans la zone : il garde tout. */
    void forceOut(Expedition ex, Expedition.Member m, Player p, String why) {
        if (!m.active()) return;
        if (m.state == Expedition.MState.DOWNED) ex.revive(p, m, null);
        m.state = Expedition.MState.EXTRACTED;
        Bilan b = ex.bilan(m, false, ex.gained(p, m), List.of("Expédition arrêtée (" + why + "). Tu gardes ce que tu portes."));
        cleanup(ex, m, p);
        pendingBilan.put(m.id, b);
        unregister(m.id);
        if (plugin.isEnabled()) toRefuge(p);
        else {
            RefugeApi r = plugin.refuge();
            Location s = null;
            try {
                s = r == null ? null : r.spawn();
            } catch (RuntimeException ignored) {
            }
            if (s != null) p.teleport(s);
        }
    }

    // ------------------------------------------------------------------ événements : dégâts, mise à terre, mort

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Expedition.Member m = member(p);
        if (m == null) return;
        Expedition ex = byPlayer.get(p.getUniqueId());
        if (!p.getWorld().equals(ex.world)) return;
        if (m.state == Expedition.MState.DOWNED) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) die(ex, m, p, "Tu es tombé dans le vide.");
            return;
        }
        if (m.searchBlock != null && e.getFinalDamage() > 0) ex.cancelSearch(p, m, "tu as pris un coup");
        if (e.getFinalDamage() < p.getHealth()) return;
        e.setCancelled(true);
        boolean teammate = false;
        for (Expedition.Member o : ex.members.values()) {
            if (o == m || o.state != Expedition.MState.ACTIVE) continue;
            Player op = o.player();
            if (op != null && op.getWorld().equals(ex.world)) teammate = true;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            die(ex, m, p, "Tu es tombé dans le vide.");
            return;
        }
        if (teammate) {
            if (m.downs < plugin.getConfig().getInt("expeditions.a-terre.max", 2)) ex.goDown(p, m);
            else die(ex, m, p, "Troisième mise à terre : tu n'as pas survécu.");
            return;
        }
        // Seul (ou plus personne debout) : mort directe, sauf « Seconde chance ».
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (!Expedition.isSecondChance(it)) continue;
            it.setAmount(it.getAmount() - 1);
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(Math.max(1, (max == null ? 20 : max.getValue()) * 0.3));
            p.setNoDamageTicks(60);
            p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1f, 1f);
            Txt.msg(p, "§6Seconde chance ! §7Tu te relèves.");
            return;
        }
        die(ex, m, p, "Tu as succombé.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Firework fw && fw.getScoreboardTags().contains(TAG)) {
            e.setCancelled(true);
            return;
        }
        Player attacker = d instanceof Player pl ? pl : d instanceof Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
        if (attacker == null) return;
        Expedition ex = byPlayer.get(attacker.getUniqueId());
        if (ex == null || ex.state != Expedition.State.RUNNING) return;
        Expedition.Member m = ex.members.get(attacker.getUniqueId());
        if (m == null) return;
        if (m.state == Expedition.MState.DOWNED) {
            e.setCancelled(true);
            return;
        }
        Entity v = e.getEntity();
        // Coop pur : pas de tir ami. Décor de la map (cadres, porte-armures) protégé.
        if (v instanceof Player || v instanceof Hanging || v instanceof ArmorStand) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p) || !plugin.zombies().ours(e.getEntity())) return;
        Expedition ex = byWorld(e.getEntity().getWorld());
        if (ex == null) return;
        Expedition.Member m = ex.members.get(p.getUniqueId());
        if (m == null || m.state != Expedition.MState.ACTIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        if (!plugin.zombies().ours(e.getEntity())) return;
        Player k = e.getEntity().getKiller();
        Expedition ex = byWorld(e.getEntity().getWorld());
        if (ex == null) return;
        ex.hordeMobs.remove(e.getEntity().getUniqueId());
        if (k == null) return;
        Expedition.Member m = ex.members.get(k.getUniqueId());
        if (m == null || !m.active()) return;
        m.kills++;
        if (m.killRewards < plugin.getConfig().getInt("expeditions.eemicore.kills-max", 10)) {
            m.killRewards++;
            eemi("reward " + m.name + " refuge kill");
        }
    }

    /** Filet de sécurité : mort vanilla (/kill, dégâts qui contournent l'annulation). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Expedition.Member m = member(p);
        if (m == null) return;
        Expedition ex = byPlayer.get(p.getUniqueId());
        e.getDrops().clear();
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        m.state = Expedition.MState.DEAD;
        List<String> notes = new ArrayList<>();
        notes.add("Tu es mort.");
        dropToSac(ex, m, p, notes);
        cleanup(ex, m, p);
        pendingBilan.put(m.id, ex.bilan(m, false, List.of(), notes));
        ex.squadMsg("§4" + m.name + " est mort. §7Son sac est resté sur place.");
        unregister(m.id);
        respawnToRefuge.add(p.getUniqueId());
        ex.checkEnd();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!respawnToRefuge.remove(p.getUniqueId())) return;
        RefugeApi r = plugin.refuge();
        Location s = null;
        try {
            s = r == null ? null : r.spawn();
        } catch (RuntimeException ignored) {
        }
        if (s == null) {
            World lobby = Bukkit.getWorld("lobby");
            s = (lobby != null ? lobby : Bukkit.getWorlds().get(0)).getSpawnLocation();
        }
        e.setRespawnLocation(s);
        Bukkit.getScheduler().runTaskLater(plugin, () -> afterReturn(p), 10L);
    }

    // ------------------------------------------------------------------ événements : interactions en zone

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Expedition ex = byWorld(p.getWorld());
        if (ex == null || ex.state != Expedition.State.RUNNING) return;
        Expedition.Member m = ex.members.get(p.getUniqueId());
        boolean admin = m == null && p.hasPermission(EemiRefuge.PERM);
        if (m != null && m.state == Expedition.MState.DOWNED) {
            e.setCancelled(true);
            return;
        }
        if (m != null && !m.active()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b != null) {
            Expedition.Sac sac = ex.sacs.get(Zone.blockKey(b.getX(), b.getY(), b.getZ()));
            if (sac != null && b.getType() == Material.BARREL) {
                e.setCancelled(true);
                p.openInventory(sac.inv);
                p.playSound(b.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 0.8f, 0.9f);
                return;
            }
        }
        if (m == null) return;
        ItemStack hand = e.getItem();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && hand != null
                && (hand.getType() == Material.FIREWORK_ROCKET || "fusee_signal".equals(fr.eemicraft.refuge.Catalogue.id(hand)))) {
            Integer i = circle(ex, p.getLocation());
            if (i != null && !ex.extractions.containsKey(i)) {
                e.setCancelled(true);
                if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                ex.startExtraction(i, p);
                return;
            }
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || b == null) return;
        if (ex.wantsSearch(b)) {
            e.setCancelled(true);
            ex.startSearch(p, m, b);
            return;
        }
        long bk = Zone.blockKey(b.getX(), b.getY(), b.getZ());
        if (ex.zone.byBlock.containsKey(bk)) ex.searched.add(bk); // étagère, pot... touché : plus jamais retiré
        BlockData data = b.getBlockData();
        if (data instanceof Openable && !admin) {
            ex.noteRestore(b);
            if (data instanceof Door door)
                ex.noteRestore(b.getRelative(door.getHalf() == Bisected.Half.TOP ? org.bukkit.block.BlockFace.DOWN : org.bukkit.block.BlockFace.UP));
            plugin.bruit().emit(b.getLocation().add(0.5, 0.5, 0.5), 6, p);
        }
    }

    private Integer circle(Expedition ex, Location loc) {
        for (int i = 0; i < ex.zone.extractions.size(); i++) {
            Zone.Point pt = ex.zone.extractions.get(i);
            double dx = loc.getX() - pt.x(), dz = loc.getZ() - pt.z();
            if (dx * dx + dz * dz <= pt.radius() * pt.radius() && Math.abs(loc.getY() - pt.y()) <= 4) return i;
        }
        return null;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Expedition ex = byWorld(e.getWorld());
        if (ex == null || ex.state != Expedition.State.RUNNING) return;
        ex.fillChunk(e.getChunk().getX(), e.getChunk().getZ());
    }

    /** Pas de griefing en expédition : ni casse, ni pose, ni seau (les admins hors escouade construisent). */
    private boolean locked(Player p) {
        Expedition ex = byWorld(p.getWorld());
        if (ex == null || ex.state != Expedition.State.RUNNING) return false;
        return ex.members.containsKey(p.getUniqueId()) || p.getGameMode() != GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && locked(p)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ événements : sortie de la zone

    /** Téléporté hors de son instance sans extraction (commande /lobby...) : compté comme une mort, sac posé. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Expedition.Member m = member(p);
        if (m == null || e.getTo() == null) return;
        Expedition ex = byPlayer.get(p.getUniqueId());
        if (ex.world == null || !p.getWorld().equals(ex.world) || ex.world.equals(e.getTo().getWorld())) return;
        abandon(ex, m, p, true, "Tu as quitté la zone sans extraction : c'est comme une mort.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> afterReturn(p), 10L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Expedition.Member m = member(p);
        if (m != null) {
            Expedition ex = byPlayer.get(p.getUniqueId());
            if (ex.world != null && e.getFrom().equals(ex.world) && !p.getWorld().equals(ex.world)) {
                // Cas rare (portail...) : l'inventaire est déjà celui d'un autre monde, on n'y touche pas.
                abandon(ex, m, p, false, "Tu as quitté la zone sans extraction.");
            }
        }
        if (inRefugeWorld(p)) Bukkit.getScheduler().runTaskLater(plugin, () -> afterReturn(p), 10L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Expedition ex = byPlayer.get(p.getUniqueId());
        if (ex == null) return;
        if (ex.state == Expedition.State.PREPARING) {
            ex.members.remove(p.getUniqueId());
            byPlayer.remove(p.getUniqueId());
            return;
        }
        Expedition.Member m = member(p);
        if (m == null) return;
        // Déconnexion en expédition = mort (inventaire vidé avant la sauvegarde de Multiverse-Inventories).
        abandon(ex, m, p, p.getWorld().equals(ex.world), "Déconnexion pendant l'expédition : c'est comme une mort.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            boolean zone = isAnyZoneWorld(p.getWorld()) && !inExpedition(p);
            // Un admin peut rester dans le monde d'origine (construction) tant qu'aucune expédition n'y tourne.
            if (zone && (!p.hasPermission(EemiRefuge.PERM) || byWorld(p.getWorld()) != null)) toRefuge(p);
            else afterReturn(p);
        }, 5L);
    }

    // ------------------------------------------------------------------ EemiCore

    void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    void reportStatus() {
        int playing = 0, running = 0;
        for (ZoneRt zr : zones.values())
            for (Expedition ex : zr.running.values()) {
                running++;
                for (Expedition.Member m : ex.members.values()) if (m.active()) playing++;
            }
        int max = 0;
        for (ZoneRt zr : zones.values()) if (zr.origin != null) max += zr.zone.maxPlayers * maxInstances();
        String status = "status refuge " + (running > 0 ? "playing" : "waiting") + " 0 " + playing + " " + Math.max(4, max);
        long now = System.currentTimeMillis();
        if (status.equals(lastStatus) && now - lastStatusAt < 30_000) return;
        lastStatus = status;
        lastStatusAt = now;
        eemi(status);
    }

    // ------------------------------------------------------------------ commandes admin

    @Override
    public boolean handleCommand(CommandSender s, String[] a) {
        if (a.length == 0) return false;
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("expe") && !sub.equals("zone")) return false;
        if (!s.hasPermission(EemiRefuge.PERM)) {
            Txt.msg(s, "Réservé aux admins.");
            return true;
        }
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("expe")) {
            switch (act) {
                case "liste", "list" -> list(s);
                case "stop" -> stop(s, a);
                case "start" -> adminStart(s, a);
                case "reload" -> {
                    loot.load(new File(plugin.getDataFolder(), "loot.yml"));
                    Txt.msg(s, "loot.yml relu. (Les zones se relisent au redémarrage ; config.yml par /refuge reload.)");
                }
                default -> Txt.msg(s, "/refuge expe liste | stop [zone] [n] | start <zone> <joueur...> [nuit] | reload");
            }
        } else {
            switch (act) {
                case "verifier", "vérifier", "check" -> verify(s, a.length > 2 ? a[2] : null);
                case "modele", "modèle" -> {
                    ZoneRt zr = a.length > 2 ? zones.get(a[2].toLowerCase(Locale.ROOT)) : null;
                    if (zr == null || zr.origin == null) {
                        Txt.msg(s, "Zone inconnue ou monde absent.");
                        return true;
                    }
                    if (!zr.running.isEmpty()) {
                        Txt.msg(s, "§cUne expédition est en cours dans cette zone : attends qu'elle finisse.");
                        return true;
                    }
                    makeModel(zr);
                    Txt.msg(s, "Modèle de " + zr.zone.id + " en cours de copie depuis " + zr.zone.world + ".");
                }
                case "liste", "list" -> {
                    for (ZoneRt zr : zones.values())
                        Txt.msg(s, "§f" + zr.zone.id + " §7(" + zr.zone.name + ", acte " + zr.zone.act + ") monde " + zr.zone.world
                                + (zr.origin == null ? " §cabsent" : zr.model != null ? " §amodèle prêt" : " §emodèle en préparation"));
                }
                default -> Txt.msg(s, "/refuge zone liste | verifier <id> | modele <id>");
            }
        }
        return true;
    }

    /** Complétion des sous-commandes admin (appelée par EemiRefuge). */
    public List<String> complete(String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 2 && a[0].equalsIgnoreCase("expe")) out.addAll(List.of("liste", "stop", "start", "reload"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("zone")) out.addAll(List.of("liste", "verifier", "modele"));
        else if (a.length == 3 && (a[0].equalsIgnoreCase("zone") || a[1].equalsIgnoreCase("start") || a[1].equalsIgnoreCase("stop")))
            out.addAll(zones.keySet());
        else if (a.length >= 4 && a[1].equalsIgnoreCase("start")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            out.add("nuit");
        }
        return out;
    }

    private void list(CommandSender s) {
        if (zones.isEmpty()) {
            Txt.msg(s, "Aucune zone chargée.");
            return;
        }
        for (ZoneRt zr : zones.values()) {
            Txt.msg(s, "§f" + zr.zone.name + " §8(" + zr.zone.id + ") §7: " + (zr.origin == null ? "§cmonde absent" : zr.running.size() + " expédition(s)")
                    + (zr.model == null && zr.origin != null ? " §e(modèle pas prêt : 1 instance)" : ""));
            for (Expedition ex : zr.running.values()) {
                StringBuilder sb = new StringBuilder();
                for (Expedition.Member m : ex.members.values()) sb.append(m.name).append(" (").append(m.state).append(") ");
                s.sendMessage(Txt.c("§8 - §e#" + ex.index + " §f" + (ex.world == null ? "?" : ex.world.getName()) + " §7"
                        + ex.state + " " + Expedition.mmss(ex.elapsedSec()) + (ex.night ? " nuit" : " jour") + " §f" + sb));
            }
        }
    }

    private void stop(CommandSender s, String[] a) {
        Expedition target = null;
        if (a.length >= 3) {
            ZoneRt zr = zones.get(a[2].toLowerCase(Locale.ROOT));
            int n = 1;
            if (zr == null && zones.size() == 1) {
                zr = zones.values().iterator().next();
                try {
                    n = Integer.parseInt(a[2]);
                } catch (NumberFormatException ignored) {
                }
            } else if (a.length >= 4) {
                try {
                    n = Integer.parseInt(a[3]);
                } catch (NumberFormatException ignored) {
                }
            }
            if (zr != null) target = zr.running.get(n);
        } else if (s instanceof Player p) {
            target = byWorld(p.getWorld());
            if (target == null) target = byPlayer.get(p.getUniqueId());
        }
        if (target == null) {
            Txt.msg(s, "Aucune expédition trouvée. /refuge expe liste");
            return;
        }
        target.adminStop = true;
        if (target.state == Expedition.State.PREPARING) {
            target.state = Expedition.State.ENDED;
            for (UUID id : target.members.keySet()) byPlayer.remove(id);
            target.zr.running.remove(target.index);
        } else target.end("arrêt admin", false);
        Txt.msg(s, "Expédition " + target.label() + " arrêtée.");
    }

    private void adminStart(CommandSender s, String[] a) {
        if (a.length < 4) {
            Txt.msg(s, "/refuge expe start <zone> <joueur...> [nuit]");
            return;
        }
        boolean night = false;
        List<Player> squad = new ArrayList<>();
        for (int i = 3; i < a.length; i++) {
            if (a[i].equalsIgnoreCase("nuit") || a[i].equalsIgnoreCase("night")) {
                night = true;
                continue;
            }
            if (a[i].equalsIgnoreCase("jour")) continue;
            Player p = Bukkit.getPlayerExact(a[i]);
            if (p == null) {
                Txt.msg(s, "Joueur introuvable : " + a[i]);
                return;
            }
            squad.add(p);
        }
        if (squad.isEmpty()) {
            Txt.msg(s, "Aucun joueur.");
            return;
        }
        String err = start(squad.get(0), squad, a[2], night);
        Txt.msg(s, err == null ? "Expédition lancée (" + (night ? "nuit" : "jour") + ")." : "§c" + err);
    }

    // ------------------------------------------------------------------ /refuge zone verifier <id>

    private void verify(CommandSender s, String id) {
        ZoneRt zr = id == null ? null : zones.get(id.toLowerCase(Locale.ROOT));
        Zone z;
        if (zr != null) z = zr.zone;
        else {
            File f = id == null ? null : new File(new File(plugin.getDataFolder(), "zones"), id.toLowerCase(Locale.ROOT) + ".yml");
            if (f == null || !f.isFile()) {
                Txt.msg(s, "Zone inconnue. Fichiers dans plugins/EemiRefuge/zones/.");
                return;
            }
            z = Zone.load(f, plugin.getLogger());
        }
        World w = Bukkit.getWorld(z.world);
        if (w == null) {
            Txt.msg(s, "§cLe monde " + z.world + " n'est pas chargé.");
            return;
        }
        Set<Long> chunks = new HashSet<>();
        List<Zone.Point> pts = new ArrayList<>();
        pts.addAll(z.arrivals);
        pts.addAll(z.extractions);
        pts.addAll(z.hordes);
        for (Zone.Point p : pts) chunks.add(Zone.chunkKey((int) Math.floor(p.x()) >> 4, (int) Math.floor(p.z()) >> 4));
        chunks.addAll(z.byChunk.keySet());
        Txt.msg(s, "Vérification de " + z.id + " : chargement de " + chunks.size() + " chunk(s)...");
        List<CompletableFuture<?>> fs = new ArrayList<>();
        for (long k : chunks) fs.add(w.getChunkAtAsync((int) k, (int) (k >> 32)));
        CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            List<String> problems = new ArrayList<>(z.warnings);
            checkPoints(w, z, z.arrivals, "arrivée", true, problems);
            checkPoints(w, z, z.extractions, "extraction", true, problems);
            checkPoints(w, z, z.hordes, "horde", false, problems);
            int noInv = 0, noTable = 0, out = 0, mismatch = 0;
            Map<String, Integer> pieces = new HashMap<>();
            for (Zone.Box b : z.containers) {
                if (!z.inBounds(b.x(), b.y(), b.z())) {
                    out++;
                    if (out <= 5) problems.add("conteneur hors des bornes : " + b.x() + " " + b.y() + " " + b.z());
                }
                Block block = w.getBlockAt(b.x(), b.y(), b.z());
                BlockState st = block.getState(false);
                if (Expedition.inventoryOf(st) == null) {
                    noInv++;
                    if (noInv <= 8) problems.add("pas de conteneur en " + b.x() + " " + b.y() + " " + b.z() + " (" + block.getType().getKey().getKey() + ")");
                } else if (!b.type().equals("?") && !block.getType().getKey().getKey().contains(b.type().replace("_shelf", "").replace("shelf", "_shelf"))
                        && !(b.type().equals("shelf") && block.getType().getKey().getKey().endsWith("_shelf"))) mismatch++;
                if (!loot.hasOwnTable(b.piece())) {
                    noTable++;
                    pieces.merge(b.piece(), 1, Integer::sum);
                }
            }
            if (noInv > 8) problems.add("... " + (noInv - 8) + " autre(s) conteneur(s) absent(s)");
            if (out > 5) problems.add("... " + (out - 5) + " autre(s) conteneur(s) hors des bornes");
            if (mismatch > 0) problems.add(mismatch + " conteneur(s) dont le bloc ne correspond pas au type noté");
            if (noTable > 0) problems.add(noTable + " conteneur(s) sans table propre (table « defaut ») : " + pieces);
            Txt.msg(s, "§f" + z.name + " §7: " + z.arrivals.size() + " arrivée(s), " + z.extractions.size() + " extraction(s), "
                    + z.hordes.size() + " horde(s), " + z.containers.size() + " conteneur(s).");
            if (problems.isEmpty()) Txt.msg(s, "§aAucun problème : la zone est jouable.");
            else {
                Txt.msg(s, "§c" + problems.size() + " problème(s) :");
                for (int i = 0; i < Math.min(25, problems.size()); i++) s.sendMessage(Txt.c("§8 - §7" + problems.get(i)));
                if (problems.size() > 25) s.sendMessage(Txt.c("§8 ... (voir verification_" + z.id + ".txt)"));
            }
            try {
                Files.write(new File(plugin.getDataFolder(), "verification_" + z.id + ".txt").toPath(), problems);
            } catch (IOException ignored) {
            }
        }));
    }

    private void checkPoints(World w, Zone z, List<Zone.Point> pts, String kind, boolean strict, List<String> problems) {
        for (Zone.Point p : pts) {
            String where = kind + " « " + p.name() + " » (" + (int) Math.floor(p.x()) + " " + (int) Math.floor(p.y()) + " " + (int) Math.floor(p.z()) + ")";
            if (!z.inBounds(p.x(), p.y(), p.z())) problems.add(where + " : hors des bornes");
            int x = (int) Math.floor(p.x()), y = (int) Math.floor(p.y()), zz = (int) Math.floor(p.z());
            Block feet = w.getBlockAt(x, y, zz), head = w.getBlockAt(x, y + 1, zz), below = w.getBlockAt(x, y - 1, zz);
            if (!feet.isPassable() || !head.isPassable()) problems.add(where + " : pas d'air (" + feet.getType().getKey().getKey() + "/" + head.getType().getKey().getKey() + ")");
            if (!below.getType().isSolid()) problems.add(where + " : pas de sol (" + below.getType().getKey().getKey() + ")");
            if (kind.equals("extraction") && p.radius() < 1) problems.add(where + " : rayon trop petit");
            if (!strict) continue;
        }
    }

}
