package fr.eemicraft.refuge.expe;

import fr.eemicraft.refuge.Catalogue;
import fr.eemicraft.refuge.Txt;
import fr.eemicraft.refuge.Zombies;
import fr.eemicraft.refuge.api.Bilan;
import fr.eemicraft.refuge.api.Profile;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.loot.Lootable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Une expédition en cours : une escouade dans une instance (copie) d'une zone. */
final class Expedition {

    enum State { PREPARING, RUNNING, ENDED }

    enum MState { ACTIVE, DOWNED, EXTRACTED, DEAD, LEFT }

    static final class Member {
        final UUID id;
        final String name;
        MState state = MState.ACTIVE;
        int downs, kills, revives, killRewards;
        long downedUntil;
        UUID reviver;
        int reviveTicks;
        Scoreboard board;
        Objective obj;
        Map<String, Integer> startCounts = new HashMap<>();
        Location lastSafe;
        // fouille
        Block searchBlock;
        Location searchFrom;
        int searchTicks, searchTotal;
        // allumage de la fusée accroupi
        int sneakTicks, sneakPoint = -1;
        long lastHint;

        Member(Player p) {
            id = p.getUniqueId();
            name = p.getName();
        }

        boolean active() {
            return state == MState.ACTIVE || state == MState.DOWNED;
        }

        Player player() {
            return Bukkit.getPlayer(id);
        }
    }

    /** Le sac d'un joueur mort : un tonneau dans la zone, contenu dans un inventaire du plugin. */
    static final class Sac implements InventoryHolder {
        final UUID owner;
        final String ownerName;
        final Block block;
        final Inventory inv;
        final Sacs.Saved previous;   // non null : sac d'une expédition précédente reposé
        Sacs.Saved current;          // enregistrement fait à la mort (mis à jour à la fin)
        TextDisplay label;

