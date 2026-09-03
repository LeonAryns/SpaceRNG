package com.spacerng.solrng.rarity;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared formatting for rolled items: the "Rolled X (Rarity)" display name,
 * the Rarity/Chance lore lines, and the chat/broadcast wording. Kept in one
 * place so the item itself, chat messages, and broadcasts always agree.
 */
public final class RollFormat {

    private RollFormat() {
    }

    /**
     * "Stone" fully colored in the rarity's color. Used for the item itself
     * (its in-inventory display name) — the "you rolled X" phrasing only
     * appears in chat, not baked into the item name.
     */
    public static String displayName(SolRNGPlugin plugin, RollableItem item) {
        String color = plugin.getRarityManager().colorFor(item.getRarity());
        return color + item.getDisplayName();
    }

    /**
     * Lore lines showing the item's rarity and its odds, both as a chat
     * message suffix and as stat lines on the item itself.
     */
    public static List<String> lore(SolRNGPlugin plugin, RollableItem item) {
        String color = plugin.getRarityManager().colorFor(item.getRarity());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Rarity: " + color + item.getRarity().displayName());
        lore.add(ChatColor.GRAY + "Chance: " + color + chance(item.getOdds()));
        return lore;
    }

    /**
     * "1 in 500" style odds string, used in item lore.
     */
    public static String chance(long odds) {
        return "1 in " + odds;
    }

    /**
     * Compact "1/5.1M" style odds, used in chat/broadcast messages where
     * a full number would be too long to read at a glance.
     */
    public static String compactOdds(long odds) {
        return "1/" + abbreviate(odds);
    }

    private static String abbreviate(long n) {
        if (n < 1_000) return String.valueOf(n);
        if (n < 1_000_000) return trimZero(n / 1_000.0) + "K";
        if (n < 1_000_000_000L) return trimZero(n / 1_000_000.0) + "M";
        return trimZero(n / 1_000_000_000.0) + "B";
    }

    private static String trimZero(double value) {
        String formatted = String.format("%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    /**
     * The chat line every player sees for their own roll, e.g.
     * "⚡ You rolled Sand [COMMON] (1/67)".
     */
    public static String personalRollLine(SolRNGPlugin plugin, RollableItem item) {
        String color = plugin.getRarityManager().colorFor(item.getRarity());
        return ChatColor.AQUA + "⚡ " + ChatColor.WHITE + "You rolled " + item.getDisplayName() + " "
                + ChatColor.GRAY + "[" + color + item.getRarity().name() + ChatColor.GRAY + "] "
                + ChatColor.GRAY + "(" + compactOdds(item.getOdds()) + ")";
    }

    /**
     * The server-wide banner for a rare drop, e.g.
     * "✦ LEGENDARY DROP ✦" / "Player just found Fallen Star [LEGENDARY]" /
     * "Odds: 1/1M"
     */
    public static String broadcastBanner(SolRNGPlugin plugin, String playerName, RollableItem item) {
        String color = plugin.getRarityManager().colorFor(item.getRarity());
        String rarityWord = item.getRarity().name();
        return color + "" + ChatColor.BOLD + "✦ " + rarityWord + " DROP ✦" + "\n"
                + ChatColor.WHITE + playerName + ChatColor.GRAY + " just found " + color + item.getDisplayName() + " "
                + ChatColor.GRAY + "[" + color + rarityWord + ChatColor.GRAY + "]" + "\n"
                + ChatColor.GRAY + "Odds: " + ChatColor.WHITE + compactOdds(item.getOdds());
    }
}
