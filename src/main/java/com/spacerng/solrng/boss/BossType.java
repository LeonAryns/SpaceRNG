package com.spacerng.solrng.boss;

import org.bukkit.Material;

import java.util.List;

/**
 * One kind of boss, straight from config.
 *
 * Health is counted in crops, not Coins: 8000 health is 8000 crops in
 * the window, whatever your hoe pays for them. Everyone fights their own
 * copy, so this number is one player's job and not the server's.
 *
 * Beating one pays Boss Boxes rather than loot straight into the
 * inventory. The box is a crate like any other, so what is inside it is
 * tuned in one place for every boss and opening it is the same show as
 * every other crate.
 */
public record BossType(String id, String display, List<String> colors, Material icon,
                       long health, double weight, int durationMinutes,
                       String boxCrate, int boxes) {

    /** The gradient stops in the shape Lore wants them. */
    public String[] stops() {
        return colors.toArray(new String[0]);
    }

    /** One colour standing in for the gradient, for a bar or a particle. */
    public String accent() {
        return colors.isEmpty() ? "#FFD54F" : colors.get(0);
    }
}
