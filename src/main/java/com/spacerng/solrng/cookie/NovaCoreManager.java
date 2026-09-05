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

    private int maxTier = 20;
    private int firstCheckpoint = 5;
    private double baseChance = 0.75;
    private double decay = 0.85;
    private double luckWeight = 1.0;
    private double minChance = 0.02;
    private double maxChance = 0.95;
    // One multiplier per tier, read straight from config. A formula gave a
    // straight line that was far too steep at the top; a hand-written curve
    // lets the early tiers be a gentle nudge and the last few be a real
    // prize.
    private java.util.List<Double> multipliers = java.util.List.of();
    private long baseCost = 500L;
    private double costGrowth = 1.18;

    public NovaCoreManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        maxTier = config.getInt("novacore.max-tier", 20);
        firstCheckpoint = Math.max(1, config.getInt("novacore.first-checkpoint", 5));
        baseChance = config.getDouble("novacore.base-chance", 0.75);
        decay = config.getDouble("novacore.decay", 0.85);
        luckWeight = config.getDouble("novacore.luck-weight", 1.0);
        minChance = config.getDouble("novacore.min-chance", 0.02);
        maxChance = config.getDouble("novacore.max-chance", 0.95);
        multipliers = config.getDoubleList("novacore.multipliers");
        baseCost = config.getLong("novacore.base-cost-tokens", 500L);
        costGrowth = config.getDouble("novacore.cost-growth", 1.18);
    }

    public int getMaxTier() {
        return maxTier;
    }

    public int getFirstCheckpoint() {
        return firstCheckpoint;
    }

    /**
     * Checkpoints widen as you climb: the first gap is
     * checkpoint-first-gap, and every gap after it is one longer. With the
     * defaults that's 5, 11, 18, 26 — safe ground gets rarer exactly as the
     * odds get worse, so the back half of the ladder is where the risk
     * actually lives.
     */
    private java.util.List<Integer> checkpoints() {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        int at = firstCheckpoint;
        int gap = firstCheckpoint + 1;
        while (at <= maxTier) {
            out.add(at);
            at += gap;
            gap++;
        }
        return out;
    }

    public boolean isCheckpoint(int tier) {
        return tier > 0 && checkpoints().contains(tier);
    }

    /** Where a failed attempt drops you back to. */
    public int checkpointBelow(int tier) {
        int best = 0;
        for (int checkpoint : checkpoints()) {
            if (checkpoint <= tier) best = checkpoint;
            else break;
        }
        return best;
    }

    /** "5, 11, 18" — for the menu's footnote. */
    public String checkpointList() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (int checkpoint : checkpoints()) parts.add(String.valueOf(checkpoint));
        return String.join(", ", parts);
    }

    /**
     * The Nova Core's name in a rainbow gradient, built with the same
     * per-character engine the Epic+ item names use — it's the single
     * flashiest thing in the plugin, so it gets the flashiest treatment.
     */
    public String styledName() {
        return plugin.getRarityManager()
                .buildStyle(java.util.List.of("#FF4E6A", "#FFB03A", "#FFF35C", "#5CFF8F", "#4FC3FF", "#B36BFF"),
                        true, false, false)
                .apply("NOVA CORE");
    }

    /** Inventory titles can't take hex colours, so the menu gets a flat one. */
    public String styledTitle() {
        return ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Nova Core";
    }

    /**
     * The universal multiplier a tier is worth — it scales Luck, the Money
     * a roll pays, and farm Tokens alike. One number that lifts everything
     * is easier to reason about than three separate ladders, and it makes
     * the climb worth doing whatever a player is actually grinding.
     *
     * Tier 0 is always 1.00x.
     */
    public double multiplierAt(int tier) {
        if (tier <= 0 || multipliers.isEmpty()) return 1.0;
        int index = Math.min(tier, multipliers.size()) - 1;
        return multipliers.get(index);
    }

    public long costFor(int tier) {
        return Math.round(baseCost * Math.pow(costGrowth, tier));
    }

    /**
     * The same cost after the Core Efficiency skills. Floored at 10% of
     * list price: a discount chain that could reach zero would turn the
     * ladder into a free reroll button.
     */
    public long costFor(PlayerData data, int tier) {
        double discount = plugin.getSkillTreeManager()
                .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.NOVA_DISCOUNT);
        return Math.max(1L, Math.round(costFor(tier) * Math.max(0.10, 1.0 - discount)));
    }

    /** Core Anchor: the chance a failed climb doesn't drop you at all. */
    public boolean holdsOnFailure(PlayerData data) {
        double chance = plugin.getSkillTreeManager()
                .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.NOVA_SAFETY);
        return chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
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
        return attempt(player, data, true);
    }

    /** {@code charge} is false for free attempts, e.g. the Nova Finder enchant. */
    public boolean attempt(Player player, PlayerData data, boolean charge) {
        int tier = data.getNovaTier();
        if (tier >= maxTier) {
            player.sendMessage(ChatColor.GREEN + "Your Nova Core is already fully forged.");
            return false;
        }

        long cost = costFor(data, tier);
        if (charge && !data.spendTokens(cost)) {
            player.sendMessage(ChatColor.RED + "You need " + String.format("%,d", cost) + " Tokens for that.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }

        double luck = plugin.getPrestigeManager().baseLuck(data);
        // Nova Touch nudges the roll itself rather than the Luck feeding it,
        // so it stays useful at high Luck where the odds already clamp.
        double chance = Math.min(maxChance, chanceAt(tier, luck)
                + plugin.getPrestigeManager().upgradeTotal(data,
                        com.spacerng.solrng.player.PrestigeUpgrade.Effect.NOVA_ODDS));
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
        } else if (holdsOnFailure(data)) {
            // Core Anchor: the attempt is still lost, and so are the Tokens.
            // Only the fall is cancelled — otherwise the skill would remove
            // the risk instead of softening it.
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Anchored! "
                    + ChatColor.RESET + ChatColor.GRAY + "The climb failed but your Core held at tier "
                    + ChatColor.WHITE + tier);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
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
