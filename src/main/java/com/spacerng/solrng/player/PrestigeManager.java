package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Levels are earned purely by rolling — reaching level*rolls-per-level
 * total rolls lets you level up. Prestiging resets your level back to 1
 * in exchange for a permanent Luck *multiplier* (unlike every other Luck
 * source, which is additive), and needs progressively more levels each
 * time (first-prestige-levels, then +levels-increment-per-prestige each
 * time after).
 */
public class PrestigeManager {

    private final SolRNGPlugin plugin;
    private int rollsPerLevel;
    private int firstPrestigeLevels;
    private int levelsIncrementPerPrestige;
    private double luckMultiplierPerPrestige;

    public PrestigeManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        rollsPerLevel = config.getInt("prestige.rolls-per-level", 50);
        firstPrestigeLevels = config.getInt("prestige.first-prestige-levels", 10);
        levelsIncrementPerPrestige = config.getInt("prestige.levels-increment-per-prestige", 5);
        luckMultiplierPerPrestige = config.getDouble("prestige.luck-multiplier-per-prestige", 0.10);
    }

    public long rollsNeededForNextLevel(PlayerData data) {
        return (long) data.getLevel() * rollsPerLevel;
    }

    public int levelsNeededForNextPrestige(PlayerData data) {
        return firstPrestigeLevels + data.getPrestige() * levelsIncrementPerPrestige;
    }

    public boolean canLevelUp(PlayerData data) {
        return data.getTotalRolls() >= rollsNeededForNextLevel(data);
    }

    public boolean canPrestige(PlayerData data) {
        return data.getLevel() >= levelsNeededForNextPrestige(data);
    }

    public boolean levelUp(PlayerData data) {
        if (!canLevelUp(data)) return false;
        data.setLevel(data.getLevel() + 1);
        return true;
    }

    public boolean prestige(PlayerData data) {
        if (!canPrestige(data)) return false;
        data.setPrestige(data.getPrestige() + 1);
        data.setLevel(1);
        return true;
    }

    public double effectiveLuck(PlayerData data) {
        return data.getEffectiveLuck(luckMultiplierPerPrestige,
                plugin.getStarforgeManager().luckBonusOf(data));
    }
}
