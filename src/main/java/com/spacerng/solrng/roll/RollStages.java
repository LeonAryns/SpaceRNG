package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollableItem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The ladder a reveal climbs while its odds counter runs.
 *
 * A big drop used to be one rarity from the first frame: Divine colours,
 * Divine size, Divine everything, for fifteen seconds. That gives the
 * answer away before anything has happened and it wastes the one thing a
 * counter is good at, which is crossing a line in front of you.
 *
 * So the reveal starts in the lowest band the player can see and climbs.
 * Epic is small and violet, and the moment the counter passes into
 * Legendary odds the whole thing grows, recolours and pops, then again at
 * Mythical and again at Divine. Every stage IS that rarity's own look,
 * the same numbers {@link RollAura} and {@link RollComet} already use for
 * it, so the stages cannot drift apart from the auras they are borrowed
 * from.
 *
 * Which bands are in the ladder is the roller's own choice. A player who
 * switched the Epic aura off in /options does not get an Epic stage, so
 * their show opens at Legendary, which is exactly what they asked for by
 * switching it off.
 *
 * A band's entry is the shortest odds any drop in it actually has, read
 * off the item list rather than written down twice. Epic and up roll at
 * their true label, so the entry of a band is a real number a player can
 * check against their own /index.
 */
public final class RollStages {

    /** One rung: the rarity, and the odds at which the reveal enters it. */
    public record Stage(Rarity rarity, long entryOdds) {
    }

    /**
     * How hard the counter's climb is eased. Above 1 the big numbers land
     * late, which is what keeps the last band from arriving halfway
     * through.
     */
    private static final double EASE = 1.6;

    /** Nothing below this is ever shown, however cheap the opening band is. */
    private static final long FLOOR = 100L;

    /**
     * Where the counter stops climbing, as a fraction of the flight. The
     * rest of the run holds the final number.
     *
     * It cannot be 1.0. The top band's entry is often the drop's own odds
     * exactly, and this server's only Divine is one in ten million, which
     * is also where Divine starts. A counter that finished on the last
     * frame would promote to Divine ON the impact, so the whole build-up
     * would be red Mythical and the thing the player waited fifteen
     * seconds for would never be on the screen at all. Landing at 0.78
     * leaves the last fifth of the run in the band that was actually
     * found, and the implosion at 0.88 happens in its colours.
     *
     * The same figure the reel lands on, which is not a coincidence: it is
     * how long a payoff needs to be looked at before the next thing moves.
     */
    private static final double COUNTS_UNTIL = 0.78;

    /**
     * The latest the LAST promotion is allowed to happen.
     *
     * Without it, a drop sitting on its own band's entry promotes at the
     * moment the counter finishes and the band it was actually found in
     * gets a second of screen time. Both of this server's worst cases are
     * exactly that: the only Divine is one in ten million and Divine
     * starts at ten million, and the cheapest Legendary is one in a
     * hundred thousand where Legendary starts. Pulling the whole climb
     * forward until the top rung lands here gives the band that was found
     * better than a third of the run in its own colours, and costs the
     * lower bands nothing but a slightly quicker walk through them.
     *
     * A drop comfortably inside its band already crosses early and is
     * left alone.
     */
    private static final double TOP_BY = 0.62;

    private final List<Stage> stages;
    private final long fromOdds;
    private final long toOdds;
    /** Where this particular climb finishes, at or before {@link #COUNTS_UNTIL}. */
    private final double until;

    private RollStages(List<Stage> stages, long fromOdds, long toOdds, double until) {
        this.stages = stages;
        this.fromOdds = fromOdds;
        this.toOdds = toOdds;
        this.until = until;
    }

