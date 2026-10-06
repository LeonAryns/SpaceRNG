package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-crop upgrades, bought in /crops on the crop you are standing in the
 * field growing.
 *
 * V353, Leon's call. The farm tree used to carry a Yield node for every
 * crop, which meant a player could buy Nether Wart Yield from a menu
 * without ever having unlocked Nether Wart, and the nodes took up a third
 * of the tree doing it. These live on the crop instead: the levels are
 * stored per crop id, so pouring Coins into Wheat does nothing for
 * Carrots, and specialising is a real choice again.
 *
 * Everything is read from the `crop-boosts` config section, so adding a
 * fifth stat is a config change. The levels are a flat map on PlayerData
 * keyed "<cropId>:<statId>", the same shape as node levels.
 */
public final class CropBoosts {

    /**
     * One buyable stat. `perLevel` is read as a fraction: 0.08 is +8% a
     * level, and the four below are deliberately different shapes, so the
     * same Coins spent on Growth and on Coin Fortune do different things.
     */
    public record Stat(String id, String display, ChatColor colour, Material icon,
                       String blurb, double perLevel, long baseCost, double growth, int maxLevel) {

        /** What level `level` is worth, as a multiplier on 1.0. */
        public double multiplier(int level) {
            return 1.0 + perLevel * level;
        }

        /** What the NEXT level costs, in Coins. */
        public long costAt(int level) {
            return Math.max(1L, Math.round(baseCost * Math.pow(growth, level)));
        }
    }

    private CropBoosts() {
    }

    /** Every stat, in the order they are drawn. Empty if the section is missing. */
    public static List<Stat> all(SolRNGPlugin plugin) {
        List<Stat> out = new ArrayList<>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("crop-boosts.stats");
        if (root == null) return out;
        int defaultMax = plugin.getConfig().getInt("crop-boosts.max-level", 25);
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "WHEAT"));
            if (icon == null) icon = Material.WHEAT;
            ChatColor colour;
            try {
                colour = ChatColor.valueOf(s.getString("colour", "GREEN"));
            } catch (IllegalArgumentException ex) {
                colour = ChatColor.GREEN;
            }
            out.add(new Stat(id.toUpperCase(java.util.Locale.ROOT),
                    s.getString("display", id), colour, icon,
                    s.getString("blurb", ""),
                    s.getDouble("per-level", 0.05),
                    s.getLong("base-cost", 1000L),
                    s.getDouble("cost-growth", 1.3),
                    s.getInt("max-level", defaultMax)));
        }
        return out;
    }

    public static Stat get(SolRNGPlugin plugin, String statId) {
        if (statId == null) return null;
        for (Stat stat : all(plugin)) {
            if (stat.id().equalsIgnoreCase(statId)) return stat;
        }
        return null;
    }

    /** The storage key. One flat map keeps saving and loading trivial. */
    public static String key(String cropId, String statId) {
        return cropId + ":" + statId.toUpperCase(java.util.Locale.ROOT);
    }

    public static int levelOf(PlayerData data, String cropId, String statId) {
        return data.getCropBoost(key(cropId, statId));
    }

    /**
     * The multiplier one stat is currently worth on one crop. Returns 1.0
     * for a crop that has never been boosted, so every caller can multiply
     * unconditionally.
     */
    public static double multiplierOf(SolRNGPlugin plugin, PlayerData data, String cropId, String statId) {
        if (cropId == null) return 1.0;
        Stat stat = get(plugin, statId);
        if (stat == null) return 1.0;
        return stat.multiplier(levelOf(data, cropId, statId));
    }

    /** The same, for whichever crop the player is growing right now. */
    public static double onSelected(SolRNGPlugin plugin, PlayerData data, String statId) {
        CropType crop = plugin.getFarmPlotManager().cropFor(data);
        return crop == null ? 1.0 : multiplierOf(plugin, data, crop.getId(), statId);
    }

    /**
     * Buys one level. Returns false when the stat is maxed or the Coins
     * are not there, so the caller can say which without asking twice.
     */
    public static boolean buy(SolRNGPlugin plugin, PlayerData data, String cropId, Stat stat) {
        int level = levelOf(data, cropId, stat.id());
        if (level >= stat.maxLevel()) return false;
        long cost = stat.costAt(level);
        if (!data.spendTokens(cost)) return false;
        data.setCropBoost(key(cropId, stat.id()), level + 1);
        return true;
    }
}
