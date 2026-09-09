package com.spacerng.solrng.player;

/**
 * One purchase on the Prestige Upgrades board, bought with the Prestige
 * Points a prestige awards.
 *
 * Points are the only thing prestige gives you that you choose how to
 * spend, which is what makes prestiging a decision rather than a button.
 */
public class PrestigeUpgrade {

    public enum Effect {
        LUCK_BONUS,     // x(1+value) Luck per level, compounding
        TOKEN_BONUS,    // x(1+value) farm Coins per level, compounding
        MONEY_BONUS,    // x(1+value) roll Money per level, compounding
        SHARD_BONUS,    // +value chance of a bonus Shard per farm harvest
        NOVA_ODDS       // +value to the Nova Core success roll per level
    }

    private final String id;
    private final String display;
    private final String icon;
    private final int slot;
    private final Effect effect;
    private final double perLevel;
    private final int maxLevel;
    private final int costPoints;
    private final String unit; // how perLevel reads, e.g. "%" or "x"

    public PrestigeUpgrade(String id, String display, String icon, int slot, Effect effect,
                           double perLevel, int maxLevel, int costPoints, String unit) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.slot = slot;
        this.effect = effect;
        this.perLevel = perLevel;
        this.maxLevel = Math.max(1, maxLevel);
        this.costPoints = Math.max(1, costPoints);
        this.unit = unit == null ? "" : unit;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getIcon() {
        return icon;
    }

    public int getSlot() {
        return slot;
    }

    public Effect getEffect() {
        return effect;
    }

    public double getPerLevel() {
        return perLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getCostPoints() {
        return costPoints;
    }

    public String getUnit() {
        return unit;
    }

    /** The total effect at a given level, for the additive kinds. */
    public double totalAt(int level) {
        return perLevel * Math.max(0, Math.min(level, maxLevel));
    }

    /**
     * Whether this effect scales what you already have or adds to it.
     *
     * The three that scale are the three worth prestiging for: a flat
     * +0.02 Luck is a rounding error next to a full armour set, while a
     * 1.02x on the finished number is worth the same proportion forever.
     * The two chance effects stay additive, because a probability is not
     * something you multiply your way out of.
     */
    public boolean isMultiplicative() {
        return effect == Effect.LUCK_BONUS
                || effect == Effect.TOKEN_BONUS
                || effect == Effect.MONEY_BONUS;
    }

    /** The compounding total at a given level, as a multiplier. */
    public double multiplierAt(int level) {
        return Math.pow(1.0 + perLevel, Math.max(0, Math.min(level, maxLevel)));
    }
}
