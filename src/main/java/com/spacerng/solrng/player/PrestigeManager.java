package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Levels are earned purely by rolling - reaching level*rolls-per-level
 * total rolls lets you level up. Prestiging resets your level back to 1
 * in exchange for a permanent Luck *multiplier* (unlike every other Luck
 * source, which is additive), and needs progressively more levels each
 * time (first-prestige-levels, then +levels-increment-per-prestige each
 * time after).
 */
public class PrestigeManager {

    private final SolRNGPlugin plugin;
    private int rollsPerLevel;
    private double levelCostGrowth = 1.15;
    private int firstPrestigeLevels;
    private int levelsIncrementPerPrestige;
    private double luckMultiplierPerPrestige;
    private double indexCompletionPerRarity = 2.0;
    private double indexCompletionPerShiny = 5.0;
    private int pointsPerPrestige = 1;
    private final java.util.Map<String, PrestigeUpgrade> upgrades = new java.util.LinkedHashMap<>();

    public PrestigeManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        rollsPerLevel = config.getInt("prestige.rolls-per-level", 50);
        levelCostGrowth = config.getDouble("prestige.level-cost-growth", 1.15);
        firstPrestigeLevels = config.getInt("prestige.first-prestige-levels", 10);
        levelsIncrementPerPrestige = config.getInt("prestige.levels-increment-per-prestige", 5);
        luckMultiplierPerPrestige = config.getDouble("prestige.luck-multiplier-per-prestige", 0.10);
        indexCompletionPerRarity = config.getDouble("index.completion.per-rarity", 2.0);
        indexCompletionPerShiny = config.getDouble("index.completion.per-shiny-rarity", 3.0);
        pointsPerPrestige = config.getInt("prestige.points-per-prestige", 1);

