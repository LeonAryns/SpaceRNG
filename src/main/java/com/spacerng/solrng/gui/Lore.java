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

    // The plugin's headline gradient — light lilac into deep violet. Used
    // for sidebar section headers so the two of them read as one voice.
    private static final String[] HEADER_STOPS = {"#F6D6FF", "#DFA6FF", "#C77DFF", "#B15CFF"};
    // A full loop of the spectrum, ending where it started so a long
    // string doesn't finish on a jarringly different colour from its start.
    private static final String[] RAINBOW_STOPS = {
            "#FF5555", "#FFAA00", "#FFFF55", "#55FF55", "#55FFFF", "#FF55FF", "#FF5555"
    };

    private Lore() {
    }

    /**
     * A per-character gradient across hex stops, emitted as legacy §x
     * codes.
     *
     * Written here rather than reusing RarityManager's engine because that
     * one is loaded from config and belongs to the item table; a header
     * colour shouldn't change because somebody retuned a rarity. Spaces
     * are left uncoloured — colouring them wastes six characters a piece
     * against Minecraft's line length limits and looks identical.
     */
    public static String gradient(String text, String... hexStops) {
        return gradient(text, false, hexStops);
    }

    /**
     * Bold has to be re-emitted after every colour code, not once at the
     * front: a colour code clears formatting in Minecraft, so a single
     * leading §l is wiped out by the first character's own colour.
     */
    public static String gradient(String text, boolean bold, String... hexStops) {
        if (hexStops.length == 0) return text;
        String weight = bold ? ChatColor.BOLD.toString() : "";
        if (hexStops.length == 1) return of(hexStops[0]) + weight + text;

        StringBuilder out = new StringBuilder();
        int length = text.length();
        int segments = hexStops.length - 1;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                out.append(' ');
                continue;
            }
            double t = length <= 1 ? 0.0 : (double) i / (length - 1);
            int segment = Math.min((int) (t * segments), segments - 1);
            double local = (t * segments) - segment;
            out.append(of(blend(hexStops[segment], hexStops[segment + 1], local)))
               .append(weight)
               .append(c);
        }
        return out.toString();
    }

    /** A bold sidebar/menu section header in the house purple. */
    public static String header(String text) {
        return gradient(text, true, HEADER_STOPS);
    }

    /**
     * The same purple, mirrored so it runs light-dark-light. A sidebar
     * title is short and centred; a one-way ramp on a short centred string
     * just reads as "the right-hand side is dimmer".
     */
    public static String banner(String text) {
        return gradient(text, true, "#F6D6FF", "#C77DFF", "#A855F7", "#C77DFF", "#F6D6FF");
    }

    /** Full-spectrum text — the Credits treatment. */
    public static String rainbow(String text) {
        return gradient(text, RAINBOW_STOPS);
    }

    private static String blend(String fromHex, String toHex, double t) {
        int[] a = rgb(fromHex);
        int[] b = rgb(toHex);
        return String.format("#%02X%02X%02X",
                (int) Math.round(a[0] + (b[0] - a[0]) * t),
                (int) Math.round(a[1] + (b[1] - a[1]) * t),
                (int) Math.round(a[2] + (b[2] - a[2]) * t));
    }

    private static int[] rgb(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        return new int[]{
                Integer.parseInt(clean.substring(0, 2), 16),
                Integer.parseInt(clean.substring(2, 4), 16),
                Integer.parseInt(clean.substring(4, 6), 16)};
    }

    private static String of(String hex) {
        int[] c = rgb(hex);
        return net.md_5.bungee.api.ChatColor.of(new java.awt.Color(c[0], c[1], c[2])).toString();
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
