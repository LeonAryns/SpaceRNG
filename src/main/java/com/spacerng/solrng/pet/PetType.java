package com.spacerng.solrng.pet;

import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.stats.StatSources;
import org.bukkit.Material;

import java.util.List;

/**
 * One pet, straight from config.
 *
 * A pet is a relic that rides in one of the three aura slots and
 * MULTIPLIES a single stat. One stat each, on purpose, and since V324
 * every stat exists at every rarity: which rarity you hatch and which
 * stat it turns out to boost are two separate rolls, so the best pet in
 * the game can still land on a stat you did not want.
 *
 * {@code bonus} is the fraction above 1 that a fresh copy is worth, and
 * it comes from pets.multipliers by rarity rather than from the pet's
 * own entry, so the whole ladder is retuned in one block. 0.64 reads as
 * 1.64x.
 */
public record PetType(String id, String display, List<String> colors, Material icon,
                      Rarity rarity, StatSources.Id stat, double bonus, double weight, String blurb) {

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
     * folded in by {@link PetUpgrades}. V324: a multiplier, not a
     * percentage, Leon's call.
     */
    public String boostText(double multiplier) {
        return multiText(multiplier) + " " + statName();
    }

    /** Just the number: "1.64x". Always two decimals, so a column lines up. */
    public String multiText(double multiplier) {
        return String.format("%.2f", 1.0 + bonus * multiplier) + "x";
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

}
