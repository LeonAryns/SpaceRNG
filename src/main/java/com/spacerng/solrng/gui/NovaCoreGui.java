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
 * /novacore — the ladder drawn as a path you can actually trace.
 *
 * The tiers snake through the menu rather than filling rows left to right,
 * so the climb reads as a route with a start and an end instead of a
 * spreadsheet. Colour carries the state: green behind you, yellow ahead,
 * and a glinting yellow pane on the rung you're about to attempt.
 */
public class NovaCoreGui {

    /**
     * The route, in (column, row) order — 1-indexed, converted to slots as
     * (row-1)*9 + (column-1). Tier 1 is the first entry.
     */
    private static final int[] PATH_SLOTS = {
            37, 28, 19, 10,   // (2,5) (2,4) (2,3) (2,2)  — up the left side
            11, 12,           // (3,2) (4,2)              — across the top
            21, 30, 39,       // (4,3) (4,4) (4,5)        — back down
            40, 41,           // (5,5) (6,5)              — across the bottom
            32, 23, 14,       // (6,4) (6,3) (6,2)        — up again
            15, 16,           // (7,2) (8,2)              — across
            25, 34, 43,       // (8,3) (8,4) (8,5)        — down the right
            44                // (9,5)                    — the last rung
    };

    public static final int FORGE_SLOT = 49;
    private static final int INFO_SLOT = 45;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        NovaCoreHolder holder = new NovaCoreHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.getNovaCoreManager().styledTitle());
        holder.setInventory(inv);

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        NovaCoreManager nova = plugin.getNovaCoreManager();
        int tier = data.getNovaTier();

        int rungs = Math.min(nova.getMaxTier(), PATH_SLOTS.length);
        for (int t = 1; t <= rungs; t++) {
            inv.setItem(PATH_SLOTS[t - 1], buildTier(nova, t, tier));
        }

        inv.setItem(INFO_SLOT, buildInfo(plugin, data, nova, tier));
        inv.setItem(FORGE_SLOT, buildForge(plugin, data, nova, tier));
        return inv;
    }

    private static ItemStack buildTier(NovaCoreManager nova, int tier, int current) {
        boolean cleared = tier <= current;
        boolean next = tier == current + 1;
        boolean checkpoint = nova.isCheckpoint(tier);

        // Checkpoints keep their own icon at every state — they're the part
        // of the route worth planning around.
        Material material;
        if (checkpoint) {
            material = cleared ? Material.ENDER_EYE : Material.ENDER_PEARL;
        } else if (cleared) {
            material = Material.GREEN_STAINED_GLASS_PANE;
        } else {
            material = Material.YELLOW_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((cleared ? ChatColor.GREEN : next ? ChatColor.YELLOW : ChatColor.GRAY)
                + "" + ChatColor.BOLD + "TIER " + tier
                + (checkpoint ? ChatColor.AQUA + " ✦" : ""));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "NOVA CORE");
        lore.add("");
        lore.add((cleared ? ChatColor.GREEN : ChatColor.YELLOW) + "▎ " + ChatColor.GRAY + "Luck: "
                + ChatColor.LIGHT_PURPLE + String.format("%.2f", nova.multiplierAt(tier)) + "x");
        if (checkpoint) {
            lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Checkpoint");
            lore.add(ChatColor.DARK_GRAY + "   A shatter never falls below here.");
        }
        lore.add("");
        if (cleared) {
            lore.add(ChatColor.GREEN + "✔ Forged");
        } else if (next) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "NEXT UP");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Locked");
        }

        meta.setLore(lore);
        // Only the rung you're attempting glints, so the eye lands on it.
        meta.setEnchantmentGlintOverride(next ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildInfo(SolRNGPlugin plugin, PlayerData data, NovaCoreManager nova, int tier) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nova.styledName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "PUSH YOUR LUCK");
        lore.add("");
        lore.add(ChatColor.GRAY + "Every forge climbs a tier, or drops");
        lore.add(ChatColor.GRAY + "you to the last checkpoint.");
        lore.add("");
        lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Tier: " + ChatColor.AQUA + ChatColor.BOLD + tier
                + ChatColor.GRAY + "/" + ChatColor.AQUA + nova.getMaxTier());
        lore.add(ChatColor.LIGHT_PURPLE + "▎ " + ChatColor.GRAY + "Multi: " + ChatColor.LIGHT_PURPLE
                + ChatColor.BOLD + String.format("%.2f", nova.multiplierAt(tier)) + "x");
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Best ever: " + ChatColor.WHITE
                + data.getNovaBestTier());
        lore.add(ChatColor.GREEN + "▎ " + ChatColor.GRAY + "Safety net: " + ChatColor.WHITE + "tier "
                + nova.checkpointBelow(tier));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Checkpoints: " + ChatColor.GRAY + nova.checkpointList());
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildForge(SolRNGPlugin plugin, PlayerData data, NovaCoreManager nova, int tier) {
        boolean maxed = tier >= nova.getMaxTier();
        long cost = nova.costFor(tier);
        double luck = plugin.getPrestigeManager().baseLuck(data);
        double chance = nova.chanceAt(tier, luck);
        boolean affordable = data.getTokens() >= cost;

        ItemStack item = new ItemStack(maxed ? Material.NETHER_STAR : Material.HEART_OF_THE_SEA);
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
            ChatColor odds = chance >= 0.5 ? ChatColor.GREEN : chance >= 0.2 ? ChatColor.YELLOW : ChatColor.RED;
            lore.add(odds + "▎ " + ChatColor.GRAY + "Success: " + odds + ChatColor.BOLD
                    + String.format("%.1f%%", chance * 100.0));
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + "▎ " + ChatColor.GRAY + "Cost: "
                    + (affordable ? ChatColor.WHITE : ChatColor.RED)
                    + String.format("%,d", cost) + ChatColor.GRAY + " Tokens");
            lore.add(ChatColor.RED + "▎ " + ChatColor.GRAY + "On fail: " + ChatColor.RED + "back to tier "
                    + nova.checkpointBelow(tier));
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Your Luck raises the odds. The Nova");
            lore.add(ChatColor.DARK_GRAY + "Core's own multiplier does not.");
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO FORGE"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH TOKENS");
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(maxed ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(name);
        pane.setItemMeta(meta);
        return pane;
    }
}
