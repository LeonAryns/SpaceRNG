package com.spacerng.solrng.pet;

/**
 * The maths behind a pet: what a level is worth, what it costs, and the
 * ceiling on all of it.
 *
 * This exists for the same reason {@code StatSources} does. The moment
 * the curve is half in the manager and half in the menu, nobody can say
 * what a level 7 pet is actually worth without reading two files, and it
 * stops being tunable. Every number a pet has comes from here.
 *
 * V359, Leon's rework. There used to be two ladders, a rarity bought
 * with Gems and a tier bought with Gems that could fail, plus a cap to
 * stop the two of them multiplying away from each other. There is one
 * ladder now: level 1 to 10, every level worth 10% more multiplier, paid
 * in Cosmic Dust, which is what ties growing a pet to the Cosmic Dust
 * skills. It always takes. Shiny is still bought on top, and the cap
 * still exists because shiny multiplies the level.
 */
public class PetUpgrades {

    private int maxLevel = 10;

    // What one level is worth, as a fraction of the pet's base bonus.
    private double levelStep = 0.10;
    private double shinyBonus = 0.10;

    // The ceiling on level x shiny together.
    private double maxMultiplier = 6.0;

    // How many pets a player may hold. Full stops the opening, Leon's
    // call, so nothing is ever thrown away to make room.
    private int storage = 100;

    // Cosmic Dust, for making a pet.
    private long makeCost = 100L;

    // The eggs, cheapest first.
    private final java.util.List<PetEgg> eggs = new java.util.ArrayList<>();
    // V324: what a fresh pet of each rarity multiplies its stat by, held
    // as the bonus above 1 (0.64 is 1.64x). Exponential by Leon's ask:
    // the bonus doubles every rarity.
    private final java.util.EnumMap<com.spacerng.solrng.rarity.Rarity, Double> bonuses =
            new java.util.EnumMap<>(com.spacerng.solrng.rarity.Rarity.class);
    // The Prestige a player needs before any pet can be made.
    private int minPrestige = 10;

    // Cosmic Dust, for levels.
    private long levelBaseCost = 250L;
    private double levelCostGrowth = 1.8;

    // Gems, for shiny.
    private long shinyCost = 100000L;

    // How much more a pet out of each next egg is worth.
    private double eggBonusGrowth = 1.15;

    public void load(org.bukkit.configuration.file.FileConfiguration config) {
        maxLevel = Math.max(1, config.getInt("pets.upgrades.max-level", 10));
        levelStep = Math.max(0.0, config.getDouble("pets.upgrades.level-step", 0.10));
        shinyBonus = Math.max(0.0, config.getDouble("pets.upgrades.shiny-bonus", 0.10));
        maxMultiplier = Math.max(1.0, config.getDouble("pets.upgrades.max-multiplier", 6.0));
        storage = Math.max(1, config.getInt("pets.storage", 100));
        eggBonusGrowth = Math.max(1.0, config.getDouble("pets.egg-bonus-growth", 1.15));

        makeCost = Math.max(1L, config.getLong("pets.upgrades.make-cost", 100L));
        loadBonuses(config);
        loadEggs(config);
        levelBaseCost = Math.max(1L, config.getLong("pets.upgrades.level-base-cost", 250L));
        levelCostGrowth = Math.max(1.0, config.getDouble("pets.upgrades.level-cost-growth", 1.8));
        shinyCost = Math.max(1L, config.getLong("pets.upgrades.shiny-cost", 100000L));
    }

    /**
     * What a fresh pet of each rarity is worth, from pets.multipliers.
     *
     * The config holds the multiplier a player reads on the tooltip
     * (1.64) and this keeps the bonus behind it (0.64), because every
     * other number in this class multiplies the bonus and nothing
     * multiplies a multiplier cleanly. The defaults are the doubling
     * ladder, so a config without the section still has the shape Leon
     * asked for rather than a flat nothing.
     */
    private void loadBonuses(org.bukkit.configuration.file.FileConfiguration config) {
        bonuses.clear();
        double[] fallback = {1.01, 1.02, 1.04, 1.08, 1.16, 1.32, 1.64, 1.64};
        for (com.spacerng.solrng.rarity.Rarity rarity : com.spacerng.solrng.rarity.Rarity.values()) {
            double value = config.getDouble("pets.multipliers." + rarity.name(),
                    fallback[Math.min(rarity.ordinal(), fallback.length - 1)]);
            bonuses.put(rarity, Math.max(0.0, value - 1.0));
        }
    }

