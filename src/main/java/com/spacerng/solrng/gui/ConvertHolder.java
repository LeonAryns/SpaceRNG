package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ConvertHolder implements InventoryHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    // Slots 0-8 (top row) are the "drop items here" input area.
    public static final int[] INPUT_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    public static final int CONFIRM_SLOT = 22;
    public static final int AUTO_TOGGLE_ROW_START = 27; // one button per rarity, if unlocked
}
