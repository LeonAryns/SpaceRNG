package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.farming.FarmingManager;
import com.spacerng.solrng.farming.HoeEnchantManager;
import com.spacerng.solrng.player.PlayerData;
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
 * The hoe's own upgrade board, opened by right-clicking it.
 *
 * The split between this and /farmtree is deliberate: the tree decides
 * WHICH enchants you have access to and how high they can go, and this
 * decides how far up you've actually taken them. One is a progression
 * choice you make a handful of times; the other is a Token sink you come
 * back to constantly, and they'd fight each other in one menu.
 */
public class HoeGui {

    /**
     * Every interior slot of the top three rows, left to right. Enchants
     * fill them in order and anything left over is drawn as an empty
     * socket rather than as nothing - the rack should look like it has
     * room, not like it stopped early.
     */
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int HOE_SLOT = 4;
    private static final int COINS_SLOT = 49;
    private static final int FARM_SOUND_SLOT = 47;
    private static final int ENCHANT_SOUND_SLOT = 51;
    private static final int HIDE_PLAYERS_SLOT = 53;

    public static int hidePlayersSlot() {
        return HIDE_PLAYERS_SLOT;
    }

    public static NamespacedKey enchantKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key( "solrng_hoe_enchant");
    }

    public static int farmSoundSlot() {
        return FARM_SOUND_SLOT;
    }

    public static int enchantSoundSlot() {
        return ENCHANT_SOUND_SLOT;
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        HoeHolder holder = new HoeHolder();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        FarmingManager.HoeTier tier = plugin.getFarmingManager().tierOf(data);

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Farmer's Hoe"
                        + ChatColor.GRAY + " \u2014 " + tier.display());
        holder.setInventory(inv);

        // Green rim, one row of glass above the controls, everything else
        // is rack. Nothing is spaced out for the sake of it.
        ItemStack frame = pane(Material.GREEN_STAINED_GLASS_PANE, " ");
        ItemStack divider = pane(Material.LIME_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 54; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            if (row == 4) {
                inv.setItem(slot, divider);
            } else if (row == 0 || row == 5 || column == 0 || column == 8) {
                inv.setItem(slot, frame);
            } else {
                inv.setItem(slot, pane(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }

        HoeEnchantManager hoe = plugin.getHoeEnchantManager();
        int i = 0;
        for (HoeEnchantManager.Enchant enchant : hoe.getEnchants().values()) {
            if (i >= SLOTS.length) break;
            inv.setItem(SLOTS[i], buildEnchant(plugin, data, hoe, enchant));
            i++;
        }
        for (; i < SLOTS.length; i++) {
            inv.setItem(SLOTS[i], emptySocket());
        }

        inv.setItem(HOE_SLOT, buildHoeCard(plugin, player, data, tier));
        inv.setItem(COINS_SLOT, buildCoins(data));
        inv.setItem(FARM_SOUND_SLOT, buildToggle(Material.NOTE_BLOCK, "Farming Sounds",
                data.isFarmSoundEnabled(), "The click of a crop coming up."));
        inv.setItem(ENCHANT_SOUND_SLOT, buildToggle(Material.BELL, "Enchant Sounds",
                data.isEnchantSoundEnabled(), "The chime when an enchant fires."));
        inv.setItem(HIDE_PLAYERS_SLOT, buildToggle(Material.ENDER_EYE, "Hide Other Farmers",
                data.isFarmHidePlayers(), "Other players vanish while you're on the farm."));
        return inv;
    }

    /** A slot with no enchant in it yet. Room, not a gap. */
    private static ItemStack emptySocket() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "Empty Socket"));
        meta.setLore(java.util.List.of(
                ChatColor.DARK_GRAY + Lore.BULLET + " An enchant will live here.",
                "",
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Coming soon"));
        item.setItemMeta(meta);
        return item;
    }

    /** The tool itself: what it is now, and what the next rung would make it. */
    private static ItemStack buildHoeCard(SolRNGPlugin plugin, Player player, PlayerData data,
                                          FarmingManager.HoeTier tier) {
        FarmingManager farming = plugin.getFarmingManager();
        int index = farming.tierIndexOf(data);
        List<FarmingManager.HoeTier> tiers = farming.getHoeTiers();

        ItemStack item = new ItemStack(farming.hoeMaterial(index));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, farming.getHoeName() + " " + tier.display()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.GOLD, "The tool"));
        lore.add(Lore.stat(ChatColor.YELLOW, "Tier", (index + 1) + " / " + tiers.size()));
        lore.add(Lore.stat(Currency.COINS.colour(), "Coins",
                String.format("%.2f", tier.coinMultiplier()) + "x"));
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Enchant proc",
                String.format("%.2f", tier.procMultiplier()) + "x"));
        lore.add(Lore.bar(tiers.size() <= 1 ? 1.0 : index / (double) (tiers.size() - 1)));
        lore.add("");
        if (index + 1 < tiers.size()) {
            FarmingManager.HoeTier next = tiers.get(index + 1);
            lore.add(Lore.section(ChatColor.AQUA, "Next tier"));
            lore.add(Lore.upgrade(Currency.COINS.colour(), "Coins",
                    String.format("%.2f", tier.coinMultiplier()) + "x",
                    String.format("%.2f", next.coinMultiplier()) + "x"));
            lore.add(Lore.upgrade(ChatColor.LIGHT_PURPLE, "Enchant proc",
                    String.format("%.2f", tier.procMultiplier()) + "x",
                    String.format("%.2f", next.procMultiplier()) + "x"));

            lore.add("");
            lore.add(Lore.section(ChatColor.YELLOW, "Price"));
            boolean afford = true;
            for (var cost : next.costs().entrySet()) {
                long held = com.spacerng.solrng.player.DropWallet
                        .total(plugin, player, data, cost.getKey());
                boolean met = held >= cost.getValue();
                afford &= met;
                lore.add((met ? ChatColor.GREEN : ChatColor.RED) + Lore.BULLET + " "
                        + plugin.getRarityManager().style(cost.getKey(), cost.getKey().displayName())
                        + ChatColor.GRAY + ": " + (met ? ChatColor.GREEN : ChatColor.RED) + held
                        + ChatColor.DARK_GRAY + " / " + ChatColor.WHITE + cost.getValue());
            }
            lore.add("");
            lore.add(afford
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough drops");
        } else {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Fully upgraded");
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildEnchant(SolRNGPlugin plugin, PlayerData data, HoeEnchantManager hoe,
                                          HoeEnchantManager.Enchant enchant) {
        boolean unlocked = hoe.isUnlocked(data, enchant.id());
        int level = hoe.levelOf(data, enchant.id());

        Material material = Material.matchMaterial(enchant.icon());
        if (material == null) material = Material.ENCHANTED_BOOK;

        // The real icon even while locked: a wall of grey dye tells you
        // nothing about what you're working toward.
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(unlocked ? ChatColor.YELLOW : ChatColor.DARK_GRAY, enchant.display()));

        meta.setLore(enchantLore(plugin, data, hoe, enchant, plugin.getConfig().getString("enchant-style", "classic")));
        meta.setEnchantmentGlintOverride(unlocked && level > 0 ? Boolean.TRUE : null);
        meta.getPersistentDataContainer().set(enchantKey(plugin), PersistentDataType.STRING, enchant.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The looks an enchant card can take, picked with enchant-style. */
    public static final List<String> ENCHANT_STYLES = List.of("classic", "card", "compact", "stats");

    /**
     * An enchant card's tooltip in one style. Every style carries the same
     * facts: what it does, its power now, level and cap, the price, and the
     * one action footer that says what a click does or why it can't.
     */
    public static List<String> enchantLore(SolRNGPlugin plugin, PlayerData data, HoeEnchantManager hoe,
                                           HoeEnchantManager.Enchant enchant, String style) {
        boolean unlocked = hoe.isUnlocked(data, enchant.id());
        int level = hoe.levelOf(data, enchant.id());
        int cap = hoe.maxLevelFor(data, enchant);
        boolean maxed = level >= cap;
        long cost = hoe.costFor(enchant, level);
        boolean affordable = data.getTokens() >= cost;
        String colour = String.valueOf(enchant.colour());
        String power = hoe.describePower(data, enchant.id());
        String levelText = String.format("%,d", level) + " / " + String.format("%,d", cap);
        double filled = cap <= 0 ? 0.0 : level / (double) cap;
        String price = unlocked && !maxed ? Currency.COINS.price(cost, affordable) : null;

        List<String> actions = new ArrayList<>();
        if (!unlocked) {
            actions.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            actions.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Unlock it in "
                    + ChatColor.YELLOW + "/farmtree");
        } else if (maxed) {
            actions.add(ChatColor.GREEN + "" + ChatColor.BOLD + (cap >= enchant.maxLevel() ? "Maxed" : "At the cap"));
            if (cap < enchant.maxLevel()) {
                actions.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + "Buy Enchant Mastery in "
                        + ChatColor.YELLOW + "/farmtree");
            }
        } else if (affordable) {
            actions.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade");
            actions.add(Lore.footnote("Shift for 100, right-click for all you can afford"));
        } else {
            actions.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Coins");
        }

        List<String> lore = new ArrayList<>();
        switch (style == null ? "" : style.toLowerCase(java.util.Locale.ROOT)) {
            case "card" -> {
                List<String> top = new ArrayList<>();
                for (String line : wrap(enchant.description())) top.add("  " + ChatColor.GRAY + line);
                List<String> rows = new ArrayList<>();
                rows.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + "Now  " + ChatColor.WHITE + power);
                rows.add(ChatColor.AQUA + Lore.BULLET + " " + ChatColor.GRAY + "Level  " + ChatColor.WHITE + levelText);
                if (price != null) {
                    rows.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                            + ChatColor.GRAY + "Cost  " + price);
                }
                int widest = 0;
                for (String line : top) widest = Math.max(widest, com.spacerng.solrng.rarity.LoreStyle.pixelWidth(line));
                for (String row : rows) widest = Math.max(widest, com.spacerng.solrng.rarity.LoreStyle.pixelWidth(row));
                String rule = colour + ChatColor.STRIKETHROUGH + " ".repeat(widest / 4 + 2);
                lore.add(rule);
                lore.addAll(top);
                lore.add(rule);
                lore.addAll(rows);
                lore.add(rule);
                lore.addAll(actions);
            }
            case "compact" -> {
                for (String line : wrap(enchant.description())) lore.add(ChatColor.GRAY + line);
                lore.add("");
                lore.add(ChatColor.GREEN + power);
                int bars = (int) Math.round(filled * 10);
                lore.add(ChatColor.WHITE + levelText + "  " + colour + "▬".repeat(bars)
                        + ChatColor.DARK_GRAY + "▬".repeat(10 - bars));
                if (price != null) lore.add(ChatColor.GRAY + "Cost " + price);
                lore.add("");
                lore.addAll(actions);
            }
            case "stats" -> {
                for (String line : wrap(enchant.description())) lore.add(Lore.line(ChatColor.AQUA, line));
                lore.add(Lore.stat(ChatColor.GREEN, "Right now", power));
                lore.add(Lore.stat(ChatColor.AQUA, "Level", levelText));
                lore.add(Lore.bar(filled));
                if (price != null) lore.add(Lore.stat(affordable ? ChatColor.YELLOW : ChatColor.RED, "Cost", price));
                lore.add("");
                lore.addAll(actions);
            }
            default -> {
                lore.add(Lore.section(ChatColor.AQUA, "What it does"));
                for (String line : wrap(enchant.description())) lore.add(Lore.line(ChatColor.AQUA, line));
                lore.add("");
                lore.add(Lore.section(ChatColor.AQUA, "Information"));
                lore.add(Lore.stat(ChatColor.GREEN, "Right now", power));
                lore.add(Lore.stat(ChatColor.AQUA, "Level", levelText));
                lore.add(Lore.bar(filled));
                if (cap < enchant.maxLevel()) {
                    lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Enchant Mastery raises the cap toward "
                            + String.format("%,d", enchant.maxLevel()) + ".");
                }
                if (price != null) {
                    lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                            + ChatColor.GRAY + "Cost: " + price);
                }
                lore.add("");
                lore.addAll(actions);
            }
        }
        return lore;
    }

    private static ItemStack buildCoins(PlayerData data) {
        ItemStack item = new ItemStack(Material.HAY_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(Currency.COINS.colour(), "Your Coins"));
        meta.setLore(List.of(
                Currency.COINS.colour() + Lore.BULLET + " " + Currency.COINS.exact(data.getTokens()),
                "",
                ChatColor.DARK_GRAY + Lore.BULLET + " Enchants are unlocked in /farmtree",
                ChatColor.DARK_GRAY + Lore.BULLET + " and levelled here with Coins."));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildToggle(Material material, String label, boolean on, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(on ? ChatColor.GREEN : ChatColor.RED, label)
                + ChatColor.DARK_GRAY + " - "
                + (on ? ChatColor.GREEN.toString() + ChatColor.BOLD + "On"
                      : ChatColor.RED.toString() + ChatColor.BOLD + "Off"));
        meta.setLore(List.of(
                Lore.section(ChatColor.AQUA, "What it controls"),
                Lore.line(ChatColor.AQUA, description),
                "",
                ChatColor.DARK_GRAY + Lore.BULLET + " Only affects what you hear.",
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to toggle"));
        meta.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    /** Splits a description into ~32-char lines so lore never runs off screen. */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() + word.length() + 1 > 32) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(name);
        pane.setItemMeta(meta);
        return pane;
    }
}
