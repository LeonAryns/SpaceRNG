package com.spacerng.solrng.pet;

/**
 * The maths behind a pet: what rarity and tier are worth, what they cost,
 * and how likely a tier upgrade is to take.
 *
 * This exists for the same reason {@code StatSources} does. The moment
 * the curve is half in the manager and half in the menu, nobody can say
 * what a Tier 7 pet is actually worth without reading two files, and it
 * stops being tunable. Every number a pet has comes from here.
 *
 * The cap is the important part. A pet's base percentage is multiplied by
 * rarity, by tier and again by shiny, and three multipliers stacked on a
 * player who has been grinding for a month runs away fast. maxMultiplier
 * is the ceiling on all of it together.
 */
public class PetUpgrades {

    private int maxRarity = 10;
    private int maxTier = 10;

    // What one step is worth, as a fraction of the base percentage.
    private double rarityStep = 0.25;
    private double tierStep = 0.15;
    private double shinyBonus = 0.10;

    // The ceiling on rarity x tier x shiny together.
    private double maxMultiplier = 6.0;

    // Cosmic Dust, for making a pet and for rarity.
    private long makeCost = 100L;
    private long rarityBaseCost = 8L;
    private double rarityCostGrowth = 1.35;
    private long shinyCost = 50L;

    // Farm Dust, for tier.
    private long tierBaseCost = 25L;
    private double tierCostGrowth = 1.40;

    // The chance a tier upgrade takes. Falls off as the tier climbs, and
    // the skill tree adds back onto it.
    private double tierBaseChance = 0.80;
    private double tierChanceFalloff = 0.06;
    private double tierMinChance = 0.15;

    public void load(org.bukkit.configuration.file.FileConfiguration config) {
        maxRarity = Math.max(1, config.getInt("pets.upgrades.max-rarity", 10));
        maxTier = Math.max(1, config.getInt("pets.upgrades.max-tier", 10));

        rarityStep = Math.max(0.0, config.getDouble("pets.upgrades.rarity-step", 0.25));
        tierStep = Math.max(0.0, config.getDouble("pets.upgrades.tier-step", 0.15));
        shinyBonus = Math.max(0.0, config.getDouble("pets.upgrades.shiny-bonus", 0.10));
        maxMultiplier = Math.max(1.0, config.getDouble("pets.upgrades.max-multiplier", 6.0));

        makeCost = Math.max(1L, config.getLong("pets.upgrades.make-cost", 100L));
        rarityBaseCost = Math.max(1L, config.getLong("pets.upgrades.rarity-base-cost", 8L));
        rarityCostGrowth = Math.max(1.0, config.getDouble("pets.upgrades.rarity-cost-growth", 1.35));
        shinyCost = Math.max(1L, config.getLong("pets.upgrades.shiny-cost", 50L));

        tierBaseCost = Math.max(1L, config.getLong("pets.upgrades.tier-base-cost", 25L));
        tierCostGrowth = Math.max(1.0, config.getDouble("pets.upgrades.tier-cost-growth", 1.40));

        tierBaseChance = clamp(config.getDouble("pets.upgrades.tier-base-chance", 0.80));
        tierChanceFalloff = Math.max(0.0, config.getDouble("pets.upgrades.tier-chance-falloff", 0.06));
        tierMinChance = clamp(config.getDouble("pets.upgrades.tier-min-chance", 0.15));
    }

    // ---------------------------------------------------------------
    // What a pet is worth
    // ---------------------------------------------------------------

    /**
     * The multiplier on a pet's base percentage. A fresh Rarity 1 Tier 1
     * pet returns exactly 1.0, so the numbers Leon already tuned in
     * pets.types are still what a new pet gives.
     */
    public double multiplier(PetInstance pet) {
        if (pet == null) return 1.0;
        double value = (1.0 + rarityStep * (pet.rarity() - 1))
                * (1.0 + tierStep * (pet.tier() - 1));
        if (pet.shiny()) value *= 1.0 + shinyBonus;
        return Math.min(maxMultiplier, value);
    }

    /** The most any pet can be worth: top rarity, top tier and shiny, under the ceiling. */
    public double topMultiplier() {
        return Math.min(maxMultiplier, (1.0 + rarityStep * (maxRarity - 1))
                * (1.0 + tierStep * (maxTier - 1)) * (1.0 + shinyBonus));
    }

    /** True once a pet is pinned against the ceiling, so the menu can say so. */
    public boolean capped(PetInstance pet) {
        if (pet == null) return false;
        double raw = (1.0 + rarityStep * (pet.rarity() - 1))
                * (1.0 + tierStep * (pet.tier() - 1))
                * (pet.shiny() ? 1.0 + shinyBonus : 1.0);
        return raw > maxMultiplier;
    }

    // ---------------------------------------------------------------
    // What it costs
    // ---------------------------------------------------------------

    /** Cosmic Dust for one more pet. */
    public long makeCost() {
        return makeCost;
    }

    /** Cosmic Dust to go from this rarity to the next. */
    public long rarityCost(int fromRarity) {
        return (long) Math.ceil(rarityBaseCost * Math.pow(rarityCostGrowth, Math.max(0, fromRarity - 1)));
    }

    /** Farm Dust to attempt the next tier. Paid whether or not it takes. */
    public long tierCost(int fromTier) {
        return (long) Math.ceil(tierBaseCost * Math.pow(tierCostGrowth, Math.max(0, fromTier - 1)));
    }

    /** Cosmic Dust to make a pet shiny. */
    public long shinyCost() {
        return shinyCost;
    }

    // ---------------------------------------------------------------
    // Whether it takes
    // ---------------------------------------------------------------

    /**
     * The chance a tier attempt succeeds, before the skill tree bonus.
     * Climbing gets harder, which is what makes a high tier worth
     * anything, but it never drops below tierMinChance or the last few
     * tiers become a wall nobody bothers with.
     */
    public double tierChance(int fromTier, double skillBonus) {
        double raw = tierBaseChance - tierChanceFalloff * Math.max(0, fromTier - 1);
        return clamp(Math.max(tierMinChance, raw) + Math.max(0.0, skillBonus));
    }

    public int maxRarity() {
        return maxRarity;
    }

    public int maxTier() {
        return maxTier;
    }

    public double shinyBonus() {
        return shinyBonus;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