        Sac(UUID owner, String ownerName, Block block, ItemStack[] items, Sacs.Saved previous) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.block = block;
            this.previous = previous;
            inv = Bukkit.createInventory(this, 54, Txt.c("§8Sac de " + ownerName));
            for (ItemStack i : items) if (i != null && !i.getType().isAir()) inv.addItem(i);
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class Extraction {
        final int idx;
        final Zone.Point point;
        final long startMs, endMs;
        long nextWave;
        /** Vagues déjà envoyées (plafonnées : extraction.vagues ou extraction-vagues de la zone). */
        int waves;
        final BossBar bar;

        Extraction(int idx, Zone.Point point, long startMs, long endMs) {
            this.idx = idx;
            this.point = point;
            this.startMs = startMs;
            this.endMs = endMs;
            bar = BossBar.bossBar(Txt.c("§6Extraction"), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        }
    }

    final Expeditions mgr;
    final Expeditions.ZoneRt zr;
    final Zone zone;
    final int index;
    final boolean night;
    final String leaderName;
    World world;
    State state = State.PREPARING;
    long startMs;
    boolean adminStop;
    final Map<UUID, Member> members = new LinkedHashMap<>();
    final Set<Long> filled = new HashSet<>();
    final Set<Long> searched = new HashSet<>();
    final Map<Long, Sac> sacs = new HashMap<>();
    final Set<UUID> hordeMobs = new HashSet<>();
    final Map<Integer, Extraction> extractions = new HashMap<>();
    final Set<Long> restoreNoted = new HashSet<>();
    final BossBar bar = BossBar.bossBar(Txt.c("§fExpédition"), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    private int ticks;
    /** Panneau flottant au-dessus de chaque point d'extraction (recréé si son chunk a été déchargé). */
    private final Map<Integer, TextDisplay> extractLabels = new HashMap<>();
    private long nextHordeSec = -1, lastPermanentSec;
    private boolean mistAnnounced;
    private final Set<Zone.Box> warnedBoxes = new HashSet<>();

    Expedition(Expeditions mgr, Expeditions.ZoneRt zr, int index, boolean night, Player leader, List<Player> squad) {
        this.mgr = mgr;
        this.zr = zr;
        this.zone = zr.zone;
        this.index = index;
        this.night = night;
        this.leaderName = leader.getName();
        for (Player p : squad) members.put(p.getUniqueId(), new Member(p));
    }

    long elapsedSec() {
        return state == State.RUNNING || state == State.ENDED ? (System.currentTimeMillis() - startMs) / 1000 : 0;
    }

    List<Player> activePlayers(boolean withDowned) {
        List<Player> out = new ArrayList<>();
        for (Member m : members.values()) {
            if (!(m.state == MState.ACTIVE || withDowned && m.state == MState.DOWNED)) continue;
            Player p = m.player();
            if (p != null && world != null && p.getWorld().equals(world)) out.add(p);
        }
        return out;
    }

    void squadMsg(String legacy) {
        for (Member m : members.values()) {
            Player p = m.player();
            if (p != null && m.active()) Txt.msg(p, legacy);
        }
    }

    private int cfg(String path, int def) {
        return mgr.plugin.getConfig().getInt("expeditions." + path, def);
    }

    private double cfgD(String path, double def) {
        return mgr.plugin.getConfig().getDouble("expeditions." + path, def);
    }

    // ------------------------------------------------------------------ lancement

    /** Le monde de l'instance est prêt : on prépare la zone et on y téléporte l'escouade. */
    void launch(World w) {
        world = w;
        Expeditions.rules(w);
        w.setTime(night ? zone.nightTime : zone.dayTime);
        w.setStorm(false);
        w.setThundering(false);
        if (zone.hasBorder()) {
            w.getWorldBorder().setCenter(zone.centerX(), zone.centerZ());
            w.getWorldBorder().setSize(zone.borderSize());
            w.getWorldBorder().setWarningDistance(6);
        }
        // Restes : nos zombies, objets au sol, sacs d'une expédition précédente (chunks chargés).
        for (Entity e : w.getEntities()) {
            if (mgr.plugin.zombies().ours(e) || e instanceof Item || e instanceof ExperienceOrb
                    || e.getScoreboardTags().contains(Expeditions.TAG)) e.remove();
        }
        filled.clear();
        searched.clear();
        for (List<Zone.Box> boxes : zone.byChunk.values()) {
            Zone.Box b = boxes.get(0);
            if (w.isChunkLoaded(b.x() >> 4, b.z() >> 4)) fillChunk(b.x() >> 4, b.z() >> 4);
        }
        List<Player> squad = new ArrayList<>();
        for (Member m : new ArrayList<>(members.values())) {
            Player p = m.player();
            if (p == null || !p.isOnline()) {
                members.remove(m.id);
                mgr.unregister(m.id);
                continue;
            }
            squad.add(p);
        }
        if (squad.isEmpty()) {
            mgr.plugin.getLogger().info("Expedition " + zone.id + " #" + index + " annulee : personne au depart.");
            state = State.RUNNING;
            startMs = System.currentTimeMillis();
            end("annulée", false);
            return;
        }
        // Sacs laissés par les membres lors d'une expédition précédente dans cette zone.
        for (Member m : members.values()) {
            Sacs.Saved s = mgr.sacs.get(zone.id, m.id);
            if (s == null) continue;
            Block b = w.getBlockAt(s.x, s.y, s.z);
            Sac sac = placeSac(m.id, s.ownerName, b, s.items, s);
            if (sac != null) {
                Player p = m.player();
                if (p != null) Txt.msg(p, "§eTon sac de la dernière expédition t'attend toujours en §f"
                        + s.x + " " + s.y + " " + s.z + "§e. C'est ta dernière chance de le récupérer.");
            }
        }
        Zone.Point arrival = zone.arrivals.isEmpty() ? null : zone.arrivals.get(ThreadLocalRandom.current().nextInt(zone.arrivals.size()));
        Location to = arrival != null ? arrival.at(w) : w.getSpawnLocation();
        state = State.RUNNING;
        startMs = System.currentTimeMillis();
        nextHordeSec = cfg("hordes.premiere", 240);
        w.getChunkAtAsync(to).thenAccept(c -> Bukkit.getScheduler().runTask(mgr.plugin, () -> {
            for (Player p : squad) {
                Member m = members.get(p.getUniqueId());
                if (m == null || !p.isOnline() || state != State.RUNNING) continue;
                p.closeInventory();
                p.teleport(to);
                Bukkit.getScheduler().runTask(mgr.plugin, () -> arrived(p, m, arrival));
            }
        }));
        mgr.plugin.getLogger().info("Expedition " + zone.id + " #" + index + " (" + w.getName() + ", " + (night ? "nuit" : "jour")
                + ") : " + squad.size() + " joueur(s), chef " + leaderName + ".");
        mgr.reportStatus();
    }

    private void arrived(Player p, Member m, Zone.Point arrival) {
        if (!p.isOnline() || !p.getWorld().equals(world) || !m.active()) return;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
        m.lastSafe = p.getLocation();
        m.startCounts = counts(p);
        setupBoard(p, m);
        p.showBossBar(bar);
        p.showTitle(Title.title(Txt.c("§2" + zone.name), Txt.c(night ? "§9Nuit · rentre avant la brume" : "§eJour · rentre avant la brume"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.8f);
        Txt.msg(p, "Arrivée : §f" + (arrival != null ? arrival.name() : "?") + "§7. Fouille, puis rentre par un point d'extraction.");
        StringBuilder sb = new StringBuilder();
        for (Zone.Point e : zone.extractions) {
            if (sb.length() > 0) sb.append("§7, ");
            sb.append("§f").append(e.name());
        }
        if (sb.length() > 0) Txt.msg(p, "Extractions : " + sb + "§7. Sur place : §faccroupis-toi 3 s§7 ou §fclic droit avec une fusée§7.");
        Txt.msg(p, "§8La Marée monte à 15 min, horde permanente à 20 min, brume mortelle à 25 min.");
    }

    // ------------------------------------------------------------------ butin

    /**
     * Remplit les supports d'un chunk (au départ pour les chunks chargés, puis à chaque chargement de chunk).
     * Un support déjà ouvert ou touché pendant cette expédition (searched) n'est jamais retiré : on ne sait pas si
     * la copie de la zone a gardé son contenu au déchargement, mais un support jamais vu peut être retiré sans risque.
     */
    void fillChunk(int cx, int cz) {
        long key = Zone.chunkKey(cx, cz);
        filled.add(key);
        List<Zone.Box> boxes = zone.byChunk.get(key);
        if (boxes == null) return;
        List<String> ignored = mgr.plugin.getConfig().getStringList("expeditions.butin.types-ignores");
        if (ignored.isEmpty()) ignored = IGNORED;
        for (Zone.Box b : boxes) {
            if (ignored.contains(b.type()) || searched.contains(b.block())) continue;
            Block block = world.getBlockAt(b.x(), b.y(), b.z());
            BlockState st = block.getState(false);
            if (st instanceof Lootable l && l.hasLootTable()) l.clearLootTable();
            Inventory inv = inventoryOf(st);
            if (inv == null) {
                if (warnedBoxes.add(b))
                    mgr.plugin.getLogger().warning("Zone " + zone.id + " : pas de conteneur en " + b.x() + " " + b.y() + " " + b.z()
                            + " (" + block.getType() + ")");
                continue;
            }
            String type = b.type().equals("?") ? block.getType().getKey().getKey() : b.type();
            if (type.endsWith("_shelf")) type = "shelf";
            mgr.loot.fill(inv, type, b.piece(), zone.act, night);
        }
    }

    /** Supports marqués qui ne sont pas (encore) remplis : mécanismes du décor, entités, supports sans menu. */
    static final List<String> IGNORED = List.of("entonnoir", "hopper", "distributeur", "dispenser", "dropper", "porte_armure",
            "armor_stand", "cadre", "item_frame", "feu_de_camp", "campfire", "pupitre", "lectern");

    static Inventory inventoryOf(BlockState st) {
        if (st instanceof Chest c) return c.getBlockInventory();
        if (st instanceof io.papermc.paper.block.TileStateInventoryHolder h) return h.getInventory();
        return null;
    }

    /** Clic droit sur un support marqué : fouille de 1 à 2 s la première fois (conteneurs à menu seulement). */
    boolean wantsSearch(Block b) {
        long k = Zone.blockKey(b.getX(), b.getY(), b.getZ());
        Zone.Box box = zone.byBlock.get(k);
        if (box == null || searched.contains(k) || IGNORED.contains(box.type())) return false;
        return b.getState(false) instanceof Container;
    }

    void startSearch(Player p, Member m, Block b) {
        if (m.searchBlock != null && m.searchBlock.equals(b)) return;
        m.searchBlock = b;
        m.searchFrom = p.getLocation();
        m.searchTicks = 0;
        m.searchTotal = ThreadLocalRandom.current().nextInt(cfg("fouille.min-ticks", 20), Math.max(cfg("fouille.min-ticks", 20), cfg("fouille.max-ticks", 40)) + 1);
        p.playSound(b.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 0.7f);
        mgr.plugin.bruit().emit(b.getLocation().add(0.5, 0.5, 0.5), 3, p);
    }

    void cancelSearch(Player p, Member m, String why) {
        if (m.searchBlock == null) return;
        m.searchBlock = null;
        if (p != null && why != null) mgr.plugin.bruit().hitMessage(p, "§cFouille interrompue §7(" + why + ")");
    }

    private void searchTick(Player p, Member m) {
        if (m.searchBlock == null) return;
        if (p.getLocation().distanceSquared(m.searchFrom) > 0.36 || !p.getWorld().equals(world)) {
            cancelSearch(p, m, "tu as bougé");
            return;
        }
        m.searchTicks += 5;
        int n = Math.min(10, m.searchTicks * 10 / Math.max(1, m.searchTotal));
        p.sendActionBar(Txt.c("§7Fouille §a" + "▌".repeat(n) + "§8" + "▌".repeat(10 - n)));
        if (m.searchTicks % 10 == 0) p.playSound(m.searchBlock.getLocation(), Sound.BLOCK_GRAVEL_HIT, 0.4f, 1.4f);
        if (m.searchTicks < m.searchTotal) return;
        Block b = m.searchBlock;
        m.searchBlock = null;
        searched.add(Zone.blockKey(b.getX(), b.getY(), b.getZ()));
        BlockState st = b.getState(false);
        if (st instanceof Container c) p.openInventory(c.getInventory());
    }

    // ------------------------------------------------------------------ boucle (toutes les 5 ticks)

    void tick() {
        if (state != State.RUNNING || world == null) return;
        ticks++;
        long now = System.currentTimeMillis();
        long el = elapsedSec();
        boolean second = ticks % 4 == 0;
        for (Member m : new ArrayList<>(members.values())) {
            if (!m.active()) continue;
            Player p = m.player();
            if (p == null || !p.getWorld().equals(world)) continue;
            Location loc = p.getLocation();
            if (!zone.inBounds(loc.getX(), loc.getY(), loc.getZ()) && m.lastSafe != null && loc.getY() >= zone.minY) {
                p.teleport(m.lastSafe);
                mgr.plugin.bruit().hitMessage(p, "§cTu ne peux pas quitter la zone par là : trouve une extraction.");
            } else if (p.isOnGround() && loc.getY() >= zone.minY) m.lastSafe = loc;
            if (m.state == MState.DOWNED) {
                downedTick(p, m, now);
                continue;
            }
            searchTick(p, m);
            extractionHint(p, m);
        }
        if (second) {
            for (Extraction ex : new ArrayList<>(extractions.values())) extractionTick(ex, now);
            mist(el);
            updateBar(el);
            for (Member m : members.values()) {
                Player p = m.player();
                if (m.active() && p != null && p.getWorld().equals(world)) {
                    updateBoard(p, m, el);
                    compassTarget(p);
                }
            }
            if (ticks % 8 == 0) circles();
        }
        if (ticks % 20 == 0 && el >= cfg("zombies.grace", 20)) {
            despawnFar();
            ambientSpawns(el);
            hordes(el);
            hordePath();
        }
        long fatal = cfg("duree.brume", 1500) + cfg("duree.brume-fatale", 120);
        if (el >= fatal) {
            for (Member m : new ArrayList<>(members.values())) {
                Player p = m.player();
                if (m.active() && p != null) mgr.die(this, m, p, "La brume noire t'a emporté.");
            }
            checkEnd();
        }
    }

    // ------------------------------------------------------------------ à terre et relève

    void goDown(Player p, Member m) {
        m.state = MState.DOWNED;
        m.downs++;
        m.downedUntil = System.currentTimeMillis() + cfg("a-terre.duree", 40) * 1000L;
        m.reviver = null;
        m.reviveTicks = 0;
        cancelSearch(p, m, null);
        p.setHealth(1.0);
        p.setFireTicks(0);
        p.setPose(Pose.SWIMMING, true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3, false, false, true));
        mgr.plugin.armes().stopBleed(p);
        for (Mob mob : mgr.plugin.zombies().alive()) if (p.equals(mob.getTarget())) mob.setTarget(null);
        p.showTitle(Title.title(Txt.c("§c§lÀ TERRE"), Txt.c("§7Un coéquipier accroupi 4 s te relève"),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        int left = cfg("a-terre.max", 2) - m.downs + 1;
        squadMsg("§c" + m.name + " est à terre ! §7Accroupis-toi à côté 4 s pour le relever"
                + (left <= 1 ? " §c(la prochaine fois, c'est la mort)" : "") + ".");
    }

    private void downedTick(Player p, Member m, long now) {
        if (now >= m.downedUntil) {
            mgr.die(this, m, p, "Personne ne t'a relevé à temps.");
            return;
        }
        if (ticks % 8 == 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3, false, false, true));
        if (p.getPose() != Pose.SWIMMING) p.setPose(Pose.SWIMMING, true);
        if (ticks % 4 == 0)
            world.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.3, 0), 4, 0.3, 0.1, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(0x8A0303), 1.1f));
        Player helper = null;
        for (Member o : members.values()) {
            if (o == m || o.state != MState.ACTIVE) continue;
            Player op = o.player();
            if (op == null || !op.getWorld().equals(world) || !op.isSneaking()) continue;
            if (op.getLocation().distanceSquared(p.getLocation()) <= 2.2 * 2.2) {
                helper = op;
                break;
            }
        }
        long left = (m.downedUntil - now) / 1000;
        if (helper == null) {
            m.reviver = null;
            m.reviveTicks = 0;
            p.sendActionBar(Txt.c("§c§lÀ TERRE §7– §f" + left + " s §7– un coéquipier doit s'accroupir à côté de toi"));
            return;
        }
        if (!helper.getUniqueId().equals(m.reviver)) {
            m.reviver = helper.getUniqueId();
            m.reviveTicks = 0;
        }
        m.reviveTicks += 5;
        int need = cfg("a-terre.releve", 4) * 20;
        int n = Math.min(10, m.reviveTicks * 10 / need);
        String bar10 = "§a" + "▌".repeat(n) + "§8" + "▌".repeat(10 - n);
        p.sendActionBar(Txt.c("§eRelève par " + helper.getName() + " " + bar10));
        helper.sendActionBar(Txt.c("§eTu relèves " + m.name + " " + bar10));
        if (m.reviveTicks >= need) revive(p, m, helper);
    }

    void revive(Player p, Member m, Player by) {
        m.state = MState.ACTIVE;
        m.reviver = null;
        m.reviveTicks = 0;
        p.setPose(Pose.STANDING, false);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double hp = (max == null ? 20 : max.getValue()) * cfgD("a-terre.pv-releve", 0.3);
        p.setHealth(Math.max(1, hp));
        p.setNoDamageTicks(40);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.2f);
        if (by != null) {
            Member bm = members.get(by.getUniqueId());
            if (bm != null) bm.revives++;
            squadMsg("§a" + by.getName() + " a relevé " + m.name + ".");
        } else squadMsg("§a" + m.name + " se relève.");
    }

