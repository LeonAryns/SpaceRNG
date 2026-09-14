package com.spacerng.solrng.gui;

import org.bukkit.inventory.InventoryHolder;

/**
 * Every plugin menu's holder. GuiListener uses it as the one test for "this
 * inventory is ours", so a click or a drag in a menu nobody routed yet is
 * still cancelled instead of letting an item fall into the menu and vanish
 * when it closes.
 */
public interface MenuHolder extends InventoryHolder {
}
