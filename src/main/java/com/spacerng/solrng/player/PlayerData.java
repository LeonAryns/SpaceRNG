package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private double bonusLuck = 0.0;
    private long points = 0L; // shown on the scoreboard as "Credits"
    private long tokens = 0L; // second scoreboard currency, reserved for future systems
    // Multiplies roll speed: 1.0 = base roll-duration-seconds, 2.0 = twice as fast.
    // Nothing grants bonuses to this yet — it's here so future skill tree /
    // armor upgrades have somewhere to write to.
    private double rollSpeedMultiplier = 1.0;
    private final Set<String> unlockedNodes = new HashSet<>();
    // Item display names (e.g. "Fallen Star") the player has ever rolled —
    // backs /index and its per-discovery luck bonus.
    private final Set<String> discoveredItems = new HashSet<>();
    private final Set<Rarity> autoConvertRarities = EnumSet.noneOf(Rarity.class);
    private int autoRollIntervalSeconds = 0; // 0 = disabled
    private String equippedTagItemKey = null; // e.g. "Fallen Star"
    private String equippedTagRarity = null;  // stored so we can re-color it on load

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

    public double getRollSpeedMultiplier() {
        return rollSpeedMultiplier;
    }

    public void setRollSpeedMultiplier(double rollSpeedMultiplier) {
        this.rollSpeedMultiplier = Math.max(0.1, rollSpeedMultiplier);
    }

    public Set<String> getUnlockedNodes() {
        return unlockedNodes;
    }

    public boolean hasUnlocked(String nodeId) {
        return unlockedNodes.contains(nodeId);
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

    public int getAutoRollIntervalSeconds() {
        return autoRollIntervalSeconds;
    }

    public void setAutoRollIntervalSeconds(int seconds) {
        this.autoRollIntervalSeconds = seconds;
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
}
