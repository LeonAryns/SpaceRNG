package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.ArmorManager;
import com.spacerng.solrng.player.ArmorTier;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
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
 * /armor shop: each tier shows its full 4-piece set (helmet/chest/legs/
 * boots) as individual icons, two tiers per row. Clicking any piece buys
 * the whole set — the Luck bonus only applies once all 4 pieces are
 * actually worn (see ArmorManager). Bottom-right corner shows a running
 * drop total across every rarity, since armor spans the full cost range.
 */
public class ArmorGui {

    // Two tiers per row: left tier at cols 0-3, right tier at cols 5-8.
    private static final String[] TIER_ORDER = {
            "LEATHER", "CHAINMAIL", "IRON", "GOLD", "DIAMOND", "NETHERITE"
    };
    private static final int[] LEFT_SLOTS = {9, 10, 11, 12};
    private static final int[] RIGHT_SLOTS = {14, 15, 16, 17};
    private static final int STATS_SLOT = 44;

    public static NamespacedKey tierIdKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_armor_tier_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        ArmorHolder holder = new ArmorHolder();
        Inventory inv = Bukkit.createInventory(holder, 45, ChatColor.GOLD + "" + ChatColor.BOLD + "Armor Shop");
        holder.setInventory(inv);

        ItemStack filler = glassFiller();
        for (int slot = 0; slot < 45; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ArmorManager armor = plugin.getArmorManager();
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        NamespacedKey tierIdKey = tierIdKey(plugin);

        for (int i = 0; i < TIER_ORDER.length; i++) {
            ArmorTier tier = armor.get(TIER_ORDER[i]);
            if (tier == null) continue;

            int row = i / 2;
            int[] slots = (i % 2 == 0) ? LEFT_SLOTS : RIGHT_SLOTS;
            int rowOffset = row * 9;

            Material[] pieces = {tier.helmet(), tier.chestplate(), tier.leggings(), tier.boots()};
            for (int p = 0; p < pieces.length; p++) {
                inv.setItem(rowOffset + slots[p], buildPieceIcon(plugin, player, data, armor, tier, pieces[p], rarityKey, tierIdKey));
            }
        }

        inv.setItem(STATS_SLOT, buildDropTotals(plugin, player, data));

        return inv;
    }

    private static ItemStack buildPieceIcon(SolRNGPlugin plugin, Player player, PlayerData data, ArmorManager armor,
                                             ArmorTier tier, Material pieceMaterial, NamespacedKey rarityKey, NamespacedKey tierIdKey) {
        boolean owned = data.hasPurchasedArmor(tier.getId());
        boolean canAfford = armor.canAfford(player, tier, rarityKey);

        ItemStack icon = new ItemStack(pieceMaterial);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName((owned ? ChatColor.GREEN : ChatColor.AQUA) + tier.getDisplay());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_AQUA + "+" + Math.round(tier.getLuckBonus() * 100) + "% Luck "
                + ChatColor.GRAY + "(while full set worn)");
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "Owned");
        } else {
            lore.add(ChatColor.GRAY + "Cost:");
            for (Map.Entry<Rarity, Long> cost : tier.getCosts().entrySet()) {
                String color = plugin.getRarityManager().colorFor(cost.getKey());
                lore.add(ChatColor.GRAY + " - " + color + cost.getValue() + " " + cost.getKey().displayName());
            }
            lore.add(canAfford ? ChatColor.GREEN + "Click to buy!" : ChatColor.RED + "Not enough drops");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(tierIdKey, PersistentDataType.STRING, tier.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack glassFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack buildDropTotals(SolRNGPlugin plugin, Player player, PlayerData data) {
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();

        ItemStack stats = new ItemStack(Material.HOPPER);
        ItemMeta meta = stats.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Drop Totals");

        List<String> lore = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) {
            long held = countHeld(player, rarityKey, rarity);
            long converted = rarity == Rarity.COMMON ? data.getConvertedCommon()
                    : rarity == Rarity.UNCOMMON ? data.getConvertedUncommon() : 0L;
            String color = plugin.getRarityManager().colorFor(rarity);
            lore.add(color + rarity.displayName() + ": " + ChatColor.WHITE + (held + converted));
        }
        meta.setLore(lore);
        stats.setItemMeta(meta);
        return stats;
    }

    private static long countHeld(Player player, NamespacedKey rarityKey, Rarity rarity) {
        long total = 0L;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String rarityName = stack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (rarity.name().equals(rarityName)) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
