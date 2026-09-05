package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.Statistic;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Collection log: every rollable item, greyed out until the player has
 * actually rolled it at least once. Top row is a tab bar — a button per
 * rarity (left to right, click to filter, click again to clear it) plus
 * an Index Progress readout and page controls on the right. The 45 slots
 * below page through whatever's currently selected.
 */
public class IndexGui {

    // Row 1 is the tab bar, row 2 a glass divider, so entries fill rows 3-6.
    private static final int PAGE_SIZE = 36;
    private static final int ENTRY_START_SLOT = 18;
    private static final int DIVIDER_ROW_START = 9;
    private static final int PREV_SLOT = 6;
    private static final int PROGRESS_SLOT = 7;
    private static final int NEXT_SLOT = 8;

    public static Inventory build(SolRNGPlugin plugin, Player player, Rarity filter, int page) {
        IndexHolder holder = new IndexHolder();
        holder.setFilter(filter);

        List<RollableItem> allItems = plugin.getRarityManager().getItems();
        List<RollableItem> shown = filter == null ? allItems
                : allItems.stream().filter(i -> i.getRarity() == filter).toList();

        int totalPages = Math.max(1, (int) Math.ceil(shown.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        holder.setPage(page);

        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Your Index");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // Divider under the tab bar so the filters read as their own
        // section rather than running straight into the entries.
        for (int slot = DIVIDER_ROW_START; slot < DIVIDER_ROW_START + 9; slot++) {
            inv.setItem(slot, divider());
        }

        for (Rarity rarity : Rarity.values()) {
            inv.setItem(rarity.ordinal(), buildTab(plugin, rarity, filter == rarity));
        }

        inv.setItem(PROGRESS_SLOT, buildProfile(plugin, player, data, filter, shown.size()));
        if (page > 0) {
            inv.setItem(PREV_SLOT, buildPageButton(false, page, totalPages));
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, buildPageButton(true, page, totalPages));
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(shown.size(), from + PAGE_SIZE);
        int slot = ENTRY_START_SLOT;
        for (RollableItem item : shown.subList(from, to)) {
            inv.setItem(slot, buildEntry(plugin, data, item));
            slot++;
        }

        return inv;
    }

    private static ItemStack buildTab(SolRNGPlugin plugin, Rarity rarity, boolean selected) {
        Material material = switch (rarity) {
            case COMMON -> Material.WHITE_DYE;
            case UNCOMMON -> Material.LIME_DYE;
            case RARE -> Material.LIGHT_BLUE_DYE;
            case EPIC -> Material.PURPLE_DYE;
            case LEGENDARY -> Material.ORANGE_DYE;
            case MYTHICAL -> Material.RED_DYE;
        };
        ItemStack tab = new ItemStack(material);
        ItemMeta meta = tab.getItemMeta();
        meta.setDisplayName(plugin.getRarityManager().style(rarity, rarity.displayName()));
        meta.setLore(List.of(selected
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "SHOWING THIS TIER"
                : ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO FILTER"));
        tab.setItemMeta(meta);
        return tab;
    }

    /**
     * The player's own card, top right. It's their head rather than a
     * book because this panel is about them, not about the collection —
     * and it carries the two numbers the sidebar doesn't already show
     * (rolls and playtime) plus the per-rarity breakdown, which is the
     * thing you actually want while staring at a wall of entries.
     */
    private static ItemStack buildProfile(SolRNGPlugin plugin, Player player, PlayerData data,
                                          Rarity filter, int shownCount) {
        int discovered = data.getDiscoveredItems().size();
        int total = plugin.getRarityManager().getItems().size();

        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = info.getItemMeta();
        // Not every meta is a SkullMeta on every server build, so the head
        // degrades to a blank one rather than throwing.
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + player.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "YOUR INDEX");
        lore.add("");
        lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Discovered: " + ChatColor.AQUA + discovered
                + ChatColor.DARK_GRAY + "/" + ChatColor.AQUA + total);
        lore.add(Lore.bar(total <= 0 ? 0.0 : (double) discovered / total));
        lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Shinies: " + ChatColor.AQUA
                + data.getDiscoveredShiny().size() + ChatColor.DARK_GRAY + "/" + ChatColor.AQUA + total);
        lore.add(ChatColor.GREEN + "▎ " + ChatColor.GRAY + "Index Luck: " + ChatColor.GREEN
                + String.format("%.2f", plugin.getRarityManager().tagMultiplierFor(data)) + "x");

        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "BY RARITY");
        Map<Rarity, Integer> found = new EnumMap<>(Rarity.class);
        Map<Rarity, Integer> tierTotal = new EnumMap<>(Rarity.class);
        Map<Rarity, Integer> shinyFound = new EnumMap<>(Rarity.class);
        for (RollableItem item : plugin.getRarityManager().getItems()) {
            tierTotal.merge(item.getRarity(), 1, Integer::sum);
            if (data.hasDiscovered(item.getDisplayName())) {
                found.merge(item.getRarity(), 1, Integer::sum);
            }
            if (data.hasDiscoveredShiny(item.getDisplayName())) {
                shinyFound.merge(item.getRarity(), 1, Integer::sum);
            }
        }
        for (Rarity rarity : Rarity.values()) {
            int have = found.getOrDefault(rarity, 0);
            int all = tierTotal.getOrDefault(rarity, 0);
            if (all == 0) continue;
            int shiny = shinyFound.getOrDefault(rarity, 0);
            lore.add(plugin.getRarityManager().style(rarity, "▎ " + rarity.displayName() + ": ")
                    + (have >= all ? ChatColor.GREEN : ChatColor.GRAY) + have
                    + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + all
                    + (shiny > 0 ? ChatColor.DARK_GRAY + "   " + ChatColor.AQUA + "✦ " + shiny : ""));
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Rolls: " + ChatColor.YELLOW
                + String.format("%,d", data.getTotalRolls()));
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Playtime: " + ChatColor.YELLOW
                + playtime(player));

