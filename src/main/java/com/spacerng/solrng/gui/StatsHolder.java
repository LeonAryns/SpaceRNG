package com.spacerng.solrng.gui;

import com.spacerng.solrng.stats.StatSources;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marks the stats menu, and remembers who it's about and which stat is
 * open — a breakdown of somebody else's Luck has to keep being about them
 * when the back button is pressed.
 */
public class StatsHolder implements InventoryHolder {

    private Inventory inventory;
    private final UUID target;
    private final String targetName;
    private final StatSources.Id open;

    public StatsHolder(UUID target, String targetName, StatSources.Id open) {
        this.target = target;
        this.targetName = targetName;
        this.open = open;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getTarget() {
        return target;
    }

    public String getTargetName() {
        return targetName;
    }

    /** Null on the overview page. */
    public StatSources.Id getOpen() {
        return open;
    }
}
