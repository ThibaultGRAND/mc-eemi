package fr.eemicraft.refuge;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Les zombies de la section 8 : apparition, comportements, ouïe. Salle d'essai et mondes d'expédition en cours. */
public final class Zombies implements Listener {

    public enum Type {
        RODEUR("Rôdeur"), COUREUR("Coureur"), BRUTE("Brute"), HURLEUR("Hurleur"), CRACHEUR("Cracheur"),
        GONFLE("Gonflé"), NOYE("Noyé"), RAMPANT("Rampant"), BLINDE("Blindé");

        public final String label;

        Type(String label) {
            this.label = label;
        }
    }

    static final class State {
        final Type type;
        final boolean elite;
        Location investigate;
        long investigateUntil, lureUntil, lastSpit, lastScream;
        boolean fusing;
        int barricadeTicks;

        State(Type type, boolean elite) {
            this.type = type;
            this.elite = elite;
        }
    }

    private final EemiRefuge plugin;
    final NamespacedKey keyType, keyElite, keySpit;
    final Map<UUID, State> states = new HashMap<>();
    boolean frozen;

    Zombies(EemiRefuge plugin) {
        this.plugin = plugin;
        keyType = new NamespacedKey(plugin, "zombie");
        keyElite = new NamespacedKey(plugin, "elite");
        keySpit = new NamespacedKey(plugin, "acide");
    }

    // ------------------------------------------------------------------ apparition

    public Mob spawn(Location loc, Type type, boolean elite) {
        World w = loc.getWorld();
        Class<? extends Mob> cls = switch (type) {
            case HURLEUR -> ZombieVillager.class;
            case CRACHEUR -> Husk.class;
            case NOYE -> Drowned.class;
            default -> Zombie.class;
        };
        Mob mob = w.spawn(loc, cls, m -> {
            m.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, type.name());
            if (elite) m.getPersistentDataContainer().set(keyElite, PersistentDataType.BYTE, (byte) 1);
            m.addScoreboardTag("eemirefuge_zombie");
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            if (m instanceof Zombie z) {
                z.setAdult();
                z.setShouldBurnInDay(false);
                z.setConversionTime(-1);
            }
            setup(m, type, elite);
        });
        if (!mob.isValid()) {
            plugin.getLogger().warning("Le zombie " + type + " n'a pas pu apparaitre (annule par un autre plugin).");
            return null;
        }
        states.put(mob.getUniqueId(), new State(type, elite));
        if (frozen) mob.setAI(false);
        return mob;
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    private void setup(Mob m, Type t, boolean elite) {
        double hp, speed, dmg;
        EntityEquipment eq = m.getEquipment();
        eq.clear();
        switch (t) {
            case COUREUR -> { hp = 12; speed = 0.34; dmg = 2; }
            case BRUTE -> {
                hp = 60; speed = 0.2; dmg = 7;
                attr(m, Attribute.SCALE, 1.4);
                attr(m, Attribute.ATTACK_KNOCKBACK, 1.5);
                attr(m, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            }
            case HURLEUR -> { hp = 16; speed = 0.2; dmg = 3; attr(m, Attribute.FOLLOW_RANGE, 24); }
            case CRACHEUR -> { hp = 18; speed = 0.2; dmg = 3; }
            case GONFLE -> {
                hp = 14; speed = 0.2; dmg = 2;
                attr(m, Attribute.SCALE, 1.15);
                // 0.2.0 : armure de cuir remplacée par la tenue du pack (habiller).
            }
            case NOYE -> { hp = 20; speed = 0.23; dmg = 3; eq.setItemInMainHand(new ItemStack(Material.TRIDENT)); }
            case RAMPANT -> {
                hp = 14; speed = 0.12; dmg = 2;
                // Couché : pose « nage » imposée (à valider en jeu, cf. CONCEPTION 8).
                m.setPose(Pose.SWIMMING, true);
            }
            case BLINDE -> {
                hp = 30; speed = 0.18; dmg = 4;
                // 0.2.0 : armure de fer remplacée par la tenue du pack ; ses 15 points d'armure sont gardés.
                attr(m, Attribute.ARMOR, 15);
            }
            default -> { hp = 20; speed = 0.18; dmg = 3; }
        }
        habiller(m, t, elite);
        if (t != Type.HURLEUR) attr(m, Attribute.FOLLOW_RANGE, 20);
        attr(m, Attribute.SPAWN_REINFORCEMENTS, 0);
        if (elite) {
            hp *= 2.5;
            dmg *= 1.5;
            speed *= 1.1;
            attr(m, Attribute.KNOCKBACK_RESISTANCE, Math.max(0.5, t == Type.BRUTE ? 0.9 : 0.5));
        }
        attr(m, Attribute.MAX_HEALTH, hp);
        m.setHealth(hp);
        attr(m, Attribute.MOVEMENT_SPEED, speed);
        attr(m, Attribute.ATTACK_DAMAGE, dmg);
        eq.setHelmetDropChance(0);
        eq.setChestplateDropChance(0);
        eq.setLeggingsDropChance(0);
        eq.setBootsDropChance(0);
        eq.setItemInMainHandDropChance(0);
        m.customName(Txt.c((elite ? "§6" + t.label + " élite" : "§f" + t.label)));
        m.setCustomNameVisible(true);
    }

    // ------------------------------------------------------------------ tenues du pack (pack/ZOMBIES.md)

    /** Pièce de tenue : papier avec le composant equippable (asset refuge:zombie_<type>[_elite]), sans armure. */
    private static ItemStack tenue(Type t, boolean elite, org.bukkit.inventory.EquipmentSlot slot) {
        ItemStack it = ItemStack.of(Material.PAPER);
        String id = "zombie_" + t.name().toLowerCase(java.util.Locale.ROOT) + (elite ? "_elite" : "");
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE,
                io.papermc.paper.datacomponent.item.Equippable.equippable(slot)
                        .assetId(net.kyori.adventure.key.Key.key("refuge", id)));
        return it;
    }

