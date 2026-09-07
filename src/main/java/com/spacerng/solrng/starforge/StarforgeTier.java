package com.spacerng.solrng.starforge;

import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RarityStyle;

import java.util.Map;

/** One Starforge tier - its display name, base Luck bonus, and drop cost. */
public class StarforgeTier {

    private final String id;
    private final String display;
    private final double luckBonus;
    private final double speedBonus;
    private final Ability ability;

    /**
     * What a Starforge can do on demand.
     *
     * A tier is defined by its trade: the Luck-heavy ones roll slower and
     * the fast ones roll shallower, so "better" stops being a single line
     * and becomes a choice. An ability is what the late tiers get instead
     * of simply more of the same number.
     */
    public record Ability(String id, String display, long durationSeconds, long cooldownSeconds,
                          double speedMultiplier, double luckMultiplier, String description) {
    }
    // Paid in rolled drops, same as /armor.
    private final Map<Rarity, Long> costs;
    private final int order; // position in the ladder, 0 = Basic
    // The tier's own look - same per-character gradient engine the Epic+
    // item names use. Null falls back to plain white.
    private final RarityStyle style;

    public StarforgeTier(String id, String display, double luckBonus, double speedBonus,
                         Ability ability, Map<Rarity, Long> costs, int order,
                         RarityStyle style) {
        this.id = id;
        this.display = display;
        this.luckBonus = luckBonus;
        this.speedBonus = speedBonus;
        this.ability = ability;
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

    public double getSpeedBonus() {
        return speedBonus;
    }

    /** Null when this tier has no on-demand ability. */
    public Ability getAbility() {
        return ability;
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
