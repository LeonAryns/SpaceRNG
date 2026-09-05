package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.farming.HoeEnchantManager;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The hoe's own upgrade board, opened by right-clicking it.
 *
 * The split between this and /farmtree is deliberate: the tree decides
 * WHICH enchants you have access to, and this decides how strong they are.
 * One is a progression choice you make a handful of times; the other is a
 * Token sink you come back to constantly, and they'd fight each other in
 * one menu.
 */
public class HoeGui {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int INFO_SLOT = 49;

    public static NamespacedKey enchantKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_hoe_enchant");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        HoeHolder holder = new HoeHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Farming Enchants");
        holder.setInventory(inv);

        ItemStack filler = pane();
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        HoeEnchantManager hoe = plugin.getHoeEnchantManager();

        int i = 0;
        for (HoeEnchantManager.Enchant enchant : hoe.getEnchants().values()) {
            if (i >= SLOTS.length) break;
            inv.setItem(SLOTS[i], buildEnchant(plugin, data, hoe, enchant));
            i++;
        }

        inv.setItem(INFO_SLOT, buildInfo(data, hoe));
        return inv;
    }

    private static ItemStack buildEnchant(SolRNGPlugin plugin, PlayerData data, HoeEnchantManager hoe,
                                          HoeEnchantManager.Enchant enchant) {
        boolean unlocked = hoe.isUnlocked(data, enchant.id());
        int level = hoe.levelOf(data, enchant.id());
        boolean maxed = level >= enchant.maxLevel();
        long cost = hoe.costFor(enchant, level);
        boolean affordable = data.getTokens() >= cost;

        Material material = Material.matchMaterial(enchant.icon());
        if (material == null) material = Material.ENCHANTED_BOOK;

        ItemStack item = new ItemStack(unlocked ? material : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "[" + (unlocked ? ChatColor.GREEN : ChatColor.RED) + level
                + Lore.STAR + ChatColor.DARK_GRAY + "] " + enchant.styled(0));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + enchant.display().toUpperCase() + " CHANCE: "
                + ChatColor.AQUA + hoe.describePower(enchant, level));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Description"));
        for (String line : wrap(enchant.description())) {
            lore.add(ChatColor.AQUA + Lore.BULLET + " " + ChatColor.GRAY + line);
        }
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Information"));
        lore.add(ChatColor.AQUA + Lore.BULLET + " " + ChatColor.WHITE + "Level: "
                + ChatColor.GREEN + level + ChatColor.GRAY + " / " + ChatColor.RED
                + String.format("%,d", enchant.maxLevel()));
        if (!maxed) {
            lore.add(ChatColor.AQUA + Lore.BULLET + " " + ChatColor.WHITE + "Cost: "
                    + (affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.shorten(cost)
                    + ChatColor.GOLD + " Tokens");
        }
        lore.add("");

        if (!unlocked) {
            lore.add(ChatColor.AQUA + "" + ChatColor.BOLD + "LOCKED");
            lore.add(ChatColor.AQUA + "◇ " + ChatColor.GRAY + "Unlock it in "
                    + ChatColor.YELLOW + "/farmtree");
        } else if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "MAXED");
        } else {
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO UPGRADE"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH TOKENS");
            if (affordable) {
                lore.add(ChatColor.DARK_GRAY + "SHIFT CLICK TO BUY 10");
            }
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(unlocked && level > 0 ? Boolean.TRUE : null);
        meta.getPersistentDataContainer().set(enchantKey(plugin), PersistentDataType.STRING, enchant.id());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildInfo(PlayerData data, HoeEnchantManager hoe) {
        ItemStack item = new ItemStack(Material.WHEAT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Your Tokens"));
        meta.setLore(List.of(
                Lore.state("farming"),
                "",
                Lore.line(ChatColor.YELLOW, ChatColor.YELLOW + String.format("%,d", data.getTokens())
                        + ChatColor.GRAY + " Tokens"),
                "",
                ChatColor.GRAY + "Enchants are unlocked in " + ChatColor.YELLOW + "/farmtree",
                ChatColor.GRAY + "and levelled here with Tokens."));
        item.setItemMeta(meta);
        return item;
    }

    /** Splits a description into ~32-char lines so lore never runs off screen. */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() + word.length() + 1 > 32) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
