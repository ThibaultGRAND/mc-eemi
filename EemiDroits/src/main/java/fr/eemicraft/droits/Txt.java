package fr.eemicraft.droits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Txt {
    static final String PREFIXE = "§8[§eDroits§8] §7";

    private Txt() {}

    static String col(String s) { return s == null ? "" : s.replace('&', '§'); }

    static Component c(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    static ItemStack objet(Material m, String nom, List<String> lore, boolean brille) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(nom));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(c(s));
        meta.lore(l);
        if (brille) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    /** Comme EemiCore : minuscules, sans /, sans préfixe "plugin:". */
    static String normCommande(String cmd) {
        String c = cmd.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("/")) c = c.substring(1);
        c = c.trim().replaceAll("\\s+", " ");
        int espace = c.indexOf(' ');
        String premier = espace < 0 ? c : c.substring(0, espace);
        int deuxPoints = premier.indexOf(':');
        if (deuxPoints >= 0) c = c.substring(deuxPoints + 1);
        return c;
    }
}
