package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
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
 * The respec, as its own screen (V159). It used to be one shift-click on
 * a flower in the corner of the tree, with the refund only named after it
 * had happened. Now the player sees what comes back, what it costs and
 * what is lost before anything is taken, and confirms or goes back.
 */
public class RespecGui {

    public static Inventory build(SolRNGPlugin plugin, Player player, String tree, int page) {
        RespecHolder holder = new RespecHolder(tree, page);
        Inventory inv = Bukkit.createInventory(holder, 27,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Respec" + ChatColor.GRAY + " - start the trees over");
        holder.setInventory(inv);

        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int cost = data.nextRespecCost();
        long have = data.totalShinies();
        long money = plugin.getSkillTreeManager().totalMoneySpent(data);
        long coins = plugin.getSkillTreeManager().totalCoinsSpent(data);
        int owned = 0;
        for (SkillNode node : plugin.getSkillTreeManager().getNodes().values()) {
            owned += plugin.getSkillTreeManager().levelOf(data, node);
        }
        boolean affordable = have >= cost;

        inv.setItem(RespecHolder.SUMMARY_SLOT, summary(money, coins, owned, cost, have, data.getRespecCount()));
        inv.setItem(RespecHolder.CONFIRM_SLOT, confirm(owned, affordable, cost));
        inv.setItem(RespecHolder.CANCEL_SLOT, cancel());
        return inv;
    }

    private static ItemStack summary(long money, long coins, int owned, int cost, long have, int times) {
        ItemStack item = new ItemStack(Material.WITHER_ROSE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "What a respec does"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.GREEN, "You get back"));
        lore.add(Lore.stat(ChatColor.GREEN, "Money", Currency.MONEY.amount(money)));
        lore.add(Lore.stat(ChatColor.GREEN, "Coins", Currency.COINS.amount(coins)));
        lore.add("");
        lore.add(Lore.section(ChatColor.RED, "You lose"));
        lore.add(Lore.stat(ChatColor.RED, "Skill levels", String.format("%,d", owned)));
        lore.add(Lore.line(ChatColor.RED, "Both trees start over, /skilltree"));
        lore.add(Lore.line(ChatColor.RED, "and /farmtree."));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Cost"));
        lore.add(Lore.stat(have >= cost ? ChatColor.GREEN : ChatColor.RED, "Shinies", cost + " of any rarity"));
        lore.add(Lore.stat(ChatColor.AQUA, "You have", String.valueOf(have)));
        lore.add(Lore.stat(ChatColor.AQUA, "Respecs so far", String.valueOf(times)));
        lore.add(Lore.line(ChatColor.AQUA, "Each one costs a shiny more."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack confirm(int owned, boolean affordable, int cost) {
        boolean ready = owned > 0 && affordable;
        ItemStack item = new ItemStack(ready ? Material.LIME_CONCRETE : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ready ? ChatColor.GREEN : ChatColor.RED, "Confirm respec"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GREEN, "Spends " + cost + " shin" + (cost == 1 ? "y" : "ies")
                + " and refunds everything."));
        lore.add("");
        if (owned == 0) {
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Nothing to refund");
        } else if (!affordable) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough shinies");
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to respec");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack cancel() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.YELLOW, "Go back"));
        meta.setLore(List.of(
                Lore.line(ChatColor.YELLOW, "Back to the skill tree. Nothing"),
                Lore.line(ChatColor.YELLOW, "is taken."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to go back"));
        item.setItemMeta(meta);
        return item;
    }
}
