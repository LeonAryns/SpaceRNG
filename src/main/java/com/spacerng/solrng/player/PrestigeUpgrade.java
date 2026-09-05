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
        LUCK_BONUS,     // +value Luck per level, added like a skill node
        TOKEN_BONUS,    // +value to the farm Token multiplier per level
        MONEY_BONUS,    // +value to roll Money per level
        SHARD_BONUS,    // +value chance of a bonus Shard per farm harvest
        NOVA_ODDS       // +value to the Nova Core's success roll per level
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

    /** The total effect at a given level. */
    public double totalAt(int level) {
        return perLevel * Math.max(0, Math.min(level, maxLevel));
    }
}
