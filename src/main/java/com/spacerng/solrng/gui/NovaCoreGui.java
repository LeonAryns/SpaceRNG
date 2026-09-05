package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.cookie.NovaCoreManager;
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
 * /rngcookie — the Nova Core ladder as a board you climb.
 *
 * Tiers run left to right in rows: cleared ones are green, the tier you're
 * standing on is a Nova Core, and checkpoints are marked so the safe rungs
 * are obvious before you commit to a climb. The forge button at the bottom
 * always shows the real odds and the real price, because the whole appeal
 * is deciding whether to push one more step.
 */
public class NovaCoreGui {

    private static final int[] TIER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    public static final int FORGE_SLOT = 49;
    private static final int INFO_SLOT = 45;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        NovaCoreHolder holder = new NovaCoreHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Nova Core Tiers");
        holder.setInventory(inv);

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        NovaCoreManager nova = plugin.getNovaCoreManager();
        int tier = data.getNovaTier();

        for (int t = 1; t <= nova.getMaxTier() && t <= TIER_SLOTS.length; t++) {
            inv.setItem(TIER_SLOTS[t - 1], buildTier(nova, t, tier));
        }

        inv.setItem(INFO_SLOT, buildInfo(plugin, data, nova, tier));
        inv.setItem(FORGE_SLOT, buildForge(plugin, data, nova, tier));
        return inv;
    }

    private static ItemStack buildTier(NovaCoreManager nova, int tier, int current) {
        boolean cleared = tier <= current;
        boolean here = tier == current + 1;
        boolean checkpoint = nova.isCheckpoint(tier);

        Material material;
        if (here) {
            material = Material.HEART_OF_THE_SEA;          // the rung you're attempting
        } else if (checkpoint) {
            material = cleared ? Material.EMERALD : Material.ENDER_EYE;
        } else {
            material = cleared ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((cleared ? ChatColor.GREEN : here ? ChatColor.YELLOW : ChatColor.DARK_GRAY)
                + "" + ChatColor.BOLD + "Tier " + tier
                + (checkpoint ? ChatColor.AQUA + " ✦" : ""));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "NOVA CORE");
        lore.add("");
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Luck: " + ChatColor.LIGHT_PURPLE
                + String.format("%.2f", nova.multiplierAt(tier)) + "x");
        if (checkpoint) {
            lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Checkpoint — failures fall back here");
        }
        lore.add("");
        lore.add(cleared ? ChatColor.GREEN + "Already forged"
                : here ? ChatColor.YELLOW + "Next up"
                : ChatColor.DARK_GRAY + "Locked");

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(here ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildInfo(SolRNGPlugin plugin, PlayerData data, NovaCoreManager nova, int tier) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "NOVA CORE");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "PUSH YOUR LUCK");
        lore.add("");
        lore.add(ChatColor.GRAY + "Every forge either climbs a tier or");
        lore.add(ChatColor.GRAY + "drops you to the last checkpoint.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Tier: " + ChatColor.AQUA + tier
                + ChatColor.GRAY + "/" + ChatColor.AQUA + nova.getMaxTier());
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Multi: " + ChatColor.LIGHT_PURPLE
                + String.format("%.2f", nova.multiplierAt(tier)) + "x");
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Best ever: " + ChatColor.WHITE
                + data.getNovaBestTier());
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Safety net: " + ChatColor.WHITE
                + "tier " + nova.checkpointBelow(tier));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildForge(SolRNGPlugin plugin, PlayerData data, NovaCoreManager nova, int tier) {
        boolean maxed = tier >= nova.getMaxTier();
        long cost = nova.costFor(tier);
        double luck = plugin.getPrestigeManager().baseLuck(data);
        double chance = nova.chanceAt(tier, luck);
        boolean affordable = data.getTokens() >= cost;

        ItemStack item = new ItemStack(maxed ? Material.EMERALD_BLOCK : Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(maxed
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "FULLY FORGED"
                : ChatColor.YELLOW + "" + ChatColor.BOLD + "FORGE TIER " + (tier + 1));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "NOVA CORE");
        lore.add("");
        if (maxed) {
            lore.add(ChatColor.GREEN + "There's nothing left to climb.");
        } else {
            lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Success: "
                    + (chance >= 0.5 ? ChatColor.GREEN : chance >= 0.2 ? ChatColor.YELLOW : ChatColor.RED)
                    + String.format("%.1f%%", chance * 100.0));
            lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Cost: "
                    + (affordable ? ChatColor.WHITE : ChatColor.RED) + String.format("%,d", cost) + " Tokens");
            lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "On fail: " + ChatColor.RED + "back to tier "
                    + nova.checkpointBelow(tier));
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Your Luck raises the odds — the Nova");
            lore.add(ChatColor.DARK_GRAY + "Core's own multiplier doesn't.");
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO FORGE"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH TOKENS");
        }
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
