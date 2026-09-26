package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkStat;
import com.spacerng.solrng.perk.PerkType;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * How a perk reads on a tooltip, shared by the perk menu and the perk
 * index: the name in its rarity's colours, what it boosts, and then every
 * level from I to V with its values, as Leon's reference showed it.
 */
public final class PerkLore {

    private PerkLore() { }

    public static String name(SolRNGPlugin plugin, PerkType type) {
        return plugin.getRarityManager().styleBold(type.rarity(), type.display());
    }

    /** "Money +50%, Coins +50%" for chat lines. */
    public static String shortStats(PerkType type, int level) {
        StringJoiner out = new StringJoiner(ChatColor.DARK_GRAY + ", ");
        for (PerkStat stat : type.stats().keySet()) {
            out.add(ChatColor.WHITE + stat.label() + " " + stat.colour() + stat.format(type.valueAt(stat, level)));
        }
        return out.toString();
    }

    /** "Money/Coins/Speed" in white with grey slashes. */
    private static String statNames(PerkType type) {
        StringJoiner out = new StringJoiner(ChatColor.GRAY + "/" + ChatColor.WHITE);
        for (PerkStat stat : type.stats().keySet()) out.add(stat.label());
        return ChatColor.WHITE + out.toString();
    }

    /** A perk with all five levels and each level's confirmation, as the perk index shows it. */
    public static ItemStack indexItem(SolRNGPlugin plugin, PlayerData data, PerkType type, boolean bedrock) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name(plugin, type));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + type.rarity().displayName() + " perk");
        lore.add("");
        lore.add(ChatColor.GRAY + "This perk gives you an extra");
        lore.add(statNames(type) + ChatColor.GRAY + " boost.");
        lore.add("");
        int found = 0;
        for (int level = 1; level <= 5; level++) {
            String key = type.key(level);
            boolean has = data.getPerkFound().contains(key);
            if (has) found++;
            String head = plugin.getRarityManager().style(type.rarity(), PerkType.roman(level))
                    + (has ? ChatColor.GREEN + " " + Lore.TICK : "") + ChatColor.DARK_GRAY + "  ";
            List<String> parts = new ArrayList<>();
            for (PerkStat stat : type.stats().keySet()) {
                parts.add(ChatColor.WHITE + stat.label() + " " + ChatColor.GRAY + stat.format(type.valueAt(stat, level)));
            }
            String status = data.getPerkConfirm().contains(key)
                    ? ChatColor.GREEN + "Confirmation on" : ChatColor.DARK_GRAY + "Confirmation off";
            if (parts.size() <= 3) {
                lore.add(head + String.join("  ", parts) + "  " + status);
            } else {
                lore.add(head + String.join("  ", parts.subList(0, 3)));
                lore.add("      " + String.join("  ", parts.subList(3, parts.size())) + "  " + status);
            }
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Chance per roll", PerkIndexGui.percent(perks.chanceOf(type))));
        lore.add("");
        if (bedrock) {
            // No number keys in a Bedrock menu (V223): a click moves the
            // level it starts asking from down by one, then back to none.
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to ask from one level lower");
            lore.add(Lore.footnote("A level with confirmation on asks"));
            lore.add(Lore.footnote("before a roll replaces it."));
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Press 1 to 5 to switch a level");
            lore.add(Lore.footnote("Click to switch all five. A level with"));
            lore.add(Lore.footnote("confirmation on asks before a roll replaces it."));
        }
        meta.setLore(lore);
        if (found >= 5) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(PerkIndexGui.typeKey(), PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The perk the player has now, with what it gives at their level. */
    public static ItemStack activeItem(SolRNGPlugin plugin, PlayerData data) {
        PerkType type = plugin.getPerkManager().activeType(data);
        if (type == null) {
            ItemStack item = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "No perk yet"));
            meta.setLore(List.of(
                    Lore.line(ChatColor.GRAY, "Roll one with a Perk Ticket."),
                    Lore.line(ChatColor.GRAY, "It boosts you until you roll again.")));
            item.setItemMeta(meta);
            return item;
        }
        int level = data.getActivePerkLevel();
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name(plugin, type) + " " + plugin.getRarityManager().styleBold(type.rarity(), PerkType.roman(level)));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + type.rarity().displayName() + " perk, level " + PerkType.roman(level));
        lore.add("");
        lore.add(Lore.section(ChatColor.GOLD, "Your boost"));
        for (PerkStat stat : type.stats().keySet()) {
            lore.add(Lore.stat(stat.colour(), stat.label(), stat.format(type.valueAt(stat, level))));
        }
        lore.add("");
        if (data.getPerkConfirm().contains(type.key(level))) {
            lore.add(Lore.line(ChatColor.GREEN, "Confirmation on: a roll asks first."));
        } else {
            lore.add(Lore.line(ChatColor.DARK_GRAY, "Confirmation off for this level."));
        }
        lore.add(Lore.footnote("A new roll replaces this perk."));
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }
}
