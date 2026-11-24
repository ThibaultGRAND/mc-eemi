package fr.eemicraft.casino;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Menu du casino : chaque case peut porter une action. Les objets ne bougent jamais.
 * Avec les modeles du pack, le titre porte un fond dessine (police eemicraft:casino) : espace -8, glyphe du fond
 * (176 px de large), espace -169 pour revenir au debut du titre, puis le titre en blanc.
 */
final class Gui implements InventoryHolder {
    /** Meme ordre que BACKGROUNDS dans pack_src/gen_casino_ui.py (caractere U+E101 + index). */
    static final List<String> BACKGROUNDS = List.of("main", "slots", "blackjack", "roulette", "roulette-plein", "videopoker", "fortune", "plinko",
            "cashier", "shop", "top", "pokerlist", "poker-buyin", "poker");
    private static final Key FONT = Key.key("eemicraft", "casino");

    final String kind;
    final Inventory inv;
    final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    Runnable onClose;

    Gui(String kind, int size, String title) {
        this.kind = kind;
        this.inv = Bukkit.createInventory(this, size, title(kind, title));
    }

    private static Component title(String kind, String title) {
        int idx = BACKGROUNDS.indexOf(kind);
        if (!Icons.on || idx < 0) {
            return U.c(title);
        }
        String glyph = "" + (char) (0xE101 + idx) + "";
        String t = title.startsWith("&8") ? "&f" + title.substring(2) : title;
        return Component.text(glyph).font(FONT).color(NamedTextColor.WHITE).append(U.c(t).font(Key.key("minecraft", "default")));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    void set(int slot, ItemStack it, Consumer<ClickType> action) {
        if (slot < 0 || slot >= inv.getSize()) {
            return;
        }
        inv.setItem(slot, it);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    void fill(Material m) {
        ItemStack p = U.pane(m);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, p);
        }
        actions.clear();
    }
}
