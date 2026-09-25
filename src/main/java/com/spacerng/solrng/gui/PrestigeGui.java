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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /prestige, two screens sharing one holder. Rebuilt in V220 to the house
 * style.
 *
 * The main card answers the questions a prestige raises, left to right:
 * can I level, can I prestige, and what do I spend the points on. Under
 * them a row of seven panes fills as the level climbs towards the next
 * prestige, so how far off it is reads before any tooltip is opened.
 *
 *    row 0   rail, the prestige overview in the middle, you on the right
 *    row 2   Level up · Prestige · Upgrades
 *    row 3   the road to the next prestige
 *
 * The upgrades screen keeps the slots Leon set in config, with the back
 * arrow bottom left and the points in the middle of the bottom row. Every
 * upgrade says what it does in one line, then now, next level and max,
 * its level with a bar, and its price.
 */
public class PrestigeGui {

    private static final int MAIN_SIZE = 45;
    private static final int OVERVIEW_SLOT = 4;
    private static final int YOU_SLOT = 8;
    public static final int LEVEL_SLOT = 20;
    public static final int PRESTIGE_SLOT = 22;
    public static final int UPGRADES_SLOT = 24;
    private static final int[] ROAD_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    public static final int BACK_SLOT = 45;
    private static final int POINTS_SLOT = 49;

