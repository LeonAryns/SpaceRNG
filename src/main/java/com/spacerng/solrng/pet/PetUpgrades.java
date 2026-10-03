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

    // The eggs, cheapest first, and where their boost starts.
    private final java.util.List<PetEgg> eggs = new java.util.ArrayList<>();
    private com.spacerng.solrng.rarity.Rarity boostedFrom = com.spacerng.solrng.rarity.Rarity.EPIC;
    // The Prestige a player needs before any pet can be made.
    private int minPrestige = 10;
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
        loadEggs(config);
        rarityBaseCost = Math.max(1L, config.getLong("pets.upgrades.rarity-base-cost", 8L));
        rarityCostGrowth = Math.max(1.0, config.getDouble("pets.upgrades.rarity-cost-growth", 1.35));
        shinyCost = Math.max(1L, config.getLong("pets.upgrades.shiny-cost", 50L));

        tierBaseCost = Math.max(1L, config.getLong("pets.upgrades.tier-base-cost", 25L));
        tierCostGrowth = Math.max(1.0, config.getDouble("pets.upgrades.tier-cost-growth", 1.40));

        tierBaseChance = clamp(config.getDouble("pets.upgrades.tier-base-chance", 0.80));
        tierChanceFalloff = Math.max(0.0, config.getDouble("pets.upgrades.tier-chance-falloff", 0.06));
        tierMinChance = clamp(config.getDouble("pets.upgrades.tier-min-chance", 0.15));
    }

    /**
     * The eggs from pets.eggs.tiers. A config without them gets the three
     * Leon asked for, built off make-cost so a tuned price is still the
     * first egg's price.
     */
    private void loadEggs(org.bukkit.configuration.file.FileConfiguration config) {
        eggs.clear();
        minPrestige = Math.max(0, config.getInt("pets.min-prestige", 10));
        try {
            boostedFrom = com.spacerng.solrng.rarity.Rarity.valueOf(
                    config.getString("pets.eggs.boosted-from", "EPIC").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            boostedFrom = com.spacerng.solrng.rarity.Rarity.EPIC;
        }
        var tiers = config.getConfigurationSection("pets.eggs.tiers");
        if (tiers != null) {
            for (String id : tiers.getKeys(false)) {
                var egg = tiers.getConfigurationSection(id);
                if (egg == null) continue;
                org.bukkit.Material icon = org.bukkit.Material.matchMaterial(egg.getString("icon", "TURTLE_EGG"));
                java.util.List<String> colors = egg.getStringList("colors");
                var band = PetEgg.defaultBand(id);
                eggs.add(new PetEgg(id, egg.getString("display", id),
                        colors.isEmpty() ? java.util.List.of("#C77DFF", "#7FDBFF") : colors,
                        icon == null ? org.bukkit.Material.TURTLE_EGG : icon,
                        Math.max(1L, egg.getLong("cost", makeCost)),
                        Math.max(1.0, egg.getDouble("boost", 1.0)),
                        Math.max(0, egg.getInt("min-prestige", minPrestige)),
                        rarityOr(egg.getString("min-rarity"), band[0]),
                        rarityOr(egg.getString("max-rarity"), band[1]),
                        Math.max(0.0, egg.getDouble("divine-chance", 0.0))));
            }
        }
        if (eggs.isEmpty()) {
            // V313: the ladder Leon asked for. Egg one from the start and
            // never a Divine, egg two at Prestige 10 with a small Divine
            // chance, egg three at Prestige 25 with exactly ten times it.
            eggs.add(new PetEgg("stardust", "Stardust Egg", java.util.List.of("#C9D6FF", "#7FDBFF"),
                    org.bukkit.Material.TURTLE_EGG, makeCost, 1.0, 0,
                    PetEgg.defaultBand("stardust")[0], PetEgg.defaultBand("stardust")[1], 0.0));
            eggs.add(new PetEgg("nebula", "Nebula Egg", java.util.List.of("#C77DFF", "#FF7AD9"),
                    org.bukkit.Material.SNIFFER_EGG, makeCost * 15L, 25.0, 10,
                    PetEgg.defaultBand("nebula")[0], PetEgg.defaultBand("nebula")[1], 0.001));
            eggs.add(new PetEgg("supernova", "Supernova Egg", java.util.List.of("#FFD54F", "#FF6F3C"),
                    org.bukkit.Material.DRAGON_EGG, makeCost * 120L, 400.0, 25,
                    PetEgg.defaultBand("supernova")[0], PetEgg.defaultBand("supernova")[1], 0.01));
        }
        eggs.sort(java.util.Comparator.comparingLong(PetEgg::cost));
    }

    private static com.spacerng.solrng.rarity.Rarity rarityOr(String raw, com.spacerng.solrng.rarity.Rarity fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return com.spacerng.solrng.rarity.Rarity.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public java.util.List<PetEgg> eggs() {
        return eggs;
    }

    public PetEgg egg(String id) {
        for (PetEgg egg : eggs) if (egg.id().equals(id)) return egg;
        return null;
    }

    public com.spacerng.solrng.rarity.Rarity boostedFrom() {
        return boostedFrom;
    }

    public int minPrestige() {
        return minPrestige;
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
