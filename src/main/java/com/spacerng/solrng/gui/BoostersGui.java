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
 * /potions (V264 as /boosters): the potions Potion Finder digs up, and
 * everything brewed in /brewer, wait here instead of filling the
 * inventory. One icon per kind with how many are stored; a click drinks
 * one.
 */
public final class BoostersGui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    /** The corner button over to /brewer (V356). */
    public static final int BREWER_SLOT = 27;

    private BoostersGui() {
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        BoostersHolder holder = new BoostersHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, MenuStyle.title("Potions", "#EA80FC", "#7C4DFF"));
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

        inv.setItem(BREWER_SLOT, brewerLink(data));
        MenuStyle.apply(inv, MenuStyle.Palette.PURPLE);
        return inv;
    }

    /** The way back to the shelf, in the matching corner (V356). */
    private static ItemStack brewerLink(PlayerData data) {
        boolean unlocked = data.hasUnlocked("potion_unlock");
        ItemStack item = new ItemStack(Material.BREWING_STAND);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "The Brewer"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "The shelf, not the cupboard");
        lore.add("");
        lore.add(Lore.line(ChatColor.GRAY, "Brew a potion out of the drops"));
        lore.add(Lore.line(ChatColor.GRAY, "you have rolled."));
        lore.add("");
        lore.add(unlocked
                ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open /brewer"
                : ChatColor.RED + "" + ChatColor.BOLD + "Locked");
        if (!unlocked) {
            lore.add(Lore.line(ChatColor.DARK_GRAY, "Unlock Potions in /skilltree."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
