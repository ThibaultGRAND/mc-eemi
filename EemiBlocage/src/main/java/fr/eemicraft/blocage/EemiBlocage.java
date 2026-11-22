package fr.eemicraft.blocage;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Blocage à l'épée façon 1.8, et rien d'autre : le combat (dégâts, recul, recharge) reste à
 * OldCombatMechanics. Dans les mondes PvP, chaque épée reçoit le composant vanilla blocks_attacks :
 * clic droit maintenu = garde, sans bouclier, dégâts (dégâts + 1) × 0,5 comme en 1.8, aucune usure,
 * une hache ne casse pas la garde. Hors de ces mondes, le composant est retiré.
 * Ne touche jamais à la vitesse d'attaque (EemiCombat 2.x la réglait et se battait avec OCM).
 */
public final class EemiBlocage extends JavaPlugin implements Listener {

    private final Set<String> worlds = new HashSet<>();
    private BlocksAttacks blocking;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worlds.addAll(getConfig().getStringList("worlds"));
        // Le composant ne sert qu'à l'animation de garde : réduction neutre. La 1.0.0 confiait la réduction
        // au mécanisme « bouclier » moderne et, avec le module bouclier d'OCM par-dessus, un coup bloqué
        // ne faisait plus rien (27/10/2026). La réduction 1.8 est faite dans onHit.
        blocking = BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(0f)
                .disableCooldownScale(0f)
                .addDamageReduction(DamageReduction.damageReduction()
                        .horizontalBlockingAngle(180f).base(0f).factor(0f).build())
                .itemDamage(ItemDamageFunction.itemDamageFunction().threshold(10000f).base(0f).factor(0f).build())
                .build();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(this::update), 20L, 10L);
    }

    @Override
    public void onDisable() {
        // Rendre des épées vanilla si le plugin est retiré.
        for (Player p : getServer().getOnlinePlayers()) strip(p);
    }

    private boolean pvp(Player p) {
        return worlds.contains(p.getWorld().getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Un tick plus tard : les plugins de jeu et Multiverse-Inventories changent l'inventaire au même moment.
        getServer().getScheduler().runTask(this, () -> update(event.getPlayer()));
    }

    private void update(Player player) {
        if (!player.isOnline()) return;
        if (!pvp(player)) {
            strip(player);
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || !Tag.ITEMS_SWORDS.isTagged(item.getType())) continue;
            if (blocking.equals(item.getData(DataComponentTypes.BLOCKS_ATTACKS))) continue;
            item.setData(DataComponentTypes.BLOCKS_ATTACKS, blocking);
            inv.setItem(slot, item);
        }
    }

    private static void strip(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || !Tag.ITEMS_SWORDS.isTagged(item.getType())) continue;
            if (!item.hasData(DataComponentTypes.BLOCKS_ATTACKS)) continue;
            item.resetData(DataComponentTypes.BLOCKS_ATTACKS);
            inv.setItem(slot, item);
        }
    }

    /**
     * Garde 1.8 (EntityPlayer.damageEntity) : dégâts = (1 + dégâts) × 0,5, avant l'armure.
     * HIGHEST, et chargé après OldCombatMechanics : on part des dégâts de base déjà mis en 1.8 par OCM,
     * on réduit la base (setDamage recalcule armure et résistance dessus) et on annule toute réduction
     * « bouclier » (vanilla ou module shield-damage-reduction d'OCM) pour ne pas compter la garde deux fois.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !pvp(victim) || !swordBlocking(victim)) return;
        double base = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        if (base <= 0) return;
        event.setDamage((1 + base) * getConfig().getDouble("block-factor", 0.5));
        if (event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) event.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
        if (getConfig().getBoolean("log-blocks", true)) {
            getLogger().info(String.format("Garde de %s : base %.2f -> %.2f, degats finaux %.2f",
                    victim.getName(), base, event.getDamage(), event.getFinalDamage()));
        }
    }

    /** En 1.8, isBlocking() = épée levée au clic droit, sans délai. */
    private static boolean swordBlocking(Player p) {
        return p.hasActiveItem() && Tag.ITEMS_SWORDS.isTagged(p.getActiveItem().getType());
    }

    /** En 1.8, bloquer ne repoussait pas l'attaquant : on annule le recul « bouclier » moderne. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShieldKnockback(EntityKnockbackEvent event) {
        if (event.getCause() != EntityKnockbackEvent.KnockbackCause.SHIELD_BLOCK) return;
        if (event.getEntity() instanceof Player p && pvp(p)) event.setCancelled(true);
    }
}
