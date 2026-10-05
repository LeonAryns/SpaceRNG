package com.spacerng.solrng.player;

/**
 * One purchase on the Prestige Upgrades board, bought with the Prestige
 * Points a prestige awards.
 *
 * Points are the only thing prestige gives you that you choose how to
 * spend, which is what makes prestiging a decision rather than a button.
 */
public class PrestigeUpgrade {

    /**
     * V346: prestige buys the Secret Realm and nothing else, Leon's call.
     *
     * The five effects above the line are not on the board any more. The
     * enum keeps them because the code that reads them is still there and
     * answers 1.0 or 0.0 with nothing bought, and because a save that
     * still carries one is refunded rather than broken.
     */
    public enum Effect {
        LUCK_BONUS,     // retired V346
        TOKEN_BONUS,    // retired V346
        MONEY_BONUS,    // retired V346
        SHARD_BONUS,    // retired V346
        NOVA_ODDS,      // retired V346
        SECRET_CHANCE,  // x(1+value) every secret's chance in the Secret Realm per level (V291)
        REALM_ACCESS,   // the Secret Realm itself: one level, and /realm opens
        REALM_LUCK,     // x(1+value) Luck INSIDE the realm per level, on top of the flat 100%
        REALM_SPEED,    // +value roll Speed inside the realm per level
        REALM_TIME      // +value seconds you may stay after the realm closes, per level
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
    /** An upgrade id that has to be bought first, or empty (V346). */
    private final String requires;

    public PrestigeUpgrade(String id, String display, String icon, int slot, Effect effect,
                           double perLevel, int maxLevel, int costPoints, String unit) {
        this(id, display, icon, slot, effect, perLevel, maxLevel, costPoints, unit, "");
    }

    public PrestigeUpgrade(String id, String display, String icon, int slot, Effect effect,
                           double perLevel, int maxLevel, int costPoints, String unit,
                           String requires) {
        this.requires = requires == null ? "" : requires;
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

    public String getRequires() {
        return requires;
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
                || effect == Effect.MONEY_BONUS
                || effect == Effect.SECRET_CHANCE;
    }

    /** The compounding total at a given level, as a multiplier. */
    public double multiplierAt(int level) {
        return Math.pow(1.0 + perLevel, Math.max(0, Math.min(level, maxLevel)));
    }
}
