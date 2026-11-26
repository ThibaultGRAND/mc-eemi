package fr.eemicraft.iles;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.BlockTypeTagKeys;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Construction et lecture du MultiTool (composant « tool » de Paper 26.3, règles par tags mineable/*). */
final class MultiTool {

    record Level(long blocs, String nom, Material materiau, float vitesse, String tier, int efficacite) {
    }

    private final EemiIles pl;
    final NamespacedKey keyTool, keyLevel, keyActive;
    final List<Level> levels = new ArrayList<>();
    final List<TagKey<BlockType>> mineables = new ArrayList<>();

    MultiTool(EemiIles pl) {
        this.pl = pl;
        keyTool = new NamespacedKey(pl, "multitool");
        keyLevel = new NamespacedKey(pl, "niveau");
        keyActive = new NamespacedKey(pl, "actif");
    }

    void load(ConfigurationSection c) {
        levels.clear();
        mineables.clear();
        if (c == null) return;
        for (String s : c.getStringList("outils")) {
            TagKey<BlockType> t = switch (s.toLowerCase()) {
                case "pickaxe", "pioche" -> BlockTypeTagKeys.MINEABLE_PICKAXE;
                case "shovel", "pelle" -> BlockTypeTagKeys.MINEABLE_SHOVEL;
                case "axe", "hache" -> BlockTypeTagKeys.MINEABLE_AXE;
                case "hoe", "houe" -> BlockTypeTagKeys.MINEABLE_HOE;
                default -> null;
            };
            if (t == null) pl.getLogger().warning("multitool.outils : famille inconnue " + s);
            else mineables.add(t);
        }
        for (Map<?, ?> m : c.getMapList("niveaux")) {
            try {
                Material mat = Material.matchMaterial(String.valueOf(m.get("materiau")));
                if (mat == null || !mat.isItem()) mat = Material.WOODEN_PICKAXE;
                levels.add(new Level(num(m.get("blocs")).longValue(), String.valueOf(m.get("nom")), mat,
                        num(m.get("vitesse")).floatValue(), String.valueOf(m.get("tier")), num(m.get("efficacite")).intValue()));
            } catch (RuntimeException ex) {
                pl.getLogger().warning("multitool.niveaux : entree ignoree " + m + " (" + ex + ")");
            }
        }
        levels.sort((a, b) -> Long.compare(a.blocs(), b.blocs()));
        if (levels.isEmpty()) levels.add(new Level(0, "Bois", Material.WOODEN_PICKAXE, 2f, "bois", 0));
    }

    private static Number num(Object o) {
        if (o instanceof Number n) return n;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Index du niveau atteint avec ce nombre de blocs. */
    int levelFor(long count) {
        int idx = 0;
        for (int i = 0; i < levels.size(); i++) if (count >= levels.get(i).blocs()) idx = i;
        return idx;
    }

    boolean isTool(ItemStack it) {
        return it != null && !it.isEmpty() && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(keyTool, PersistentDataType.BYTE);
    }

    int levelOf(ItemStack it) {
        Integer v = it.getItemMeta().getPersistentDataContainer().get(keyLevel, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, Math.min(levels.size() - 1, v));
    }

    boolean activeOf(ItemStack it) {
        Byte b = it.getItemMeta().getPersistentDataContainer().get(keyActive, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    /** Construit l'outil au niveau donné ; inactif = mine comme une main (hors du OneBlock). */
    ItemStack build(int idx, boolean active) {
        idx = Math.max(0, Math.min(levels.size() - 1, idx));
        Level lv = levels.get(idx);
        ItemStack it = ItemStack.of(lv.materiau());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(U.c("&6&lMultiTool &8(&e" + lv.nom() + "&8)"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(U.c("&7Pioche, pelle et hache en un seul outil."));
        lore.add(U.c("&7Niveau : &e" + (idx + 1) + "/" + levels.size() + " &8(" + lv.nom() + ")"));
        lore.add(U.c("&7Vitesse : &f×" + U.dec(lv.vitesse()) + (lv.efficacite() > 0 ? " &7+ &dEfficacité " + U.roman(lv.efficacite()) : "")));
        if (idx + 1 < levels.size()) {
            Level next = levels.get(idx + 1);
            lore.add(U.c("&7Prochain palier : &f" + U.n(next.blocs()) + " blocs &7cassés sur ton île &8(" + next.nom() + ")"));
        } else {
            lore.add(U.c("&6Niveau maximum atteint !"));
        }
        lore.add(U.c("&8Monte tout seul avec les blocs de ton île."));
        lore.add(active ? U.c("&aActif dans le OneBlock.") : U.c("&cEn sommeil hors du OneBlock."));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (active && lv.efficacite() > 0) meta.addEnchant(Enchantment.EFFICIENCY, lv.efficacite(), true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyTool, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyLevel, PersistentDataType.INTEGER, idx);
        pdc.set(keyActive, PersistentDataType.BYTE, (byte) (active ? 1 : 0));
        it.setItemMeta(meta);

        Tool.Builder tb = Tool.tool().defaultMiningSpeed(1f).damagePerBlock(0);
        if (active) {
            Registry<BlockType> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK);
            TagKey<BlockType> incorrect = incorrectFor(lv.tier());
            // même ordre que les outils vanilla : d'abord « pas les bons drops » pour les blocs trop durs, puis la vitesse
            if (incorrect != null) tb.addRule(Tool.rule(reg.getTag(incorrect), null, TriState.FALSE));
            for (TagKey<BlockType> t : mineables) tb.addRule(Tool.rule(reg.getTag(t), lv.vitesse(), TriState.TRUE));
        }
        it.setData(DataComponentTypes.TOOL, tb.build());
        return it;
    }

    private static TagKey<BlockType> incorrectFor(String tier) {
        return switch (tier == null ? "" : tier.toLowerCase()) {
            case "bois", "wood", "wooden" -> BlockTypeTagKeys.INCORRECT_FOR_WOODEN_TOOL;
            case "pierre", "stone" -> BlockTypeTagKeys.INCORRECT_FOR_STONE_TOOL;
            case "cuivre", "copper" -> BlockTypeTagKeys.INCORRECT_FOR_COPPER_TOOL;
            case "fer", "iron" -> BlockTypeTagKeys.INCORRECT_FOR_IRON_TOOL;
            case "or", "gold" -> BlockTypeTagKeys.INCORRECT_FOR_GOLD_TOOL;
            case "diamant", "diamond" -> BlockTypeTagKeys.INCORRECT_FOR_DIAMOND_TOOL;
            case "netherite" -> BlockTypeTagKeys.INCORRECT_FOR_NETHERITE_TOOL;
            default -> BlockTypeTagKeys.INCORRECT_FOR_WOODEN_TOOL;
        };
    }
}
