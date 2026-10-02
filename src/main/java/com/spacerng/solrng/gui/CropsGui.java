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
 * /crops - pick what the shared farm looks like for you. Changing it
 * repaints every plot in range immediately; nobody else's field changes.
 */
public class CropsGui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public static NamespacedKey cropKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key( "solrng_crop_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        CropsHolder holder = new CropsHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, MenuStyle.title("Crops", "#B9F6CA", "#00C853"));
        holder.setInventory(inv);

        ItemStack filler = filler();
        for (int slot = 0; slot < 36; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        FarmPlotManager farm = plugin.getFarmPlotManager();
        CropType selected = farm.cropFor(data);

        // V277: one centred row while the crops fit in it, so six read as a
        // ladder rather than as a list that stopped early.
        List<CropType> crops = new ArrayList<>(farm.getCrops().values());
        int[] slots = crops.size() <= 7 ? centred(crops.size()) : SLOTS;
        for (int i = 0; i < crops.size() && i < slots.length; i++) {
            CropType crop = crops.get(i);
            inv.setItem(slots[i], buildIcon(plugin, data, farm, crop,
                    selected != null && selected.getId().equals(crop.getId()), i + 1, crops.size()));
        }

        inv.setItem(31, info(plugin, data, farm));
        MenuStyle.apply(inv, MenuStyle.Palette.GREEN);
        return inv;
    }

    /** The middle of the row for n crops, with the centre left open when n is even. */
    private static int[] centred(int n) {
        int[][] rows = {
                {}, {13}, {12, 14}, {12, 13, 14}, {11, 12, 14, 15},
                {11, 12, 13, 14, 15}, {10, 11, 12, 14, 15, 16}, {10, 11, 12, 13, 14, 15, 16}};
        return rows[Math.max(0, Math.min(7, n))];
    }

    private static ItemStack buildIcon(SolRNGPlugin plugin, PlayerData data, FarmPlotManager farm,
                                       CropType crop, boolean selected, int place, int of) {
        boolean unlocked = farm.isUnlocked(data, crop);

        // The real crop even while locked (V277): grey dye said nothing
        // about what you are working toward.
        ItemStack icon = new ItemStack(seedItem(crop.getMaterial()));
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Lore.title(
                selected ? ChatColor.GREEN : unlocked ? ChatColor.YELLOW : ChatColor.DARK_GRAY,
                crop.getDisplay()));
        meta.setEnchantmentGlintOverride(selected ? Boolean.TRUE : null);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Crop " + place + " of " + of);
        lore.add("");
        // V287: the unlock first, then what it pays. "Per harvest" over a
        // Coins and a Gems line read as a price to Leon, so it says Earns.
        long unlockAt = farm.unlockAt(crop);
        if (unlockAt > 0) {
            lore.add(Lore.stat(unlocked ? ChatColor.GREEN : ChatColor.AQUA, "Unlocks at",
                    String.format("%,d", unlockAt) + " crops farmed"));
            lore.add("");
        }
        lore.add(Lore.section(ChatColor.GREEN, "Earns per crop"));
        lore.add(Currency.COINS.colour() + Lore.BULLET + " " + Currency.COINS.exact(crop.getTokens()));
        if (crop.getShards() > 0) {
            boolean gems = farm.shardsUnlocked(data);
            lore.add((gems ? Currency.GEMS.colour() : ChatColor.DARK_GRAY) + Lore.BULLET + " "
                    + (gems ? Currency.GEMS.exact(crop.getShards())
                            : ChatColor.DARK_GRAY + Currency.GEMS.icon() + " "
                                    + String.format("%,d", crop.getShards()) + " Gems (locked)"));
        }
        lore.add("");
        if (selected) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Growing now");
        } else if (unlocked) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to plant");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            long at = farm.unlockAt(crop);
            if (at > 0) {
                // V279: opens with farming, no price.
                long have = Math.min(at, data.getCropsHarvested());
                lore.add(Lore.stat(ChatColor.AQUA, "Crops farmed",
                        String.format("%,d", have) + " / " + String.format("%,d", at)));
                lore.add(Lore.bar(have / (double) at));
                lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Opens by itself, no cost");
            } else {
                lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Unlock it in "
                        + ChatColor.YELLOW + "/farmtree");
            }
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
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> cropBlock.isItem() ? cropBlock : Material.WHEAT_SEEDS;
        };
    }

    private static ItemStack info(SolRNGPlugin plugin, PlayerData data, FarmPlotManager farm) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Your Farm"));
        meta.setLore(List.of(
                Lore.section(ChatColor.GREEN, "How it works"),
                Lore.line(ChatColor.GREEN, "The field is shared, but the crop"),
                Lore.line(ChatColor.GREEN, "on it is yours alone."),
                "",
                Lore.section(ChatColor.AQUA, "Information"),
                Lore.stat(ChatColor.AQUA, "Harvested", String.format("%,d", data.getCropsHarvested())),
                Lore.stat(ChatColor.AQUA, "Gem payouts",
                        farm.shardsUnlocked(data) ? "Unlocked" : "Locked")));
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
