package com.spacerng.solrng.pet;

import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.stats.StatSources;
import org.bukkit.Material;

import java.util.List;

/**
 * One pet, straight from config.
 *
 * A pet is a relic that rides in one of the three aura slots and pays a
 * percentage on a single stat. One stat each, on purpose: a pet that
 * gives a bit of everything is impossible to compare against the next
 * one, and a player picking three of them should be making a choice.
 */
public record PetType(String id, String display, List<String> colors, Material icon,
                      Rarity rarity, StatSources.Id stat, double percent, double weight, String blurb) {

    /** The gradient stops in the shape Lore wants them. */
    public String[] stops() {
        return colors.toArray(new String[0]);
    }

    /** What a fresh copy of this pet reads as on a tooltip. */
    public String boostText() {
        return boostText(1.0);
    }

    /**
     * The same line for an owned copy, with its rarity and tier already
     * folded in by {@link PetUpgrades}.
     */
    public String boostText(double multiplier) {
        return "+" + trim(percent * multiplier * 100.0) + "% " + statName();
    }

    public String statName() {
        return switch (stat) {
            case LUCK -> "Luck";
            case SPEED -> "Speed";
            case MONEY -> "Money";
            case COINS -> "Coins";
            case ENCHANT -> "Enchant Proc";
            case SHINY -> "Shiny Boost";
        };
    }

    private static String trim(double value) {
        String text = String.format("%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
