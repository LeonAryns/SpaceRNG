package com.spacerng.solrng.gui;

import com.spacerng.solrng.rarity.RollFormat;
import org.bukkit.ChatColor;

/**
 * The four currencies, and the one place their icon and colour are
 * decided.
 *
 * A currency has to be recognisable from a glance at a sidebar line, a
 * price in a menu, and a chat message \u2014 three places that were each
 * picking their own colour before this existed.
 *
 * The glyphs are four different SHAPES, not four decorations: a note, a
 * coin, a gem, a star. Colour alone stops working the moment two
 * currencies sit on adjacent sidebar lines, and a shape stays legible at
 * one pixel of contrast. All four live in Minecraft's built-in unicode
 * font, so none of this needs a resource pack.
 *
 * Money carries two colours rather than one: the amount is the part
 * you're actually reading, so it takes the brighter green and the label
 * sits behind it in a darker one.
 */
public enum Currency {

    /** The Vault balance. Rolling pays this. */
    MONEY("Money", "\u25a0", ChatColor.DARK_GREEN, ChatColor.GREEN, false),
    /** What the farm pays, and what the farm tree is bought with. */
    COINS("Coins", "\u25cf", ChatColor.GOLD, ChatColor.YELLOW, false),
    GEMS("Gems", "\u25c6", ChatColor.AQUA, ChatColor.AQUA, false),
    // Credits are the one currency real money buys, so they get the one
    // treatment nothing else in the plugin uses. colour() stays purple for
    // bullets and accents \u2014 a rainbow bullet would just look broken.
    CREDITS("Credits", "\u272a", ChatColor.LIGHT_PURPLE, ChatColor.LIGHT_PURPLE, true);

    private final String label;
    private final String icon;
    private final ChatColor colour;
    private final ChatColor numberColour;
    private final boolean rainbow;

    Currency(String label, String icon, ChatColor colour, ChatColor numberColour, boolean rainbow) {
        this.label = label;
        this.icon = icon;
        this.colour = colour;
        this.numberColour = numberColour;
        this.rainbow = rainbow;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }

    /** The currency's own colour \u2014 bullets, labels and accents. */
    public ChatColor colour() {
        return colour;
    }

    /** The colour the amount itself takes. */
    public ChatColor numberColour() {
        return numberColour;
    }

    /** The coloured glyph on its own, for prefixing a line. */
    public String mark() {
        return rainbow ? Lore.rainbow(icon) : colour + icon;
    }

    /**
     * "1.2M Money \u25a0" \u2014 the amount in its own colour, then the label
     * and glyph in the currency's.
     *
     * The glyph trails rather than leads because Minecraft's font is
     * proportional: four different leading glyphs are four different
     * widths, so a leading icon pushes every amount into a slightly
     * different column and a stacked list looks ragged.
     */
    public String amount(long value) {
        return paint(RollFormat.abbreviate(value));
    }

    /**
     * The same readout, turned red when it's a price the player can't
     * meet \u2014 red has to mean "you can't afford this" without the
     * currency becoming unrecognisable.
     */
    public String price(long value, boolean affordable) {
        if (affordable) return amount(value);
        return ChatColor.RED + RollFormat.abbreviate(value) + " " + label + " " + icon;
    }

    /** "1,200,000 Money \u25a0" \u2014 unabbreviated, when the exact figure matters. */
    public String exact(long value) {
        return paint(String.format("%,d", value));
    }

    private String paint(String number) {
        String tail = label + " " + icon;
        if (rainbow) return Lore.rainbow(number + " " + tail);
        return numberColour + number + " " + colour + tail;
    }
}