    private static List<org.bukkit.inventory.EquipmentSlot> pieces(Type t) {
        var H = org.bukkit.inventory.EquipmentSlot.HEAD;
        var C = org.bukkit.inventory.EquipmentSlot.CHEST;
        var L = org.bukkit.inventory.EquipmentSlot.LEGS;
        var F = org.bukkit.inventory.EquipmentSlot.FEET;
        return switch (t) {
            case COUREUR, BRUTE, BLINDE -> List.of(H, C, L, F);
            case GONFLE, RAMPANT -> List.of(H, C, L);
            default -> List.of();
        };
    }

    private static void habiller(Mob m, Type t, boolean elite) {
        EntityEquipment eq = m.getEquipment();
        java.util.Set<org.bukkit.inventory.EquipmentSlot> slots = new java.util.LinkedHashSet<>(pieces(t));
        if (elite) { // yeux dorés (casque) + brassard doré (plastron)
            if (t != Type.HURLEUR) slots.add(org.bukkit.inventory.EquipmentSlot.HEAD);
            slots.add(org.bukkit.inventory.EquipmentSlot.CHEST);
        }
        for (org.bukkit.inventory.EquipmentSlot s : slots) {
            eq.setItem(s, tenue(t, elite, s));
            eq.setDropChance(s, 0f);
        }
        if (m instanceof ZombieVillager zv) { // Hurleur : aucune surcouche de métier
            zv.setVillagerProfession(org.bukkit.entity.Villager.Profession.NONE);
            zv.setVillagerType(org.bukkit.entity.Villager.Type.PLAINS);
        }
    }

