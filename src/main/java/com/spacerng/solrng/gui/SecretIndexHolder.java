package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks /secretindex. Read only, so it needs no click route of its own. */
public class SecretIndexHolder implements MenuHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
