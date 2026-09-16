package com.spacerng.solrng.boss;

/**
 * What one player is handed when a boss falls. The top three get their
 * own entry instead of this one, never on top of it.
 */
public record BossReward(long credits, long perkTickets, long coins, String crate, int keys) {

    public boolean isEmpty() {
        return credits <= 0 && perkTickets <= 0 && coins <= 0 && keys <= 0;
    }
}
