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
    // Rarities that roll at exactly their label, and the band shares the
    // rest divide up. See assignRollWeights.
    private final Map<Rarity, Boolean> trueOdds = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Double> shares = new EnumMap<>(Rarity.class);
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
        trueOdds.clear();
        shares.clear();
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
                trueOdds.put(rarity, r.getBoolean("true-odds", false));
                if (r.contains("share")) shares.put(rarity, r.getDouble("share"));
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
                logger.warning("Skipped a malformed item entry in config.yml: " + raw);
            }
        }

        assignLuckMultipliers(config);
        assignRollWeights();

        byName.clear();
        for (RollableItem item : items) {
            byName.put(item.getDisplayName(), item);
        }

        logger.info("Loaded " + items.size() + " rollable items across " + luckFactors.size() + " rarities.");
    }

    /**
     * Gives every item its index Luck multiplier, scaled across its
     * rarity's configured band by how rare it is WITHIN that rarity - the
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

            // Interpolate on LOG odds, not raw odds. A band now runs from
            // 1 in 16 to 1 in 12,000, and on a linear scale every entry
            // but the last three collapses onto the floor of the band.
            double logMin = Math.log(Math.max(1L, minOdds));
            double logMax = Math.log(Math.max(1L, maxOdds));
            for (RollableItem item : tier) {
                double t = logMax == logMin
                        ? 1.0
                        : (Math.log(Math.max(1L, item.getOdds())) - logMin) / (logMax - logMin);
                item.setLuckMultiplier(low + t * (high - low));
            }
        }
    }

    /** O(1) lookup used when recomputing a player's index multiplier. */
    public RollableItem byName(String displayName) {
        return byName.get(displayName);
    }

    /**
     * The Luck multiplier a player is currently getting from their index:
     * the multiplier of whichever drop they have equipped as their tag,
     * or 1.0 with nothing equipped. Grinding a deeper index is what gives
     * you a rarer drop to equip, and equipping it is what cashes it in.
     */
    public double tagMultiplierFor(com.spacerng.solrng.player.PlayerData data) {
        // Gated behind the Index Luck skill - until that's bought the
        // equipped tag is cosmetic and the multiplier reads a flat 1.00x.
        if (!data.hasUnlocked("index_luck")) return 1.0;

        String equipped = data.getEquippedTagItemKey();
        if (equipped == null) return 1.0;
        RollableItem item = byName.get(equipped);
        return item == null ? 1.0 : item.getLuckMultiplier();
    }

    /** How many rollable items a rarity has, for completion counting. */
    public int countIn(Rarity rarity) {
        int total = 0;
        for (RollableItem item : items) {
            if (item.getRarity() == rarity) total++;
        }
        return total;
    }

    /** How many of a rarity this player has found, normal and shiny. */
    public int foundIn(com.spacerng.solrng.player.PlayerData data, Rarity rarity, boolean shiny) {
        int found = 0;
        for (RollableItem item : items) {
            if (item.getRarity() != rarity) continue;
            if (shiny ? data.hasDiscoveredShiny(item.getDisplayName())
                      : data.hasDiscovered(item.getDisplayName())) {
                found++;
            }
        }
        return found;
    }

    public boolean isComplete(com.spacerng.solrng.player.PlayerData data, Rarity rarity, boolean shiny) {
        int total = countIn(rarity);
        return total > 0 && foundIn(data, rarity, shiny) >= total;
    }

    /**
     * What one finished rarity is worth: the shiny figure INSTEAD of the
     * plain one, never both. 1.0 means nothing has been finished.
     */
    public double completionMultiplierFor(com.spacerng.solrng.player.PlayerData data, Rarity rarity,
                                          double perRarity, double perShiny) {
        if (!isComplete(data, rarity, false)) return 1.0;
        return isComplete(data, rarity, true) ? perShiny : perRarity;
    }

    /**
     * Every finished rarity multiplied together. Finishing a whole tier is
     * the end of the collection game rather than a step in it, so the
     * tiers multiply rather than add - and completing all of them in shiny
     * is meant to be the largest number in the plugin.
     *
     * Gated behind the same Index Luck skill the equipped tag is: until
     * that's bought, the index is a collection log and nothing more.
     */
    public double completionMultiplier(com.spacerng.solrng.player.PlayerData data,
                                       double perRarity, double perShiny) {
        if (!data.hasUnlocked("index_luck")) return 1.0;
        double total = 1.0;
        for (Rarity rarity : Rarity.values()) {
            total *= completionMultiplierFor(data, rarity, perRarity, perShiny);
        }
        return total;
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

    /**
     * Builds a style from raw config values - used by anything outside the
     * item table that wants the same gradient look (the Starforge tiers).
     */
    public RarityStyle buildStyle(List<String> colors, boolean bold, boolean underline, boolean strikethrough) {
        return parseStyle(colors, bold, underline, strikethrough);
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
                logger.warning("Bad hex color in config: " + raw);
                return null;
            }
        }
        if ((s.startsWith("&") || s.startsWith("§")) && s.length() == 2) {
            int[] rgb = LEGACY_RGB.get(Character.toLowerCase(s.charAt(1)));
            if (rgb != null) return rgb;
        }
        logger.warning("Unrecognized color in config: " + raw);
        return null;
    }

    private Rarity safeRarity(String key) {
        try {
            return Rarity.valueOf(key.toUpperCase());
        } catch (IllegalArgumentException ex) {
            logger.warning("Unknown rarity in config: " + key);
            return null;
        }
    }

    /** The rarity's plain label color - used for the word "Legendary" etc, not for item names. */
    public String style(Rarity rarity, String text) {
        RarityStyle style = styles.get(rarity);
        return style == null ? text : style.apply(text);
    }

    /** Same, forced bold - used by the shop price lines. */
    public String styleBold(Rarity rarity, String text) {
        RarityStyle style = styles.get(rarity);
        return style == null ? text : style.apply(text, true);
    }

    /**
     * An item's name in its OWN colors, wrapped in the obfuscated flair
     * character on both sides if its rarity is flagged for it (Epic and
     * up). Items without their own "colors" fall back to the flat natural
     * color of their material.
     */
    public String styleItemName(RollableItem item) {
        return styleItemName(item, true);
    }

    /**
     * Same, but the flair can be suppressed. The flair is an obfuscated
     * character, which is fine on a nametag but reads as flickering noise
     * in a tab list - so %solrng_tag_plain% asks for it without.
     */
    public String styleItemName(RollableItem item, boolean withFlair) {
        String colored = item.getStyle() != null
                ? item.getStyle().apply(item.getDisplayName())
                : RollFormat.naturalColor(item.getMaterial()) + item.getDisplayName();

        if (!withFlair || !Boolean.TRUE.equals(symbolFlair.get(item.getRarity()))) {
            return colored;
        }
        String flair = ChatColor.MAGIC + "#" + ChatColor.RESET;
        return flair + " " + colored + " " + flair;
    }

    public double luckFactorFor(Rarity rarity) {
        return luckFactors.getOrDefault(rarity, 0.0);
    }

    /**
     * Turns the labels into the weights the roll uses.
     *
     * A rarity with `true-odds: true` rolls at exactly its label: 1 in
     * 10,000 means 1 in 10,000. Every other rarity is a band taking a
     * `share` of whatever the literal rarities leave, and inside a band the
     * labels only decide who wins against whom. That is how Epic and up can
     * be honest while a Common keeps a friendly "1 in 2" on the tin, and it
     * is why Money, which pays on the label, stays where it was.
     *
     * A band with no share falls back to its raw label sum, which is the
     * old behaviour. Configure shares on every non-literal band or on none.
     */
    private void assignRollWeights() {
        double literal = 0.0;
        Map<Rarity, Double> bandSum = new EnumMap<>(Rarity.class);
        for (RollableItem item : items) {
            double raw = 1.0 / Math.max(1L, item.getOdds());
            if (trueOdds.getOrDefault(item.getRarity(), false)) {
                literal += raw;
            } else {
                bandSum.merge(item.getRarity(), raw, Double::sum);
            }
        }

        double shareTotal = 0.0;
        for (Map.Entry<Rarity, Double> band : bandSum.entrySet()) {
            shareTotal += shares.getOrDefault(band.getKey(), band.getValue());
        }
        double room = Math.max(0.0, 1.0 - literal);

        for (RollableItem item : items) {
            double raw = 1.0 / Math.max(1L, item.getOdds());
            if (trueOdds.getOrDefault(item.getRarity(), false)) {
                item.setRollWeight(raw);
                continue;
            }
            double sum = bandSum.get(item.getRarity());
            double share = shares.getOrDefault(item.getRarity(), sum);
            double bandWeight = shareTotal <= 0.0 ? 0.0 : share / shareTotal * room;
            item.setRollWeight(sum <= 0.0 ? 0.0 : raw / sum * bandWeight);
        }
    }

    /** Whether a rarity's labels are its real odds. */
    public boolean isTrueOdds(Rarity rarity) {
        return trueOdds.getOrDefault(rarity, false);
    }

    public List<RollableItem> getItems() {
        return items;
    }

    /**
     * Looks up a rollable item by its display name - used to recover the
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
        return roll(luck, null);
    }

    /**
     * A roll restricted to a floor rarity, for the Pity Timer skills.
     * Weights inside the surviving band keep their normal proportions, so
     * a forced Rare+ is still much more likely to be a Rare than a
     * Mythical - pity guarantees you something good, not something absurd.
     */
    public RollableItem rollAtLeast(double luck, Rarity minimum) {
        return roll(luck, minimum);
    }

    private RollableItem roll(double luck, Rarity minimum) {
        if (items.isEmpty()) {
            throw new IllegalStateException("No rollable items configured - check config.yml");
        }

        double totalWeight = 0.0;
        double[] effectiveWeights = new double[items.size()];

        for (int i = 0; i < items.size(); i++) {
            RollableItem item = items.get(i);
            if (minimum != null && item.getRarity().ordinal() < minimum.ordinal()) {
                effectiveWeights[i] = 0.0;
                continue;
            }
            double factor = 1.0 + (luck * luckFactorFor(item.getRarity()));
            double weight = item.getRollWeight() * factor;
            effectiveWeights[i] = weight;
            totalWeight += weight;
        }

        // Nothing survived the floor (a rarity with no items configured).
        // Falling back to an unrestricted roll beats handing back null.
        if (totalWeight <= 0.0) {
            return minimum == null ? items.get(0) : roll(luck, null);
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
