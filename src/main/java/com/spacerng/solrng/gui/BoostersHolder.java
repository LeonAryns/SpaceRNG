package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/** /boosters (V264): which slot holds which stored potion. */
public class BoostersHolder implements MenuHolder {

    private Inventory inventory;
    private final Map<Integer, String> slots = new HashMap<>();

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Map<Integer, String> slots() {
        return slots;
    }
}
