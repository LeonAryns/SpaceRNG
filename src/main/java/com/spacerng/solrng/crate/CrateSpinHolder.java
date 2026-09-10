package com.spacerng.solrng.crate;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks a crate's spinning reel, and knows which opening it belongs to. */
public class CrateSpinHolder implements InventoryHolder {

    private final CrateSpin spin;
    private Inventory inventory;

    public CrateSpinHolder(CrateSpin spin) {
        this.spin = spin;
    }

    public CrateSpin spin() {
        return spin;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
