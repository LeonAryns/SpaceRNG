package com.spacerng.solrng.perk;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every stat a perk can carry, and how it is displayed.
 *
 * A perk's value for a stat is the bonus on top of 1: 0.5 reads as 1.5x.
 * Bonuses from several equipped perks add up, so 1.5x and 1.3x Luck make
 * 1.8x, and StatSources multiplies the stat by that.
 *
 * Only the rollable stats come out of the Perk Roller. The others are
 * kept so older saves and the linked account bonus still resolve; no perk
 * rolled since V133 carries them.
 */
public enum PerkStat {

    LUCK_PERCENT("Luck", ChatColor.GREEN, Material.RABBIT_FOOT, true,
            "Multiplies your Luck."),
    MONEY_PERCENT("Money", ChatColor.GOLD, Material.GOLD_INGOT, true,
            "Multiplies the Money every drop pays."),
    COINS_PERCENT("Coins", ChatColor.YELLOW, Material.SUNFLOWER, true,
            "Multiplies the Coins every crop pays."),
    SHINY_PERCENT("Shiny Chance", ChatColor.AQUA, Material.PRISMARINE_CRYSTALS, true,
            "Multiplies your chance at a shiny."),
    ROLL_SPEED_FLAT("Speed", ChatColor.YELLOW, Material.SUGAR, true,
            "Multiplies your roll Speed."),
    ENCHANT_PROC_PERCENT("Enchant Proc", ChatColor.LIGHT_PURPLE, Material.ENCHANTED_BOOK, true,
            "Multiplies how often hoe enchants fire."),
    RARE_BAND_PUSH("Rare Band Push", ChatColor.LIGHT_PURPLE, Material.AMETHYST_SHARD, false,
            "No longer rolled."),
    BONUS_ROLL_PERCENT("Bonus Roll", ChatColor.LIGHT_PURPLE, Material.ECHO_SHARD, false,
            "No longer rolled."),
    INSTANT_ROLL_PERCENT("Instant Roll", ChatColor.AQUA, Material.FIREWORK_ROCKET, false,
            "No longer rolled."),
    DUPLICATE_PERCENT("Duplicate Bonus", ChatColor.GOLD, Material.BOOK, false,
            "No longer rolled."),
    CONVERT_PERCENT("Convert Bonus", ChatColor.AQUA, Material.BLAST_FURNACE, false,
            "No longer rolled."),
    GOLDEN_CROP_PERCENT("Golden Crop", ChatColor.GOLD, Material.GOLD_NUGGET, false,
            "No longer rolled."),
    CROP_YIELD_PERCENT("Crop Yield", ChatColor.GREEN, Material.WHEAT, false,
            "No longer rolled.");

    private final String label;
    private final ChatColor colour;
    private final Material icon;
    private final boolean rollable;
    private final String description;

    PerkStat(String label, ChatColor colour, Material icon, boolean rollable, String description) {
        this.label = label;
        this.colour = colour;
        this.icon = icon;
        this.rollable = rollable;
        this.description = description;
    }

    public String label() { return label; }
    public ChatColor colour() { return colour; }
    public Material icon() { return icon; }
    public boolean rollable() { return rollable; }
    /** One short sentence on what the stat does in game. */
    public String description() { return description; }

    /** A bonus as the multiplier it gives: 0.37 reads "1.37x". */
    public String format(double bonus) {
        return multiplier(1.0 + bonus);
    }

    /** "1.5x", "1.37x", "4x". */
    public static String multiplier(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text + "x";
    }

    /** The stats the Perk Roller hands out when config names none. */
    public static List<PerkStat> rollableStats() {
        List<PerkStat> out = new ArrayList<>();
        for (PerkStat stat : values()) {
            if (stat.rollable) out.add(stat);
        }
        return out;
    }
}
