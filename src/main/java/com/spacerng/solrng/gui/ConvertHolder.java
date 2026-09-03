package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.stream.IntStream;

public class ConvertHolder implements InventoryHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    // Rows 0-2 (27 slots) are the "drop items here" input area.
    public static final int[] INPUT_SLOTS = IntStream.rangeClosed(0, 26).toArray();
    public static final int CONFIRM_SLOT = 31; // row 3, centered
    public static final int AUTO_TOGGLE_ROW_START = 37; // row 4 — one button per rarity
    public static final int AUTO_TOGGLE_ROW_END = 42;
}
