package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

public class OptionsHolder implements MenuHolder {
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    // V310: four of your own settings on the first row, three rarity
    // ladders on the second. It used to be sixteen separate switches over
    // three rows, one per rarity per question, which Leon read as clutter
    // ("dan is het niet zo druk"). Each ladder is a Stepper: the three
    // groups all answered the same question, from which tier up.
    public static final int SOUND_SLOT = 10;
    public static final int ANIMATION_SLOT = 12;
    public static final int WORN_AURA_SLOT = 14;
    public static final int OWN_AURA_SLOT = 16;
    public static final int AURA_STEP_SLOT = 20;
    public static final int SHOUT_STEP_SLOT = 22;
    public static final int DROP_STEP_SLOT = 24;
}
