package com.spacerng.solrng.perk;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Material;

import java.util.Map;

/**
 * One perk a roll can land on, from perks.types in config: its rarity, the
 * chance a roll lands on it, and what each of its stats gives at level I
 * and at level V. The levels between step evenly, so every level of the
 * same perk is better at everything than the one below it.
 */
public record PerkType(String id, String display, Rarity rarity, Material icon, double chance,
                       Map<PerkStat, double[]> stats) {

    /** The bonus a stat gives at a level: 0.5 is +50%. Zero for a stat this perk doesn't carry. */
    public double valueAt(PerkStat stat, int level) {
        double[] range = stats.get(stat);
        if (range == null) return 0.0;
        int clamped = Math.max(1, Math.min(5, level));
        return range[0] + (range[1] - range[0]) * (clamped - 1) / 4.0;
    }

    /** "typeId:level", how a perk and level is kept in save files. */
    public String key(int level) {
        return id + ":" + level;
    }

    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }
}
