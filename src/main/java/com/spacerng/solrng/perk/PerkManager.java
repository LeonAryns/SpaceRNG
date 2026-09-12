package com.spacerng.solrng.perk;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Loads the perks section of config.yml, rolls perks, and answers the
 * "what does this player's loadout add up to" question that StatSources
 * asks whenever it computes a stat.
 *
 * The whole roll table is data-driven: costs, tier chances, level
 * chances, tier and level multipliers, and stat ceilings all come from
 * config so retuning is a config change rather than a recompile.
 */
public class PerkManager {

    /** One buyable Roll button in /perks. */
    public record RollTier(Rarity costRarity, int costAmount, Map<Rarity, Double> tierChances) { }

    private final Logger logger;
    private final Map<Rarity, RollTier> rolls = new LinkedHashMap<>();
    private final Map<Rarity, Double> tierMultipliers = new EnumMap<>(Rarity.class);
    private final double[] levelMultipliers = new double[5];
    private final double[] levelChances = new double[5];
    private final Map<Rarity, Integer> statCount = new EnumMap<>(Rarity.class);
    private final Map<PerkStat, Double> statCeilings = new EnumMap<>(PerkStat.class);
    private int loadoutSlots = 3;

    public PerkManager(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        rolls.clear();
        tierMultipliers.clear();
        statCount.clear();
        statCeilings.clear();

        loadoutSlots = Math.max(1, config.getInt("perks.loadout-slots", 3));

        // Level chances - the piramid for I..V.
        var lc = config.getDoubleList("perks.level-chances");
        for (int i = 0; i < 5; i++) {
            levelChances[i] = i < lc.size() ? lc.get(i) : (i == 0 ? 1.0 : 0.0);
        }
        var lm = config.getDoubleList("perks.level-multipliers");
        for (int i = 0; i < 5; i++) {
            levelMultipliers[i] = i < lm.size() ? lm.get(i) : (i + 1) * 0.2;
        }

        ConfigurationSection tm = config.getConfigurationSection("perks.tier-multipliers");
        if (tm != null) {
            for (String key : tm.getKeys(false)) {
                try {
                    tierMultipliers.put(Rarity.valueOf(key), tm.getDouble(key));
                } catch (IllegalArgumentException ignored) { }
            }
        }

        ConfigurationSection sc = config.getConfigurationSection("perks.stat-count");
        if (sc != null) {
            for (String key : sc.getKeys(false)) {
                try {
                    statCount.put(Rarity.valueOf(key), sc.getInt(key, 1));
                } catch (IllegalArgumentException ignored) { }
            }
        }

        ConfigurationSection ceil = config.getConfigurationSection("perks.stat-ceilings");
        if (ceil != null) {
            for (String key : ceil.getKeys(false)) {
                try {
                    statCeilings.put(PerkStat.valueOf(key), ceil.getDouble(key));
                } catch (IllegalArgumentException ignored) { }
            }
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

        logger.info("Loaded " + rolls.size() + " perk roll tiers with "
                + statCeilings.size() + " stats and " + tierMultipliers.size() + " tier multipliers.");
    }

    public int loadoutSlots() { return loadoutSlots; }

    public Map<Rarity, RollTier> getRolls() { return rolls; }

    public RollTier getRoll(Rarity tier) { return rolls.get(tier); }

    public int statCountFor(Rarity tier) {
        return statCount.getOrDefault(tier, 1);
    }

    public double tierMultiplierFor(Rarity tier) {
        return tierMultipliers.getOrDefault(tier, 0.0);
    }

    public double levelMultiplier(int level) {
        int i = Math.max(1, Math.min(5, level)) - 1;
        return levelMultipliers[i];
    }

    /**
     * The effective value one perk grants for one stat, folded through
     * the tier and level curves.
     */
    public double valueOf(PerkInstance perk, PerkStat stat) {
        Double ceiling = statCeilings.get(stat);
        if (ceiling == null) return 0.0;
        return ceiling * tierMultiplierFor(perk.tier()) * levelMultiplier(perk.level());
    }

    /** Every stat a perk grants, and how much. */
    public Map<PerkStat, Double> statsOf(PerkInstance perk) {
        Map<PerkStat, Double> out = new EnumMap<>(PerkStat.class);
        for (PerkStat stat : perk.type().statsFor(statCountFor(perk.tier()))) {
            out.merge(stat, valueOf(perk, stat), Double::sum);
        }
        return out;
    }

    /** Sums one stat across every equipped perk. */
    public double totalOf(PlayerData data, PerkStat stat) {
        double total = 0.0;
        for (PerkInstance perk : data.getEquippedPerks()) {
            for (var entry : statsOf(perk).entrySet()) {
                if (entry.getKey() == stat) total += entry.getValue();
            }
        }
        return total;
    }

    /**
     * Rolls a fresh perk from one of the roll tiers.
     *
     * Tier and level roll independently against their own tables; type
     * is uniform across the five (there is no "better" type). Returns
     * null when the roll table is empty, so a bad config still fails
     * loudly rather than silently handing out a Common every time.
     */
    public PerkInstance rollFrom(Rarity rollTier) {
        RollTier roll = rolls.get(rollTier);
        if (roll == null || roll.tierChances().isEmpty()) return null;

        Rarity resultTier = pickTier(roll.tierChances());
        if (resultTier == null) return null;

        int level = pickLevel();
        PerkType type = PerkType.values()[ThreadLocalRandom.current().nextInt(PerkType.values().length)];

        return PerkInstance.freshly(type, resultTier, level);
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

    private int pickLevel() {
        double total = 0.0;
        for (double v : levelChances) total += v;
        if (total <= 0.0) return 1;

        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < levelChances.length; i++) {
            cumulative += levelChances[i];
            if (roll <= cumulative) return i + 1;
        }
        return levelChances.length;
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

        PerkInstance perk = rollFrom(rollTier);
        if (perk != null) data.getPerkVault().add(perk);
        return perk;
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