        if (filter != null) {
            lore.add("");
            lore.add(ChatColor.GRAY + "Showing: " + plugin.getRarityManager().style(filter, filter.displayName())
                    + ChatColor.GRAY + " (" + shownCount + ")");
        }
        meta.setLore(lore);
        info.setItemMeta(meta);
        return info;
    }

    /** PLAY_ONE_MINUTE is misnamed — it counts ticks, not minutes. */
    private static String playtime(Player player) {
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long seconds = ticks / 20L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    /** Same black pane the other menus use as filler/section break. */
    private static ItemStack divider() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack buildPageButton(boolean next, int page, int totalPages) {
        ItemStack button = new ItemStack(next ? Material.ARROW : Material.SPECTRAL_ARROW);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + (next ? "Next Page ▶" : "◀ Previous Page"));
        meta.setLore(List.of(ChatColor.GRAY + "Page " + (page + 1) + "/" + totalPages));
        button.setItemMeta(meta);
        return button;
    }

    private static ItemStack buildEntry(SolRNGPlugin plugin, PlayerData data, RollableItem item) {
        boolean discovered = data.hasDiscovered(item.getDisplayName());
        boolean shiny = data.hasDiscoveredShiny(item.getDisplayName());

        ItemStack icon = new ItemStack(discovered ? item.getMaterial() : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();

        List<String> lore = new ArrayList<>();
        if (discovered) {
            meta.setDisplayName(RollFormat.displayName(plugin, item));
            lore.add(Lore.section(ChatColor.AQUA, "The drop"));
            lore.addAll(RollFormat.lore(plugin, item));
            lore.add("");
            lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + "Discovered  "
                    + ChatColor.GREEN + Lore.TICK);
            // The shiny is a second, rarer find of the same drop, so it's a
            // line on the entry rather than an entry of its own.
            lore.add(shiny
                    ? ChatColor.AQUA + Lore.BULLET + " " + ChatColor.GRAY + "Shiny found  "
                            + ChatColor.AQUA + Lore.SPARK
                    : ChatColor.DARK_GRAY + Lore.BULLET + " Shiny not found");
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO EQUIP AS YOUR TAG");

            meta.getPersistentDataContainer().set(plugin.getRollListener().getRarityKey(),
                    PersistentDataType.STRING, item.getRarity().name());
            meta.getPersistentDataContainer().set(plugin.getRollListener().getRollNameKey(),
                    PersistentDataType.STRING, item.getDisplayName());
        } else {
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "???"));
            lore.add(Lore.section(ChatColor.AQUA, "What's known"));
            lore.add(Lore.stat(ChatColor.AQUA, "Rarity",
                    ChatColor.stripColor(item.getRarity().displayName())));
            // The odds show even before it's found — that's the hook that
            // makes an undiscovered slot worth chasing.
            lore.add(Lore.stat(ChatColor.AQUA, "Chance", RollFormat.chance(item.getOdds())));
            lore.add(Lore.stat(ChatColor.AQUA, "Index Luck",
                    String.format("%.2f", item.getLuckMultiplier()) + "x"));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "NOT YET DISCOVERED");
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Shiny not found");
        }
        meta.setLore(lore);
        // A glint on the entry marks the shiny as caught — the same signal
        // the shiny item itself carries.
        meta.setEnchantmentGlintOverride(shiny ? Boolean.TRUE : null);
        icon.setItemMeta(meta);
        return icon;
    }
}
