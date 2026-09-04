package com.spacerng.solrng.milestone;

import org.bukkit.Material;

import java.util.List;

/**
 * One milestone category — a single ladder of thresholds against one
 * tracked number (prestiges earned, items discovered, crops harvested,
 * hours played).
 *
 * Tiers are just the thresholds in ascending order; a player's progress is
 * "how many of these have I passed". Rewards are per-tier and optional.
 */
public class MilestoneTrack {

    /** One rung of the ladder. */
    public record Tier(int index, long threshold, long tokens, long shards, double money) {
    }

    private final String id;
    private final String display;
    private final Material icon;
    private final String unit;        // "prestiges", "hours", ...
    private final String description;
    private final List<Tier> tiers;

    public MilestoneTrack(String id, String display, Material icon, String unit, String description,
                          List<Tier> tiers) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.unit = unit;
        this.description = description;
        this.tiers = tiers;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public Material getIcon() {
        return icon;
    }

    public String getUnit() {
        return unit;
    }

    public String getDescription() {
        return description;
    }

    public List<Tier> getTiers() {
        return tiers;
    }

    /** The key a claimed tier is stored under, e.g. "farming:4". */
    public String keyFor(Tier tier) {
        return id + ":" + tier.index();
    }

    /** How many tiers this progress value has passed. */
    public int completedCount(long progress) {
        int done = 0;
        for (Tier tier : tiers) {
            if (progress >= tier.threshold()) done++;
        }
        return done;
    }

    /** The next unreached tier, or null once the ladder is finished. */
    public Tier nextTier(long progress) {
        for (Tier tier : tiers) {
            if (progress < tier.threshold()) return tier;
        }
        return null;
    }
}
