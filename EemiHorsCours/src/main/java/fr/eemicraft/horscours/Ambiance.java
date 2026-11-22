package fr.eemicraft.horscours;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * La peur dans l'école, inspirée des grands jeux d'horreur (P.T., Outlast, Amnesia, FNAF) : rien n'arrive
 * à heure fixe. Néons qui grésillent puis coupure de courant (les lumières ne reviennent que par éclairs),
 * pas qui approchent dans le dos, portes qui claquent, chuchotements, obscurité qui pulse, et Loïc qui
 * apparaît au bout du couloir puis disparaît quand on le regarde. Tout est envoyé au seul joueur concerné :
 * le monde (l'école officielle) n'est jamais modifié.
 */
final class Ambiance {
    private final EemiHorsCours plugin;
    private final Ecole ecole;
    private final Random rnd = new Random();
    private boolean cut;
    private final Map<UUID, Long> nextScare = new HashMap<>();
    private final Map<UUID, Apparition> apps = new HashMap<>();

    private static final class Apparition {
        ItemDisplay e;
        long born;
        int seen;
    }

    Ambiance(EemiHorsCours plugin, Ecole ecole) {
        this.plugin = plugin;
        this.ecole = ecole;
    }

    void reset() {
        cut = false;
        nextScare.clear();
        for (Apparition a : apps.values()) if (a.e != null && a.e.isValid()) a.e.remove();
        apps.clear();
    }

    void forget(Player p) {
        nextScare.remove(p.getUniqueId());
        Apparition a = apps.remove(p.getUniqueId());
        if (a != null && a.e != null && a.e.isValid()) a.e.remove();
        // Rendre les vraies lumières au joueur
        for (Ecole.Lum l : ecole.lights) p.sendBlockChange(l.at(), l.on());
    }

    private void later(long t, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, t);
    }

    private static void send(List<Player> ps, Location at, BlockData d) {
        for (Player p : ps) if (p.isOnline() && p.getWorld().equals(at.getWorld())) p.sendBlockChange(at, d);
    }

    // ------------------------------------------------------------------ lumières
    /** Coupure de courant : toutes les lumières de l'école s'éteignent pour les joueurs de la partie. */
    void powerCut(List<Player> ps) {
        cut = true;
        for (Player p : ps) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 1f, 0.5f);
            p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.AMBIENT, 0.8f, 0.6f);
        }
        // les néons meurent les uns après les autres, du plus proche au plus loin
        for (Ecole.Lum l : ecole.lights) {
            double d = ps.isEmpty() ? 0 : l.at().distance(ps.get(0).getLocation());
            later((long) Math.min(40, d * 1.5), () -> send(ps, l.at(), l.off()));
        }
    }

    /** Néons qui s'affolent autour d'un point pendant n ticks (télés et café, frayeurs). */
    void strobe(List<Player> ps, Location c, int ticks) {
        for (Ecole.Lum l : ecole.lights) {
            if (l.at().distanceSquared(c) > 18 * 18) continue;
            for (int t = 0; t < ticks; t += 2 + rnd.nextInt(4)) {
                final boolean on = rnd.nextBoolean();
                later(t, () -> send(ps, l.at(), on ? l.on() : l.off()));
            }
            later(ticks + 2, () -> send(ps, l.at(), cut ? l.off() : l.on()));
        }
        for (Player p : ps) p.playSound(c, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 1f, 2f);
    }

    /** Un néon qui grésille : quelques coupures rapides et un bourdonnement. */
    private void flicker(List<Player> ps, Ecole.Lum l) {
        int t = 0;
        int n = 2 + rnd.nextInt(4);
        for (int i = 0; i < n; i++) {
            later(t, () -> send(ps, l.at(), l.off()));
            t += 1 + rnd.nextInt(3);
            later(t, () -> send(ps, l.at(), l.on()));
            t += 1 + rnd.nextInt(4);
        }
        for (Player p : ps)
            p.playSound(l.at(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.AMBIENT, 0.5f, 0.6f + rnd.nextFloat() * 0.3f);
    }

    /** Appelé toutes les 2 ticks. */
    void tickLights(List<Player> all, long tick) {
        if (ecole.lights.isEmpty()) return;
        List<Player> in = all.stream().filter(ecole::inside).toList();
        if (in.isEmpty()) return;
        Player ref = in.get(rnd.nextInt(in.size()));
        if (!cut) {
            if (rnd.nextInt(12) == 0) {
                Ecole.Lum l = ecole.randomLightNear(ref.getLocation(), 16, rnd);
                if (l != null) flicker(in, l);
            }
            return;
        }
        // Après la coupure : on garde le noir (le client peut recharger un tronçon), et parfois un éclair
        if (tick % 20 == 0)
            for (Ecole.Lum l : ecole.lights) send(in, l.at(), l.off());
        if (rnd.nextInt(30) == 0) {
            Ecole.Lum l = ecole.randomLightNear(ref.getLocation(), 14, rnd);
            if (l != null) {
                send(in, l.at(), l.on());
                for (Player p : in) p.playSound(l.at(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.AMBIENT, 0.3f, 1.8f);
                later(2 + rnd.nextInt(5), () -> send(in, l.at(), l.off()));
            }
        }
    }

    // ------------------------------------------------------------------ frayeurs
    /** Appelé toutes les 2 ticks pour chaque joueur. lv 0 = école, 1 = labyrinthe. */
    void tickPlayer(Player p, int lv, int stage, long tick) {
        watchApparition(p);
        if (lv == 0 && stage < 1) return; // avant la coupure : seulement les néons
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        long next = nextScare.computeIfAbsent(id, k -> now + 15_000 + rnd.nextInt(15_000));
        if (now < next) return;
        nextScare.put(id, now + (lv == 1 ? 25_000 : 20_000) + rnd.nextInt(25_000));
        int r = rnd.nextInt(100);
        if (lv == 0 && ecole.inside(p)) {
            if (r < 22) footsteps(p);
            else if (r < 40) slamDoor(p);
            else if (r < 58) whisper(p);
            else if (r < 82) appear(p);
            else darkness(p);
        } else {
            if (r < 40) footsteps(p);
            else if (r < 70) whisper(p);
            else darkness(p);
        }
    }

    /** Des pas qui approchent dans le dos, puis plus rien. */
    private void footsteps(Player p) {
        Vector back = p.getLocation().getDirection().setY(0).normalize().multiply(-1);
        for (int i = 0; i < 5; i++) {
            final double d = 6 - i * 1.1;
            later(i * 7L, () -> {
                if (!p.isOnline()) return;
                Location at = p.getLocation().add(back.clone().multiply(d));
                p.playSound(at, Sound.BLOCK_STONE_STEP, SoundCategory.HOSTILE, 0.9f, 0.7f);
            });
        }
    }

    /** La porte la plus proche s'ouvre et claque (chez le joueur seulement). */
    private void slamDoor(Player p) {
        Location best = null;
        double bd = 14 * 14;
        for (Location d : ecole.doors) {
            double x = d.distanceSquared(p.getLocation());
            if (x < bd && x > 4) {
                bd = x;
                best = d;
            }
        }
        if (best == null) {
            whisper(p);
            return;
        }
        Block low = best.getBlock(), up = low.getRelative(0, 1, 0);
        if (!(low.getBlockData() instanceof Door dl) || !(up.getBlockData() instanceof Door du)) return;
        Door ol = (Door) dl.clone(), ou = (Door) du.clone();
        ol.setOpen(!dl.isOpen());
        ou.setOpen(!du.isOpen());
        p.sendBlockChange(low.getLocation(), ol);
        p.sendBlockChange(up.getLocation(), ou);
        p.playSound(best, Sound.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 0.7f, 0.6f);
        final Location at = best;
        later(14, () -> {
            p.sendBlockChange(low.getLocation(), low.getBlockData());
            p.sendBlockChange(up.getLocation(), up.getBlockData());
            p.playSound(at, Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1f, 0.5f);
            p.playSound(at, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.6f, 0.6f);
        });
    }

    /** Un chuchotement tout près de l'oreille. */
    private void whisper(Player p) {
        Vector right = p.getLocation().getDirection().setY(0).normalize().crossProduct(new Vector(0, 1, 0));
        Location ear = p.getEyeLocation().add(right.multiply(rnd.nextBoolean() ? 1.2 : -1.2));
        p.playSound(ear, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, SoundCategory.HOSTILE, 1f, 1.4f);
        p.playSound(ear, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, SoundCategory.HOSTILE, 0.4f, 0.5f);
    }

    /** L'obscurité pulse, le cœur s'emballe. */
    private void darkness(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 90, 0, false, false, false));
        for (int i = 0; i < 6; i++)
            later(i * 9L, () -> {
                if (p.isOnline()) p.playSound(p, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1f, 1.2f);
            });
    }

    /** Loïc au bout du couloir, visible du seul joueur ; il disparaît quand on le fixe. */
    private void appear(Player p) {
        if (apps.containsKey(p.getUniqueId())) return;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().setY(0).normalize();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, dir, 16, FluidCollisionMode.NEVER, true);
        double dist = hit == null ? 14 : hit.getHitPosition().distance(eye.toVector()) - 1.2;
        if (dist < 5) {
            footsteps(p);
            return;
        }
        // un peu de côté, pour qu'on le voie du coin de l'œil
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0)).multiply((rnd.nextDouble() - 0.5) * 2);
        Location at = eye.clone().add(dir.clone().multiply(Math.min(dist, 12))).add(side);
        Block floor = null;
        for (int dy = 0; dy < 4; dy++) {
            Block b = at.getBlock().getRelative(0, -dy, 0);
            if (b.getType().isSolid()) {
                floor = b;
                break;
            }
        }
        if (floor == null) return;
        Location feet = new Location(p.getWorld(), at.getX(), floor.getY() + 1, at.getZ());
        double dx = p.getX() - feet.getX(), dz = p.getZ() - feet.getZ();
        feet.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)) + (float) plugin.getConfig().getDouble("loic.yaw-offset", 180));
        ItemDisplay e = p.getWorld().spawn(feet, ItemDisplay.class, d -> {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setItemModel(new NamespacedKey("eemicraft", "horscours/loic_idle"));
            it.setItemMeta(m);
            d.setItemStack(it);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0f, 0.5f, 0f), new AxisAngle4f(), new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(0, 0)); // une silhouette sombre
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            plugin.tag(d, "apparition");
        });
        p.showEntity(plugin, e);
        Apparition a = new Apparition();
        a.e = e;
        a.born = System.currentTimeMillis();
        apps.put(p.getUniqueId(), a);
        p.playSound(feet, Sound.BLOCK_STONE_STEP, SoundCategory.HOSTILE, 0.5f, 0.6f);
    }

    private void watchApparition(Player p) {
        Apparition a = apps.get(p.getUniqueId());
        if (a == null) return;
        if (a.e == null || !a.e.isValid() || !p.getWorld().equals(a.e.getWorld())) {
            apps.remove(p.getUniqueId());
            return;
        }
        Location eye = p.getEyeLocation();
        Vector to = a.e.getLocation().add(0, 1.4, 0).toVector().subtract(eye.toVector());
        double d = to.length();
        boolean seen = d > 0.1 && to.clone().multiply(1 / d).dot(eye.getDirection()) > 0.93
                && p.getWorld().rayTraceBlocks(eye, to.clone().normalize(), Math.max(0, d - 0.5), FluidCollisionMode.NEVER, true) == null;
        if (seen) a.seen++;
        boolean old = System.currentTimeMillis() - a.born > 7000;
        if (a.seen >= 3 || old || d < 2.5) {
            Location at = a.e.getLocation();
            a.e.remove();
            apps.remove(p.getUniqueId());
            if (!old) {
                p.playSound(at, Sound.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 1f, 0.6f);
                p.playSound(p, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1f, 1.4f);
                p.spawnParticle(Particle.SMOKE, at.clone().add(0, 1, 0), 30, 0.3, 0.8, 0.3, 0.01);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
                strobe(List.of(p), at, 14);
            }
        }
    }
}
