package com.spacerng.solrng.perk;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;

/**
 * The five categories a perk can be. Each has an ordered pool of stats
 * it can grant: the tier's stat-count from config takes the first N off
 * the pool, so a Common Money Perk gives +Money, an Epic Money Perk
 * gives +Money and +Coins, and so on.
 *
 * A Universal perk carries the same stats as everything else, one per
 * category - it is the "small buff to everything" tier the screenshot
 * asks for.
 */
public enum PerkType {

    MONEY("Money Perk", ChatColor.GOLD, Material.GOLD_INGOT,
            List.of(PerkStat.MONEY_PERCENT, PerkStat.COINS_PERCENT,
                    PerkStat.DUPLICATE_PERCENT, PerkStat.CONVERT_PERCENT)),

    LUCK("Luck Perk", ChatColor.GREEN, Material.RABBIT_FOOT,
            List.of(PerkStat.LUCK_PERCENT, PerkStat.SHINY_PERCENT,
                    PerkStat.RARE_BAND_PUSH, PerkStat.MONEY_PERCENT)),

    SPEED("Speed Perk", ChatColor.YELLOW, Material.SUGAR,
            List.of(PerkStat.ROLL_SPEED_FLAT, PerkStat.BONUS_ROLL_PERCENT,
                    PerkStat.INSTANT_ROLL_PERCENT, PerkStat.LUCK_PERCENT)),

    FARM("Farm Perk", ChatColor.DARK_GREEN, Material.WHEAT,
            List.of(PerkStat.COINS_PERCENT, PerkStat.GOLDEN_CROP_PERCENT,
                    PerkStat.CROP_YIELD_PERCENT, PerkStat.ENCHANT_PROC_PERCENT)),

    UNIVERSAL("Universal Perk", ChatColor.LIGHT_PURPLE, Material.NETHER_STAR,
            List.of(PerkStat.LUCK_PERCENT, PerkStat.MONEY_PERCENT,
                    PerkStat.SHINY_PERCENT, PerkStat.BONUS_ROLL_PERCENT));

    private final String label;
    private final ChatColor colour;
    private final Material icon;
    private final List<PerkStat> pool;

    PerkType(String label, ChatColor colour, Material icon, List<PerkStat> pool) {
        this.label = label;
        this.colour = colour;
        this.icon = icon;
        this.pool = pool;
    }

    public String label() { return label; }
    public ChatColor colour() { return colour; }
    public Material icon() { return icon; }
    public List<PerkStat> pool() { return pool; }

    /** The first `count` stats from this type's pool. */
    public List<PerkStat> statsFor(int count) {
        int clamped = Math.max(1, Math.min(count, pool.size()));
        return pool.subList(0, clamped);
    }
}
