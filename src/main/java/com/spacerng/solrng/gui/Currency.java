package com.spacerng.solrng.gui;

import com.spacerng.solrng.rarity.RollFormat;
import org.bukkit.ChatColor;

/**
 * The four currencies, and the one place their icon and colour are
 * decided.
 *
 * A currency has to be recognisable from a glance at a sidebar line, a
 * price in a menu, and a chat message — three places that were each
 * picking their own colour before this existed. Every one now gets a
 * glyph as well, because colour alone stops working the moment two
 * currencies sit on adjacent lines.
 *
 * The colours are deliberately spread across the wheel rather than chosen
 * for realism: Coins gold, Tokens green (they come from the farm), Gems
 * aqua, Credits purple (the store colour).
 *
 * The glyphs are four different SHAPES, not four decorations — circle,
 * square, diamond, star. Colour alone stops working the moment two
 * currencies sit on adjacent sidebar lines, and a shape is still legible
 * at one pixel of contrast. All four live in Minecraft's built-in unicode
 * font, so none of this needs a resource pack.
 */
public enum Currency {

    COINS("Coins", "●", ChatColor.GOLD, false),
    TOKENS("Tokens", "■", ChatColor.GREEN, false),
    GEMS("Gems", "◆", ChatColor.AQUA, false),
    // Credits are the one currency real money buys, so they get the one
    // treatment nothing else in the plugin uses. colour() stays purple for
    // bullets and accents — a rainbow bullet would just look broken.
    CREDITS("Credits", "✪", ChatColor.LIGHT_PURPLE, true);

    private final String label;
    private final String icon;
    private final ChatColor colour;
    private final boolean rainbow;

    Currency(String label, String icon, ChatColor colour, boolean rainbow) {
        this.label = label;
        this.icon = icon;
        this.colour = colour;
        this.rainbow = rainbow;
    }

    /** Paints one readout in this currency's own style. */
    private String paint(String text) {
        return rainbow ? Lore.rainbow(text) : colour + text;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }

    public ChatColor colour() {
        return colour;
    }

    /** The coloured glyph on its own, for prefixing a line. */
    public String mark() {
        return paint(icon);
    }

    /**
     * "1.2M Coins ●" — the full readout, all in the currency's colour.
     *
     * The glyph trails rather than leads because Minecraft's font is
     * proportional: four different leading glyphs are four different
     * widths, so a leading icon pushes every amount to a slightly
     * different column and the sidebar looks ragged. Trailing it, every
     * amount starts at the same x.
     */
    public String amount(long value) {
        return paint(RollFormat.abbreviate(value) + " " + label + " " + icon);
    }

    /**
     * The same readout, turned red when it's a price the player can't
     * meet — red has to be able to mean "you can't afford this" without
     * the currency becoming unrecognisable.
     */
    public String price(long value, boolean affordable) {
        String text = RollFormat.abbreviate(value) + " " + label + " " + icon;
        return affordable ? paint(text) : ChatColor.RED + text;
    }

    /** "1,200,000 Coins ●" — unabbreviated, for when the exact figure matters. */
    public String exact(long value) {
        return paint(String.format("%,d", value) + " " + label + " " + icon);
    }
}