    /**
     * The ladder for one drop, or an empty one when there is nothing to
     * climb: no odds behind the effect, or not a single band the roller
     * has left switched on.
     */
    public static RollStages of(SolRNGPlugin plugin, PlayerData data, Rarity drop, long odds) {
        if (drop == null || odds <= 0L) return new RollStages(List.of(), 0L, 0L, COUNTS_UNTIL);

        Map<Rarity, Long> entries = entryOdds(plugin);
        List<Stage> rungs = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) {
            if (!RollAura.isBigDrop(rarity)) continue;
            if (rarity.ordinal() > drop.ordinal()) break;
            // The roller's own switches decide where their show opens.
            if (!data.isAuraEnabled(rarity)) continue;
            Long entry = entries.get(rarity);
            if (entry == null) continue;
            rungs.add(new Stage(rarity, entry));
        }
        if (rungs.isEmpty()) return new RollStages(List.of(), 0L, 0L, COUNTS_UNTIL);

        long start = rungs.get(0).entryOdds();
        // A drop sitting on its own band's entry would leave the counter
        // with nowhere to climb, so it opens a little below instead.
        if (start >= odds) start = Math.max(FLOOR, odds / 4L);
        start = Math.max(FLOOR, start);
        return new RollStages(List.copyOf(rungs), start, odds,
                until(start, odds, rungs.get(rungs.size() - 1).entryOdds()));
    }

    /**
     * Where this climb should finish so that the top rung lands by
     * {@link #TOP_BY}.
     *
     * The counter is linear in log space, so the top band is entered at
     * {@code until * k^(1/EASE)} where k is how far up the log climb that
     * band's entry sits. Solving that for the moment it should land is
     * one line, and a drop that already crosses early keeps the full
     * window.
     */
    private static double until(long start, long odds, long topEntry) {
        double span = Math.log((double) odds / start);
        if (span <= 1.0e-9) return COUNTS_UNTIL;
        double k = Math.log((double) topEntry / start) / span;
        if (k <= 0.0) return COUNTS_UNTIL;
        double lands = Math.pow(Math.min(1.0, k), 1.0 / EASE);
        if (lands <= 1.0e-9) return COUNTS_UNTIL;
        return Math.min(COUNTS_UNTIL, TOP_BY / lands);
    }

    /** The shortest odds in each Epic and up band, read off the loaded items. */
    private static Map<Rarity, Long> entryOdds(SolRNGPlugin plugin) {
        Map<Rarity, Long> out = new EnumMap<>(Rarity.class);
        for (RollableItem item : plugin.getRarityManager().getItems()) {
            if (!RollAura.isBigDrop(item.getRarity())) continue;
            Long held = out.get(item.getRarity());
            if (held == null || item.getOdds() < held) out.put(item.getRarity(), item.getOdds());
        }
        return out;
    }

    public boolean isEmpty() {
        return stages.isEmpty();
    }

    public int size() {
        return stages.size();
    }

    /** The rarity of one rung, clamped, so a caller never has to bounds check. */
    public Rarity rarityAt(int index) {
        if (stages.isEmpty()) return Rarity.EPIC;
        return stages.get(Math.max(0, Math.min(stages.size() - 1, index))).rarity();
    }

    /**
     * What the counter reads at this point of the run.
     *
     * It climbs in a straight line through the LOGARITHM of the odds
     * rather than through the odds themselves. A linear climb to one in
     * five million sits under a hundred thousand for the first nine tenths
     * of the run and then jumps, so every rarity would read the same for
     * most of its build-up and the bands would all be crossed in the last
     * second. In log space the digits turn over at a steady rate the whole
     * way down, and the rungs are spread across the run.
     */
    public long shownOdds(double progress) {
        if (stages.isEmpty() || toOdds <= fromOdds) return toOdds;
        double run = Math.max(0.0, Math.min(1.0, progress / until));
        double t = Math.pow(run, EASE);
        long shown = Math.round(fromOdds * Math.pow((double) toOdds / fromOdds, t));
        return Math.max(fromOdds, Math.min(toOdds, shown));
    }

    /**
     * Which rung the reveal is on at those odds. Never below the opening
     * band: the show starts inside its first stage rather than climbing
     * into it, so the first pop a player sees is a real promotion.
     */
    public int indexAt(long shown) {
        int index = 0;
        for (int i = 0; i < stages.size(); i++) {
            if (shown >= stages.get(i).entryOdds()) index = i;
        }
        return index;
    }
}
