package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.consumable.Consumable;
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
 * /potion — the brewing shelf.
 *
 * Potions are bought with rolled drops rather than with any currency, so
 * the thing you spend to get luckier is the thing rolling produces. That
 * also means the shelf competes directly with /armor and /starforge for
 * the same drops, which is the choice it's there to create.
 */
public class PotionGui {

    // A shelf, not a grid: two rows of four, centred, with the drops panel
    // beneath so the price and the wallet are never far apart.
    private static final int[] SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};
    private static final int DROPS_SLOT = 49;

    public static NamespacedKey potionKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_potion_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PotionHolder holder = new PotionHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Brewing Shelf");
        holder.setInventory(inv);

        ItemStack frame = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            inv.setItem(slot, row == 0 || row == 5 || column == 0 || column == 8 ? frame : fill);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        int i = 0;
        for (Consumable consumable : plugin.getConsumableManager().getAll().values()) {
            // Only what's for sale: a reward with no price is something you
            // earn, and putting it on a shelf you can't buy from is noise.
            if (consumable.costs().isEmpty()) continue;
            if (i >= SLOTS.length) break;
            inv.setItem(SLOTS[i], buildEntry(plugin, player, data, consumable));
            i++;
        }

        inv.setItem(DROPS_SLOT, buildDrops(plugin, player, data));
        return inv;
    }

    private static ItemStack buildEntry(SolRNGPlugin plugin, Player player, PlayerData data,
                                        Consumable consumable) {
        boolean affordable = true;
        List<String> price = new ArrayList<>();
        for (Map.Entry<Rarity, Long> cost : consumable.costs().entrySet()) {
            long held = DropWallet.total(plugin, player, data, cost.getKey());
            boolean enough = held >= cost.getValue();
            affordable &= enough;
            price.add(Lore.requirement(
                    plugin.getRarityManager().style(cost.getKey(), cost.getKey().displayName()),
                    String.valueOf(held), String.valueOf(cost.getValue()), enough));
        }

        ItemStack item = new ItemStack(consumable.material());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(consumable.colors().isEmpty()
                ? Lore.title(ChatColor.LIGHT_PURPLE, consumable.display())
                : plugin.getRarityManager().buildStyle(consumable.colors(), true, false, false)
                        .apply(consumable.display()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "What it does"));
        if (!consumable.description().isEmpty()) {
            lore.add(Lore.line(ChatColor.AQUA, consumable.description()));
        }
        lore.add(plugin.getConsumableManager().describe(consumable));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Price"));
        lore.addAll(price);
        lore.add("");
        lore.add(affordable
                ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO BREW"
                : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH DROPS");
        if (affordable) {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Shift-click brews five.");
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(affordable ? Boolean.TRUE : null);
        meta.getPersistentDataContainer().set(potionKey(plugin), PersistentDataType.STRING, consumable.id());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildDrops(SolRNGPlugin plugin, Player player, PlayerData data) {
        ItemStack item = new ItemStack(Material.BREWING_STAND);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Your Drops"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.GOLD, "Spendable here"));
        for (Rarity rarity : Rarity.values()) {
            long banked = data.getBankedDrops(rarity);
            lore.add(plugin.getRarityManager().style(rarity, Lore.BULLET + " " + rarity.displayName() + ": ")
                    + ChatColor.WHITE + DropWallet.total(plugin, player, data, rarity)
                    + (banked > 0 ? ChatColor.DARK_GRAY + " (" + banked + " stored)" : ""));
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Held items first, then your bank.");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " The same drops buy armor and Starforges.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
