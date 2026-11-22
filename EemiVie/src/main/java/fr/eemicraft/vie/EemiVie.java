package fr.eemicraft.vie;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.TropicalFish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vie du lobby : passants (mannequins qui se promènent, s'arrêtent, saluent), animaux décoratifs (chats sur les
 * toits, perroquets dans les arbres, poissons dans l'eau) et sièges sur les escaliers et dalles.
 * Toutes les entités sont non sauvegardées, recréées si besoin, marquées (PDC eemivie:role).
 */
public final class EemiVie extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§aVie§8] §7";

    private NamespacedKey roleKey;
    private World world;
    private boolean paused;
    private final List<double[]> avoid = new ArrayList<>();
    private double avoidR = 1.6;

    // ------------------------------------------------------------------ passants
    private static final class Passant {
        Mannequin m;
        Location anchor;
        double x, y, z;
        float yaw;
        double tx, tz;
        boolean hasTarget;
        int pause;
        long nextWave;
        String skin;
    }

    private final List<Passant> passants = new ArrayList<>();
    private final Map<String, ResolvableProfile> profiles = new HashMap<>();
    private double speed, radius, activation;

    // ------------------------------------------------------------------ animaux
    private static final class Spot {
        String type;
        double x, y, z;
        Entity e;
    }

    private final List<Spot> spots = new ArrayList<>();
    private boolean scanning;

    // ------------------------------------------------------------------ sièges
    private final Map<UUID, ItemDisplay> seats = new HashMap<>();
    private final Map<UUID, Block> seatBlock = new HashMap<>();
    private final Set<UUID> leavingByTeleport = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        roleKey = new NamespacedKey(this, "role");
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, this::start);
    }

    private void start() {
        loadSettings();
        if (world == null) {
            getLogger().warning("Monde " + getConfig().getString("world") + " introuvable : rien n'est lance.");
            return;
        }
        removeOrphans();
        if (getConfig().getBoolean("animaux.enabled", true)) loadOrScanSpots();
        Bukkit.getScheduler().runTaskTimer(this, this::tickPassants, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::maintain, 40L, 40L);
        getLogger().info(passants.size() + " passants prevus, assise " + (getConfig().getBoolean("assise.enabled", true) ? "activee" : "coupee") + ".");
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(seats.keySet())) unseat(id, false);
        for (Passant p : passants) if (p.m != null) p.m.remove();
        for (Spot s : spots) if (s.e != null) s.e.remove();
    }

    private void loadSettings() {
        reloadConfig();
        world = Bukkit.getWorld(getConfig().getString("world", "lobby"));
        avoid.clear();
        for (String s : getConfig().getStringList("avoid")) {
            String[] p = s.trim().split("\\s+");
            if (p.length < 2) continue;
            try {
                avoid.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])});
            } catch (NumberFormatException ignored) {
            }
        }
        avoidR = getConfig().getDouble("avoid-radius", 1.6);
        speed = getConfig().getDouble("passants.speed", 0.09);
        radius = getConfig().getDouble("passants.radius", 22);
        activation = getConfig().getDouble("passants.activation-range", 56);
        for (Passant p : passants) if (p.m != null) p.m.remove();
        passants.clear();
        if (world == null || !getConfig().getBoolean("passants.enabled", true)) return;
        List<Location> anchors = new ArrayList<>();
        for (String s : getConfig().getStringList("passants.anchors")) {
            String[] p = s.trim().split("\\s+");
            if (p.length < 3) continue;
            try {
                anchors.add(new Location(world, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        if (anchors.isEmpty()) return;
        List<String> skins = getConfig().getStringList("passants.skins");
        int count = Math.max(0, getConfig().getInt("passants.count", 8));
        for (int i = 0; i < count; i++) {
            Passant p = new Passant();
            p.anchor = anchors.get(i % anchors.size());
            p.skin = skins.isEmpty() ? null : skins.get(i % skins.size());
            passants.add(p);
        }
    }

    private boolean ours(Entity e) {
        return e != null && e.getPersistentDataContainer().has(roleKey, PersistentDataType.STRING);
    }

    private void tag(Entity e, String role) {
        e.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role);
        e.setPersistent(false);
    }

    private void removeOrphans() {
        for (Entity e : world.getEntities()) if (ours(e)) e.remove();
    }

    private boolean tracked(Entity e) {
        for (Passant p : passants) if (p.m == e) return true;
        for (Spot s : spots) if (s.e == e) return true;
        return seats.containsValue(e);
    }

    private static final ThreadLocalRandom R() {
        return ThreadLocalRandom.current();
    }

    // ================================================================== sol praticable

    /** Hauteur de la face du dessus de la collision du bloc (0..1.5), -1 s'il ne bloque pas. */
    private static double topOf(Block b) {
        Collection<BoundingBox> boxes = b.getCollisionShape().getBoundingBoxes();
        if (boxes.isEmpty()) return -1;
        double max = 0;
        for (BoundingBox box : boxes) max = Math.max(max, box.getMaxY());
        return max;
    }

    private static boolean badSupport(Material m) {
        return switch (m) {
            case MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, SWEET_BERRY_BUSH, POWDER_SNOW, BARRIER, LIGHT -> true;
            default -> m.name().endsWith("_LEAVES") || m.name().endsWith("_BED");
        };
    }

    /** Hauteur des pieds si on peut se tenir en (x, z) en venant de curY (marche de 1, chute de 1,5), sinon null. */
    private Double stand(double x, double z, double curY) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return null;
        int base = (int) Math.floor(curY);
        for (int by = base + 1; by >= base - 2; by--) {
            Block b = world.getBlockAt(bx, by, bz);
            if (b.isLiquid()) return null;
            double top = topOf(b);
            if (top < 0) continue;
            if (top > 1.01 || badSupport(b.getType())) return null;
            double foot = by + top;
            if (foot - curY > 1.05) return null;
            if (curY - foot > 1.6) return null;
            if (!clear(bx, bz, by, foot)) return null;
            return foot;
        }
        return null;
    }

    /** Hauteur des pieds la plus proche de refY dans [refY-8, refY+6], pour poser un passant. */
    private Double standAround(double x, double z, double refY) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return null;
        for (int d = 0; d <= 8; d++) {
            for (int sign : new int[]{1, -1}) {
                int by = (int) Math.floor(refY) - 1 + d * sign;
                Block b = world.getBlockAt(bx, by, bz);
                double top = topOf(b);
                if (top < 0 || top > 1.01 || badSupport(b.getType())) continue;
                double foot = by + top;
                if (clear(bx, bz, by, foot)) return foot;
            }
        }
        return null;
    }

    private boolean clear(int bx, int bz, int supportY, double foot) {
        for (int cy = (int) Math.floor(foot + 0.01); cy <= (int) Math.floor(foot + 1.8); cy++) {
            if (cy == supportY) continue;
            Block c = world.getBlockAt(bx, cy, bz);
            if (c.isLiquid() || topOf(c) >= 0) return false;
        }
        return true;
    }

    private boolean nearAvoid(double x, double z) {
        for (double[] a : avoid) {
            double dx = a[0] - x, dz = a[1] - z;
            if (dx * dx + dz * dz < avoidR * avoidR) return true;
        }
        return false;
    }

    /** Toute la ligne droite de (x,y,z) vers (tx,tz) est praticable ? Renvoie la hauteur d'arrivée ou null. */
    private Double walkable(double x, double y, double z, double tx, double tz) {
        double dx = tx - x, dz = tz - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(dist / 0.4);
        double cy = y;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double px = x + dx * t, pz = z + dz * t;
            if (nearAvoid(px, pz)) return null;
            Double ny = stand(px, pz, cy);
            if (ny == null) return null;
            cy = ny;
        }
        return cy;
    }

    // ================================================================== passants

    private boolean playerNear(double x, double y, double z, double range) {
        double r2 = range * range;
        for (Player pl : world.getPlayers()) {
            Location l = pl.getLocation();
            double dx = l.getX() - x, dy = l.getY() - y, dz = l.getZ() - z;
            if (dx * dx + dy * dy + dz * dz < r2) return true;
        }
        return false;
    }

    private Player nearest(double x, double y, double z, double range) {
        Player best = null;
        double bd = range * range;
        for (Player pl : world.getPlayers()) {
            if (pl.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = pl.getLocation();
            double dx = l.getX() - x, dy = l.getY() - y, dz = l.getZ() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bd) {
                bd = d;
                best = pl;
            }
        }
        return best;
    }

    private boolean pickTarget(Passant p) {
        for (int i = 0; i < 14; i++) {
            double a = R().nextDouble(Math.PI * 2);
            double r = R().nextDouble(3, radius);
            double tx = p.anchor.getX() + Math.cos(a) * r, tz = p.anchor.getZ() + Math.sin(a) * r;
            double dx = tx - p.x, dz = tz - p.z;
            if (dx * dx + dz * dz < 9) continue;
            if (dx * dx + dz * dz > 30 * 30) {
                // pas trop loin d'un coup : on raccourcit le trajet
                double d = Math.sqrt(dx * dx + dz * dz);
                tx = p.x + dx / d * 30;
                tz = p.z + dz / d * 30;
            }
            if (walkable(p.x, p.y, p.z, tx, tz) == null) continue;
            p.tx = tx;
            p.tz = tz;
            p.hasTarget = true;
            return true;
        }
        return false;
    }

    private void spawnPassant(Passant p) {
        Double y = standAround(p.anchor.getX(), p.anchor.getZ(), p.anchor.getY());
        if (y == null) return;
        p.x = p.anchor.getX();
        p.y = y;
        p.z = p.anchor.getZ();
        // départ éparpillé : on le pose au bout d'une promenade au hasard
        if (pickTarget(p)) {
            Double ny = walkable(p.x, p.y, p.z, p.tx, p.tz);
            if (ny != null) {
                p.x = p.tx;
                p.z = p.tz;
                p.y = ny;
            }
            p.hasTarget = false;
        }
        p.yaw = R().nextFloat() * 360f - 180f;
        Location loc = new Location(world, p.x, p.y, p.z, p.yaw, 0);
        boolean names = getConfig().getBoolean("passants.show-names", false);
        p.m = world.spawn(loc, Mannequin.class, m -> {
            tag(m, "passant");
            m.setInvulnerable(true);
            m.setImmovable(true);
            m.setGravity(false);
            m.setRemoveWhenFarAway(false);
            m.setCollidable(false);
            m.setSilent(true);
            m.setDescription(null);
            if (names && p.skin != null) {
                m.customName(Component.text(p.skin));
                m.setCustomNameVisible(true);
            }
        });
        if (!p.m.isValid()) {
            getLogger().warning("Un passant n'a pas pu apparaitre (apparition annulee par un autre plugin).");
            p.m = null;
            return;
        }
        p.pause = R().nextInt(20, 100);
        applySkin(p.m, p.skin);
    }

    private void applySkin(Mannequin m, String name) {
        if (name == null || name.isBlank()) return;
        ResolvableProfile cached = profiles.get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            m.setProfile(cached);
            return;
        }
        ResolvableProfile.resolvableProfile().name(name).build().resolve().whenComplete((profile, error) -> {
            if (error != null || profile == null) {
                getLogger().warning("Skin introuvable pour " + name);
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                ResolvableProfile rp = ResolvableProfile.resolvableProfile(profile);
                profiles.put(name.toLowerCase(Locale.ROOT), rp);
                if (m.isValid()) m.setProfile(rp);
            });
        });
    }

    private static float yawTo(double fx, double fz, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - fx), tz - fz));
    }

    private static float approach(float from, float to, float maxStep) {
        float d = to - from;
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        if (Math.abs(d) <= maxStep) return to;
        return from + Math.signum(d) * maxStep;
    }

    private void tickPassants() {
        if (paused || world == null) return;
        for (Passant p : passants) {
            if (p.m == null || !p.m.isValid()) continue;
            if (!playerNear(p.x, p.y, p.z, activation)) continue;
            if (p.pause > 0) {
                p.pause--;
                idle(p);
                continue;
            }
            if (!p.hasTarget && !pickTarget(p)) {
                p.pause = 40;
                continue;
            }
            double dx = p.tx - p.x, dz = p.tz - p.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= speed) {
                p.hasTarget = false;
                p.pause = R().nextInt(40, 160);
                continue;
            }
            double nx = p.x + dx / dist * speed, nz = p.z + dz / dist * speed;
            Double ny = nearAvoid(nx, nz) ? null : stand(nx, nz, p.y);
            if (ny == null) {
                // quelque chose a changé (bloc posé, joueur...) : nouveau but après une petite pause
                p.hasTarget = false;
                p.pause = 15;
                continue;
            }
            p.yaw = approach(p.yaw, yawTo(p.x, p.z, p.tx, p.tz), 18f);
            p.x = nx;
            p.y = ny;
            p.z = nz;
            p.m.teleport(new Location(world, p.x, p.y, p.z, p.yaw, 0));
            p.m.setBodyYaw(p.yaw);
        }
    }

    /** À l'arrêt : regarde le joueur proche, le salue de temps en temps. */
    private void idle(Passant p) {
        Player pl = nearest(p.x, p.y, p.z, 6);
        if (pl == null) return;
        Location l = pl.getEyeLocation();
        float target = yawTo(p.x, p.z, l.getX(), l.getZ());
        double h = Math.hypot(l.getX() - p.x, l.getZ() - p.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(l.getY() - (p.y + 1.62), Math.max(0.1, h)));
        p.yaw = approach(p.yaw, target, 12f);
        p.m.teleport(new Location(world, p.x, p.y, p.z, p.yaw, Math.max(-40, Math.min(40, pitch))));
        long now = System.currentTimeMillis();
        if (now >= p.nextWave && R().nextInt(60) == 0) {
            p.nextWave = now + 15000L;
            wave(p.m);
        }
    }

    private void wave(LivingEntity m) {
        m.swingMainHand();
        Bukkit.getScheduler().runTaskLater(this, () -> { if (m.isValid()) m.swingMainHand(); }, 6L);
    }

    // ================================================================== animaux

    private File spotsFile() {
        return new File(getDataFolder(), "spots.yml");
    }

    private void loadOrScanSpots() {
        File f = spotsFile();
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (String s : y.getStringList("spots")) {
                String[] p = s.split(" ");
                if (p.length < 4) continue;
                Spot sp = new Spot();
                sp.type = p[0];
                sp.x = Double.parseDouble(p[1]);
                sp.y = Double.parseDouble(p[2]);
                sp.z = Double.parseDouble(p[3]);
                spots.add(sp);
            }
            getLogger().info(spots.size() + " places d'animaux lues dans spots.yml.");
        } else {
            scan(null);
        }
    }

    private void scan(CommandSender who) {
        if (scanning) return;
        scanning = true;
        int minX = getConfig().getInt("animaux.scan.min-x"), maxX = getConfig().getInt("animaux.scan.max-x");
        int minZ = getConfig().getInt("animaux.scan.min-z"), maxZ = getConfig().getInt("animaux.scan.max-z");
        int minY = getConfig().getInt("animaux.scan.min-y", 40), maxY = getConfig().getInt("animaux.scan.max-y", 120);
        int roofMin = getConfig().getInt("animaux.roof-min-y", 56);
        List<CompletableFuture<ChunkSnapshot>> futures = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
                futures.add(world.getChunkAtAsync(cx, cz).thenApply(ch -> ch.getChunkSnapshot(false, false, false)));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, err) -> {
            if (err != null) {
                scanning = false;
                getLogger().warning("Fouille impossible : " + err.getMessage());
                return;
            }
            List<ChunkSnapshot> snaps = new ArrayList<>();
            for (CompletableFuture<ChunkSnapshot> fu : futures) snaps.add(fu.join());
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                List<Spot> cats = new ArrayList<>(), parrots = new ArrayList<>(), fish = new ArrayList<>();
                for (ChunkSnapshot s : snaps) {
                    for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                        int x = (s.getX() << 4) + lx, z = (s.getZ() << 4) + lz;
                        if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                        double snow = 0;
                        for (int y = maxY; y >= minY; y--) {
                            Material m = s.getBlockType(lx, y, lz);
                            if (m.isAir()) continue;
                            if (m == Material.SNOW) {
                                snow = 0.125;
                                continue;
                            }
                            String n = m.name();
                            if (n.endsWith("_LEAVES")) {
                                parrots.add(spot("parrot", x + 0.5, y + 1 + snow, z + 0.5));
                            } else if (m == Material.WATER) {
                                if (y > minY && s.getBlockType(lx, y - 1, lz) == Material.WATER)
                                    fish.add(spot("fish", x + 0.5, y - 0.5, z + 0.5));
                            } else if (y >= roofMin && roofMaterial(n)) {
                                BlockData d = s.getBlockData(lx, y, lz);
                                double top = 1;
                                if (d instanceof Slab sl) top = sl.getType() == Slab.Type.BOTTOM ? 0.5 : 1;
                                else if (d instanceof Stairs st) top = st.getHalf() == Bisected.Half.TOP ? 1 : -1;
                                if (top > 0) cats.add(spot("cat", x + 0.5, y + top + snow, z + 0.5));
                            }
                            break;
                        }
                    }
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    scanning = false;
                    choose(cats, parrots, fish);
                    String msg = "Fouille terminee : " + cats.size() + " toits, " + parrots.size() + " cimes d'arbres, "
                            + fish.size() + " cases d'eau ; " + spots.size() + " animaux places.";
                    getLogger().info(msg);
                    if (who != null) who.sendMessage(PREFIX + msg);
                });
            });
        });
    }

    private static boolean roofMaterial(String n) {
        if (n.contains("GLASS") || n.contains("FENCE") || n.contains("WALL") || n.endsWith("_LOG") || n.endsWith("_WOOD")
                || n.contains("LANTERN") || n.contains("TORCH") || n.contains("CANDLE") || n.contains("BANNER")
                || n.contains("SIGN") || n.contains("PANE") || n.contains("BARS") || n.contains("CHAIN")) return false;
        return n.contains("STAIRS") || n.contains("SLAB") || n.endsWith("PLANKS") || n.contains("TERRACOTTA")
                || n.contains("BRICK") || n.contains("TILES") || n.endsWith("_WOOL") || n.contains("CONCRETE")
                || n.contains("COPPER") || n.endsWith("STONE") || n.contains("QUARTZ") || n.contains("DEEPSLATE");
    }

    private static Spot spot(String type, double x, double y, double z) {
        Spot s = new Spot();
        s.type = type;
        s.x = x;
        s.y = y;
        s.z = z;
        return s;
    }

    private void choose(List<Spot> cats, List<Spot> parrots, List<Spot> fish) {
        for (Spot s : spots) if (s.e != null) s.e.remove();
        spots.clear();
        double spacing = getConfig().getDouble("animaux.spacing", 7);
        pick(cats, getConfig().getInt("animaux.cats", 6), spacing);
        pick(parrots, getConfig().getInt("animaux.parrots", 8), spacing);
        pick(fish, getConfig().getInt("animaux.fish", 10), 3);
        YamlConfiguration y = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Spot s : spots) out.add(s.type + " " + s.x + " " + s.y + " " + s.z);
        y.set("spots", out);
        try {
            getDataFolder().mkdirs();
            y.save(spotsFile());
        } catch (Exception e) {
            getLogger().warning("Impossible d'ecrire spots.yml : " + e.getMessage());
        }
    }

    private void pick(List<Spot> pool, int count, double spacing) {
        Collections.shuffle(pool);
        int taken = 0;
        for (Spot c : pool) {
            if (taken >= count) break;
            if (nearAvoid(c.x, c.z)) continue;
            boolean ok = true;
            for (Spot s : spots) {
                double dx = s.x - c.x, dy = s.y - c.y, dz = s.z - c.z;
                if (dx * dx + dy * dy + dz * dz < spacing * spacing) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            spots.add(c);
            taken++;
        }
    }

    private void spawnSpot(Spot s) {
        Location loc = new Location(world, s.x, s.y, s.z, R().nextFloat() * 360f - 180f, 0);
        switch (s.type) {
            case "cat" -> s.e = world.spawn(loc, Cat.class, c -> {
                common(c, "cat");
                c.setAI(false);
                c.setSilent(true);
                if (R().nextBoolean()) c.setLyingDown(true);
                else c.setSitting(true);
            });
            case "parrot" -> s.e = world.spawn(loc, Parrot.class, c -> {
                common(c, "parrot");
                c.setAI(false);
                c.setSilent(true);
                Parrot.Variant[] v = Parrot.Variant.values();
                c.setVariant(v[R().nextInt(v.length)]);
            });
            case "fish" -> s.e = world.spawn(loc, TropicalFish.class, c -> {
                common(c, "fish");
                TropicalFish.Pattern[] pa = TropicalFish.Pattern.values();
                DyeColor[] col = DyeColor.values();
                c.setPattern(pa[R().nextInt(pa.length)]);
                c.setBodyColor(col[R().nextInt(col.length)]);
                c.setPatternColor(col[R().nextInt(col.length)]);
            });
            default -> {
                return;
            }
        }
        if (!s.e.isValid()) s.e = null;
    }

    private void common(LivingEntity e, String role) {
        tag(e, role);
        e.setInvulnerable(true);
        e.setRemoveWhenFarAway(false);
        e.setCollidable(false);
    }

    /** Toutes les 2 s : recrée ce qui manque (chunk chargé), petits sons des animaux, sièges abandonnés. */
    private void maintain() {
        if (world == null) return;
        if (!paused) {
            for (Passant p : passants) {
                if (p.m != null && p.m.isValid()) continue;
                p.m = null;
                if (world.isChunkLoaded(p.anchor.getBlockX() >> 4, p.anchor.getBlockZ() >> 4)) spawnPassant(p);
            }
            for (Spot s : spots) {
                if (s.e != null && s.e.isValid()) {
                    // poisson parti trop loin : on le remet à sa place
                    if (s.type.equals("fish") && s.e.getLocation().distanceSquared(new Location(world, s.x, s.y, s.z)) > 100)
                        s.e.teleport(new Location(world, s.x, s.y, s.z));
                    ambient(s);
                    continue;
                }
                s.e = null;
                if (world.isChunkLoaded((int) Math.floor(s.x) >> 4, (int) Math.floor(s.z) >> 4)) spawnSpot(s);
            }
        }
        for (UUID id : new ArrayList<>(seats.keySet())) {
            Player pl = Bukkit.getPlayer(id);
            Block b = seatBlock.get(id);
            ItemDisplay d = seats.get(id);
            if (pl == null || d == null || !d.isValid() || !d.getPassengers().contains(pl) || b == null || seatY(b) == null)
                unseat(id, pl != null && d != null && d.getPassengers().contains(pl));
        }
    }

    private void ambient(Spot s) {
        if (R().nextInt(12) != 0) return;
        Location l = s.e.getLocation();
        if (!playerNear(l.getX(), l.getY(), l.getZ(), 16)) return;
        switch (s.type) {
            case "cat" -> world.playSound(l, R().nextBoolean() ? Sound.ENTITY_CAT_AMBIENT : Sound.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.6f, 1f);
            case "parrot" -> {
                world.playSound(l, Sound.ENTITY_PARROT_AMBIENT, SoundCategory.NEUTRAL, 0.6f, 1f);
                s.e.setRotation(R().nextFloat() * 360f - 180f, 0);
            }
            default -> {
            }
        }
    }

    // ================================================================== sièges

    /** Hauteur de l'assise d'un escalier (moitié basse) ou d'une dalle basse, sinon null. */
    private static Double seatY(Block b) {
        BlockData d = b.getBlockData();
        if (d instanceof Stairs st) return st.getHalf() == Bisected.Half.BOTTOM ? b.getY() + 0.5 : null;
        if (d instanceof Slab sl) return sl.getType() == Slab.Type.BOTTOM ? b.getY() + 0.5 : null;
        return null;
    }

    private static float faceYaw(BlockFace f) {
        return switch (f) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSit(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        if (world == null || !p.getWorld().equals(world) || !getConfig().getBoolean("assise.enabled", true)) return;
        if (p.isSneaking() || p.isInsideVehicle() || p.isFlying()) return;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        if (!p.getInventory().getItemInMainHand().getType().isAir()) return;
        Block b = e.getClickedBlock();
        Double y = seatY(b);
        if (y == null) return;
        Block above = b.getRelative(BlockFace.UP);
        if (topOf(above) >= 0 || above.isLiquid()) return;
        Location center = new Location(world, b.getX() + 0.5, y, b.getZ() + 0.5);
        if (p.getLocation().distanceSquared(center) > 9) return;
        for (Block taken : seatBlock.values()) {
            if (taken.equals(b)) {
                p.sendActionBar(Component.text("§cQuelqu'un est déjà assis ici."));
                return;
            }
        }
        e.setCancelled(true);
        float yaw = b.getBlockData() instanceof Stairs st ? faceYaw(st.getFacing().getOppositeFace()) : p.getLocation().getYaw();
        center.setYaw(yaw);
        ItemDisplay seat = world.spawn(center, ItemDisplay.class, d -> {
            tag(d, "seat");
            d.setInvulnerable(true);
        });
        if (!seat.isValid()) return;
        p.teleport(center.clone());
        seat.addPassenger(p);
        seats.put(p.getUniqueId(), seat);
        seatBlock.put(p.getUniqueId(), b);
        p.sendActionBar(Component.text("§7Accroupis-toi pour te lever"));
    }

    private void unseat(UUID id, boolean eject) {
        ItemDisplay d = seats.remove(id);
        Block b = seatBlock.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (d != null) {
            if (eject && p != null) d.removePassenger(p);
            d.remove();
        }
        if (p != null && b != null && !leavingByTeleport.remove(id) && p.getWorld().equals(world)) {
            Location l = new Location(world, b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5,
                    p.getLocation().getYaw(), p.getLocation().getPitch());
            Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline() && !p.isInsideVehicle()) p.teleport(l); });
        }
        leavingByTeleport.remove(id);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemDisplay d = seats.get(p.getUniqueId());
        if (d == null || !d.equals(e.getDismounted())) return;
        Bukkit.getScheduler().runTask(this, () -> unseat(p.getUniqueId(), false));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        if (seats.containsKey(e.getPlayer().getUniqueId()) && !e.isCancelled()) {
            // départ vers un jeu, /lobby... : ne pas le reposer sur le siège ensuite
            if (e.getFrom().distanceSquared(e.getTo()) > 4 || !e.getTo().getWorld().equals(e.getFrom().getWorld()))
                leavingByTeleport.add(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (seats.containsKey(e.getPlayer().getUniqueId())) {
            leavingByTeleport.add(e.getPlayer().getUniqueId());
            unseat(e.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        if (seats.containsKey(e.getPlayer().getUniqueId())) {
            leavingByTeleport.add(e.getPlayer().getUniqueId());
            unseat(e.getPlayer().getUniqueId(), true);
        }
    }

    // ================================================================== protections

    /** WorldGuard (block-plugin-spawning + mob-spawning deny au lobby) annule l'apparition : on rétablit les nôtres. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (ours(e.getEntity())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (ours(e.getEntity())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (ours(e.getEntity()) && !e.getEntity().getPersistentDataContainer().get(roleKey, PersistentDataType.STRING).equals("seat"))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent e) {
        if (ours(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucket(PlayerBucketEntityEvent e) {
        if (ours(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        if (ours(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (!ours(t)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        String role = t.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        Location l = t.getLocation();
        switch (role == null ? "" : role) {
            case "cat" -> {
                world.playSound(l, Sound.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.8f, 1f);
                world.spawnParticle(Particle.HEART, l.clone().add(0, 0.7, 0), 3, 0.3, 0.2, 0.3, 0);
            }
            case "parrot" -> {
                world.playSound(l, Sound.ENTITY_PARROT_AMBIENT, SoundCategory.NEUTRAL, 0.8f, 1.2f);
                world.spawnParticle(Particle.NOTE, l.clone().add(0, 0.8, 0), 2, 0.2, 0.1, 0.2, 1);
            }
            case "passant" -> {
                for (Passant p : passants) {
                    if (p.m != t) continue;
                    p.hasTarget = false;
                    p.pause = Math.max(p.pause, 60);
                    p.yaw = yawTo(p.x, p.z, e.getPlayer().getX(), e.getPlayer().getZ());
                    p.m.teleport(new Location(world, p.x, p.y, p.z, p.yaw, 0));
                    p.m.setBodyYaw(p.yaw);
                    wave(p.m);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (ours(en) && !tracked(en)) en.remove();
    }

    // ================================================================== commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                for (Spot s : spots) if (s.e != null) s.e.remove();
                loadSettings();
                for (Spot s : spots) s.e = null;
                sender.sendMessage(PREFIX + "Rechargé : " + passants.size() + " passants, " + spots.size() + " animaux.");
            }
            case "scan" -> {
                if (world == null) return true;
                sender.sendMessage(PREFIX + "Fouille du lobby en cours...");
                scan(sender);
            }
            case "pause" -> {
                paused = !paused;
                if (paused) {
                    for (Passant p : passants) if (p.m != null) { p.m.remove(); p.m = null; }
                    for (Spot s : spots) if (s.e != null) { s.e.remove(); s.e = null; }
                }
                sender.sendMessage(PREFIX + (paused ? "En pause (passants et animaux retirés)." : "Reprise."));
            }
            default -> {
                int pv = 0, sv = 0;
                for (Passant p : passants) if (p.m != null && p.m.isValid()) pv++;
                for (Spot s : spots) if (s.e != null && s.e.isValid()) sv++;
                sender.sendMessage(PREFIX + "Passants : " + pv + "/" + passants.size() + " ; animaux : " + sv + "/" + spots.size()
                        + " ; assis : " + seats.size() + (paused ? " ; EN PAUSE" : ""));
            }
        }
        return true;
    }
}
