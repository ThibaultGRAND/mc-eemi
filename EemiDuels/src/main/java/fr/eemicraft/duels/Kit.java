package fr.eemicraft.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;

/**
 * Kits des duels, repris des modes les plus joués des serveurs connus :
 * Classic (Hypixel Classic Duels), NoDebuff (Minemen / Lunar practice), Sumo, Boxing (Hypixel Boxing),
 * BuildUHC (Hypixel / practice BuildUHC). Objets incassables, comme sur ces serveurs.
 */
public enum Kit {
    CLASSIC("Classic", Material.IRON_SWORD, "Armure en fer, épée en fer, arc, canne à pêche",
            Arena.Type.DUEL, false, false, true),
    NODEBUFF("NoDebuff", Material.SPLASH_POTION, "Diamant Prot II, potions de soin, perles",
            Arena.Type.DUEL, false, false, true),
    SUMO("Sumo", Material.SLIME_BALL, "Pas d'arme : pousse l'autre hors de la plateforme",
            Arena.Type.SUMO, true, false, false),
    BOXING("Boxing", Material.DIAMOND_SWORD, "Pas de dégâts : le premier à 100 coups gagne",
            Arena.Type.DUEL, true, false, false),
    BUILDUHC("BuildUHC", Material.LAVA_BUCKET, "UHC : blocs, seaux, pommes d'or, pas de régénération naturelle",
            Arena.Type.DUEL, false, true, true);

    final String label;
    final Material icon;
    final String description;
    final Arena.Type arena;
    /** Aucun dégât (Sumo, Boxing) : on ne perd que par chute ou au nombre de coups. */
    final boolean noDamage;
    /** Construction autorisée dans l'arène (blocs posés retirés à la fin). */
    final boolean building;
    /** La faim baisse (kits avec de la nourriture). */
    final boolean hunger;

    Kit(String label, Material icon, String description, Arena.Type arena, boolean noDamage, boolean building, boolean hunger) {
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.arena = arena;
        this.noDamage = noDamage;
        this.building = building;
        this.hunger = hunger;
    }

    String key() {
        return name().toLowerCase();
    }

    static Kit byName(String name) {
        for (Kit k : values()) if (k.name().equalsIgnoreCase(name) || k.label.equalsIgnoreCase(name)) return k;
        return null;
    }

    ItemStack icon() {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD));
        meta.lore(List.of(Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        if (item.getType() == Material.SPLASH_POTION && meta instanceof PotionMeta pm) pm.setBasePotionType(PotionType.STRONG_HEALING);
        item.setItemMeta(meta);
        return item;
    }

    /** Vide l'inventaire et donne le kit. */
    void give(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        switch (this) {
            case CLASSIC -> {
                inv.setItem(0, item(Material.IRON_SWORD));
                inv.setItem(1, item(Material.FISHING_ROD));
                inv.setItem(2, item(Material.BOW));
                inv.setItem(8, new ItemStack(Material.ARROW, 8));
                armor(inv, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, 0);
            }
            case NODEBUFF -> {
                ItemStack sword = item(Material.DIAMOND_SWORD);
                sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
                sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
                inv.setItem(0, sword);
                inv.setItem(1, new ItemStack(Material.ENDER_PEARL, 16));
                inv.setItem(2, potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                inv.setItem(3, potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE));
                inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 64));
                inv.setItem(17, potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                inv.setItem(26, potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                for (int slot = 0; slot < 36; slot++) {
                    if (inv.getItem(slot) == null) inv.setItem(slot, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
                }
                armor(inv, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, 2);
            }
            case SUMO -> { }
            case BOXING -> {
                ItemStack sword = item(Material.DIAMOND_SWORD);
                sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
                inv.setItem(0, sword);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
            }
            case BUILDUHC -> {
                ItemStack sword = item(Material.DIAMOND_SWORD);
                sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
                ItemStack bow = item(Material.BOW);
                bow.addUnsafeEnchantment(Enchantment.POWER, 3);
                inv.setItem(0, sword);
                inv.setItem(1, item(Material.FISHING_ROD));
                inv.setItem(2, bow);
                inv.setItem(3, new ItemStack(Material.LAVA_BUCKET));
                inv.setItem(4, new ItemStack(Material.WATER_BUCKET));
                inv.setItem(5, new ItemStack(Material.GOLDEN_APPLE, 6));
                inv.setItem(6, new ItemStack(Material.COBBLESTONE, 64));
                inv.setItem(7, new ItemStack(Material.OAK_PLANKS, 64));
                inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 32));
                inv.setItem(9, new ItemStack(Material.ARROW, 20));
                inv.setItem(10, item(Material.DIAMOND_PICKAXE));
                inv.setItem(11, item(Material.DIAMOND_AXE));
                inv.setItem(12, new ItemStack(Material.LAVA_BUCKET));
                inv.setItem(13, new ItemStack(Material.WATER_BUCKET));
                inv.setItem(14, new ItemStack(Material.COBBLESTONE, 64));
                armor(inv, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, 2);
            }
        }
    }

    /**
     * Kit de l'arène FFA (1.2, demande de Thibault : NoDebuff pas adapté au FFA) : fer Prot I, épée en diamant,
     * arc et flèches, canne à pêche, pommes d'or ; le tueur est soigné et regagne une pomme et des flèches.
     */
    static void giveFfa(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        inv.setItem(0, item(Material.DIAMOND_SWORD));
        inv.setItem(1, item(Material.FISHING_ROD));
        inv.setItem(2, item(Material.BOW));
        inv.setItem(3, new ItemStack(Material.GOLDEN_APPLE, 3));
        inv.setItem(7, new ItemStack(Material.COOKED_BEEF, 16));
        inv.setItem(8, new ItemStack(Material.ARROW, 16));
        armor(inv, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, 1);
    }

    /** Récompense d'un kill en FFA : soin complet, +1 pomme d'or (5 max), +4 flèches. */
    static void ffaKillReward(Player p) {
        var hp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        PlayerInventory inv = p.getInventory();
        int apples = 0;
        for (ItemStack it : inv.getContents()) if (it != null && it.getType() == Material.GOLDEN_APPLE) apples += it.getAmount();
        if (apples < 5) inv.addItem(new ItemStack(Material.GOLDEN_APPLE));
        inv.addItem(new ItemStack(Material.ARROW, 4));
    }

    private static ItemStack item(Material m) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack potion(Material m, PotionType type) {
        ItemStack item = new ItemStack(m);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    private static void armor(PlayerInventory inv, Material head, Material chest, Material legs, Material feet, int protection) {
        ItemStack[] pieces = {item(head), item(chest), item(legs), item(feet)};
        if (protection > 0) for (ItemStack piece : pieces) piece.addUnsafeEnchantment(Enchantment.PROTECTION, protection);
        inv.setHelmet(pieces[0]);
        inv.setChestplate(pieces[1]);
        inv.setLeggings(pieces[2]);
        inv.setBoots(pieces[3]);
    }
}
