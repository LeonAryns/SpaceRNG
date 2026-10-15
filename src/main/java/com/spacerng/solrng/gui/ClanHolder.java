package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks the /clan screens (V393). */
public class ClanHolder implements MenuHolder {

    public enum View { MAIN, TOP, UPGRADES }

    private final View view;
    private Inventory inventory;

    public ClanHolder(View view) {
        this.view = view;
    }

    public View view() {
        return view;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
