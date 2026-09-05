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

    /**
     * Luck WITHOUT the Nova Core's own multiplier. The /rngcookie ladder
     * rolls against this: feeding a tier's multiplier back into the odds
     * of climbing to the next tier makes the ladder easier the further up
     * you get, which is backwards.
     */
    public double baseLuck(PlayerData data) {
        double luck = data.getEffectiveLuck(luckMultiplierPerPrestige,
                plugin.getRarityManager().tagMultiplierFor(data));
        return luck * plugin.getBoostManager().multiplier();
    }

    /** Everything: base Luck, the global boost, and the Nova Core tier. */
    public double effectiveLuck(PlayerData data) {
        return baseLuck(data) * plugin.getNovaCoreManager().multiplierAt(data.getNovaTier());
    }
}
