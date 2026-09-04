package com.spacerng.solrng.rarity;

import net.md_5.bungee.api.ChatColor;
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
    private final Map<String, RollableItem> byName = new java.util.HashMap<>();
    private final Map<Rarity, Double> luckFactors = new EnumMap<>(Rarity.class);
    private final Map<Rarity, RarityStyle> styles = new EnumMap<>(Rarity.class);
    // Rarities flagged with "symbol: true" wrap item names in an
    // obfuscated flair character on BOTH sides (Epic and up).
    private final Map<Rarity, Boolean> symbolFlair = new EnumMap<>(Rarity.class);
    private final Logger logger;

    public RarityManager(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        items.clear();
        luckFactors.clear();
        styles.clear();
        symbolFlair.clear();

        ConfigurationSection raritySection = config.getConfigurationSection("rarities");
        if (raritySection != null) {
            for (String key : raritySection.getKeys(false)) {
                Rarity rarity = safeRarity(key);
                if (rarity == null) continue;
                ConfigurationSection r = raritySection.getConfigurationSection(key);
                if (r == null) continue;

                luckFactors.put(rarity, r.getDouble("luck-factor", 0.0));
                styles.put(rarity, parseStyle(r.getStringList("colors"),
                        r.getBoolean("bold", false),
                        r.getBoolean("underline", false),
                        r.getBoolean("strikethrough", false)));
                symbolFlair.put(rarity, r.getBoolean("symbol", false));
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
                items.add(new RollableItem(material, name, rarity, odds, parseItemStyle(raw)));
            } catch (Exception ex) {
                logger.warning("[SolRNG] Skipped a malformed item entry in config.yml: " + raw);
            }
        }

        assignLuckMultipliers(config);

        byName.clear();
        for (RollableItem item : items) {
            byName.put(item.getDisplayName(), item);
        }

        logger.info("[SolRNG] Loaded " + items.size() + " rollable items across " + luckFactors.size() + " rarities.");
    }

    /**
     * Gives every item its index Luck multiplier, scaled across its
     * rarity's configured band by how rare it is WITHIN that rarity — the
     * longest-odds item in a tier lands on the band's ceiling, the
     * shortest-odds one on its floor, everything else linearly between.
     * Derived rather than hand-written so the 143-item table stays
     * maintainable and self-balancing when odds change.
     */
    private void assignLuckMultipliers(FileConfiguration config) {
        for (Rarity rarity : Rarity.values()) {
            List<Double> band = config.getDoubleList("index.luck-multipliers." + rarity.name());
            double low = band.size() > 0 ? band.get(0) : 1.0;
            double high = band.size() > 1 ? band.get(1) : low;

            List<RollableItem> tier = new ArrayList<>();
            for (RollableItem item : items) {
                if (item.getRarity() == rarity) tier.add(item);
            }
            if (tier.isEmpty()) continue;

            long minOdds = Long.MAX_VALUE;
            long maxOdds = Long.MIN_VALUE;
            for (RollableItem item : tier) {
                minOdds = Math.min(minOdds, item.getOdds());
                maxOdds = Math.max(maxOdds, item.getOdds());
            }

            for (RollableItem item : tier) {
                double t = maxOdds == minOdds
                        ? 1.0
                        : (double) (item.getOdds() - minOdds) / (maxOdds - minOdds);
                item.setLuckMultiplier(low + t * (high - low));
            }
        }
    }

    /** O(1) lookup used when recomputing a player's index multiplier. */
    public RollableItem byName(String displayName) {
        return byName.get(displayName);
    }

    /**
     * A player's total index multiplier: 1 + the sum of every discovered
     * item's (multiplier - 1). Summing the bonuses rather than multiplying
     * them together keeps a full index worth a big number instead of an
     * astronomical one — chaining 143 multiplications would run to many
     * orders of magnitude.
     */
    public double indexMultiplierFor(java.util.Collection<String> discoveredNames) {
        double bonus = 0.0;
        for (String name : discoveredNames) {
            RollableItem item = byName.get(name);
            if (item != null) {
                bonus += item.getLuckMultiplier() - 1.0;
            }
        }
        return 1.0 + bonus;
    }

    /** An item's own "colors"/bold/underline/strikethrough, or null if it doesn't define any. */
    private RarityStyle parseItemStyle(Map<?, ?> raw) {
        Object colorsRaw = raw.get("colors");
        if (!(colorsRaw instanceof List<?> list) || list.isEmpty()) return null;

        List<String> colors = new ArrayList<>();
        for (Object o : list) colors.add(String.valueOf(o));
        return parseStyle(colors,
                Boolean.TRUE.equals(raw.get("bold")),
                Boolean.TRUE.equals(raw.get("underline")),
                Boolean.TRUE.equals(raw.get("strikethrough")));
    }

    private RarityStyle parseStyle(List<String> colors, boolean bold, boolean underline, boolean strikethrough) {
        List<int[]> stops = new ArrayList<>();
        for (String colorStr : colors) {
            int[] rgb = parseColor(colorStr);
            if (rgb != null) stops.add(rgb);
        }
        if (stops.isEmpty()) stops.add(new int[]{255, 255, 255});
        return new RarityStyle(stops, bold, underline, strikethrough);
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
        logger.warning("[SolRNG] Unrecognized color in config: " + raw);
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

    /** The rarity's plain label color — used for the word "Legendary" etc, not for item names. */
    public String style(Rarity rarity, String text) {
        RarityStyle style = styles.get(rarity);
        return style == null ? text : style.apply(text);
    }

    /**
     * An item's name in its OWN colors, wrapped in the obfuscated flair
     * character on both sides if its rarity is flagged for it (Epic and
     * up). Items without their own "colors" fall back to the flat natural
     * color of their material.
     */
    public String styleItemName(RollableItem item) {
        String colored = item.getStyle() != null
                ? item.getStyle().apply(item.getDisplayName())
                : RollFormat.naturalColor(item.getMaterial()) + item.getDisplayName();

        if (!Boolean.TRUE.equals(symbolFlair.get(item.getRarity()))) {
            return colored;
        }
        String flair = ChatColor.MAGIC + "#" + ChatColor.RESET;
        return flair + " " + colored + " " + flair;
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