    public static NamespacedKey upgradeKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_prestige_upgrade");
    }

    // ------------------------------------------------------------- main

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        // Leon picked the neon look for prestige, whatever the other menus wear.
        return Lore.withTheme(Lore.Theme.NEON, () -> buildMain(plugin, player));
    }

    private static Inventory buildMain(SolRNGPlugin plugin, Player player) {
        PrestigeHolder holder = new PrestigeHolder();
        Inventory inv = Bukkit.createInventory(holder, MAIN_SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Prestige");
        holder.setInventory(inv);

        ItemStack rail = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < MAIN_SIZE; i++) inv.setItem(i, i < 9 || i >= 36 ? rail : filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PrestigeManager prestige = plugin.getPrestigeManager();

        inv.setItem(OVERVIEW_SLOT, overview(plugin, data));
        inv.setItem(YOU_SLOT, you(player, data, prestige));
        inv.setItem(LEVEL_SLOT, levelUp(data, prestige));
        inv.setItem(PRESTIGE_SLOT, ascend(plugin, data, prestige));
        inv.setItem(UPGRADES_SLOT, upgradesButton(data, prestige));

        int needed = Math.max(1, prestige.levelsNeededForNextPrestige(data));
        for (int i = 0; i < ROAD_SLOTS.length; i++) {
            inv.setItem(ROAD_SLOTS[i], roadPane(data.getLevel(), needed, i));
        }
        return inv;
    }

    /** The prestige held and what it is worth, in the top middle. */
    private static ItemStack overview(SolRNGPlugin plugin, PlayerData data) {
        double per = plugin.getConfig().getDouble("prestige.luck-multiplier-per-prestige", 0.10);
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Prestige " + data.getPrestige()));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every prestige multiplies all"),
                Lore.line(ChatColor.GRAY, "your Luck, for good."),
                "",
                Lore.stat(ChatColor.GREEN, "Luck multiplier",
                        String.format("%.2fx", 1.0 + data.getPrestige() * per)),
                Lore.stat(ChatColor.AQUA, "Per prestige", String.format("+%.2fx", per))));
        item.setItemMeta(meta);
        return item;
    }

    /** Your own panel, top right: level, rolls and points. */
    private static ItemStack you(Player player, PlayerData data, PrestigeManager prestige) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.setDisplayName(Lore.title(ChatColor.AQUA, player.getName()));
        meta.setLore(List.of(
                Lore.stat(ChatColor.AQUA, "Level", String.valueOf(data.getLevel())),
                Lore.stat(ChatColor.AQUA, "Prestige", String.valueOf(data.getPrestige())),
                Lore.stat(ChatColor.LIGHT_PURPLE, "Prestige Points", String.valueOf(data.getPrestigePoints())),
                "",
                Lore.stat(ChatColor.YELLOW, "Lifetime rolls", String.format("%,d", data.getTotalRolls())),
                Lore.stat(ChatColor.YELLOW, "This prestige", String.format("%,d", data.getRollsThisPrestige()))));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack levelUp(PlayerData data, PrestigeManager prestige) {
        long needed = prestige.rollsNeededForNextLevel(data);
        boolean can = prestige.canLevelUp(data);

        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(can ? ChatColor.GREEN : ChatColor.AQUA, "Level " + (data.getLevel() + 1)));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Rolling is what levels you, and"));
        lore.add(Lore.line(ChatColor.GRAY, "levels are what a prestige asks for."));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Requirements"));
        lore.add(Lore.requirement("Rolls", Lore.shorten(data.getTotalRolls()), Lore.shorten(needed), can));
        lore.add(Lore.bar(needed <= 0 ? 1.0 : (double) data.getTotalRolls() / needed));
        lore.add("");
        if (can) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to level up");
            lore.add(Lore.footnote("Shift click to go as far as your rolls reach."));
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough rolls");
            lore.add(Lore.line(ChatColor.GRAY, Lore.shorten(needed - data.getTotalRolls()) + " more to go."));
        }
        meta.setLore(lore);
        if (can) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack ascend(SolRNGPlugin plugin, PlayerData data, PrestigeManager prestige) {
        int needed = prestige.levelsNeededForNextPrestige(data);
        boolean can = prestige.canPrestige(data);
        int next = data.getPrestige() + 1;
        double per = plugin.getConfig().getDouble("prestige.luck-multiplier-per-prestige", 0.10);
        int points = prestige.getPointsPerPrestige();

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(can ? ChatColor.LIGHT_PURPLE : ChatColor.AQUA, "Prestige " + next));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Start your level over for a"));
        lore.add(Lore.line(ChatColor.GRAY, "boost that never goes away."));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Requirements"));
        lore.add(Lore.requirement("Level", String.valueOf(data.getLevel()), String.valueOf(needed), can));
        lore.add(Lore.bar(needed <= 0 ? 1.0 : (double) data.getLevel() / needed));
        lore.add("");
        lore.add(Lore.section(ChatColor.GREEN, "You gain"));
        lore.add(Lore.upgrade(ChatColor.GREEN, "Luck",
                String.format("%.2fx", 1.0 + data.getPrestige() * per), String.format("%.2fx", 1.0 + next * per)));
        lore.add(Lore.line(ChatColor.GREEN, "+" + points + " Prestige Point" + (points == 1 ? "" : "s")));
        lore.add("");
        lore.add(Lore.section(ChatColor.RED, "You lose"));
        lore.add(Lore.line(ChatColor.RED, "Your level, back to 1"));
        lore.add("");
        if (can) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to prestige");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Reach level " + needed + " first."));
        }
        meta.setLore(lore);
        if (can) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack upgradesButton(PlayerData data, PrestigeManager prestige) {
        int bought = 0;
        int total = 0;
        for (PrestigeUpgrade upgrade : prestige.getUpgrades().values()) {
            bought += Math.min(data.getUpgradeLevel(upgrade.getId()), upgrade.getMaxLevel());
            total += upgrade.getMaxLevel();
        }
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Prestige Upgrades"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Spend Prestige Points on boosts"),
                Lore.line(ChatColor.GRAY, "you pick yourself."),
                "",
                Lore.stat(ChatColor.LIGHT_PURPLE, "Points to spend", String.valueOf(data.getPrestigePoints())),
                Lore.stat(ChatColor.AQUA, "Levels bought", bought + " / " + total),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        if (data.getPrestigePoints() > 0) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One seventh of the road from level 1 to the level the next prestige
     * needs. Lime once reached, grey until then; the name says which level
     * it stands for.
     */
    private static ItemStack roadPane(int level, int needed, int index) {
        int stands = (int) Math.ceil(needed * (index + 1) / (double) ROAD_SLOTS.length);
        boolean reached = level >= stands;
        ItemStack item = new ItemStack(reached ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(reached ? ChatColor.GREEN : ChatColor.DARK_GRAY, "Level " + stands));
        meta.setLore(List.of(
                Lore.stat(reached ? ChatColor.GREEN : ChatColor.RED, "You are at", "level " + level),
                Lore.footnote(index == ROAD_SLOTS.length - 1
                        ? "The last pane is the next prestige."
                        : "The road to your next prestige.")));
        item.setItemMeta(meta);
        return item;
    }

    // --------------------------------------------------------- upgrades

    public static Inventory buildUpgrades(SolRNGPlugin plugin, Player player) {
        return Lore.withTheme(Lore.Theme.NEON, () -> buildUpgradesScreen(plugin, player));
    }

    private static Inventory buildUpgradesScreen(SolRNGPlugin plugin, Player player) {
        PrestigeHolder holder = new PrestigeHolder();
        holder.setUpgradesPage(true);

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Prestige Upgrades");
        holder.setInventory(inv);
        ItemStack rail = pane(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setItem(i, i < 9 || i >= 45 ? rail : filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PrestigeManager prestige = plugin.getPrestigeManager();

        for (PrestigeUpgrade upgrade : prestige.getUpgrades().values()) {
            if (upgrade.getSlot() < 0 || upgrade.getSlot() >= 45) continue;
            inv.setItem(upgrade.getSlot(), buildUpgrade(plugin, data, upgrade));
        }

        inv.setItem(BACK_SLOT, back());
        inv.setItem(POINTS_SLOT, pointsPanel(data));
        return inv;
    }

    private static ItemStack buildUpgrade(SolRNGPlugin plugin, PlayerData data, PrestigeUpgrade upgrade) {
        int level = Math.min(data.getUpgradeLevel(upgrade.getId()), upgrade.getMaxLevel());
        boolean maxed = level >= upgrade.getMaxLevel();
        boolean affordable = data.getPrestigePoints() >= upgrade.getCostPoints();

        Material material = Material.matchMaterial(upgrade.getIcon());
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(maxed ? ChatColor.GREEN : ChatColor.GOLD, upgrade.getDisplay())
                + ChatColor.DARK_GRAY + "  " + level + "/" + upgrade.getMaxLevel());

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, pitch(upgrade)));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, what(upgrade)));
        lore.add(Lore.stat(ChatColor.GREEN, "Now", value(upgrade, level)));
        if (!maxed) {
            lore.add(Lore.stat(ChatColor.YELLOW, "Next level", value(upgrade, level + 1)));
            lore.add(Lore.stat(ChatColor.AQUA, "At max", value(upgrade, upgrade.getMaxLevel())));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Level", level + " / " + upgrade.getMaxLevel()));
        lore.add(Lore.bar((double) level / upgrade.getMaxLevel()));
        lore.add("");
        if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
        } else {
            lore.add(Lore.stat(affordable ? ChatColor.YELLOW : ChatColor.RED, "Price",
                    (affordable ? ChatColor.WHITE : ChatColor.RED) + String.valueOf(upgrade.getCostPoints())
                            + " Prestige Point" + (upgrade.getCostPoints() == 1 ? "" : "s")));
            lore.add("");
            if (affordable) {
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade");
                lore.add(Lore.footnote("Shift click to spend as many as you can."));
            } else {
                lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Prestige Points");
                lore.add(Lore.line(ChatColor.GRAY, "Prestige to earn more."));
            }
        }

        meta.setLore(lore);
        if (maxed) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(upgradeKey(plugin), PersistentDataType.STRING, upgrade.getId());
        item.setItemMeta(meta);
        return item;
    }

    /** One line on what the upgrade is for, in the player's terms. */
    private static String pitch(PrestigeUpgrade upgrade) {
        return switch (upgrade.getEffect()) {
            case LUCK_BONUS -> "Multiplies all your Luck.";
            case TOKEN_BONUS -> "Multiplies the Coins you farm.";
            case MONEY_BONUS -> "Multiplies the Money your rolls pay.";
            case SHARD_BONUS -> "A chance at Gems on every harvest.";
            case NOVA_ODDS -> "Better odds on every Nova Core forge.";
        };
    }

    /** The section header over the numbers. */
    private static String what(PrestigeUpgrade upgrade) {
        return switch (upgrade.getEffect()) {
            case LUCK_BONUS -> "Luck";
            case TOKEN_BONUS -> "Coins";
            case MONEY_BONUS -> "Money";
            case SHARD_BONUS -> "Gem chance";
            case NOVA_ODDS -> "Nova Core odds";
        };
    }

    /** The upgrade's worth at a level: a multiplier for the scaling kinds, a flat add for the chances. */
    private static String value(PrestigeUpgrade upgrade, int level) {
        if (upgrade.isMultiplicative()) return String.format("%.2fx", upgrade.multiplierAt(level));
        double total = upgrade.totalAt(level);
        if ("%".equals(upgrade.getUnit())) return "+" + String.format("%.1f", total * 100.0) + "%";
        if ("x".equals(upgrade.getUnit())) return "+" + String.format("%.2f", total) + "x";
        return "+" + String.format("%.2f", total);
    }

    private static ItemStack back() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Prestige"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Level up and prestige."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to go back"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pointsPanel(PlayerData data) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Prestige Points"));
        meta.setLore(List.of(
                Lore.stat(ChatColor.LIGHT_PURPLE, "To spend", String.valueOf(data.getPrestigePoints())),
                "",
                Lore.footnote("Every prestige pays more.")));
        if (data.getPrestigePoints() > 0) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------- helpers

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
