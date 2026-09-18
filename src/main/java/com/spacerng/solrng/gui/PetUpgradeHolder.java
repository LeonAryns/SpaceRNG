package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks the pet upgrade menu, and remembers which pet it is showing. */
public class PetUpgradeHolder implements MenuHolder {

    private Inventory inventory;
    private final String petId;

    public PetUpgradeHolder(String petId) {
        this.petId = petId;
    }

    public String getPetId() {
        return petId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
