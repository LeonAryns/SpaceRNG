package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private double bonusLuck = 0.0;
    private long points = 0L;
    private final Set<String> unlockedNodes = new HashSet<>();
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

    public Set<String> getUnlockedNodes() {
        return unlockedNodes;
    }

    public boolean hasUnlocked(String nodeId) {
        return unlockedNodes.contains(nodeId);
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
