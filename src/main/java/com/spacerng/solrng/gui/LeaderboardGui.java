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
 * /leaderboards - the six standings that never reset.
 *
 * The DAILY farming board is deliberately not here. It resets every
 * night and pays out, which makes it a race rather than a standing, so it
 * keeps /top and the hologram at spawn. What's here is the long game -
 * what you've found, what you've earned - and it belongs somewhere you
 * can read all of it at once rather than one chat command at a time.
 */
public class LeaderboardGui {

    /**
     * One board on the wall. `board` is the id LeaderboardManager sorts
     * by, so the menu can never show a board the placeholders can't.
     */
    private record Card(int slot, Material icon, ChatColor accent, String name, String board,
                        String unit, String blurb) {
    }

    // Top row: what you've found. Bottom row: what you've earned. The
    // player's own head sits between them.
    private static final Card[] CARDS = {
            new Card(11, Material.ENCHANTED_BOOK, ChatColor.AQUA, "Total Index", "index", "drops",
                    "Every drop you've discovered."),
            new Card(13, Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE, "Shiny Index", "shiny", "shinies",
                    "Shiny drops you've discovered."),
            new Card(15, Material.NETHER_STAR, ChatColor.YELLOW, "Rolls", "rolls", "rolls",
                    "Every roll you've ever made."),
            new Card(29, Material.GOLD_INGOT, ChatColor.GOLD, "Coins", "coins", "Coins",
                    "Coins in hand, from farming."),
            new Card(31, Material.EMERALD, ChatColor.GREEN, "Money", "money", "Money",
                    "Money in hand, from rolling."),
            new Card(33, Material.WHEAT, ChatColor.DARK_GREEN, "Crops", "farming_total", "crops",
                    "Crops broken, all time."),
    };

    private static final int ROWS = 5;
    private static final int SIZE = ROWS * 9;
    private static final int SELF_SLOT = 22;
    private static final int SHOWN = 5;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        LeaderboardHolder holder = new LeaderboardHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Leaderboards");
        holder.setInventory(inv);

        ItemStack frame = pane(Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            inv.setItem(slot,
                    row == 0 || row == ROWS - 1 || column == 0 || column == 8 ? frame : fill);
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
     * The player's own card, carrying all six placings at once - the
     * answer to "where am I" without reading six tooltips.
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

    /**
     * Gold on first place, white on everyone else.
     *
     * A three-tone podium sounds right and reads badly: silver and bronze
     * are close enough to each other and to the grey body text that the
     * eye has to stop and work out which is which. One colour marking one
     * player is legible at a glance, and the rank number already says the
     * rest.
     */
    private static ChatColor placeColour(int index) {
        return index == 0 ? ChatColor.GOLD : ChatColor.WHITE;
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
