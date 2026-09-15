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
 * The DAILY farming board is deliberately not a card here. It resets every
 * night and pays out, which makes it a race rather than a standing, so it
 * keeps /top and the hologram at spawn, and this menu only points at it.
 * What's here is the long game - what you've found, what you've earned -
 * readable all at once rather than one chat command at a time.
 */
public class LeaderboardGui {

    /**
     * One board on the wall. `board` is the id LeaderboardManager sorts
     * by, so the menu can never show a board the placeholders can't.
     */
    private record Card(int slot, Material icon, ChatColor accent, String name, String board,
                        String unit, String blurb) {
    }

    // Upper row: what you've found. Lower row: what you've earned. The two
    // rows are staggered so every card has room around it.
    private static final Card[] CARDS = {
            new Card(20, Material.ENCHANTED_BOOK, ChatColor.AQUA, "Total Index", "index", "drops",
                    "Every drop you've discovered."),
            new Card(22, Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE, "Shiny Index", "shiny", "shinies",
                    "Shiny drops you've discovered."),
            new Card(24, Material.NETHER_STAR, ChatColor.YELLOW, "Rolls", "rolls", "rolls",
                    "Every roll you've ever made."),
            new Card(29, Material.GOLD_INGOT, ChatColor.GOLD, "Coins", "coins", "Coins",
                    "Coins in hand, from farming."),
            new Card(31, Material.EMERALD, ChatColor.GREEN, "Money", "money", "Money",
                    "Money in hand, from rolling."),
            new Card(33, Material.WHEAT, ChatColor.DARK_GREEN, "Crops", "farming_total", "crops",
                    "Crops broken, all time."),
    };

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int SELF_SLOT = 49;
    private static final int DAILY_SLOT = 47;
    private static final int SHOWN = 10;

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
        inv.setItem(DAILY_SLOT, buildDaily());
        inv.setItem(SELF_SLOT, selfItem(plugin, player, plugin.getConfig().getString("standings-style", "summary")));
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
                    + ChatColor.DARK_GRAY + "  ·  " + card.accent() + Lore.shorten(value)
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
        boolean ranked = value > 0 && place > 0;
        lore.add(Lore.stat(ChatColor.YELLOW, "Place", ranked ? "#" + place + " of " + boards.size() : "unranked"));
        lore.add(Lore.stat(card.accent(), capitalise(card.unit()), Lore.shorten(value)));

