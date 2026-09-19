package com.spacerng.solrng.gui;

import org.bukkit.inventory.Inventory;

/**
 * The level buying screen for one hoe enchant, and where it was opened
 * from, so "go back" lands on the hoe menu or the same /farmtree page.
 */
public class EnchantBuyHolder implements MenuHolder {

    public static final int INFO_SLOT = 4;
    public static final int ONE_SLOT = 10;
    public static final int TEN_SLOT = 12;
    public static final int HUNDRED_SLOT = 14;
    public static final int MAX_SLOT = 16;
    public static final int BACK_SLOT = 22;

    private Inventory inventory;
    private final String enchantId;
    // Null when opened from the hoe menu, otherwise the tree page to go back to.
    private final String tree;
    private final int page;

    public EnchantBuyHolder(String enchantId, String tree, int page) {
        this.enchantId = enchantId;
        this.tree = tree;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getEnchantId() {
        return enchantId;
    }

    public String getTree() {
        return tree;
    }

    public int getPage() {
        return page;
    }
}
