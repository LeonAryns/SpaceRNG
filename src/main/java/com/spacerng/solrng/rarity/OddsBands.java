package com.spacerng.solrng.rarity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the band rarities from overlapping on their labels: the rarest
 * Common is never rarer than the easiest Uncommon, and the rarest Uncommon
 * never rarer than the easiest Rare. Epic and up roll at their true odds
 * and already stack cleanly above Rare.
 *
 * The remap keeps every item's place inside its rarity by spacing the band
 * on log odds, the same scale Tag Luck uses, then rounds to numbers that
 * read well. Money pays on the label, so a respaced label pays its new odds.
 */
public final class OddsBands {

    /** The label range each band is spread over. */
    public static final Map<Rarity, long[]> TARGETS = Map.of(
            Rarity.COMMON, new long[]{2L, 750L},
            Rarity.UNCOMMON, new long[]{800L, 4500L},
            Rarity.RARE, new long[]{5000L, 15000L});

    private static final Rarity[] ORDER = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};

    private OddsBands() {
    }

    /** True when a lower band's rarest label runs past the next band's easiest. */
    public static boolean overlaps(Map<Rarity, long[]> minMax) {
        for (int i = 0; i + 1 < ORDER.length; i++) {
            long[] lower = minMax.get(ORDER[i]);
            long[] upper = minMax.get(ORDER[i + 1]);
            if (lower != null && upper != null && lower[1] > upper[0]) return true;
        }
        return false;
    }

    /** One label moved into its band's target range, keeping its place in the band. */
    public static long remap(Rarity rarity, long odds, long oldMin, long oldMax) {
        long[] target = TARGETS.get(rarity);
        if (target == null) return odds;
        double low = Math.log(Math.max(1L, oldMin));
        double high = Math.log(Math.max(1L, oldMax));
        double t = high <= low ? 0.0 : (Math.log(Math.max(1L, odds)) - low) / (high - low);
        t = Math.max(0.0, Math.min(1.0, t));
        double spread = Math.exp(Math.log(target[0]) + t * (Math.log(target[1]) - Math.log(target[0])));
        return Math.max(target[0], Math.min(target[1], nice(spread)));
    }

    /** Whole numbers below 100, fives below 1,000, fifties below 10,000, then five hundreds. */
    static long nice(double value) {
        if (value < 100) return Math.round(value);
        if (value < 1000) return Math.round(value / 5.0) * 5;
        if (value < 10000) return Math.round(value / 50.0) * 50;
        return Math.round(value / 500.0) * 500;
    }

    /**
     * Respaces the band labels in a config item list, but only when they
     * overlap. Returns whether anything changed.
     */
    @SuppressWarnings("unchecked")
    public static boolean remapItems(List<Map<?, ?>> items) {
        Map<Rarity, long[]> minMax = new EnumMap<>(Rarity.class);
        for (Map<?, ?> item : items) {
            Rarity rarity = rarityOf(item);
            Long odds = oddsOf(item);
            if (rarity == null || odds == null) continue;
            minMax.merge(rarity, new long[]{odds, odds},
                    (a, b) -> new long[]{Math.min(a[0], b[0]), Math.max(a[1], b[1])});
        }
        if (!overlaps(minMax)) return false;
        for (Map<?, ?> item : items) {
            Rarity rarity = rarityOf(item);
            Long odds = oddsOf(item);
            if (rarity == null || odds == null || !TARGETS.containsKey(rarity)) continue;
            long[] range = minMax.get(rarity);
            ((Map<Object, Object>) item).put("odds", remap(rarity, odds, range[0], range[1]));
        }
        return true;
    }

    private static Rarity rarityOf(Map<?, ?> item) {
        try {
            return Rarity.valueOf(String.valueOf(item.get("rarity")).toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Long oddsOf(Map<?, ?> item) {
        try {
            return Long.parseLong(String.valueOf(item.get("odds")));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
