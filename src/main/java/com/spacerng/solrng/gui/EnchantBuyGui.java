package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.farming.HoeEnchantManager;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Levelling one hoe enchant (V161): +1, +10, +100 or as many as the
 * wallet covers, each button quoting its own price. The same screen opens
 * from the hoe menu and from an unlocked enchant in /farmtree, so both
 * places level an enchant the same way.
 */
public class EnchantBuyGui {

    public static Inventory build(SolRNGPlugin plugin, Player player, String enchantId, String tree, int page) {
        HoeEnchantManager hoe = plugin.getHoeEnchantManager();
        HoeEnchantManager.Enchant enchant = hoe.get(enchantId);
        EnchantBuyHolder holder = new EnchantBuyHolder(enchantId, tree, page);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_GREEN + "" + ChatColor.BOLD
                + (enchant == null ? "Enchant" : ChatColor.stripColor(enchant.display()))
                + ChatColor.GRAY + " - level up");
        holder.setInventory(inv);

        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        inv.setItem(EnchantBuyHolder.BACK_SLOT, back(tree));
        if (enchant == null) return inv;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        inv.setItem(EnchantBuyHolder.INFO_SLOT, info(plugin, data, hoe, enchant));
        inv.setItem(EnchantBuyHolder.ONE_SLOT, button(data, hoe, enchant, 1, Material.LIME_DYE));
        inv.setItem(EnchantBuyHolder.TEN_SLOT, button(data, hoe, enchant, 10, Material.EMERALD));
        inv.setItem(EnchantBuyHolder.HUNDRED_SLOT, button(data, hoe, enchant, 100, Material.EMERALD_BLOCK));
        inv.setItem(EnchantBuyHolder.MAX_SLOT, maxButton(data, hoe, enchant));
        return inv;
    }

    private static ItemStack info(SolRNGPlugin plugin, PlayerData data, HoeEnchantManager hoe,
                                  HoeEnchantManager.Enchant enchant) {
        Material icon = Material.matchMaterial(enchant.icon());
        ItemStack item = new ItemStack(icon == null ? Material.ENCHANTED_BOOK : icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(enchant.colour() + "" + ChatColor.BOLD + enchant.display());
        int level = hoe.levelOf(data, enchant.id());
        int cap = hoe.maxLevelFor(data, enchant);
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.AQUA, enchant.description()));
        lore.add("");
        lore.add(Lore.stat(ChatColor.GREEN, "Now", hoe.describePower(data, enchant.id())));
        lore.add(Lore.stat(ChatColor.AQUA, "Level", String.format("%,d", level) + " / " + String.format("%,d", cap)));
        lore.add(Lore.bar(cap <= 0 ? 0.0 : level / (double) cap));
        lore.add(Lore.stat(ChatColor.YELLOW, "You have", Currency.COINS.amount(data.getTokens())));
        if (cap < enchant.maxLevel()) {
            lore.add("");
            lore.add(Lore.line(ChatColor.AQUA, "Enchant Mastery in /farmtree raises"));
            lore.add(Lore.line(ChatColor.AQUA, "the cap to " + String.format("%,d", enchant.maxLevel()) + "."));
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(PlayerData data, HoeEnchantManager hoe, HoeEnchantManager.Enchant enchant,
                                    int count, Material material) {
        long[] quote = hoe.priceOfNext(data, enchant, count);
        int levels = (int) quote[0];
        long price = quote[1];
        boolean affordable = levels > 0 && data.getTokens() >= price;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(affordable ? ChatColor.GREEN : ChatColor.RED,
                "+" + count + " level" + (count == 1 ? "" : "s")));
        List<String> lore = new ArrayList<>();
        if (levels == 0) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
        } else {
            if (levels < count) {
                lore.add(Lore.line(ChatColor.AQUA, "Only " + levels + " left before the cap."));
            }
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Cost: " + Currency.COINS.price(price, affordable));
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to buy"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Coins");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack maxButton(PlayerData data, HoeEnchantManager hoe, HoeEnchantManager.Enchant enchant) {
        long[] quote = hoe.affordable(data, enchant);
        int levels = (int) quote[0];
        boolean room = hoe.levelOf(data, enchant.id()) < hoe.maxLevelFor(data, enchant);

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(levels > 0 ? ChatColor.GREEN : ChatColor.RED, "Max"));
        List<String> lore = new ArrayList<>();
        if (!room) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
        } else if (levels == 0) {
            lore.add(Lore.line(ChatColor.AQUA, "Every level your Coins cover."));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Coins");
        } else {
            lore.add(Lore.line(ChatColor.AQUA, "Every level your Coins cover."));
            lore.add(Lore.stat(ChatColor.GREEN, "Levels", "+" + String.format("%,d", levels)));
            lore.add(ChatColor.YELLOW + Lore.BULLET + " " + ChatColor.GRAY + "Cost: "
                    + Currency.COINS.price(quote[1], true));
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to buy");
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack back(String tree) {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.YELLOW, tree == null ? "Back to your hoe" : "Back to the farm tree"));
        item.setItemMeta(meta);
        return item;
    }
}
