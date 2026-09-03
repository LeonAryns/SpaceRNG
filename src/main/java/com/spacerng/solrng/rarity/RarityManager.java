package com.spacerng.solrng.rarity;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class RarityManager {

    private final List<RollableItem> items = new ArrayList<>();
    private final Map<Rarity, Double> luckFactors = new EnumMap<>(Rarity.class);
    private final Map<Rarity, String> colors = new EnumMap<>(Rarity.class);
    private final Logger logger;

    public RarityManager(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        items.clear();
        luckFactors.clear();
        colors.clear();

        ConfigurationSection raritySection = config.getConfigurationSection("rarities");
        if (raritySection != null) {
            for (String key : raritySection.getKeys(false)) {
                Rarity rarity = safeRarity(key);
                if (rarity == null) continue;
                ConfigurationSection r = raritySection.getConfigurationSection(key);
                if (r == null) continue;
                double luckFactor = r.getDouble("luck-factor", 0.0);
                String color = ChatColor.translateAlternateColorCodes('&', r.getString("color", "&f"));
                luckFactors.put(rarity, luckFactor);
                colors.put(rarity, color);
            }
        }

        List<Map<?, ?>> itemList = config.getMapList("items");
        for (Map<?, ?> raw : itemList) {
            try {
                Material material = Material.valueOf(String.valueOf(raw.get("material")).toUpperCase());
                String name = String.valueOf(raw.get("name"));
                Rarity rarity = safeRarity(String.valueOf(raw.get("rarity")));
                long odds = Long.parseLong(String.valueOf(raw.get("odds")));
                if (rarity == null) continue;
                items.add(new RollableItem(material, name, rarity, odds));
            } catch (Exception ex) {
                logger.warning("[SolRNG] Skipped a malformed item entry in config.yml: " + raw);
            }
        }

        logger.info("[SolRNG] Loaded " + items.size() + " rollable items across " + luckFactors.size() + " rarities.");
    }

    private Rarity safeRarity(String key) {
        try {
            return Rarity.valueOf(key.toUpperCase());
        } catch (IllegalArgumentException ex) {
            logger.warning("[SolRNG] Unknown rarity in config: " + key);
            return null;
        }
    }

    public String colorFor(Rarity rarity) {
        return colors.getOrDefault(rarity, "&f");
    }

    public double luckFactorFor(Rarity rarity) {
        return luckFactors.getOrDefault(rarity, 0.0);
    }

    public List<RollableItem> getItems() {
        return items;
    }

    /**
     * Weighted random roll. luck is the player's total luck stat
     * (0.0 = no bonus). Each item's effective weight is scaled up
     * based on its rarity's luck-factor, so higher luck disproportionately
     * favors rarer tiers without needing to touch common items directly.
     */
    public RollableItem roll(double luck) {
        if (items.isEmpty()) {
            throw new IllegalStateException("No rollable items configured — check config.yml");
        }

        double totalWeight = 0.0;
        double[] effectiveWeights = new double[items.size()];

        for (int i = 0; i < items.size(); i++) {
            RollableItem item = items.get(i);
            double factor = 1.0 + (luck * luckFactorFor(item.getRarity()));
            double weight = item.getBaseWeight() * factor;
            effectiveWeights[i] = weight;
            totalWeight += weight;
        }

        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (int i = 0; i < items.size(); i++) {
            cumulative += effectiveWeights[i];
            if (roll <= cumulative) {
                return items.get(i);
            }
        }
        return items.get(items.size() - 1);
    }
}
