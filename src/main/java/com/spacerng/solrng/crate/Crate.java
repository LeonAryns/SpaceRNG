package com.spacerng.solrng.crate;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One crate type: its name, the key that opens it, and what it can give.
 *
 * Weights are relative, the same way the roll table is, and the chance a
 * player is shown is always computed from them here. A hand-written
 * percentage next to a weight is two numbers that will disagree the first
 * time somebody retunes one of them.
 */
public record Crate(String id, String display, List<String> colors, String keyId,
                    String keySource, List<CrateReward> rewards, double jackpotBelow) {

    public double totalWeight() {
        double total = 0.0;
        for (CrateReward reward : rewards) total += reward.weight();
        return total;
    }

    /** The real chance of one reward, 0 to 1. */
    public double chanceOf(CrateReward reward) {
        double total = totalWeight();
        return total <= 0.0 ? 0.0 : reward.weight() / total;
    }

    /** Rare enough to be announced to the server and celebrated on screen. */
    public boolean isJackpot(CrateReward reward) {
        return chanceOf(reward) < jackpotBelow;
    }

    public CrateReward pick() {
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight();
        double cumulative = 0.0;
        for (CrateReward reward : rewards) {
            cumulative += reward.weight();
            if (roll < cumulative) return reward;
        }
        return rewards.get(rewards.size() - 1);
    }
}
