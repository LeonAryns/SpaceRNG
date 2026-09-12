package com.spacerng.solrng.perk;

import org.bukkit.ChatColor;

/**
 * Every stat a perk can grant, and how it is displayed.
 *
 * The value scale a perk carries is a FRACTION - 0.25 means 25% or
 * whichever unit the stat is measured in - so the same math folds every
 * stat down to one number in the config's ceilings table.
 *
 * The suffix separates the unit: PCT reads as a percentage, FLAT reads
 * as a scalar (e.g. milliseconds off a roll), and CHANCE also reads as
 * a percentage but is described as "chance".
 */
public enum PerkStat {

    LUCK_PERCENT("Luck", ChatColor.GREEN, Kind.PCT),
    MONEY_PERCENT("Money", ChatColor.GOLD, Kind.PCT),
    COINS_PERCENT("Coins", ChatColor.YELLOW, Kind.PCT),
    SHINY_PERCENT("Shiny Chance", ChatColor.AQUA, Kind.PCT),
    RARE_BAND_PUSH("Rare Band Push", ChatColor.LIGHT_PURPLE, Kind.PCT),
    ROLL_SPEED_FLAT("Roll Speed", ChatColor.YELLOW, Kind.FLAT),
    BONUS_ROLL_PERCENT("Bonus Roll", ChatColor.LIGHT_PURPLE, Kind.CHANCE),
    INSTANT_ROLL_PERCENT("Instant Roll", ChatColor.AQUA, Kind.CHANCE),
    DUPLICATE_PERCENT("Duplicate Bonus", ChatColor.GOLD, Kind.PCT),
    CONVERT_PERCENT("Convert Bonus", ChatColor.AQUA, Kind.CHANCE),
    GOLDEN_CROP_PERCENT("Golden Crop", ChatColor.GOLD, Kind.PCT),
    CROP_YIELD_PERCENT("Crop Yield", ChatColor.GREEN, Kind.PCT),
    ENCHANT_PROC_PERCENT("Enchant Proc", ChatColor.LIGHT_PURPLE, Kind.PCT);

    public enum Kind { PCT, FLAT, CHANCE }

    private final String label;
    private final ChatColor colour;
    private final Kind kind;

    PerkStat(String label, ChatColor colour, Kind kind) {
        this.label = label;
        this.colour = colour;
        this.kind = kind;
    }

    public String label() { return label; }
    public ChatColor colour() { return colour; }
    public Kind kind() { return kind; }

    /** Human-readable value: "+25%", "+0.3x", "+1.5 chance". */
    public String format(double value) {
        return switch (kind) {
            case PCT, CHANCE -> "+" + Math.round(value * 100) + "%";
            case FLAT -> "+" + String.format("%.2fx", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        };
    }
}
