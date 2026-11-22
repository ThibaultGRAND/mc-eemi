package fr.eemicraft.refuge;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Les armes de la section 7 : corps à corps (niveaux, gravures, dague furtive, hache), arc et arbalète,
 * objets à lancer, armes à feu (lancer de rayon), saignement, cibles du stand de tir.
 * Rechargement des armes à feu : touche F (échanger les mains) ; automatique quand le chargeur est vide.
 */
public final class Armes implements Listener {

    private final EemiRefuge plugin;
    private final NamespacedKey keyThrow, keyThrowLevel, keyCible;
    private final Map<UUID, Integer> bleeding = new HashMap<>();
    private final Set<UUID> reloading = new HashSet<>();
    /** Dégâts que le plugin applique lui-même (balles, couteaux, fendoir) : pas de bonus de niveau en double. */
    private boolean internalDamage;

    Armes(EemiRefuge plugin) {
        this.plugin = plugin;
        keyThrow = new NamespacedKey(plugin, "lancer");
        keyThrowLevel = new NamespacedKey(plugin, "lancer_niveau");
        keyCible = new NamespacedKey(plugin, "cible");
    }

    public NamespacedKey keyCible() {
        return keyCible;
    }

    private boolean here(Entity e) {
        return e != null && plugin.inCombat(e.getWorld());
    }

    /** Munitions infinies (levier de la salle d'essai) : seulement dans la salle d'essai. */
    private boolean inf(Player p) {
        return plugin.infiniteAmmo() && p != null && plugin.inEssai(p.getWorld());
    }

    // ------------------------------------------------------------------ dégâts : centralisés

    /** Dégâts infligés par le plugin (balle, couteau...). Les cibles du stand ne prennent rien mais affichent. */
    void hurt(Player p, LivingEntity victim, double dmg, boolean head, String weapon) {
        if (isCible(victim)) {
            cibleHit(p, victim, dmg, head, weapon);
            return;
        }
        Zombies.State st = plugin.zombies().state(victim);
        if (st != null && st.type == Zombies.Type.BLINDE && weapon != null && Catalogue.GUNS.containsKey(weapon)) dmg *= 1.5;
        internalDamage = true;
        try {
            victim.setNoDamageTicks(0);
            victim.damage(dmg, p);
        } finally {
            internalDamage = false;
        }
        if (plugin.zombies().ours(victim)) showHit(p, victim, dmg, head ? "§6TÊTE ! " : "");
    }

