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
 * aqua, Credits purple (the store colour). All four glyphs live in
 * Minecraft's built-in unicode font, so none of this needs a resource
 * pack.
 */
public enum Currency {

    COINS("Coins", "●", ChatColor.GOLD),
    TOKENS("Tokens", "✿", ChatColor.GREEN),
    GEMS("Gems", "◆", ChatColor.AQUA),
    CREDITS("Credits", "✪", ChatColor.LIGHT_PURPLE);

    private final String label;
    private final String icon;
    private final ChatColor colour;

    Currency(String label, String icon, ChatColor colour) {
        this.label = label;
        this.icon = icon;
        this.colour = colour;
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
        return colour + icon;
    }

    /** "● 1.2M Coins" — the full readout, all in the currency's colour. */
    public String amount(long value) {
        return colour + icon + " " + RollFormat.abbreviate(value) + " " + label;
    }

    /**
     * "● 1.2M Coins" with the amount tinted separately — for prices, where
     * red has to be able to mean "you can't afford this" without the icon
     * changing colour and looking like a different currency.
     */
    public String price(long value, boolean affordable) {
        return colour + icon + " " + (affordable ? colour : ChatColor.RED)
                + RollFormat.abbreviate(value) + " " + (affordable ? colour : ChatColor.RED) + label;
    }

    /** "● 1,200,000 Coins" — unabbreviated, for when the exact figure matters. */
    public String exact(long value) {
        return colour + icon + " " + String.format("%,d", value) + " " + label;
    }
}
