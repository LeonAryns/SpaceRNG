package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import java.util.List;

public class ConvertGui {

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        ConvertHolder holder = new ConvertHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, ChatColor.DARK_GREEN + "Convert Items → Points");
        holder.setInventory(inv);

        // Glass border so players know slots 9-21 (minus confirm) aren't functional input.
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);
        for (int i = 9; i < 27; i++) {
            if (i == ConvertHolder.CONFIRM_SLOT) continue;
            inv.setItem(i, pane);
        }

        ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Convert!");
        confirmMeta.setLore(List.of(ChatColor.GRAY + "Converts every item placed",
                ChatColor.GRAY + "in the top row into points."));
        confirm.setItemMeta(confirmMeta);
        inv.setItem(ConvertHolder.CONFIRM_SLOT, confirm);

        // Auto-convert toggles, only meaningful once the auto_convert node is unlocked.
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        boolean autoConvertUnlocked = data.hasUnlocked("auto_convert");
        int slot = 27;
        for (Rarity rarity : Rarity.values()) {
            ItemStack toggle = new ItemStack(autoConvertUnlocked ? Material.HOPPER : Material.BARRIER);
            ItemMeta meta = toggle.getItemMeta();
            String color = plugin.getRarityManager().colorFor(rarity);
            boolean on = data.isAutoConverting(rarity);
            meta.setDisplayName(color + rarity.name() + ": " + (on ? ChatColor.GREEN + "AUTO-CONVERT ON" : ChatColor.RED + "OFF"));
            meta.setLore(List.of(autoConvertUnlocked
                    ? ChatColor.GRAY + "Click to toggle auto-convert for this rarity."
                    : ChatColor.RED + "Unlock 'Auto-Convert' in /skilltree first."));
            toggle.setItemMeta(meta);
            inv.setItem(slot, toggle);
            slot++;
        }

        return inv;
    }
}
