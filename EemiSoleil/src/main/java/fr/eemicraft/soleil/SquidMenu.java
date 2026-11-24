package fr.eemicraft.soleil;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Menu d'une epreuve (billes, dossards du pont) : le moteur annule tout clic et le transmet a l'epreuve. */
final class SquidMenu implements InventoryHolder {
    final String kind;
    final Object ref;
    Inventory inv;

    SquidMenu(String kind, Object ref) {
        this.kind = kind;
        this.ref = ref;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
