package com.spacerng.solrng.rarity;

import net.md_5.bungee.api.ChatColor;

import java.awt.Color;
import java.util.List;

/**
 * A visual identity: one flat color, or a real per-character gradient
 * across 2+ color stops (computed here, not via a static prefix — a fixed
 * prefix string can't represent a gradient since the same prefix would
 * need to stretch across whatever text follows it, of whatever length),
 * plus optional bold/underline/strikethrough.
 *
 * Used for two things: each individual item's own look (config: per-item
 * "colors"), and each rarity's plain label color.
 */
public class RarityStyle {

    private final List<int[]> colorStopsRgb; // each stop = {r, g, b}
    private final boolean bold;
    private final boolean underline;
    private final boolean strikethrough;

    public RarityStyle(List<int[]> colorStopsRgb, boolean bold, boolean underline, boolean strikethrough) {
        this.colorStopsRgb = colorStopsRgb;
        this.bold = bold;
        this.underline = underline;
        this.strikethrough = strikethrough;
    }

    /** Applies this style's color(s) and formatting to the given text. */
    public String apply(String text) {
        return apply(text, false);
    }

    /**
     * Same, but bold can be forced on regardless of the style's own flag —
     * used for price lines, where the rarity name should stand out without
     * every rarity label everywhere else turning bold.
     */
    public String apply(String text, boolean forceBold) {
        StringBuilder out = new StringBuilder();

        if (colorStopsRgb.size() <= 1) {
            int[] rgb = colorStopsRgb.isEmpty() ? new int[]{255, 255, 255} : colorStopsRgb.get(0);
            out.append(colorCode(rgb)).append(flagCodes(forceBold)).append(text);
            return out.toString();
        }

        int n = text.length();
        int segments = colorStopsRgb.size() - 1;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                out.append(' ');
                continue;
            }
            double t = n <= 1 ? 0.0 : (double) i / (n - 1);
            int seg = Math.min((int) (t * segments), segments - 1);
            double localT = (t * segments) - seg;
            int[] c1 = colorStopsRgb.get(seg);
            int[] c2 = colorStopsRgb.get(seg + 1);
            int r = (int) Math.round(c1[0] + (c2[0] - c1[0]) * localT);
            int g = (int) Math.round(c1[1] + (c2[1] - c1[1]) * localT);
            int b = (int) Math.round(c1[2] + (c2[2] - c1[2]) * localT);
            out.append(colorCode(new int[]{r, g, b})).append(flagCodes(forceBold)).append(c);
        }
        return out.toString();
    }

    private String flagCodes(boolean forceBold) {
        StringBuilder flags = new StringBuilder();
        if (bold || forceBold) flags.append(ChatColor.BOLD);
        if (underline) flags.append(ChatColor.UNDERLINE);
        if (strikethrough) flags.append(ChatColor.STRIKETHROUGH);
        return flags.toString();
    }

    private String colorCode(int[] rgb) {
        return ChatColor.of(new Color(rgb[0], rgb[1], rgb[2])).toString();
    }
}