    void showHit(Player p, LivingEntity victim, double dmg, String extra) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String name = victim.customName() != null
                    ? net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(victim.customName())
                    : victim.getType().name();
            double max = victim.getAttribute(Attribute.MAX_HEALTH) != null ? victim.getAttribute(Attribute.MAX_HEALTH).getValue() : 20;
            double hp = victim.isDead() ? 0 : Math.max(0, victim.getHealth());
            plugin.bruit().hitMessage(p, extra + "§c-" + Txt.num(Math.round(dmg * 10) / 10.0) + " §8| " + name
                    + " §c❤ " + Txt.num(Math.round(hp * 10) / 10.0) + "§7/" + Txt.num(max)
                    + (hp <= 0 ? " §4§lMORT" : ""));
        });
    }

    // ------------------------------------------------------------------ corps à corps

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (internalDamage || !here(e.getEntity())) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        Catalogue.Def d = Catalogue.def(hand);
        double dmg = e.getDamage();
        String extra = "";
        if (d != null && d.kind == Catalogue.Kind.MELEE) {
            dmg += 0.5 * Catalogue.level(hand);
            Zombies.State st = plugin.zombies().state(victim);
            if (d.id.equals("dague") && victim instanceof Mob m && plugin.zombies().ours(m) && m.getTarget() != p && behind(p, m)) {
                dmg *= 3;
                extra = "§5§lFURTIF ×3 ! ";
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0.2);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
            }
            if (st != null && st.type == Zombies.Type.BLINDE) {
                if (Tag.ITEMS_SWORDS.isTagged(hand.getType())) dmg *= 0.5;
                else if (Tag.ITEMS_AXES.isTagged(hand.getType())) dmg *= 1.5;
            }
            if (st != null && st.type == Zombies.Type.BRUTE && d.id.equals("hache")) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 9));
                extra += "§eÉtourdie ! ";
            }
            List<String> eng = Catalogue.engravings(hand);
            if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) applyEngravings(p, victim, eng, dmg);
            if (!eng.contains("silence")) plugin.bruit().emit(p.getLocation(), 6, p);
        } else {
            plugin.bruit().emit(p.getLocation(), 6, p);
        }
        e.setDamage(dmg);
        if (plugin.zombies().ours(victim)) {
            double fin = e.getFinalDamage();
            showHit(p, victim, fin, extra);
        }
    }

    /** Flèches et carreaux : affiche les dégâts et les PV restants du zombie touché. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onArrowHit(EntityDamageByEntityEvent e) {
        if (internalDamage || !here(e.getEntity())) return;
        if (!(e.getDamager() instanceof AbstractArrow a) || !(a.getShooter() instanceof Player p)) return;
        if (e.getEntity() instanceof LivingEntity le && plugin.zombies().ours(le)) {
            BoundingBox bb = le.getBoundingBox();
            boolean head = a.getLocation().getY() >= bb.getMaxY() - bb.getHeight() * 0.25;
            showHit(p, le, e.getFinalDamage(), head ? "§6(haut du corps) " : "");
        }
    }

    /** Le joueur est-il dans le dos du zombie (plus de 90° derrière son regard) ? */
    private static boolean behind(Player p, LivingEntity m) {
        Vector facing = m.getLocation().getDirection().setY(0);
        Vector toP = p.getLocation().toVector().subtract(m.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() < 1e-6 || toP.lengthSquared() < 1e-6) return false;
        return facing.normalize().dot(toP.normalize()) < 0;
    }

    private void applyEngravings(Player p, LivingEntity victim, List<String> eng, double dmg) {
        for (String g : eng) {
            switch (g) {
                case "saignement" -> bleed(victim, 4);
                case "feu" -> victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
                case "recul" -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Vector v = victim.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() > 1e-6) victim.setVelocity(victim.getVelocity().add(v.normalize().multiply(0.9).setY(0.35)));
                });
                case "vol_de_vie" -> {
                    double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                    p.setHealth(Math.min(max, p.getHealth() + dmg * 0.2));
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 2, 0.3, 0.2, 0.3, 0);
                }
                case "fendoir" -> {
                    for (Entity o : victim.getNearbyEntities(2.5, 1.5, 2.5)) {
                        if (o == p || o == victim || !(o instanceof LivingEntity le) || !plugin.zombies().ours(o)) continue;
                        double d2 = dmg * 0.5;
                        Bukkit.getScheduler().runTask(plugin, () -> hurt(p, le, d2, false, null));
                    }
                    victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 3, 1, 0.2, 1, 0);
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ saignement

    public void bleed(LivingEntity e, int seconds) {
        bleeding.merge(e.getUniqueId(), seconds, Math::max);
    }

    public int bleedLeft(Player p) {
        return bleeding.getOrDefault(p.getUniqueId(), 0);
    }

    public void stopBleed(Player p) {
        bleeding.remove(p.getUniqueId());
    }

    /** Toutes les secondes. */
    void bleedTick() {
        for (UUID id : new ArrayList<>(bleeding.keySet())) {
            Entity e = Bukkit.getEntity(id);
            int left = bleeding.get(id) - 1;
            if (!(e instanceof LivingEntity le) || !le.isValid() || le.isDead() || !here(le)
                    || (le instanceof Player pl && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR))) {
                bleeding.remove(id);
                continue;
            }
            // 0.2.2 : un joueur ne perd 1 PV que toutes les `saignement.intervalle` (3) s ; les zombies
            // (gravure Saignement) gardent 1 dégât par seconde.
            int every = le instanceof Player ? Math.max(1, plugin.getConfig().getInt("saignement.intervalle", 3)) : 1;
            if (left % every == 0) {
                internalDamage = true;
                try {
                    le.damage(1.0);
                } finally {
                    internalDamage = false;
                }
            }
            le.getWorld().spawnParticle(Particle.DUST, le.getLocation().add(0, 1, 0), 8, 0.25, 0.4, 0.25, 0,
                    new Particle.DustOptions(Color.fromRGB(0x8A0303), 1.2f));
            if (left <= 0) bleeding.remove(id);
            else bleeding.put(id, left);
        }
    }

    // ------------------------------------------------------------------ armures

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !here(p)) return;
        int levels = 0;
        for (ItemStack a : p.getInventory().getArmorContents()) {
            Catalogue.Def d = Catalogue.def(a);
            if (d != null && d.kind == Catalogue.Kind.ARMOR) levels += Catalogue.level(a);
        }
        if (levels > 0) e.setDamage(e.getDamage() * (1 - 0.02 * levels));
    }

    // ------------------------------------------------------------------ arc et arbalète

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !here(p)) return;
        ItemStack bow = e.getBow();
        int lvl = Catalogue.level(bow);
        if (lvl > 0 && e.getProjectile() instanceof AbstractArrow a) a.setDamage(a.getDamage() + 0.5 * lvl);
        if (inf(p)) {
            e.setConsumeItem(false);
            if (e.getProjectile() instanceof AbstractArrow a) a.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        }
        plugin.bruit().emit(p.getLocation(), 5, p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoadCrossbow(EntityLoadCrossbowEvent e) {
        if (e.getEntity() instanceof Player p && here(p) && inf(p)) e.setConsumeItem(false);
    }

    // ------------------------------------------------------------------ clic droit : armes à feu, lancers, bandage

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!here(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() != null && plugin.inEssai(p.getWorld()) && plugin.salle().isInteractive(e.getClickedBlock())) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        Catalogue.Def d = Catalogue.def(hand);
        if (d == null) return;
        if (d.kind == Catalogue.Kind.GUN) {
            e.setCancelled(true);
            fire(p, hand, d.id);
        } else if (d.kind == Catalogue.Kind.THROW) {
            e.setCancelled(true);
            throwItem(p, hand, d.id);
        } else if (d.id.equals("bandage")) {
            e.setCancelled(true);
            stopBleed(p);
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + 2));
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.2f);
            plugin.bruit().hitMessage(p, "§aBandage posé : saignement arrêté, +2 PV.");
            consume(p, hand);
        }
    }

    private void consume(Player p, ItemStack hand) {
        if (inf(p) || p.getGameMode() == GameMode.CREATIVE) return;
        hand.setAmount(hand.getAmount() - 1);
    }

    // ------------------------------------------------------------------ armes à feu

    public boolean isReloading(Player p) {
        return reloading.contains(p.getUniqueId());
    }

    public int countAmmo(Player p, String ammoId) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (ammoId.equals(Catalogue.id(it))) n += it.getAmount();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (ammoId.equals(Catalogue.id(off))) n += off.getAmount();
        return n;
    }

    private void takeAmmo(Player p, String ammoId, int n) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize() && n > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (!ammoId.equals(Catalogue.id(it))) continue;
            int take = Math.min(n, it.getAmount());
            it.setAmount(it.getAmount() - take);
            n -= take;
        }
    }

    private void fire(Player p, ItemStack gunItem, String id) {
        Catalogue.Gun g = Catalogue.GUNS.get(id);
        if (isReloading(p) || p.getCooldown(Catalogue.cooldownGroup(id)) > 0) return;
        int ammo = Catalogue.ammo(gunItem);
        if (ammo <= 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1.8f);
            reload(p);
            return;
        }
        int lvl = Catalogue.level(gunItem);
        Catalogue.setAmmo(gunItem, ammo - 1);
        p.setCooldown(Catalogue.cooldownGroup(id), g.fireTicks());
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection();
        Location muzzle = eye.clone().add(look.clone().multiply(0.8)).add(rightOf(look).multiply(0.25)).add(0, -0.15, 0);
        w.spawnParticle(Particle.SMOKE, muzzle, 6, 0.05, 0.05, 0.05, 0.02);
        w.spawnParticle(Particle.FLAME, muzzle, 2, 0.02, 0.02, 0.02, 0.01);
        float pitch = id.equals("carabine") ? 0.7f : id.equals("tromblon") ? 0.5f : 1.1f;
        w.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.5f, pitch);
        w.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.9f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < g.pellets(); i++) {
            Vector dir = spread(look, g.spreadDeg(), r);
            RayTraceResult hit = w.rayTrace(eye, dir, g.range(), FluidCollisionMode.NEVER, true, 0.1,
                    en -> en != p && en instanceof LivingEntity && !(en instanceof ArmorStand) && en.isValid()
                            && !(en instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR));
            Vector end = hit != null ? hit.getHitPosition() : eye.toVector().add(dir.clone().multiply(g.range()));
            trail(w, muzzle.toVector(), end);
            if (hit == null) continue;
            if (hit.getHitEntity() instanceof LivingEntity le) {
                if (le instanceof Player) continue; // pas de tir ami
                BoundingBox bb = le.getBoundingBox();
                boolean head = hit.getHitPosition().getY() >= bb.getMaxY() - bb.getHeight() * 0.25;
                double dmg = (g.damage() + 0.5 * lvl) * (head ? g.headMult() : 1);
                hurt(p, le, dmg, head, id);
                w.spawnParticle(Particle.DAMAGE_INDICATOR, hit.getHitPosition().toLocation(w), 3, 0.1, 0.1, 0.1, 0.1);
                if (head) w.playSound(le.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.6f);
            } else if (hit.getHitBlock() != null) {
                w.spawnParticle(Particle.BLOCK, hit.getHitPosition().toLocation(w), 6, 0.05, 0.05, 0.05, 0,
                        hit.getHitBlock().getBlockData());
                if (plugin.inEssai(w) && hit.getHitBlock().getType().name().endsWith("GLASS_PANE")) plugin.salle().shootGlass(p, hit.getHitBlock());
            }
        }
        if (g.recoil() > 0) p.setVelocity(p.getVelocity().add(look.clone().multiply(-g.recoil()).setY(0.1)));
        plugin.bruit().emit(p.getLocation(), g.noise(), p);
        if (ammo - 1 <= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && id.equals(Catalogue.id(p.getInventory().getItemInMainHand()))) reload(p);
        }, g.fireTicks());
    }

    private static Vector rightOf(Vector look) {
        Vector r = new Vector(-look.getZ(), 0, look.getX());
        return r.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : r.normalize();
    }

    private static Vector spread(Vector look, double deg, ThreadLocalRandom r) {
        if (deg <= 0) return look.clone();
        double rad = Math.toRadians(deg);
        Vector v = look.clone().add(new Vector(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(rad * 0.5));
        return v.normalize();
    }

    private static void trail(World w, Vector from, Vector to) {
        Vector d = to.clone().subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        d.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0xC8C8C8), 0.5f);
        for (double t = 0.5; t < len; t += 0.5) {
            Vector p = from.clone().add(d.clone().multiply(t));
            w.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0, dust);
        }
        w.spawnParticle(Particle.CRIT, to.getX(), to.getY(), to.getZ(), 4, 0.05, 0.05, 0.05, 0.1);
    }

    /** Recharge l'arme en main : munitions du sac (ou infinies), barre de recharge vanilla sur l'objet. */
    void reload(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        String id = Catalogue.id(hand);
        Catalogue.Gun g = id == null ? null : Catalogue.GUNS.get(id);
        if (g == null || isReloading(p)) return;
        int missing = g.magazine() - Catalogue.ammo(hand);
        if (missing <= 0) {
            plugin.bruit().hitMessage(p, "§7Chargeur plein.");
            return;
        }
        boolean infinite = inf(p);
        if (!infinite && countAmmo(p, g.ammo()) <= 0) {
            plugin.bruit().hitMessage(p, "§cPlus de munitions (" + Catalogue.def(g.ammo()).name + ").");
            p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 0.6f);
            return;
        }
        int slot = p.getInventory().getHeldItemSlot();
        reloading.add(p.getUniqueId());
        p.setCooldown(Catalogue.cooldownGroup(id), g.reloadTicks());
        p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1f, 0.9f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reloading.remove(p.getUniqueId());
            if (!p.isOnline() || !here(p)) return;
            ItemStack it = p.getInventory().getItem(slot);
            if (!id.equals(Catalogue.id(it))) return;
            int need = g.magazine() - Catalogue.ammo(it);
            int got = inf(p) ? need : Math.min(need, countAmmo(p, g.ammo()));
            if (!inf(p)) takeAmmo(p, g.ammo(), got);
            Catalogue.setAmmo(it, Catalogue.ammo(it) + got);
            p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.2f);
        }, g.reloadTicks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!here(p)) return;
        String id = Catalogue.id(p.getInventory().getItemInMainHand());
        if (id != null && Catalogue.GUNS.containsKey(id)) {
            e.setCancelled(true);
            reload(p);
        }
    }

    // ------------------------------------------------------------------ objets à lancer

    private void throwItem(Player p, ItemStack hand, String id) {
        World w = p.getWorld();
        if (id.equals("tnt_meche")) {
            Location at = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6));
            w.spawn(at, TNTPrimed.class, t -> {
                t.setFuseTicks(50);
                t.setSource(p);
                t.setYield(3f);
                t.setVelocity(p.getEyeLocation().getDirection().multiply(0.9).add(new Vector(0, 0.2, 0)));
                t.addScoreboardTag("eemirefuge_tnt");
            });
            w.playSound(at, Sound.ENTITY_TNT_PRIMED, 1f, 1f);
        } else {
            ItemStack shown = hand.clone();
            shown.setAmount(1);
            Snowball s = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(id.equals("couteau_lancer") ? 1.9 : 1.2));
            s.setItem(shown);
            s.getPersistentDataContainer().set(keyThrow, PersistentDataType.STRING, id);
            s.getPersistentDataContainer().set(keyThrowLevel, PersistentDataType.INTEGER, Catalogue.level(hand));
            w.playSound(p.getLocation(), id.equals("couteau_lancer") ? Sound.ITEM_TRIDENT_THROW : Sound.ENTITY_WITCH_THROW, 1f, 1.3f);
            if (id.equals("couteau_lancer")) plugin.bruit().emit(p.getLocation(), 3, p);
        }
        consume(p, hand);
    }

    @EventHandler
    public void onThrownHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        String id = pr.getPersistentDataContainer().get(keyThrow, PersistentDataType.STRING);
        if (id == null || !here(pr)) return;
        Player p = pr.getShooter() instanceof Player pl ? pl : null;
        Location loc = pr.getLocation();
        World w = pr.getWorld();
        switch (id) {
            case "couteau_lancer" -> {
                if (e.getHitEntity() instanceof LivingEntity le && p != null && le != p && !(le instanceof Player)) {
                    Integer lvl = pr.getPersistentDataContainer().get(keyThrowLevel, PersistentDataType.INTEGER);
                    BoundingBox bb = le.getBoundingBox();
                    boolean head = loc.getY() >= bb.getMaxY() - bb.getHeight() * 0.25;
                    hurt(p, le, 5 + 0.5 * (lvl == null ? 0 : lvl), head, id);
                    w.playSound(loc, Sound.ENTITY_ARROW_HIT, 1f, 1.4f);
                } else {
                    w.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.3f, 2f);
                }
            }
            case "bouteille_incendiaire" -> {
                Location c = e.getHitBlock() != null ? e.getHitBlock().getLocation().add(0.5, 1, 0.5) : loc;
                w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.9f);
                w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
                fireZone(p, c, 3.0, 100);
                plugin.bruit().emit(c, 16, p);
            }
            case "fusee_appat" -> {
                Location c = e.getHitBlock() != null ? e.getHitBlock().getLocation().add(0.5, 1.2, 0.5) : loc;
                lureEffect(c, 15);
                plugin.zombies().lure(c, 48, 15);
                if (p != null) plugin.bruit().hitMessage(p, "§eFusée d'appât allumée : les zombies à 48 blocs y vont (15 s).");
            }
            default -> { }
        }
    }

    private void fireZone(Player p, Location c, double radius, int ticks) {
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (t[0] >= ticks || c.getWorld() == null) {
                task.cancel();
                return;
            }
            c.getWorld().spawnParticle(Particle.FLAME, c, 30, radius * 0.5, 0.15, radius * 0.5, 0.01);
            c.getWorld().spawnParticle(Particle.SMOKE, c, 6, radius * 0.5, 0.3, radius * 0.5, 0.01);
            if (t[0] % 10 == 0) {
                for (Entity en : c.getWorld().getNearbyEntities(c, radius, 1.5, radius)) {
                    if (!(en instanceof LivingEntity le) || isCible(le) || en instanceof ArmorStand || en instanceof Mannequin) continue;
                    le.setFireTicks(Math.max(le.getFireTicks(), 60));
                }
            }
            t[0] += 2;
        }, 0L, 2L);
    }

    private void lureEffect(Location c, int seconds) {
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (t[0] >= seconds * 20 || c.getWorld() == null) {
                task.cancel();
                return;
            }
            c.getWorld().spawnParticle(Particle.FIREWORK, c, 6, 0.2, 0.2, 0.2, 0.05);
            c.getWorld().spawnParticle(Particle.DUST, c, 4, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(Color.RED, 1.4f));
            if (t[0] % 20 == 0) c.getWorld().playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1.6f);
            t[0] += 5;
        }, 0L, 5L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!here(e.getEntity())) return;
        e.blockList().clear(); // jamais de bloc cassé dans la salle
        if (e.getEntity() instanceof TNTPrimed) plugin.bruit().emit(e.getLocation(), 64, null);
    }

    // ------------------------------------------------------------------ amélioration et gravure dans l'inventaire

    @EventHandler(ignoreCancelled = true)
    public void onInventoryApply(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !here(p)) return;
        ItemStack cursor = e.getCursor();
        ItemStack target = e.getCurrentItem();
        Catalogue.Def cd = Catalogue.def(cursor), td = Catalogue.def(target);
        if (cd == null || td == null) return;
        if (cd.id.equals("pierre_amelioration")) {
            if (!td.upgradable()) return;
            e.setCancelled(true);
            int lvl = (Catalogue.level(target) + 1) % 6;
            List<String> eng = new ArrayList<>(Catalogue.engravings(target));
            while (eng.size() > Catalogue.engraveSlots(lvl)) eng.remove(eng.size() - 1);
            e.setCurrentItem(Catalogue.remake(target, lvl, eng));
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
            Txt.msg(p, td.name + " passe au niveau §e+" + lvl + "§7.");
        } else if (cd.kind == Catalogue.Kind.ENGRAVING) {
            if (!td.engravable()) return;
            e.setCancelled(true);
            String key = cd.id.substring("gravure_".length());
            List<String> eng = new ArrayList<>(Catalogue.engravings(target));
            if (eng.contains(key)) {
                Txt.msg(p, "Cette arme porte déjà cette gravure.");
                return;
            }
            int lvl = Catalogue.level(target);
            if (eng.size() >= Catalogue.engraveSlots(lvl)) {
                String removed = eng.remove(0);
                Txt.msg(p, "Plus de place : la gravure " + Catalogue.ENGRAVINGS.getOrDefault(removed, removed) + " est remplacée.");
            }
            eng.add(key);
            e.setCurrentItem(Catalogue.remake(target, lvl, eng));
            if (!inf(p) && p.getGameMode() != GameMode.CREATIVE) {
                cursor.setAmount(cursor.getAmount() - 1);
                p.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
            }
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        }
    }

    /** Rien ne s'use dans la salle d'essai. */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (here(e.getPlayer())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ cibles du stand de tir

    public boolean isCible(Entity e) {
        return e != null && e.getPersistentDataContainer().has(keyCible, PersistentDataType.INTEGER);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCibleDamage(EntityDamageEvent e) {
        if (!isCible(e.getEntity())) return;
        if (e instanceof EntityDamageByEntityEvent ed) {
            Player p = null;
            String weapon = null;
            boolean head = false;
            if (ed.getDamager() instanceof Player pl) {
                p = pl;
                weapon = Catalogue.id(pl.getInventory().getItemInMainHand());
            } else if (ed.getDamager() instanceof Projectile pr && pr.getPersistentDataContainer().has(keyThrow, PersistentDataType.STRING)) {
                // Objets lancés : le coup est déjà compté par onThrownHit (la boule de neige porteuse fait 0 dégât).
                p = null;
            } else if (ed.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl) {
                p = pl;
                BoundingBox bb = e.getEntity().getBoundingBox();
                head = pr.getLocation().getY() >= bb.getMaxY() - bb.getHeight() * 0.25;
                weapon = pr instanceof AbstractArrow ? "fleche" : pr.getPersistentDataContainer().get(keyThrow, PersistentDataType.STRING);
            }
            if (p != null && !internalDamage) {
                double dmg = e.getDamage();
                if (ed.getDamager() instanceof Player) dmg += 0.5 * Catalogue.level(p.getInventory().getItemInMainHand());
                cibleHit(p, (LivingEntity) e.getEntity(), dmg, head, weapon);
            }
        }
        e.setCancelled(true);
    }

    void cibleHit(Player p, LivingEntity cible, double dmg, boolean head, String weapon) {
        Integer dist = cible.getPersistentDataContainer().get(keyCible, PersistentDataType.INTEGER);
        double real = p.getEyeLocation().distance(cible.getLocation().add(0, 1, 0));
        String txt = "§fCible " + dist + " m §8| §c" + Txt.num(Math.round(dmg * 10) / 10.0) + " dégâts"
                + (head ? " §6§lTÊTE" : " §7corps") + " §8| §7tir à " + Txt.num(Math.round(real * 10) / 10.0) + " m";
        plugin.bruit().hitMessage(p, txt);
        cible.getWorld().playSound(cible.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, head ? 2f : 1.2f);
        for (Entity en : cible.getNearbyEntities(1, 3.5, 1))
            if (en instanceof TextDisplay td && en.getScoreboardTags().contains("eemirefuge_ciblelabel"))
                td.text(Txt.c("§fCible " + dist + " m\n§c" + Txt.num(Math.round(dmg * 10) / 10.0) + " dégâts"
                        + (head ? "\n§6§lTÊTE" : "\n§7corps")));
    }
}
