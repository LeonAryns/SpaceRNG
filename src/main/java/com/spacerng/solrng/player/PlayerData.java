package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;

import java.util.EnumMap;
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
    // Virtual drop bank: /convert turns physical rolled items into stored
    // drops of the same rarity instead of Credits, and /armor and
    // /starforge spend from here once the player's inventory runs out.
    // Credits stay reserved for the paid store.
    private final Map<Rarity, Long> dropBank = new EnumMap<>(Rarity.class);
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
    // /armor pieces ever bought, keyed "TIER:PIECE" (e.g.
    // "LEATHER:BOOTS") — pieces are sold individually. Worn status is
    // checked separately for whether the Luck bonus applies.
    private final Set<String> purchasedArmorTiers = new HashSet<>();
    // /options toggles.
    private boolean rollSoundEnabled = true;
    private boolean rollAnimationEnabled = true;
    // Whether this player SEES and HEARS Epic+ reveal auras — their own
    // and other people's. The effect is sent per-viewer, so switching it
    // off only quiets it for them.
    private boolean revealAuraEnabled = true;
    // Multiplies Tokens earned from harvesting farm crops. 1.0 = base
    // reward. Nothing raises this yet — reserved for future farming
    // upgrades (hoe enchants, prestige tie-in, etc.).
    private double farmTokenMultiplier = 1.0;
    // Milestone tiers already awarded, keyed "track:index" — the ledger
    // that stops a tier paying out twice.
    private final Set<String> claimedMilestones = new HashSet<>();
    // Lifetime farm crops harvested. The only milestone track that needed
    // its own counter; the rest are derived from existing state.
    private long cropsHarvested = 0L;
    // The crop this player sees on the shared farm, and which ones they're
    // allowed to pick. Everyone starts on wheat.
    private String selectedCrop = "WHEAT";
    private final Set<String> unlockedCrops = new HashSet<>();
    // Whether farm crops pay Shards as well as Tokens.
    private boolean cropShardsUnlocked = false;
    // Nova Core ladder (/rngcookie): the tier currently held, and the
    // deepest ever reached. A failed climb drops the first back to a
    // checkpoint; the second is a record and never falls.
    private int novaTier = 0;
    private int novaBestTier = 0;
    // Which Starforge the player owns — their BASE Luck comes from this.
    private String starforgeTier = "BASIC";
    // Base Luck from the Starforge, but only while it's actually in a
    // hand. Recomputed live like armorLuckBonus, not persisted.
    private double starforgeLuckBonus = 0.0;

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

    public Map<Rarity, Long> getDropBank() {
        return dropBank;
    }

    public long getBankedDrops(Rarity rarity) {
        return dropBank.getOrDefault(rarity, 0L);
    }

    public void addBankedDrops(Rarity rarity, long amount) {
        if (amount <= 0) return;
        dropBank.merge(rarity, amount, Long::sum);
    }

    /**
     * Spends up to {@code amount} banked drops of a rarity, returning how
     * many were actually taken — the caller covers any shortfall from the
     * player's physical inventory.
     */
    public long takeBankedDrops(Rarity rarity, long amount) {
        long have = getBankedDrops(rarity);
        long taken = Math.min(have, amount);
        if (taken <= 0) return 0L;
        dropBank.put(rarity, have - taken);
        return taken;
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
     * Total Luck actually applied to rolls. Each source has a distinct
     * role: the held Starforge sets the base, the skill tree and worn
     * armor add flat bonuses on top, then the equipped tag's index
     * multiplier and prestige both scale the whole total.
     *
     * indexMultiplier is passed in rather than stored because it's read
     * off the equipped tag's item, which lives in RarityManager.
     */
    public double getEffectiveLuck(double prestigeLuckMultiplierPerPrestige, double indexMultiplier) {
        double base = starforgeLuckBonus + bonusLuck + armorLuckBonus;
        return base * indexMultiplier * (1.0 + prestige * prestigeLuckMultiplierPerPrestige);
    }

    public Set<String> getPurchasedArmorTiers() {
        return purchasedArmorTiers;
    }

    public boolean hasPurchasedArmor(String tierId, ArmorPiece piece) {
        return purchasedArmorTiers.contains(ArmorPiece.key(tierId, piece));
    }

    public void markArmorPurchased(String tierId, ArmorPiece piece) {
        purchasedArmorTiers.add(ArmorPiece.key(tierId, piece));
    }

    public Set<String> getClaimedMilestones() {
        return claimedMilestones;
    }

    public boolean hasClaimedMilestone(String key) {
        return claimedMilestones.contains(key);
    }

    public void markMilestoneClaimed(String key) {
        claimedMilestones.add(key);
    }

    public long getCropsHarvested() {
        return cropsHarvested;
    }

    public void addCropsHarvested(long amount) {
        this.cropsHarvested += amount;
    }

    public void setCropsHarvested(long cropsHarvested) {
        this.cropsHarvested = cropsHarvested;
    }

    public String getSelectedCrop() {
        return selectedCrop;
    }

    public void setSelectedCrop(String selectedCrop) {
        this.selectedCrop = selectedCrop;
    }

    public Set<String> getUnlockedCrops() {
        return unlockedCrops;
    }

    public boolean hasUnlockedCrop(String cropId) {
        return unlockedCrops.contains(cropId);
    }

    public boolean isCropShardsUnlocked() {
        return cropShardsUnlocked;
    }

    public void setCropShardsUnlocked(boolean cropShardsUnlocked) {
        this.cropShardsUnlocked = cropShardsUnlocked;
    }

    public int getNovaTier() {
        return novaTier;
    }

    public void setNovaTier(int novaTier) {
        this.novaTier = Math.max(0, novaTier);
    }

    public int getNovaBestTier() {
        return novaBestTier;
    }

    public void setNovaBestTier(int novaBestTier) {
        this.novaBestTier = Math.max(0, novaBestTier);
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

    public boolean isRevealAuraEnabled() {
        return revealAuraEnabled;
    }

    public void setRevealAuraEnabled(boolean revealAuraEnabled) {
        this.revealAuraEnabled = revealAuraEnabled;
    }

    public double getFarmTokenMultiplier() {
        return farmTokenMultiplier;
    }

    public void setFarmTokenMultiplier(double farmTokenMultiplier) {
        this.farmTokenMultiplier = Math.max(0.1, farmTokenMultiplier);
    }

    public String getStarforgeTier() {
        return starforgeTier;
    }

    public void setStarforgeTier(String starforgeTier) {
        this.starforgeTier = starforgeTier;
    }

    public double getStarforgeLuckBonus() {
        return starforgeLuckBonus;
    }

    public void setStarforgeLuckBonus(double starforgeLuckBonus) {
        this.starforgeLuckBonus = starforgeLuckBonus;
    }
}
