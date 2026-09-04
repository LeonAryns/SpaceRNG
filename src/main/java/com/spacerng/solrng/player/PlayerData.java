package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private double bonusLuck = 0.0;
    private long points = 0L; // shown on the scoreboard as "Credits"
    private long tokens = 0L; // second scoreboard currency, earned from farming
    private long shards = 0L; // third scoreboard currency, reserved for future systems
    // Multiplies roll speed: 1.0 = base roll-duration-seconds, 2.0 = twice as fast.
    // Granted by the Rolling Speed skill tree branch; armor upgrades could
    // add to this too later.
    private double rollSpeedMultiplier = 1.0;
    private final Set<String> unlockedNodes = new HashSet<>();
    // Current level (0 = not started) of leveled skill tree nodes, e.g.
    // "speed_skill" -> 4 out of a maxLevel of 10. One-time nodes never
    // appear here — they live in unlockedNodes instead.
    private final Map<String, Integer> nodeLevels = new HashMap<>();
    // Item display names (e.g. "Fallen Star") the player has ever rolled —
    // backs /index and its per-discovery luck bonus.
    private final Set<String> discoveredItems = new HashSet<>();
    private final Set<Rarity> autoConvertRarities = EnumSet.noneOf(Rarity.class);
    // Auto-roll always fires at the player's own current roll speed — no
    // separate fixed interval.
    private boolean autoRollEnabled = false;
    private String equippedTagItemKey = null; // e.g. "Fallen Star"
    private String equippedTagRarity = null;  // stored so we can re-color it on load
    // Chance (0.0-1.0) of an extra free roll right after any roll finishes —
    // granted by the Bonus Roll skill tree branch.
    private double bonusRollChance = 0.0;
    // Lifetime count of Common/Uncommon items converted via /convert or
    // auto-convert — shown on the skill tree screen alongside what's
    // currently sitting unconverted in the player's inventory.
    private long convertedCommon = 0L;
    private long convertedUncommon = 0L;
    // Lifetime roll count — levels up off of this via /prestige.
    private long totalRolls = 0L;
    private int level = 1;
    private int prestige = 0;
    // Flat Luck bonus from currently-worn /armor — recomputed live each
    // tick from equipped armor, not persisted.
    private double armorLuckBonus = 0.0;
    // Flat Speed bonus from currently-worn /armor — same live recompute
    // as armorLuckBonus, not persisted.
    private double armorSpeedBonus = 0.0;
    // /armor tiers ever bought (e.g. "LEATHER") — one-time purchase, worn
    // status is checked separately for whether the Luck bonus applies.
    private final Set<String> purchasedArmorTiers = new HashSet<>();
    // /options toggles.
    private boolean rollSoundEnabled = true;
    private boolean rollAnimationEnabled = true;
    // Multiplies Tokens earned from harvesting farm crops. 1.0 = base
    // reward. Nothing raises this yet — reserved for future farming
    // upgrades (hoe enchants, prestige tie-in, etc.).
    private double farmTokenMultiplier = 1.0;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public double getBonusLuck() {
        return bonusLuck;
    }

    public void addBonusLuck(double amount) {
        this.bonusLuck += amount;
    }

    public long getPoints() {
        return points;
    }

    public void addPoints(long amount) {
        this.points += amount;
    }

    public boolean spendPoints(long amount) {
        if (points < amount) return false;
        points -= amount;
        return true;
    }

    public long getTokens() {
        return tokens;
    }

    public void addTokens(long amount) {
        this.tokens += amount;
    }

    public boolean spendTokens(long amount) {
        if (tokens < amount) return false;
        tokens -= amount;
        return true;
    }

    public long getShards() {
        return shards;
    }

    public void addShards(long amount) {
        this.shards += amount;
    }

    public boolean spendShards(long amount) {
        if (shards < amount) return false;
        shards -= amount;
        return true;
    }

    public double getRollSpeedMultiplier() {
        return rollSpeedMultiplier;
    }

    public void setRollSpeedMultiplier(double rollSpeedMultiplier) {
        this.rollSpeedMultiplier = Math.max(0.1, rollSpeedMultiplier);
    }

    public double getArmorSpeedBonus() {
        return armorSpeedBonus;
    }

    public void setArmorSpeedBonus(double armorSpeedBonus) {
        this.armorSpeedBonus = armorSpeedBonus;
    }

    /**
     * Total roll speed actually applied: skill-tree base plus worn armor.
     * 1.0 = the "100 Speed" baseline shown on the scoreboard (Speed = this
     * x 100, rounded).
     */
    public double getEffectiveRollSpeedMultiplier() {
        return Math.max(0.1, rollSpeedMultiplier + armorSpeedBonus);
    }

    public Set<String> getUnlockedNodes() {
        return unlockedNodes;
    }

    public boolean hasUnlocked(String nodeId) {
        return unlockedNodes.contains(nodeId);
    }

    public Map<String, Integer> getNodeLevels() {
        return nodeLevels;
    }

    public int getNodeLevel(String nodeId) {
        return nodeLevels.getOrDefault(nodeId, 0);
    }

    public void setNodeLevel(String nodeId, int level) {
        nodeLevels.put(nodeId, level);
    }

    public Set<String> getDiscoveredItems() {
        return discoveredItems;
    }

    public boolean hasDiscovered(String itemDisplayName) {
        return discoveredItems.contains(itemDisplayName);
    }

    public void markDiscovered(String itemDisplayName) {
        discoveredItems.add(itemDisplayName);
    }

    public Set<Rarity> getAutoConvertRarities() {
        return autoConvertRarities;
    }

    public boolean isAutoConverting(Rarity rarity) {
        return autoConvertRarities.contains(rarity);
    }

    public void toggleAutoConvert(Rarity rarity) {
        if (!autoConvertRarities.remove(rarity)) {
            autoConvertRarities.add(rarity);
        }
    }

    public boolean isAutoRollEnabled() {
        return autoRollEnabled;
    }

    public void setAutoRollEnabled(boolean autoRollEnabled) {
        this.autoRollEnabled = autoRollEnabled;
    }

    public String getEquippedTagItemKey() {
        return equippedTagItemKey;
    }

    public String getEquippedTagRarity() {
        return equippedTagRarity;
    }

    public void setEquippedTag(String itemKey, String rarityName) {
        this.equippedTagItemKey = itemKey;
        this.equippedTagRarity = rarityName;
    }

    public void clearEquippedTag() {
        this.equippedTagItemKey = null;
        this.equippedTagRarity = null;
    }

    public double getBonusRollChance() {
        return bonusRollChance;
    }

    public void addBonusRollChance(double amount) {
        this.bonusRollChance = Math.min(1.0, this.bonusRollChance + amount);
    }

    public long getConvertedCommon() {
        return convertedCommon;
    }

    public long getConvertedUncommon() {
        return convertedUncommon;
    }

    /**
     * Tracks a conversion for the skill tree's Common/Uncommon summary —
     * no-op for any other rarity.
     */
    public void addConverted(Rarity rarity, long amount) {
        if (rarity == Rarity.COMMON) {
            convertedCommon += amount;
        } else if (rarity == Rarity.UNCOMMON) {
            convertedUncommon += amount;
        }
    }

    public long getTotalRolls() {
        return totalRolls;
    }

    public void addRoll() {
        totalRolls++;
    }

    public void setTotalRolls(long totalRolls) {
        this.totalRolls = totalRolls;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
    }

    public double getArmorLuckBonus() {
        return armorLuckBonus;
    }

    public void setArmorLuckBonus(double armorLuckBonus) {
        this.armorLuckBonus = armorLuckBonus;
    }

    /**
     * Total Luck actually applied to rolls: base bonuses (skill tree,
     * index discoveries) plus worn armor, then scaled by the prestige
     * multiplier — prestige multiplies rather than adds, unlike every
     * other Luck source.
     */
    public double getEffectiveLuck(double prestigeLuckMultiplierPerPrestige) {
        return (bonusLuck + armorLuckBonus) * (1.0 + prestige * prestigeLuckMultiplierPerPrestige);
    }

    public Set<String> getPurchasedArmorTiers() {
        return purchasedArmorTiers;
    }

    public boolean hasPurchasedArmor(String tierId) {
        return purchasedArmorTiers.contains(tierId);
    }

    public void markArmorPurchased(String tierId) {
        purchasedArmorTiers.add(tierId);
    }

    public boolean isRollSoundEnabled() {
        return rollSoundEnabled;
    }

    public void setRollSoundEnabled(boolean rollSoundEnabled) {
        this.rollSoundEnabled = rollSoundEnabled;
    }

    public boolean isRollAnimationEnabled() {
        return rollAnimationEnabled;
    }

    public void setRollAnimationEnabled(boolean rollAnimationEnabled) {
        this.rollAnimationEnabled = rollAnimationEnabled;
    }

    public double getFarmTokenMultiplier() {
        return farmTokenMultiplier;
    }

    public void setFarmTokenMultiplier(double farmTokenMultiplier) {
        this.farmTokenMultiplier = Math.max(0.1, farmTokenMultiplier);
    }
}
