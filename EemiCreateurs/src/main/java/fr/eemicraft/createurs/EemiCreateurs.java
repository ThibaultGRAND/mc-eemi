package fr.eemicraft.createurs;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * L'arbre des créateurs du lobby d'EEMICRAFT : un mannequin (entité Mannequin de 26.x) par créateur, avec son
 * skin et son pseudo, qui s'accroupit et se relève de temps en temps (depuis la 1.2.0 c'est le seul geste joué ;
 * les autres — saluer, coucou, sauter, courir, reculer… — restent dans animate() mais ne sont plus tirés). Même principe qu'EemiRing : décoratifs, invulnérables,
 * non sauvegardés (recréés au démarrage et au chargement du chunk), animés seulement si un joueur est proche.
 */
public final class EemiCreateurs extends JavaPlugin implements Listener {

    private static final String PREFIX = "§6[Createurs] §r";
    /** Tag posé sur nos mannequins, pour les reconnaître (dégâts, nettoyage). */
    private static final String TAG = "eemicreateurs";

    private final List<Createur> createurs = new ArrayList<>();
    private World world;
    private Location center;
    private BukkitTask task;
    private boolean paused;
    private long tick;

    private static final class Createur {
        String name;
        Location base;
        Mannequin entity;
        long nextAt;
        /** Fin de l'animation en cours (tick), pour ne pas en lancer deux à la fois. */
        long busyUntil;

        boolean valid() {
            return entity != null && entity.isValid();
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        load();
        task = getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 1L);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        removeAll();
    }

    // ------------------------------------------------------------------ configuration

