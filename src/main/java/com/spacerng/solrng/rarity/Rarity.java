package com.spacerng.solrng.rarity;

/**
 * Rarity tiers in ascending order. Ordinal order matters — it's used
 * for "min-rarity-to-broadcast" comparisons and skill-tree gating.
 */
public enum Rarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHICAL;

    /**
     * Proper-case name for display, e.g. "Common" instead of "COMMON".
     */
    public String displayName() {
        String n = name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }
}
