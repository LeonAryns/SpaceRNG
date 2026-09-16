package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/**
 * Marks a /pv menu. The selector lists the pages; a page holds items, so
 * the click listener has to know which of the two it is looking at before
 * it decides whether to cancel the click.
 */
public class PrivateVaultHolder implements MenuHolder {

    private Inventory inventory;
    private boolean selector;
    private int page;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isSelector() {
        return selector;
    }

    public void setSelector(boolean selector) {
        this.selector = selector;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
