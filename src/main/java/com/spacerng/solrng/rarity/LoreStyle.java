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
    CLASSIC("classic", "labelled lines, what drops use today"),
    PIPE("pipe", "a coloured bar down the left, sectioned"),
    COMPACT("compact", "two short lines and nothing else"),
    STATS("stats", "stat rows behind rarity-coloured bullets"),
    CARD("card", "a framed card with rules above and below"),
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
        return parse(plugin.getConfig().getString("roll-item.lore-style", "classic"));
    }

    public static LoreStyle parse(String raw) {
        if (raw != null) {
            for (LoreStyle style : values()) {
                if (style.key.equalsIgnoreCase(raw.trim())) return style;
            }
        }
        return CLASSIC;
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
                String rule = ChatColor.DARK_GRAY + "⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯";
                lore.add(rule);
                lore.add("  " + rarities.style(rarity, (shiny ? "✦ Shiny " : "") + rarity.displayName() + " drop"));
                lore.add(rule);
                lore.add("  " + ChatColor.GRAY + "Odds  " + ChatColor.WHITE + odds);
                lore.add("  " + ChatColor.GRAY + "Index Luck  " + ChatColor.DARK_AQUA + luck);
                if (shiny) {
                    lore.add("  " + ChatColor.GRAY + "Shiny  " + ChatColor.AQUA + shinyOdds);
                }
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
}
