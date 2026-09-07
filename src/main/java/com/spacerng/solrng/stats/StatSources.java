package com.spacerng.solrng.stats;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.PrestigeUpgrade;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.player.SkillTreeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Every derived stat, and the list of things that made it.
 *
 * This is the only place each formula is written down. The roll payout,
 * the farm payout and /stats all read it, so the number a player is shown
 * is the number they are actually getting - a screen that explains a
 * formula it doesn't share is worse than no screen at all, because it
 * lies with authority.
 *
 * A stat is built by walking its parts in order: ADD parts fall onto a
 * running total, MULTIPLY parts scale it. The order is the formula, which
 * is why the parts are a list rather than a map.
 */
public final class StatSources {

    /** How a part lands on the running total. */
    public enum Op {
        ADD, MULTIPLY
    }

    /** How the finished number should be read. */
    public enum Format {
        /** 1.5 reads as "+150%" - a bonus on top of nothing. */
        PERCENT,
        /** 1.5 reads as "1.50x" - a scale on something that already exists. */
        MULTIPLIER,
        /** 0.01 reads as "1 in 100". */
        CHANCE
    }

    public enum Id {
        LUCK, SPEED, MONEY, COINS, ENCHANT, SHINY
    }

    /**
     * One contribution. `hint` says where more of it comes from, so a part
     * sitting at zero still teaches something instead of just being blank.
     */
    public record Part(String label, String hint, double value, Op op) {

        /** A part that isn't doing anything yet. */
        public boolean idle() {
            return op == Op.ADD ? value == 0.0 : value == 1.0;
        }
    }

    /** A finished stat: what it's called, what built it, what it came to. */
    public record Stat(Id id, String name, String blurb, Format format,
                       List<Part> parts, double total, String note) {
    }

    private StatSources() {
    }

    // ------------------------------------------------------------- luck

    /**
     * Luck, in the exact order PrestigeManager applies it.
     *
     * Flat sources add so a new one is always worth something; the tag,
     * the index and prestige multiply so late progression scales what you
     * already built instead of being one more small addition.
     */
    public static Stat luck(SolRNGPlugin plugin, PlayerData data, boolean includeNova) {
        SkillTreeManager skills = plugin.getSkillTreeManager();
        List<Part> parts = new ArrayList<>();

        parts.add(new Part("Starforge", "Buy a better one in /starforge",
                data.getStarforgeLuckBonus() * skills.multiplierOf(data, SkillNode.Effect.STARFORGE_POWER),
                Op.ADD));
        parts.add(new Part("Armor", "Buy and wear a set from /armor",
                data.getArmorLuckBonus() * skills.multiplierOf(data, SkillNode.Effect.ARMOR_POWER),
                Op.ADD));
        parts.add(new Part("Skills", "Luck nodes in /skilltree",
                skills.skillLuck(data), Op.ADD));
        parts.add(new Part("Curator", "Luck per drop discovered, from /skilltree",
                skills.totalOf(data, SkillNode.Effect.LUCK_PER_DISCOVERY) * data.getDiscoveredItems().size(),
                Op.ADD));
        parts.add(new Part("Affinity", "Luck per prestige, from /skilltree",
                skills.totalOf(data, SkillNode.Effect.LUCK_PER_PRESTIGE) * data.getPrestige(),
                Op.ADD));
        parts.add(new Part("Permanent", "Fortune rewards you've drunk",
                data.getFlatLuck(), Op.ADD));
        parts.add(new Part("Potions", "Draughts from /potion",
                data.getPotionLuck(), Op.ADD));

        parts.add(new Part("Equipped tag", "Equip a rarer drop in /index",
                plugin.getRarityManager().tagMultiplierFor(data), Op.MULTIPLY));
        parts.add(new Part("Index completion", "Finish whole rarities in /index",
                plugin.getPrestigeManager().indexCompletion(data), Op.MULTIPLY));
        // COMPOUNDING, not linear. Each prestige is worth 1.1x on top of
        // the last, so the tenth is worth more than the first: prestige 10
        // is 2.59x rather than 2.00x, and prestige 30 is 17.4x rather than
        // 4.00x. Resetting has to get better the more often you have done
        // it, or nobody does it twice.
        parts.add(new Part("Prestige", "Prestige again in /prestige",
                Math.pow(1.0 + plugin.getPrestigeManager().getLuckMultiplierPerPrestige(),
                        data.getPrestige()),
                Op.MULTIPLY));

        parts.add(new Part("Prestige upgrades", "Spend Prestige Points in /prestige",
                plugin.getPrestigeManager().upgradeTotal(data, PrestigeUpgrade.Effect.LUCK_BONUS),
                Op.ADD));

        parts.add(new Part("Server boost", "Active for everyone, from /boosts",
                plugin.getBoostManager().multiplier(), Op.MULTIPLY));
        parts.add(new Part("Your boost", "Bought with Credits in /buy",
                data.boostMultiplier("LUCK"), Op.MULTIPLY));
        if (includeNova) {
            parts.add(new Part("Nova Core", "Hold Nova Cores - see /nova",
                    plugin.getNovaCoreManager().multiplierAt(data.getNovaTier()), Op.MULTIPLY));
        }

        return new Stat(Id.LUCK, "Luck",
                "Shifts every roll toward the rarer end of the table.",
                Format.PERCENT, parts, fold(parts), "");
    }

