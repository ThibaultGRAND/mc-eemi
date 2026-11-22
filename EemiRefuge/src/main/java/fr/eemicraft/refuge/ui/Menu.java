package fr.eemicraft.refuge.ui;

import fr.eemicraft.refuge.Txt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu du mode, dessiné par le pack (fond = glyphe du titre, cases = objets invisibles) ou, sans le pack, en
 * objets vanilla. Une sous-classe remplit {@link #fill()} avec {@link #button}, {@link #cell}, {@link #overlay}
 * et {@link #text}. Tous les clics sont annulés (voir {@link Menus}), sauf dans les cases de rangement d'un menu
 * qui en déclare ({@link #storageFrom()}).
 */
public abstract class Menu implements InventoryHolder {

    @FunctionalInterface
    public interface Action {
        void run(Player p, ClickType click);
    }

    protected final Player viewer;
    protected final String layout;
    protected boolean pack;
    private Inventory inv;
    private final Map<Integer, ItemStack> items = new HashMap<>();
    private final Map<Integer, Action> actions = new HashMap<>();
    private final List<String> overlays = new ArrayList<>();
    private final List<Ui.Ligne> lignes = new ArrayList<>();

    protected Menu(Player viewer, String layout) {
        this.viewer = viewer;
        this.layout = layout;
    }

    /** Titre simple (sans pack). */
    protected abstract String plainTitle();

    /** Remplit le menu (appelé à chaque ouverture). */
    protected abstract void fill();

    /** Lignes d'aide du bouton i. */
    protected abstract List<String> help();

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public Player viewer() { return viewer; }

    public Fonds.Disposition disposition() {
        return Fonds.MENUS.get(layout);
    }

    /** Ouvre (ou rouvre, pour changer le fond) le menu au tick suivant. */
    public void open() {
        Bukkit.getScheduler().runTask(Ui.plugin(), () -> {
            if (!viewer.isOnline()) return;
            if (!canOpen()) return;
            build();
            viewer.openInventory(inv);
            if (viewer.getOpenInventory().getTopInventory() != inv) openFailed();
        });
    }

    /** Dernier contrôle juste avant l'ouverture (monde, état…). */
    protected boolean canOpen() {
        return true;
    }

    private void build() {
        items.clear();
        actions.clear();
        overlays.clear();
        lignes.clear();
        pack = Ui.hasPack(viewer);
        button("fermer", Material.BARRIER, "§cFermer", List.of(), (p, c) -> p.closeInventory());
        button("info", Material.BOOK, "§bInfos", help(), (p, c) -> {
            for (String s : help()) Txt.msg(p, s);
        });
        fill();
        Fonds.Disposition d = disposition();
        Component title = pack ? Ui.title(layout, overlays, lignes) : Txt.c("§8" + plainTitle());
        inv = Bukkit.createInventory(this, d.lignes() * 9, title);
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) inv.setItem(e.getKey(), e.getValue());
        afterBuild(inv);
    }

    /** L'ouverture a été refusée (autre plugin) : défaire ce que afterBuild a réservé. */
    protected void openFailed() { }

    /** Objet d'information montré seulement sans le pack (les textes dessinés n'existent pas alors). */
    protected void plain(int slot, Material m, String name, List<String> lore) {
        if (!pack) items.put(slot, Ui.button(false, m, name, lore));
    }

    /** Pour les menus de rangement : poser le contenu réel après la construction. */
    protected void afterBuild(Inventory inv) { }

    // ------------------------------------------------------------------ remplissage

    protected void button(String id, Material fallback, String name, List<String> lore, Action a) {
        int[] slots = disposition().boutons().get(id);
        if (slots == null) return;
        ItemStack it = Ui.button(pack, fallback, name, lore);
        for (int i = 0; i < slots.length; i++) {
            // sans pack : un seul objet par bouton (les autres cases restent vides)
            if (!pack && i > 0) {
                actions.put(slots[i], a);
                continue;
            }
            items.put(slots[i], it);
            actions.put(slots[i], a);
        }
    }

    protected int cells(String list) {
        int[] s = disposition().cases().get(list);
        return s == null ? 0 : s.length;
    }

    protected int cellSlot(String list, int index) {
        return disposition().cases().get(list)[index];
    }

    protected void cell(String list, int index, ItemStack item, Action a) {
        int[] s = disposition().cases().get(list);
        if (s == null || index < 0 || index >= s.length) return;
        if (item != null) items.put(s[index], item);
        if (a != null) actions.put(s[index], a);
    }

    /** Calque d'état (« sel:jour », « ok:membres:2 »…), seulement avec le pack. */
    protected void overlay(String key) {
        overlays.add(key);
    }

    protected void text(String anchor, String s, TextColor color) {
        lignes.add(new Ui.Ligne(anchor, s, color));
    }

    // ------------------------------------------------------------------ clics

    void clickTop(Player p, int slot, ClickType click) {
        Action a = actions.get(slot);
        if (a == null) return;
        Ui.click(p);
        try {
            a.run(p, click);
        } catch (RuntimeException ex) {
            Ui.plugin().getLogger().warning("Menu " + layout + " : " + ex);
            ex.printStackTrace();
        }
    }

    /** Clic dans l'inventaire du joueur pendant que le menu est ouvert (déjà annulé). */
    protected void clickBottom(Player p, int slot, ItemStack item, ClickType click) { }

    /** Première case de rangement (-1 = aucune) : cases [storageFrom, taille) libres. */
    protected int storageFrom() {
        return -1;
    }

    /** Appelé quand un clic de rangement a été accepté (synchroniser au tick suivant). */
    protected void storageChanged() { }

    protected void closed(Player p) { }

    /** Pour un menu de rangement : laisser Menus gérer ce clic autrement (ex. maj-clic depuis le sac). */
    protected boolean customStorageClick(InventoryClickEvent e) {
        return false;
    }
}
