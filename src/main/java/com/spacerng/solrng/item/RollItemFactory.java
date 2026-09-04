package com.spacerng.solrng.item;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.starforge.StarforgeManager;
import com.spacerng.solrng.starforge.StarforgeTier;
import org.bukkit.inventory.ItemStack;

/**
 * Builds a player's Starforge — the item they right-click to roll and
 * left-click to toggle Auto Roll. Shared by the /rngcore give command and
 * the first-join starter kit so both hand out the same thing.
 *
 * The item is always built for a specific tier; the actual construction
 * lives in StarforgeManager since the tier table is loaded there.
 */
public final class RollItemFactory {

    private RollItemFactory() {
    }

    /** The player's own tier, or Basic if they somehow have none. */
    public static ItemStack create(SolRNGPlugin plugin, int amount) {
        StarforgeManager starforge = plugin.getStarforgeManager();
        StarforgeTier tier = starforge.get(StarforgeManager.DEFAULT_TIER);
        if (tier == null && !starforge.getOrderedTiers().isEmpty()) {
            tier = starforge.getOrderedTiers().get(0);
        }
        if (tier == null) return new ItemStack(org.bukkit.Material.NETHER_STAR, amount);

        ItemStack item = starforge.create(tier);
        item.setAmount(amount);
        return item;
    }
}
