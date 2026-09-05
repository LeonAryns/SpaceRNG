package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final SolRNGPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public PlayerDataManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    /**
     * Wipes a player back to a brand-new account: the cached object is
     * replaced and the save file deleted, so nothing can flush the old
     * state back over the top afterwards.
     */
    public PlayerData reset(UUID uuid) {
        cache.remove(uuid);
        File file = fileFor(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("[SolRNG] Couldn't delete player data file for " + uuid);
        }
        PlayerData fresh = new PlayerData(uuid);
        cache.put(uuid, fresh);
        return fresh;
    }

    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            save(data);
        }
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            save(data);
        }
    }

    private File fileFor(UUID uuid) {
        return new File(dataFolder, uuid.toString() + ".yml");
    }

    private PlayerData load(UUID uuid) {
        File file = fileFor(uuid);
        PlayerData data = new PlayerData(uuid);
        if (!file.exists()) {
            return data;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        data.addBonusLuck(yml.getDouble("luck", 0.0));
        data.addPoints(yml.getLong("points", 0L));
        data.addTokens(yml.getLong("tokens", 0L));
        data.addShards(yml.getLong("shards", 0L));
        data.setRollSpeedMultiplier(yml.getDouble("roll-speed-multiplier", 1.0));
        data.setAutoRollEnabled(yml.getBoolean("auto-roll-enabled", false));
        data.addBonusRollChance(yml.getDouble("bonus-roll-chance", 0.0));
        data.addConverted(Rarity.COMMON, yml.getLong("converted-common", 0L));
        data.addConverted(Rarity.UNCOMMON, yml.getLong("converted-uncommon", 0L));
        data.setTotalRolls(yml.getLong("total-rolls", 0L));
        data.setLevel(yml.getInt("level", 1));
        data.setPrestige(yml.getInt("prestige", 0));
        data.setRollSoundEnabled(yml.getBoolean("roll-sound-enabled", true));
        data.setRollAnimationEnabled(yml.getBoolean("roll-animation-enabled", true));
        data.setRevealAuraEnabled(yml.getBoolean("reveal-aura-enabled", true));
        data.setFarmTokenMultiplier(yml.getDouble("farm-token-multiplier", 1.0));
        data.setStarforgeTier(yml.getString("starforge-tier", "BASIC"));
        data.setCropsHarvested(yml.getLong("crops-harvested", 0L));
        data.setNovaTier(yml.getInt("nova-tier", 0));
        data.setNovaBestTier(yml.getInt("nova-best-tier", 0));
        data.setSelectedCrop(yml.getString("selected-crop", "WHEAT"));
        data.setCropShardsUnlocked(yml.getBoolean("crop-shards-unlocked", false));
        data.getUnlockedCrops().addAll(yml.getStringList("unlocked-crops"));
        data.getClaimedMilestones().addAll(yml.getStringList("claimed-milestones"));
        data.getAnnouncedMilestones().addAll(yml.getStringList("announced-milestones"));
        data.getCompletedQuests().addAll(yml.getStringList("completed-quests"));
        data.setDailyStreak(yml.getInt("daily-streak", 0));
        data.setDailyLastClaimDay(yml.getLong("daily-last-claim-day", 0L));
        data.setDailyTotalClaims(yml.getLong("daily-total-claims", 0L));
        data.setPrestigePoints(yml.getInt("prestige-points", 0));
        org.bukkit.configuration.ConfigurationSection upgrades = yml.getConfigurationSection("prestige-upgrades");
        if (upgrades != null) {
            for (String id : upgrades.getKeys(false)) {
                data.setUpgradeLevel(id, upgrades.getInt(id));
            }
        }

        org.bukkit.configuration.ConfigurationSection bank = yml.getConfigurationSection("drop-bank");
        if (bank != null) {
            for (String rarityName : bank.getKeys(false)) {
                try {
                    data.addBankedDrops(Rarity.valueOf(rarityName), bank.getLong(rarityName));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (String node : yml.getStringList("unlocked-nodes")) {
            data.getUnlockedNodes().add(node);
        }
        org.bukkit.configuration.ConfigurationSection nodeLevels = yml.getConfigurationSection("node-levels");
        if (nodeLevels != null) {
            for (String nodeId : nodeLevels.getKeys(false)) {
                data.setNodeLevel(nodeId, nodeLevels.getInt(nodeId));
            }
        }
        for (String itemName : yml.getStringList("discovered-items")) {
            data.getDiscoveredItems().add(itemName);
        }
        for (String rarityName : yml.getStringList("auto-convert-rarities")) {
            try {
                data.getAutoConvertRarities().add(Rarity.valueOf(rarityName));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String entry : yml.getStringList("purchased-armor-tiers")) {
            if (entry.contains(":")) {
                data.getPurchasedArmorTiers().add(entry);
                continue;
            }
            // Pre-V30 saves stored a bare tier id meaning "bought the whole
            // set" — anyone who owned a set keeps all four pieces.
            for (ArmorPiece piece : ArmorPiece.values()) {
                data.getPurchasedArmorTiers().add(ArmorPiece.key(entry, piece));
            }
        }

        String tagItem = yml.getString("tag-item", null);
        String tagRarity = yml.getString("tag-rarity", null);
        if (tagItem != null) {
            data.setEquippedTag(tagItem, tagRarity);
        }

        return data;
    }

    public void save(PlayerData data) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("luck", data.getBonusLuck());
        yml.set("points", data.getPoints());
        yml.set("tokens", data.getTokens());
        yml.set("shards", data.getShards());
        yml.set("roll-speed-multiplier", data.getRollSpeedMultiplier());
        yml.set("auto-roll-enabled", data.isAutoRollEnabled());
        yml.set("bonus-roll-chance", data.getBonusRollChance());
        yml.set("converted-common", data.getConvertedCommon());
        yml.set("converted-uncommon", data.getConvertedUncommon());
        yml.set("total-rolls", data.getTotalRolls());
        yml.set("level", data.getLevel());
        yml.set("prestige", data.getPrestige());
        yml.set("roll-sound-enabled", data.isRollSoundEnabled());
        yml.set("roll-animation-enabled", data.isRollAnimationEnabled());
        yml.set("reveal-aura-enabled", data.isRevealAuraEnabled());
        yml.set("farm-token-multiplier", data.getFarmTokenMultiplier());
        yml.set("starforge-tier", data.getStarforgeTier());
        yml.set("crops-harvested", data.getCropsHarvested());
        yml.set("nova-tier", data.getNovaTier());
        yml.set("nova-best-tier", data.getNovaBestTier());
        yml.set("selected-crop", data.getSelectedCrop());
        yml.set("crop-shards-unlocked", data.isCropShardsUnlocked());
        yml.set("unlocked-crops", new java.util.ArrayList<>(data.getUnlockedCrops()));
        yml.set("claimed-milestones", new java.util.ArrayList<>(data.getClaimedMilestones()));
        yml.set("announced-milestones", new java.util.ArrayList<>(data.getAnnouncedMilestones()));
        yml.set("completed-quests", new java.util.ArrayList<>(data.getCompletedQuests()));
        yml.set("daily-streak", data.getDailyStreak());
        yml.set("daily-last-claim-day", data.getDailyLastClaimDay());
        yml.set("daily-total-claims", data.getDailyTotalClaims());
        yml.set("prestige-points", data.getPrestigePoints());
        for (Map.Entry<String, Integer> entry : data.getPrestigeUpgrades().entrySet()) {
            yml.set("prestige-upgrades." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Rarity, Long> entry : data.getDropBank().entrySet()) {
            yml.set("drop-bank." + entry.getKey().name(), entry.getValue());
        }
        yml.set("unlocked-nodes", new java.util.ArrayList<>(data.getUnlockedNodes()));
        for (Map.Entry<String, Integer> entry : data.getNodeLevels().entrySet()) {
            yml.set("node-levels." + entry.getKey(), entry.getValue());
        }
        yml.set("discovered-items", new java.util.ArrayList<>(data.getDiscoveredItems()));

        java.util.List<String> rarityNames = new java.util.ArrayList<>();
        for (Rarity r : data.getAutoConvertRarities()) {
            rarityNames.add(r.name());
        }
        yml.set("auto-convert-rarities", rarityNames);
        yml.set("purchased-armor-tiers", new java.util.ArrayList<>(data.getPurchasedArmorTiers()));

        if (data.getEquippedTagItemKey() != null) {
            yml.set("tag-item", data.getEquippedTagItemKey());
            yml.set("tag-rarity", data.getEquippedTagRarity());
        }

        try {
            yml.save(fileFor(data.getUuid()));
        } catch (IOException e) {
            plugin.getLogger().warning("[SolRNG] Failed to save player data for " + data.getUuid() + ": " + e.getMessage());
        }
    }
}
