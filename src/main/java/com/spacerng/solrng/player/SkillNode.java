package com.spacerng.solrng.player;

public class SkillNode {

    public enum Effect {
        LUCK,
        UNLOCK_AUTO_CONVERT,
        AUTO_ROLL,
        ROLL_SPEED,
        BONUS_ROLL_CHANCE,
        UNLOCK_FARMING,
        UNLOCK_ARMOR,
        UNLOCK_POTION,
        UNLOCK_SHINY,
        UNLOCK_INDEX_LUCK
    }

    private final String id;
    private final String display;
    // Skill tree nodes are paid for with Money (Vault). Rolled drops are
    // the currency for /armor, potions and farming-hoe upgrades instead.
    private final double moneyCost;
    private final String requires; // id of required node, or null
    private final Effect effect;
    private final double value;
    // 1 = a normal one-time unlock. >1 = a leveled node — the same cost is
    // paid repeatedly (once per level, up to maxLevel), each purchase
    // adding another `value` to the effect (e.g. maxLevel 10, value 0.05
    // LUCK = +5% Luck per level, up to +50% at level 10).
    private final int maxLevel;
    // Multiplies the price after each level of a leveled node, so 1.2
    // means every level costs 20% more than the one before. 1.0 = flat.
    private final double costGrowth;

    public SkillNode(String id, String display, double moneyCost, String requires, Effect effect, double value, int maxLevel, double costGrowth) {
        this.id = id;
        this.display = display;
        this.moneyCost = moneyCost;
        this.requires = (requires == null || requires.isBlank()) ? null : requires;
        this.effect = effect;
        this.value = value;
        this.maxLevel = Math.max(1, maxLevel);
        this.costGrowth = costGrowth <= 0 ? 1.0 : costGrowth;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public double getMoneyCost() {
        return moneyCost;
    }

    public String getRequires() {
        return requires;
    }

    public Effect getEffect() {
        return effect;
    }

    public double getValue() {
        return value;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public double getCostGrowth() {
        return costGrowth;
    }

    /** What the NEXT level costs, given how many are already bought. */
    public double costAtLevel(int currentLevel) {
        return moneyCost * Math.pow(costGrowth, currentLevel);
    }
}