    // ------------------------------------------------------------------ extraction

    private Integer circleOf(Location loc) {
        for (int i = 0; i < zone.extractions.size(); i++) {
            Zone.Point e = zone.extractions.get(i);
            double dx = loc.getX() - e.x(), dz = loc.getZ() - e.z();
            if (dx * dx + dz * dz <= e.radius() * e.radius() && Math.abs(loc.getY() - e.y()) <= 4) return i;
        }
        return null;
    }

    private void extractionHint(Player p, Member m) {
        Integer i = circleOf(p.getLocation());
        if (i == null || extractions.containsKey(i)) {
            m.sneakTicks = 0;
            m.sneakPoint = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (p.isSneaking()) {
            if (m.sneakPoint != i) {
                m.sneakPoint = i;
                m.sneakTicks = 0;
            }
            m.sneakTicks += 5;
            int need = cfg("extraction.accroupi", 3) * 20;
            int n = Math.min(10, m.sneakTicks * 10 / need);
            p.sendActionBar(Txt.c("§6Fusée de signal §e" + "▌".repeat(n) + "§8" + "▌".repeat(10 - n)));
            if (m.sneakTicks >= need) {
                m.sneakTicks = 0;
                startExtraction(i, p);
            }
        } else {
            m.sneakTicks = 0;
            if (now - m.lastHint > 2500) {
                m.lastHint = now;
                mgr.plugin.bruit().hitMessage(p, "§6" + zone.extractions.get(i).name() + " §7: accroupis-toi 3 s (ou clic droit avec une fusée) pour appeler le transport");
            }
        }
    }

    /** Fusée de signal : la vague arrive, il faut tenir 45 à 90 s dans le cercle. */
    void startExtraction(int i, Player by) {
        if (extractions.containsKey(i) || state != State.RUNNING) return;
        Zone.Point pt = zone.extractions.get(i);
        long now = System.currentTimeMillis();
        int min = cfg("extraction.duree-min", 45), max = Math.max(min, cfg("extraction.duree-max", 90));
        int dur = ThreadLocalRandom.current().nextInt(min, max + 1);
        Extraction ex = new Extraction(i, pt, now, now + dur * 1000L);
        ex.nextWave = now + 3000;
        extractions.put(i, ex);
        Location c = pt.center(world);
        world.spawn(c.clone().add(0, 1, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(Color.RED).withFade(Color.ORANGE).with(FireworkEffect.Type.BALL_LARGE).build());
            meta.setPower(2);
            fw.setFireworkMeta(meta);
            fw.addScoreboardTag(Expeditions.TAG);
        });
        world.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 3f, 0.8f);
        mgr.plugin.bruit().emit(c, 40, by);
        for (Player p : activePlayers(true)) p.showBossBar(ex.bar);
        squadMsg("§6" + by.getName() + " a allumé la fusée de signal à §f" + pt.name() + "§6 ! §7Tenez " + dur
                + " s dans le cercle, le transport arrive. Les zombies aussi.");
    }

