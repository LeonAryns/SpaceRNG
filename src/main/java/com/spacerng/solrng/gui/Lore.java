package com.spacerng.solrng.gui;

import org.bukkit.ChatColor;

/**
 * One shared vocabulary for every menu's text, so the plugin reads as a
 * single product rather than a dozen screens that each invented their own
 * formatting.
 *
 * The rules it encodes:
 *   「 Name 」   framed titles for headline items
 *   [STATE]     a dark-grey tag saying what state a thing is in
 *   ▎ line      a coloured bar prefixes every fact, and the colour IS the
 *               meaning — green good, red blocked, yellow actionable
 *   Section:    a coloured label above a group of related facts
 *
 * All of the glyphs live in Minecraft's unicode font pages, so none of it
 * needs a resource pack.
 */
public final class Lore {

    public static final String BULLET = "▎";
    public static final String ARROW = "➜";
    public static final String TICK = "✔";
    public static final String CROSS = "✘";
    public static final String STAR = "★";
    public static final String SPARK = "✦";

    private static final String BAR_FULL = "▬";
    private static final int BAR_LENGTH = 20;

    private Lore() {
    }

    /** 「 Prestige 4 」 — the framed name a headline item carries. */
    public static String title(ChatColor colour, String text) {
        return ChatColor.DARK_GRAY + "「 " + colour + ChatColor.BOLD + text + ChatColor.RESET
                + ChatColor.DARK_GRAY + " 」";
    }

    /** [ASCEND] — the small state tag under a title. */
    public static String state(String text) {
        return ChatColor.DARK_GRAY + "[" + text.toUpperCase() + "]";
    }

    /** A coloured section label: "Requirements:" */
    public static String section(ChatColor colour, String text) {
        return colour + "" + ChatColor.BOLD + text + ":";
    }

    /** ▎ one fact, the bar carrying the colour. */
    public static String line(ChatColor colour, String text) {
        return colour + BULLET + " " + ChatColor.GRAY + text;
    }

    /** ▎ Label: value — the most common shape. */
    public static String stat(ChatColor colour, String label, String value) {
        return colour + BULLET + " " + ChatColor.GRAY + label + ": " + ChatColor.WHITE + value;
    }

    /** A requirement line with a tick or a cross on the end. */
    public static String requirement(String label, String have, String need, boolean met) {
        return (met ? ChatColor.GREEN : ChatColor.RED) + BULLET + " " + ChatColor.GRAY + label + " "
                + (met ? ChatColor.GREEN : ChatColor.YELLOW) + have + ChatColor.DARK_GRAY + " / "
                + ChatColor.WHITE + need + "  " + (met ? ChatColor.GREEN + TICK : ChatColor.RED + CROSS);
    }

    /** "1.75x ➜ 2x" — what an upgrade turns a number into. */
    public static String upgrade(ChatColor colour, String label, String from, String to) {
        return colour + BULLET + " " + ChatColor.GRAY + label + " " + ChatColor.YELLOW + from
                + ChatColor.DARK_GRAY + " " + ARROW + " " + ChatColor.GREEN + ChatColor.BOLD + to;
    }

    /**
     * A filled bar that shifts hue as it fills — red when you've barely
     * started, green when you're done. The colour does the reading for you
     * before the numbers do.
     */
    public static String bar(double fraction) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * BAR_LENGTH);
        StringBuilder out = new StringBuilder(ChatColor.DARK_GRAY + "[");
        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i < filled) {
                out.append(rampColour((double) i / (BAR_LENGTH - 1))).append(BAR_FULL);
            } else {
                out.append(ChatColor.DARK_GRAY).append(BAR_FULL);
            }
        }
        return out.append(ChatColor.DARK_GRAY).append("]").toString();
    }

    private static ChatColor rampColour(double t) {
        if (t < 0.25) return ChatColor.RED;
        if (t < 0.45) return ChatColor.GOLD;
        if (t < 0.65) return ChatColor.YELLOW;
        if (t < 0.85) return ChatColor.GREEN;
        return ChatColor.AQUA;
    }

    /** "12.4K" — short numbers for tight lore lines. */
    public static String shorten(double value) {
        if (value < 1_000) return String.format("%.0f", value);
        if (value < 1_000_000) return trim(value / 1_000.0) + "K";
        if (value < 1_000_000_000L) return trim(value / 1_000_000.0) + "M";
        return trim(value / 1_000_000_000.0) + "B";
    }

    private static String trim(double value) {
        String formatted = String.format("%.2f", value);
        while (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }
}
