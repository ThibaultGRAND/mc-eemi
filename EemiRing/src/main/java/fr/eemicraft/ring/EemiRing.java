package fr.eemicraft.ring;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ring du lobby d'EEMICRAFT : deux mannequins (entités Mannequin de 26.x, skins de joueurs) se battent
 * en boucle — coups d'épée alternés, bouclier levé à temps ou non, dégâts, critiques, petit recul.
 * Purement décoratif : invulnérables, non sauvegardés (recréés au démarrage et au chargement du chunk),
 * et le combat ne tourne que si un joueur est proche.
 */
public final class EemiRing extends JavaPlugin implements Listener {

    private static final String PREFIX = "§6[Ring] §r";
    /** Tag posé sur nos mannequins, pour les reconnaître (dégâts, nettoyage). */
    private static final String TAG = "eemiring";
    /** Délais (ticks) d'un échange : élan de l'attaquant, impact, retour en place. */
    private static final int SWING_AT = 4, IMPACT_AT = 6, RESET_AT = 12;

    private final Fighter[] fighters = {new Fighter(), new Fighter()};
    private World world;
    private BukkitTask task;
    private boolean paused;
    private long tick;
    private int cooldown = 40;
    private int attacker;

    private static final class Fighter {
        Location base;
        String skin = "";
        Material weapon, helmet, chestplate, leggings, boots;
        Mannequin entity;

        boolean valid() {
            return entity != null && entity.isValid();
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        loadFighters();
        task = getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 1L);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        removeAll();
    }

    // ------------------------------------------------------------------ configuration

    private void loadFighters() {
        reloadConfig();
        removeAll();
        world = Bukkit.getWorld(getConfig().getString("world", "lobby"));
        if (world == null) {
            getLogger().warning("Monde " + getConfig().getString("world") + " introuvable : ring desactive.");
            return;
        }
        for (int i = 0; i < 2; i++) {
            ConfigurationSection s = getConfig().getConfigurationSection("fighters." + (i + 1));
            Fighter f = fighters[i];
            if (s == null) {
                getLogger().warning("Section fighters." + (i + 1) + " absente du config.yml.");
                world = null;
                return;
            }
            f.base = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
            f.skin = s.getString("skin", "");
            f.weapon = material(s.getString("weapon"));
            f.helmet = material(s.getString("helmet"));
            f.chestplate = material(s.getString("chestplate"));
            f.leggings = material(s.getString("leggings"));
            f.boots = material(s.getString("boots"));
        }
        // Chacun regarde l'autre.
        face(fighters[0].base, fighters[1].base);
        face(fighters[1].base, fighters[0].base);
    }

    private Material material(String name) {
        if (name == null || name.isBlank()) return null;
        Material m = Material.matchMaterial(name);
        if (m == null) getLogger().warning("Objet inconnu dans le config.yml : " + name);
        return m;
    }

    private static void face(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        from.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        from.setPitch(5f);
    }

    // ------------------------------------------------------------------ mannequins

    private void ensureSpawned() {
        if (world == null) return;
        for (Fighter f : fighters) {
            if (f.valid()) continue;
            if (!world.isChunkLoaded(f.base.getBlockX() >> 4, f.base.getBlockZ() >> 4)) continue;
            spawn(f);
        }
    }

    private void spawn(Fighter f) {
        f.entity = world.spawn(f.base, Mannequin.class, m -> {
            m.setPersistent(false);
            m.addScoreboardTag(TAG);
            m.setInvulnerable(true);
            m.setImmovable(true);
            m.setRemoveWhenFarAway(false);
            m.setCollidable(false);
            m.setDescription(null);
            m.setCustomNameVisible(false);
            EntityEquipment eq = m.getEquipment();
            eq.setItemInMainHand(item(f.weapon));
            eq.setItemInOffHand(new ItemStack(Material.SHIELD));
            eq.setHelmet(item(f.helmet));
            eq.setChestplate(item(f.chestplate));
            eq.setLeggings(item(f.leggings));
            // Pas de setDropChance : réservé aux Mob, un Mannequin lève une exception (vu en 1.0.0).
            eq.setBoots(item(f.boots));
        });
        if (!f.entity.isValid()) {
            getLogger().warning("Le mannequin n'a pas pu apparaitre (apparition annulee par un autre plugin).");
            f.entity = null;
            return;
        }
        applySkin(f);
    }

