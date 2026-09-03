package com.spacerng.solrng.rarity;

import org.bukkit.Material;

/**
 * One entry from config.yml's "items" list.
 * baseWeight is derived from "odds" (1-in-X) so that rarer items
 * naturally get picked less often before luck is applied.
 */
public class RollableItem {

    private final Material material;
    private final String displayName;
    private final Rarity rarity;
    private final long odds;
    private final long baseWeight;

    // Large numerator keeps weights precise even for 1-in-1,000,000+ items.
    private static final long WEIGHT_NUMERATOR = 1_000_000_000L;

    public RollableItem(Material material, String displayName, Rarity rarity, long odds) {
        this.material = material;
        this.displayName = displayName;
        this.rarity = rarity;
        this.odds = odds;
        this.baseWeight = Math.max(1L, WEIGHT_NUMERATOR / odds);
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public long getOdds() {
        return odds;
    }

    public long getBaseWeight() {
        return baseWeight;
    }
}
