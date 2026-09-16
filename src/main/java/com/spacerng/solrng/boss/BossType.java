package com.spacerng.solrng.boss;

import com.spacerng.solrng.crate.Crate;
import org.bukkit.Material;

import java.util.List;

/**
 * One kind of boss, straight from config.
 *
 * Health is counted in crops, not Coins: 8000 health is 8000 crops in
 * the window, whatever your hoe pays for them. Everyone fights their own
 * copy, so this number is one player's job and not the server's.
 *
 * The reward table is held as a Crate because it is written in the crate
 * vocabulary and paid out by the crate code. Nothing is ever placed or
 * opened, it is only a loot table with a name and colours.
 */
public record BossType(String id, String display, List<String> colors, Material icon,
                       long health, double weight, int durationMinutes,
                       int rewardRolls, Crate rewards) {

    /** The gradient stops in the shape Lore wants them. */
    public String[] stops() {
        return colors.toArray(new String[0]);
    }

    /** One colour standing in for the gradient, for a bar or a particle. */
    public String accent() {
        return colors.isEmpty() ? "#FFD54F" : colors.get(0);
    }
}
