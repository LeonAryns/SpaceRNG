package com.spacerng.solrng.gui;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class IndexHolder implements MenuHolder {
    private Inventory inventory;
    // null = showing every rarity; otherwise filtered to just this one.
    private Rarity filter;
    private int page;
    // V311: which collection the grid is scoring. It was a shiny on/off
    // before the secret index joined the same block.
    private IndexGui.Mode mode = IndexGui.Mode.NORMAL;

    public IndexGui.Mode getMode() {
        return mode;
    }

    public void setMode(IndexGui.Mode mode) {
        this.mode = mode == null ? IndexGui.Mode.NORMAL : mode;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isShinyView() {
        return mode == IndexGui.Mode.SHINY;
    }

    public void setShinyView(boolean shinyView) {
        this.mode = shinyView ? IndexGui.Mode.SHINY : IndexGui.Mode.NORMAL;
    }

    public Rarity getFilter() {
        return filter;
    }

    public void setFilter(Rarity filter) {
        this.filter = filter;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
