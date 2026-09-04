package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
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
import java.util.Map;

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
        StarforgeManager starforge = plugin.getStarforgeManager();
        boolean owned = tier.getOrder() <= currentOrder;
        boolean isNext = tier.getOrder() == currentOrder + 1;
        boolean affordable = starforge.canAfford(player, tier);

        Material material = owned ? Material.LIME_DYE
                : isNext ? (affordable ? Material.NETHER_STAR : Material.RED_DYE)
                : Material.GRAY_DYE;

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName((owned ? ChatColor.GREEN : isNext ? ChatColor.WHITE : ChatColor.DARK_GRAY)
                + tier.getDisplay());

        // Same stat/controls block the held item shows, then the price.
        List<String> lore = new ArrayList<>(starforge.statLines(tier));
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "OWNED");
        } else if (isNext) {
            lore.add(ChatColor.GRAY + "Price:");
            for (Map.Entry<Rarity, Long> cost : tier.getCosts().entrySet()) {
                long held = starforge.countHeld(player, cost.getKey());
                boolean enough = held >= cost.getValue();
                lore.add(ChatColor.DARK_GRAY + "- " + (enough ? ChatColor.WHITE : ChatColor.RED)
                        + cost.getValue() + "x "
                        + plugin.getRarityManager().style(cost.getKey(),
                                cost.getKey().displayName().toUpperCase() + " ITEM")
                        + ChatColor.DARK_GRAY + " (" + held + ")");
            }
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO BUY"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH DROPS");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Forge the tiers before it first.");
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(tierIdKey(plugin), PersistentDataType.STRING, tier.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack buildBalance(SolRNGPlugin plugin, Player player, StarforgeTier current) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Your Drops");

        List<String> lore = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) {
            lore.add(plugin.getRarityManager().style(rarity, rarity.displayName() + ": ")
                    + ChatColor.WHITE + plugin.getStarforgeManager().countHeld(player, rarity));
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "Current: " + ChatColor.WHITE
                + (current == null ? "None" : current.getDisplay()));

        meta.setLore(lore);
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
