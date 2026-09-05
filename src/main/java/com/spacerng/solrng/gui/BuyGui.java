package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.boost.BoostManager;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.RollFormat;
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
 * /buy — the Credits store. Credits are the one currency gameplay can't
 * produce, so everything in here is genuinely bought.
 *
 * The luck boost is deliberately server-wide: a store item that helps
 * everyone is one other players cheer for rather than resent, and it makes
 * the escalating price defensible since the whole server rides on it.
 */
public class BuyGui {

    public static final int BOOST_SLOT = 20;
    public static final int BATTLEPASS_SLOT = 22;
    public static final int RANKS_SLOT = 24;
    private static final int BALANCE_SLOT = 40;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        BuyHolder holder = new BuyHolder();
        Inventory inv = Bukkit.createInventory(holder, 45,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "SpaceRNG Store");
        holder.setInventory(inv);

        ItemStack filler = pane();
        for (int slot = 0; slot < 45; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        inv.setItem(BOOST_SLOT, buildBoost(plugin, data));
        inv.setItem(BATTLEPASS_SLOT, buildPass(plugin, data));
        inv.setItem(RANKS_SLOT, comingSoon(Material.NAME_TAG, "RANKS",
                "Permanent perks and a coloured tag.",
                "Handled by the permissions plugin."));
        inv.setItem(BALANCE_SLOT, buildBalance(data));
        return inv;
    }

    private static ItemStack buildBoost(SolRNGPlugin plugin, PlayerData data) {
        BoostManager boost = plugin.getBoostManager();
        boolean maxed = boost.isMaxed();
        long cost = boost.nextCost();
        boolean affordable = data.getPoints() >= cost;

        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta meta = item.getItemMeta();
        // Named after what you'd actually be buying right now, so the shelf
        // reads "Global 2x Luck" rather than a generic product name.
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Global "
                + BoostManager.formatMultiplier(boost.nextMultiplier()) + " Luck"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "What it does"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "Multiplies Luck for everyone on"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "the server for "
                + (plugin.getConfig().getInt("boost.duration-seconds", 900) / 60) + " minutes."));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Right now"));
        if (boost.isActive()) {
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Live",
                    BoostManager.formatMultiplier(boost.multiplier())));
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Time left", boost.timeLeftText()));
            if (boost.getBoughtBy() != null) {
                lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Started by", boost.getBoughtBy()));
            }
        } else {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Nothing running right now.");
        }
        lore.add("");
        if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "MAXED FOR THIS BOOST");
            lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + "Buyable again once it expires.");
        } else {
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Next",
                    BoostManager.formatMultiplier(boost.nextMultiplier())));
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Cost: " + Currency.CREDITS.price(cost, affordable));
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Buying while it's live doubles it.");
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO BUY"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH CREDITS");
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(boost.isActive() ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The premium track, sold here as well as inside /pass. Credits are
     * spent in this menu, so a Credit purchase that only existed somewhere
     * else would be the one thing missing from the shop.
     */
    private static ItemStack buildPass(SolRNGPlugin plugin, PlayerData data) {
        com.spacerng.solrng.pass.PassManager pass = plugin.getPassManager();
        boolean owned = data.isPassPremium();

        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Premium Battle Pass"));

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, pass.getSeasonName()));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "A second reward track for the"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "whole season, including every"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "level you have already cleared."));
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "UNLOCKED");
            lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + "Claim it in "
                    + ChatColor.YELLOW + "/pass");
        } else {
            boolean affordable = data.getPoints() >= pass.getPremiumCost();
            lore.add(Lore.section(ChatColor.AQUA, "Information"));
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Cost: " + Currency.CREDITS.price(pass.getPremiumCost(), affordable));
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + ChatColor.DARK_GRAY + "You have "
                    + Currency.CREDITS.amount(data.getPoints()));
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO UNLOCK"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH CREDITS");
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(owned ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack comingSoon(Material material, String name, String... description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, name));

        List<String> lore = new ArrayList<>();
        for (String line : description) {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + line);
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "COMING SOON");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildBalance(PlayerData data) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Your Credits"));
        meta.setLore(List.of(
                Currency.CREDITS.colour() + Lore.BULLET + " " + Currency.CREDITS.amount(data.getPoints()),
                "",
                ChatColor.DARK_GRAY + Lore.BULLET + " Credits can't be earned in game —",
                ChatColor.DARK_GRAY + Lore.BULLET + " they only come from the web store."));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
