package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
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

public class OptionsGui {

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        OptionsHolder holder = new OptionsHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Options");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        inv.setItem(OptionsHolder.SOUND_SLOT, toggleItem(Material.NOTE_BLOCK,
                "Rolling Sound", data.isRollSoundEnabled(),
                "The click track while a roll is counting down."));
        inv.setItem(OptionsHolder.ANIMATION_SLOT, toggleItem(Material.ITEM_FRAME,
                "Rolling Animation", data.isRollAnimationEnabled(),
                "The item names flashing on screen mid-roll."));
        inv.setItem(OptionsHolder.AURA_SLOT, toggleItem(Material.FIREWORK_ROCKET,
                "Reveal Auras", data.isRevealAuraEnabled(),
                "Epic+ particle build-ups and their sound,",
                "yours and everyone else's. Off hides them",
                "for you only."));

        return inv;
    }

    private static ItemStack toggleItem(Material material, String label, boolean on, String... description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + label + ChatColor.GRAY + " — "
                + (on ? ChatColor.GREEN.toString() + ChatColor.BOLD + "On" : ChatColor.RED.toString() + ChatColor.BOLD + "Off"));

        List<String> lore = new ArrayList<>();
        for (String line : description) {
            lore.add(ChatColor.DARK_GRAY + line);
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to toggle");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
