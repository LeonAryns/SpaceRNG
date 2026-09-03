package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PrestigeHolder implements InventoryHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public static final int LEVEL_SLOT = 11;
    public static final int PRESTIGE_SLOT = 15;
}
