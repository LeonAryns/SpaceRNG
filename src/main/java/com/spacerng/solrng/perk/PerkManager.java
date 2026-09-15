package com.spacerng.solrng.perk;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Loads the perks section of config.yml, rolls perks, and answers the
 * "what does this player's loadout add up to" question that StatSources
 * asks whenever it computes a stat.
 *
 * A perk is a tier and one to three stats from the pool. Each stat rolls
 * its own bonus inside the tier's range, weighted toward the low end by
 * roll-curve, so a roll near the top of a range stays something to chase.
 * Costs, tier chances, ranges and stat counts all come from config.
 */
public class PerkManager {

    /** One buyable Roll button in /perks. */
    public record RollTier(Rarity costRarity, int costAmount, Map<Rarity, Double> tierChances) { }

    /** Bonus ranges (0.1 is 1.1x) for a config that names none, and for converting old perks. */
    static final Map<Rarity, double[]> DEFAULT_RANGES = new EnumMap<>(Rarity.class);

    static {
        DEFAULT_RANGES.put(Rarity.COMMON, new double[]{0.10, 0.50});
        DEFAULT_RANGES.put(Rarity.UNCOMMON, new double[]{0.15, 0.75});
        DEFAULT_RANGES.put(Rarity.RARE, new double[]{0.20, 1.00});
        DEFAULT_RANGES.put(Rarity.EPIC, new double[]{0.25, 1.25});
        DEFAULT_RANGES.put(Rarity.LEGENDARY, new double[]{0.30, 1.50});
        DEFAULT_RANGES.put(Rarity.MYTHICAL, new double[]{0.40, 2.00});
        DEFAULT_RANGES.put(Rarity.DIVINE, new double[]{0.50, 3.00});
    }

    /** One stat up to Rare, two for Epic and Legendary, three from Mythical. */
    static int defaultStatCount(Rarity tier) {
        return switch (tier) {
            case COMMON, UNCOMMON, RARE -> 1;
            case EPIC, LEGENDARY -> 2;
            default -> 3;
        };
    }

    private final Logger logger;
    private final Map<Rarity, RollTier> rolls = new LinkedHashMap<>();
    private final Map<Rarity, Integer> statCount = new EnumMap<>(Rarity.class);
    private final Map<Rarity, double[]> ranges = new EnumMap<>(Rarity.class);
    private final List<PerkStat> pool = new ArrayList<>();
    private double rollCurve = 2.0;
    private int loadoutSlots = 3;
    private long saveCost = 1L;