        upgrades.clear();
        var section = config.getConfigurationSection("prestige.upgrades");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                var u = section.getConfigurationSection(id);
                if (u == null) continue;
                try {
                    int slot = -1;
                    String raw = u.getString("slot", "");
                    if (!raw.isBlank()) {
                        String[] parts = raw.split(",");
                        slot = SkillNode.slotOf(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                    }
                    upgrades.put(id, new PrestigeUpgrade(id,
                            u.getString("display", id),
                            u.getString("icon", "PAPER"),
                            slot,
                            PrestigeUpgrade.Effect.valueOf(u.getString("effect", "LUCK_BONUS").toUpperCase()),
                            u.getDouble("per-level", 0.0),
                            u.getInt("max-level", 10),
                            u.getInt("cost-points", 1),
                            u.getString("unit", "%"),
                            u.getString("requires", "")));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipped malformed prestige upgrade '" + id + "'.");
                }
            }
        }
    }

    public java.util.Map<String, PrestigeUpgrade> getUpgrades() {
        return upgrades;
    }

    public PrestigeUpgrade getUpgrade(String id) {
        return upgrades.get(id);
    }

    public int getPointsPerPrestige() {
        return pointsPerPrestige;
    }

    /**
     * The compounding total for a multiplicative effect, as a multiplier.
     *
     * Returns 1.0 when nothing is bought, so a caller can multiply by it
     * unconditionally.
     */
    public double upgradeMultiplier(PlayerData data, PrestigeUpgrade.Effect effect) {
        double total = 1.0;
        for (PrestigeUpgrade upgrade : upgrades.values()) {
            if (upgrade.getEffect() == effect) {
                total *= upgrade.multiplierAt(data.getUpgradeLevel(upgrade.getId()));
            }
        }
        return total;
    }

    /** The additive total an upgrade effect is contributing. */
    public double upgradeTotal(PlayerData data, PrestigeUpgrade.Effect effect) {
        double total = 0.0;
        for (PrestigeUpgrade upgrade : upgrades.values()) {
            if (upgrade.getEffect() == effect) {
                total += upgrade.totalAt(data.getUpgradeLevel(upgrade.getId()));
            }
        }
        return total;
    }

    /** Whether an upgrade's own requirement is bought (V346). */
    public boolean requirementMet(PlayerData data, PrestigeUpgrade upgrade) {
        String needs = upgrade.getRequires();
        return needs.isEmpty() || data.getUpgradeLevel(needs) > 0;
    }

    /** Whether any upgrade with this effect is bought at all. */
    public boolean hasUpgrade(PlayerData data, PrestigeUpgrade.Effect effect) {
        for (PrestigeUpgrade upgrade : upgrades.values()) {
            if (upgrade.getEffect() == effect && data.getUpgradeLevel(upgrade.getId()) > 0) return true;
        }
        return false;
    }

    /**
     * Hands back the points spent on upgrades that no longer exist (V346).
     *
     * The board is the Secret Realm now, so Lucky Star, Coin Master, Gold
     * Rush, Gem Seeker and Nova Touch are gone. Nobody is going to lose
     * what they paid for them, so the first time a save is seen after the
     * change its levels are cleared and the points come back at the price
     * those upgrades charged.
     */
    public int refundRetired(PlayerData data) {
        java.util.Map<String, Integer> oldCost = java.util.Map.of(
                "lucky_star", 1, "token_master", 1, "gold_rush", 1,
                "shard_seeker", 2, "nova_touch", 3);
        int points = 0;
        for (java.util.Map.Entry<String, Integer> entry : oldCost.entrySet()) {
            if (upgrades.containsKey(entry.getKey())) continue;
            int level = data.getUpgradeLevel(entry.getKey());
            if (level <= 0) continue;
            points += level * entry.getValue();
            data.setUpgradeLevel(entry.getKey(), 0);
        }
        if (points > 0) data.addPrestigePoints(points);
        return points;
    }

    /**
     * Buys one level. Returns false when it's maxed or unaffordable - the
     * caller reports which, since the menu already knows both.
     */
    public boolean buyUpgrade(PlayerData data, String id) {
        PrestigeUpgrade upgrade = upgrades.get(id);
        if (upgrade == null) return false;

        int level = data.getUpgradeLevel(id);
        if (level >= upgrade.getMaxLevel()) return false;
        if (!requirementMet(data, upgrade)) return false;
        if (!data.spendPrestigePoints(upgrade.getCostPoints())) return false;

        data.setUpgradeLevel(id, level + 1);
        return true;
    }

    /**
     * Total rolls to reach the next level, compounding.
     *
     * It used to be level x rolls-per-level, so level 50 cost the same
     * fifty rolls that level 2 did and the ladder flattened into a
     * formality. Each level now costs `level-cost-growth` times the one
     * before it, and this returns the geometric SUM because the caller
     * compares it against lifetime rolls.
     */
    public long rollsNeededForNextLevel(PlayerData data) {
        return rollsNeededForLevel(data.getLevel());
    }

    /**
     * Lifetime rolls needed to leave the given level.
     *
     * V354: the compounding STOPS at prestige.level-cost-cap-level. Past
     * it every level costs what that level cost, so the ladder goes from
     * exponential to straight. Compounding for ever is what made the late
     * prestiges harder than the early ones, which is the opposite of what
     * Leon wants from them: level 43 on the old curve wanted thousands of
     * rolls on its own.
     */
    public long rollsNeededForLevel(int level) {
        level = Math.max(1, level);
        if (levelCostGrowth <= 1.0) return (long) level * rollsPerLevel;
        int cap = levelCostCap();
        int compounded = Math.min(level, cap);
        double total = rollsPerLevel
                * (Math.pow(levelCostGrowth, compounded) - 1.0) / (levelCostGrowth - 1.0);
        if (level > cap) {
            // One flat level, at what the capped level costs.
            double flat = rollsPerLevel * Math.pow(levelCostGrowth, cap - 1);
            total += flat * (level - cap);
        }
        return Math.round(total);
    }

    /** Where the per-level cost stops compounding. 0 or less means never. */
    private int levelCostCap() {
        int cap = plugin.getConfig().getInt("prestige.level-cost-cap-level", 25);
        return cap <= 0 ? Integer.MAX_VALUE : cap;
    }

    /**
     * V354: the levels asked for stop growing at
     * prestige.max-levels-per-prestige. They used to climb by one every
     * prestige for ever, so prestige 30 asked forty levels of a ladder
     * that was also getting steeper - the late game got harder twice over.
     */
    public int levelsNeededForNextPrestige(PlayerData data) {
        int needed = firstPrestigeLevels + data.getPrestige() * levelsIncrementPerPrestige;
        int max = plugin.getConfig().getInt("prestige.max-levels-per-prestige", 25);
        return max > 0 ? Math.min(needed, max) : needed;
    }

    /**
     * V290: rolls since the last prestige, not lifetime rolls. A prestige
     * sets the level back to 1 and the prestige counter to 0, but this
     * asked the lifetime count, so straight after a prestige every old roll
     * still counted and the levels could be bought straight back (a
     * player's bug report).
     */
    public boolean canLevelUp(PlayerData data) {
        return data.getRollsThisPrestige() >= rollsNeededForNextLevel(data);
    }

    public boolean canPrestige(PlayerData data) {
        return data.getLevel() >= levelsNeededForNextPrestige(data);
    }

    public boolean levelUp(PlayerData data) {
        if (!canLevelUp(data)) return false;
        data.setLevel(data.getLevel() + 1);
        return true;
    }

    public boolean prestige(PlayerData data) {
        if (!canPrestige(data)) return false;
        data.setPrestige(data.getPrestige() + 1);
        data.setLevel(1);
        data.setRollsThisPrestige(0L);
        data.addPrestigePoints(pointsPerPrestige);
        return true;
    }

    /**
     * Luck WITHOUT the Nova Core's own multiplier. The /novacore ladder
     * rolls against this: feeding a tier's multiplier back into the odds
     * of climbing to the next tier makes the ladder easier the further up
     * you get, which is backwards.
     *
     * This is the one place Luck is assembled, and the order matters:
     *
     *   flat  = Starforge (x Forge Attunement)
     *         + worn armor (x Quartermaster)
     *         + every Luck node bought
     *         + Curator, per entry found in /index
     *         + Prestige Affinity, per prestige held
     *         + anything granted directly (admin, future systems)
     *   total = flat x equipped tag's index multiplier
     *                x (1 + prestige x luck-multiplier-per-prestige)
     *         + Prestige Points spent on Luck
     *   result = total x the global boost
     *
     * Flat sources add so that a new one is always worth something; the
     * tag and prestige multiply so that late progression scales what you
     * already built rather than being one more small addition.
     *
     * Every skill contribution here is read live from node levels, so
     * retuning a value in config.yml immediately retunes it for everyone
     * who owns it - nothing is frozen into a save file.
     */
    public double baseLuck(PlayerData data) {
        return com.spacerng.solrng.stats.StatSources.luck(plugin, data, false).total();
    }

    public double getLuckMultiplierPerPrestige() {
        return luckMultiplierPerPrestige;
    }

    /** The Luck multiplier earned by finishing whole rarities in /index. */
    public double indexCompletion(PlayerData data) {
        return plugin.getRarityManager().completionMultiplier(data,
                indexCompletionPerRarity, indexCompletionPerShiny);
    }

    public double getIndexCompletionPerRarity() {
        return indexCompletionPerRarity;
    }

    public double getIndexCompletionPerShiny() {
        return indexCompletionPerShiny;
    }

    /** Everything: base Luck, the global boost, and the Nova Core tier. */
    public double effectiveLuck(PlayerData data) {
        return com.spacerng.solrng.stats.StatSources.luck(plugin, data, true).total();
    }
}
