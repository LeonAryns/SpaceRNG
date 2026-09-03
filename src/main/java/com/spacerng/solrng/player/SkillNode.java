package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;

import java.util.Map;

public class SkillNode {

    public enum Effect {
        LUCK,
        UNLOCK_AUTO_CONVERT,
        AUTO_ROLL,
        ROLL_SPEED,
        BONUS_ROLL_CHANCE
    }

    private final String id;
    private final String display;
    // Paid in rolled drops (Common, Uncommon, ...), not Credits — Credits
    // are reserved for the real-money store, not free skill progression.
    private final Map<Rarity, Long> costs;
    private final String requires; // id of required node, or null
    private final Effect effect;
    private final double value;

    public SkillNode(String id, String display, Map<Rarity, Long> costs, String requires, Effect effect, double value) {
        this.id = id;
        this.display = display;
        this.costs = costs;
        this.requires = (requires == null || requires.isBlank()) ? null : requires;
        this.effect = effect;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public Map<Rarity, Long> getCosts() {
        return costs;
    }

    public String getRequires() {
        return requires;
    }

    public Effect getEffect() {
        return effect;
    }

    public double getValue() {
        return value;
    }
}
