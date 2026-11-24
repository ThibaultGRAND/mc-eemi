package fr.eemicraft.casino;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Icones dessinees des menus (pack : eemicraft:casino/icone/<nom>), a la place des blocs Minecraft.
 * U.item applique automatiquement l'icone associee au materiau ; les jeux posent une icone precise quand le
 * materiau seul ne suffit pas (numeros, symboles, actions...). Actif seulement avec modeles-3d.
 */
final class Icons {
    static volatile boolean on;
    private static final Map<Material, String> BY_MAT = new EnumMap<>(Material.class);

    static {
        for (Material m : Material.values()) {
            if (m.name().endsWith("_STAINED_GLASS_PANE")) {
                BY_MAT.put(m, "vide");
            }
        }
        BY_MAT.put(Material.WHITE_STAINED_GLASS_PANE, "garder_non");
        BY_MAT.put(Material.LIME_STAINED_GLASS_PANE, "garder_oui");
        BY_MAT.put(Material.YELLOW_STAINED_GLASS_PANE, "ligne");
        BY_MAT.put(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "siege");
        BY_MAT.put(Material.WHITE_CARPET, "jeton_10");
        BY_MAT.put(Material.RED_CARPET, "jeton_50");
        BY_MAT.put(Material.BLUE_CARPET, "jeton_100");
        BY_MAT.put(Material.GREEN_CARPET, "jeton_500");
        BY_MAT.put(Material.BLACK_CARPET, "jeton_1k");
        BY_MAT.put(Material.PURPLE_CARPET, "jeton_5k");
        BY_MAT.put(Material.YELLOW_CARPET, "jeton_10k");
        BY_MAT.put(Material.SUNFLOWER, "jetons");
        BY_MAT.put(Material.BOOK, "regles");
        BY_MAT.put(Material.KNOWLEDGE_BOOK, "regles");
        BY_MAT.put(Material.GOLD_NUGGET, "mise");
        BY_MAT.put(Material.ENDER_PEARL, "lancer");
        BY_MAT.put(Material.CLOCK, "horloge");
        BY_MAT.put(Material.REPEATER, "rejouer");
        BY_MAT.put(Material.SPONGE, "effacer");
        BY_MAT.put(Material.GRAY_DYE, "desactive");
        BY_MAT.put(Material.GRAY_CONCRETE, "desactive");
        BY_MAT.put(Material.LEVER, "levier");
        BY_MAT.put(Material.NETHER_STAR, "jackpot");
        BY_MAT.put(Material.REDSTONE_TORCH, "stop");
        BY_MAT.put(Material.ARROW, "retour");
        BY_MAT.put(Material.BARRIER, "fermer");
        BY_MAT.put(Material.OAK_DOOR, "porte");
        BY_MAT.put(Material.OAK_SIGN, "message");
        BY_MAT.put(Material.SKELETON_SKULL, "croupier");
        BY_MAT.put(Material.EMERALD_BLOCK, "distribuer");
        BY_MAT.put(Material.LIME_CONCRETE, "vert");
        BY_MAT.put(Material.RED_CONCRETE, "rouge");
        BY_MAT.put(Material.YELLOW_CONCRETE, "relance");
        BY_MAT.put(Material.GOLD_BLOCK, "or");
        BY_MAT.put(Material.SHEARS, "separer");
        BY_MAT.put(Material.SHIELD, "assurance");
        BY_MAT.put(Material.TARGET, "cible");
        BY_MAT.put(Material.TNT, "tapis");
        BY_MAT.put(Material.NAME_TAG, "etiquette");
        BY_MAT.put(Material.PAPER, "info");
        BY_MAT.put(Material.HEAVY_CORE, "jeu_plinko");
        BY_MAT.put(Material.SNOWBALL, "bille");
        BY_MAT.put(Material.SLIME_BALL, "billes");
        BY_MAT.put(Material.ENDER_EYE, "oeil");
        BY_MAT.put(Material.MINECART, "cadeau_pris");
        BY_MAT.put(Material.CHEST_MINECART, "cadeau");
        BY_MAT.put(Material.AMETHYST_SHARD, "boutique");
        BY_MAT.put(Material.GOLDEN_HELMET, "couronne");
        BY_MAT.put(Material.TNT_MINECART, "jeu_poker");
        BY_MAT.put(Material.IRON_NUGGET, "pack_1");
        BY_MAT.put(Material.GOLD_INGOT, "pack_3");
    }

    private Icons() {
    }

    static String forMaterial(Material m) {
        return BY_MAT.get(m);
    }

    /** Pose l'icone nommee (si les modeles sont actifs). */
    static ItemStack icon(ItemStack it, String name) {
        if (!on || it == null || name == null) {
            return it;
        }
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new org.bukkit.NamespacedKey("eemicraft", "casino/icone/" + name));
            meta.setHideTooltip("vide".equals(name));
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Remet l'apparence Minecraft d'origine (boutique : icones choisies dans le config). */
    static ItemStack vanilla(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasItemModel()) {
            meta.setItemModel(null);
            meta.setHideTooltip(false);
            it.setItemMeta(meta);
        }
        return it;
    }
}
