package com.spacerng.solrng.cookie;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Nova Core ladder (/rngcookie) — a push-your-luck climb.
 *
 * Each attempt either moves you up one tier or drops you back to the last
 * checkpoint, and the odds get worse the higher you are. Your Luck stat
 * feeds directly into the roll, so the ladder is the one place where Luck
 * buys you progression instead of just better drops.
 *
 * Balance: the chance of clearing tier T is
 *
 *     base * decay^T * (1 + luck), clamped to [floor, ceiling]
 *
 * At 100% Luck that puts the first five tiers at ~95/95/95/92/78%, so
 * reaching the first checkpoint is around a 60% run — comfortable, which
 * is what a first checkpoint should be. By tier 20 the same player is at
 * ~6% a step, so the top of the ladder stays a genuine grind no matter
 * how much Luck is stacked.
 *
 * Reaching tier T grants a permanent Luck multiplier, which is why the
 * ladder is worth climbing at all.
 */
public class NovaCoreManager {

    private final SolRNGPlugin plugin;

    private int maxTier = 25;
    private int checkpointEvery = 5;
    private double baseChance = 0.75;
    private double decay = 0.85;
    private double luckWeight = 1.0;
    private double minChance = 0.02;
    private double maxChance = 0.95;
    private double multiplierPerTier = 0.5;
    private long baseCost = 500L;
    private double costGrowth = 1.18;

    public NovaCoreManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        maxTier = config.getInt("novacore.max-tier", 25);
        checkpointEvery = Math.max(1, config.getInt("novacore.checkpoint-every", 5));
        baseChance = config.getDouble("novacore.base-chance", 0.75);
        decay = config.getDouble("novacore.decay", 0.85);
        luckWeight = config.getDouble("novacore.luck-weight", 1.0);
        minChance = config.getDouble("novacore.min-chance", 0.02);
        maxChance = config.getDouble("novacore.max-chance", 0.95);
        multiplierPerTier = config.getDouble("novacore.multiplier-per-tier", 0.5);
        baseCost = config.getLong("novacore.base-cost-tokens", 500L);
        costGrowth = config.getDouble("novacore.cost-growth", 1.18);
    }

    public int getMaxTier() {
        return maxTier;
    }

    public int getCheckpointEvery() {
        return checkpointEvery;
    }

    public boolean isCheckpoint(int tier) {
        return tier > 0 && tier % checkpointEvery == 0;
    }

    /** Where a failed attempt drops you back to. */
    public int checkpointBelow(int tier) {
        return (tier / checkpointEvery) * checkpointEvery;
    }

    /** The Luck multiplier a tier is worth. */
    public double multiplierAt(int tier) {
        return 1.0 + tier * multiplierPerTier;
    }

    public long costFor(int tier) {
        return Math.round(baseCost * Math.pow(costGrowth, tier));
    }

    /** Odds of clearing the step from {@code tier} to {@code tier + 1}. */
    public double chanceAt(int tier, double luck) {
        double raw = baseChance * Math.pow(decay, tier) * (1.0 + luck * luckWeight);
        return Math.max(minChance, Math.min(maxChance, raw));
    }

    /**
     * One attempt. Returns true if the player advanced.
     *
     * The Tokens are taken before the roll, so a failure still costs —
     * that's the whole tension of the ladder.
     */
    public boolean attempt(Player player, PlayerData data) {
        int tier = data.getNovaTier();
        if (tier >= maxTier) {
            player.sendMessage(ChatColor.GREEN + "Your Nova Core is already fully forged.");
            return false;
        }

        long cost = costFor(tier);
        if (!data.spendTokens(cost)) {
            player.sendMessage(ChatColor.RED + "You need " + String.format("%,d", cost) + " Tokens for that.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }

        double luck = plugin.getPrestigeManager().baseLuck(data);
        double chance = chanceAt(tier, luck);
        boolean success = ThreadLocalRandom.current().nextDouble() < chance;

        if (success) {
            int next = tier + 1;
            data.setNovaTier(next);
            if (next > data.getNovaBestTier()) {
                data.setNovaBestTier(next);
            }
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Tier " + next + "! "
                    + ChatColor.RESET + ChatColor.GRAY + "Nova Core Luck is now "
                    + ChatColor.LIGHT_PURPLE + String.format("%.2f", multiplierAt(next)) + "x"
                    + (isCheckpoint(next) ? ChatColor.AQUA + "  (checkpoint secured)" : ""));
            player.playSound(player.getLocation(),
                    isCheckpoint(next) ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                    0.9f, isCheckpoint(next) ? 1.4f : 1.8f);
        } else {
            int fallback = checkpointBelow(tier);
            data.setNovaTier(fallback);
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Shattered! "
                    + ChatColor.RESET + ChatColor.GRAY + "Back to tier " + ChatColor.WHITE + fallback
                    + ChatColor.DARK_GRAY + " (was " + tier + ")");
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        }

        plugin.getScoreboardManager().update(player);
        return success;
    }
}
