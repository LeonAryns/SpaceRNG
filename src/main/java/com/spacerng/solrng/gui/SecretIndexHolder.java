package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks /secretindex; since V291 a click on a found secret picks it. */
public class SecretIndexHolder implements MenuHolder {

    private final java.util.Map<Integer, String> slots = new java.util.HashMap<>();

    public java.util.Map<Integer, String> slots() {
        return slots;
    }

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
