package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks the /pets menus: the main screen, the storage and the index (V215). */
public class PetsHolder implements MenuHolder {

    public enum View { MAIN, STORAGE, INDEX }

    private final View view;
    private final int page;
    private Inventory inventory;

    public PetsHolder() {
        this(View.MAIN);
    }

    public PetsHolder(View view) {
        this(view, 0);
    }

    /** V366: /pets pages through storage, 36 pets a page. */
    public PetsHolder(View view, int page) {
        this.view = view;
        this.page = Math.max(0, page);
    }

    public int page() {
        return page;
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
