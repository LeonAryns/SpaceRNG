package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
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
 * Read-only collection log: every rollable item, greyed out until the
 * player has actually rolled it at least once.
 */
public class IndexGui {

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        IndexHolder holder = new IndexHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Your Index");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        List<RollableItem> items = plugin.getRarityManager().getItems();
        int discovered = data.getDiscoveredItems().size();
        double luckPerItem = plugin.getConfig().getDouble("index.luck-per-item", 0.01);

        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Index Progress: " + discovered + "/" + items.size());
        infoMeta.setLore(List.of(
                ChatColor.GRAY + "Every new item adds " + ChatColor.GREEN + "+" + luckPerItem + " Luck" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "Luck from your index so far: " + ChatColor.GREEN + "+" + String.format("%.2f", discovered * luckPerItem)
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        int slot = 9;
        for (RollableItem item : items) {
            if (slot >= 54) break; // safety net if the item list ever outgrows the GUI
            inv.setItem(slot, buildEntry(plugin, data, item));
            slot++;
        }

        return inv;
    }

    private static ItemStack buildEntry(SolRNGPlugin plugin, PlayerData data, RollableItem item) {
        boolean discovered = data.hasDiscovered(item.getDisplayName());
        String color = plugin.getRarityManager().colorFor(item.getRarity());

        ItemStack icon = new ItemStack(discovered ? item.getMaterial() : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();

        List<String> lore = new ArrayList<>();
        if (discovered) {
            meta.setDisplayName(color + item.getDisplayName());
            lore.addAll(RollFormat.lore(plugin, item));
            lore.add("");
            lore.add(ChatColor.GREEN + "✔ Discovered");
        } else {
            meta.setDisplayName(ChatColor.DARK_GRAY + "???");
            lore.add(ChatColor.GRAY + "Rarity: " + color + item.getRarity().displayName());
            lore.add("");
            lore.add(ChatColor.RED + "Not yet discovered");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }
}
