package fr.eemicraft.fete;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Petits outils : textes (codes &), objets, menus. */
final class U {
    static final String P = "&8[&dFête foraine&8] &7";
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    private U() {
    }

    static Component c(String s) {
        return L.deserialize(s == null ? "" : s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        if (s != null) s.sendMessage(c(P + m));
    }

    static net.kyori.adventure.title.Title title(String main, String sub) {
        return net.kyori.adventure.title.Title.title(c(main), c(sub), net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(400)));
    }

    /** 12345 -> "12 345", 1,2 M, 3,4 Md, 5,6 T. */
    static String fmt(double n) {
        if (n < 1e6) {
            String s = Long.toString((long) Math.floor(Math.max(0, n)));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
                b.append(s.charAt(i));
            }
            return b.toString();
        }
        String[] suf = {" M", " Md", " T", " Qa"};
        double v = n / 1e6;
        int k = 0;
        while (v >= 1000 && k < suf.length - 1) {
            v /= 1000;
            k++;
        }
        return String.format(java.util.Locale.FRANCE, "%.2f", v) + suf[k];
    }

    /** Tickets de la fête (la monnaie du parc). */
    static String eur(double n) {
        return fmt(n) + " ✪";
    }

    static ItemStack item(Material m, String name, List<String> lore, boolean glint) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(c(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> l = new ArrayList<>();
                for (String s : lore) l.add(c(s));
                meta.lore(l);
            }
            if (glint) meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Menu simple : actions par case. */
    static final class Gui implements InventoryHolder {
        final Inventory inv;
        final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

        Gui(int size, String title) {
            inv = Bukkit.createInventory(this, size, c(title));
        }

        void set(int slot, ItemStack it, Consumer<ClickType> a) {
            inv.setItem(slot, it);
            if (a != null) actions.put(slot, a);
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
