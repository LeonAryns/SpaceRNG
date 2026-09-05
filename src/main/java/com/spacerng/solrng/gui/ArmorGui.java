package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.ArmorManager;
import com.spacerng.solrng.player.ArmorPiece;
import com.spacerng.solrng.player.ArmorTier;
import com.spacerng.solrng.player.DropWallet;
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
 * /armor shop: each tier is its own column, pieces stacked top to bottom
 * (helmet, chestplate, leggings, boots) — all 6 tiers side by side, with
 * light stained glass dividing left / middle / right. Clicking any piece
 * buys the whole set. Each piece grants its own Luck bonus independently
 * while worn (see ArmorManager) — no need for the full set.
 */
public class ArmorGui {

    // Column layout: 0=divider, 1-3=tiers, 4=divider, 5-7=tiers, 8=divider.
    private static final String[] TIER_ORDER = {
            "LEATHER", "CHAINMAIL", "IRON", "GOLD", "DIAMOND", "NETHERITE"
    };
    private static final int[] TIER_COLUMNS = {1, 2, 3, 5, 6, 7};
    private static final int[] DIVIDER_COLUMNS = {0, 4, 8};
    private static final int PIECE_ROWS = 4; // helmet, chestplate, leggings, boots
    private static final int STATS_SLOT = 44;

    public static NamespacedKey tierIdKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_armor_tier_id");
    }

    /** Which slot's piece this icon sells — pieces are bought one at a time. */
    public static NamespacedKey pieceKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_armor_piece");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        ArmorHolder holder = new ArmorHolder();
        Inventory inv = Bukkit.createInventory(holder, 45, ChatColor.GOLD + "" + ChatColor.BOLD + "Armor Shop");
        holder.setInventory(inv);

        ItemStack filler = glassFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 45; slot++) {
            inv.setItem(slot, filler);
        }

        ItemStack divider = glassFiller(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        for (int col : DIVIDER_COLUMNS) {
            for (int row = 0; row < PIECE_ROWS; row++) {
                inv.setItem(row * 9 + col, divider);
            }
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ArmorManager armor = plugin.getArmorManager();
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        NamespacedKey tierIdKey = tierIdKey(plugin);

        for (int i = 0; i < TIER_ORDER.length; i++) {
            ArmorTier tier = armor.get(TIER_ORDER[i]);
            if (tier == null) continue;
            int col = TIER_COLUMNS[i];

            ArmorPiece[] pieces = ArmorPiece.values(); // helmet, chest, legs, boots
            for (int row = 0; row < pieces.length; row++) {
                inv.setItem(row * 9 + col,
                        buildPieceIcon(plugin, player, data, armor, tier, pieces[row], rarityKey, tierIdKey, pieceKey(plugin)));
            }
        }

        inv.setItem(STATS_SLOT, buildDropTotals(plugin, player, data));

        return inv;
    }

    private static ItemStack buildPieceIcon(SolRNGPlugin plugin, Player player, PlayerData data, ArmorManager armor,
                                             ArmorTier tier, ArmorPiece piece, NamespacedKey rarityKey,
                                             NamespacedKey tierIdKey, NamespacedKey pieceKey) {
        boolean owned = data.hasPurchasedArmor(tier.getId(), piece);

        ItemStack icon = new ItemStack(tier.materialFor(piece));
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Lore.title(owned ? ChatColor.GREEN : ChatColor.AQUA,
                ChatColor.stripColor(tier.pieceDisplay(piece))));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "While worn"));
        // Exactly the stat block the real item carries, so what you see in
        // the shop is what you get.
        lore.addAll(armor.statLines(tier));
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "OWNED");
        } else {
            boolean affordable = true;
            lore.add(Lore.section(ChatColor.YELLOW, "Price"));
            for (Map.Entry<Rarity, Long> cost : tier.getCosts().entrySet()) {
                long held = DropWallet.total(plugin, player, data, cost.getKey());
                boolean enough = held >= cost.getValue();
                affordable &= enough;
                lore.add(Lore.requirement(
                        plugin.getRarityManager().style(cost.getKey(), cost.getKey().displayName()),
                        String.valueOf(held), String.valueOf(cost.getValue()), enough));
            }
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO BUY THIS PIECE"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH DROPS");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(tierIdKey, PersistentDataType.STRING, tier.getId());
        meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece.name());
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack glassFiller(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack buildDropTotals(SolRNGPlugin plugin, Player player, PlayerData data) {
        ItemStack stats = new ItemStack(Material.HOPPER);
        ItemMeta meta = stats.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Your Drops"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.GOLD, "Spendable here"));
        // Inventory plus banked — armor spends from both, so both count.
        for (Rarity rarity : Rarity.values()) {
            long banked = data.getBankedDrops(rarity);
            lore.add(plugin.getRarityManager().style(rarity, Lore.BULLET + " " + rarity.displayName() + ": ")
                    + ChatColor.WHITE + DropWallet.total(plugin, player, data, rarity)
                    + (banked > 0 ? ChatColor.DARK_GRAY + " (" + banked + " stored)" : ""));
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Held items first, then your bank.");
        meta.setLore(lore);
        stats.setItemMeta(meta);
        return stats;
    }

}
