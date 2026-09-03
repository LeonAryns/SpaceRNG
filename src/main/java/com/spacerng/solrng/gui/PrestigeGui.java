package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.PrestigeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class PrestigeGui {

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PrestigeHolder holder = new PrestigeHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.AQUA + "" + ChatColor.BOLD + "Level & Prestige");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PrestigeManager prestige = plugin.getPrestigeManager();

        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD
                + (data.getPrestige() > 0 ? "[P" + data.getPrestige() + "] " : "") + "Level " + data.getLevel());
        infoMeta.setLore(List.of(ChatColor.GRAY + "Lifetime rolls: " + ChatColor.WHITE + data.getTotalRolls()));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        long rollsNeeded = prestige.rollsNeededForNextLevel(data);
        boolean canLevel = prestige.canLevelUp(data);
        ItemStack levelItem = new ItemStack(canLevel ? Material.LIME_DYE : Material.YELLOW_DYE);
        ItemMeta levelMeta = levelItem.getItemMeta();
        levelMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Level Up");
        levelMeta.setLore(List.of(
                ChatColor.GRAY + "Rolls: " + ChatColor.WHITE + data.getTotalRolls() + ChatColor.GRAY + "/" + ChatColor.WHITE + rollsNeeded,
                canLevel ? ChatColor.GREEN + "Click to level up!" : ChatColor.RED + "Keep rolling to level up."
        ));
        levelItem.setItemMeta(levelMeta);
        inv.setItem(PrestigeHolder.LEVEL_SLOT, levelItem);

        int levelsNeeded = prestige.levelsNeededForNextPrestige(data);
        boolean canPrestige = prestige.canPrestige(data);
        ItemStack prestigeItem = new ItemStack(canPrestige ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta prestigeMeta = prestigeItem.getItemMeta();
        prestigeMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Prestige");
        prestigeMeta.setLore(List.of(
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + data.getLevel() + ChatColor.GRAY + "/" + ChatColor.WHITE + levelsNeeded,
                ChatColor.GRAY + "Resets your level, grants a permanent",
                ChatColor.GRAY + "Luck " + ChatColor.WHITE + "multiplier" + ChatColor.GRAY + " (stacks with prestige).",
                canPrestige ? ChatColor.GREEN + "Click to prestige!" : ChatColor.RED + "Not enough levels yet."
        ));
        prestigeItem.setItemMeta(prestigeMeta);
        inv.setItem(PrestigeHolder.PRESTIGE_SLOT, prestigeItem);

        return inv;
    }
}
