package com.spacerng.solrng.starforge;

import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RarityStyle;

import java.util.Map;

/** One Starforge tier — its display name, base Luck bonus, and drop cost. */
public class StarforgeTier {

    private final String id;
    private final String display;
    private final double luckBonus;
    // Paid in rolled drops, same as /armor.
    private final Map<Rarity, Long> costs;
    private final int order; // position in the ladder, 0 = Basic
    // The tier's own look — same per-character gradient engine the Epic+
    // item names use. Null falls back to plain white.
    private final RarityStyle style;

    public StarforgeTier(String id, String display, double luckBonus, Map<Rarity, Long> costs, int order,
                         RarityStyle style) {
        this.id = id;
        this.display = display;
        this.luckBonus = luckBonus;
        this.costs = costs;
        this.order = order;
        this.style = style;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public RarityStyle getStyle() {
        return style;
    }

    /** The tier name in its own colors, for item names and menu titles. */
    public String styledDisplay() {
        return style == null ? display : style.apply(display);
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
