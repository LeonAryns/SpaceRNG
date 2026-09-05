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
        UNLOCK_INDEX_LUCK,
        // --- farming tree ---
        UNLOCK_CROP,        // target = crop id
        UNLOCK_SHARDS,      // farm crops start paying Shards
        UNLOCK_ENCHANT,     // target = hoe enchant id
        ENCHANT_POWER,      // target = hoe enchant id, +value per level
        TOKEN_MULTIPLIER,   // +value to the farm Token multiplier per level
        FARM_SPEED          // -value regrow time per level
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
    // Which tree this node belongs to, and where it sits in it. Both come
    // from config so a new tree is a config change rather than a code one.
    private final String tree;
    private final int slot;
    private final String icon;
    // Free-form pointer some effects need — a crop id, an enchant id.
    private final String target;

    public SkillNode(String id, String display, double moneyCost, String requires, Effect effect, double value,
                     int maxLevel, double costGrowth, String tree, int slot, String icon, String target) {
        this.id = id;
        this.display = display;
        this.moneyCost = moneyCost;
        this.requires = (requires == null || requires.isBlank()) ? null : requires;
        this.effect = effect;
        this.value = value;
        this.maxLevel = Math.max(1, maxLevel);
        this.costGrowth = costGrowth <= 0 ? 1.0 : costGrowth;
        this.tree = tree;
        this.slot = slot;
        this.icon = icon;
        this.target = (target == null || target.isBlank()) ? null : target;
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

    public String getTree() {
        return tree;
    }

    /** Inventory slot, already converted from the config's (column, row). */
    public int getSlot() {
        return slot;
    }

    public String getIcon() {
        return icon;
    }

    public String getTarget() {
        return target;
    }

    /** What the NEXT level costs, given how many are already bought. */
    public double costAtLevel(int currentLevel) {
        return moneyCost * Math.pow(costGrowth, currentLevel);
    }

    /**
     * Which wallet this node is bought from. It follows the tree rather
     * than being set per node: a tree that charged two different
     * currencies would make its prices impossible to compare at a glance.
     */
    public boolean usesTokens() {
        return "farmtree".equals(tree);
    }

    /** Converts a 1-indexed (column, row) into a 9-wide inventory slot. */
    public static int slotOf(int column, int row) {
        return (row - 1) * 9 + (column - 1);
    }
}
