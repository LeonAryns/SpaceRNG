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
 * /novacore - a climb drawn as something you climb.
 *
 * The tiers used to snake through the whole menu, which meant the eye had
 * to follow a route it could not see: twenty identical panes scattered
 * over six rows with black filler between them read as decoration, not as
 * a ladder. They now fill bottom row first and run upward, so the shape on
 * screen is the shape of the thing - you start at the bottom left and the
 * top right rung is the one worth all the money.
 *
 * Colour carries state: green behind you, a glinting yellow rung ahead,
 * grey for everything still out of reach, and checkpoints keep their own
 * icon at every state because they are the part worth planning around.
 */
public class NovaCoreGui {

    /**
     * The rungs, bottom row first so the ladder climbs.
     *
     * Twenty-one slots for a twenty-tier ladder: the spare is the top
     * right, which stays empty unless max-tier is raised.
     */
    private static final int[] PATH_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,   // bottom rung row, tiers 1-7
            19, 20, 21, 22, 23, 24, 25,   // middle, tiers 8-14
            10, 11, 12, 13, 14, 15, 16,   // top, tiers 15-21
    };

    public static final int FORGE_SLOT = 49;
    private static final int INFO_SLOT = 47;
    private static final int HELD_SLOT = 51;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        NovaCoreHolder holder = new NovaCoreHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.getNovaCoreManager().styledTitle());
        holder.setInventory(inv);

        frame(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        NovaCoreManager nova = plugin.getNovaCoreManager();
        int tier = data.getNovaTier();

        int rungs = Math.min(nova.getMaxTier(), PATH_SLOTS.length);
        for (int t = 1; t <= rungs; t++) {
            inv.setItem(PATH_SLOTS[t - 1], buildTier(nova, t, tier));
        }

        inv.setItem(INFO_SLOT, buildInfo(data, nova, tier));
        inv.setItem(FORGE_SLOT, buildForge(plugin, data, nova, tier));
        inv.setItem(HELD_SLOT, buildHeld(nova, tier));
        return inv;
    }

    /**
     * Cyan on the border, black inside.
     *
     * The whole menu used to be black, which left the two buttons in the
     * bottom row floating in a void with nothing to sit on.
     */
    private static void frame(Inventory inv) {
        ItemStack rim = pane(Material.CYAN_STAINED_GLASS_PANE, " ");
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int column = slot % 9;
            int row = slot / 9;
            inv.setItem(slot, row == 0 || row == 5 || column == 0 || column == 8 ? rim : fill);
        }
    }

    private static ItemStack buildTier(NovaCoreManager nova, int tier, int current) {
        boolean cleared = tier <= current;
        boolean next = tier == current + 1;
        boolean checkpoint = nova.isCheckpoint(tier);

        Material material;
        if (checkpoint) {
            material = cleared ? Material.ENDER_EYE : Material.ENDER_PEARL;
        } else if (cleared) {
            material = Material.LIME_STAINED_GLASS_PANE;
        } else if (next) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
        } else {
            // Grey, not yellow. Every unforged rung looking like the next
            // one made the menu a wall of yellow with nothing to aim at.
            material = Material.GRAY_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(
                cleared ? ChatColor.GREEN : next ? ChatColor.YELLOW : ChatColor.DARK_GRAY,
                "Tier " + tier + (checkpoint ? " " + Lore.SPARK : "")));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state(cleared ? "forged" : next ? "next up" : "locked"));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Holding this tier"));
        // One line per thing it multiplies, each in that thing's own
        // colour. One grey sentence doing three jobs did none of them.
        String times = String.format("%.2f", nova.multiplierAt(tier)) + "x";
        lore.add(Lore.stat(ChatColor.GREEN, "Luck", times));
        lore.add(Lore.stat(Currency.MONEY.colour(), "Money", times));
        lore.add(Lore.stat(Currency.COINS.colour(), "Coins", times));

        // What this rung is worth over the one below it, which is the
        // number that actually decides whether the gamble is worth taking.
        if (tier > 1) {
            double step = nova.multiplierAt(tier) / nova.multiplierAt(tier - 1);
            lore.add(Lore.stat(ChatColor.DARK_AQUA, "Over tier " + (tier - 1),
                    String.format("%.2f", step) + "x"));
        }

        if (checkpoint) {
            lore.add("");
            lore.add(Lore.line(ChatColor.AQUA, "Checkpoint. A shatter never"));
            lore.add(Lore.line(ChatColor.AQUA, "drops you below this rung."));
        }

        meta.setLore(lore);
        // Only the rung you are attempting glints, so the eye lands on it.
        meta.setEnchantmentGlintOverride(next ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildInfo(PlayerData data, NovaCoreManager nova, int tier) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nova.styledName());

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("how it works"));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "The climb"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "Every forge is a gamble. Win and"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "you climb a rung. Lose and the"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "Core shatters back to a checkpoint."));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Where you are"));
        lore.add(Lore.stat(ChatColor.AQUA, "Tier", tier + " / " + nova.getMaxTier()));
        lore.add(Lore.bar(nova.getMaxTier() <= 0 ? 0.0 : (double) tier / nova.getMaxTier()));
        lore.add(Lore.stat(ChatColor.YELLOW, "Best ever", String.valueOf(data.getNovaBestTier())));
        lore.add(Lore.stat(ChatColor.GREEN, "Safety net", "tier " + nova.checkpointBelow(tier)));
        lore.add(Lore.stat(ChatColor.DARK_AQUA, "Checkpoints", nova.checkpointList()));
        lore.add("");
        lore.add(Lore.footnote("Your Luck raises the odds of a forge."));
        lore.add(Lore.footnote("The Core's own multiplier does not."));
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /** What the tier you are holding right now is worth, in one place. */
    private static ItemStack buildHeld(NovaCoreManager nova, int tier) {
        ItemStack item = new ItemStack(tier > 0 ? Material.NETHER_STAR : Material.GLASS_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(tier > 0 ? ChatColor.LIGHT_PURPLE : ChatColor.DARK_GRAY,
                "Your Core"));

        List<String> lore = new ArrayList<>();
        if (tier <= 0) {
            lore.add(Lore.state("unforged"));
            lore.add("");
            lore.add(Lore.line(ChatColor.GRAY, "You are holding nothing yet."));
            lore.add(Lore.line(ChatColor.GREEN, "The first forge cannot fail."));
        } else {
            lore.add(Lore.state("tier " + tier));
            lore.add("");
            String times = String.format("%.2f", nova.multiplierAt(tier)) + "x";
            lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Right now"));
            lore.add(Lore.stat(ChatColor.GREEN, "Luck", times));
            lore.add(Lore.stat(Currency.MONEY.colour(), "Money", times));
            lore.add(Lore.stat(Currency.COINS.colour(), "Coins", times));
            lore.add("");
            lore.add(Lore.footnote("Applied on top of everything else you own."));
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(tier > 0 ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildForge(SolRNGPlugin plugin, PlayerData data,
                                        NovaCoreManager nova, int tier) {
        boolean maxed = tier >= nova.getMaxTier();
        long cost = nova.costFor(data, tier);
        double luck = plugin.getPrestigeManager().baseLuck(data);
        double chance = nova.chanceAt(tier, luck);
        boolean affordable = data.getTokens() >= cost;

        // A compass, because this button is a gamble on a direction: it is
        // the one thing in the menu you press rather than read.
        ItemStack item = new ItemStack(maxed ? Material.NETHER_STAR : Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(maxed
                ? Lore.title(ChatColor.GREEN, "Fully Forged")
                : Lore.title(ChatColor.YELLOW, "Forge Tier " + (tier + 1)));

        List<String> lore = new ArrayList<>();
        if (maxed) {
            lore.add(Lore.state("maxed"));
            lore.add("");
            lore.add(Lore.line(ChatColor.GREEN, "There is nothing left to climb."));
            meta.setLore(lore);
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            item.setItemMeta(meta);
            return item;
        }

        boolean certain = chance >= 0.999;
        ChatColor odds = certain ? ChatColor.GREEN
                : chance >= 0.5 ? ChatColor.GREEN
                : chance >= 0.2 ? ChatColor.YELLOW : ChatColor.RED;

        lore.add(Lore.state(certain ? "guaranteed" : "a gamble"));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "This attempt"));
        lore.add(Lore.stat(odds, "Success", certain ? "100%"
                : String.format("%.1f%%", chance * 100.0)));
        lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                + ChatColor.GRAY + "Cost: " + Currency.COINS.price(cost, affordable));
        lore.add(Lore.stat(certain ? ChatColor.GREEN : ChatColor.RED, "On fail",
                certain ? "cannot fail" : "back to tier " + nova.checkpointBelow(tier)));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "What you would gain"));
        String from = String.format("%.2f", nova.multiplierAt(tier)) + "x";
        String to = String.format("%.2f", nova.multiplierAt(tier + 1)) + "x";
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, from + ChatColor.DARK_GRAY + " to "
                + ChatColor.LIGHT_PURPLE + to + ChatColor.DARK_GRAY
                + " on Luck, Money and Coins"));
        lore.add("");
        lore.add(affordable
                ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to forge"
                : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Coins");

        meta.setLore(lore);
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
