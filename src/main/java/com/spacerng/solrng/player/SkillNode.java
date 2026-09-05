package com.spacerng.solrng.player;

public class SkillNode {

    public enum Effect {
        // --- general tree: stats ---
        LUCK,               // +value Luck per level
        ROLL_SPEED,         // +value Speed per level (0.02 = +2 on the 100 scale)
        BONUS_ROLL_CHANCE,  // +value chance of a free extra roll
        INSTANT_ROLL,       // +value chance the roll resolves with no animation
        SHINY_CHANCE,       // +value x base shiny chance per level
        LUCK_PER_DISCOVERY, // +value Luck for every entry found in /index
        LUCK_PER_PRESTIGE,  // +value Luck for every prestige
        STARFORGE_POWER,    // +value x the held Starforge's base Luck
        ARMOR_POWER,        // +value x the Luck from worn armor

        // --- general tree: currencies ---
        MONEY_MULTIPLIER,   // +value x Coins earned per roll
        MONEY_PER_LEVEL,    // +value x Coins for every /prestige level held
        TOKEN_GAIN,         // +value x Tokens earned from farming
        GEM_MULTIPLIER,     // +value x Gems earned from farming
        DUPLICATE_BONUS,    // +value x Coins when the roll is already in the index
        CONVERT_BONUS,      // +value chance a converted drop banks twice
        PASS_XP,            // +value x Battle Pass XP

        // --- general tree: event effects ---
        SUPERCHARGE,        // every `interval` rolls, one roll at value x Luck
        NOVA_SAFETY,        // +value chance a failed Nova climb doesn't drop you

        // --- general tree: gates ---
        AUTO_ROLL,
        UNLOCK_CONVERT,
        UNLOCK_AUTO_CONVERT,
        UNLOCK_FARMING,
        UNLOCK_ARMOR,
        UNLOCK_POTION,
        UNLOCK_SHINY,
        UNLOCK_INDEX_LUCK,
        UNLOCK_ARTIFACT,
        UNLOCK_PRIVATE_VAULT,
        UNLOCK_PASS,

        // --- farming tree ---
        UNLOCK_CROP,        // target = crop id
        UNLOCK_SHARDS,      // farm crops start paying Gems
        UNLOCK_ENCHANT,     // target = hoe enchant id
        ENCHANT_POWER,      // target = hoe enchant id, +value levels per rank
        ENCHANT_PROC,       // +value x EVERY hoe enchant's chance
        ENCHANT_CAP,        // +value to the level ceiling of every hoe enchant
        CROP_YIELD,         // target = crop id, +value x its Tokens and Gems
        TOKEN_MULTIPLIER,   // +value to the farm Token multiplier per level
        FARM_SPEED          // -value regrow time per level
    }

    private final String id;
    private final String display;
    // Skill tree nodes are paid for with Coins (Vault). Rolled drops are
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
    // Which page of the tree draws this node, 0-indexed.
    private final int page;
    private final String icon;
    // Free-form pointer some effects need — a crop id, an enchant id.
    private final String target;
    // How many rolls between triggers, for the effects that fire on a
    // count rather than continuously (SUPERCHARGE).
    private final int interval;
    // Extra prerequisites, ALL of which must be owned. `requires` alone can
    // only express one path; a page root has to demand the whole page below
    // it, not just the one branch that happens to end at the capstone.
    private final java.util.List<String> requiresAll;

    public SkillNode(String id, String display, double moneyCost, String requires, Effect effect, double value,
                     int maxLevel, double costGrowth, String tree, int slot, int page, String icon,
                     String target, int interval, java.util.List<String> requiresAll) {
        this.requiresAll = requiresAll == null ? java.util.List.of() : java.util.List.copyOf(requiresAll);
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
        this.page = Math.max(0, page);
        this.icon = icon;
        this.target = (target == null || target.isBlank()) ? null : target;
        this.interval = Math.max(0, interval);
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

    /** Every extra prerequisite, all of which must be owned. Never null. */
    public java.util.List<String> getRequiresAll() {
        return requiresAll;
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

    /** 0-indexed page of the tree menu this node is drawn on. */
    public int getPage() {
        return page;
    }

    public String getIcon() {
        return icon;
    }

    public String getTarget() {
        return target;
    }

    public int getInterval() {
        return interval;
    }

    /** Whether this node is bought once or bought level by level. */
    public boolean isLeveled() {
        return maxLevel > 1;
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
