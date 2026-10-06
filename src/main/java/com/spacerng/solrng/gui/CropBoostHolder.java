package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/** Marks the per-crop upgrade board opened from /crops. */
public class CropBoostHolder implements MenuHolder {
    private Inventory inventory;
    private String cropId = "";

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getCropId() {
        return cropId;
    }

    public void setCropId(String cropId) {
        this.cropId = cropId;
    }
}
