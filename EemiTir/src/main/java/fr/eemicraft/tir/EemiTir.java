package fr.eemicraft.tir;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand de tir du lobby : on entre dans la zone de tir, on reçoit un arc, des cibles apparaissent puis disparaissent
 * dans la zone des cibles, chaque cible touchée rapporte des points pendant une manche chronométrée.
 * Les joueurs ne peuvent pas entrer dans la zone interdite (autour des cibles).
 */
public final class EemiTir extends JavaPlugin implements Listener {

    static final String PREFIX = "§8[§6Stand de tir§8] §7";
    private static final int BOW_SLOT = 1, ARROW_SLOT = 9;

    // ------------------------------------------------------------------ zones
    record Zone(int x1, int y1, int z1, int x2, int y2, int z2) {
        static Zone of(int ax, int ay, int az, int bx, int by, int bz) {
            return new Zone(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
        }

        boolean valid() {
            return !(x1 == 0 && y1 == 0 && z1 == 0 && x2 == 0 && y2 == 0 && z2 == 0);
        }

        boolean contains(double x, double y, double z) {
            return x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
        }

        boolean contains(Location l) {
            return contains(l.getX(), l.getY(), l.getZ());
        }

        Zone grow(int m) {
            return new Zone(x1 - m, y1 - m, z1 - m, x2 + m, y2 + m, z2 + m);
        }
    }

    private World world;
    private Zone tir, cibles, interdite;

    // ------------------------------------------------------------------ cibles
    private record Kind(String id, Material block, int points, int chance, int lifeMin, int lifeMax) {
    }

    private static final class Target {
        Block block;
        Kind kind;
        int life;
    }

    private final List<Kind> kinds = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private int spawnTimer;
    private boolean targetsDirty;

    // ------------------------------------------------------------------ joueurs
    private static final class Shooter {
        int phase; // 0 décompte, 1 en jeu, 2 pause
        int ticks;
        int score, shots, hits;
        BossBar bar;
        ItemStack oldBowSlot, oldArrowSlot;
        int oldHeld;
    }

    private final Map<UUID, Shooter> shooters = new HashMap<>();
    private final Map<UUID, Integer> showZones = new HashMap<>();

    // ------------------------------------------------------------------ records
    private record Rec(String name, int best) {
    }

    private final Map<UUID, Rec> records = new HashMap<>();
    private volatile List<Map.Entry<UUID, Rec>> top = new ArrayList<>();

