package com.spacerng.solrng.rarity;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Interchangeable layouts for a drop's tooltip. Every style carries the
 * same facts, rarity, odds, Index Luck and whether it is shiny, and only
 * changes how they read. Chosen with roll-item.lore-style; /rngadmin
 * lorestyles hands out one sample of each so they can be compared.
 *
 * Lore is written onto an item when it is created, so a switch only
 * changes drops rolled after it.
 */
public enum LoreStyle {
    CLASSIC("classic", "labelled lines, the original layout"),
    PIPE("pipe", "a coloured bar down the left, sectioned"),
    COMPACT("compact", "two short lines and nothing else"),
    STATS("stats", "stat rows behind rarity-coloured bullets"),
    CARD("card", "framed card with full-width rules and bulleted rows"),
    LADDER("ladder", "a seven-step bar showing where the rarity sits"),
    STORY("story", "plain sentences instead of labels");

    private final String key;
    private final String summary;

    LoreStyle(String key, String summary) {
        this.key = key;
        this.summary = summary;
    }

    public String key() {
        return key;
    }

    public String summary() {
        return summary;
    }

    public static LoreStyle configured(SolRNGPlugin plugin) {
        return parse(plugin.getConfig().getString("roll-item.lore-style", "card"));
    }

    public static LoreStyle parse(String raw) {
        if (raw != null) {
            for (LoreStyle style : values()) {
                if (style.key.equalsIgnoreCase(raw.trim())) return style;
            }
        }
        return CARD;
    }

