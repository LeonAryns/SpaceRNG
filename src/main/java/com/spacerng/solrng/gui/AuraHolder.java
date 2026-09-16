package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks the /aura menu. */
public class AuraHolder implements MenuHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
