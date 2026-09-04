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

import java.util.ArrayList;
import java.util.List;

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

        inv.setItem(PROGRESS_SLOT, buildProgressInfo(plugin, data, filter, shown.size()));
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
                ? ChatColor.YELLOW + "Selected — click to clear"
                : ChatColor.GRAY + "Click to filter"));
        tab.setItemMeta(meta);
        return tab;
    }

    private static ItemStack buildProgressInfo(SolRNGPlugin plugin, PlayerData data, Rarity filter, int shownCount) {
        int discovered = data.getDiscoveredItems().size();
        int total = plugin.getRarityManager().getItems().size();

        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Index Progress: " + discovered + "/" + total);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Every item carries its own Luck multiplier —");
        lore.add(ChatColor.GRAY + "the rarer the find, the bigger it is.");
        lore.add(ChatColor.GRAY + "Equip one as your tag to use its multiplier.");
        lore.add("");
        lore.add(ChatColor.GRAY + "Equipped multiplier: " + ChatColor.GREEN
                + String.format("%.2f", plugin.getRarityManager().tagMultiplierFor(data)) + "x");
        if (filter != null) {
            lore.add("");
            lore.add(ChatColor.GRAY + "Showing: " + plugin.getRarityManager().style(filter, filter.displayName())
                    + ChatColor.GRAY + " (" + shownCount + ")");
        }
        meta.setLore(lore);
        info.setItemMeta(meta);
        return info;
    }

    /** Same light-grey pane the other menus use as a section break. */
    private static ItemStack divider() {
        ItemStack pane = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
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

        ItemStack icon = new ItemStack(discovered ? item.getMaterial() : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();

        List<String> lore = new ArrayList<>();
        if (discovered) {
            meta.setDisplayName(RollFormat.displayName(plugin, item));
            lore.addAll(RollFormat.lore(plugin, item));
            lore.add("");
            lore.add(ChatColor.GREEN + "✔ Discovered");
            lore.add(ChatColor.YELLOW + "Click to equip as your tag");

            meta.getPersistentDataContainer().set(plugin.getRollListener().getRarityKey(),
                    PersistentDataType.STRING, item.getRarity().name());
            meta.getPersistentDataContainer().set(plugin.getRollListener().getRollNameKey(),
                    PersistentDataType.STRING, item.getDisplayName());
        } else {
            meta.setDisplayName(ChatColor.DARK_GRAY + "???");
            lore.add(ChatColor.GRAY + "Rarity: " + plugin.getRarityManager().style(item.getRarity(), item.getRarity().displayName()));
            lore.add(ChatColor.GRAY + "Index Luck: " + ChatColor.DARK_AQUA
                    + String.format("%.2f", item.getLuckMultiplier()) + "x");
            lore.add("");
            lore.add(ChatColor.RED + "Not yet discovered");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }
}
