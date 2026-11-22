package fr.eemicraft.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat 1.8 dans les mondes PvP d'EEMICRAFT, recopié des règles du code de la 1.8.9
 * (EntityPlayer.attackTargetEntityWithCurrentItem, EntityLivingBase.attackEntityFrom / knockBack,
 * EntityPlayer.damageEntity, applyArmorCalculations, applyPotionDamageCalculations, FoodStats).
 * Le dégât d'un coup de joueur sur joueur est recalculé entièrement ici ; les réductions modernes
 * (armure avec résistance, invulnérabilité, bouclier) sont mises à zéro pour ne pas s'ajouter.
 * Remplace OldCombatMechanics dans ces mondes : ne pas garder les deux actifs au même endroit.
 */
public final class EemiCombat extends JavaPlugin implements Listener {

    /** Fenêtre d'invulnérabilité 1.8 : hurtResistantTime 20, coups réduits tant qu'il reste plus de 10 ticks. */
    private static final int IFRAME_WINDOW = 10;
    /** Vitesse d'attaque qui supprime la recharge (même valeur qu'OldCombatMechanics). */
    private static final double NO_COOLDOWN_SPEED = 40.0;
    private static final double VANILLA_SPEED = 4.0;

    /** Dégâts 1.8 (attribut attackDamage total, poing compris) par arme. Absent = coup de poing (1). */
    private final Map<Material, Double> weaponDamage = new HashMap<>();
    private final Set<String> worlds = new HashSet<>();
    /** Dernier coup « plein » reçu par joueur (pour la fenêtre d'invulnérabilité 1.8). */
    private final Map<UUID, Hurt> hurts = new HashMap<>();
    /** Minuteur de régénération 1.8 (FoodStats.foodTimer) par joueur. */
    private final Map<UUID, Integer> regenTimers = new HashMap<>();
    /** Recul 1.8 calculé pendant l'événement de dégâts, posé sur le recul vanilla du même tick. */
    private final Map<UUID, Pending> pendingKnockback = new HashMap<>();
    /** Joueurs qui voient le détail de chaque coup (/combat debug). */
    private final Set<UUID> debug = new HashSet<>();
    private BlocksAttacks blocking;

    private record Hurt(int tick, double amount) {}

    private static final class Pending {
        final Vector kb;
        final int tick;
        boolean used;

        Pending(Vector kb, int tick) {
            this.kb = kb;
            this.tick = tick;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worlds.addAll(getConfig().getStringList("worlds"));
        loadWeapons();
        // Garde à l'épée : on ne garde du composant que l'animation et l'état « bloque » ;
        // la réduction 1.8 ((dégâts + 1) / 2) est calculée dans onPlayerHit, celle-ci est neutre.
        blocking = BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(0f)
                .disableCooldownScale(0f)
                .addDamageReduction(DamageReduction.damageReduction()
                        .horizontalBlockingAngle(180f).base(0f).factor(0f).build())
                .itemDamage(ItemDamageFunction.itemDamageFunction().threshold(10000f).base(0f).factor(0f).build())
                .build();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(this::update), 20L, 10L);
        getServer().getScheduler().runTaskTimer(this, this::regenTick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (Player p : getServer().getOnlinePlayers()) setAttackSpeed(p, false);
    }

    /** Tables des ItemSword / ItemTool de la 1.8 (+1 du poing). Cuivre et netherite : interpolés. */
    private void loadWeapons() {
        put("_SWORD", 5, 6, 6, 7, 8, 9, 5);
        put("_AXE", 4, 5, 5, 6, 7, 8, 4);
        put("_PICKAXE", 3, 4, 4, 5, 6, 7, 3);
        put("_SHOVEL", 2, 3, 3, 4, 5, 6, 2);
    }

