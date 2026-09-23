package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/**
 * Marks a /cosmetics screen and remembers which of the three it is: the
 * hub, the titles or the name colours. One holder for all three, the way
 * SkillTreeHolder serves both trees.
 */
public class CosmeticsHolder implements MenuHolder {

    public static final String HUB = "hub";
    public static final String TITLES = "titles";
    public static final String COLOURS = "colours";

    private Inventory inventory;
    private String section = HUB;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }
}
