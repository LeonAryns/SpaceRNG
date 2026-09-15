package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /stash - items the plugin handed over while the inventory was full. The
 * first 45 stacks fill the top five rows; clicking one takes it, and the
 * hopper at the bottom takes as much as fits.
 */
public final class StashGui {

    public static final int ITEM_SLOTS = 45;
    public static final int TAKE_ALL_SLOT = 49;
    private static final int EMPTY_SLOT = 22;

    private StashGui() {
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        StashHolder holder = new StashHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.GOLD + "" + ChatColor.BOLD + "Stash");
        holder.setInventory(inv);

        List<ItemStack> stash = plugin.getPlayerDataManager().get(player.getUniqueId()).getStash();
        for (int i = 0; i < Math.min(ITEM_SLOTS, stash.size()); i++) {
            inv.setItem(i, stash.get(i).clone());
        }
        ItemStack frame = pane();
        for (int slot = ITEM_SLOTS; slot < 54; slot++) {
            inv.setItem(slot, frame);
        }
        inv.setItem(TAKE_ALL_SLOT, takeAll(stash.size()));
        if (stash.isEmpty()) inv.setItem(EMPTY_SLOT, empty());
        return inv;
    }

    private static ItemStack takeAll(int stacks) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Take everything"));
        List<String> lore = new ArrayList<>();
        if (stacks > 0) {
            lore.add(Lore.stat(ChatColor.GOLD, "Waiting", stacks + (stacks == 1 ? " stack" : " stacks")));
            if (stacks > ITEM_SLOTS) {
                lore.add(Lore.line(ChatColor.AQUA, "Showing the first " + ITEM_SLOTS + "."));
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to claim");
        } else {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Claimed");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack empty() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Your stash is empty"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.AQUA, "Anything that doesn't fit in"));
        lore.add(Lore.line(ChatColor.AQUA, "your inventory waits here."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
