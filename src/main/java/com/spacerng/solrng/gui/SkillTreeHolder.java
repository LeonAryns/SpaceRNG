package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks a skill tree screen, and remembers which tree and page it shows. */
public class SkillTreeHolder implements InventoryHolder {

    private Inventory inventory;
    private String tree = "skilltree";
    private int page;

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

    public void setTree(String tree) {
        this.tree = tree;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
