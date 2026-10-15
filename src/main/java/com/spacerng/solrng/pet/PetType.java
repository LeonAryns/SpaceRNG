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
                      Rarity rarity, StatSources.Id stat, double bonus, double weight, String blurb,
                      String egg, String head) {

    /**
     * What the pet looks like on a card, worn and in the opening (V384):
     * a custom skull when {@code head} holds a texture Value from
     * minecraft-heads.com, otherwise the plain item in {@code icon}.
     * {@link com.spacerng.solrng.gui.Heads} builds each texture once.
     */
    public org.bukkit.inventory.ItemStack item() {
        org.bukkit.inventory.ItemStack skull = com.spacerng.solrng.gui.Heads.textured(head);
        return skull != null ? skull : new org.bukkit.inventory.ItemStack(icon);
    }

    /** The gradient stops in the shape Lore wants them. */
    public String[] stops() {
        return colors.toArray(new String[0]);
    }

    /** What a fresh copy of this pet reads as on a tooltip. */
    public String boostText() {
        return boostText(1.0, PetInstance.allowed(stat));
    }

    /**
     * The same line for an owned copy, with its level and shiny already
     * folded in by {@link PetUpgrades}, pointed at whichever stat the
     * owner picked. V324: a multiplier, not a percentage, Leon's call.
     */
    public String boostText(double multiplier, StatSources.Id pointedAt) {
        StatSources.Id at = PetInstance.allowed(pointedAt == null ? stat : pointedAt);
        return multiText(multiplier, at) + " " + statName(at);
    }

    /**
     * The share of a pet's boost a stat gets (V397, Leon): Shiny takes 10%,
     * every other stat all of it. pets.shiny-share in config.
     */
    public static double share(StatSources.Id stat) {
        if (stat != StatSources.Id.SHINY) return 1.0;
        return org.bukkit.plugin.java.JavaPlugin.getPlugin(com.spacerng.solrng.SolRNGPlugin.class)
                .getConfig().getDouble("pets.shiny-share", 0.10);
    }

    /** A stat's name in a list of choices: "Shiny Boost (10%)" (V397). */
    public static String choiceName(StatSources.Id stat) {
        double share = share(stat);
        return statName(stat) + (share < 1.0 ? " (" + Math.round(share * 100) + "%)" : "");
    }

    /** The number for a pet pointed at a stat, its share already taken. */
    public String multiText(double multiplier, StatSources.Id stat) {
        return String.format("%.2f", 1.0 + bonus * multiplier * share(stat)) + "x";
    }

    /** Just the number: "1.64x". Always two decimals, so a column lines up. */
    public String multiText(double multiplier) {
        return String.format("%.2f", 1.0 + bonus * multiplier) + "x";
    }

    /**
     * V397: the stat a fresh copy pays, never Enchant Proc. Types that
     * start on it in config pay Luck.
     */
    public String statName() {
        return statName(PetInstance.allowed(stat));
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
            case GEMS -> "Gems";
        };
    }

}