    // ------------------------------------------------------------ speed

    /**
     * Every bought source of Speed is flat, on purpose: a potion carrying
     * a MINUS has to be a trade rather than a catastrophe, and a 0.75x
     * multiplier on a maxed player is far more brutal than -25 flat.
     *
     * The one multiplier is the Starforge ability, which is temporary and
     * has to stay worth firing on a maxed player.
     */
    public static Stat speed(SolRNGPlugin plugin, PlayerData data) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("Base", "Everybody starts here",
                data.getRollSpeedMultiplier(), Op.ADD));
        parts.add(new Part("Skills", "Speed nodes in /skilltree",
                data.getSkillSpeedBonus(), Op.ADD));
        parts.add(new Part("Armor", "Buy and wear a set from /armor",
                data.getArmorSpeedBonus(), Op.ADD));
        parts.add(new Part("Starforge", "Some tiers trade Luck for Speed",
                data.getStarforgeSpeedBonus(), Op.ADD));
        parts.add(new Part("Potions", "Draughts from /potion",
                data.getPotionSpeed(), Op.ADD));
        parts.add(new Part("Ability", "Shift-right-click a late Starforge",
                data.boostMultiplier("SPEED"), Op.MULTIPLY));

        return new Stat(Id.SPEED, "Speed",
                "How fast a roll resolves. Higher is more rolls an hour.",
                Format.MULTIPLIER, parts, Math.max(0.1, fold(parts)),
                "Never drops below 0.10x, however deep a potion's minus goes.");
    }

    // ------------------------------------------------------------ money

    /** What a roll's odds get multiplied by before it pays out. */
    public static Stat money(SolRNGPlugin plugin, PlayerData data) {
        SkillTreeManager skills = plugin.getSkillTreeManager();
        List<Part> parts = new ArrayList<>();

        parts.add(new Part("Base rate", "Every roll pays odds x this",
                plugin.getConfig().getDouble("economy.money-per-odds-multiplier", 10.0), Op.ADD));
        parts.add(new Part("Nova Core", "Hold Nova Cores - see /nova",
                plugin.getNovaCoreManager().multiplierAt(data.getNovaTier()), Op.MULTIPLY));
        parts.add(new Part("Prestige upgrades", "Spend Prestige Points in /prestige",
                1.0 + plugin.getPrestigeManager().upgradeTotal(data, PrestigeUpgrade.Effect.MONEY_BONUS),
                Op.MULTIPLY));
        parts.add(new Part("Skills", "Money nodes in /skilltree",
                1.0 + skills.totalOf(data, SkillNode.Effect.MONEY_MULTIPLIER)
                        + skills.totalOf(data, SkillNode.Effect.MONEY_PER_LEVEL) * data.getLevel(),
                Op.MULTIPLY));

        return new Stat(Id.MONEY, "Money",
                "What rolling pays. Rarer drops pay more of it.",
                Format.MULTIPLIER, parts, fold(parts),
                "A duplicate pays extra on top, if you've bought Duplicate Value.");
    }

    // ------------------------------------------------------------ coins

    public static Stat coins(SolRNGPlugin plugin, PlayerData data) {
        return coins(plugin, data, 1.0);
    }

    /**
     * The farm payout for one crop, before that crop's own yield skills.
     *
     * `momentum` is passed in rather than read, because it's earned live
     * and resets the moment you stop swinging - the farm hands us the real
     * one mid-chain, and /stats asks for 1.0 and shows the part idle. It
     * lands where it does in the order on purpose: ahead of the prestige
     * upgrade, which ADDS, so moving it would quietly change the payout.
     *
     * Per-crop yield stays out entirely. It differs per crop, so quoting
     * it here would be quoting a number that isn't true of the next block
     * you break.
     */
    public static Stat coins(SolRNGPlugin plugin, PlayerData data, double momentum) {
        List<Part> parts = new ArrayList<>();

        parts.add(new Part("Farm skills", "Coin nodes in /farmtree",
                data.getFarmTokenMultiplier(), Op.ADD));
        parts.add(new Part("Hoe tier", "Upgrade the hoe in /crops",
                plugin.getFarmingManager().tierOf(data).coinMultiplier(), Op.MULTIPLY));
        parts.add(new Part("Coin Greed", "Level the enchant on your hoe",
                plugin.getHoeEnchantManager().powerOf(data, "TOKEN_GREED"), Op.ADD));
        parts.add(new Part("Momentum", "Earned live - keep the chain going",
                momentum, Op.MULTIPLY));
        parts.add(new Part("Your boost", "Coin Potions, and Credit boosts",
                data.boostMultiplier("TOKENS"), Op.MULTIPLY));
        parts.add(new Part("Nova Core", "Hold Nova Cores - see /nova",
                plugin.getNovaCoreManager().multiplierAt(data.getNovaTier()), Op.MULTIPLY));
        parts.add(new Part("Prestige upgrades", "Spend Prestige Points in /prestige",
                plugin.getPrestigeManager().upgradeTotal(data, PrestigeUpgrade.Effect.TOKEN_BONUS),
                Op.ADD));
        parts.add(new Part("General skills", "Coin nodes in /skilltree",
                plugin.getSkillTreeManager().multiplierOf(data, SkillNode.Effect.TOKEN_GAIN),
                Op.MULTIPLY));

        return new Stat(Id.COINS, "Coins",
                "What every crop pays on the farm.",
                Format.MULTIPLIER, parts, fold(parts),
                "Each crop's own yield skills multiply this on top.");
    }

    // ---------------------------------------------------------- enchants

    public static Stat enchantProc(SolRNGPlugin plugin, PlayerData data) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("Base", "Every enchant's own chance", 1.0, Op.ADD));
        parts.add(new Part("Skills", "Proc Chance nodes in /farmtree",
                plugin.getSkillTreeManager().multiplierOf(data, SkillNode.Effect.ENCHANT_PROC),
                Op.MULTIPLY));
        parts.add(new Part("Hoe tier", "Upgrade the hoe in /crops",
                plugin.getFarmingManager().tierOf(data).procMultiplier(), Op.MULTIPLY));
        parts.add(new Part("Potions", "Enchant Potions",
                data.boostMultiplier("ENCHANT_PROC"), Op.MULTIPLY));

        return new Stat(Id.ENCHANT, "Enchant Proc",
                "How often every hoe enchant fires.",
                Format.MULTIPLIER, parts, fold(parts),
                "Scales every enchant at once rather than adding levels.");
    }

    // ------------------------------------------------------------ shiny

    public static Stat shiny(SolRNGPlugin plugin, PlayerData data) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("Base", "The same for everyone",
                plugin.getConfig().getDouble("shiny.chance", 0.01), Op.ADD));
        parts.add(new Part("Skills", "Shiny Chance nodes in /skilltree",
                plugin.getSkillTreeManager().multiplierOf(data, SkillNode.Effect.SHINY_CHANCE),
                Op.MULTIPLY));

        return new Stat(Id.SHINY, "Shiny Chance",
                "The odds any drop comes out shiny.",
                Format.CHANCE, parts, Math.min(1.0, fold(parts)),
                "Rolled on top of the drop, so a shiny Common is still a Common.");
    }

    // ----------------------------------------------------------- helpers

    public static Stat of(SolRNGPlugin plugin, PlayerData data, Id id) {
        return switch (id) {
            case SPEED -> speed(plugin, data);
            case MONEY -> money(plugin, data);
            case COINS -> coins(plugin, data);
            case ENCHANT -> enchantProc(plugin, data);
            case SHINY -> shiny(plugin, data);
            default -> luck(plugin, data, true);
        };
    }

    /** Walks the parts in order. The order IS the formula. */
    private static double fold(List<Part> parts) {
        double total = 0.0;
        for (Part part : parts) {
            if (part.op() == Op.ADD) {
                total += part.value();
            } else {
                total *= part.value();
            }
        }
        return total;
    }
}
