package com.spacerng.solrng.starforge;

import com.spacerng.solrng.rarity.Rarity;

import java.util.Map;

/** One Starforge tier — its display name, base Luck bonus, and drop cost. */
public class StarforgeTier {

    private final String id;
    private final String display;
    private final double luckBonus;
    // Paid in rolled drops, same as /armor.
    private final Map<Rarity, Long> costs;
    private final int order; // position in the ladder, 0 = Basic

    public StarforgeTier(String id, String display, double luckBonus, Map<Rarity, Long> costs, int order) {
        this.id = id;
        this.display = display;
        this.luckBonus = luckBonus;
        this.costs = costs;
        this.order = order;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public double getLuckBonus() {
        return luckBonus;
    }

    public Map<Rarity, Long> getCosts() {
        return costs;
    }

    public int getOrder() {
        return order;
    }
}
