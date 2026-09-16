package com.spacerng.solrng.crate;

import org.bukkit.Material;

/**
 * One line of a crate's loot table.
 *
 * `target` is whatever the type needs to know beyond an amount: the
 * consumable id for CONSUMABLE, the rarity name for DROP, "STAT:percent"
 * for BOOST, and nothing for the currencies or for TICKETS. `amount` is
 * the minutes a BOOST runs for. `icon` and `name` are optional
 * overrides; left empty, the crate works both out from the type.
 */
public record CrateReward(Type type, String target, long amount, double weight,
                          Material icon, String name) {

    public enum Type {
        COINS, GEMS, MONEY, CREDITS, TICKETS, BOOST, PERMANENT, CONSUMABLE, DROP
    }

    /** The stat a PERMANENT line adds to: LUCK or SPEED. */
    public String permanentStat() {
        return boostStat();
    }

    /**
     * How much it adds, forever. Luck counts in percent, Speed in the flat
     * points every other Speed source is measured in.
     */
    public double permanentAmount() {
        return boostPercent();
    }

    /** The stat a BOOST lifts, as the boost system names it. */
    public String boostStat() {
        int cut = target.indexOf(':');
        return cut < 0 ? target : target.substring(0, cut);
    }

    /** The percentage a BOOST adds. 20 reads as "+20%" and applies as 1.2x. */
    public double boostPercent() {
        int cut = target.indexOf(':');
        if (cut < 0) return 0.0;
        try {
            return Double.parseDouble(target.substring(cut + 1));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}