    private void extractionTick(Extraction ex, long now) {
        Location c = ex.point.center(world);
        long left = Math.max(0, (ex.endMs - now + 999) / 1000);
        float prog = (float) Math.max(0, Math.min(1, (now - ex.startMs) / (double) (ex.endMs - ex.startMs)));
        int inside = 0;
        for (Player p : activePlayers(true)) if (circleOf(p.getLocation()) != null && circleOf(p.getLocation()) == ex.idx) inside++;
        ex.bar.name(Txt.c("§6Extraction §f" + ex.point.name() + " §8│ §e" + left + " s §8│ §7" + inside + " dans le cercle"));
        ex.bar.progress(prog);
        for (Player p : activePlayers(true)) p.showBossBar(ex.bar);
        int maxWaves = zone.extractionWaves >= 0 ? zone.extractionWaves : cfg("extraction.vagues", 1);
        if (ex.waves < maxWaves && now >= ex.nextWave && now < ex.endMs - 3000) {
            ex.waves++;
            ex.nextWave = now + cfg("extraction.vague", 10) * 1000L;
            int n = zone.extractionZombies > 0 ? zone.extractionZombies : cfg("extraction.zombies", 8);
            spawnAround(c, 10, 18, n, true);
        }
        if (now < ex.endMs) return;
        extractions.remove(ex.idx);
        for (Member m : members.values()) {
            Player p = m.player();
            if (p != null) p.hideBossBar(ex.bar);
        }
        world.playSound(c, transportSound(ex.point.transport()), 3f, 1f);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, c, 30, 1.5, 0.5, 1.5, 0.02);
        List<Player> out = new ArrayList<>();
        for (Player p : activePlayers(true)) {
            Integer ci = circleOf(p.getLocation());
            if (ci != null && ci == ex.idx) out.add(p);
        }
        if (out.isEmpty()) {
            squadMsg("§7Le transport de §f" + ex.point.name() + "§7 est reparti à vide.");
            return;
        }
        for (Player p : out) {
            Member m = members.get(p.getUniqueId());
            if (m != null) mgr.extract(this, m, p, ex.point);
        }
        checkEnd();
    }

    private static Sound transportSound(String t) {
        if (t == null) return Sound.BLOCK_BELL_USE;
        String s = t.toLowerCase();
        if (s.contains("wagon") || s.contains("train") || s.contains("mine")) return Sound.ENTITY_MINECART_RIDING;
        if (s.contains("barque") || s.contains("bateau") || s.contains("boat")) return Sound.ENTITY_BOAT_PADDLE_WATER;
        if (s.contains("camion") || s.contains("truck") || s.contains("voiture")) return Sound.ITEM_GOAT_HORN_SOUND_0;
        if (s.contains("helico") || s.contains("hélico")) return Sound.ENTITY_BREEZE_WIND_BURST;
        return Sound.BLOCK_BELL_USE;
    }

    /** Cercles de particules autour des points d'extraction proches d'un joueur. */
    private void circles() {
        List<Player> ps = activePlayers(true);
        if (ps.isEmpty()) return;
        for (int i = 0; i < zone.extractions.size(); i++) {
            Zone.Point e = zone.extractions.get(i);
            Location c = e.center(world);
            boolean active = extractions.containsKey(i);
            Color col = active ? Color.ORANGE : Color.LIME;
            // Colonne de lumière visible de loin (force = affichée bien au-delà de la distance normale des particules).
            Particle.DustOptions big = new Particle.DustOptions(col, 3.0f);
            for (double h = 1.5; h <= 40; h += 1.5)
                Particle.DUST.builder().location(c.clone().add(0, h, 0)).count(1).data(big).force(true).receivers(ps).spawn();
            Particle.END_ROD.builder().location(c.clone().add(0, 40, 0)).count(3).offset(0.2, 0.2, 0.2).extra(0.02).force(true).receivers(ps).spawn();
            // Cercle au sol, de près seulement.
            boolean near = false;
            for (Player p : ps) if (p.getLocation().distanceSquared(c) < 48 * 48) near = true;
            if (near) {
                Particle.DustOptions dust = new Particle.DustOptions(col, 1.3f);
                int pts = (int) Math.max(16, e.radius() * 8);
                for (int k = 0; k < pts; k++) {
                    double a = Math.PI * 2 * k / pts;
                    world.spawnParticle(Particle.DUST, e.x() + Math.cos(a) * e.radius(), e.y() + 0.2, e.z() + Math.sin(a) * e.radius(), 1, 0, 0, 0, 0, dust);
                }
            }
            extractLabel(i, e, c, active);
        }
    }

    private void extractLabel(int i, Zone.Point e, Location c, boolean active) {
        TextDisplay t = extractLabels.get(i);
        String text = (active ? "§6§lEXTRACTION EN COURS" : "§a§lEXTRACTION") + "\n§f" + e.name()
                + (active ? "" : "\n§7accroupis-toi 3 s dans le cercle");
        if (t != null && t.isValid()) {
            t.text(Txt.c(text));
            return;
        }
        if (!world.isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4)) return;
        extractLabels.put(i, world.spawn(c.clone().add(0, 4.5, 0), TextDisplay.class, d -> {
            d.text(Txt.c(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(4f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                    new org.joml.AxisAngle4f(), new org.joml.Vector3f(2.5f, 2.5f, 2.5f), new org.joml.AxisAngle4f()));
            d.setPersistent(false);
            d.addScoreboardTag(Expeditions.TAG);
        }));
    }

    /** La boussole vanilla de chaque joueur pointe vers l'extraction la plus proche. */
    private void compassTarget(Player p) {
        Zone.Point best = null;
        double bd = Double.MAX_VALUE;
        Location l = p.getLocation();
        for (Zone.Point e : zone.extractions) {
            double dx = e.x() - l.getX(), dz = e.z() - l.getZ(), d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best != null) p.setCompassTarget(best.center(world));
    }

    // ------------------------------------------------------------------ zombies

    private List<Mob> mobsHere() {
        List<Mob> out = new ArrayList<>();
        for (Mob m : mgr.plugin.zombies().alive()) if (m.getWorld().equals(world)) out.add(m);
        return out;
    }

    private double mult(long el) {
        int n = activePlayers(true).size();
        double m = 1 + 0.5 * Math.max(0, n - 1);
        if (night) m *= zone.nightMult;
        if (el >= cfg("duree.horde-permanente", 1200)) m *= 2;
        else if (el >= cfg("duree.maree", 900)) m *= 1.5;
        return m;
    }

    private void despawnFar() {
        List<Player> ps = activePlayers(true);
        double d = cfg("zombies.despawn", 96);
        for (Mob mob : mobsHere()) {
            boolean near = false;
            for (Player p : ps) if (p.getLocation().distanceSquared(mob.getLocation()) < d * d) near = true;
            if (!near) {
                hordeMobs.remove(mob.getUniqueId());
                mob.remove();
            }
        }
    }

    private void ambientSpawns(long el) {
        List<Player> ps = activePlayers(false);
        if (ps.isEmpty()) return;
        int alive = mobsHere().size();
        int cap = (int) Math.round(zone.perPlayer * mult(el));
        int hard = cfg("zombies.max-total", 80);
        Collections.shuffle(ps);
        for (Player p : ps) {
            // Pendant une extraction, plus d'apparitions d'ambiance autour de ceux qui attendent dans le cercle :
            // seule la vague de l'extraction arrive (sinon les zombies se renouvellent sans fin).
            Integer ci = circleOf(p.getLocation());
            if (ci != null && extractions.containsKey(ci)) continue;
            for (int k = 0; k < 2 && alive < Math.min(cap, hard); k++) {
                Location loc = findSpawn(p.getLocation(), zone.spawnMinDist, zone.spawnMaxDist, true);
                if (loc == null) break;
                if (spawnOne(loc, false) != null) alive++;
            }
        }
    }

    private void hordes(long el) {
        long maree = cfg("duree.maree", 900), perm = cfg("duree.horde-permanente", 1200);
        if (!extractions.isEmpty()) return; // pas de horde pendant une extraction (reportée à la fin)
        if (el >= perm) {
            if (el - lastPermanentSec >= cfg("hordes.permanente", 12)) {
                lastPermanentSec = el;
                int n = Math.max(2, (int) Math.round(1.5 * activePlayers(false).size()));
                horde(n, false);
            }
            return;
        }
        if (nextHordeSec < 0 || el < nextHordeSec) return;
        int size = (int) Math.round(zone.hordeSize * mult(el) / (el >= maree ? 1.5 : 1));
        horde(Math.max(3, size), true);
        long interval;
        if (el >= maree) {
            double t = (el - maree) / (double) Math.max(1, perm - maree);
            interval = Math.round(cfg("hordes.maree-debut", 150) + t * (cfg("hordes.maree-fin", 45) - cfg("hordes.maree-debut", 150)));
        } else interval = cfg("hordes.intervalle", 300);
        nextHordeSec = el + Math.max(15, interval);
    }

    /** Une horde à un point « hordes » (30 à 80 blocs d'un joueur), sinon autour d'un joueur. */
    private void horde(int n, boolean announce) {
        List<Player> ps = activePlayers(false);
        if (ps.isEmpty()) return;
        List<Zone.Point> cands = new ArrayList<>();
        for (Zone.Point h : zone.hordes) {
            double best = Double.MAX_VALUE;
            for (Player p : ps) best = Math.min(best, p.getLocation().distanceSquared(h.center(world)));
            if (best >= 30 * 30 && best <= 80 * 80) cands.add(h);
        }
        Location center;
        if (!cands.isEmpty()) {
            Zone.Point h = cands.get(ThreadLocalRandom.current().nextInt(cands.size()));
            center = h.center(world);
            if (h.size() > 0 && announce) n = Math.max(n, h.size());
            world.getChunkAt(center); // un seul chunk, chargé si besoin
        } else {
            Player p = ps.get(ThreadLocalRandom.current().nextInt(ps.size()));
            center = findSpawn(p.getLocation(), 30, 45, true);
            if (center == null) return;
        }
        int made = spawnAround(center, 0, 4, n, false);
        if (made > 0 && announce) {
            for (Player p : ps) p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.3f);
            squadMsg("§c§lUne horde approche ! §7(" + made + " zombies)");
        }
    }

    /** Fait apparaître n zombies de horde autour d'un point (anneau min..max) ; ils foncent sur le joueur le plus proche. */
    private int spawnAround(Location center, int min, int max, int n, boolean forExtraction) {
        int made = 0;
        int hard = cfg("zombies.max-total", 80) + 20;
        int alive = mobsHere().size();
        for (int i = 0; i < n * 3 && made < n && alive < hard; i++) {
            Location loc = findSpawn(center, min, max, false);
            if (loc == null) continue;
            Mob mob = spawnOne(loc, true);
            if (mob == null) continue;
            made++;
            alive++;
            hordeMobs.add(mob.getUniqueId());
            AttributeInstance fr = mob.getAttribute(Attribute.FOLLOW_RANGE);
            if (fr != null) fr.setBaseValue(64);
            Player t = nearest(mob.getLocation(), forExtraction);
            if (t != null) mob.setTarget(t);
        }
        return made;
    }

    private Player nearest(Location loc, boolean any) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : activePlayers(false)) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void hordePath() {
        for (Mob mob : mobsHere()) {
            if (!hordeMobs.contains(mob.getUniqueId()) || mob.getTarget() != null) continue;
            Player t = nearest(mob.getLocation(), true);
            if (t != null) mob.getPathfinder().moveTo(t.getLocation(), 1.1);
        }
    }

    private Mob spawnOne(Location loc, boolean horde) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int total = 0;
        for (int w : zone.types.values()) total += w;
        Zombies.Type type = Zombies.Type.RODEUR;
        int x = r.nextInt(Math.max(1, total));
        for (Map.Entry<Zombies.Type, Integer> e : zone.types.entrySet()) {
            x -= e.getValue();
            if (x < 0) {
                type = e.getKey();
                break;
            }
        }
        if (type == Zombies.Type.NOYE && !loc.getBlock().isLiquid()) type = Zombies.Type.RODEUR;
        boolean elite = r.nextDouble() < zone.eliteChance * (night ? 2 : 1);
        Mob mob = mgr.plugin.zombies().spawn(loc, type, elite);
        if (mob == null) return null;
        mob.addScoreboardTag(Expeditions.TAG);
        if (night) {
            AttributeInstance sp = mob.getAttribute(Attribute.MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(sp.getBaseValue() * 1.15);
        }
        return mob;
    }

    /**
     * Un point d'apparition au sol à min..max blocs de from, dans les bornes, chunk chargé ;
     * hidden = hors de la vue de tous les joueurs et à plus de min blocs de chacun.
     */
    Location findSpawn(Location from, int min, int max, boolean hidden) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Player> ps = activePlayers(true);
        for (int tries = 0; tries < 14; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = min + r.nextDouble() * Math.max(0.5, max - min);
            int x = (int) Math.floor(from.getX() + Math.cos(a) * d), z = (int) Math.floor(from.getZ() + Math.sin(a) * d);
            if (!zone.inBounds(x, z) || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int base = from.getBlockY();
            Location found = null;
            boolean outside = hidden && zone.spawnRule != null && zone.spawnRule.toLowerCase().startsWith("ext");
            if (outside) {
                // Règle de la zone : colonne à ciel ouvert (feuillage permis), sol entre spawnMinY et spawnMaxY.
                int sky = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).getY();
                int y = sky + 1;
                if (y >= zone.spawnMinY && y <= zone.spawnMaxY && standable(world, x, y, z))
                    found = new Location(world, x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
            } else {
                for (int k = 0; k <= 16 && found == null; k++) {
                    int y = base + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
                    if (y <= zone.minY || y >= zone.maxY) continue;
                    if (hidden && (y < zone.spawnMinY || y > zone.spawnMaxY)) continue;
                    if (standable(world, x, y, z)) found = new Location(world, x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
                }
            }
            if (found == null) continue;
            if (hidden) {
                boolean ok = true;
                for (Player p : ps) {
                    double dist = p.getLocation().distance(found);
                    if (dist < min * 0.9) ok = false;
                    else if (dist < 64 && sees(p, found)) ok = false;
                    if (!ok) break;
                }
                if (!ok) continue;
            }
            return found;
        }
        return null;
    }

    static boolean standable(World w, int x, int y, int z) {
        Block below = w.getBlockAt(x, y - 1, z), feet = w.getBlockAt(x, y, z), head = w.getBlockAt(x, y + 1, z);
        return below.getType().isSolid() && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid();
    }

    private boolean sees(Player p, Location target) {
        Location eye = p.getEyeLocation();
        for (double h : new double[]{1.0, 1.8}) {
            Vector to = target.clone().add(0, h, 0).toVector().subtract(eye.toVector());
            double len = to.length();
            if (len < 0.1) return true;
            RayTraceResult hit = world.rayTraceBlocks(eye, to.normalize(), len, FluidCollisionMode.NEVER, true);
            if (hit == null) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Marée et brume

    private String tideLabel(long el) {
        if (el >= cfg("duree.brume", 1500)) return "§5§lBRUME NOIRE";
        if (el >= cfg("duree.horde-permanente", 1200)) return "§cHorde permanente";
        if (el >= cfg("duree.maree", 900)) return "§6La Marée monte";
        return "§aCalme";
    }

    private void updateBar(long el) {
        long brume = cfg("duree.brume", 1500);
        BossBar.Color col = el >= brume ? BossBar.Color.PURPLE : el >= cfg("duree.horde-permanente", 1200) ? BossBar.Color.RED
                : el >= cfg("duree.maree", 900) ? BossBar.Color.YELLOW : BossBar.Color.GREEN;
        bar.color(col);
        bar.progress((float) Math.max(0, Math.min(1, el / (double) brume)));
        bar.name(Txt.c("§f⌚ " + mmss(el) + " §8/ " + mmss(brume) + " §8│ §fMarée : " + tideLabel(el)));
        for (Player p : activePlayers(true)) p.showBossBar(bar);
    }

    private void mist(long el) {
        long brume = cfg("duree.brume", 1500);
        if (el < brume) return;
        if (!mistAnnounced) {
            mistAnnounced = true;
            squadMsg("§5§lLa brume noire arrive. §7Elle tue : rejoins vite une extraction !");
        }
        double dmg = 1 + 0.5 * ((el - brume) / 10.0);
        for (Player p : activePlayers(false)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, false));
            world.spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 12, 2, 1, 2, 0.01);
            world.spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 8, 3, 1.5, 3, 0.01);
            p.damage(dmg);
        }
    }

    static String mmss(long s) {
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ tableau latéral

    private static final String[] ENTRIES = new String[15];

    static {
        String hex = "0123456789abcdef";
        for (int i = 0; i < 15; i++) ENTRIES[i] = "§" + hex.charAt(i) + "§r";
    }

    private void setupBoard(Player p, Member m) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("refuge", Criteria.DUMMY, Txt.c("§2§lDernier Refuge"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank());
        m.board = sb;
        m.obj = o;
        p.setScoreboard(sb);
        updateBoard(p, m, elapsedSec());
    }

    private void updateBoard(Player p, Member m, long el) {
        if (m.obj == null) return;
        List<String> lines = new ArrayList<>();
        lines.add("§7" + zone.name + " · " + (night ? "§9Nuit" : "§eJour"));
        lines.add("§fTemps §e" + mmss(el));
        lines.add("§fMarée " + tideLabel(el));
        lines.add("");
        lines.add("§fEscouade");
        for (Member o : members.values()) {
            Player op = o.player();
            String st = switch (o.state) {
                case ACTIVE -> op == null ? "§8?" : "§c❤ " + (int) Math.ceil(op.getHealth());
                case DOWNED -> "§6À TERRE §e" + Math.max(0, (o.downedUntil - System.currentTimeMillis()) / 1000) + " s";
                case EXTRACTED -> "§aExtrait";
                case DEAD -> "§8Mort";
                case LEFT -> "§8Parti";
            };
            lines.add((o == m ? "§f» " : "§7") + o.name + " " + st);
        }
        lines.add(" ");
        lines.add("§fZombies tués §e" + m.kills);
        String near = nearestExtraction(p);
        if (near != null) lines.add("§fExtraction " + near);
        for (int i = 0; i < ENTRIES.length; i++) {
            if (i < lines.size()) {
                var sc = m.obj.getScore(ENTRIES[i]);
                sc.setScore(ENTRIES.length - i);
                sc.customName(Txt.c(lines.get(i).isEmpty() ? " " : lines.get(i)));
            } else m.board.resetScores(ENTRIES[i]);
        }
    }

    private String nearestExtraction(Player p) {
        Zone.Point best = null;
        double bd = Double.MAX_VALUE;
        Location l = p.getLocation();
        for (Zone.Point e : zone.extractions) {
            double dx = e.x() - l.getX(), dz = e.z() - l.getZ();
            double d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return null;
        double dx = best.x() - l.getX(), dz = best.z() - l.getZ();
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((target - l.getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[(int) Math.floorMod(Math.round(rel / 45.0), 8L)];
        String name = best.name().length() > 14 ? best.name().substring(0, 14) : best.name();
        return "§e" + name + " §f" + (int) Math.sqrt(bd) + " m " + arrow;
    }

    // ------------------------------------------------------------------ sacs

    /** Pose le sac (tonneau) au plus près de b et enregistre le bloc d'origine pour le remettre. */
    Sac placeSac(UUID owner, String ownerName, Block b, ItemStack[] items, Sacs.Saved previous) {
        boolean any = false;
        for (ItemStack i : items) if (i != null && !i.getType().isAir()) any = true;
        if (!any) return null;
        Block spot = sacSpot(b);
        noteRestore(spot);
        spot.setType(Material.BARREL, false);
        BlockData data = spot.getBlockData();
        if (data instanceof Directional d) {
            d.setFacing(BlockFace.UP);
            spot.setBlockData(d, false);
        }
        Sac sac = new Sac(owner, ownerName, spot, items, previous);
        Location top = spot.getLocation().add(0.5, 1.35, 0.5);
        sac.label = world.spawn(top, TextDisplay.class, t -> {
            t.text(Txt.c("§cSac de " + ownerName + "\n§7clic droit pour fouiller"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            t.addScoreboardTag(Expeditions.TAG);
        });
        sacs.put(Zone.blockKey(spot.getX(), spot.getY(), spot.getZ()), sac);
        return sac;
    }

    private Block sacSpot(Block b) {
        if (b.getY() < world.getMinHeight() + 1) b = world.getBlockAt(b.getX(), world.getMinHeight() + 1, b.getZ());
        for (int dy = 0; dy <= 3; dy++)
            for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Block c = b.getRelative(o[0], dy, o[1]);
                if (c.getType().isAir() && c.getRelative(BlockFace.DOWN).getType().isSolid()
                        && !sacs.containsKey(Zone.blockKey(c.getX(), c.getY(), c.getZ()))) return c;
            }
        for (int dy = 0; dy <= 3; dy++) {
            Block c = b.getRelative(0, dy, 0);
            if (c.getType().isAir() || c.isPassable()) return c;
        }
        return b;
    }

    /** Monde d'origine (partie 1) : on note l'état d'origine d'un bloc qu'on va changer, pour le remettre. */
    void noteRestore(Block b) {
        if (index != 1) return;
        long k = Zone.blockKey(b.getX(), b.getY(), b.getZ());
        if (!restoreNoted.add(k)) return;
        mgr.sacs.noteRestore(zone.id, b.getX(), b.getY(), b.getZ(), b.getBlockData().getAsString());
    }

    // ------------------------------------------------------------------ fin

    void checkEnd() {
        if (state != State.RUNNING) return;
        for (Member m : members.values()) if (m.active()) return;
        end("tout le monde est rentré", true);
    }

    void end(String why, boolean rewards) {
        if (state == State.ENDED) return;
        boolean wasRunning = state == State.RUNNING;
        state = State.ENDED;
        if (world != null) {
            for (Member m : new ArrayList<>(members.values())) {
                Player p = m.player();
                if (m.active() && p != null) mgr.forceOut(this, m, p, why);
            }
            for (Mob mob : mobsHere()) mob.remove();
            for (Entity e : world.getEntities())
                if (e instanceof Item || e instanceof ExperienceOrb || e.getScoreboardTags().contains(Expeditions.TAG)) e.remove();
            // Sacs : les sacs reposés sont perdus, ceux de cette expédition restent pour la suivante.
            for (Sac s : sacs.values()) {
                if (s.previous != null) mgr.sacs.removeIfSame(s.previous);
                if (s.current != null) {
                    List<ItemStack> left = new ArrayList<>();
                    for (ItemStack i : s.inv.getContents()) if (i != null && !i.getType().isAir()) left.add(i);
                    if (left.isEmpty()) mgr.sacs.removeIfSame(s.current);
                    else {
                        s.current.items = left.toArray(new ItemStack[0]);
                        mgr.sacs.saveSacs();
                    }
                }
                for (var v : new ArrayList<>(s.inv.getViewers())) v.closeInventory();
            }
            if (index == 1) mgr.restoreOrigin(zone, world);
        }
        for (Extraction ex : extractions.values())
            for (Member m : members.values()) {
                Player p = m.player();
                if (p != null) p.hideBossBar(ex.bar);
            }
        extractions.clear();
        if (wasRunning && rewards && !adminStop)
            for (Member m : members.values()) mgr.eemi("reward " + m.name + " refuge play");
        mgr.plugin.getLogger().info("Expedition " + zone.id + " #" + index + " terminee (" + why + ").");
        for (Member m : members.values()) mgr.unregister(m.id);
        mgr.release(this);
    }

    // ------------------------------------------------------------------ comptes d'objets (butin ramené)

    static Map<String, Integer> counts(Player p) {
        Map<String, Integer> out = new HashMap<>();
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {
            String k = Loot.key(it);
            if (k != null) out.merge(k, it.getAmount(), Integer::sum);
        }
        return out;
    }

    /** Objets en plus par rapport au départ (pour le bilan). */
    List<ItemStack> gained(Player p, Member m) {
        Map<String, Integer> now = counts(p);
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : now.entrySet()) {
            int diff = e.getValue() - m.startCounts.getOrDefault(e.getKey(), 0);
            if (diff <= 0) continue;
            ItemStack sample = null;
            for (ItemStack it : p.getInventory().getContents())
                if (e.getKey().equals(Loot.key(it))) {
                    sample = it;
                    break;
                }
            if (sample == null) continue;
            int left = diff;
            int stack = Math.max(1, sample.getMaxStackSize());
            while (left > 0) {
                ItemStack c = sample.clone();
                c.setAmount(Math.min(stack, left));
                out.add(c);
                left -= c.getAmount();
            }
        }
        return out;
    }

    Bilan bilan(Member m, boolean extracted, List<ItemStack> loot, List<String> notes) {
        int dur = (int) elapsedSec();
        int xp = m.kills * cfg("xp.kill", 2) + m.revives * cfg("xp.releve", 5) + (dur / 60) * cfg("xp.minute", 1)
                + (extracted ? cfg("xp.extraction", 25) : cfg("xp.mort", 5));
        int value = 0;
        for (ItemStack it : loot) value += mgr.loot.value(it);
        List<String> n = new ArrayList<>(notes);
        if (extracted && value > 0) n.add("Valeur estimée du butin : " + value + " ⚙ (à vendre au Refuge).");
        if (m.downs > 0) n.add("Mis à terre " + m.downs + " fois.");
        Profile pr = mgr.profile(m.id);
        if (pr != null) {
            pr.addXp(xp);
            pr.save();
        }
        return new Bilan(zone.name, extracted, dur, m.kills, m.revives, 0, xp, loot, n);
    }

    String label() {
        return zone.id + " #" + index;
    }

    static boolean isSecondChance(ItemStack it) {
        return "seconde_chance".equals(Catalogue.id(it));
    }
}
