package com.spacerng.solrng.perk;

import org.bukkit.ChatColor;

/**
 * Every stat a perk can carry, and how it is displayed.
 *
 * A perk's value for a stat is the bonus it adds: 0.5 reads as +50% and
 * StatSources multiplies the stat by 1.5. Perks give Luck, Money, Coins,
 * Speed and Enchant proc; the other constants stay so the linked account
 * bonus and older hooks still resolve, and no perk carries them.
 */
public enum PerkStat {

    LUCK_PERCENT("Luck", ChatColor.GREEN, "More Luck on every roll."),
    MONEY_PERCENT("Money", ChatColor.GOLD, "More Money from every drop."),
    COINS_PERCENT("Coins", ChatColor.YELLOW, "More Coins from every crop."),
    ROLL_SPEED_FLAT("Speed", ChatColor.AQUA, "Your rolls finish faster."),
    ENCHANT_PROC_PERCENT("Enchant", ChatColor.LIGHT_PURPLE, "Hoe enchants fire more often."),
    SHINY_PERCENT("Shiny Chance", ChatColor.AQUA, "Not on any perk."),
    RARE_BAND_PUSH("Rare Band Push", ChatColor.LIGHT_PURPLE, "Not on any perk."),
    BONUS_ROLL_PERCENT("Bonus Roll", ChatColor.LIGHT_PURPLE, "Not on any perk."),
    INSTANT_ROLL_PERCENT("Instant Roll", ChatColor.AQUA, "Not on any perk."),
    DUPLICATE_PERCENT("Duplicate Bonus", ChatColor.GOLD, "Not on any perk."),
    CONVERT_PERCENT("Convert Bonus", ChatColor.AQUA, "Not on any perk."),
    GOLDEN_CROP_PERCENT("Golden Crop", ChatColor.GOLD, "Not on any perk."),
    CROP_YIELD_PERCENT("Crop Yield", ChatColor.GREEN, "Not on any perk.");

    private final String label;
    private final ChatColor colour;
    private final String description;

    PerkStat(String label, ChatColor colour, String description) {
        this.label = label;
        this.colour = colour;
        this.description = description;
    }

    public String label() { return label; }
    public ChatColor colour() { return colour; }
    public String description() { return description; }

    /** A bonus as the percent it adds: 0.5 reads "+50%". */
    public String format(double bonus) {
        return "+" + Math.round(bonus * 100) + "%";
    }
}
