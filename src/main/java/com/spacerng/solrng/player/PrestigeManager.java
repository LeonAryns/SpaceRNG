package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Levels are earned purely by rolling — reaching level*rolls-per-level
 * total rolls lets you level up. Prestiging resets your level back to 1
 * in exchange for a permanent Luck *multiplier* (unlike every other Luck
 * source, which is additive), and needs progressively more levels each
 * time (first-prestige-levels, then +levels-increment-per-prestige each
 * time after).
 */
public class PrestigeManager {

    private final SolRNGPlugin plugin;
    private int rollsPerLevel;
    private int firstPrestigeLevels;
    private int levelsIncrementPerPrestige;
    private double luckMultiplierPerPrestige;
    private int pointsPerPrestige = 1;
    private final java.util.Map<String, PrestigeUpgrade> upgrades = new java.util.LinkedHashMap<>();

    public PrestigeManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        rollsPerLevel = config.getInt("prestige.rolls-per-level", 50);
        firstPrestigeLevels = config.getInt("prestige.first-prestige-levels", 10);
        levelsIncrementPerPrestige = config.getInt("prestige.levels-increment-per-prestige", 5);
        luckMultiplierPerPrestige = config.getDouble("prestige.luck-multiplier-per-prestige", 0.10);
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
                            u.getString("unit", "%")));
                } catch (Exception ex) {
                    plugin.getLogger().warning("[SolRNG] Skipped malformed prestige upgrade '" + id + "'.");
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

    /** The total an upgrade effect is contributing for this player. */
    public double upgradeTotal(PlayerData data, PrestigeUpgrade.Effect effect) {
        double total = 0.0;
        for (PrestigeUpgrade upgrade : upgrades.values()) {
            if (upgrade.getEffect() == effect) {
                total += upgrade.totalAt(data.getUpgradeLevel(upgrade.getId()));
            }
        }
        return total;
    }

    /**
     * Buys one level. Returns false when it's maxed or unaffordable — the
     * caller reports which, since the menu already knows both.
     */
    public boolean buyUpgrade(PlayerData data, String id) {
        PrestigeUpgrade upgrade = upgrades.get(id);
        if (upgrade == null) return false;

        int level = data.getUpgradeLevel(id);
        if (level >= upgrade.getMaxLevel()) return false;
        if (!data.spendPrestigePoints(upgrade.getCostPoints())) return false;

        data.setUpgradeLevel(id, level + 1);
        return true;
    }

    public long rollsNeededForNextLevel(PlayerData data) {
        return (long) data.getLevel() * rollsPerLevel;
    }

    public int levelsNeededForNextPrestige(PlayerData data) {
        return firstPrestigeLevels + data.getPrestige() * levelsIncrementPerPrestige;
    }

    public boolean canLevelUp(PlayerData data) {
        return data.getTotalRolls() >= rollsNeededForNextLevel(data);
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
     * who owns it — nothing is frozen into a save file.
     */
    public double baseLuck(PlayerData data) {
        SkillTreeManager skills = plugin.getSkillTreeManager();

        double starforge = data.getStarforgeLuckBonus()
                * skills.multiplierOf(data, SkillNode.Effect.STARFORGE_POWER);
        double armor = data.getArmorLuckBonus()
                * skills.multiplierOf(data, SkillNode.Effect.ARMOR_POWER);
        double curator = skills.totalOf(data, SkillNode.Effect.LUCK_PER_DISCOVERY)
                * data.getDiscoveredItems().size();
        double affinity = skills.totalOf(data, SkillNode.Effect.LUCK_PER_PRESTIGE)
                * data.getPrestige();

        double flat = starforge + armor + skills.skillLuck(data)
                + curator + affinity + data.getFlatLuck();

        double luck = flat
                * plugin.getRarityManager().tagMultiplierFor(data)
                * (1.0 + data.getPrestige() * luckMultiplierPerPrestige);

        // Prestige Points spent on Luck ride along with everything else the
        // player has bought, before the global boost scales the total.
        luck += upgradeTotal(data, PrestigeUpgrade.Effect.LUCK_BONUS);
        return luck * plugin.getBoostManager().multiplier();
    }

    /** Everything: base Luck, the global boost, and the Nova Core tier. */
    public double effectiveLuck(PlayerData data) {
        return baseLuck(data) * plugin.getNovaCoreManager().multiplierAt(data.getNovaTier());
    }
}
