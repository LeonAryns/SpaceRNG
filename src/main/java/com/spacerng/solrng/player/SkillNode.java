package com.spacerng.solrng.player;

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
    private final long cost;
    private final String requires; // id of required node, or null
    private final Effect effect;
    private final double value;

    public SkillNode(String id, String display, long cost, String requires, Effect effect, double value) {
        this.id = id;
        this.display = display;
        this.cost = cost;
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

    public long getCost() {
        return cost;
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
