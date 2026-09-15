package com.spacerng.solrng.perk;

import com.spacerng.solrng.rarity.Rarity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * One perk in a player's collection: a tier and one to three stats, each
 * with its own rolled bonus (0.37 is 1.37x).
 *
 * A stable UUID rather than a list index so equipping and moving survive
 * vault reordering, and so a save file can point at "this perk"
 * unambiguously even when the vault is rewritten.
 */
public record PerkInstance(UUID id, Rarity tier, Map<PerkStat, Double> stats) {

    public PerkInstance {
        EnumMap<PerkStat, Double> copy = new EnumMap<>(PerkStat.class);
        if (stats != null) copy.putAll(stats);
        stats = Collections.unmodifiableMap(copy);
    }

    /** "Legendary Perk" - the display name of the item. */
    public String display() {
        return tier.displayName() + " Perk";
    }

    /** The stat with the biggest bonus, which gives the perk its icon. Null without stats. */
    public PerkStat strongest() {
        PerkStat best = null;
        for (var entry : stats.entrySet()) {
            if (best == null || entry.getValue() > stats.get(best)) best = entry.getKey();
        }
        return best;
    }

    /** Every bonus added together, for sorting perks of one tier. */
    public double total() {
        double sum = 0.0;
        for (double bonus : stats.values()) sum += bonus;
        return sum;
    }

    /** Compact key for save files: id|TIER|STAT=0.3700,STAT=0.1200 */
    public String encode() {
        StringJoiner joined = new StringJoiner(",");
        stats.forEach((stat, bonus) -> joined.add(stat.name() + "=" + String.format(Locale.ROOT, "%.4f", bonus)));
        return id + "|" + tier.name() + "|" + joined;
    }

    /** Reads a perk out of a save file, including ones saved before V133. Null on bad input. */
    public static PerkInstance decode(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split("\\|", -1);
        try {
            if (parts.length == 3) {
                Map<PerkStat, Double> stats = new EnumMap<>(PerkStat.class);
                for (String piece : parts[2].split(",")) {
                    if (piece.isBlank()) continue;
                    String[] pair = piece.split("=");
                    if (pair.length == 2) stats.put(PerkStat.valueOf(pair[0]), Double.parseDouble(pair[1]));
                }
                return new PerkInstance(UUID.fromString(parts[0]), Rarity.valueOf(parts[1]), stats);
            }
            if (parts.length == 4) {
                return legacy(UUID.fromString(parts[0]), parts[1], Rarity.valueOf(parts[2]),
                        Integer.parseInt(parts[3]));
            }
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return null;
    }

    /**
     * Perks rolled before V133 had a type and a level from I to V. They
     * keep their tier; the type picks the stats and the level where in the
     * tier's range each one lands, so a V is still the top of its range.
     */
    private static PerkInstance legacy(UUID id, String type, Rarity tier, int level) {
        List<PerkStat> order = switch (type) {
            case "MONEY" -> List.of(PerkStat.MONEY_PERCENT, PerkStat.COINS_PERCENT, PerkStat.SHINY_PERCENT);
            case "LUCK" -> List.of(PerkStat.LUCK_PERCENT, PerkStat.SHINY_PERCENT, PerkStat.MONEY_PERCENT);
            case "SPEED" -> List.of(PerkStat.ROLL_SPEED_FLAT, PerkStat.LUCK_PERCENT, PerkStat.MONEY_PERCENT);
            case "FARM" -> List.of(PerkStat.COINS_PERCENT, PerkStat.ENCHANT_PROC_PERCENT, PerkStat.MONEY_PERCENT);
            default -> List.of(PerkStat.LUCK_PERCENT, PerkStat.MONEY_PERCENT, PerkStat.ROLL_SPEED_FLAT);
        };
        int count = PerkManager.defaultStatCount(tier);
        double[] range = PerkManager.DEFAULT_RANGES.get(tier);
        double at = (Math.max(1, Math.min(5, level)) - 1) / 4.0;
        Map<PerkStat, Double> stats = new EnumMap<>(PerkStat.class);
        for (int i = 0; i < count && i < order.size(); i++) {
            stats.put(order.get(i), range[0] + (range[1] - range[0]) * at);
        }
        return new PerkInstance(id, tier, stats);
    }
}
