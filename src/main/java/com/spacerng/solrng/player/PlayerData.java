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
    // appear here - they live in unlockedNodes instead.
    private final Map<String, Integer> nodeLevels = new HashMap<>();
    // Item display names (e.g. "Fallen Star") the player has ever rolled -
    // backs /index and its per-discovery luck bonus.
    private final Set<String> discoveredItems = new HashSet<>();
    private final Set<Rarity> autoConvertRarities = EnumSet.noneOf(Rarity.class);
    // Shinies are held apart from ordinary drops at every level: their own
    // discovery set, their own bank, and their own auto-convert switch.
    // Folding them into the normal ones would mean a 1-in-100 find could be
    // eaten by a toggle set for the common version of the same drop.
    private final Set<String> discoveredShiny = new HashSet<>();
    private final Map<Rarity, Long> shinyBank = new EnumMap<>(Rarity.class);
    private boolean autoConvertShiny = false;
    // Rarities whose reveal aura this player has switched off. Stored as
    // the exceptions so a new rarity is visible by default.
    private final Set<Rarity> disabledAuras = EnumSet.noneOf(Rarity.class);
    // Rarities whose global announcement this player has switched off.
    // Their own drops are never muted - this is for everyone else's.
    private final Set<Rarity> mutedBroadcasts = EnumSet.noneOf(Rarity.class);
    // Rarities whose OWN drop line this player has switched off. Separate
    // from mutedBroadcasts: somebody auto-rolling thousands of Commons
    // wants their own spam gone without losing everyone else's Divines.
    private final Set<Rarity> mutedDrops = EnumSet.noneOf(Rarity.class);
    private int hoeTier;
    private int freeSkills;
    private double starforgeSpeedBonus;
    private long abilityReadyAt;

    // Coin Factory pays back the last minute of Coin income, so a minute
    // of it has to be remembered. Sixty one-second buckets rather than a
    // list of every payout: a fast farmer harvests several times a second
    // and the memory has to be bounded by the WINDOW, not by their speed.
    private final long[] coinBuckets = new long[60];
    private long coinBucketSecond;
    private int coinBucketIndex;
    // Auto-roll always fires at the player's own current roll speed - no
    // separate fixed interval.
    private boolean autoRollEnabled = false;
    private String equippedTagItemKey = null; // e.g. "Fallen Star"
    private String equippedTagRarity = null;  // stored so we can re-color it on load
    // Chance (0.0-1.0) of an extra free roll right after any roll finishes -
    // granted by the Bonus Roll skill tree branch.
    private double bonusRollChance = 0.0;
    // Virtual drop bank: /convert turns physical rolled items into stored
    // drops of the same rarity instead of Credits, and /armor and
    // /starforge spend from here once the player's inventory runs out.
    // Credits stay reserved for the paid store.
    private final Map<Rarity, Long> dropBank = new EnumMap<>(Rarity.class);
    // Lifetime count of Common/Uncommon items converted via /convert or
    // auto-convert - shown on the skill tree screen alongside what's
    // currently sitting unconverted in the player's inventory.
    private long convertedCommon = 0L;
    private long convertedUncommon = 0L;
    // Lifetime roll count - levels up off of this via /prestige.
    private long totalRolls = 0L;
    private int level = 1;
    private int prestige = 0;
    // Flat Luck bonus from currently-worn /armor - recomputed live each
    // tick from equipped armor, not persisted.
    private double armorLuckBonus = 0.0;
    // Flat Speed bonus from currently-worn /armor - same live recompute
    // as armorLuckBonus, not persisted.
    private double armorSpeedBonus = 0.0;
    // /armor pieces ever bought, keyed "TIER:PIECE" (e.g.
    // "LEATHER:BOOTS") - pieces are sold individually. Worn status is
    // checked separately for whether the Luck bonus applies.
    private final Set<String> purchasedArmorTiers = new HashSet<>();
    // /options toggles.
    private boolean rollSoundEnabled = true;
    private boolean rollAnimationEnabled = true;
    // Farming's own two, toggled from the hoe menu rather than /options -
    // they belong next to the thing that makes the noise.
    private boolean farmSoundEnabled = true;
    private boolean enchantSoundEnabled = true;
    // Timed boosts from potions, keyed by effect: {multiplier, expiry millis}.
    // One entry per effect rather than a list, so drinking a second potion
    // of the same kind extends or upgrades what's running instead of
    // stacking into something absurd.
    private final Map<String, double[]> boosts = new HashMap<>();
    // Banked rolls that fire at a multiplied Luck - the "10x Roll" reward.
    private long rollCharges = 0L;
    private double rollChargeMultiplier = 1.0;
    // The draught currently running: flat Luck and Speed, and how many
    // rolls are left of it. One at a time on purpose - drinking a second
    // replaces the first, so two can never be stacked into something the
    // numbers were never balanced for.
    private double potionLuck = 0.0;
    private double potionSpeed = 0.0;
    private long potionRolls = 0L;
    // Multiplies Tokens earned from harvesting farm crops. 1.0 = base
    // reward. Nothing raises this yet - reserved for future farming
    // upgrades (hoe enchants, prestige tie-in, etc.).
    private double farmTokenMultiplier = 1.0;
    // Milestone tiers already awarded, keyed "track:index" - the ledger
    // that stops a tier paying out twice.
    private final Set<String> claimedMilestones = new HashSet<>();
    // Tiers the player has been TOLD about. Separate from claimed so the
    // "you reached it" message fires once while the reward waits in
    // /milestones to be collected by hand.
    private final Set<String> announcedMilestones = new HashSet<>();
    // Starting-guide steps already cleared. The guide is linear, so this is
    // effectively "how far in am I", but keyed by id so the order can be
    // changed later without resetting anyone.
    private final Set<String> completedQuests = new HashSet<>();
    // Daily streak: the run so far, and the epoch day it was last claimed.
    // A calendar day, not a rolling 24h window - see DailyManager.
    private int dailyStreak = 0;
    private long dailyLastClaimDay = 0L;
    private long dailyTotalClaims = 0L;
    // Prestige Points and what they've been spent on. Points are the part
    // of prestige the player chooses how to use.
    private int prestigePoints = 0;
    private final Map<String, Integer> prestigeUpgrades = new HashMap<>();
    // Lifetime farm crops harvested. The only milestone track that needed
    // its own counter; the rest are derived from existing state.
    private long cropsHarvested = 0L;
    // What's been farmed since the last payout. Separate from the lifetime
    // count because a payout has to be about what you did TODAY, while the
    // all-time number is the one worth bragging about.
    private long cropsThisPeriod = 0L;
    // The crop this player sees on the shared farm, and which ones they're
    // allowed to pick. Everyone starts on wheat.
    private String selectedCrop = "WHEAT";
    private final Set<String> unlockedCrops = new HashSet<>();
    // Whether farm crops pay Shards as well as Tokens.
    private boolean cropShardsUnlocked = false;
    // Hoe enchant levels bought with Tokens. The farming tree gates which
    // enchants count at all, so a level here is inert until it's unlocked.
    private final Map<String, Integer> hoeEnchantLevels = new HashMap<>();
    // Nova Core ladder (/rngcookie): the tier currently held, and the
    // deepest ever reached. A failed climb drops the first back to a
    // checkpoint; the second is a record and never falls.
    private int novaTier = 0;
    private int novaBestTier = 0;
    // Which Starforge the player owns - their BASE Luck comes from this.
    // Blank, not "BASIC". A fresh account starts on whatever the config
    // calls the first tier, and hardcoding one here is what made a reset
    // hand back a Basic Starforge somebody had not earned.
    private String starforgeTier = "";
    // Base Luck from the Starforge, but only while it's actually in a
    // hand. Recomputed live like armorLuckBonus, not persisted.
    private double starforgeLuckBonus = 0.0;
    // Speed granted by skill tree nodes. Derived from node levels rather
    // than added on purchase, so retuning a Speed skill in config retunes
    // it for everyone who already owns it. Refreshed on a timer, not saved.
    private double skillSpeedBonus = 0.0;
    // Battle Pass (/pass). The season number is stored so that starting a
    // new season resets XP and claims without touching anything else.
    private int passSeason = 1;
    private long passXp = 0L;
    private boolean passPremium = false;
    // Rewards already taken, keyed "F:12" / "P:12" for the free and
    // premium track of level 12.
    private final Set<String> passClaimed = new HashSet<>();

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

    public double getSkillSpeedBonus() {
        return skillSpeedBonus;
    }

    public void setSkillSpeedBonus(double skillSpeedBonus) {
        this.skillSpeedBonus = Math.max(0.0, skillSpeedBonus);
    }

    /**
     * Total roll speed actually applied: the 1.0 baseline, plus every
     * Speed node bought, plus worn armor. 1.0 = the "100 Speed" baseline
     * shown on the scoreboard (Speed = this x 100, rounded).
     */
    public double getEffectiveRollSpeedMultiplier() {
        // A draught's Speed joins the flat pile rather than multiplying it,
        // which is what lets a potion carry a MINUS without wiping somebody
        // out - a 0.75x multiplier on a maxed player is brutal, -25 flat is
        // a trade.
        // The flat pile first, then anything multiplying it. An ability
        // like Overcharge is a multiplier on purpose: doubling a maxed
        // player's Speed has to stay worth something.
        return Math.max(0.1, (rollSpeedMultiplier + skillSpeedBonus + armorSpeedBonus
                + starforgeSpeedBonus + getPotionSpeed()) * boostMultiplier("SPEED"));
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
     * many were actually taken - the caller covers any shortfall from the
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
     * Tracks a conversion for the skill tree's Common/Uncommon summary -
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
     * The flat Luck that isn't tied to a skill node - admin grants, and
     * anything a future system hands out directly.
     *
     * The full Luck calculation deliberately does NOT live here: it needs
     * the skill tree, the index, prestige upgrades and the global boost,
     * none of which a plain data object should know about. PrestigeManager
     * assembles it instead, and is the single place to read Luck from.
     */
    public double getFlatLuck() {
        return bonusLuck;
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

    public Set<String> getAnnouncedMilestones() {
        return announcedMilestones;
    }

    public boolean hasAnnouncedMilestone(String key) {
        return announcedMilestones.contains(key);
    }

    public void markMilestoneAnnounced(String key) {
        announcedMilestones.add(key);
    }

    public Set<String> getCompletedQuests() {
        return completedQuests;
    }

    public boolean hasCompletedQuest(String id) {
        return completedQuests.contains(id);
    }

    public void markQuestCompleted(String id) {
        completedQuests.add(id);
    }

    public int getDailyStreak() {
        return dailyStreak;
    }

    public void setDailyStreak(int dailyStreak) {
        this.dailyStreak = Math.max(0, dailyStreak);
    }

    public long getDailyLastClaimDay() {
        return dailyLastClaimDay;
    }

    public void setDailyLastClaimDay(long dailyLastClaimDay) {
        this.dailyLastClaimDay = dailyLastClaimDay;
    }

    public long getDailyTotalClaims() {
        return dailyTotalClaims;
    }

    public void addDailyTotalClaims(long amount) {
        this.dailyTotalClaims += amount;
    }

    public void setDailyTotalClaims(long dailyTotalClaims) {
        this.dailyTotalClaims = dailyTotalClaims;
    }

    public int getPassSeason() {
        return passSeason;
    }

    public void setPassSeason(int passSeason) {
        this.passSeason = Math.max(1, passSeason);
    }

    public long getPassXp() {
        return passXp;
    }

    public void setPassXp(long passXp) {
        this.passXp = Math.max(0L, passXp);
    }

    public void addPassXp(long amount) {
        if (amount <= 0) return;
        this.passXp += amount;
    }

    public boolean isPassPremium() {
        return passPremium;
    }

    public void setPassPremium(boolean passPremium) {
        this.passPremium = passPremium;
    }

    public Set<String> getPassClaimed() {
        return passClaimed;
    }

    /** track is "F" for the free row or "P" for the premium one. */
    public boolean hasClaimedPass(String track, int level) {
        return passClaimed.contains(track + ":" + level);
    }

    public void markPassClaimed(String track, int level) {
        passClaimed.add(track + ":" + level);
    }

    /**
     * Wipes everything season-scoped. The season number itself is set by
     * the caller, so a reset is one call rather than a checklist.
     */
    public void resetPass() {
        passXp = 0L;
        passPremium = false;
        passClaimed.clear();
    }

    public int getPrestigePoints() {
        return prestigePoints;
    }

    public void setPrestigePoints(int prestigePoints) {
        this.prestigePoints = Math.max(0, prestigePoints);
    }

    public void addPrestigePoints(int amount) {
        this.prestigePoints = Math.max(0, this.prestigePoints + amount);
    }

    public boolean spendPrestigePoints(int amount) {
        if (prestigePoints < amount) return false;
        prestigePoints -= amount;
        return true;
    }

    public Map<String, Integer> getPrestigeUpgrades() {
        return prestigeUpgrades;
    }

    public int getUpgradeLevel(String id) {
        return prestigeUpgrades.getOrDefault(id, 0);
    }

    public void setUpgradeLevel(String id, int level) {
        prestigeUpgrades.put(id, Math.max(0, level));
    }

    public long getCropsHarvested() {
        return cropsHarvested;
    }

    public void addCropsHarvested(long amount) {
        this.cropsHarvested += amount;
        this.cropsThisPeriod += amount;
    }

    public long getCropsThisPeriod() {
        return cropsThisPeriod;
    }

    public void setCropsThisPeriod(long cropsThisPeriod) {
        this.cropsThisPeriod = Math.max(0L, cropsThisPeriod);
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

    public Map<String, Integer> getHoeEnchantLevels() {
        return hoeEnchantLevels;
    }

    public int getHoeEnchantLevel(String id) {
        return hoeEnchantLevels.getOrDefault(id, 0);
    }

    public void setHoeEnchantLevel(String id, int level) {
        hoeEnchantLevels.put(id, Math.max(0, level));
    }

    public Set<String> getDiscoveredShiny() {
        return discoveredShiny;
    }

    public boolean hasDiscoveredShiny(String itemDisplayName) {
        return discoveredShiny.contains(itemDisplayName);
    }

    public void markShinyDiscovered(String itemDisplayName) {
        discoveredShiny.add(itemDisplayName);
    }

    public Map<Rarity, Long> getShinyBank() {
        return shinyBank;
    }

    public long getBankedShiny(Rarity rarity) {
        return shinyBank.getOrDefault(rarity, 0L);
    }

    public void addBankedShiny(Rarity rarity, long amount) {
        if (amount <= 0) return;
        shinyBank.merge(rarity, amount, Long::sum);
    }

    public long takeBankedShiny(Rarity rarity, long amount) {
        long have = getBankedShiny(rarity);
        long taken = Math.min(have, amount);
        if (taken <= 0) return 0L;
        shinyBank.put(rarity, have - taken);
        return taken;
    }

    public boolean isAutoConvertShiny() {
        return autoConvertShiny;
    }

    public void setAutoConvertShiny(boolean autoConvertShiny) {
        this.autoConvertShiny = autoConvertShiny;
    }

    public boolean isRollSoundEnabled() {
        return rollSoundEnabled;
    }

    public void setRollSoundEnabled(boolean rollSoundEnabled) {
        this.rollSoundEnabled = rollSoundEnabled;
    }

    public boolean isFarmSoundEnabled() {
        return farmSoundEnabled;
    }

    public void setFarmSoundEnabled(boolean farmSoundEnabled) {
        this.farmSoundEnabled = farmSoundEnabled;
    }

    public boolean isEnchantSoundEnabled() {
        return enchantSoundEnabled;
    }

    public void setEnchantSoundEnabled(boolean enchantSoundEnabled) {
        this.enchantSoundEnabled = enchantSoundEnabled;
    }

    // ------------------------------------------------------------- boosts

    public Map<String, double[]> getBoosts() {
        return boosts;
    }

    /**
     * The live multiplier for one boost, or 1.0 when nothing is running.
     * Expiry is checked on read rather than swept on a timer: a boost
     * nobody is reading doesn't need to have ended yet.
     */
    public double boostMultiplier(String effect) {
        double[] state = boosts.get(effect);
        if (state == null) return 1.0;
        if (System.currentTimeMillis() >= state[1]) {
            boosts.remove(effect);
            return 1.0;
        }
        return state[0];
    }

    public long boostRemainingMillis(String effect) {
        double[] state = boosts.get(effect);
        if (state == null) return 0L;
        return Math.max(0L, (long) state[1] - System.currentTimeMillis());
    }

    /**
     * Starts or refreshes a boost. A stronger one replaces a weaker one
     * outright; an equal one adds its time on. Neither case can multiply
     * two potions together, which is the failure mode worth designing out.
     */
    public void applyBoost(String effect, double multiplier, long durationMillis) {
        double[] state = boosts.get(effect);
        long now = System.currentTimeMillis();
        if (state == null || now >= state[1] || multiplier > state[0]) {
            boosts.put(effect, new double[]{multiplier, now + durationMillis});
            return;
        }
        state[1] += durationMillis;
    }

    public void setBoost(String effect, double multiplier, long expiryMillis) {
        if (multiplier <= 1.0 || expiryMillis <= System.currentTimeMillis()) return;
        boosts.put(effect, new double[]{multiplier, expiryMillis});
    }

    // ------------------------------------------------------------ draughts

    public double getPotionLuck() {
        return potionRolls > 0 ? potionLuck : 0.0;
    }

    public double getPotionSpeed() {
        return potionRolls > 0 ? potionSpeed : 0.0;
    }

    public long getPotionRolls() {
        return Math.max(0L, potionRolls);
    }

    public void setPotion(double luck, double speed, long rolls) {
        this.potionLuck = luck;
        this.potionSpeed = speed;
        this.potionRolls = Math.max(0L, rolls);
    }

    /** Spends one roll of the draught, clearing it when it runs out. */
    public boolean tickPotion() {
        if (potionRolls <= 0) return false;
        potionRolls--;
        if (potionRolls <= 0) {
            potionLuck = 0.0;
            potionSpeed = 0.0;
            potionRolls = 0L;
            return true;
        }
        return false;
    }

    public long getRollCharges() {
        return rollCharges;
    }

    public double getRollChargeMultiplier() {
        return rollChargeMultiplier;
    }

    public void setRollCharges(long charges, double multiplier) {
        this.rollCharges = Math.max(0L, charges);
        this.rollChargeMultiplier = Math.max(1.0, multiplier);
    }

    /** A stronger charge replaces a weaker one rather than averaging with it. */
    public void addRollCharges(long charges, double multiplier) {
        if (charges <= 0) return;
        if (multiplier > rollChargeMultiplier || rollCharges <= 0) {
            rollChargeMultiplier = Math.max(1.0, multiplier);
            rollCharges = charges;
            return;
        }
        rollCharges += charges;
    }

    /** Spends one charge, returning the multiplier it was worth (1.0 if none). */
    public double consumeRollCharge() {
        if (rollCharges <= 0) return 1.0;
        rollCharges--;
        double multiplier = rollChargeMultiplier;
        if (rollCharges <= 0) rollChargeMultiplier = 1.0;
        return multiplier;
    }

    public boolean isRollAnimationEnabled() {
        return rollAnimationEnabled;
    }

    public void setRollAnimationEnabled(boolean rollAnimationEnabled) {
        this.rollAnimationEnabled = rollAnimationEnabled;
    }

    /**
     * Whether this player sees the reveal aura for a given rarity. Split
     * per tier because the tiers are wildly different events: a Mythical
     * once a month is a spectacle, an Epic several times an hour can be a
     * nuisance, and one switch can't express that.
     */
    public boolean isAuraEnabled(Rarity rarity) {
        return rarity != null && !disabledAuras.contains(rarity);
    }

    public void setAuraEnabled(Rarity rarity, boolean enabled) {
        if (rarity == null) return;
        if (enabled) {
            disabledAuras.remove(rarity);
        } else {
            disabledAuras.add(rarity);
        }
    }

    public Set<Rarity> getDisabledAuras() {
        return disabledAuras;
    }

    /** Whether this player wants to see other people's drops at a rarity. */
    public boolean isBroadcastEnabled(Rarity rarity) {
        return rarity != null && !mutedBroadcasts.contains(rarity);
    }

    public void setBroadcastEnabled(Rarity rarity, boolean enabled) {
        if (rarity == null) return;
        if (enabled) {
            mutedBroadcasts.remove(rarity);
        } else {
            mutedBroadcasts.add(rarity);
        }
    }

    public Set<Rarity> getMutedBroadcasts() {
        return mutedBroadcasts;
    }

    /** Whether this player wants their own drop at a rarity printed. */
    public boolean isDropMessageEnabled(Rarity rarity) {
        return rarity == null || !mutedDrops.contains(rarity);
    }

    public void setDropMessageEnabled(Rarity rarity, boolean enabled) {
        if (rarity == null) return;
        if (enabled) {
            mutedDrops.remove(rarity);
        } else {
            mutedDrops.add(rarity);
        }
    }

    public Set<Rarity> getMutedDrops() {
        return mutedDrops;
    }

    /**
     * Skill nodes this player can buy for nothing.
     *
     * Spent automatically by the next purchase, whatever it costs, so the
     * reward is worth most to somebody who saves it for a node they could
     * not otherwise afford.
     */
    public int getFreeSkills() {
        return freeSkills;
    }

    public void addFreeSkills(int amount) {
        this.freeSkills = Math.max(0, this.freeSkills + amount);
    }

    public boolean useFreeSkill() {
        if (freeSkills <= 0) return false;
        freeSkills--;
        return true;
    }

    /** Hoe tiers bought with drops. Skill-node tiers are counted separately. */
    public int getHoeTier() {
        return hoeTier;
    }

    public void setHoeTier(int hoeTier) {
        this.hoeTier = Math.max(0, hoeTier);
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

    /** The held Starforge's Speed, which some tiers make negative. */
    public double getStarforgeSpeedBonus() {
        return starforgeSpeedBonus;
    }

    public void setStarforgeSpeedBonus(double starforgeSpeedBonus) {
        this.starforgeSpeedBonus = starforgeSpeedBonus;
    }

    /** Records Coins as they are earned, into the current second's bucket. */
    public void trackCoins(long amount) {
        long now = System.currentTimeMillis() / 1000L;
        if (coinBucketSecond == 0L) coinBucketSecond = now;
        long advance = now - coinBucketSecond;
        if (advance > 0L) {
            // Clearing at most sixty buckets: past a minute away, the whole
            // window is stale anyway.
            for (long i = 0; i < Math.min(advance, coinBuckets.length); i++) {
                coinBucketIndex = (coinBucketIndex + 1) % coinBuckets.length;
                coinBuckets[coinBucketIndex] = 0L;
            }
            coinBucketSecond = now;
        }
        coinBuckets[coinBucketIndex] += amount;
    }

    public long coinsInLastMinute() {
        trackCoins(0L); // roll the window forward before reading it
        long total = 0L;
        for (long bucket : coinBuckets) total += bucket;
        return total;
    }

    /** Epoch millis the Starforge ability can be fired again. */
    public long getAbilityReadyAt() {
        return abilityReadyAt;
    }

    public void setAbilityReadyAt(long abilityReadyAt) {
        this.abilityReadyAt = abilityReadyAt;
    }

    public double getStarforgeLuckBonus() {
        return starforgeLuckBonus;
    }

    public void setStarforgeLuckBonus(double starforgeLuckBonus) {
        this.starforgeLuckBonus = starforgeLuckBonus;
    }
}
