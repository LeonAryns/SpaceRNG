package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class OptionsHolder implements InventoryHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    // Row 1: the two rolling toggles. Row 2: one aura toggle per tier.
    public static final int SOUND_SLOT = 11;
    public static final int ANIMATION_SLOT = 15;
    public static final int AURA_EPIC_SLOT = 20;
    public static final int AURA_LEGENDARY_SLOT = 22;
    public static final int AURA_MYTHICAL_SLOT = 24;
}
