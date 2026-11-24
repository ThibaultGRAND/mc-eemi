package fr.eemicraft.casino;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Petits outils communs. */
final class U {
    static final String P = "&6&l[Casino] &r";
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    private U() {
    }

    static Component c(String s) {
        return L.deserialize(s == null ? "" : s).decoration(TextDecoration.ITALIC, false);
    }

    /** Titre court (resultat d'un lancer). */
    static net.kyori.adventure.title.Title title(String main, String sub) {
        return net.kyori.adventure.title.Title.title(c(main), c(sub), net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(400)));
    }

    static void msg(CommandSender s, String m) {
        if (s != null) {
            s.sendMessage(c(P + m));
        }
    }

    static ItemStack item(Material m, String name, List<String> lore, boolean glint) {
        return item(m, 1, name, lore, glint);
    }

    static ItemStack item(Material m, int amount, String name, List<String> lore, boolean glint) {
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(c(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> l = new ArrayList<>();
                for (String s : lore) {
                    l.add(c(s));
                }
                meta.lore(l);
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return Icons.on ? Icons.icon(it, Icons.forMaterial(m)) : it;
    }

    static ItemStack head(UUID uuid, String name, List<String> lore, boolean glint) {
        ItemStack it = item(Material.PLAYER_HEAD, name, lore, glint);
        if (uuid != null && it.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            it.setItemMeta(sm);
        }
        return it;
    }

    static ItemStack pane(Material m) {
        return item(m, " ", List.of(), false);
    }

    /** 12345 -> "12 345". */
    static String fmt(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    /** Couleur d'un jeton selon sa valeur. */
    static Material chip(long v) {
        if (v >= 10000) {
            return Material.YELLOW_CARPET;
        }
        if (v >= 5000) {
            return Material.PURPLE_CARPET;
        }
        if (v >= 1000) {
            return Material.BLACK_CARPET;
        }
        if (v >= 500) {
            return Material.GREEN_CARPET;
        }
        if (v >= 100) {
            return Material.BLUE_CARPET;
        }
        if (v >= 50) {
            return Material.RED_CARPET;
        }
        return Material.WHITE_CARPET;
    }
}
