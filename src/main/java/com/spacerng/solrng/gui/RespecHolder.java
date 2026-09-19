package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** The respec confirmation, remembering which skill tree page to go back to. */
public class RespecHolder implements MenuHolder {

    public static final int CONFIRM_SLOT = 11;
    public static final int SUMMARY_SLOT = 13;
    public static final int CANCEL_SLOT = 15;

    private Inventory inventory;
    private final String tree;
    private final int page;

    public RespecHolder(String tree, int page) {
        this.tree = tree;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getTree() {
        return tree;
    }

    public int getPage() {
        return page;
    }
}