    private static ItemStack leather(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta lam) {
            lam.setColor(Color.fromRGB(rgb));
            it.setItemMeta(lam);
        }
        return it;
    }

    // ------------------------------------------------------------------ état

    public State state(Entity e) {
        return e == null ? null : states.get(e.getUniqueId());
    }

    public boolean ours(Entity e) {
        return e != null && e.getPersistentDataContainer().has(keyType, PersistentDataType.STRING);
    }

    public List<Mob> alive() {
        List<Mob> list = new ArrayList<>();
        for (UUID id : new ArrayList<>(states.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Mob m && m.isValid() && !m.isDead()) list.add(m);
            else states.remove(id);
        }
        return list;
    }

    public void killAll() {
        for (Mob m : alive()) m.remove();
        states.clear();
        World w = plugin.essaiWorld();
        if (w != null)
            for (Entity e : w.getEntities()) if (ours(e)) e.remove();
    }

    public void setFrozen(boolean f) {
        frozen = f;
        for (Mob m : alive()) {
            m.setAI(!f);
            if (f) m.setTarget(null);
        }
    }

    // ------------------------------------------------------------------ ouïe

    /** Un bruit au point loc : les zombies à portée qui ne poursuivent personne viennent voir. */
    public void hear(Location loc, double radius) {
        long now = System.currentTimeMillis();
        for (Mob m : alive()) {
            if (!m.getWorld().equals(loc.getWorld())) continue;
            if (m.getLocation().distanceSquared(loc) > radius * radius) continue;
            State s = state(m);
            if (s == null || m.getTarget() != null) continue;
            s.investigate = loc.clone();
            s.investigateUntil = now + 15_000;
            if (!frozen) m.getPathfinder().moveTo(loc, 1.0);
        }
    }

    /** Fusée d'appât : tout zombie à portée lâche sa cible et vient au point. */
    public void lure(Location loc, double radius, int seconds) {
        long now = System.currentTimeMillis();
        for (Mob m : alive()) {
            if (!m.getWorld().equals(loc.getWorld())) continue;
            if (m.getLocation().distanceSquared(loc) > radius * radius) continue;
            State s = state(m);
            if (s == null) continue;
            m.setTarget(null);
            s.investigate = loc.clone();
            s.investigateUntil = now + seconds * 1000L;
            s.lureUntil = now + seconds * 1000L;
            if (!frozen) m.getPathfinder().moveTo(loc, 1.2);
        }
    }

    // ------------------------------------------------------------------ boucle (toutes les 10 ticks)

    void tick() {
        if (frozen) return;
        long now = System.currentTimeMillis();
        for (Mob m : alive()) {
            State s = state(m);
            if (s == null) continue;
            if (s.investigate != null) {
                if (now > s.investigateUntil || m.getLocation().distanceSquared(s.investigate) < 4) s.investigate = null;
                else if (m.getTarget() == null || now < s.lureUntil) m.getPathfinder().moveTo(s.investigate, now < s.lureUntil ? 1.2 : 1.0);
            }
            switch (s.type) {
                case CRACHEUR -> spitTick(m, s, now);
                case GONFLE -> bloatTick(m, s);
                case BRUTE -> plugin.salle().bruteNear(m, s);
                default -> { }
            }
        }
    }

    private void spitTick(Mob m, State s, long now) {
        if (!(m.getTarget() instanceof Player p) || now - s.lastSpit < 3000) return;
        double d = p.getLocation().distance(m.getLocation());
        if (d < 3 || d > 16 || !m.hasLineOfSight(p)) return;
        s.lastSpit = now;
        Location eye = m.getEyeLocation();
        Vector dir = p.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        dir.setY(dir.getY() + 0.08 * d / 8);
        LlamaSpit spit = m.launchProjectile(LlamaSpit.class, dir.normalize().multiply(1.1));
        spit.getPersistentDataContainer().set(keySpit, PersistentDataType.BYTE, (byte) 1);
        m.getWorld().playSound(eye, Sound.ENTITY_LLAMA_SPIT, 1f, 0.6f);
    }

    private void bloatTick(Mob m, State s) {
        if (s.fusing) return;
        Player near = null;
        for (Player p : m.getWorld().getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            if (p.getLocation().distanceSquared(m.getLocation()) < 2.2 * 2.2) { near = p; break; }
        }
        if (near == null) return;
        s.fusing = true;
        m.setGlowing(true);
        m.getWorld().playSound(m.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1f, 0.7f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> explodeBloat(m), 25L);
    }

    void explodeBloat(Mob m) {
        if (!m.isValid() || m.isDead()) return;
        if (frozen) {
            // Figé pendant la mèche : on attend qu'il soit défigé.
            Bukkit.getScheduler().runTaskLater(plugin, () -> explodeBloat(m), 10L);
            return;
        }
        Location loc = m.getLocation();
        State s = state(m);
        float power = s != null && s.elite ? 3.5f : 2.6f;
        states.remove(m.getUniqueId());
        m.remove();
        // Explosion sans casser de blocs ni mettre le feu.
        loc.getWorld().createExplosion(loc, power, false, false);
        loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc.clone().add(0, 1, 0), 40, 1, 0.6, 1, 0.1);
        plugin.bruit().emit(loc, 64, null);
    }

    // ------------------------------------------------------------------ événements

    /** WorldGuard (mobs.block-plugin-spawning) annule les apparitions par plugin : on rétablit les nôtres. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (ours(e.getEntity()) && plugin.inCombat(e.getEntity().getWorld())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        if (ours(e.getEntity()) && plugin.inCombat(e.getEntity().getWorld())) e.setCancelled(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (ours(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        // Pas de brûlure au soleil (le feu des gravures et des bouteilles passe par setFireTicks, pas par cet événement).
        if (ours(e.getEntity()) && e.getClass() == EntityCombustEvent.class) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!ours(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        states.remove(e.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (!ours(e.getEntity()) || !plugin.inCombat(e.getEntity().getWorld())) return;
        State s = state(e.getEntity());
        if (s == null) return;
        long now = System.currentTimeMillis();
        if (now < s.lureUntil && e.getTarget() != null) {
            e.setCancelled(true);
            return;
        }
        if (s.type == Type.HURLEUR && e.getTarget() instanceof Player p && now - s.lastScream > 8000) {
            s.lastScream = now;
            scream((Mob) e.getEntity(), p);
        }
    }

    /** Le Hurleur crie : tout zombie à 30 blocs prend le joueur pour cible. */
    private void scream(Mob m, Player p) {
        Location loc = m.getEyeLocation();
        World w = m.getWorld();
        w.playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 1.6f);
        w.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT, 2f, 0.5f);
        w.spawnParticle(Particle.CLOUD, loc, 30, 0.6, 0.4, 0.6, 0.15);
        plugin.bruit().hitMessage(p, "§c§lUn Hurleur a crié ! §7Les zombies à 30 blocs arrivent.");
        for (Mob o : alive()) {
            if (o == m || o.getLocation().distanceSquared(loc) > 30 * 30) continue;
            State os = state(o);
            if (os != null) os.lastScream = Math.max(os.lastScream, System.currentTimeMillis()); // pas de cri en chaîne
            o.setTarget(p);
        }
        plugin.bruit().emit(loc, 30, null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onZombieHit(EntityDamageByEntityEvent e) {
        if (!ours(e.getDamager()) || !(e.getEntity() instanceof Player p)) return;
        State s = state(e.getDamager());
        if (s == null) return;
        if (s.type == Type.COUREUR) {
            // 0.2.2 (Thibault : « hémorragie hyper violente ») : plus systématique, et dégâts espacés
            // (Armes.bleedTick : 1 dégât toutes les `saignement.intervalle` s pour les joueurs).
            double chance = plugin.getConfig().getDouble(s.elite ? "saignement.chance-elite" : "saignement.chance", s.elite ? 0.5 : 0.35);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                plugin.armes().bleed(p, plugin.getConfig().getInt(s.elite ? "saignement.duree-elite" : "saignement.duree", s.elite ? 12 : 9));
                plugin.bruit().hitMessage(p, "§cUn Coureur t'a griffé : tu saignes !");
            }
        } else if (s.type == Type.RAMPANT) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            plugin.bruit().hitMessage(p, "§cUn Rampant t'attrape les jambes !");
        }
    }

    @EventHandler
    public void onSpitHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof LlamaSpit spit) || !spit.getPersistentDataContainer().has(keySpit, PersistentDataType.BYTE))
            return;
        Location loc = spit.getLocation();
        if (e.getHitBlock() != null) {
            Block b = e.getHitBlock();
            loc = b.getLocation().add(0.5, 1, 0.5);
        }
        AreaEffectCloud cloud = loc.getWorld().spawn(loc, AreaEffectCloud.class, c -> {
            c.setRadius(2.5f);
            c.setDuration(80);
            c.setWaitTime(0);
            c.setReapplicationDelay(20);
            c.setColor(Color.fromRGB(0x7FB238));
            c.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 60, 0), true);
            c.addScoreboardTag("eemirefuge_acide");
        });
        cloud.setRadiusPerTick(-0.01f);
    }

    // ------------------------------------------------------------------ boutons

    public Location randomSpawn(Location min, Location max) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double x = min.getX() + r.nextDouble() * (max.getX() - min.getX());
        double z = min.getZ() + r.nextDouble() * (max.getZ() - min.getZ());
        return new Location(min.getWorld(), x, min.getY(), z, r.nextFloat() * 360, 0);
    }
}