        // The one thing worth knowing after where you are: how far the next
        // place is, or that there is no next place.
        boolean first = ranked && place == 1;
        if (first) {
            lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GREEN + "You hold first place");
        } else if (ranked) {
            List<LeaderboardManager.Entry> above = boards.top(card.board(), place - 1);
            if (above.size() >= place - 1) {
                LeaderboardManager.Entry next = above.get(place - 2);
                long gap = Math.max(1L, LeaderboardManager.valueOf(card.board(), next) - value + 1);
                lore.add(ChatColor.AQUA + Lore.BULLET + " " + ChatColor.WHITE + Lore.shorten(gap) + " "
                        + ChatColor.AQUA + card.unit() + " to pass " + ChatColor.WHITE + next.name());
            }
        } else {
            lore.add(ChatColor.AQUA + Lore.BULLET + " " + ChatColor.AQUA + "Earn your first to join this board");
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(first ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    /** Where the daily race lives, since it isn't a card of its own. */
    private static ItemStack buildDaily() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Daily Farming Race"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GOLD, "Resets every night."));
        lore.add(Lore.line(ChatColor.GOLD, "The top three are paid Credits."));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "See it with /top farming");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** The looks the player's own standings card can take, picked with standings-style. */
    public static final List<String> STANDINGS_STYLES = List.of("summary", "list", "ranked", "meter", "compact");

    private record Standing(Card card, long value, int place) {
        boolean ranked() {
            return value > 0 && place > 0;
        }
    }

    /**
     * The player's own card, carrying all six placings at once - the
     * answer to "where am I" without reading six tooltips. /rngadmin
     * standingstyles shows every style side by side.
     */
    public static ItemStack selfItem(SolRNGPlugin plugin, Player player, String style) {
        LeaderboardManager boards = plugin.getLeaderboardManager();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        meta.setDisplayName(Lore.title(ChatColor.GOLD, player.getName()));

        LeaderboardManager.Entry mine = boards.entryOf(player.getUniqueId());
        List<Standing> standings = new ArrayList<>();
        for (Card card : CARDS) {
            long value = mine == null ? 0L : LeaderboardManager.valueOf(card.board(), mine);
            standings.add(new Standing(card, value, boards.positionOf(card.board(), player.getUniqueId())));
        }
        int tracked = boards.size();
        String footer = ChatColor.AQUA + Lore.BULLET + " " + ChatColor.WHITE + tracked
                + ChatColor.AQUA + (tracked == 1 ? " player" : " players") + " on the boards";

        List<String> lore = new ArrayList<>();
        switch (style == null ? "" : style.toLowerCase(java.util.Locale.ROOT)) {
            case "list" -> {
                lore.add(Lore.section(ChatColor.GOLD, "Your standings"));
                for (Standing s : standings) {
                    String place = !s.ranked() ? ChatColor.DARK_GRAY + "unranked"
                            : (s.place() == 1 ? ChatColor.GOLD : ChatColor.YELLOW) + "#" + s.place();
                    lore.add(s.card().accent() + Lore.BULLET + " " + ChatColor.GRAY + s.card().name() + ": "
                            + place + ChatColor.DARK_GRAY + "  ·  " + s.card().accent() + Lore.shorten(s.value()));
                }
                lore.add("");
                lore.add(footer);
            }
            case "ranked" -> {
                // Best placing first, so the top of the card is what you're proudest of.
                List<Standing> sorted = new ArrayList<>(standings);
                sorted.sort(java.util.Comparator.comparingInt((Standing s) -> s.ranked() ? s.place() : Integer.MAX_VALUE));
                lore.add(Lore.section(ChatColor.GOLD, "Your best boards"));
                for (Standing s : sorted) {
                    if (!s.ranked()) {
                        lore.add(ChatColor.DARK_GRAY + "  -   " + s.card().accent() + s.card().name()
                                + ChatColor.DARK_GRAY + "  not on it yet");
                        continue;
                    }
                    ChatColor placeColour = s.place() == 1 ? ChatColor.GOLD : s.place() <= 3 ? ChatColor.YELLOW
                            : s.place() <= 10 ? ChatColor.WHITE : ChatColor.GRAY;
                    lore.add(placeColour + "" + ChatColor.BOLD + "#" + s.place() + ChatColor.RESET + "  "
                            + s.card().accent() + s.card().name() + "  " + ChatColor.WHITE + Lore.shorten(s.value())
                            + " " + ChatColor.GRAY + s.card().unit());
                }
                lore.add("");
                lore.add(footer);
            }
            case "meter" -> {
                lore.add(Lore.section(ChatColor.GOLD, "How high you stand"));
                for (Standing s : standings) {
                    int filled = !s.ranked() || tracked <= 0 ? 0
                            : (int) Math.round(10.0 * (tracked - s.place() + 1) / tracked);
                    lore.add(s.card().accent() + "▬".repeat(filled) + ChatColor.DARK_GRAY + "▬".repeat(10 - filled)
                            + "  " + s.card().accent() + s.card().name()
                            + (s.ranked() ? ChatColor.WHITE + "  #" + s.place() : ChatColor.DARK_GRAY + "  unranked"));
                }
                lore.add("");
                lore.add(footer);
            }
            case "compact" -> {
                StringBuilder row = new StringBuilder();
                int firsts = 0;
                for (int i = 0; i < standings.size(); i++) {
                    Standing s = standings.get(i);
                    if (s.ranked() && s.place() == 1) firsts++;
                    if (i % 3 != 0) row.append(ChatColor.DARK_GRAY).append("  ·  ");
                    row.append(s.card().accent()).append(s.card().name()).append(" ")
                            .append(s.ranked() ? ChatColor.WHITE + "#" + s.place() : ChatColor.DARK_GRAY + "-");
                    if (i % 3 == 2 || i == standings.size() - 1) {
                        lore.add(row.toString());
                        row.setLength(0);
                    }
                }
                lore.add("");
                lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "First on " + firsts
                        + (firsts == 1 ? " board" : " boards"));
                lore.add(footer);
            }
            default -> {
                // Grouped by how you're doing, so the answer comes before the detail.
                List<String> first = new ArrayList<>();
                List<String> top = new ArrayList<>();
                List<String> rest = new ArrayList<>();
                for (Standing s : standings) {
                    // Every board shows your number, then the place you hold behind it.
                    String name = s.card().accent() + s.card().name() + ChatColor.GRAY + ": ";
                    String value = ChatColor.WHITE + Lore.shorten(s.value());
                    if (!s.ranked()) rest.add(s.card().accent() + s.card().name());
                    else if (s.place() == 1) first.add(name + value + ChatColor.GOLD + "  #1");
                    else top.add(name + value + ChatColor.YELLOW + "  #" + String.format("%,d", s.place()));
                }
                if (!first.isEmpty()) {
                    lore.add(Lore.section(ChatColor.GOLD, "First place"));
                    for (String line : first) lore.add(ChatColor.GOLD + Lore.BULLET + " " + line);
                    lore.add("");
                }
                if (!top.isEmpty()) {
                    lore.add(Lore.section(ChatColor.YELLOW, "On the boards"));
                    for (String line : top) lore.add(ChatColor.YELLOW + Lore.BULLET + " " + line);
                    lore.add("");
                }
                if (!rest.isEmpty()) {
                    lore.add(Lore.section(ChatColor.AQUA, "Not on yet"));
                    lore.add(ChatColor.AQUA + Lore.BULLET + " " + String.join(ChatColor.DARK_GRAY + ", ", rest));
                    lore.add("");
                }
                lore.add(footer);
            }
        }
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

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
