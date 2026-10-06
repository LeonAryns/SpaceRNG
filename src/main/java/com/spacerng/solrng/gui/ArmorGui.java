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
 * (helmet, chestplate, leggings, boots) - all 6 tiers side by side, with
 * light stained glass dividing left / middle / right. Clicking any piece
 * buys the whole set. Each piece grants its own Luck bonus independently
 * while worn (see ArmorManager) - no need for the full set.
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
    // V353: the bottom row carries one upgrade button under each tier.
    private static final int UPGRADE_ROW = 4;

    public static NamespacedKey tierIdKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key( "solrng_armor_tier_id");
    }

    /** Which slot's piece this icon sells - pieces are bought one at a time. */
    public static NamespacedKey pieceKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key( "solrng_armor_piece");
    }

    /** Set on the bottom-row buttons that level a tier up (V353). */
    public static NamespacedKey upgradeKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key( "solrng_armor_upgrade");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        ArmorHolder holder = new ArmorHolder();
        Inventory inv = Bukkit.createInventory(holder, 45, MenuStyle.title("Armor", "#FFE082", "#FF8F00"));
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
            inv.setItem(UPGRADE_ROW * 9 + col,
                    buildUpgradeIcon(plugin, player, data, armor, tier, tierIdKey));
        }

        inv.setItem(STATS_SLOT, buildDropTotals(plugin, player, data));

        MenuStyle.apply(inv, MenuStyle.Palette.GOLD);

        return inv;
    }

    private static ItemStack buildPieceIcon(SolRNGPlugin plugin, Player player, PlayerData data, ArmorManager armor,
                                             ArmorTier tier, ArmorPiece piece, NamespacedKey rarityKey,
                                             NamespacedKey tierIdKey, NamespacedKey pieceKey) {
        boolean owned = data.hasPurchasedArmor(tier.getId(), piece);
        boolean open = armor.tierOpen(data, tier.getId());
        int level = armor.levelOf(data, tier.getId());

        ItemStack icon = new ItemStack(tier.materialFor(piece));
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Lore.title(owned ? ChatColor.GREEN : open ? ChatColor.AQUA : ChatColor.DARK_GRAY,
                ChatColor.stripColor(tier.pieceDisplay(piece))));
        meta.setEnchantmentGlintOverride(owned ? Boolean.TRUE : null);

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "While worn"));
        // Exactly the stat block the real item carries, so what you see in
        // the shop is what you get, at the level this tier is on.
        lore.addAll(wornLines(armor, tier, Math.max(1, level)));
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Owned");
        } else if (!open) {
            // V353: the tier below has to be maxed first, and a Locked
            // footer always says the one way in.
            ArmorTier previous = armor.previousTier(tier.getId());
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Take "
                    + ChatColor.WHITE + (previous == null ? "the set below" : previous.getDisplay())
                    + ChatColor.GRAY + " to level " + armor.maxArmorLevel() + " first.");
        } else {
            boolean affordable = true;
            lore.add(Lore.section(ChatColor.YELLOW, "Price"));
            for (Map.Entry<Rarity, Long> cost : tier.costsFor(piece).entrySet()) {
                long held = DropWallet.total(plugin, player, data, cost.getKey());
                boolean enough = held >= cost.getValue();
                affordable &= enough;
                lore.add(Lore.requirement(
                        plugin.getRarityManager().style(cost.getKey(), cost.getKey().displayName()),
                        String.valueOf(held), String.valueOf(cost.getValue()), enough));
            }
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to buy this piece"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough drops");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(tierIdKey, PersistentDataType.STRING, tier.getId());
        meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece.name());
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * The While worn block at a given level. The numbers are the tier's own
     * scaled by ArmorManager, so the shop and the real item cannot drift.
     */
    private static List<String> wornLines(ArmorManager armor, ArmorTier tier, int level) {
        double scale = armor.levelScale(level);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "When Worn:");
        lore.add(ChatColor.AQUA + "◆ " + ChatColor.GRAY + "Luck: " + ChatColor.GREEN
                + "+" + Math.round(tier.getLuckBonus() * scale * 100) + "%");
        lore.add(ChatColor.AQUA + "◆ " + ChatColor.GRAY + "Speed: " + ChatColor.YELLOW
                + "+" + Math.round(tier.getSpeedBonus() * scale * 100));
        return lore;
    }

    /**
     * The bottom-row button that levels one tier up. It is the only place
     * drops are spent on something already owned, so it says what the next
     * level is worth as well as what it costs.
     */
    private static ItemStack buildUpgradeIcon(SolRNGPlugin plugin, Player player, PlayerData data,
                                              ArmorManager armor, ArmorTier tier, NamespacedKey tierIdKey) {
        int level = armor.levelOf(data, tier.getId());
        int max = armor.maxArmorLevel();
        boolean owned = level >= 1;
        boolean maxed = level >= max;

        ItemStack icon = new ItemStack(maxed ? Material.NETHER_STAR
                : owned ? Material.ANVIL : Material.STONE_BUTTON);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Lore.title(maxed ? ChatColor.GREEN : owned ? ChatColor.YELLOW : ChatColor.DARK_GRAY,
                tier.getDisplay() + " Level"));
        meta.setEnchantmentGlintOverride(maxed ? Boolean.TRUE : null);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Set upgrade");
        lore.add("");
        if (!owned) {
            lore.add(Lore.line(ChatColor.GRAY, "Buy a piece of this set to"));
            lore.add(Lore.line(ChatColor.GRAY, "start levelling it."));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Buy any piece above.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(tierIdKey, PersistentDataType.STRING, tier.getId());
            meta.getPersistentDataContainer().set(upgradeKey(plugin), PersistentDataType.STRING, "1");
            icon.setItemMeta(meta);
            return icon;
        }

        lore.add(Lore.line(ChatColor.GRAY, "Every level raises what each"));
        lore.add(Lore.line(ChatColor.GRAY, "worn piece of this set is worth."));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Your set"));
        lore.add(Lore.stat(ChatColor.AQUA, "Level", level + " / " + max));
        lore.add(Lore.stat(ChatColor.GREEN, "Luck each",
                "+" + Math.round(tier.getLuckBonus() * armor.levelScale(level) * 100) + "%"));
        if (!maxed) {
            lore.add(Lore.upgrade(ChatColor.GREEN, "Next",
                    "+" + Math.round(tier.getLuckBonus() * armor.levelScale(level) * 100) + "%",
                    "+" + Math.round(tier.getLuckBonus() * armor.levelScale(level + 1) * 100) + "%"));
        }
        lore.add(Lore.barMinimal(level / (double) max));
        lore.add("");

        if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
            ArmorTier next = nextTier(armor, tier.getId());
            if (next != null) {
                lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + next.getDisplay() + " is open.");
            }
        } else {
            Rarity rarity = armor.levelRarity(tier);
            long cost = armor.levelCost(tier, level);
            long held = rarity == null ? 0L : DropWallet.total(plugin, player, data, rarity);
            boolean enough = rarity != null && held >= cost;
            lore.add(Lore.section(ChatColor.YELLOW, "Price"));
            if (rarity != null) {
                lore.add(Lore.requirement(
                        plugin.getRarityManager().style(rarity, rarity.displayName()),
                        String.valueOf(held), String.valueOf(cost), enough));
            }
            lore.add("");
            lore.add(enough
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough drops");
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(tierIdKey, PersistentDataType.STRING, tier.getId());
        meta.getPersistentDataContainer().set(upgradeKey(plugin), PersistentDataType.STRING, "1");
        icon.setItemMeta(meta);
        return icon;
    }

    private static ArmorTier nextTier(ArmorManager armor, String tierId) {
        boolean found = false;
        for (Map.Entry<String, ArmorTier> entry : armor.getTiers().entrySet()) {
            if (found) return entry.getValue();
            if (entry.getKey().equals(tierId)) found = true;
        }
        return null;
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
        // Inventory plus banked - armor spends from both, so both count.
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
