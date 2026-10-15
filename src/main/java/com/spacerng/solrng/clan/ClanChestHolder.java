package com.spacerng.solrng.clan;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The clan chest (V394). Deliberately NOT a MenuHolder: it is real
 * storage, so GuiListener must leave its clicks and drags alone, the way
 * it does for a /pv page.
 */
public class ClanChestHolder implements InventoryHolder {

    private final String clanId;
    private Inventory inventory;

    public ClanChestHolder(String clanId) {
        this.clanId = clanId;
    }

    public String clanId() {
        return clanId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
