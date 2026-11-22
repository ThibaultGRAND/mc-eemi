package fr.eemicraft.abysse;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu (inventaire) à boutons. Tout clic est annulé ; un bouton reçoit le type de clic. {@link #bottom}
 * reçoit les clics dans l'inventaire du joueur (pour déposer des objets dans une machine), {@link #refresh}
 * redessine le menu (appelé chaque seconde tant qu'il est ouvert).
 */
final class Menu implements InventoryHolder {

    interface Click {
        void on(Player p, ClickType type);
    }

    interface BottomClick {
        void on(Player p, int slot, ItemStack item, ClickType type);
    }

    private final Inventory inv;
    final Map<Integer, Click> buttons = new HashMap<>();
    BottomClick bottom;
    Consumer<Menu> refresh;
    /** Identifiant libre (par ex. la machine affichée) pour retrouver le menu. */
    String tag = "";

    Menu(int rows, String title) {
        inv = Bukkit.createInventory(this, rows * 9, Txt.c(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    void set(int slot, ItemStack item, Click click) {
        inv.setItem(slot, item);
        if (click == null) buttons.remove(slot);
        else buttons.put(slot, click);
    }

    void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    void clear() {
        inv.clear();
        buttons.clear();
    }

    /** Remplit les cases vides avec une vitre (cadre). */
    void fill(ItemStack pane) {
        for (int i = 0; i < inv.getSize(); i++)
            if (inv.getItem(i) == null) inv.setItem(i, pane);
    }

    // ------------------------------------------------------------------ boutons communs (2.5.0)

    /** Icônes du pack EEMICRAFT ; sans le pack : flèche, barrière, béton vert / rouge. */
    static final NamespacedKey UI_BACK = new NamespacedKey("eemicraft", "lobby/retour"), UI_CLOSE = new NamespacedKey("eemicraft", "lobby/fermer"),
            UI_YES = new NamespacedKey("eemicraft", "lobby/confirmer"), UI_NO = new NamespacedKey("eemicraft", "lobby/annuler");

    static ItemStack model(ItemStack it, NamespacedKey key) {
        var meta = it.getItemMeta();
        meta.setItemModel(key);
        it.setItemMeta(meta);
        return it;
    }

    static ItemStack backIcon(String to) {
        return model(Txt.icon(Material.ARROW, "§7Retour", to == null ? List.of() : List.of("§8" + to)), UI_BACK);
    }

    static ItemStack closeIcon() {
        return model(Txt.icon(Material.BARRIER, "§cFermer"), UI_CLOSE);
    }

    static ItemStack yes(Material m, String name, String... lore) {
        return model(Txt.icon(m, name, lore), UI_YES);
    }

    static ItemStack no(String name, String... lore) {
        return model(Txt.icon(Material.RED_CONCRETE, name, lore), UI_NO);
    }

    /** Case « Retour » de la dernière ligne (juste à gauche du centre). */
    int backSlot() {
        return inv.getSize() - 6;
    }

    /** Case « Fermer » de la dernière ligne (au centre). */
    int closeSlot() {
        return inv.getSize() - 5;
    }

    /** Convention des menus de l'Abysse : dernière ligne, Retour juste à gauche du centre (si {@code back}), Fermer au centre. */
    void nav(Click back, String backTo) {
        if (back != null) set(backSlot(), backIcon(backTo), back);
        set(closeSlot(), closeIcon(), (pl, t) -> pl.closeInventory());
    }

    /**
     * Écran de confirmation (3 lignes) : Annuler à gauche (revient à {@code back}, ou ferme), le détail au centre,
     * Confirmer à droite.
     */
    static void confirm(Player p, String title, ItemStack info, String yesName, List<String> yesLore, Click yes, Click back) {
        Menu m = new Menu(3, title);
        m.set(11, no("§cAnnuler", "§7Rien ne se passe."), back != null ? back : (pl, t) -> pl.closeInventory());
        m.set(13, info);
        m.set(15, model(Txt.icon(Material.LIME_CONCRETE, yesName, yesLore), UI_YES), yes);
        m.open(p);
    }

    void open(Player p) {
        if (refresh != null) refresh.accept(this);
        p.openInventory(inv);
    }

    void handle(InventoryClickEvent e) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory().equals(inv)) {
            Click c = buttons.get(e.getSlot());
            if (c != null) c.on(p, e.getClick());
        } else if (bottom != null) {
            bottom.on(p, e.getSlot(), e.getCurrentItem(), e.getClick());
        }
    }
}
