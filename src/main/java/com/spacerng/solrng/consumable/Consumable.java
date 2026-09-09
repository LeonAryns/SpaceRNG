package com.spacerng.solrng.consumable;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * One redeemable item.
 *
 * A draught is a BUNDLE of stats measured in rolls, not a single
 * multiplier measured in minutes. That's the whole design: 50% Luck and
 * +10 Speed for a hundred rolls is a different decision from 250% Luck
 * and -25 Speed for ten, and being able to put a minus in one column is
 * what makes them worth choosing between.
 *
 * Luck and Speed are ADDITIVE - they join the flat pile the skill tree
 * and armor feed, rather than multiplying the total. A potion that
 * multiplied everything would be worth wildly different amounts to a new
 * player and a maxed one.
 *
 * Farm boosts stay on a clock instead, because a farm run isn't measured
 * in rolls.
 */
public record Consumable(String id, String display, Material material, List<String> colors,
                         double luck, double speed, long rolls,
                         double coinMultiplier, double enchantMultiplier, long durationSeconds,
                         double rollLuckMultiplier, long charges,
                         double permanentLuck, long freeSkills, long novaTiers,
                         Map<com.spacerng.solrng.rarity.Rarity, Long> costs,
                         String description) {

    /** A draught: additive Luck and/or Speed, counted down in rolls. */
    public boolean isDraught() {
        return rolls > 0 && (luck != 0.0 || speed != 0.0);
    }

    /** A farm boost: a multiplier on a clock. */
    public boolean isTimed() {
        return durationSeconds > 0 && (coinMultiplier > 1.0 || enchantMultiplier > 1.0);
    }

    /** Banked rolls that fire at a multiplied Luck. */
    public boolean isCharge() {
        return charges > 0 && rollLuckMultiplier > 1.0;
    }

    /** A one-shot permanent grant. */
    public boolean isPermanent() {
        return permanentLuck != 0.0;
    }

    /** A voucher for one free skill node. */
    public boolean isFreeSkill() {
        return freeSkills > 0;
    }

    /** A Nova Core: free tiers on the climb. */
    public boolean isNovaCore() {
        return novaTiers > 0;
    }

    public boolean isForSale() {
        return !costs.isEmpty();
    }

    /** "30m", "1h 30m" - how long a timed boost runs, for lore. */
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
