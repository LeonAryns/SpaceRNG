package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.ArmorManager;
import com.spacerng.solrng.player.ArmorTier;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /armor shop: one slot per tier showing the actual chestplate as the
 * icon. Buying grants the full 4-piece set — the Luck bonus only applies
 * once all 4 pieces are actually worn (see ArmorManager).
 */
public class ArmorGui {

    private static final int[] SLOTS = {10, 12, 14, 16, 20};

    public static NamespacedKey tierIdKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_armor_tier_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        ArmorHolder holder = new ArmorHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.GOLD + "" + ChatColor.BOLD + "Armor Shop");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ArmorManager armor = plugin.getArmorManager();
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        NamespacedKey tierIdKey = tierIdKey(plugin);

        int slot = 0;
        for (Map.Entry<String, ArmorTier> entry : orderedTiers(armor).entrySet()) {
            if (slot >= SLOTS.length) break;
            ArmorTier tier = entry.getValue();
            boolean owned = data.hasPurchasedArmor(tier.getId());
            boolean canAfford = armor.canAfford(player, tier, rarityKey);

            ItemStack icon = new ItemStack(tier.chestplate());
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName((owned ? ChatColor.GREEN : ChatColor.AQUA) + tier.getDisplay());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_AQUA + "+" + tier.getLuckBonus() + " Luck " + ChatColor.GRAY + "(while full set worn)");
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

            inv.setItem(SLOTS[slot], icon);
            slot++;
        }

        return inv;
    }

    private static Map<String, ArmorTier> orderedTiers(ArmorManager armor) {
        return new LinkedHashMap<>(armor.getTiers());
    }
}
