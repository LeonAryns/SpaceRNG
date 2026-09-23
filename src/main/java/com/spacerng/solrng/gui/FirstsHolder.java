package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks the /firsts menu. Nothing in it is clickable. */
public class FirstsHolder implements MenuHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