    public List<String> build(SolRNGPlugin plugin, RollableItem item, boolean shiny) {
        Rarity rarity = item.getRarity();
        RarityManager rarities = plugin.getRarityManager();
        String word = rarities.style(rarity, rarity.displayName());
        String odds = RollFormat.chance(item.getOdds());
        String luck = String.format("%.2f", item.getLuckMultiplier()) + "x";
        long shinyIn = Math.round(1.0 / Math.max(0.0001, plugin.getConfig().getDouble("shiny.chance", 0.0004)));
        String shinyOdds = "1 in " + String.format("%,d", shinyIn);

        List<String> lore = new ArrayList<>();
        switch (this) {
            case CLASSIC -> {
                if (shiny) {
                    lore.add(ChatColor.AQUA + "" + ChatColor.BOLD + "SHINY "
                            + ChatColor.RESET + ChatColor.DARK_GRAY + shinyOdds + " drops");
                    lore.add("");
                }
                lore.add(ChatColor.GRAY + "Rarity: " + word);
                lore.add(ChatColor.GRAY + "Chance: " + rarities.style(rarity, odds));
                lore.add(ChatColor.GRAY + "Index Luck: " + ChatColor.DARK_AQUA + luck);
            }
            case PIPE -> {
                String bar = rarities.style(rarity, "|");
                lore.add(rarities.style(rarity, "| Drop"));
                lore.add(bar + " " + ChatColor.GRAY + "Rarity " + word);
                lore.add(bar + " " + ChatColor.GRAY + "Odds " + ChatColor.WHITE + odds);
                lore.add(bar + " " + ChatColor.GRAY + "Index Luck " + ChatColor.DARK_AQUA + luck);
                if (shiny) {
                    lore.add("");
                    lore.add(ChatColor.AQUA + "| Shiny");
                    lore.add(ChatColor.AQUA + "| " + ChatColor.GRAY + shinyOdds + " drops");
                }
            }
            case COMPACT -> {
                lore.add(word + ChatColor.DARK_GRAY + "  ·  " + ChatColor.GRAY + odds);
                lore.add(ChatColor.DARK_AQUA + luck + ChatColor.GRAY + " Index Luck"
                        + (shiny ? ChatColor.DARK_GRAY + "  ·  " + ChatColor.AQUA + "✦ Shiny" : ""));
            }
            case STATS -> {
                String bullet = rarities.style(rarity, "▎");
                lore.add(bullet + " " + ChatColor.GRAY + "Rarity: " + word);
                lore.add(bullet + " " + ChatColor.GRAY + "Odds: " + ChatColor.WHITE + odds);
                lore.add(bullet + " " + ChatColor.GRAY + "Index Luck: " + ChatColor.DARK_AQUA + luck);
                if (shiny) {
                    lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Shiny: " + ChatColor.AQUA + shinyOdds);
                }
            }
            case CARD -> {
                // The card from style 5 with the bulleted rows from style 4.
                // A shiny keeps the same frame but in aqua, with its own row.
                String title = "  " + (shiny
                        ? ChatColor.AQUA + "✦ Shiny " + rarities.style(rarity, rarity.displayName() + " drop")
                        : rarities.style(rarity, rarity.displayName() + " drop"));
                String bullet = rarities.style(rarity, "▎");
                List<String> rows = new ArrayList<>();
                rows.add(bullet + " " + ChatColor.GRAY + "Odds  " + ChatColor.WHITE + odds);
                rows.add(bullet + " " + ChatColor.GRAY + "Index Luck  " + ChatColor.DARK_AQUA + luck);
                if (shiny) {
                    rows.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Shiny  " + ChatColor.AQUA + shinyOdds + " drops");
                }

                // The tooltip is as wide as its widest line, so a rule cut
                // to at least that width runs edge to edge instead of
                // stopping short like a fixed row of dashes did.
                int widest = Math.max(pixelWidth(RollFormat.displayName(plugin, item, shiny)), pixelWidth(title));
                for (String row : rows) widest = Math.max(widest, pixelWidth(row));
                String rule = (shiny ? ChatColor.DARK_AQUA : ChatColor.DARK_GRAY) + "" + ChatColor.STRIKETHROUGH
                        + " ".repeat(widest / 4 + 2);

                lore.add(rule);
                lore.add(title);
                lore.add(rule);
                lore.addAll(rows);
                lore.add(rule);
            }
            case LADDER -> {
                int filled = rarity.ordinal() + 1;
                int steps = Rarity.values().length;
                String ladder = rarities.style(rarity, "▬".repeat(filled))
                        + ChatColor.DARK_GRAY + "▬".repeat(Math.max(0, steps - filled));
                lore.add(word + "  " + ladder);
                lore.add(ChatColor.GRAY + odds + ChatColor.DARK_GRAY + "  ·  "
                        + ChatColor.DARK_AQUA + luck + ChatColor.GRAY + " Index Luck");
                if (shiny) {
                    lore.add(ChatColor.AQUA + "✦ Shiny " + ChatColor.DARK_GRAY + shinyOdds + " drops");
                }
            }
            case STORY -> {
                String article = "AEIOU".indexOf(rarity.name().charAt(0)) >= 0 ? "An " : "A ";
                lore.add(ChatColor.GRAY + article + word + ChatColor.GRAY + " drop,");
                lore.add(ChatColor.GRAY + "found at odds of " + ChatColor.WHITE + odds + ChatColor.GRAY + ".");
                lore.add(ChatColor.GRAY + "Adds " + ChatColor.DARK_AQUA + luck + ChatColor.GRAY + " to your Index Luck.");
                if (shiny) {
                    lore.add(ChatColor.AQUA + "And it is shiny, " + ChatColor.GRAY + shinyOdds + " drops.");
                }
            }
        }
        return lore;
    }

    /**
     * Roughly how wide a legacy-coloured line renders in a tooltip, in GUI
     * pixels. Minecraft's font is proportional: most ASCII advances 6, a
     * few narrow glyphs less, bold adds one per glyph, and anything outside
     * ASCII is counted generously so a rule is never shorter than the text.
     */
    static int pixelWidth(String legacy) {
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < legacy.length()) {
                char code = Character.toLowerCase(legacy.charAt(++i));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || "0123456789abcdefx".indexOf(code) >= 0) {
                    bold = false;
                }
                continue;
            }
            width += advance(c) + (bold ? 1 : 0);
        }
        return width;
    }

    private static int advance(char c) {
        if (c == ' ') return 4;
        if (c > 126) return c == '·' ? 2 : 9;
        return switch (c) {
            case 'i', '!', '|', '.', ',', ':', ';', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'I', 't', '[', ']', '(', ')', '{', '}', '"', '*' -> 4;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
