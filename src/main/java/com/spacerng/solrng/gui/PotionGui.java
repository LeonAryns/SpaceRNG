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
 * /brewer - the brewing shelf.
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
    // V356: the shelf and the cupboard are two menus now, /brewer and
    // /potions, so each one carries the way to the other in its bottom
    // left corner. Brewing and drinking were one command with two
    // aliases before this and which menu /potions opened was down to
    // which of the two Bukkit registered first.
    public static final int POTIONS_SLOT = 45;

    public static NamespacedKey potionKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key( "solrng_potion_id");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PotionHolder holder = new PotionHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                MenuStyle.title("Brewer", "#FF7AD9", "#C77DFF"));
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
            if (!consumable.isForSale()) continue;
            if (i >= SLOTS.length) break;
            inv.setItem(SLOTS[i], buildEntry(plugin, player, data, consumable));
            i++;
        }

        inv.setItem(DROPS_SLOT, buildDrops(plugin, player, data));
        inv.setItem(POTIONS_SLOT, crossLink(plugin, data));
        MenuStyle.apply(inv, MenuStyle.Palette.PURPLE);
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
        lore.addAll(plugin.getConsumableManager().describe(consumable));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Price"));
        lore.addAll(price);
        lore.add("");
        lore.add(affordable
                ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to brew"
                : ChatColor.RED + "" + ChatColor.BOLD + "Not enough drops");
        if (affordable) {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Shift-click brews five.");
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(affordable ? Boolean.TRUE : null);
        meta.getPersistentDataContainer().set(potionKey(plugin), PersistentDataType.STRING, consumable.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The corner button over to /potions, with what is waiting there. */
    private static ItemStack crossLink(SolRNGPlugin plugin, PlayerData data) {
        long stored = 0L;
        for (Long count : data.getStoredBoosters().values()) {
            if (count != null && count > 0L) stored += count;
        }
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Your Potions"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "The cupboard, not the shelf");
        lore.add("");
        lore.add(Lore.line(ChatColor.GRAY, "What you brew here waits there"));
        lore.add(Lore.line(ChatColor.GRAY, "until you drink it."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Waiting", String.format("%,d", stored)));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open /potions");
        meta.setLore(lore);
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta potion) {
            // A plain potion item draws the pink swirl and the "Uncraftable
            // Potion" hover; the colour is set and the effects hidden so it
            // reads as a bottle rather than as a drink somebody can take.
            potion.setColor(org.bukkit.Color.fromRGB(0xEA, 0x80, 0xFC));
            // HIDE_POTION_EFFECTS is gone on 1.21.11; HIDE_ADDITIONAL_TOOLTIP
            // is the one flag that covers it.
            potion.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
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