    private static ItemStack item(Material m) {
        return m == null ? null : new ItemStack(m);
    }

    /** Le profil est résolu côté serveur (textures comprises) puis posé sur le mannequin. */
    private void applySkin(Fighter f) {
        Mannequin m = f.entity;
        if (m == null) return;
        if (f.skin == null || f.skin.isBlank()) {
            m.setProfile(Mannequin.defaultProfile());
            return;
        }
        String name = f.skin;
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
        for (Fighter f : fighters) {
            if (f.entity != null) f.entity.remove();
            f.entity = null;
        }
    }

    // ------------------------------------------------------------------ combat

    private void tick() {
        tick++;
        if (tick % 40 == 0) ensureSpawned();
        if (paused || --cooldown > 0) return;
        if (!fighters[0].valid() || !fighters[1].valid() || !playersNearby()) {
            cooldown = 20;
            return;
        }
        exchange();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int min = getConfig().getInt("interval-min", 14), max = Math.max(min, getConfig().getInt("interval-max", 22));
        cooldown = RESET_AT + r.nextInt(min, max + 1);
    }

    private boolean playersNearby() {
        Location center = fighters[0].base.clone().add(fighters[1].base).multiply(0.5);
        double r = getConfig().getDouble("radius", 40);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= r * r) return true;
        }
        return false;
    }

    /** Un échange : l'attaquant prend son élan et frappe, le défenseur pare au bouclier ou encaisse. */
    private void exchange() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Fighter a = fighters[attacker], d = fighters[1 - attacker];
        Mannequin am = a.entity, dm = d.entity;
        boolean blocked = r.nextDouble() < getConfig().getDouble("block-chance", 0.4);
        boolean crit = !blocked && r.nextDouble() < getConfig().getDouble("crit-chance", 0.2);

        am.clearActiveItem();
        if (blocked) dm.startUsingItem(EquipmentSlot.OFF_HAND);
        else dm.clearActiveItem();

        later(SWING_AT, () -> {
            if (!am.isValid()) return;
            am.teleport(offset(a, d, 0.25));
            am.swingMainHand();
            sound(am.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.1f);
        });
        later(IMPACT_AT, () -> {
            if (!dm.isValid()) return;
            Location hit = dm.getLocation().add(0, 1.2, 0);
            if (blocked) {
                sound(hit, Sound.ITEM_SHIELD_BLOCK, 1f);
                world.spawnParticle(Particle.CRIT, hit, 5, 0.2, 0.2, 0.2, 0.05);
                return;
            }
            dm.playHurtAnimation(0f);
            dm.teleport(offset(d, a, -0.3));
            sound(hit, Sound.ENTITY_PLAYER_HURT, 1f);
            if (crit) {
                sound(hit, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f);
                world.spawnParticle(Particle.CRIT, hit, 14, 0.3, 0.4, 0.3, 0.15);
            } else {
                sound(hit, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f);
                world.spawnParticle(Particle.DAMAGE_INDICATOR, hit, 3, 0.2, 0.2, 0.2, 0.05);
            }
        });
        later(RESET_AT, () -> {
            if (am.isValid()) am.teleport(a.base);
            if (dm.isValid()) {
                dm.teleport(d.base);
                dm.clearActiveItem();
            }
        });

        if (r.nextDouble() >= getConfig().getDouble("combo-chance", 0.25)) attacker = 1 - attacker;
    }

    /** Position de base de f décalée de dist blocs vers other (négatif = en s'éloignant). */
    private static Location offset(Fighter f, Fighter other, double dist) {
        Vector dir = other.base.toVector().subtract(f.base.toVector()).setY(0);
        if (dir.lengthSquared() < 1e-6) return f.base.clone();
        return f.base.clone().add(dir.normalize().multiply(dist));
    }

    private void sound(Location loc, Sound sound, float pitch) {
        float volume = (float) getConfig().getDouble("sound-volume", 0.6);
        float jitter = (float) ThreadLocalRandom.current().nextDouble(-0.08, 0.08);
        world.playSound(loc, sound, SoundCategory.PLAYERS, volume, pitch + jitter);
    }

    private void later(int ticks, Runnable run) {
        getServer().getScheduler().runTaskLater(this, run, ticks);
    }

    private void resetPoses() {
        for (Fighter f : fighters) {
            if (!f.valid()) continue;
            f.entity.clearActiveItem();
            f.entity.teleport(f.base);
        }
    }

    // ------------------------------------------------------------------ protections

    private static boolean ours(Entity e) {
        return e.getScoreboardTags().contains(TAG);
    }

    /**
     * WorldGuard (block-plugin-spawning: true + mob-spawning deny dans le lobby) annule l'apparition
     * des mannequins : vu en 1.0.1, aucun combattant ni erreur. On rétablit les nôtres seulement.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(CreatureSpawnEvent event) {
        if (ours(event.getEntity())) event.setCancelled(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
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
            if (ours(e) && e != fighters[0].entity && e != fighters[1].entity) e.remove();
        }
    }

    // ------------------------------------------------------------------ commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eemiring.admin")) {
            sender.sendMessage(PREFIX + "§cPermission refusee.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "reload" -> {
                loadFighters();
                ensureSpawned();
                sender.sendMessage(PREFIX + "§aConfig rechargee, combattants recrees.");
            }
            case "pause" -> {
                paused = !paused;
                if (paused) resetPoses();
                sender.sendMessage(PREFIX + (paused ? "§eCombat en pause." : "§aCombat relance."));
            }
            case "skin" -> {
                Integer n = fighterIndex(sender, args);
                if (n == null) return true;
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + "§c/ring skin <1|2> <pseudo|aucun>");
                    return true;
                }
                String skin = args[2].equalsIgnoreCase("aucun") ? "" : args[2];
                getConfig().set("fighters." + (n + 1) + ".skin", skin);
                saveConfig();
                fighters[n].skin = skin;
                applySkin(fighters[n]);
                sender.sendMessage(PREFIX + "§aSkin du combattant " + (n + 1) + " : " + (skin.isEmpty() ? "par defaut" : skin) + ".");
            }
            case "pos" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(PREFIX + "§cCommande reservee aux joueurs.");
                    return true;
                }
                Integer n = fighterIndex(sender, args);
                if (n == null) return true;
                Location l = p.getLocation();
                String path = "fighters." + (n + 1) + ".";
                getConfig().set(path + "x", Math.round(l.getX() * 10) / 10.0);
                getConfig().set(path + "y", Math.round(l.getY() * 10) / 10.0);
                getConfig().set(path + "z", Math.round(l.getZ() * 10) / 10.0);
                saveConfig();
                loadFighters();
                ensureSpawned();
                sender.sendMessage(PREFIX + "§aCombattant " + (n + 1) + " deplace ici.");
            }
            default -> sender.sendMessage(PREFIX + "§e/ring reload | pause | skin <1|2> <pseudo|aucun> | pos <1|2>");
        }
        return true;
    }

    private Integer fighterIndex(CommandSender sender, String[] args) {
        if (args.length >= 2 && (args[1].equals("1") || args[1].equals("2"))) return Integer.parseInt(args[1]) - 1;
        sender.sendMessage(PREFIX + "§cPrecise le combattant : 1 ou 2.");
        return null;
    }
}
