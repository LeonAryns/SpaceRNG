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
    // Row 3: whether other people's drops at that tier are announced to
    // you at all. Two different questions - a player can want the party
    // without the chat spam, or the other way round.
    public static final int SOUND_SLOT = 11;
    public static final int ANIMATION_SLOT = 15;
    public static final int AURA_EPIC_SLOT = 19;
    public static final int AURA_LEGENDARY_SLOT = 21;
    public static final int AURA_MYTHICAL_SLOT = 23;
    public static final int AURA_DIVINE_SLOT = 25;
    public static final int SHOUT_EPIC_SLOT = 28;
    public static final int SHOUT_LEGENDARY_SLOT = 30;
    public static final int SHOUT_MYTHICAL_SLOT = 32;
    public static final int SHOUT_DIVINE_SLOT = 34;
}
