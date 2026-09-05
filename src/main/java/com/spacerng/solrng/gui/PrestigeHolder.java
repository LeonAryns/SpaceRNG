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

    /** True when this is the upgrades board rather than the main card. */
    private boolean upgradesPage;

    public boolean isUpgradesPage() {
        return upgradesPage;
    }

    public void setUpgradesPage(boolean upgradesPage) {
        this.upgradesPage = upgradesPage;
    }
}
