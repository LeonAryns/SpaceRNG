package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /pv: private vault pages, one chest of storage each, with how many a
 * player may open coming from their rank.
 *
 * /pv on its own shows every page and which of them are open; /pv 1 goes
 * straight to the first. A page keeps its storage in the top five rows and
 * a grey glass bar along the bottom with the page buttons, so nothing in
 * the bar can be mistaken for something stored.
 */
public class PrivateVaultGui {

    /** Storage slots on a page. The sixth row is the button bar. */
    public static final int PAGE_SLOTS = 45;
    private static final int SIZE = 54;
    private static final int PREV_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int SELECTOR_SIZE = 45;
    private static final int[] SELECTOR_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public static int prevSlot() { return PREV_SLOT; }
    public static int nextSlot() { return NEXT_SLOT; }

    public static NamespacedKey pageKey() {
        return SolRNGPlugin.key("solrng_vault_page");
    }

    /** Every page, open or not, as its own chest. */
    public static Inventory buildSelector(SolRNGPlugin plugin, Player player) {
        PrivateVaultHolder holder = new PrivateVaultHolder();
        holder.setSelector(true);
        Inventory inv = Bukkit.createInventory(holder, SELECTOR_SIZE,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Private Vaults");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int open = plugin.getRankManager().vaultPages(data);
        int shown = Math.max(open, 8);

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SELECTOR_SIZE; i++) inv.setItem(i, filler);

        for (int i = 0; i < shown && i < SELECTOR_SLOTS.length; i++) {
            int page = i + 1;
            inv.setItem(SELECTOR_SLOTS[i], pageIcon(plugin, data, page, page <= open));
        }
        inv.setItem(40, summary(plugin, data, open));
        return inv;
    }

    /** One page of storage. */
    public static Inventory build(SolRNGPlugin plugin, Player player, int page) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int open = plugin.getRankManager().vaultPages(data);
        int clamped = Math.max(1, Math.min(page, Math.max(1, open)));

        PrivateVaultHolder holder = new PrivateVaultHolder();
        holder.setPage(clamped);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Vault " + clamped
                        + ChatColor.DARK_GRAY + " / " + open);
        holder.setInventory(inv);

        List<ItemStack> stored = data.getVaults().get(clamped);
        if (stored != null) {
            for (int i = 0; i < stored.size() && i < PAGE_SLOTS; i++) {
                inv.setItem(i, stored.get(i));
            }
        }

        ItemStack bar = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = PAGE_SLOTS; i < SIZE; i++) inv.setItem(i, bar);
        if (clamped > 1) {
            inv.setItem(PREV_SLOT, button(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous page",
                    "Vault " + (clamped - 1)));
        }
        if (clamped < open) {
            inv.setItem(NEXT_SLOT, button(Material.ARROW, ChatColor.YELLOW + "Next page",
                    "Vault " + (clamped + 1)));
        }
        inv.setItem(INFO_SLOT, button(Material.ENDER_CHEST, ChatColor.AQUA + "Vault " + clamped,
                "Click for every page"));
        return inv;
    }

    private static ItemStack pageIcon(SolRNGPlugin plugin, PlayerData data, int page, boolean open) {
        List<ItemStack> stored = data.getVaults().get(page);
        int used = 0;
        if (stored != null) {
            for (ItemStack item : stored) {
                if (item != null && item.getType() != Material.AIR) used++;
            }
        }
        ItemStack item = new ItemStack(open ? Material.ENDER_CHEST : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(open ? ChatColor.AQUA : ChatColor.DARK_GRAY, "Vault " + page));
        List<String> lore = new ArrayList<>();
        if (open) {
            lore.add(Lore.stat(ChatColor.AQUA, "Used", used + " / " + PAGE_SLOTS + " slots"));
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open");
        } else {
            lore.add(Lore.line(ChatColor.GRAY, "A higher rank opens more pages."));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "See /ranks"));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(pageKey(), PersistentDataType.INTEGER, page);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack summary(SolRNGPlugin plugin, PlayerData data, int open) {
        var tier = plugin.getRankManager().rankOf(data);
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Your vaults"));
        meta.setLore(List.of(
                Lore.stat(ChatColor.AQUA, "Open pages", String.valueOf(open)),
                Lore.stat(ChatColor.LIGHT_PURPLE, "Rank", plugin.getRankManager().styled(tier)),
                "",
                Lore.line(ChatColor.GRAY, "Every page holds 45 slots."),
                Lore.footnote("Open one straight away with /pv 2.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, String name, String sub) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(Lore.line(ChatColor.GRAY, sub)));
        item.setItemMeta(meta);
        return item;
    }

    /** The page number on a clicked selector item, or null. */
    public static Integer clickedPage(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(pageKey(), PersistentDataType.INTEGER);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