    private NamespacedKey itemKey, arrowKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "objet");
        arrowKey = new NamespacedKey(this, "fleche");
        loadSettings();
        loadRecords();
        cleanLeftoverBlocks();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveTargetsIfDirty, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, this::drawZones, 20L, 10L);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new TirExpansion(this).register();
                getLogger().info("Placeholders %eemitir_...% enregistres.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI present mais expansion impossible : " + t);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) stripItems(p);
        getLogger().info("Zones : tir " + (tir.valid() ? "ok" : "A REGLER") + ", cibles " + (cibles.valid() ? "ok" : "A REGLER")
                + " ; " + kinds.size() + " types de cibles, " + records.size() + " records.");
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(shooters.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) leave(p, false);
        }
        shooters.clear();
        clearTargets();
        saveTargets();
        saveRecords();
    }

    private void loadSettings() {
        reloadConfig();
        world = Bukkit.getWorld(getConfig().getString("world", "lobby"));
        tir = readZone("zones.tir");
        cibles = readZone("zones.cibles");
        Zone forb = readZone("zones.interdite");
        interdite = forb.valid() ? forb : (cibles.valid() ? cibles.grow(getConfig().getInt("marge-interdite", 1)) : forb);
        kinds.clear();
        ConfigurationSection s = getConfig().getConfigurationSection("cibles.types");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection k = s.getConfigurationSection(id);
                if (k == null) continue;
                Material m = Material.matchMaterial(k.getString("bloc", "TARGET"));
                if (m == null || !m.isBlock()) {
                    getLogger().warning("Bloc inconnu pour la cible " + id + " : " + k.getString("bloc"));
                    continue;
                }
                kinds.add(new Kind(id, m, k.getInt("points", 1), Math.max(0, k.getInt("chance", 10)),
                        Math.max(5, k.getInt("vie-min", 50)), Math.max(5, k.getInt("vie-max", 90))));
            }
        }
        if (kinds.isEmpty()) kinds.add(new Kind("normale", Material.TARGET, 1, 1, 50, 90));
    }

    private Zone readZone(String path) {
        int[] a = parse(getConfig().getString(path + ".min", "0 0 0"));
        int[] b = parse(getConfig().getString(path + ".max", "0 0 0"));
        return Zone.of(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    private static int[] parse(String s) {
        int[] r = new int[3];
        String[] p = s == null ? new String[0] : s.trim().split("[\\s,]+");
        for (int i = 0; i < 3 && i < p.length; i++) {
            try {
                r[i] = (int) Math.floor(Double.parseDouble(p[i]));
            } catch (NumberFormatException ignored) {
            }
        }
        return r;
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    // ================================================================== boucle

    private void tick() {
        if (world == null) return;
        // entrées / sorties de la zone de tir
        for (Player p : world.getPlayers()) {
            boolean in = tir.valid() && tir.contains(p.getLocation()) && p.getGameMode() != GameMode.SPECTATOR;
            boolean has = shooters.containsKey(p.getUniqueId());
            if (in && !has) enter(p);
            else if (!in && has) leave(p, true);
        }
        boolean anyPlaying = false;
        for (Iterator<Map.Entry<UUID, Shooter>> it = shooters.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Shooter> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            Shooter s = e.getValue();
            if (s.phase == 1) anyPlaying = true;
            tickShooter(p, s);
        }
        // cibles
        for (Iterator<Target> it = targets.iterator(); it.hasNext(); ) {
            Target t = it.next();
            if (--t.life <= 0 || !anyPlaying) {
                removeBlock(t);
                if (anyPlaying) world.spawnParticle(Particle.SMOKE, t.block.getLocation().add(0.5, 0.5, 0.5), 6, 0.25, 0.25, 0.25, 0.01);
                it.remove();
                targetsDirty = true;
            }
        }
        if (anyPlaying && cibles.valid() && ++spawnTimer >= Math.max(1, getConfig().getInt("cibles.intervalle", 15))) {
            spawnTimer = 0;
            if (targets.size() < Math.max(1, getConfig().getInt("cibles.max", 4))) spawnTarget();
        }
    }

    private void tickShooter(Player p, Shooter s) {
        s.ticks--;
        int duration = Math.max(10, getConfig().getInt("manche.duree", 45)) * 20;
        switch (s.phase) {
            case 0 -> {
                if (s.ticks % 20 == 0 && s.ticks > 0) {
                    p.showTitle(Title.title(Component.text("§6" + (s.ticks / 20)), Component.text("§7Prépare ton arc"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
                if (s.ticks <= 0) {
                    s.phase = 1;
                    s.ticks = duration;
                    s.score = s.shots = s.hits = 0;
                    p.showTitle(Title.title(Component.text("§aTirez !"), Component.text("§7Touche un maximum de cibles"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(300))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                }
            }
            case 1 -> {
                if (s.ticks <= 0) {
                    endRound(p, s);
                } else if (s.ticks <= 100 && s.ticks % 20 == 0) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 1.5f);
                }
            }
            default -> {
                if (s.ticks <= 0) {
                    s.phase = 0;
                    s.ticks = Math.max(1, getConfig().getInt("manche.decompte", 3)) * 20;
                }
            }
        }
        updateBar(p, s, duration);
    }

    private void updateBar(Player p, Shooter s, int duration) {
        if (s.bar == null) {
            s.bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
            s.bar.addPlayer(p);
        }
        switch (s.phase) {
            case 0 -> {
                s.bar.setTitle("§6Stand de tir §8» §7La manche commence...");
                s.bar.setColor(BarColor.YELLOW);
                s.bar.setProgress(1);
            }
            case 1 -> {
                int sec = (s.ticks + 19) / 20;
                s.bar.setTitle("§6Stand de tir §8» §e" + s.score + " point" + (Math.abs(s.score) > 1 ? "s" : "")
                        + " §8| §f" + sec / 60 + ":" + String.format("%02d", sec % 60));
                s.bar.setColor(sec <= 5 ? BarColor.RED : BarColor.GREEN);
                s.bar.setProgress(Math.max(0, Math.min(1, s.ticks / (double) duration)));
            }
            default -> {
                s.bar.setTitle("§6Stand de tir §8» §7Nouvelle manche dans " + ((s.ticks + 19) / 20) + " s (sors de la zone pour arrêter)");
                s.bar.setColor(BarColor.WHITE);
                s.bar.setProgress(1);
            }
        }
    }

    private void endRound(Player p, Shooter s) {
        s.phase = 2;
        s.ticks = Math.max(1, getConfig().getInt("manche.pause", 5)) * 20;
        int acc = s.shots == 0 ? 0 : Math.round(100f * s.hits / s.shots);
        Rec old = records.get(p.getUniqueId());
        boolean record = old == null || s.score > old.best();
        if (record && s.score > 0) {
            records.put(p.getUniqueId(), new Rec(p.getName(), s.score));
            recomputeTop();
            saveRecords();
        }
        p.showTitle(Title.title(Component.text("§e" + s.score + " point" + (Math.abs(s.score) > 1 ? "s" : "")),
                Component.text(record && s.score > 0 ? "§6Nouveau record !" : "§7Précision " + acc + " %"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        p.sendMessage(PREFIX + "Fin de la manche : §e" + s.score + " pts §7(" + s.hits + " cible" + (s.hits > 1 ? "s" : "")
                + " sur " + s.shots + " flèche" + (s.shots > 1 ? "s" : "") + ", précision " + acc + " %)"
                + (record && s.score > 0 ? " §6Nouveau record !" : (old != null ? " §8Record : " + old.best() : "")));
        p.playSound(p.getLocation(), record && s.score > 0 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        if (record && s.score > 0) {
            int rank = rankOf(p.getUniqueId());
            if (rank == 1) {
                for (Player o : world.getPlayers())
                    if (o != p) o.sendMessage(PREFIX + "§e" + p.getName() + " §7prend la tête du stand de tir avec §e" + s.score + " pts §7!");
            }
        }
    }

    // ================================================================== entrée / sortie

    private void enter(Player p) {
        Shooter s = new Shooter();
        s.phase = 0;
        s.ticks = Math.max(1, getConfig().getInt("manche.decompte", 3)) * 20;
        PlayerInventory inv = p.getInventory();
        s.oldBowSlot = inv.getItem(BOW_SLOT);
        s.oldArrowSlot = inv.getItem(ARROW_SLOT);
        s.oldHeld = inv.getHeldItemSlot();
        inv.setItem(BOW_SLOT, bow());
        inv.setItem(ARROW_SLOT, arrow());
        inv.setHeldItemSlot(BOW_SLOT);
        shooters.put(p.getUniqueId(), s);
        p.sendMessage(PREFIX + "Bienvenue au stand ! Touche les cibles §f(+1)§7, les dorées §6(+3)§7, évite la TNT §c(-2)§7. Sors de la zone pour arrêter.");
    }

    private void leave(Player p, boolean message) {
        Shooter s = shooters.remove(p.getUniqueId());
        if (s == null) return;
        if (s.bar != null) s.bar.removeAll();
        PlayerInventory inv = p.getInventory();
        if (ours(inv.getItem(BOW_SLOT))) inv.setItem(BOW_SLOT, s.oldBowSlot);
        if (ours(inv.getItem(ARROW_SLOT))) inv.setItem(ARROW_SLOT, s.oldArrowSlot);
        stripItems(p);
        if (inv.getHeldItemSlot() == BOW_SLOT) inv.setHeldItemSlot(s.oldHeld);
        if (message && s.phase == 1)
            p.sendMessage(PREFIX + "Manche abandonnée (" + s.score + " pts).");
    }

    /** Retire les objets du stand restés n'importe où dans l'inventaire. */
    private void stripItems(Player p) {
        if (shooters.containsKey(p.getUniqueId())) return;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (ours(inv.getItem(i))) inv.setItem(i, null);
        if (ours(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    private ItemStack bow() {
        ItemStack it = new ItemStack(Material.BOW);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("§6Arc du stand de tir"));
        m.lore(List.of(Component.text("§7Touche les cibles qui apparaissent")));
        m.addEnchant(Enchantment.INFINITY, 1, true);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack arrow() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("§7Flèche du stand"));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean ours(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    // ================================================================== cibles

    private void spawnTarget() {
        Kind kind = pickKind();
        double gap = getConfig().getDouble("cibles.ecart", 2.5);
        for (int i = 0; i < 15; i++) {
            int x = rnd().nextInt(cibles.x1(), cibles.x2() + 1);
            int y = rnd().nextInt(cibles.y1(), cibles.y2() + 1);
            int z = rnd().nextInt(cibles.z1(), cibles.z2() + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = world.getBlockAt(x, y, z);
            if (!b.getType().isAir()) continue;
            boolean ok = true;
            for (Target t : targets) {
                if (t.block.getLocation().distanceSquared(b.getLocation()) < gap * gap) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            b.setType(kind.block(), false);
            Target t = new Target();
            t.block = b;
            t.kind = kind;
            t.life = rnd().nextInt(kind.lifeMin(), Math.max(kind.lifeMin(), kind.lifeMax()) + 1);
            targets.add(t);
            targetsDirty = true;
            Location c = b.getLocation().add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.CLOUD, c, 5, 0.3, 0.3, 0.3, 0.01);
            world.playSound(c, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.8f);
            return;
        }
    }

    private Kind pickKind() {
        int total = 0;
        for (Kind k : kinds) total += k.chance();
        if (total <= 0) return kinds.get(0);
        int r = rnd().nextInt(total);
        for (Kind k : kinds) {
            r -= k.chance();
            if (r < 0) return k;
        }
        return kinds.get(0);
    }

    private void removeBlock(Target t) {
        if (t.block.getType() == t.kind.block()) t.block.setType(Material.AIR, false);
    }

    private void clearTargets() {
        for (Target t : targets) removeBlock(t);
        targets.clear();
        targetsDirty = true;
    }

    private Target targetAt(Block b) {
        if (b == null) return null;
        for (Target t : targets) if (t.block.equals(b)) return t;
        return null;
    }

    // ================================================================== tirs

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !ours(e.getBow())) return;
        Shooter s = shooters.get(p.getUniqueId());
        if (s == null || s.phase != 1) {
            e.setCancelled(true);
            p.sendActionBar(Component.text(s == null ? "§cTu dois être dans la zone de tir." : "§eAttends le début de la manche."));
            return;
        }
        e.setConsumeItem(false);
        if (e.getProjectile() instanceof AbstractArrow a) {
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
        }
        s.shots++;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!pr.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE)) return;
        Bukkit.getScheduler().runTaskLater(this, pr::remove, 1L);
        if (e.getHitEntity() != null) {
            e.setCancelled(true);
            pr.remove();
            return;
        }
        Target t = targetAt(e.getHitBlock());
        if (t == null || !(pr.getShooter() instanceof Player p)) return;
        Shooter s = shooters.get(p.getUniqueId());
        if (s == null || s.phase != 1) return;
        removeBlock(t);
        targets.remove(t);
        targetsDirty = true;
        s.score += t.kind.points();
        if (t.kind.points() > 0) s.hits++;
        Location c = t.block.getLocation().add(0.5, 0.5, 0.5);
        if (t.kind.points() > 0) {
            world.spawnParticle(t.kind.points() >= 3 ? Particle.TOTEM_OF_UNDYING : Particle.CRIT, c, 15, 0.3, 0.3, 0.3, 0.2);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, t.kind.points() >= 3 ? 2f : 1.4f);
            p.sendActionBar(Component.text((t.kind.points() >= 3 ? "§6" : "§a") + "+" + t.kind.points() + " §7(" + s.score + " pts)"));
        } else {
            world.spawnParticle(Particle.LARGE_SMOKE, c, 12, 0.3, 0.3, 0.3, 0.03);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            p.sendActionBar(Component.text("§c" + t.kind.points() + " §7(" + s.score + " pts)"));
        }
    }

    /** Les flèches du stand ne blessent personne (joueurs, animaux, PNJ...). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE))
            e.setCancelled(true);
    }

    // ================================================================== zone interdite

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (world == null || !interdite.valid()) return;
        Location to = e.getTo();
        if (!to.getWorld().equals(world)) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!interdite.contains(to)) return;
        Location from = e.getFrom();
        double cx = (interdite.x1() + interdite.x2() + 1) / 2.0, cz = (interdite.z1() + interdite.z2() + 1) / 2.0;
        if (!interdite.contains(from)) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
        } else {
            // déjà dedans (téléporté, poussé...) : on le remet dans la zone de tir
            Location out = tir.valid()
                    ? new Location(world, (tir.x1() + tir.x2() + 1) / 2.0, tir.y1(), (tir.z1() + tir.z2() + 1) / 2.0, to.getYaw(), to.getPitch())
                    : world.getSpawnLocation();
            e.setTo(out);
        }
        Vector push = new Vector(from.getX() - cx, 0, from.getZ() - cz);
        if (push.lengthSquared() > 0.001) p.setVelocity(push.normalize().multiply(0.4).setY(0.15));
        p.sendActionBar(Component.text("§cLa zone des cibles est interdite."));
    }

    // ================================================================== objets protégés

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (ours(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (ours(e.getMainHandItem()) || ours(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (ours(e.getCurrentItem()) || ours(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getHotbarButton() >= 0 && e.getWhoClicked() instanceof Player p && ours(p.getInventory().getItem(e.getHotbarButton())))
            e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer(), false);
    }

    /** Téléporté hors du monde depuis le stand : on rend l'inventaire AVANT que Multiverse-Inventories le sauvegarde. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (shooters.containsKey(e.getPlayer().getUniqueId()) && e.getTo() != null && !e.getTo().getWorld().equals(e.getFrom().getWorld()))
            leave(e.getPlayer(), false);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        // l'inventaire a déjà été changé par Multiverse-Inventories : on ne restaure rien, on retire juste nos objets
        Shooter s = shooters.remove(e.getPlayer().getUniqueId());
        if (s != null && s.bar != null) s.bar.removeAll();
        stripItems(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> stripItems(e.getPlayer()), 10L);
    }

    // ================================================================== fichiers

    private File targetsFile() {
        return new File(getDataFolder(), "cibles-posees.yml");
    }

    private void saveTargetsIfDirty() {
        if (targetsDirty) saveTargets();
    }

    private void saveTargets() {
        targetsDirty = false;
        YamlConfiguration y = new YamlConfiguration();
        List<String> l = new ArrayList<>();
        for (Target t : targets)
            l.add(t.block.getWorld().getName() + " " + t.block.getX() + " " + t.block.getY() + " " + t.block.getZ() + " " + t.kind.block().name());
        y.set("cibles", l);
        try {
            y.save(targetsFile());
        } catch (Exception ex) {
            getLogger().warning("Impossible d'ecrire cibles-posees.yml : " + ex.getMessage());
        }
    }

    /** Cibles restées après un arrêt brutal : remises en air si elles sont toujours là. */
    private void cleanLeftoverBlocks() {
        File f = targetsFile();
        if (!f.exists()) return;
        int n = 0;
        for (String s : YamlConfiguration.loadConfiguration(f).getStringList("cibles")) {
            String[] p = s.split(" ");
            if (p.length < 5) continue;
            World w = Bukkit.getWorld(p[0]);
            Material m = Material.matchMaterial(p[4]);
            if (w == null || m == null) continue;
            Block b = w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            if (b.getType() == m) {
                b.setType(Material.AIR, false);
                n++;
            }
        }
        if (n > 0) getLogger().info(n + " cible(s) restee(s) d'avant retiree(s).");
        f.delete();
    }

    private File recordsFile() {
        return new File(getDataFolder(), "records.yml");
    }

    private void loadRecords() {
        records.clear();
        File f = recordsFile();
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (String k : y.getKeys(false)) {
                try {
                    records.put(UUID.fromString(k), new Rec(y.getString(k + ".nom", "?"), y.getInt(k + ".score")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        recomputeTop();
    }

    private void saveRecords() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Rec> e : records.entrySet()) {
            y.set(e.getKey() + ".nom", e.getValue().name());
            y.set(e.getKey() + ".score", e.getValue().best());
        }
        try {
            getDataFolder().mkdirs();
            y.save(recordsFile());
        } catch (Exception ex) {
            getLogger().warning("Impossible d'ecrire records.yml : " + ex.getMessage());
        }
    }

    private void recomputeTop() {
        List<Map.Entry<UUID, Rec>> l = new ArrayList<>(records.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue().best(), a.getValue().best()));
        top = l;
    }

    private int rankOf(UUID id) {
        List<Map.Entry<UUID, Rec>> l = top;
        for (int i = 0; i < l.size(); i++) if (l.get(i).getKey().equals(id)) return i + 1;
        return -1;
    }

    // ================================================================== placeholders

    String placeholder(org.bukkit.OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("top_")) {
            int n;
            try {
                n = Integer.parseInt(p.substring(4));
            } catch (NumberFormatException ex) {
                return "";
            }
            List<Map.Entry<UUID, Rec>> l = top;
            String medal = n == 1 ? "§6" : (n == 2 ? "§f" : (n == 3 ? "§c" : "§7"));
            if (n < 1 || n > l.size()) return "§8#" + n + " -";
            Rec r = l.get(n - 1).getValue();
            return medal + "#" + n + " §f" + r.name() + " §8- §e" + r.best() + " pts";
        }
        if (p.equals("best")) {
            if (player == null) return "0";
            Rec r = records.get(player.getUniqueId());
            return r == null ? "0" : String.valueOf(r.best());
        }
        if (p.equals("rank")) {
            if (player == null) return "-";
            int r = rankOf(player.getUniqueId());
            return r < 0 ? "-" : String.valueOf(r);
        }
        return null;
    }

    // ================================================================== affichage des zones

    private void drawZones() {
        if (showZones.isEmpty() || world == null) return;
        for (Iterator<Map.Entry<UUID, Integer>> it = showZones.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || e.getValue() <= 0) {
                it.remove();
                continue;
            }
            e.setValue(e.getValue() - 1);
            if (!p.getWorld().equals(world)) continue;
            edges(p, tir, Color.LIME);
            edges(p, cibles, Color.ORANGE);
            if (interdite.valid() && !interdite.equals(cibles)) edges(p, interdite, Color.RED);
        }
    }

    private void edges(Player p, Zone z, Color c) {
        if (!z.valid()) return;
        Particle.DustOptions d = new Particle.DustOptions(c, 1.2f);
        double x1 = z.x1(), y1 = z.y1(), z1 = z.z1(), x2 = z.x2() + 1, y2 = z.y2() + 1, z2 = z.z2() + 1;
        double[][] corners = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}, {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
        int[][] lines = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] l : lines) {
            double[] a = corners[l[0]], b = corners[l[1]];
            double len = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
            int steps = Math.max(1, (int) (len * 2));
            if (steps > 400) steps = 400;
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                p.spawnParticle(Particle.DUST, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, 1, 0, 0, 0, 0, d);
            }
        }
    }

    // ================================================================== commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "top" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("top")) {
            List<Map.Entry<UUID, Rec>> l = top;
            int max = Math.max(1, getConfig().getInt("top", 10));
            sender.sendMessage(PREFIX + "§eMeilleurs scores");
            if (l.isEmpty()) sender.sendMessage("§7 Personne pour l'instant.");
            for (int i = 0; i < Math.min(max, l.size()); i++) sender.sendMessage(" " + placeholder(null, "top_" + (i + 1)));
            if (sender instanceof Player p && records.containsKey(p.getUniqueId()))
                sender.sendMessage("§7 Ton record : §e" + records.get(p.getUniqueId()).best() + " pts §8(#" + rankOf(p.getUniqueId()) + ")");
            return true;
        }
        if (!sender.hasPermission("eemitir.admin")) {
            sender.sendMessage(PREFIX + "§cCommande réservée aux admins. §7/tir top");
            return true;
        }
        switch (sub) {
            case "zone" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(PREFIX + "En jeu seulement.");
                    return true;
                }
                if (args.length < 3 || !List.of("tir", "cibles", "interdite").contains(args[1].toLowerCase(Locale.ROOT))
                        || !List.of("pos1", "pos2", "effacer").contains(args[2].toLowerCase(Locale.ROOT))) {
                    sender.sendMessage(PREFIX + "/tir zone <tir|cibles|interdite> <pos1|pos2|effacer>");
                    return true;
                }
                String z = args[1].toLowerCase(Locale.ROOT), what = args[2].toLowerCase(Locale.ROOT);
                if (!p.getWorld().getName().equals(getConfig().getString("world", "lobby"))) {
                    sender.sendMessage(PREFIX + "§cTu dois être dans le monde " + getConfig().getString("world", "lobby") + ".");
                    return true;
                }
                if (what.equals("effacer")) {
                    getConfig().set("zones." + z + ".min", "0 0 0");
                    getConfig().set("zones." + z + ".max", "0 0 0");
                } else {
                    Location l = p.getLocation();
                    getConfig().set("zones." + z + "." + (what.equals("pos1") ? "min" : "max"), l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                }
                saveConfig();
                clearTargets();
                loadSettings();
                showZones.put(p.getUniqueId(), 30);
                sender.sendMessage(PREFIX + "Zone §e" + z + " §7: " + describe(readZone("zones." + z))
                        + (z.equals("interdite") && !readZone("zones.interdite").valid() ? " §8(= cibles + marge)" : ""));
            }
            case "voir" -> {
                if (sender instanceof Player p) {
                    showZones.put(p.getUniqueId(), 40);
                    sender.sendMessage(PREFIX + "Zones affichées 20 s : §avert §7= tir, §6orange §7= cibles, §crouge §7= interdite.");
                }
            }
            case "info" -> sender.sendMessage(PREFIX + "Tir : " + describe(tir) + "\n" + PREFIX + "Cibles : " + describe(cibles)
                    + "\n" + PREFIX + "Interdite : " + describe(interdite) + "\n" + PREFIX + shooters.size() + " joueur(s) au stand, "
                    + targets.size() + " cible(s), " + records.size() + " record(s).");
            case "reload" -> {
                clearTargets();
                loadSettings();
                sender.sendMessage(PREFIX + "Rechargé (" + kinds.size() + " types de cibles).");
            }
            case "reset" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirmer")) {
                    sender.sendMessage(PREFIX + "Efface tous les records : §e/tir reset confirmer");
                    return true;
                }
                records.clear();
                recomputeTop();
                saveRecords();
                sender.sendMessage(PREFIX + "Records effacés.");
            }
            default -> sender.sendMessage(PREFIX + "/tir top | zone <tir|cibles|interdite> <pos1|pos2|effacer> | voir | info | reload | reset");
        }
        return true;
    }

    private static String describe(Zone z) {
        if (!z.valid()) return "§cpas réglée";
        return "§f" + z.x1() + " " + z.y1() + " " + z.z1() + " §7→ §f" + z.x2() + " " + z.y2() + " " + z.z2()
                + " §8(" + (z.x2() - z.x1() + 1) + "×" + (z.y2() - z.y1() + 1) + "×" + (z.z2() - z.z1() + 1) + ")";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(sender.hasPermission("eemitir.admin")
                ? List.of("top", "zone", "voir", "info", "reload", "reset") : List.of("top"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("zone")) return filter(List.of("tir", "cibles", "interdite"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("zone")) return filter(List.of("pos1", "pos2", "effacer"), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> l, String start) {
        List<String> out = new ArrayList<>();
        for (String s : l) if (s.startsWith(start.toLowerCase(Locale.ROOT))) out.add(s);
        return out;
    }
}
