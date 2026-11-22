package fr.eemicraft.refuge.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clics des menus du mode. Règle : rien ne bouge, sauf dans les cases de rangement déclarées par un menu
 * (réserve). Maj-clic, glisser, touches numériques, double-clic, jeter, cloner : refusés partout ailleurs.
 */
final class Menus implements Listener {

    private final Map<UUID, Long> last = new HashMap<>();

    private static Menu menu(Inventory top) {
        return top != null && top.getHolder(false) instanceof Menu m ? m : null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Menu m = menu(e.getView().getTopInventory());
        if (m == null) return;
        if (!(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();
        int from = m.storageFrom();
        if (from >= 0 && storageAllowed(e, m, top, raw, from)) {
            m.storageChanged();
            return;
        }
        e.setCancelled(true);
        if (from >= 0 && e.isCancelled() && m.customStorageClick(e)) {
            m.storageChanged();
            return;
        }
        ClickType c = e.getClick();
        if (c != ClickType.LEFT && c != ClickType.RIGHT && c != ClickType.SHIFT_LEFT && c != ClickType.SHIFT_RIGHT) return;
        long now = System.currentTimeMillis();
        Long prev = last.get(p.getUniqueId());
        if (prev != null && now - prev < 180) return; // double envoi / clic trop rapide
        last.put(p.getUniqueId(), now);
        if (raw >= 0 && raw < top.getSize()) m.clickTop(p, raw, c);
        else if (raw >= top.getSize() && e.getClickedInventory() != null) {
            ItemStack it = e.getCurrentItem();
            if (it != null && !it.isEmpty() && !Ui.isUi(it)) m.clickBottom(p, e.getSlot(), it, c);
        }
    }

    /** Clic accepté tel quel dans un menu de rangement ? (sinon il sera annulé) */
    private static boolean storageAllowed(InventoryClickEvent e, Menu m, Inventory top, int raw, int from) {
        InventoryAction a = e.getAction();
        switch (a) {
            case CLONE_STACK, DROP_ALL_CURSOR, DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT, UNKNOWN, NOTHING,
                 COLLECT_TO_CURSOR, HOTBAR_MOVE_AND_READD -> {
                return false;
            }
            default -> { }
        }
        if (raw < 0) return false; // hors de la fenêtre
        ItemStack cur = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (Ui.isUi(cur) || Ui.isUi(cursor)) return false;
        if (raw < top.getSize()) {
            if (raw < from) return false; // en-tête (boutons)
            if (a == InventoryAction.HOTBAR_SWAP) {
                ItemStack hb = e.getHotbarButton() >= 0 ? e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) : null;
                return !Ui.isUi(hb);
            }
            return true;
        }
        // dans le sac du joueur : maj-clic géré à la main (ne doit jamais tomber dans l'en-tête)
        return a != InventoryAction.MOVE_TO_OTHER_INVENTORY;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        Menu m = menu(e.getView().getTopInventory());
        if (m == null) return;
        int from = m.storageFrom();
        int size = e.getView().getTopInventory().getSize();
        if (from < 0 || Ui.isUi(e.getOldCursor())) {
            for (int s : e.getRawSlots())
                if (s < size) {
                    e.setCancelled(true);
                    return;
                }
            return;
        }
        for (int s : e.getRawSlots())
            if (s < from) {
                e.setCancelled(true);
                return;
            }
        m.storageChanged();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Menu m = menu(e.getInventory());
        if (m != null && e.getPlayer() instanceof Player p) m.closed(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
        Menu m = menu(e.getPlayer().getOpenInventory().getTopInventory());
        if (m != null) m.closed(e.getPlayer());
    }

    static void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (menu(p.getOpenInventory().getTopInventory()) != null) p.closeInventory();
    }
}
