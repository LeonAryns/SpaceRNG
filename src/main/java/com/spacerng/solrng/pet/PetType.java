package com.spacerng.solrng.pet;

import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.stats.StatSources;
import org.bukkit.Material;

import java.util.List;

/**
 * One pet, straight from config.
 *
 * A pet is a relic that rides in one of the three aura slots and
 * MULTIPLIES a single stat. {@code stat} is the stat a freshly hatched
 * copy starts on; since V359 the owner points it wherever they like in
 * /pets, and that choice lives on the owned {@link PetInstance}, so this
 * is the starting stat rather than the pet's whole character.
 *
 * {@code bonus} is the fraction above 1 that a fresh copy is worth, and
 * it comes from pets.multipliers by rarity rather than from the pet's
 * own entry, so the whole ladder is retuned in one block. 1.0 reads as
 * 2.00x.
 */
public record PetType(String id, String display, List<String> colors, Material icon,
                      Rarity rarity, StatSources.Id stat, double bonus, double weight, String blurb) {

    /** The gradient stops in the shape Lore wants them. */
    public String[] stops() {
        return colors.toArray(new String[0]);
    }

    /** What a fresh copy of this pet reads as on a tooltip. */
    public String boostText() {
        return boostText(1.0, stat);
    }

    /**
     * The same line for an owned copy, with its level and shiny already
     * folded in by {@link PetUpgrades}, pointed at whichever stat the
     * owner picked. V324: a multiplier, not a percentage, Leon's call.
     */
    public String boostText(double multiplier, StatSources.Id pointedAt) {
        return multiText(multiplier) + " " + statName(pointedAt == null ? stat : pointedAt);
    }

    /** Just the number: "1.64x". Always two decimals, so a column lines up. */
    public String multiText(double multiplier) {
        return String.format("%.2f", 1.0 + bonus * multiplier) + "x";
    }

    public String statName() {
        return statName(stat);
    }

    /** The one place a stat id is turned into words, so no two menus disagree. */
    public static String statName(StatSources.Id stat) {
        if (stat == null) return "Luck";
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
