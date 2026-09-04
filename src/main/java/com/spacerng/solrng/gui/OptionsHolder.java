package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class OptionsHolder implements InventoryHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    // Three toggles, evenly spread across the middle row.
    public static final int SOUND_SLOT = 11;
    public static final int ANIMATION_SLOT = 13;
    public static final int AURA_SLOT = 15;
}
