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
        data.setAutoRollIntervalSeconds(yml.getInt("auto-roll-interval", 0));

        for (String node : yml.getStringList("unlocked-nodes")) {
            data.getUnlockedNodes().add(node);
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
        yml.set("auto-roll-interval", data.getAutoRollIntervalSeconds());
        yml.set("unlocked-nodes", new java.util.ArrayList<>(data.getUnlockedNodes()));

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
