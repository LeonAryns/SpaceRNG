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
    MYTHICAL
}
