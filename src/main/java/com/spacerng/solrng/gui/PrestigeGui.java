package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.PrestigeManager;
import com.spacerng.solrng.player.PrestigeUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /prestige — two screens sharing one holder.
 *
 * The main card answers the three questions a prestige actually raises,
 * in order: can I, what do I get, and what does it cost me. Requirements
 * carry their own progress bar and a tick or cross, so "not yet" is never
 * a mystery.
 */
public class PrestigeGui {

    public static final int LEVEL_SLOT = 20;
    public static final int PRESTIGE_SLOT = 24;
    public static final int UPGRADES_SLOT = 40;
    public static final int BACK_SLOT = 45;

    public static NamespacedKey upgradeKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_prestige_upgrade");
    }

    // ------------------------------------------------------------- main

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PrestigeHolder holder = new PrestigeHolder();
        Inventory inv = Bukkit.createInventory(holder, 45,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Prestige");
        holder.setInventory(inv);

        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PrestigeManager prestige = plugin.getPrestigeManager();

        inv.setItem(4, buildSummary(plugin, data, prestige));
        inv.setItem(LEVEL_SLOT, buildLevel(data, prestige));
        inv.setItem(PRESTIGE_SLOT, buildPrestige(plugin, data, prestige));
        inv.setItem(UPGRADES_SLOT, buildUpgradesButton(data, prestige));
        return inv;
    }

    private static ItemStack buildSummary(SolRNGPlugin plugin, PlayerData data, PrestigeManager prestige) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Prestige " + data.getPrestige()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("overview"));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Level", String.valueOf(data.getLevel())));
        lore.add(Lore.stat(ChatColor.GOLD, "Lifetime rolls", String.format("%,d", data.getTotalRolls())));
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Prestige Points", String.valueOf(data.getPrestigePoints())));
        lore.add("");
        lore.add(Lore.line(ChatColor.GREEN, "Each prestige multiplies all Luck."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildLevel(PlayerData data, PrestigeManager prestige) {
        long needed = prestige.rollsNeededForNextLevel(data);
        boolean can = prestige.canLevelUp(data);

        ItemStack item = new ItemStack(can ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(can ? ChatColor.GREEN : ChatColor.GRAY, "Level " + (data.getLevel() + 1)));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("level up"));
        lore.add("");
        lore.add(ChatColor.GRAY + "Rolling is what levels you.");
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Requirements"));
        lore.add(Lore.requirement("Rolls",
                Lore.shorten(data.getTotalRolls()), Lore.shorten(needed), can));
        lore.add(Lore.bar(needed <= 0 ? 1.0 : (double) data.getTotalRolls() / needed));
        lore.add("");
        lore.add(can
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO LEVEL UP"
                : ChatColor.RED + Lore.BULLET + " Keep rolling.");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(can ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildPrestige(SolRNGPlugin plugin, PlayerData data, PrestigeManager prestige) {
        int needed = prestige.levelsNeededForNextPrestige(data);
        boolean can = prestige.canPrestige(data);
        int next = data.getPrestige() + 1;

        double nowMulti = 1.0 + data.getPrestige() * plugin.getConfig()
                .getDouble("prestige.luck-multiplier-per-prestige", 0.10);
        double thenMulti = 1.0 + next * plugin.getConfig()
                .getDouble("prestige.luck-multiplier-per-prestige", 0.10);

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(can ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY, "Prestige " + next));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("ascend"));
        lore.add("");
        lore.add(ChatColor.GRAY + "Reset your level for permanent boosts.");
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Requirements"));
        lore.add(Lore.requirement("Level", String.valueOf(data.getLevel()), String.valueOf(needed), can));
        lore.add(Lore.bar(needed <= 0 ? 1.0 : (double) data.getLevel() / needed));
        lore.add("");
        lore.add(Lore.section(ChatColor.GREEN, "You gain"));
        lore.add(Lore.upgrade(ChatColor.GREEN, "Luck Multi",
                String.format("%.2fx", nowMulti), String.format("%.2fx", thenMulti)));
        lore.add(Lore.line(ChatColor.GREEN, ChatColor.LIGHT_PURPLE + "+" + prestige.getPointsPerPrestige()
                + " Prestige Point" + (prestige.getPointsPerPrestige() == 1 ? "" : "s")));
        lore.add("");
        lore.add(Lore.section(ChatColor.RED, "You lose"));
        lore.add(Lore.line(ChatColor.RED, data.getLevel() + " levels"));
        lore.add("");
        lore.add(can
                ? ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "CLICK TO ASCEND"
                : ChatColor.RED + Lore.BULLET + " Complete all requirements first");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(can ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUpgradesButton(PlayerData data, PrestigeManager prestige) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Prestige Upgrades"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("spend"));
        lore.add("");
        lore.add(ChatColor.GRAY + "Points are the part of prestige you");
        lore.add(ChatColor.GRAY + "choose how to spend.");
        lore.add("");
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Points", String.valueOf(data.getPrestigePoints())));
        lore.add(Lore.stat(ChatColor.AQUA, "Upgrades", String.valueOf(prestige.getUpgrades().size())));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO OPEN");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // --------------------------------------------------------- upgrades

    public static Inventory buildUpgrades(SolRNGPlugin plugin, Player player) {
        PrestigeHolder holder = new PrestigeHolder();
        holder.setUpgradesPage(true);

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Prestige Upgrades");
        holder.setInventory(inv);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PrestigeManager prestige = plugin.getPrestigeManager();

        for (PrestigeUpgrade upgrade : prestige.getUpgrades().values()) {
            if (upgrade.getSlot() < 0 || upgrade.getSlot() >= 54) continue;
            inv.setItem(upgrade.getSlot(), buildUpgrade(plugin, data, upgrade));
        }

        inv.setItem(BACK_SLOT, button(Material.PAINTING, ChatColor.YELLOW + "◀ Back",
                ChatColor.GRAY + "Return to Prestige"));
        inv.setItem(49, buildPointsPanel(data));
        return inv;
    }

    private static ItemStack buildUpgrade(SolRNGPlugin plugin, PlayerData data, PrestigeUpgrade upgrade) {
        int level = data.getUpgradeLevel(upgrade.getId());
        boolean maxed = level >= upgrade.getMaxLevel();
        boolean affordable = data.getPrestigePoints() >= upgrade.getCostPoints();

        Material material = Material.matchMaterial(upgrade.getIcon());
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(maxed ? ChatColor.GREEN : ChatColor.GOLD,
                Lore.STAR + " " + upgrade.getDisplay()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state(maxed ? "maxed" : "upgrade"));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Effect"));
        lore.add(Lore.line(ChatColor.GREEN, describe(upgrade) + " "
                + ChatColor.WHITE + format(upgrade.totalAt(level), upgrade.getUnit())));
        lore.add(Lore.line(ChatColor.DARK_GRAY, format(upgrade.getPerLevel(), upgrade.getUnit()) + " per level"));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Level"));
        lore.add(Lore.line(ChatColor.AQUA, ChatColor.GREEN + String.valueOf(level)
                + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE + upgrade.getMaxLevel()));
        lore.add(Lore.bar((double) level / upgrade.getMaxLevel()));
        lore.add("");
        if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "FULLY UPGRADED");
        } else {
            lore.add(Lore.section(ChatColor.YELLOW, "Cost"));
            lore.add((affordable ? ChatColor.LIGHT_PURPLE : ChatColor.RED) + Lore.BULLET + " "
                    + (affordable ? ChatColor.WHITE : ChatColor.RED) + upgrade.getCostPoints()
                    + ChatColor.GRAY + " Point" + (upgrade.getCostPoints() == 1 ? "" : "s"));
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO UPGRADE"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH POINTS");
            if (affordable) {
                lore.add(ChatColor.DARK_GRAY + "SHIFT CLICK TO MAX");
            }
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(maxed ? Boolean.TRUE : null);
        meta.getPersistentDataContainer().set(upgradeKey(plugin), PersistentDataType.STRING, upgrade.getId());
        item.setItemMeta(meta);
        return item;
    }

    private static String describe(PrestigeUpgrade upgrade) {
        return switch (upgrade.getEffect()) {
            case LUCK_BONUS -> "Luck Bonus";
            case TOKEN_BONUS -> "Token Bonus";
            case MONEY_BONUS -> "Money Bonus";
            case SHARD_BONUS -> "Gem Chance";
            case NOVA_ODDS -> "Nova Odds";
        };
    }

    private static String format(double value, String unit) {
        if ("%".equals(unit)) return "+" + String.format("%.1f", value * 100.0) + "%";
        if ("x".equals(unit)) return "+" + String.format("%.2f", value) + "x";
        return "+" + String.format("%.2f", value);
    }

    private static ItemStack buildPointsPanel(PlayerData data) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Prestige Points"));
        meta.setLore(List.of(
                Lore.state("wallet"),
                "",
                Lore.line(ChatColor.LIGHT_PURPLE, ChatColor.WHITE + String.valueOf(data.getPrestigePoints())
                        + ChatColor.GRAY + " available"),
                "",
                ChatColor.GRAY + "Earned every time you ascend."));
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------- helpers

    private static ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static void fill(Inventory inv, Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, pane);
        }
    }
}