    /** The bonus above 1 a fresh pet of this rarity carries. */
    public double bonusFor(com.spacerng.solrng.rarity.Rarity rarity) {
        Double value = bonuses.get(rarity);
        return value == null ? 0.0 : value;
    }

    /**
     * The eggs from pets.eggs.tiers. A config without them gets the three
     * Leon asked for, built off make-cost so a tuned price is still the
     * first egg's price.
     *
     * V324: an egg carries the chance of each rarity rather than a boost.
     * divine-chance is still read, because every live config has one and
     * it is the one number Leon set by hand, and it simply joins the
     * chances as the Divine line.
     */
    private void loadEggs(org.bukkit.configuration.file.FileConfiguration config) {
        eggs.clear();
        minPrestige = Math.max(0, config.getInt("pets.min-prestige", 10));
        var tiers = config.getConfigurationSection("pets.eggs.tiers");
        if (tiers != null) {
            for (String id : tiers.getKeys(false)) {
                var egg = tiers.getConfigurationSection(id);
                if (egg == null) continue;
                org.bukkit.Material icon = org.bukkit.Material.matchMaterial(egg.getString("icon", "TURTLE_EGG"));
                java.util.List<String> colors = egg.getStringList("colors");
                java.util.EnumMap<com.spacerng.solrng.rarity.Rarity, Double> chances =
                        new java.util.EnumMap<>(com.spacerng.solrng.rarity.Rarity.class);
                var written = egg.getConfigurationSection("chances");
                if (written != null) {
                    for (String key : written.getKeys(false)) {
                        try {
                            chances.put(com.spacerng.solrng.rarity.Rarity.valueOf(
                                            key.trim().toUpperCase(java.util.Locale.ROOT)),
                                    Math.max(0.0, written.getDouble(key)));
                        } catch (IllegalArgumentException ignored) {
                            // A rarity name nobody can resolve is a typo in
                            // config, not a reason to drop the whole egg.
                        }
                    }
                }
                double divine = Math.max(0.0, egg.getDouble("divine-chance", 0.0));
                if (divine > 0.0 && !chances.containsKey(com.spacerng.solrng.rarity.Rarity.DIVINE)) {
                    chances.put(com.spacerng.solrng.rarity.Rarity.DIVINE, divine);
                }
                if (chances.isEmpty()) chances.putAll(defaultChances(id));
                eggs.add(new PetEgg(id, egg.getString("display", id),
                        colors.isEmpty() ? java.util.List.of("#C77DFF", "#7FDBFF") : colors,
                        icon == null ? org.bukkit.Material.TURTLE_EGG : icon,
                        Math.max(1L, egg.getLong("cost", makeCost)),
                        Math.max(0, egg.getInt("min-prestige", minPrestige)),
                        rarityOr(egg.getString("min-rarity"), defaultFloor(id)),
                        chances, egg.getStringList("pets"), 0));
            }
        }
        if (eggs.isEmpty()) {
            eggs.add(new PetEgg("stardust", "Stardust Egg", java.util.List.of("#C9D6FF", "#7FDBFF"),
                    org.bukkit.Material.TURTLE_EGG, makeCost, 0,
                    defaultFloor("stardust"), defaultChances("stardust"),
                    java.util.List.of(), 0));
        }
        // V359: cheapest and earliest first, then numbered, because an
        // egg's place in the ladder is what decides how much its pets are
        // worth. Prestige leads, because that is the wall a player
        // actually meets; the price only breaks a tie.
        eggs.sort(java.util.Comparator.comparingInt(PetEgg::minPrestige)
                .thenComparingLong(PetEgg::cost));
        for (int i = 0; i < eggs.size(); i++) {
            PetEgg egg = eggs.get(i);
            eggs.set(i, new PetEgg(egg.id(), egg.display(), egg.colors(), egg.icon(), egg.cost(),
                    egg.minPrestige(), egg.floor(), egg.chances(), egg.pets(), i));
        }
    }

    /**
     * What a pet out of the nth egg is worth, as a multiple of the same
     * rarity out of the first one (V359).
     *
     * Leon gave 1.1x to 2.0x for the Prestige 10 egg and left the rest
     * open, so the ladder above it is one knob. At 1.15 the Prestige 100
     * egg is worth about 3.5 times the Prestige 10 one, which keeps a
     * fully grown Divine inside the range the old cap allowed. This is
     * the single number to move if the late eggs feel wrong.
     */
    public double eggBonusGrowth() {
        return eggBonusGrowth;
    }

