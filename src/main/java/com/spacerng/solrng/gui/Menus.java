package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.function.Supplier;

/**
 * Opens a menu on the next tick rather than immediately.
 *
 * Every command in this plugin can be fired by something other than a
 * player typing it - a Citizens NPC's command trait is the one that
 * matters. When a command runs inside an interact event, the client sends
 * its own follow-up packet in the same tick and the inventory that was
 * just opened is closed again before it ever draws. Nothing errors,
 * nothing logs; the menu simply never appears.
 *
 * Deferring by a single tick puts the open outside that event, which
 * costs 50ms nobody can perceive and makes every menu work whether it was
 * typed, clicked on an NPC, or run from a command block.
 */
public final class Menus {

    private Menus() {
    }

    /** Builds and opens a menu on the next tick. */
    public static void open(SolRNGPlugin plugin, Player player, Supplier<Inventory> menu) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.openInventory(menu.get());
        });
    }
}
