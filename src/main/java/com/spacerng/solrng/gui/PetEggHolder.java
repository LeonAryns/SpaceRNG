package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks one egg's own screen (V359): what is in it, and the buttons that open it. */
public class PetEggHolder implements MenuHolder {

    private final String eggId;
    private Inventory inventory;

    public PetEggHolder(String eggId) {
        this.eggId = eggId;
    }

    public String eggId() {
        return eggId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
