package com.spacerng.solrng.gui;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class IndexHolder implements InventoryHolder {
    private Inventory inventory;
    // null = showing every rarity; otherwise filtered to just this one.
    private Rarity filter;
    private int page;
    // Whether the grid is scoring shiny finds instead of ordinary ones.
    private boolean shinyView;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isShinyView() {
        return shinyView;
    }

    public void setShinyView(boolean shinyView) {
        this.shinyView = shinyView;
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
