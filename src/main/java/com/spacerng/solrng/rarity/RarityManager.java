package com.spacerng.solrng.rarity;

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

    // Official Java Edition legacy palette (foreground), so "&6" etc. in
    // config resolve to the exact vanilla RGB instead of an approximation.
    private static final Map<Character, int[]> LEGACY_RGB = Map.ofEntries(
            Map.entry('0', new int[]{0, 0, 0}),
            Map.entry('1', new int[]{0, 0, 170}),
            Map.entry('2', new int[]{0, 170, 0}),
            Map.entry('3', new int[]{0, 170, 170}),
            Map.entry('4', new int[]{170, 0, 0}),
            Map.entry('5', new int[]{170, 0, 170}),
            Map.entry('6', new int[]{255, 170, 0}),
            Map.entry('7', new int[]{170, 170, 170}),
            Map.entry('8', new int[]{85, 85, 85}),
            Map.entry('9', new int[]{85, 85, 255}),
            Map.entry('a', new int[]{85, 255, 85}),
            Map.entry('b', new int[]{85, 255, 255}),
            Map.entry('c', new int[]{255, 85, 85}),
            Map.entry('d', new int[]{255, 85, 255}),
            Map.entry('e', new int[]{255, 255, 85}),
            Map.entry('f', new int[]{255, 255, 255})
    );

    private final List<RollableItem> items = new ArrayList<>();
    private final Map<Rarity, Double> luckFactors = new EnumMap<>(Rarity.class);
    private final Map<Rarity, RarityStyle> styles = new EnumMap<>(Rarity.class);
    private final Logger logger;

    public RarityManager(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        items.clear();
        luckFactors.clear();
        styles.clear();

        ConfigurationSection raritySection = config.getConfigurationSection("rarities");
        if (raritySection != null) {
            for (String key : raritySection.getKeys(false)) {
                Rarity rarity = safeRarity(key);
                if (rarity == null) continue;
                ConfigurationSection r = raritySection.getConfigurationSection(key);
                if (r == null) continue;

                double luckFactor = r.getDouble("luck-factor", 0.0);
                luckFactors.put(rarity, luckFactor);

                List<int[]> stops = new ArrayList<>();
                for (String colorStr : r.getStringList("colors")) {
                    int[] rgb = parseColor(colorStr);
                    if (rgb != null) stops.add(rgb);
                }
                if (stops.isEmpty()) stops.add(new int[]{255, 255, 255});

                boolean bold = r.getBoolean("bold", false);
                boolean underline = r.getBoolean("underline", false);
                boolean strikethrough = r.getBoolean("strikethrough", false);
                boolean obfuscatedPrefix = r.getBoolean("obfuscated-prefix", false);
                styles.put(rarity, new RarityStyle(stops, bold, underline, strikethrough, obfuscatedPrefix));
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

    /** Accepts "&6"-style legacy codes or "#RRGGBB" hex. Null if unparseable. */
    private int[] parseColor(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("#") && s.length() == 7) {
            try {
                return new int[]{
                        Integer.parseInt(s.substring(1, 3), 16),
                        Integer.parseInt(s.substring(3, 5), 16),
                        Integer.parseInt(s.substring(5, 7), 16)
                };
            } catch (NumberFormatException ex) {
                logger.warning("[SolRNG] Bad hex color in config: " + raw);
                return null;
            }
        }
        if ((s.startsWith("&") || s.startsWith("§")) && s.length() == 2) {
            int[] rgb = LEGACY_RGB.get(Character.toLowerCase(s.charAt(1)));
            if (rgb != null) return rgb;
        }
        logger.warning("[SolRNG] Unrecognized rarity color in config: " + raw);
        return null;
    }

    private Rarity safeRarity(String key) {
        try {
            return Rarity.valueOf(key.toUpperCase());
        } catch (IllegalArgumentException ex) {
            logger.warning("[SolRNG] Unknown rarity in config: " + key);
            return null;
        }
    }

    /** Applies the rarity's full style (color/gradient + formatting) to the given text. */
    public String style(Rarity rarity, String text) {
        RarityStyle style = styles.get(rarity);
        return style == null ? text : style.apply(text);
    }

    public double luckFactorFor(Rarity rarity) {
        return luckFactors.getOrDefault(rarity, 0.0);
    }

    public List<RollableItem> getItems() {
        return items;
    }

    /**
     * Looks up a rollable item by its display name — used to recover the
     * odds of a player's currently-equipped tag, which is only stored as
     * a name + rarity string in PlayerData.
     */
    public RollableItem findByDisplayName(String displayName) {
        for (RollableItem item : items) {
            if (item.getDisplayName().equals(displayName)) {
                return item;
            }
        }
        return null;
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
