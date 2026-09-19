package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

import java.util.stream.IntStream;

public class ConvertHolder implements MenuHolder {
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
    // Row 3: the three things you do here, left to right.
    public static final int CONFIRM_SLOT = 29;
    public static final int CONVERT_ALL_SLOT = 31;
    public static final int STORED_SLOT = 33;
    // Row 4: one auto convert switch per rarity, Common to Divine. Until
    // V159 the row ended at 42, one short, so Divine's switch did nothing.
    public static final int AUTO_TOGGLE_ROW_START = 37;
    public static final int AUTO_TOGGLE_ROW_END = 43;
    // Shinies get their own switch, deliberately apart from the rarity row.
    public static final int SHINY_TOGGLE_SLOT = 49;
}
