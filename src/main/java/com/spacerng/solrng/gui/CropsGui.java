package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.farming.CropType;
import com.spacerng.solrng.farming.FarmPlotManager;
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
 * /crops — pick what the shared farm looks like for you. Changing it
 * repaints every plot in range immediately; nobody else's field changes.
 */
public class CropsGui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public static NamespacedKey cropKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_crop_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        CropsHolder holder = new CropsHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Your Crops");
        holder.setInventory(inv);

        ItemStack filler = filler();
        for (int slot = 0; slot < 36; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        FarmPlotManager farm = plugin.getFarmPlotManager();
        CropType selected = farm.cropFor(data);

        int i = 0;
        for (CropType crop : farm.getCrops().values()) {
            if (i >= SLOTS.length) break;
            inv.setItem(SLOTS[i], buildIcon(plugin, data, farm, crop,
                    selected != null && selected.getId().equals(crop.getId())));
            i++;
        }

        inv.setItem(31, info(plugin, data, farm));
        return inv;
    }

    private static ItemStack buildIcon(SolRNGPlugin plugin, PlayerData data, FarmPlotManager farm,
                                       CropType crop, boolean selected) {
        boolean unlocked = farm.isUnlocked(data, crop);

        ItemStack icon = new ItemStack(unlocked ? seedItem(crop.getMaterial()) : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName((selected ? ChatColor.GREEN : unlocked ? ChatColor.YELLOW : ChatColor.DARK_GRAY)
                + "" + ChatColor.BOLD + crop.getDisplay());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Per harvest:");
        lore.add(ChatColor.YELLOW + "◆ " + String.format("%,d", crop.getTokens()) + " Tokens");
        if (crop.getShards() > 0) {
            boolean shards = farm.shardsUnlocked(data);
            lore.add((shards ? ChatColor.AQUA : ChatColor.DARK_GRAY) + "◆ "
                    + String.format("%,d", crop.getShards()) + " Gems"
                    + (shards ? "" : ChatColor.DARK_GRAY + " (locked)"));
        }
        lore.add("");
        if (selected) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "GROWING NOW");
        } else if (unlocked) {
            lore.add(ChatColor.YELLOW + "Click to plant this");
        } else {
            lore.add(ChatColor.RED + "Locked");
            lore.add(ChatColor.DARK_GRAY + "Needs: " + crop.getRequiresNode());
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(cropKey(plugin), PersistentDataType.STRING, crop.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    /** Crops aren't obtainable as items, so show the seed/food equivalent. */
    private static Material seedItem(Material cropBlock) {
        return switch (cropBlock) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            default -> cropBlock.isItem() ? cropBlock : Material.WHEAT_SEEDS;
        };
    }

    private static ItemStack info(SolRNGPlugin plugin, PlayerData data, FarmPlotManager farm) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Your Farm");
        meta.setLore(List.of(
                ChatColor.GRAY + "The field is shared, but the crop on it",
                ChatColor.GRAY + "is yours alone — nobody else sees your",
                ChatColor.GRAY + "choice, or your harvests.",
                "",
                ChatColor.GRAY + "Harvested: " + ChatColor.WHITE + String.format("%,d", data.getCropsHarvested()),
                ChatColor.GRAY + "Gem payouts: "
                        + (farm.shardsUnlocked(data) ? ChatColor.GREEN + "Unlocked" : ChatColor.RED + "Locked")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
