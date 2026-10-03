package com.spacerng.solrng.rarity;

/**
 * Rarity tiers in ascending order. Ordinal order matters - it's used
 * for "min-rarity-to-broadcast" comparisons and skill-tree gating.
 */
public enum Rarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHICAL,
    DIVINE,
    // V319: the tier above Divine, Leon's call. The name is his pick from
    // three space words that are not Galaxy, which he is keeping for the
    // tiers after this one - so Galactic and Universal are still free and
    // the ladder has somewhere to go.
    //
    // Anything that loops Rarity.values() picked this up for free: the
    // /options and /index ladders, the index tabs, the drop messages. What
    // did NOT come free is every switch over a Rarity with no default and
    // every config map keyed by rarity name, which is what the compiler
    // and ConfigMigrator had to be walked through.
    ASTRAL;

    /**
     * Proper-case name for display, e.g. "Common" instead of "COMMON".
     */
    public String displayName() {
        String n = name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }
}
