package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.firsts.FirstTenManager;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /firsts: who took the Server First spots, per rarity.
 *
 * A Server First happens a fixed number of times per rarity for the life
 * of the server and then never again, so the board of who holds them is
 * the one leaderboard that can never be overtaken. It is worth a screen
 * of its own rather than a line in /rngadmin.
 *
 * Nothing here is clickable. It is a thing to read.
 */
public class FirstsGui {

    private static final int SIZE = 45;
    private static final int SELF_SLOT = 4;
    private static final int[] RARITY_SLOTS = {20, 22, 24, 30, 32};

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        FirstsHolder holder = new FirstsHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Server Firsts");
        holder.setInventory(inv);

        ItemStack rail = pane(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 || i >= 36 ? rail : filler);

        inv.setItem(SELF_SLOT, selfIcon(plugin, player));

        List<Rarity> tracked = plugin.getFirstTenManager().trackedRarities();
        for (int i = 0; i < tracked.size() && i < RARITY_SLOTS.length; i++) {
            inv.setItem(RARITY_SLOTS[i], rarityIcon(plugin, tracked.get(i)));
        }
        return inv;
    }

    /** What this player holds, which is the first thing anybody opens this for. */
    private static ItemStack selfIcon(SolRNGPlugin plugin, Player player) {
        List<FirstTenManager.Spot> spots = plugin.getFirstTenManager().spotsOf(player.getUniqueId());
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Your Server Firsts"));

        List<String> lore = new ArrayList<>();
        if (spots.isEmpty()) {
            lore.add(ChatColor.GRAY + "You do not hold one yet.");
            lore.add("");
            lore.add(Lore.line(ChatColor.AQUA, "Roll a tracked rarity before"));
            lore.add(Lore.line(ChatColor.AQUA, "the spots run out."));
        } else {
            lore.add(Lore.stat(ChatColor.GOLD, "Spots held", String.valueOf(spots.size())));
            lore.add("");
            for (FirstTenManager.Spot spot : spots) {
                lore.add(ChatColor.GOLD + Lore.BULLET + " " + ChatColor.WHITE + "#" + spot.place()
                        + ChatColor.GRAY + " in "
                        + plugin.getRarityManager().style(spot.rarity(), spot.rarity().displayName()));
            }
        }
        lore.add("");
        lore.add(Lore.footnote("A spot taken is taken for the life of the server."));
        meta.setLore(lore);
        if (!spots.isEmpty()) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack rarityIcon(SolRNGPlugin plugin, Rarity rarity) {
        FirstTenManager firsts = plugin.getFirstTenManager();
        List<FirstTenManager.Entry> entries = firsts.entries(rarity);
        int slots = firsts.slots();
        int left = Math.max(0, slots - entries.size());

        ItemStack item = new ItemStack(iconFor(rarity));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getRarityManager().styleBold(rarity, rarity.displayName()));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "The first " + slots + " players ever to");
        lore.add(ChatColor.GRAY + "roll one, and what they found.");
        lore.add("");
        lore.add(Lore.stat(left > 0 ? ChatColor.GREEN : ChatColor.DARK_GRAY, "Taken",
                entries.size() + " of " + slots));
        lore.add("");
        if (entries.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Nobody has one yet.");
        } else {
            for (int i = 0; i < entries.size(); i++) {
                FirstTenManager.Entry entry = entries.get(i);
                lore.add(plugin.getRarityManager().style(rarity, Lore.BULLET + " #" + (i + 1)) + " "
                        + ChatColor.WHITE + entry.name()
                        + ChatColor.DARK_GRAY + "  " + drop(plugin, entry.item()));
            }
        }
        lore.add("");
        lore.add(left > 0
                ? ChatColor.YELLOW + "" + ChatColor.BOLD + (left == 1 ? "1 spot left" : left + " spots left")
                : ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "All taken");
        meta.setLore(lore);
        if (left <= 0) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * A drop's name the way it reads everywhere else in the plugin: its
     * own gradient, its own styling (V193). firsts.yml stores the plain
     * name, so it is looked up in the item table on the way out; an item
     * renamed or retired since it was won falls back to what was saved.
     */
    private static String drop(SolRNGPlugin plugin, String name) {
        if (name == null || name.isBlank()) return "";
        var item = plugin.getRarityManager().findByDisplayName(name);
        return item == null ? ChatColor.GRAY + name
                : com.spacerng.solrng.rarity.RollFormat.displayName(plugin, item);
    }

    /** The same icon each rarity wears on the aura switches in /options. */
    private static Material iconFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> Material.CONDUIT;
            case MYTHICAL -> Material.FIRE_CHARGE;
            case LEGENDARY -> Material.BLAZE_POWDER;
            case EPIC -> Material.WITHER_ROSE;
            default -> Material.NETHER_STAR;
        };
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
