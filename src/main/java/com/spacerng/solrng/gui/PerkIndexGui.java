package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkType;
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

import java.util.List;

/**
 * Every perk and what each of its five levels gives, and which of them this
 * player has rolled. It is also where confirmation is set: hovering a perk
 * and pressing 1 to 5 switches that level, clicking switches all five. A
 * perk and level with confirmation on asks before a roll replaces it.
 */
public class PerkIndexGui {

    private static final int SIZE = 45;
    private static final int BACK_SLOT = 0;
    private static final int INFO_SLOT = 4;
    private static final int[] TYPE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public static int backSlot() { return BACK_SLOT; }

    public static NamespacedKey typeKey() {
        return SolRNGPlugin.key("solrng_perk_type");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PerkIndexHolder holder = new PerkIndexHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Perk Index");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PerkManager perks = plugin.getPerkManager();

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack rail = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 ? rail : filler);

        int index = 0;
        int confirmations = 0;
        for (PerkType type : perks.types()) {
            if (index >= TYPE_SLOTS.length) break;
            inv.setItem(TYPE_SLOTS[index++], PerkLore.indexItem(plugin, data, type));
            for (int level = 1; level <= 5; level++) {
                if (data.getPerkConfirm().contains(type.key(level))) confirmations++;
            }
        }
        int total = perks.types().size() * 5;
        int found = 0;
        for (PerkType type : perks.types()) {
            for (int level = 1; level <= 5; level++) {
                if (data.getPerkFound().contains(type.key(level))) found++;
            }
        }

        inv.setItem(BACK_SLOT, back());
        inv.setItem(INFO_SLOT, info(found, total, confirmations));
        return inv;
    }

    private static ItemStack info(int found, int total, int confirmations) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Perk Index"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every perk and what each level gives."),
                "",
                Lore.stat(ChatColor.GREEN, "Found", found + " / " + total),
                "  " + Lore.bar(total == 0 ? 0.0 : (double) found / total),
                Lore.stat(ChatColor.GOLD, "Confirmation on", String.valueOf(confirmations)),
                "",
                Lore.footnote("Hover a perk and press 1 to 5 to switch a level.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack back() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Perks"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Roll a perk and buy Perk Tickets."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        item.setItemMeta(meta);
        return item;
    }

    /** The perk id on a clicked index item, or null. */
    public static String clickedType(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey(), PersistentDataType.STRING);
    }

    /** 12.5%, 0.4%, 0.0001%: two significant digits, never scientific, never zero. */
    static String percent(double fraction) {
        double pct = fraction * 100.0;
        if (pct >= 1.0) return String.format("%.1f%%", pct).replace(".0%", "%");
        if (pct <= 0.0) return "0%";
        return new java.math.BigDecimal(pct).round(new java.math.MathContext(2))
                .stripTrailingZeros().toPlainString() + "%";
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