    /** The bonus a fresh pet of this rarity out of this egg carries. */
    public double bonusFor(com.spacerng.solrng.rarity.Rarity rarity, int eggIndex) {
        return bonusFor(rarity) * Math.pow(eggBonusGrowth, Math.max(0, eggIndex));
    }

    /**
     * The floor rarity of an egg: what it hatches when none of its own
     * chances come in. V284's floors, and they stay Leon's, so a dearer
     * egg never hands out Commons.
     */
    private static com.spacerng.solrng.rarity.Rarity defaultFloor(String id) {
        return switch (id) {
            case "nebula" -> com.spacerng.solrng.rarity.Rarity.UNCOMMON;
            case "supernova" -> com.spacerng.solrng.rarity.Rarity.RARE;
            default -> com.spacerng.solrng.rarity.Rarity.COMMON;
        };
    }

    /** The V324 ladder, for a config with no chances block yet. */
    private static java.util.EnumMap<com.spacerng.solrng.rarity.Rarity, Double> defaultChances(String id) {
        var out = new java.util.EnumMap<com.spacerng.solrng.rarity.Rarity, Double>(
                com.spacerng.solrng.rarity.Rarity.class);
        switch (id) {
            case "nebula" -> {
                out.put(com.spacerng.solrng.rarity.Rarity.RARE, 0.25);
                out.put(com.spacerng.solrng.rarity.Rarity.EPIC, 0.07);
                out.put(com.spacerng.solrng.rarity.Rarity.LEGENDARY, 0.015);
                out.put(com.spacerng.solrng.rarity.Rarity.MYTHICAL, 0.002);
                out.put(com.spacerng.solrng.rarity.Rarity.DIVINE, 0.001);
            }
            case "supernova" -> {
                out.put(com.spacerng.solrng.rarity.Rarity.EPIC, 0.25);
                out.put(com.spacerng.solrng.rarity.Rarity.LEGENDARY, 0.08);
                out.put(com.spacerng.solrng.rarity.Rarity.MYTHICAL, 0.02);
                out.put(com.spacerng.solrng.rarity.Rarity.DIVINE, 0.01);
            }
            default -> {
                out.put(com.spacerng.solrng.rarity.Rarity.UNCOMMON, 0.25);
                out.put(com.spacerng.solrng.rarity.Rarity.RARE, 0.06);
                out.put(com.spacerng.solrng.rarity.Rarity.EPIC, 0.01);
                out.put(com.spacerng.solrng.rarity.Rarity.LEGENDARY, 0.001);
                out.put(com.spacerng.solrng.rarity.Rarity.MYTHICAL, 0.0001);
            }
        }
        return out;
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

    public int minPrestige() {
        return minPrestige;
    }

    // ---------------------------------------------------------------
    // What a pet is worth
    // ---------------------------------------------------------------

    /**
     * The multiplier on a pet's base bonus. A fresh level 1 pet returns
     * exactly 1.0, so what the rarity ladder in pets.multipliers says is
     * exactly what a new pet gives. Level 10 lands on 1.9, which is
     * Leon's "every level is 10% more multi" counted from level 1.
     */
    public double multiplier(PetInstance pet) {
        if (pet == null) return 1.0;
        double value = 1.0 + levelStep * (pet.level() - 1);
        if (pet.shiny()) value *= 1.0 + shinyBonus;
        return Math.min(maxMultiplier, value);
    }

    /** The most any pet can be worth: top level and shiny, under the ceiling. */
    public double topMultiplier() {
        return Math.min(maxMultiplier, (1.0 + levelStep * (maxLevel - 1)) * (1.0 + shinyBonus));
    }

    /** True once a pet is pinned against the ceiling, so the menu can say so. */
    public boolean capped(PetInstance pet) {
        if (pet == null) return false;
        double raw = (1.0 + levelStep * (pet.level() - 1))
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

    /** Cosmic Dust to go from this level to the next. */
    public long levelCost(int fromLevel) {
        return (long) Math.ceil(levelBaseCost * Math.pow(levelCostGrowth, Math.max(0, fromLevel - 1)));
    }

    /** Gems to make a pet shiny. */
    public long shinyCost() {
        return shinyCost;
    }

    // ---------------------------------------------------------------
    // Whether it takes
    // ---------------------------------------------------------------

    public int maxLevel() {
        return maxLevel;
    }

    /** How many pets a player may hold before the eggs stop opening. */
    public int storage() {
        return storage;
    }

    public double levelStep() {
        return levelStep;
    }

    public double shinyBonus() {
        return shinyBonus;
    }
}
