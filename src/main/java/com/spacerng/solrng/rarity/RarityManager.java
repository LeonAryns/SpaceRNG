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
    private final Map<Rarity, Double> luckExponents = new EnumMap<>(Rarity.class);
    private boolean exponentCurve = true;

    /**
     * How hard each rarity bends with Luck on the exponent curve.
     *
     * Defaults live here rather than only in config so a server that has
     * never seen this key still gets the curve: a missing key falls back
     * to its code default, a whole new section would not.
     */
    // V158: Epic and up take exactly (1 + Luck), so +1,700% Luck makes a
    // 1 in 50,000 drop a 1 in 2,778. Common has no exponent at all: it is
    // whatever the other rarities leave over. See luckWeightFactor.
    private static final Map<Rarity, Double> DEFAULT_EXPONENTS = new EnumMap<>(Map.of(
            Rarity.COMMON, 0.0,
            Rarity.UNCOMMON, 0.15,
            Rarity.RARE, 0.5,
            Rarity.EPIC, 1.0,
            Rarity.LEGENDARY, 1.0,
            Rarity.MYTHICAL, 1.0,
            Rarity.DIVINE, 1.0));
    // Rarities that roll at exactly their label, and the band shares the
    // rest divide up. See assignRollWeights.
    private final Map<Rarity, Boolean> trueOdds = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Double> shares = new EnumMap<>(Rarity.class);
    private final Map<Rarity, RarityStyle> styles = new EnumMap<>(Rarity.class);
    // Rarities flagged with "symbol: true" wrap item names in an
    // obfuscated flair character on BOTH sides (Epic and up).
    private final Map<Rarity, Boolean> symbolFlair = new EnumMap<>(Rarity.class);
    /**
     * The mark a rarity wears on both sides of its name where an
     * obfuscated one cannot go.
     *
     * The flair has always been an obfuscated '#', which flickers, and
     * flickering text in a tab list is unreadable, so the plain path threw
     * it away and the tab tag had no marks at all. A real glyph solves
     * both: the nametag keeps its flicker, tab and chat get this.
     */
    private final Map<Rarity, String> symbolMark = new EnumMap<>(Rarity.class);
    private final Logger logger;
    /** How long a drop's name may be where it shares a line, from tag.max-name-length. */
    private int maxPlainName = 18;

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
        symbolMark.clear();
        maxPlainName = config.getInt("tag.max-name-length", 18);

        exponentCurve = !"linear".equalsIgnoreCase(config.getString("luck-curve", "exponent"));

        ConfigurationSection raritySection = config.getConfigurationSection("rarities");
        if (raritySection != null) {
            for (String key : raritySection.getKeys(false)) {
                Rarity rarity = safeRarity(key);
                if (rarity == null) continue;
                ConfigurationSection r = raritySection.getConfigurationSection(key);
                if (r == null) continue;

                luckFactors.put(rarity, r.getDouble("luck-factor", 0.0));
                luckExponents.put(rarity, r.getDouble("luck-exponent",
                        DEFAULT_EXPONENTS.getOrDefault(rarity, 0.0)));
                trueOdds.put(rarity, r.getBoolean("true-odds", false));
                if (r.contains("share")) shares.put(rarity, r.getDouble("share"));
                styles.put(rarity, parseStyle(r.getStringList("colors"),
                        r.getBoolean("bold", false),
                        r.getBoolean("underline", false),
                        r.getBoolean("strikethrough", false),
                        r.getBoolean("italic", false)));
                symbolFlair.put(rarity, r.getBoolean("symbol", false));
                symbolMark.put(rarity, r.getString("symbol-char", "✦"));
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
                RarityStyle style = parseItemStyle(raw);
                RarityStyle rarityStyle = styles.get(rarity);
                // No colours of its own: the name takes the colour of its
                // block, with more colour in it the rarer it is.
                if (style == null) {
                    style = parseStyle(BlockColours.gradientFor(material, rarity,
                            rarityStyle == null ? null : rarityStyle.stops()), false, false, false);
                }
                // Every drop wears its rarity's weight on its own name. V206
                // set bold and italic on the rarity and only the rarity
                // label ever used it, so no drop name changed.
                style = style.withWeightOf(rarityStyle);
                items.add(new RollableItem(material, name, rarity, odds, style));
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
        // A drop that was renamed still answers to its old name, for the
        // items already in inventories and anything saved under it.
        RENAMED.forEach((old, now) -> {
            RollableItem item = byName.get(now);
            if (item != null) byName.putIfAbsent(old, item);
        });

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

    /**
     * Drops that were renamed, old name to new. Discoveries, found counts
     * and tags are saved under a drop's name, so player data is read
     * through {@link #currentName} and an old save keeps what it had.
     * V209: the Divine was too long for tab ("Halo of the First Star").
     */
    public static final Map<String, String> RENAMED = Map.of("Halo of the First Star", "First Star");

    public static String currentName(String name) {
        return name == null ? null : RENAMED.getOrDefault(name, name);
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
        // Gated behind the Tag Luck skill - until that's bought the
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
     * Gated behind the same Tag Luck skill the equipped tag is: until
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
        return parseStyle(colors, bold, underline, strikethrough, false);
    }

    private RarityStyle parseStyle(List<String> colors, boolean bold, boolean underline,
                                   boolean strikethrough, boolean italic) {
        List<int[]> stops = new ArrayList<>();
        for (String colorStr : colors) {
            int[] rgb = parseColor(colorStr);
            if (rgb != null) stops.add(rgb);
        }
        if (stops.isEmpty()) stops.add(new int[]{255, 255, 255});
        return new RarityStyle(stops, bold, underline, strikethrough, italic);
    }

    /**
     * The mark a rarity wears on either side of a drop's name, for tab and
     * chat. Empty for a rarity that carries no symbol.
     */
    public String markFor(Rarity rarity) {
        if (!Boolean.TRUE.equals(symbolFlair.get(rarity))) return "";
        String mark = symbolMark.get(rarity);
        return mark == null ? "" : mark;
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
     * Same, but false is the copy that shares a line (tab and chat), cut
     * to tag.max-name-length. Both wear the same flickering flair: V205
     * gave the shared copy a fixed star instead, and Leon wants the mark
     * that is really on either side of the name (V209).
     */
    public String styleItemName(RollableItem item, boolean full) {
        String name = shorten(item.getDisplayName(), full);
        String colored = item.getStyle() != null
                ? item.getStyle().apply(name)
                : RollFormat.naturalColor(item.getMaterial()) + name;
        return withFlair(item, colored);
    }

    /**
     * Cuts a drop's name down for the places it has to share a line.
     *
     * A tab list entry is a name, a rank badge, a tag and the odds beside
     * each other, and a long drop name pushes the rest off: "ook wil ik de
     * namen niet te lang vanwege tab en chat dat het te druk wordt". Only
     * the plain path is cut, so the drop itself, its tooltip and the
     * nametag over somebody's head all keep the full name. 0 turns it off.
     */
    private String shorten(String name, boolean full) {
        if (full) return name;
        if (maxPlainName <= 0 || name.length() <= maxPlainName) return name;
        return name.substring(0, Math.max(1, maxPlainName - 1)).trim() + "…";
    }

    private static final List<String> SHINY_COLORS = List.of("#3CE8FF", "#A6F7FF");

    /**
     * A shiny's name: the drop's own colours are swapped for the shiny aqua
     * gradient, in bold, so a shiny reads as shiny before its markers are
     * even noticed. The flair follows the same rules as the plain name.
     */
    /** Any text in the shiny gradient, for the shiny drop banner. */
    public String styleShiny(String text) {
        return buildStyle(SHINY_COLORS, true, false, false).apply(text);
    }

    public String styleShinyName(RollableItem item) {
        RarityStyle shine = buildStyle(SHINY_COLORS, true, false, false);
        return withFlair(item, shine.apply(item.getDisplayName()));
    }

    /**
     * Wraps a coloured name in its rarity's mark when the rarity asks for
     * it. Each flair carries the colour of the name beside it: a bare flair
     * inherited whatever came before it, which in a chat line was the grey
     * of ": " and left the first glyph the wrong colour.
     */
    private String withFlair(RollableItem item, String colored) {
        // A still glyph of the rarity's own, not the obfuscated flicker it
        // was until V210: a flickering character changes width every tick
        // and shoved the whole name about in tab, and it was the same
        // noise on every rarity where each one should look different.
        String mark = markFor(item.getRarity());
        if (mark.isEmpty()) return colored;
        // Colour only on the marks. The name's own codes include its
        // weight, and Divine's underline ran under the star as well (V212).
        String left = colourOnly(leadingCodes(colored)) + mark + ChatColor.RESET;
        String right = lastColour(colored) + mark + ChatColor.RESET;
        return left + " " + colored + ChatColor.RESET + " " + right;
    }

    /** The colour and format codes a legacy string starts with. */
    private static String leadingCodes(String legacy) {
        int i = 0;
        while (i + 1 < legacy.length() && legacy.charAt(i) == ChatColor.COLOR_CHAR) i += 2;
        return legacy.substring(0, i);
    }

    /** The colour codes in a run of legacy codes, with bold, italic and the rest left out. */
    private static String colourOnly(String codes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < codes.length(); i += 2) {
            char code = Character.toLowerCase(codes.charAt(i + 1));
            if (code == 'x' || "0123456789abcdef".indexOf(code) >= 0) out.append(codes, i, i + 2);
        }
        return out.toString();
    }

    /** The last colour a legacy string sets, hex or classic, or "" if it sets none. */
    private static String lastColour(String legacy) {
        String last = "";
        for (int i = 0; i + 1 < legacy.length(); i++) {
            if (legacy.charAt(i) != ChatColor.COLOR_CHAR) continue;
            char code = Character.toLowerCase(legacy.charAt(i + 1));
            if (code == 'x' && i + 14 <= legacy.length()) {
                last = legacy.substring(i, i + 14);
                i += 13;
            } else if ("0123456789abcdef".indexOf(code) >= 0) {
                last = legacy.substring(i, i + 2);
                i++;
            }
        }
        return last;
    }

    public double luckFactorFor(Rarity rarity) {
        return luckFactors.getOrDefault(rarity, 0.0);
    }

    public double luckExponentFor(Rarity rarity) {
        return luckExponents.getOrDefault(rarity, DEFAULT_EXPONENTS.getOrDefault(rarity, 0.0));
    }

    /**
     * What one rarity's weight is multiplied by at this much Luck.
     *
     * On the exponent curve Common is not multiplied at all; roll() makes
     * it the remainder instead. Until V158 Common took a negative power,
     * which shrank the total weight of a roll, and dividing by that total
     * multiplied every other rarity a second time. At +1,700% Luck an Epic
     * came out about 39 times likelier instead of 18, and a Mythical about
     * 166 times. Now the roll's weights always add to 1, so a rarity's
     * factor is exactly how much likelier it gets.
     *
     * The exponent curve is the one that keeps working. On the old linear
     * curve every rarity's weight grew in a straight line with Luck, so
     * past roughly 100,000% the ratios between them stopped moving
     * altogether: Uncommon sat at 86% of rolls and Divine at 1 in 35,000
     * whether you had ten thousand percent Luck or twenty billion. Raising
     * each rarity to its own power instead means the gap between two
     * rarities keeps widening for as long as Luck keeps climbing, which is
     * what a player with an absurd Luck number expects to see.
     *
     * Both curves are identical at zero Luck and close through the early
     * game, so nothing about a new account changes.
     */
    public double luckWeightFactor(Rarity rarity, double luck) {
        if (exponentCurve) {
            return Math.pow(1.0 + Math.max(0.0, luck), luckExponentFor(rarity));
        }
        double f = luckFactorFor(rarity);
        return f >= 0.0 ? 1.0 + luck * f : 1.0 / (1.0 + luck * -f);
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

    /**
     * Every item's weight at this much Luck, in the order of getItems().
     *
     * On the exponent curve the lowest rarity is the filler: every other
     * rarity is multiplied by its factor, and Common gets what is left of
     * 1. The weights then add to 1, so each one is the item's real chance
     * and nothing is scaled twice. When Luck is so high that the rest adds
     * past 1, Common is gone and the others split the roll by weight.
     */
    public double[] weightsAt(double luck, Rarity minimum) {
        double[] weights = new double[items.size()];
        Rarity filler = Rarity.values()[0];
        double other = 0.0;
        double fillerBase = 0.0;
        for (int i = 0; i < items.size(); i++) {
            RollableItem item = items.get(i);
            if (minimum != null && item.getRarity().ordinal() < minimum.ordinal()) continue;
            if (exponentCurve && item.getRarity() == filler) {
                weights[i] = item.getRollWeight();
                fillerBase += weights[i];
                continue;
            }
            weights[i] = item.getRollWeight() * luckWeightFactor(item.getRarity(), luck);
            other += weights[i];
        }
        if (exponentCurve && fillerBase > 0.0) {
            double scale = Math.max(0.0, 1.0 - other) / fillerBase;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getRarity() == filler) weights[i] *= scale;
            }
        }
        return weights;
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

    /** A roll that can't land below the given rarity, for Lucky Streak. */
    public RollableItem rollAtLeast(double luck, Rarity minimum) {
        return roll(luck, minimum);
    }

    private RollableItem roll(double luck, Rarity minimum) {
        if (items.isEmpty()) {
            throw new IllegalStateException("No rollable items configured - check config.yml");
        }

        double[] effectiveWeights = weightsAt(luck, minimum);
        double totalWeight = 0.0;
        for (double weight : effectiveWeights) totalWeight += weight;

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
