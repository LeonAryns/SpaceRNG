package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.consumable.Consumable;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /boosters (V264): the potions Potion Finder digs up wait here instead of
 * filling the inventory. One icon per kind with how many are stored; a
 * click drinks one.
 */
public final class BoostersGui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private BoostersGui() {
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        BoostersHolder holder = new BoostersHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, MenuStyle.title("Boosters", "#EA80FC", "#7C4DFF"));
        holder.setInventory(inv);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int slot = 0; slot < inv.getSize(); slot++) inv.setItem(slot, filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int index = 0;
        for (Map.Entry<String, Long> entry : data.getStoredBoosters().entrySet()) {
            if (entry.getValue() <= 0 || index >= SLOTS.length) continue;
            Consumable consumable = plugin.getConsumableManager().get(entry.getKey());
            if (consumable == null) continue;
            ItemStack item = plugin.getConsumableManager().build(consumable,
                    (int) Math.min(64L, entry.getValue()));
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add(Lore.stat(ChatColor.AQUA, "Stored", String.format("%,d", entry.getValue())));
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to use");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(SLOTS[index], item);
            holder.slots().put(SLOTS[index], entry.getKey());
            index++;
        }

        if (index == 0) {
            ItemStack none = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = none.getItemMeta();
            meta.setDisplayName(ChatColor.GRAY + "Nothing stored");
            meta.setLore(List.of(
                    Lore.line(ChatColor.DARK_GRAY, "Potions from Potion Finder wait here."),
                    Lore.line(ChatColor.DARK_GRAY, "Level it up in /farmtree.")));
            none.setItemMeta(meta);
            inv.setItem(13, none);
        }

        MenuStyle.apply(inv, MenuStyle.Palette.PURPLE);
        return inv;
    }
}
