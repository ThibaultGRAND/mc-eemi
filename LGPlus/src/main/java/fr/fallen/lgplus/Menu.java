/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 */
package fr.fallen.lgplus;

import fr.fallen.lgplus.Tool;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class Menu
implements InventoryHolder {
    final Tool action;
    final Map<Integer, Tool> actions = new HashMap<Integer, Tool>();
    final Map<Integer, UUID> targets = new HashMap<Integer, UUID>();
    Inventory inventory;

    Menu(Tool action) {
        this.action = action;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

