package com.spacerng.solrng.boss;

import org.bukkit.Material;

import java.util.List;

/**
 * One kind of boss, straight from config.
 *
 * Health is the whole fight. A harvested crop pays its Coins as damage
 * and a landed roll pays its rarity's damage, so the only thing that
 * decides how long a boss stands is this number against how many people
 * are playing at the time.
 */
public record BossType(String id, String display, List<String> colors, Material icon,
                       long health, double weight, int durationMinutes,
                       BossReward base, List<BossReward> top) {

    /** The gradient stops in the shape Lore wants them. */
    public String[] stops() {
        return colors.toArray(new String[0]);
    }

    /** One colour standing in for the gradient, for a bar or a particle. */
    public String accent() {
        return colors.isEmpty() ? "#FFD54F" : colors.get(0);
    }
}
