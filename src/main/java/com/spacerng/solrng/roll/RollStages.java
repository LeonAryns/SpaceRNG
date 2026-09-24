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
    private final long openingOdds;
    private final long dropOdds;

    private RollStages(List<Stage> stages, long openingOdds, long dropOdds) {
        this.stages = stages;
        this.openingOdds = openingOdds;
        this.dropOdds = dropOdds;
    }

    /**
     * The ladder for one drop, or an empty one when there is nothing to
     * climb: no odds behind the effect, or not a single band the roller
     * has left switched on.
     */
    public static RollStages of(SolRNGPlugin plugin, PlayerData data, Rarity drop, long odds) {
        if (drop == null || odds <= 0L) return new RollStages(List.of(), 0L, 0L);

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
        if (rungs.isEmpty()) return new RollStages(List.of(), 0L, 0L);

        // The counter opens below the first band's own entry, so the first
        // act has somewhere to climb from and the number is moving from
        // the first frame rather than sitting on a threshold.
        long opening = Math.max(FLOOR, rungs.get(0).entryOdds() / 3L);
        return new RollStages(List.copyOf(rungs), opening, odds);
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
        long from = act <= 0 ? openingOdds : stages.get(Math.min(act, stages.size() - 1)).entryOdds();
        long to = target(act);
        if (to <= from) return to;
        double t = Math.pow(Math.max(0.0, Math.min(1.0, actProgress)), EASE);
        long shown = Math.round(from * Math.pow((double) to / from, t));
        return Math.max(from, Math.min(to, shown));
    }

    /**
     * Where one act's climb finishes: the next band's entry, or the drop's
     * own odds on the last act.
     */
    public long target(int act) {
        if (stages.isEmpty()) return dropOdds;
        if (act < stages.size() - 1) return stages.get(act + 1).entryOdds();
        return dropOdds;
    }
}
