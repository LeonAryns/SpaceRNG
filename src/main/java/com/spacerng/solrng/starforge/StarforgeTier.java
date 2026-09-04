package com.spacerng.solrng.starforge;

/** One Starforge tier — its display name, base Luck bonus, and Money price. */
public class StarforgeTier {

    private final String id;
    private final String display;
    private final double luckBonus;
    private final double moneyCost;
    private final int order; // position in the ladder, 0 = Basic

    public StarforgeTier(String id, String display, double luckBonus, double moneyCost, int order) {
        this.id = id;
        this.display = display;
        this.luckBonus = luckBonus;
        this.moneyCost = moneyCost;
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

    public double getMoneyCost() {
        return moneyCost;
    }

    public int getOrder() {
        return order;
    }
}