    private void put(String suffix, double wood, double stone, double copper, double iron, double diamond, double netherite, double gold) {
        String[] tiers = {"WOODEN", "STONE", "COPPER", "IRON", "DIAMOND", "NETHERITE", "GOLDEN"};
        double[] values = {wood, stone, copper, iron, diamond, netherite, gold};
        for (int i = 0; i < tiers.length; i++) {
            Material m = Material.matchMaterial(tiers[i] + suffix);
            if (m != null) weaponDamage.put(m, values[i]);
        }
    }

    private boolean pvp(Player p) {
        return worlds.contains(p.getWorld().getName());
    }

    // ------------------------------------------------------------------ état des joueurs

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        hurts.remove(id);
        regenTimers.remove(id);
        debug.remove(id);
        pendingKnockback.remove(id);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Un tick plus tard : les plugins de jeu et Multiverse-Inventories changent l'inventaire au même moment.
        getServer().getScheduler().runTask(this, () -> update(event.getPlayer()));
    }

    /** Recharge d'attaque et garde à l'épée selon le monde du joueur. */
    private void update(Player player) {
        if (!player.isOnline()) return;
        boolean pvp = pvp(player);
        setAttackSpeed(player, pvp);
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || !Tag.ITEMS_SWORDS.isTagged(item.getType())) continue;
            BlocksAttacks current = item.getData(DataComponentTypes.BLOCKS_ATTACKS);
            if (pvp && blocking.equals(current)) continue;
            if (!pvp && current == null) continue;
            if (pvp) item.setData(DataComponentTypes.BLOCKS_ATTACKS, blocking);
            else item.resetData(DataComponentTypes.BLOCKS_ATTACKS);
            inv.setItem(slot, item);
        }
    }

    private static void setAttackSpeed(Player player, boolean noCooldown) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed == null) return;
        if (noCooldown) {
            if (speed.getBaseValue() != NO_COOLDOWN_SPEED) speed.setBaseValue(NO_COOLDOWN_SPEED);
        } else if (speed.getBaseValue() == NO_COOLDOWN_SPEED) {
            speed.setBaseValue(VANILLA_SPEED);
        }
    }

    // ------------------------------------------------------------------ dégâts

    /** Toute blessure d'un joueur en monde PvP ouvre ou met à jour la fenêtre d'invulnérabilité. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !pvp(victim)) return;
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player
                && event.getCause() == DamageCause.ENTITY_ATTACK) {
            // Le recul 1.8 passe normalement par l'événement de recul vanilla (onKnockback) ; s'il n'a pas
            // eu lieu (coup bloqué par exemple), on l'applique au tick suivant.
            Pending pending = pendingKnockback.get(victim.getUniqueId());
            if (pending != null) {
                getServer().getScheduler().runTask(this, () -> {
                    if (pendingKnockback.get(victim.getUniqueId()) != pending) return;
                    pendingKnockback.remove(victim.getUniqueId());
                    if (!pending.used && pending.kb.lengthSquared() > 0 && victim.isOnline()) victim.setVelocity(pending.kb);
                });
            }
            return; // Fenêtre déjà mise à jour par onPlayerHit.
        }
        int now = Bukkit.getCurrentTick();
        Hurt last = hurts.get(victim.getUniqueId());
        double amount = event.getDamage();
        if (last == null || now - last.tick() >= IFRAME_WINDOW) hurts.put(victim.getUniqueId(), new Hurt(now, amount));
        else if (amount > last.amount()) hurts.put(victim.getUniqueId(), new Hurt(last.tick(), amount));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
        if (!pvp(victim)) return;
        if (event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true); // Pas de balayage en 1.8.
            return;
        }
        if (event.getCause() != DamageCause.ENTITY_ATTACK) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        Material type = weapon.getType();
        // Armes sans équivalent 1.8 (trident, masse, lance) : combat vanilla.
        if (!weaponDamage.containsKey(type) && isModernWeapon(type)) return;

        // --- Attaquant (attackTargetEntityWithCurrentItem) ---
        double damage = weaponDamage.getOrDefault(type, 1.0);
        PotionEffect weakness = attacker.getPotionEffect(PotionEffectType.WEAKNESS);
        if (weakness != null) damage -= 0.5 * (weakness.getAmplifier() + 1);
        PotionEffect strength = attacker.getPotionEffect(PotionEffectType.STRENGTH);
        if (strength != null) damage *= 1 + 1.3 * (strength.getAmplifier() + 1);
        damage = Math.max(0, damage);
        boolean crit = attacker.getFallDistance() > 0 && !attacker.isOnGround() && !attacker.isClimbing()
                && !attacker.isInWater() && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS)
                && !attacker.isInsideVehicle();
        if (crit && damage > 0) damage *= 1.5;
        damage += 1.25 * weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        int knockbackLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK) + (attacker.isSprinting() ? 1 : 0);

        // --- Fenêtre d'invulnérabilité (attackEntityFrom) ---
        int now = Bukkit.getCurrentTick();
        Hurt last = hurts.get(victim.getUniqueId());
        boolean fresh = last == null || now - last.tick() >= IFRAME_WINDOW;
        double amount;
        if (fresh) {
            amount = damage;
            hurts.put(victim.getUniqueId(), new Hurt(now, damage));
        } else {
            if (damage <= last.amount()) {
                event.setCancelled(true);
                return;
            }
            amount = damage - last.amount();
            hurts.put(victim.getUniqueId(), new Hurt(last.tick(), damage));
        }

        // --- Victime (damageEntity) : garde, armure, résistance, protection ---
        // 1.8 : isBlocking() = utilise un objet dont l'action est BLOCK (l'épée), sans délai.
        // Le isBlocking() de Paper suit les règles du bouclier moderne : la 2.0.0 s'y fiait et la garde
        // ne réduisait rien en jeu (signalé le 27/10/2026). On regarde directement l'objet en cours d'utilisation.
        boolean blocking = isSwordBlocking(victim);
        if (blocking && amount > 0) amount = (1 + amount) * 0.5;
        AttributeInstance armorAttr = victim.getAttribute(Attribute.ARMOR);
        double armor = armorAttr == null ? 0 : Math.min(20, Math.max(0, armorAttr.getValue()));
        amount = amount * (25 - armor) / 25;
        PotionEffect resistance = victim.getPotionEffect(PotionEffectType.RESISTANCE);
        if (resistance != null) amount = amount * Math.max(0, 25 - (resistance.getAmplifier() + 1) * 5) / 25;
        int epf = protectionFactor(victim);
        if (epf > 0) amount = amount * (25 - epf) / 25;
        amount = Math.max(0, amount);

        event.setDamage(amount);
        for (DamageModifier mod : new DamageModifier[]{DamageModifier.INVULNERABILITY_REDUCTION, DamageModifier.BLOCKING,
                DamageModifier.ARMOR, DamageModifier.RESISTANCE, DamageModifier.MAGIC, DamageModifier.FREEZING, DamageModifier.HARD_HAT}) {
            if (event.isApplicable(mod)) event.setDamage(mod, 0);
        }
        if (event.isApplicable(DamageModifier.ABSORPTION)) {
            event.setDamage(DamageModifier.ABSORPTION, -Math.min(victim.getAbsorptionAmount(), amount));
        }

        // --- Recul (knockBack + bonus de sprint / Knockback) ---
        // Vitesse finale envoyée à la victime en 1.8 : le serveur repartait d'un mouvement nul pour un joueur
        // (il restaure l'ancien mouvement après l'envoi du paquet), donc pas de moitié de vitesse à ajouter.
        double kbH = getConfig().getDouble("knockback.horizontal", 0.4);
        double kbV = getConfig().getDouble("knockback.vertical", 0.4);
        double kbExtraH = getConfig().getDouble("knockback.extra-horizontal", 0.5);
        double kbExtraV = getConfig().getDouble("knockback.extra-vertical", 0.1);
        double kbLimit = getConfig().getDouble("knockback.vertical-limit", 0.4);
        double friction = Math.max(1.0, getConfig().getDouble("knockback.friction", 2.0));
        Vector kb = new Vector();
        if (fresh) {
            // knockBack 1.8 : motion /= 2 puis + recul, plafond vertical 0,4. Partir de la vitesse actuelle
            // (comme OldCombatMechanics) : un joueur qui retombe garde sa chute, il ne remonte pas d'un coup.
            // La 2.2.0 partait d'une vitesse nulle → « trop de recul en l'air » (27/10/2026).
            kb = victim.getVelocity().clone().multiply(1.0 / friction);
            if (victim.isOnGround() && kb.getY() < 0) kb.setY(0);
            AttributeInstance resist = victim.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            double kbResist = resist == null ? 0 : resist.getValue();
            if (ThreadLocalRandom.current().nextDouble() >= kbResist) {
                double dx = attacker.getLocation().getX() - victim.getLocation().getX();
                double dz = attacker.getLocation().getZ() - victim.getLocation().getZ();
                while (dx * dx + dz * dz < 1.0E-4) {
                    dx = (Math.random() - Math.random()) * 0.01;
                    dz = (Math.random() - Math.random()) * 0.01;
                }
                double dist = Math.sqrt(dx * dx + dz * dz);
                kb.add(new Vector(-dx / dist * kbH, kbV, -dz / dist * kbH));
                if (kb.getY() > kbLimit) kb.setY(kbLimit);
            }
        }
        // En 1.8, le paquet de vitesse n'est envoyé que si velocityChanged, posé par setBeenAttacked dans la
        // branche « coup plein » d'attackEntityFrom : un coup en combo ne donne aucun recul, même en sprint.
        if (fresh && knockbackLevel > 0) {
            double yaw = Math.toRadians(attacker.getLocation().getYaw());
            kb.add(new Vector(-Math.sin(yaw) * knockbackLevel * kbExtraH, kbExtraV, Math.cos(yaw) * knockbackLevel * kbExtraH));
        }
        pendingKnockback.put(victim.getUniqueId(), new Pending(kb, now));

        if (debug.contains(attacker.getUniqueId()) || debug.contains(victim.getUniqueId())) {
            String line = String.format("§7[Combat 1.8] §f%s → %s : arme %.1f%s%s, coup %s, garde %s, armure %.0f → §c%.2f dégâts§f, recul %.2f/%.2f",
                    attacker.getName(), victim.getName(), weaponDamage.getOrDefault(type, 1.0),
                    crit ? " §ecritique ×1,5§f" : " pas critique", attacker.isSprinting() ? " §bsprint§f" : "",
                    fresh ? "plein" : "combo (différence)", blocking ? "oui" : "non", armor, amount,
                    Math.hypot(kb.getX(), kb.getZ()), kb.getY());
            if (debug.contains(attacker.getUniqueId())) attacker.sendMessage(line);
            if (debug.contains(victim.getUniqueId()) && victim != attacker) victim.sendMessage(line);
            if (!crit) {
                String why = String.format("§8  critique 1.8 : chute %.2f, au sol %s, échelle %s, eau %s",
                        attacker.getFallDistance(), attacker.isOnGround(), attacker.isClimbing(), attacker.isInWater());
                if (debug.contains(attacker.getUniqueId())) attacker.sendMessage(why);
            }
        }

        // Critique en sprintant : valable en 1.8, mais le client vanilla n'affiche rien.
        if (crit && attacker.isSprinting() && getConfig().getBoolean("crit-effects", true)) {
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.2);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1f, 1f);
        }
    }

    private static boolean isSwordBlocking(Player victim) {
        if (!victim.hasActiveItem()) return false;
        ItemStack active = victim.getActiveItem();
        return Tag.ITEMS_SWORDS.isTagged(active.getType());
    }

    private static boolean isModernWeapon(Material type) {
        String name = type.name();
        return type == Material.TRIDENT || type == Material.MACE || name.endsWith("_SPEAR");
    }

    /** EPF 1.8 contre un coup au corps à corps : Protection seulement, (int)((6 + niv²) / 3 × 0,75) par pièce. */
    private static int protectionFactor(Player victim) {
        int total = 0;
        for (ItemStack piece : victim.getInventory().getArmorContents()) {
            if (piece == null) continue;
            int level = piece.getEnchantmentLevel(Enchantment.PROTECTION);
            if (level > 0) total += (int) ((6 + level * level) / 3.0f * 0.75f);
        }
        if (total > 25) total = 25;
        int epf = ((total + 1) >> 1) + ThreadLocalRandom.current().nextInt((total >> 1) + 1);
        return Math.min(epf, 20);
    }

    /**
     * Un seul recul par coup : le recul de base vanilla (DAMAGE) est remplacé par la vitesse 1.8 calculée
     * dans onPlayerHit, tous les autres (bonus de sprint, garde, balayage) sont annulés.
     * La 2.0.0 annulait tout puis faisait setVelocity : le recul ressenti était trop fort (double envoi probable).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !pvp(victim)) return;
        if (!(event.getSourceEntity() instanceof Player)) return;
        Pending pending = pendingKnockback.get(victim.getUniqueId());
        if (pending != null && pending.tick == Bukkit.getCurrentTick()
                && event.getCause() == EntityKnockbackEvent.KnockbackCause.DAMAGE && !pending.used) {
            pending.used = true;
            // Coup en combo sans sprint : pas de recul en 1.8 (et une vitesse nulle figerait la victime).
            if (pending.kb.lengthSquared() == 0) event.setCancelled(true);
            else event.setFinalKnockback(pending.kb.clone());
            return;
        }
        switch (event.getCause()) {
            case DAMAGE, ENTITY_ATTACK, SHIELD_BLOCK, SWEEP_ATTACK -> event.setCancelled(true);
            default -> { }
        }
    }

    /** Événement de recul propre à Paper : annulé pour un joueur qui vient de prendre un coup 1.8 ce tick-ci. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPaperKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !pvp(victim)) return;
        Pending pending = pendingKnockback.get(victim.getUniqueId());
        if (pending != null && pending.tick == Bukkit.getCurrentTick()) event.setCancelled(true);
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            worlds.clear();
            worlds.addAll(getConfig().getStringList("worlds"));
            sender.sendMessage("§7[Combat 1.8] Config rechargee (recul, regen, mondes).");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande reservee aux joueurs (sauf /combat reload).");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            if (!debug.remove(p.getUniqueId())) debug.add(p.getUniqueId());
            p.sendMessage("§7[Combat 1.8] Debug " + (debug.contains(p.getUniqueId()) ? "§aactive" : "§cdesactive")
                    + "§7 : chaque coup donne ou recu s'affiche dans le chat.");
        } else {
            p.sendMessage("§7/combat debug | reload");
        }
        return true;
    }

    // ------------------------------------------------------------------ régénération

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!getConfig().getBoolean("old-regen", true)) return;
        if (event.getEntity() instanceof Player p && pvp(p)
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    /** FoodStats 1.8 : faim ≥ 18 et blessé → +1 PV toutes les 80 ticks, épuisement +3. */
    private void regenTick() {
        if (!getConfig().getBoolean("old-regen", true)) return;
        for (Player p : getServer().getOnlinePlayers()) {
            if (!pvp(p) || p.isDead()) {
                regenTimers.remove(p.getUniqueId());
                continue;
            }
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            boolean hurt = max != null && p.getHealth() < max.getValue();
            if (p.getFoodLevel() >= 18 && hurt && p.getWorld().getGameRuleValue(org.bukkit.GameRules.NATURAL_HEALTH_REGENERATION) != Boolean.FALSE) {
                int timer = regenTimers.merge(p.getUniqueId(), 1, Integer::sum);
                if (timer >= 80) {
                    p.heal(1.0, EntityRegainHealthEvent.RegainReason.REGEN);
                    p.setExhaustion(p.getExhaustion() + 3.0f);
                    regenTimers.put(p.getUniqueId(), 0);
                }
            } else {
                regenTimers.put(p.getUniqueId(), 0);
            }
        }
    }
}
