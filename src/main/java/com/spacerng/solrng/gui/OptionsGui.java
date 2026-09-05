package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
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
 * /options. Reveal auras get one switch per rarity rather than a single
 * on/off, because the tiers are wildly different events — a Mythical once
 * a month is a spectacle, an Epic several times an hour can be a nuisance,
 * and one toggle can't express that.
 */
public class OptionsGui {

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        OptionsHolder holder = new OptionsHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Options");
        holder.setInventory(inv);

        ItemStack filler = pane();
        for (int slot = 0; slot < 27; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        inv.setItem(OptionsHolder.SOUND_SLOT, toggleItem(Material.NOTE_BLOCK,
                "Rolling Sound", data.isRollSoundEnabled(),
                "The click track while a roll counts down."));
        inv.setItem(OptionsHolder.ANIMATION_SLOT, toggleItem(Material.ITEM_FRAME,
                "Rolling Animation", data.isRollAnimationEnabled(),
                "The item names flashing on screen mid-roll."));

        inv.setItem(OptionsHolder.AURA_EPIC_SLOT, auraToggle(plugin, data, Rarity.EPIC, Material.WITHER_ROSE));
        inv.setItem(OptionsHolder.AURA_LEGENDARY_SLOT, auraToggle(plugin, data, Rarity.LEGENDARY, Material.BLAZE_POWDER));
        inv.setItem(OptionsHolder.AURA_MYTHICAL_SLOT, auraToggle(plugin, data, Rarity.MYTHICAL, Material.FIRE_CHARGE));

        return inv;
    }

    private static ItemStack auraToggle(SolRNGPlugin plugin, PlayerData data, Rarity rarity, Material material) {
        boolean on = data.isAuraEnabled(rarity);
        String name = plugin.getRarityManager().style(rarity, rarity.displayName()) + ChatColor.GRAY + " Aura";

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name + ChatColor.DARK_GRAY + " — "
                + (on ? ChatColor.GREEN.toString() + ChatColor.BOLD + "On"
                      : ChatColor.RED.toString() + ChatColor.BOLD + "Off"));
        meta.setLore(List.of(
                Lore.section(ChatColor.AQUA, "What it hides"),
                Lore.line(ChatColor.AQUA, "The build-up and burst for"),
                Lore.line(ChatColor.AQUA, rarity.displayName() + " drops — yours and"),
                Lore.line(ChatColor.AQUA, "everyone else's."),
                "",
                ChatColor.DARK_GRAY + Lore.BULLET + " Off only affects what you see.",
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO TOGGLE"));
        meta.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack toggleItem(Material material, String label, boolean on, String... description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.YELLOW, label) + ChatColor.DARK_GRAY + " — "
                + (on ? ChatColor.GREEN.toString() + ChatColor.BOLD + "On"
                      : ChatColor.RED.toString() + ChatColor.BOLD + "Off"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "What it controls"));
        for (String line : description) {
            lore.add(Lore.line(ChatColor.AQUA, line));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO TOGGLE");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
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
