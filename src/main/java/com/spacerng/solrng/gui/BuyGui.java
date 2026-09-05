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
        inv.setItem(BATTLEPASS_SLOT, comingSoon(Material.WRITTEN_BOOK, "BATTLE PASS",
                "A season of checkpoints with its own",
                "reward track, unlocked with Credits."));
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
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "GLOBAL LUCK BOOST");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "STORE");
        lore.add("");
        lore.add(ChatColor.GRAY + "Multiplies Luck for " + ChatColor.WHITE + "everyone");
        lore.add(ChatColor.GRAY + "on the server for "
                + ChatColor.WHITE + (plugin.getConfig().getInt("boost.duration-seconds", 900) / 60)
                + ChatColor.GRAY + " minutes.");
        lore.add("");
        if (boost.isActive()) {
            lore.add(ChatColor.LIGHT_PURPLE + "▎ " + ChatColor.GRAY + "Live now: "
                    + ChatColor.LIGHT_PURPLE + ChatColor.BOLD
                    + BoostManager.formatMultiplier(boost.multiplier()));
            lore.add(ChatColor.LIGHT_PURPLE + "▎ " + ChatColor.GRAY + "Time left: "
                    + ChatColor.WHITE + boost.timeLeftText());
            if (boost.getBoughtBy() != null) {
                lore.add(ChatColor.LIGHT_PURPLE + "▎ " + ChatColor.GRAY + "Started by: "
                        + ChatColor.WHITE + boost.getBoughtBy());
            }
        } else {
            lore.add(ChatColor.DARK_GRAY + "▎ " + ChatColor.GRAY + "Nothing running right now.");
        }
        lore.add("");
        if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "MAXED FOR THIS BOOST");
            lore.add(ChatColor.DARK_GRAY + "Buyable again once it expires.");
        } else {
            lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Next: "
                    + ChatColor.LIGHT_PURPLE + ChatColor.BOLD
                    + BoostManager.formatMultiplier(boost.nextMultiplier())
                    + ChatColor.RESET + ChatColor.GRAY + " for "
                    + (affordable ? ChatColor.WHITE : ChatColor.RED)
                    + String.format("%,d", cost) + " Credits");
            lore.add(ChatColor.DARK_GRAY + "Buying while it's live doubles it");
            lore.add(ChatColor.DARK_GRAY + "and resets the clock.");
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

    private static ItemStack comingSoon(Material material, String name, String... description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + name);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "STORE");
        lore.add("");
        for (String line : description) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Coming soon");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildBalance(PlayerData data) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "YOUR CREDITS");
        meta.setLore(List.of(
                ChatColor.DARK_GRAY + "STORE",
                "",
                ChatColor.YELLOW + "▎ " + ChatColor.WHITE + RollFormat.abbreviate(data.getPoints()) + " Credits",
                "",
                ChatColor.GRAY + "Credits can't be earned in game —",
                ChatColor.GRAY + "they only come from the web store."));
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
