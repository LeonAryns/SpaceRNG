package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks the /rngcookie ladder screen. */
public class NovaCoreHolder implements MenuHolder {
    private Inventory inventory;
    /** Which block of twenty tiers is drawn (V355). */
    private int page;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