    public PerkManager(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        rolls.clear();
        statCount.clear();
        ranges.clear();
        pool.clear();

        loadoutSlots = Math.max(1, config.getInt("perks.loadout-slots", 3));
        saveCost = Math.max(0L, config.getLong("perks.save-cost-credits", 1L));
        rollCurve = Math.max(0.1, config.getDouble("perks.roll-curve", 2.0));

        for (String name : config.getStringList("perks.stat-pool")) {
            try {
                PerkStat stat = PerkStat.valueOf(name);
                if (!pool.contains(stat)) pool.add(stat);
            } catch (IllegalArgumentException ignored) { }
        }
        if (pool.isEmpty()) pool.addAll(PerkStat.rollableStats());

        ConfigurationSection sc = config.getConfigurationSection("perks.stat-count");
        if (sc != null) {
            for (String key : sc.getKeys(false)) {
                try {
                    statCount.put(Rarity.valueOf(key), Math.max(1, sc.getInt(key, 1)));
                } catch (IllegalArgumentException ignored) { }
            }
        }

        // Written in config as multipliers ([1.1, 1.5]), kept here as bonuses.
        ConfigurationSection rs = config.getConfigurationSection("perks.stat-ranges");
        for (Rarity tier : Rarity.values()) {
            double[] fallback = DEFAULT_RANGES.get(tier);
            List<Double> pair = rs == null ? List.of() : rs.getDoubleList(tier.name());
            double low = pair.size() > 0 ? pair.get(0) - 1.0 : fallback[0];
            double high = pair.size() > 1 ? pair.get(1) - 1.0 : fallback[1];
            ranges.put(tier, new double[]{Math.max(0.0, Math.min(low, high)), Math.max(0.0, Math.max(low, high))});
        }

        ConfigurationSection rollsSection = config.getConfigurationSection("perks.rolls");
        if (rollsSection != null) {
            for (String key : rollsSection.getKeys(false)) {
                Rarity rollTier;
                try {
                    rollTier = Rarity.valueOf(key);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                ConfigurationSection r = rollsSection.getConfigurationSection(key);
                if (r == null) continue;
                Rarity costRarity;
                try {
                    costRarity = Rarity.valueOf(r.getString("cost-rarity", key));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                int amount = Math.max(1, r.getInt("cost-amount", 1));

                Map<Rarity, Double> chances = new EnumMap<>(Rarity.class);
                ConfigurationSection tc = r.getConfigurationSection("tier-chances");
                if (tc != null) {
                    for (String t : tc.getKeys(false)) {
                        try {
                            chances.put(Rarity.valueOf(t), tc.getDouble(t));
                        } catch (IllegalArgumentException ignored) { }
                    }
                }
                rolls.put(rollTier, new RollTier(costRarity, amount, chances));
            }
        }

        logger.info("Loaded " + rolls.size() + " perk roll tiers over " + pool.size() + " stats.");
    }

    public int loadoutSlots() { return loadoutSlots; }

    /** Credits one save roll costs. */
    public long saveCost() { return saveCost; }

    public Map<Rarity, RollTier> getRolls() { return rolls; }

    public RollTier getRoll(Rarity tier) { return rolls.get(tier); }

    /** The stats perks roll from, in config order. */
    public List<PerkStat> pool() { return Collections.unmodifiableList(pool); }

    public int statCountFor(Rarity tier) {
        return Math.min(pool.size(), statCount.getOrDefault(tier, defaultStatCount(tier)));
    }

    /** The lowest and highest bonus a stat can roll at a tier. */
    public double[] rangeOf(Rarity tier) {
        return ranges.getOrDefault(tier, DEFAULT_RANGES.get(tier));
    }

    /** Where a bonus sits in its tier's range: 0 the floor, 1 the ceiling. */
    public double quality(Rarity tier, double bonus) {
        double[] range = rangeOf(tier);
        if (range[1] <= range[0]) return 1.0;
        return Math.max(0.0, Math.min(1.0, (bonus - range[0]) / (range[1] - range[0])));
    }

    public double valueOf(PerkInstance perk, PerkStat stat) {
        return perk.stats().getOrDefault(stat, 0.0);
    }

    /** Every stat a perk grants, and how much. */
    public Map<PerkStat, Double> statsOf(PerkInstance perk) {
        return perk.stats();
    }

    /** One stat's bonus across every equipped perk, added up. */
    public double totalOf(PlayerData data, PerkStat stat) {
        double total = 0.0;
        for (PerkInstance perk : data.getEquippedPerks()) {
            total += perk.stats().getOrDefault(stat, 0.0);
        }
        return total;
    }

    /** Chance one roll of this tier lands on a perk of the given tier. */
    public double chanceOf(Rarity rollTier, Rarity resultTier) {
        RollTier roll = rolls.get(rollTier);
        if (roll == null) return 0.0;
        double total = 0.0;
        for (double v : roll.tierChances().values()) total += v;
        if (total <= 0.0) return 0.0;
        return roll.tierChances().getOrDefault(resultTier, 0.0) / total;
    }

    /** Chance one roll gives a perk of the given tier that carries this stat. */
    public double chanceOf(Rarity rollTier, Rarity resultTier, PerkStat stat) {
        if (!pool.contains(stat) || pool.isEmpty()) return 0.0;
        return chanceOf(rollTier, resultTier) * statCountFor(resultTier) / (double) pool.size();
    }

    /**
     * Rolls a fresh perk from one of the roll tiers: a tier from the roll's
     * table, then that tier's number of different stats, each with a bonus
     * inside the tier's range. Null when the roll table is empty, so a bad
     * config fails loudly rather than handing out a Common every time.
     */
    public PerkInstance rollFrom(Rarity rollTier) {
        RollTier roll = rolls.get(rollTier);
        if (roll == null || roll.tierChances().isEmpty() || pool.isEmpty()) return null;

        Rarity resultTier = pickTier(roll.tierChances());
        if (resultTier == null) return null;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<PerkStat> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        double[] range = rangeOf(resultTier);
        Map<PerkStat, Double> stats = new EnumMap<>(PerkStat.class);
        for (int i = 0; i < statCountFor(resultTier); i++) {
            double bonus = range[0] + (range[1] - range[0]) * Math.pow(random.nextDouble(), rollCurve);
            stats.put(shuffled.get(i), Math.round(bonus * 100.0) / 100.0);
        }
        return new PerkInstance(UUID.randomUUID(), resultTier, stats);
    }

    private Rarity pickTier(Map<Rarity, Double> chances) {
        double total = 0.0;
        for (double v : chances.values()) total += v;
        if (total <= 0.0) return null;

        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double cumulative = 0.0;
        for (var entry : chances.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) return entry.getKey();
        }
        // Rounding safety: fall back to the last entry.
        Rarity last = null;
        for (Rarity r : chances.keySet()) last = r;
        return last;
    }

    /**
     * Runs one purchase-and-roll transaction: spends the drops if there
     * are enough (bank first, physical inventory second), and hands back
     * the rolled perk. Null return means the roll didn't happen because
     * the player couldn't afford it.
     */
    public PerkInstance purchase(SolRNGPlugin plugin, org.bukkit.entity.Player player,
                                 PlayerData data, Rarity rollTier) {
        RollTier roll = rolls.get(rollTier);
        if (roll == null) return null;

        long available = data.getBankedDrops(roll.costRarity())
                + countPhysicalDrops(plugin, player, roll.costRarity());
        if (available < roll.costAmount()) return null;

        spendDrops(plugin, player, data, roll.costRarity(), roll.costAmount());

        // Auto save puts the perk straight in the vault for one save roll.
        // Otherwise it waits in the roller and an unsaved one from before is gone.
        PerkInstance perk = rollFrom(rollTier);
        if (perk != null) {
            if (data.isPerkAutoSave() && data.getPerkSaveRolls() > 0) {
                data.setPerkSaveRolls(data.getPerkSaveRolls() - 1);
                data.getPerkVault().add(perk);
            } else {
                data.setPendingPerk(perk);
            }
        }
        return perk;
    }

    /** Buys save rolls for Credits. False when the player can't pay. */
    public boolean buySaveRolls(PlayerData data, int amount) {
        if (amount <= 0 || !data.spendPoints(amount * saveCost)) return false;
        data.setPerkSaveRolls(data.getPerkSaveRolls() + amount);
        return true;
    }

    /**
     * Moves the waiting perk into the vault for a save roll, or for
     * {@link #saveCost()} Credits when there are none. Null when there is
     * no perk waiting or the player can't pay.
     */
    public PerkInstance save(PlayerData data) {
        PerkInstance pending = data.getPendingPerk();
        if (pending == null) return null;
        if (data.getPerkSaveRolls() > 0) {
            data.setPerkSaveRolls(data.getPerkSaveRolls() - 1);
        } else if (!data.spendPoints(saveCost)) {
            return null;
        }
        data.getPerkVault().add(pending);
        data.setPendingPerk(null);
        return pending;
    }

    private long countPhysicalDrops(SolRNGPlugin plugin, org.bukkit.entity.Player player, Rarity rarity) {
        var key = plugin.getRollListener().getRarityKey();
        long total = 0L;
        for (var stack : player.getInventory().getContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String name = stack.getItemMeta().getPersistentDataContainer()
                    .get(key, org.bukkit.persistence.PersistentDataType.STRING);
            if (name == null) continue;
            try {
                if (Rarity.valueOf(name) == rarity) total += stack.getAmount();
            } catch (IllegalArgumentException ignored) { }
        }
        return total;
    }

    /** Bank first (cheaper to spend, doesn't touch the hotbar), then inventory. */
    private void spendDrops(SolRNGPlugin plugin, org.bukkit.entity.Player player,
                            PlayerData data, Rarity rarity, int amount) {
        long fromBank = data.takeBankedDrops(rarity, amount);
        long remaining = amount - fromBank;
        if (remaining <= 0) return;

        var key = plugin.getRollListener().getRarityKey();
        var contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            var stack = contents[i];
            if (stack == null || stack.getItemMeta() == null) continue;
            String name = stack.getItemMeta().getPersistentDataContainer()
                    .get(key, org.bukkit.persistence.PersistentDataType.STRING);
            if (name == null) continue;
            try {
                if (Rarity.valueOf(name) != rarity) continue;
            } catch (IllegalArgumentException ex) { continue; }

            int take = (int) Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) player.getInventory().setItem(i, null);
            remaining -= take;
        }
    }
}
