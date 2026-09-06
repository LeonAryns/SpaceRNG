package com.spacerng.solrng.consumable;

import org.bukkit.Material;

/**
 * One redeemable item: a potion, a charge, or a permanent grant.
 *
 * The distinction that matters is {@link Effect#instant()}. A timed boost
 * starts a clock; an instant one changes something once and is gone. They
 * live in the same table because to a player they're the same kind of
 * thing — something you were given, that you use.
 */
public record Consumable(String id, String display, Material material, java.util.List<String> colors,
                         Effect effect, double magnitude, long durationSeconds, long charges,
                         java.util.Map<com.spacerng.solrng.rarity.Rarity, Long> costs,
                         String description) {

    public enum Effect {
        /** Multiplies all Luck while it runs. */
        LUCK,
        /** Multiplies roll Speed while it runs. */
        SPEED,
        /** Multiplies Tokens from the farm while it runs. */
        TOKENS,
        /** Multiplies every hoe enchant's chance while it runs. */
        ENCHANT_PROC,
        /** Banks N rolls that fire at a multiplied Luck, used one at a time. */
        ROLL_CHARGE,
        /** Adds Luck permanently, on the spot. */
        PERMANENT_LUCK;

        /** Whether this happens once rather than running on a clock. */
        public boolean instant() {
            return this == PERMANENT_LUCK;
        }

        /** Whether this stores charges rather than a duration. */
        public boolean charged() {
            return this == ROLL_CHARGE;
        }

        /** The key a timed boost is stored under on the player. */
        public String key() {
            return name();
        }
    }

    /** "30m", "1h 30m" — how long the boost runs, for lore. */
    public String durationText() {
        long seconds = durationSeconds;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0 && minutes > 0) return hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}
