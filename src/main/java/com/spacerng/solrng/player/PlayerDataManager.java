package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
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
        data.setRollSpeedMultiplier(yml.getDouble("roll-speed-multiplier", 1.0));
        data.setAutoRollIntervalSeconds(yml.getInt("auto-roll-interval", 0));
        data.addBonusRollChance(yml.getDouble("bonus-roll-chance", 0.0));
        data.addConverted(Rarity.COMMON, yml.getLong("converted-common", 0L));
        data.addConverted(Rarity.UNCOMMON, yml.getLong("converted-uncommon", 0L));
        data.setTotalRolls(yml.getLong("total-rolls", 0L));
        data.setLevel(yml.getInt("level", 1));
        data.setPrestige(yml.getInt("prestige", 0));

        for (String node : yml.getStringList("unlocked-nodes")) {
            data.getUnlockedNodes().add(node);
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
        yml.set("roll-speed-multiplier", data.getRollSpeedMultiplier());
        yml.set("auto-roll-interval", data.getAutoRollIntervalSeconds());
        yml.set("bonus-roll-chance", data.getBonusRollChance());
        yml.set("converted-common", data.getConvertedCommon());
        yml.set("converted-uncommon", data.getConvertedUncommon());
        yml.set("total-rolls", data.getTotalRolls());
        yml.set("level", data.getLevel());
        yml.set("prestige", data.getPrestige());
        yml.set("unlocked-nodes", new java.util.ArrayList<>(data.getUnlockedNodes()));
        yml.set("discovered-items", new java.util.ArrayList<>(data.getDiscoveredItems()));

        java.util.List<String> rarityNames = new java.util.ArrayList<>();
        for (Rarity r : data.getAutoConvertRarities()) {
            rarityNames.add(r.name());
        }
        yml.set("auto-convert-rarities", rarityNames);

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
