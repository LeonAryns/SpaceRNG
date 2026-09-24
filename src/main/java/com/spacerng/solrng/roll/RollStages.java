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
 * The ladder a reveal climbs, one act per rarity band.
 *
 * Leon set the shape of this himself: "ik wil gwn een rarity van bv 20s
 * eerste 5s epic 10s legend 15 mythical 20s divine". Every band gets the
 * same fixed slice of time, so a roll runs five seconds of Epic, and then
 * either it ENDS there and the drop was an Epic, or it breaks through
 * into five seconds of Legendary, and so on. A Divine is four acts and
 * twenty seconds; with Epic switched off in /options it is three acts and
 * fifteen.
 *
 * That fixed slice is the whole point, and it is what V196 got wrong. A
 * counter racing along a curve meant the bands arrived at unpredictable
 * moments, an Epic was over before its number could be read, and the
 * length of the roll and the size of the comet told you what you had
 * before the first band was done. Now every act is exactly the same
 * length and exactly the same act, whatever is coming: an Epic roll and
 * the first five seconds of a Divine are indistinguishable, which is the
 * only way the climb can be suspenseful.
 *
 * Which bands are in the ladder is the roller's own choice. A player who
 * switched the Epic aura off in /options does not get an Epic act, so
 * their show opens at Legendary.
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
     * How hard the counter's climb inside one act is eased. Above 1 the
     * digits turn over slowly at first and race at the end, so an act
     * finishes ON its next band's entry rather than drifting up to it.
     */
    private static final double EASE = 1.35;

    /** Nothing below this is ever shown, however cheap the opening band is. */
    private static final long FLOOR = 100L;

    private final List<Stage> stages;
    private final long dropOdds;
    /**
     * Where each act's climb starts and finishes: {@code acts + 1} numbers,
     * strictly increasing, opening below the first band and landing exactly
     * on the drop's own odds.
     *
     * The band entries alone will not do, and that is what Leon saw as "je
     * ziet nogsteeds niet de odds omhoog gaan bij mythical en divine". A
     * band's entry is the shortest odds in it, so a drop that IS the
     * shortest odds in its band has its own number as the boundary the act
     * before it climbs to, and its own act then has nowhere left to go and
     * sits frozen for five seconds. Both of this server's worst cases are
     * exactly that: the only Divine is one in ten million where Divine
     * starts, and the cheapest Mythical is one in 250,000 where Mythical
     * starts. {@link #boundaries} pulls any boundary that has caught up
     * with the one behind it back to the midpoint, so every act always has
     * a stretch of its own to climb.
     */
    private final long[] marks;

    private RollStages(List<Stage> stages, long[] marks, long dropOdds) {
        this.stages = stages;
        this.marks = marks;
        this.dropOdds = dropOdds;
    }

    /**
     * The ladder for one drop, or an empty one when there is nothing to
     * climb: no odds behind the effect, or not a single band the roller
     * has left switched on.
     */
    public static RollStages of(SolRNGPlugin plugin, PlayerData data, Rarity drop, long odds) {
        if (drop == null || odds <= 0L) return new RollStages(List.of(), new long[0], 0L);

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
        if (rungs.isEmpty()) return new RollStages(List.of(), new long[0], 0L);

        // The counter opens below the first band's own entry, so the first
        // act has somewhere to climb from and the number is moving from
        // the first frame rather than sitting on a threshold.
        long opening = Math.max(FLOOR, rungs.get(0).entryOdds() / 3L);
        return new RollStages(List.copyOf(rungs), boundaries(rungs, opening, odds), odds);
    }

    /**
     * The numbers each act climbs between: the opening, then each band's
     * entry, then the drop's own odds.
     *
     * Walked backwards afterwards so that no boundary has caught up with
     * the one in front of it. Where one has, it is pulled back to the
     * geometric midpoint of its neighbours, which is the middle of the
     * climb in the space the counter actually moves through. Backwards
     * because pulling one back can leave the one behind IT too high, and
     * the same pass fixes that on its next step.
     */
    private static long[] boundaries(List<Stage> rungs, long opening, long odds) {
        int acts = rungs.size();
        long[] marks = new long[acts + 1];
        marks[0] = opening;
        for (int i = 1; i < acts; i++) marks[i] = rungs.get(i).entryOdds();
        marks[acts] = odds;
        for (int i = acts - 1; i >= 1; i--) {
            if (marks[i] < marks[i + 1]) continue;
            long midpoint = Math.round(Math.sqrt((double) marks[i - 1] * marks[i + 1]));
            marks[i] = Math.max(marks[i - 1] + 1L, Math.min(marks[i + 1] - 1L, midpoint));
        }
        return marks;
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

    /** How many acts this reveal runs, which is what sets its length. */
    public int acts() {
        return stages.size();
    }

    /** The rarity of one act, clamped, so a caller never has to bounds check. */
    public Rarity rarityAt(int act) {
        if (stages.isEmpty()) return Rarity.EPIC;
        return stages.get(Math.max(0, Math.min(stages.size() - 1, act))).rarity();
    }

    /** True while this act is not the last one, so a breakthrough follows it. */
    public boolean climbsAfter(int act) {
        return act < stages.size() - 1;
    }

    /**
     * What the counter reads part way through one act.
     *
     * Each act owns one leg of the climb: it starts where that band starts
     * and finishes exactly on the NEXT band's entry, which is the number
     * that promotes it. The last act finishes on the drop's own odds.
     *
     * The climb is linear through the LOGARITHM of the odds rather than
     * through the odds themselves, because a Mythical act covering 250,000
     * to ten million would otherwise sit under a million for four of its
     * five seconds and then blur. In log space the digits turn over at a
     * steady rate the whole way.
     */
    public long shownOdds(int act, double actProgress) {
        if (stages.isEmpty()) return dropOdds;
        int i = Math.max(0, Math.min(stages.size() - 1, act));
        long from = marks[i];
        long to = marks[i + 1];
        if (to <= from) return to;
        double t = Math.pow(Math.max(0.0, Math.min(1.0, actProgress)), EASE);
        long shown = Math.round(from * Math.pow((double) to / from, t));
        return Math.max(from, Math.min(to, shown));
    }

    /** Where one act's climb finishes, which is what its impact pops on. */
    public long target(int act) {
        if (stages.isEmpty()) return dropOdds;
        int i = Math.max(0, Math.min(stages.size() - 1, act));
        return marks[i + 1];
    }
}
