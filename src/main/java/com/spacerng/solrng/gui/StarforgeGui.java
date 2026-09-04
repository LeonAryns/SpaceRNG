package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.starforge.StarforgeManager;
import com.spacerng.solrng.starforge.StarforgeTier;
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
 * /starforge — the upgrade shop. One row of tiers left to right in ladder
 * order: owned ones show green, the next one up is buyable, everything
 * past that is locked until you've worked your way there.
 */
public class StarforgeGui {

    private static final int[] TIER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19};
    private static final int BALANCE_SLOT = 22;

    public static NamespacedKey tierIdKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_starforge_tier_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        StarforgeHolder holder = new StarforgeHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Starforge");
        holder.setInventory(inv);

        ItemStack filler = filler();
        for (int slot = 0; slot < 27; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        StarforgeManager starforge = plugin.getStarforgeManager();
        StarforgeTier current = starforge.tierOf(data);
        int currentOrder = current == null ? 0 : current.getOrder();

        List<StarforgeTier> tiers = starforge.getOrderedTiers();
        for (int i = 0; i < tiers.size() && i < TIER_SLOTS.length; i++) {
            inv.setItem(TIER_SLOTS[i], buildTierIcon(plugin, player, tiers.get(i), currentOrder));
        }

        inv.setItem(BALANCE_SLOT, buildBalance(plugin, player, current));
        return inv;
    }

    private static ItemStack buildTierIcon(SolRNGPlugin plugin, Player player, StarforgeTier tier, int currentOrder) {
        boolean owned = tier.getOrder() <= currentOrder;
        boolean isNext = tier.getOrder() == currentOrder + 1;
        double balance = plugin.getStarforgeManager().balanceOf(player);
        boolean affordable = balance >= tier.getMoneyCost();

        Material material = owned ? Material.LIME_DYE
                : isNext ? (affordable ? Material.NETHER_STAR : Material.RED_DYE)
                : Material.GRAY_DYE;

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName((owned ? ChatColor.GREEN : isNext ? ChatColor.YELLOW : ChatColor.DARK_GRAY)
                + ChatColor.BOLD.toString() + tier.getDisplay());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_AQUA + "+" + StarforgeManager.formatPercent(tier.getLuckBonus()) + "% base Luck");
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "Owned");
        } else if (isNext) {
            lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + "$" + String.format("%,.0f", tier.getMoneyCost()));
            lore.add(affordable
                    ? ChatColor.GREEN + "Click to forge!"
                    : ChatColor.RED + "Not enough Money");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Upgrade the tiers before it first.");
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(tierIdKey(plugin), PersistentDataType.STRING, tier.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack buildBalance(SolRNGPlugin plugin, Player player, StarforgeTier current) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Your Balance");
        meta.setLore(List.of(
                ChatColor.GREEN + "$" + String.format("%,.0f", plugin.getStarforgeManager().balanceOf(player)),
                "",
                ChatColor.GRAY + "Current: " + ChatColor.WHITE
                        + (current == null ? "None" : current.getDisplay())
        ));
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
