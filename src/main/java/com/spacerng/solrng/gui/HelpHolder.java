package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** /help (V224). Read only: every click is cancelled and does nothing. */
public class HelpHolder implements MenuHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
