package com.spacerng.solrng.quest;

/**
 * One step of the starting guide.
 *
 * A quest is deliberately just a threshold against a number the plugin
 * already tracks — same approach as milestones. Nothing here counts
 * anything itself, so a quest can't drift out of sync with the thing it's
 * describing, and a player who did the task before the guide existed is
 * already credited for it.
 */
public class Quest {

    /**
     * What number a quest watches. Every one of these is read from live
     * state at check time; none of them is a counter this class keeps.
     */
    public enum Goal {
        ROLLS,              // lifetime rolls
        DISCOVERIES,        // unique drops in the index
        SKILL_NODES,        // skill tree nodes owned, across both trees
        HAS_NODE,           // a specific node — target is its id
        TAG_EQUIPPED,       // 1 once a tag is on
        BANKED_DROPS,       // drops stored via /convert
        STARFORGE_TIER,     // ladder position, 0 = Basic
        ARMOR_PIECES,       // armor pieces bought
        CROPS_HARVESTED,
        NOVA_TIER,          // best Nova Core tier ever reached
        MILESTONES_CLAIMED,
        LEVEL,
        PRESTIGE
    }

    private final String id;
    private final String display;
    private final String hint;      // the one-line "how" shown under the goal
    private final Goal goal;
    private final String target;    // node id, crop id — goal-dependent
    private final long amount;
    private final long rewardTokens;
    private final double rewardMoney;

    public Quest(String id, String display, String hint, Goal goal, String target, long amount,
                 long rewardTokens, double rewardMoney) {
        this.id = id;
        this.display = display;
        this.hint = hint;
        this.goal = goal;
        this.target = (target == null || target.isBlank()) ? null : target;
        this.amount = Math.max(1L, amount);
        this.rewardTokens = rewardTokens;
        this.rewardMoney = rewardMoney;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getHint() {
        return hint;
    }

    public Goal getGoal() {
        return goal;
    }

    public String getTarget() {
        return target;
    }

    public long getAmount() {
        return amount;
    }

    public long getRewardTokens() {
        return rewardTokens;
    }

    public double getRewardMoney() {
        return rewardMoney;
    }
}