    private void load() {
        reloadConfig();
        removeAll();
        createurs.clear();
        world = Bukkit.getWorld(getConfig().getString("world", "lobby"));
        if (world == null) {
            getLogger().warning("Monde " + getConfig().getString("world") + " introuvable : createurs desactives.");
            return;
        }
        ConfigurationSection s = getConfig().getConfigurationSection("createurs");
        if (s == null) {
            getLogger().warning("Section createurs absente du config.yml.");
            return;
        }
        double sx = 0, sy = 0, sz = 0;
        for (String name : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(name);
            if (c == null) continue;
            Createur cr = new Createur();
            cr.name = name;
            cr.base = new Location(world, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), (float) c.getDouble("yaw"), 0f);
            cr.nextAt = ThreadLocalRandom.current().nextInt(40, 120);
            createurs.add(cr);
            sx += cr.base.getX();
            sy += cr.base.getY();
            sz += cr.base.getZ();
        }
        int n = Math.max(1, createurs.size());
        center = new Location(world, sx / n, sy / n, sz / n);
    }

    // ------------------------------------------------------------------ mannequins

    private void ensureSpawned() {
        if (world == null) return;
        for (Createur c : createurs) {
            if (c.valid()) continue;
            if (!world.isChunkLoaded(c.base.getBlockX() >> 4, c.base.getBlockZ() >> 4)) continue;
            spawn(c);
        }
    }

    private void spawn(Createur c) {
        c.entity = world.spawn(c.base, Mannequin.class, m -> {
            m.setPersistent(false);
            m.addScoreboardTag(TAG);
            m.setInvulnerable(true);
            m.setImmovable(true);
            m.setGravity(false);
            m.setRemoveWhenFarAway(false);
            m.setCollidable(false);
            m.setDescription(null);
            m.customName(Component.text(c.name));
            m.setCustomNameVisible(true);
        });
        if (!c.entity.isValid()) {
            getLogger().warning("Le mannequin de " + c.name + " n'a pas pu apparaitre (apparition annulee par un autre plugin).");
            c.entity = null;
            return;
        }
        applySkin(c);
    }

    /** Le profil est résolu côté serveur (textures comprises) puis posé sur le mannequin. */
    private void applySkin(Createur c) {
        Mannequin m = c.entity;
        String name = c.name;
        ResolvableProfile.resolvableProfile().name(name).build().resolve().whenComplete((profile, error) -> {
            if (error != null || profile == null) {
                getLogger().warning("Skin introuvable pour le pseudo " + name + (error != null ? " : " + error.getMessage() : ""));
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                if (m.isValid()) m.setProfile(ResolvableProfile.resolvableProfile(profile));
            });
        });
    }

    private void removeAll() {
        for (Createur c : createurs) {
            if (c.entity != null) c.entity.remove();
            c.entity = null;
        }
    }

    // ------------------------------------------------------------------ animations

    private void tick() {
        tick++;
        if (tick % 40 == 0) ensureSpawned();
        if (paused || world == null || tick % 5 != 0 || !playersNearby()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int min = getConfig().getInt("interval-min", 60), max = Math.max(min, getConfig().getInt("interval-max", 160));
        for (Createur c : createurs) {
            if (!c.valid() || tick < c.nextAt || tick < c.busyUntil) continue;
            // seul geste gardé (demande de Thibault, 28/10/2026) : s'accroupir / se relever
            int length = animate(c, 2);
            c.busyUntil = tick + length;
            c.nextAt = c.busyUntil + r.nextInt(min, max + 1);
        }
    }

    private boolean playersNearby() {
        if (center == null) return false;
        double r = getConfig().getDouble("radius", 40);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= r * r) return true;
        }
        return false;
    }

    /** Lance une animation et renvoie sa durée en ticks. */
    private int animate(Createur c, int kind) {
        Mannequin m = c.entity;
        float yaw = c.base.getYaw();
        switch (kind) {
            case 0 -> { // salue de la main
                for (int i = 0; i < 4; i++) later(i * 6, () -> { if (m.isValid()) m.swingMainHand(); });
                return 24;
            }
            case 1 -> { // regarde à gauche puis à droite
                turn(m, yaw, 0, yaw - 45, 0, 0, 10);
                turn(m, yaw - 45, 0, yaw + 45, 0, 20, 20);
                turn(m, yaw + 45, 0, yaw, 0, 50, 10);
                return 60;
            }
            case 2 -> { // s'accroupit plusieurs fois
                if (!Mannequin.validPoses().contains(Pose.SNEAKING)) return 20;
                for (int i = 0; i < 4; i++) {
                    later(i * 8, () -> { if (m.isValid()) m.setPose(Pose.SNEAKING, true); });
                    later(i * 8 + 4, () -> { if (m.isValid()) m.setPose(Pose.STANDING, false); });
                }
                return 32;
            }
            case 3 -> { // petit saut sur place
                double[] hop = {0.2, 0.35, 0.42, 0.42, 0.35, 0.2, 0};
                for (int i = 0; i < hop.length; i++) {
                    double dy = hop[i];
                    later(i, () -> { if (m.isValid()) m.teleport(c.base.clone().add(0, dy, 0)); });
                }
                later(1, () -> sound(c.base, Sound.ENTITY_PLAYER_SMALL_FALL, 1.4f));
                return hop.length;
            }
            case 4 -> { // hoche la tête
                turn(m, yaw, 0, yaw, 25, 0, 4);
                turn(m, yaw, 25, yaw, -5, 4, 4);
                turn(m, yaw, -5, yaw, 25, 8, 4);
                turn(m, yaw, 25, yaw, 0, 12, 4);
                return 16;
            }
            case 5 -> { // regarde le joueur le plus proche
                for (int i = 0; i < 60; i += 2) later(i, () -> lookAtNearest(c));
                turn(m, yaw, 0, yaw, 0, 62, 1);
                return 63;
            }
            case 7 -> { // coucou : se tourne vers le joueur le plus proche et agite la main
                for (int i = 0; i < 10; i += 2) later(i, () -> lookAtNearest(c));
                for (int i = 0; i < 6; i++) later(4 + i * 4, () -> { if (m.isValid()) m.swingMainHand(); });
                later(6, () -> world.spawnParticle(Particle.HAPPY_VILLAGER, c.base.clone().add(0, 2.2, 0), 5, 0.3, 0.2, 0.3, 0));
                turn(m, yaw, 0, yaw, 0, 30, 6);
                return 36;
            }
            case 8 -> { // grand saut
                double[] jump = {0.15, 0.4, 0.62, 0.8, 0.92, 1.0, 1.02, 1.0, 0.92, 0.8, 0.62, 0.4, 0.15, 0};
                later(0, () -> sound(c.base, Sound.ENTITY_GOAT_LONG_JUMP, 1.3f));
                for (int i = 0; i < jump.length; i++) {
                    double dy = jump[i];
                    later(i + 1, () -> { if (m.isValid()) m.teleport(c.base.clone().add(0, dy, 0)); });
                }
                later(jump.length, () -> sound(c.base, Sound.ENTITY_PLAYER_SMALL_FALL, 1f));
                return jump.length + 1;
            }
            case 9 -> { // petite course : part devant, fait demi-tour, revient
                double dist = getConfig().getDouble("run-distance", 1.2);
                run(c, 0, yaw, 0, dist, 10);
                turn(m, yaw, 0, yaw + 180, 0, 11, 4);
                run(c, 15, yaw + 180, dist, 0, 10);
                turn(m, yaw + 180, 0, yaw, 0, 26, 4);
                return 30;
            }
            case 10 -> { // recul : touché, recule puis revient en place
                later(0, () -> {
                    if (!m.isValid()) return;
                    m.playHurtAnimation(0f);
                    sound(c.base, Sound.ENTITY_PLAYER_HURT, 1.1f);
                });
                double[] back = {-0.3, -0.55, -0.7, -0.75, -0.7, -0.6, -0.45, -0.3, -0.15, 0};
                for (int i = 0; i < back.length; i++) {
                    double d = back[i];
                    later(i + 1, () -> { if (m.isValid()) m.teleport(ahead(c.base, yaw, d, yaw)); });
                }
                return back.length + 1;
            }
            default -> { // content : bras en l'air et petites notes
                for (int i = 0; i < 6; i++) {
                    boolean main = i % 2 == 0;
                    later(i * 4, () -> {
                        if (!m.isValid()) return;
                        if (main) m.swingMainHand();
                        else m.swingOffHand();
                    });
                }
                later(0, () -> world.spawnParticle(Particle.NOTE, c.base.clone().add(0, 2.3, 0), 3, 0.3, 0.1, 0.3, 1));
                later(0, () -> sound(c.base, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f));
                return 24;
            }
        }
    }

    /** Tourne la tête de (y1, p1) à (y2, p2) en steps ticks, à partir de start. */
    private void turn(Mannequin m, float y1, float p1, float y2, float p2, int start, int steps) {
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float y = y1 + (y2 - y1) * t, p = p1 + (p2 - p1) * t;
            later(start + i, () -> { if (m.isValid()) m.setRotation(y, p); });
        }
    }

    /**
     * Court de from à to blocs devant la position de base (selon son regard au repos), en ticks ticks,
     * en regardant vers runYaw, avec les bras qui balancent et un petit rebond.
     */
    private void run(Createur c, int start, float runYaw, double from, double to, int ticks) {
        Mannequin m = c.entity;
        float yaw = c.base.getYaw();
        for (int i = 1; i <= ticks; i++) {
            double t = (double) i / ticks;
            double d = from + (to - from) * t;
            double bounce = (i % 2 == 0) ? 0.08 : 0;
            boolean main = i % 4 == 1, off = i % 4 == 3;
            later(start + i, () -> {
                if (!m.isValid()) return;
                m.teleport(ahead(c.base, yaw, d, runYaw).add(0, bounce, 0));
                if (main) m.swingMainHand();
                if (off) m.swingOffHand();
            });
        }
        later(start + ticks, () -> {
            if (m.isValid()) m.teleport(ahead(c.base, yaw, to, runYaw));
        });
    }

    /** base décalée de dist blocs dans la direction yaw (négatif = en arrière), en regardant vers lookYaw. */
    private static Location ahead(Location base, float yaw, double dist, float lookYaw) {
        double rad = Math.toRadians(yaw);
        Location l = base.clone().add(-Math.sin(rad) * dist, 0, Math.cos(rad) * dist);
        l.setYaw(lookYaw);
        l.setPitch(0f);
        return l;
    }

    private void lookAtNearest(Createur c) {
        if (!c.valid()) return;
        Location eye = c.entity.getEyeLocation();
        double r = getConfig().getDouble("radius", 40);
        Player best = null;
        double bestD = r * r;
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().distanceSquared(eye);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best == null) return;
        Location target = best.getEyeLocation();
        double dx = target.getX() - eye.getX(), dy = target.getY() - eye.getY(), dz = target.getZ() - eye.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        c.entity.setRotation(yaw, Math.max(-40, Math.min(40, pitch)));
    }

    private void sound(Location loc, Sound sound, float pitch) {
        float volume = (float) getConfig().getDouble("sound-volume", 0.4);
        world.playSound(loc, sound, SoundCategory.NEUTRAL, volume, pitch);
    }

    private void later(int ticks, Runnable run) {
        if (ticks <= 0) run.run();
        else getServer().getScheduler().runTaskLater(this, run, ticks);
    }

    private void resetPoses() {
        for (Createur c : createurs) {
            if (!c.valid()) continue;
            c.entity.setPose(Pose.STANDING, false);
            c.entity.teleport(c.base);
        }
    }

    // ------------------------------------------------------------------ protections

    private static boolean ours(Entity e) {
        return e.getScoreboardTags().contains(TAG);
    }

    /** WorldGuard (block-plugin-spawning + mob-spawning deny au lobby) annule l'apparition : on rétablit les nôtres. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(CreatureSpawnEvent event) {
        if (ours(event.getEntity())) event.setCancelled(false);
    }

    /**
     * Aucun dégât, même d'un joueur en créatif (setInvulnerable ne protège pas contre le créatif) : annulé en
     * dernier, quoi qu'aient décidé les autres plugins, et sans animation ni son de dégât.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (ours(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (ours(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (ours(event.getRightClicked())) event.setCancelled(true);
    }

    /** Mannequins orphelins (non sauvegardés en principe, mais on ne prend pas de risque). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (!ours(e)) continue;
            boolean known = false;
            for (Createur c : createurs) if (c.entity == e) known = true;
            if (!known) e.remove();
        }
    }

    // ------------------------------------------------------------------ commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eemicreateurs.admin")) {
            sender.sendMessage(PREFIX + "§cPermission refusee.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "reload" -> {
                load();
                ensureSpawned();
                sender.sendMessage(PREFIX + "§aConfig rechargee, createurs recrees.");
            }
            case "pause" -> {
                paused = !paused;
                if (paused) resetPoses();
                sender.sendMessage(PREFIX + (paused ? "§eAnimations en pause." : "§aAnimations relancees."));
            }
            case "pos" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(PREFIX + "§cCommande reservee aux joueurs.");
                    return true;
                }
                if (args.length < 2 || getConfig().getConfigurationSection("createurs." + args[1]) == null) {
                    sender.sendMessage(PREFIX + "§c/createurs pos <pseudo> (pseudo exact du config.yml)");
                    return true;
                }
                Location l = p.getLocation();
                String path = "createurs." + args[1] + ".";
                getConfig().set(path + "x", Math.round(l.getX() * 100) / 100.0);
                getConfig().set(path + "y", Math.round(l.getY() * 100) / 100.0);
                getConfig().set(path + "z", Math.round(l.getZ() * 100) / 100.0);
                getConfig().set(path + "yaw", Math.round(l.getYaw() * 10) / 10.0);
                saveConfig();
                load();
                ensureSpawned();
                sender.sendMessage(PREFIX + "§a" + args[1] + " deplace ici.");
            }
            default -> sender.sendMessage(PREFIX + "§e/createurs reload | pause | pos <pseudo>");
        }
        return true;
    }
}
