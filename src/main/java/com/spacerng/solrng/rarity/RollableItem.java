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
    // This item's own look (gradient/bold/etc). Null = fall back to the
    // material's natural color — styling lives per item, not per rarity.
    private final RarityStyle style;
    // Index Luck multiplier, filled in by RarityManager once every item
    // is loaded (it's relative to the other items in the same rarity).
    private double luckMultiplier = 1.0;

    // Large numerator keeps weights precise even for 1-in-1,000,000+ items.
    private static final long WEIGHT_NUMERATOR = 1_000_000_000L;

    public RollableItem(Material material, String displayName, Rarity rarity, long odds, RarityStyle style) {
        this.material = material;
        this.displayName = displayName;
        this.rarity = rarity;
        this.odds = odds;
        this.style = style;
        this.baseWeight = Math.max(1L, WEIGHT_NUMERATOR / odds);
    }

    /** Null when the item doesn't define its own colors in config. */
    public RarityStyle getStyle() {
        return style;
    }

    /**
     * This item's index Luck multiplier — derived from its odds within
     * its rarity's configured band, so the rarest item in a tier is worth
     * the most. Set by RarityManager after all items are loaded, since it
     * depends on the other items in the same rarity.
     */
    public double getLuckMultiplier() {
        return luckMultiplier;
    }

    public void setLuckMultiplier(double luckMultiplier) {
        this.luckMultiplier = luckMultiplier;
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
