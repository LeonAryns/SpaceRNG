package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.leaderboard.LeaderboardManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /leaderboards — the four standings that aren't the farming one.
 *
 * The farming board has its own life: it resets daily and pays out, so it
 * lives in /top and on the hologram at spawn. These four never reset.
 * They're the long game — what you've collected, what you've hoarded —
 * and they belong somewhere you can read all of them at once rather than
 * one chat command at a time.
 */
public class LeaderboardGui {

    /**
     * One board on the wall. `board` is the id LeaderboardManager sorts
     * by, so the menu can never show a board the placeholders can't.
     */
    private record Card(int slot, Material icon, ChatColor accent, String name, String board,
                        String unit, String blurb) {
    }

    private static final Card[] CARDS = {
            new Card(19, Material.ENCHANTED_BOOK, ChatColor.AQUA, "Total Index", "index", "drops",
                    "Every drop you've discovered."),
            new Card(21, Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE, "Shiny Index", "shiny", "shinies",
                    "Shiny drops you've discovered."),
            new Card(23, Material.GOLD_INGOT, ChatColor.GOLD, "Coins", "coins", "Coins",
                    "Coins in hand, from farming."),
            new Card(25, Material.EMERALD, ChatColor.GREEN, "Money", "money", "Money",
                    "Money in hand, from rolling."),
    };

    private static final int SELF_SLOT = 49;
    private static final int SHOWN = 5;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        LeaderboardHolder holder = new LeaderboardHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Leaderboards");
        holder.setInventory(inv);

        ItemStack frame = pane(Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            inv.setItem(slot, row == 0 || row == 5 || column == 0 || column == 8 ? frame : fill);
        }

        LeaderboardManager boards = plugin.getLeaderboardManager();
        for (Card card : CARDS) {
            inv.setItem(card.slot(), buildCard(boards, player, card));
        }
        inv.setItem(SELF_SLOT, buildSelf(boards, player));
        return inv;
    }

    private static ItemStack buildCard(LeaderboardManager boards, Player player, Card card) {
        ItemStack item = new ItemStack(card.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(card.accent(), card.name()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(card.accent(), card.blurb()));
        lore.add("");

        List<LeaderboardManager.Entry> rows = boards.top(card.board(), SHOWN);
        boolean any = false;
        lore.add(Lore.section(ChatColor.GOLD, "Top " + SHOWN));
        for (int i = 0; i < rows.size(); i++) {
            LeaderboardManager.Entry entry = rows.get(i);
            long value = LeaderboardManager.valueOf(card.board(), entry);
            // A row with nothing on it isn't a standing, it's a player who
            // happens to exist. Podium slots stay empty until somebody
            // actually earns them.
            if (value <= 0) continue;
            any = true;
            lore.add(placeColour(i) + Lore.BULLET + " #" + (i + 1) + " "
                    + ChatColor.WHITE + entry.name()
                    + ChatColor.DARK_GRAY + " · " + card.accent() + Lore.shorten(value)
                    + ChatColor.GRAY + " " + card.unit());
        }
        if (!any) {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Nobody's on this board yet.");
        }

        lore.add("");
        lore.add(Lore.section(card.accent(), "You"));
        int place = boards.positionOf(card.board(), player.getUniqueId());
        LeaderboardManager.Entry mine = boards.entryOf(player.getUniqueId());
        long value = mine == null ? 0L : LeaderboardManager.valueOf(card.board(), mine);
        lore.add(Lore.stat(ChatColor.YELLOW, "Place", value <= 0 ? "unranked" : "#" + place));
        lore.add(Lore.stat(card.accent(), card.unit(), Lore.shorten(value)));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The player's own card, carrying all four placings at once — the
     * answer to "where am I" without reading four tooltips.
     */
    private static ItemStack buildSelf(LeaderboardManager boards, Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        meta.setDisplayName(Lore.title(ChatColor.GOLD, player.getName()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.GOLD, "Your standings"));
        LeaderboardManager.Entry mine = boards.entryOf(player.getUniqueId());
        for (Card card : CARDS) {
            long value = mine == null ? 0L : LeaderboardManager.valueOf(card.board(), mine);
            int place = boards.positionOf(card.board(), player.getUniqueId());
            lore.add(card.accent() + Lore.BULLET + " " + ChatColor.GRAY + card.name() + ": "
                    + ChatColor.WHITE + (value <= 0 ? "unranked" : "#" + place)
                    + ChatColor.DARK_GRAY + " · " + ChatColor.GRAY + Lore.shorten(value));
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Farming has its own board: /top farming");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + boards.size() + " players tracked");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Gold, silver, bronze, then quiet — so the podium reads at a glance. */
    private static ChatColor placeColour(int index) {
        return switch (index) {
            case 0 -> ChatColor.GOLD;
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.YELLOW;
            default -> ChatColor.DARK_GRAY;
        };
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
