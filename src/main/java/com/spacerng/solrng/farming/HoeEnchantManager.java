package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hoe enchants: the farming equivalent of the Starforge ladder.
 *
 * An enchant is UNLOCKED by a node in the farming tree and then LEVELLED
 * with Tokens by right-clicking the hoe. Splitting it that way keeps a
 * handful of progression choices apart from a sink you return to
 * constantly, and neither menu has to explain the other.
 *
 * The hoe item itself stores nothing: its power is read back from the tree
 * and the player's bought levels every time. A bound, undroppable hoe with
 * no state can't be lost, traded or duplicated, and re-locking an enchant
 * can't leave one carrying power it isn't entitled to.
 *
 * Every enchant's numbers live in config. What each one DOES is applied in
 * FarmPlotManager at harvest time, keyed off the ids below.
 */
public class HoeEnchantManager {

    /** One enchant definition. Level comes from the player's skill nodes. */
    public record Enchant(String id, String display, String description, String icon, int maxLevel,
                          int baseCap, double perLevel, String colour, long baseCost,
                          double costStep, double costPower) {

        /** e.g. "Token Greed III" in the enchant's own colour. */
        public String styled(int level) {
            return colour + display + (level > 1 ? " " + roman(level) : "");
        }

        private static String roman(int value) {
            String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
            return value >= 1 && value <= numerals.length ? numerals[value - 1] : String.valueOf(value);
        }
    }

    private final SolRNGPlugin plugin;
    private final Map<String, Enchant> enchants = new LinkedHashMap<>();

    public HoeEnchantManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        enchants.clear();
        ConfigurationSection section = config.getConfigurationSection("farming.enchants");
        if (section == null) {
            plugin.getLogger().info("No hoe enchants configured.");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection e = section.getConfigurationSection(id);
            if (e == null) continue;
            enchants.put(id.toUpperCase(), new Enchant(
                    id.toUpperCase(),
                    e.getString("display", id),
                    e.getString("description", ""),
                    e.getString("icon", "ENCHANTED_BOOK"),
                    Math.max(1, e.getInt("max-level", 1000)),
                    Math.max(1, e.getInt("base-cap", e.getInt("max-level", 1000))),
                    e.getDouble("per-level", 0.0),
                    colourOf(e.getString("color", "&d")),
                    e.getLong("base-cost", 25000L),
                    e.getDouble("cost-step", 0.0001),
                    e.getDouble("cost-power", 2.0)));
        }
        plugin.getLogger().info("Loaded " + enchants.size() + " hoe enchants.");
    }

    private String colourOf(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "&d" : raw);
    }

    public Map<String, Enchant> getEnchants() {
        return enchants;
    }

    public Enchant get(String id) {
        return id == null ? null : enchants.get(id.toUpperCase());
    }

    /**
     * Whether the farming tree has opened this enchant up. The tree decides
     * WHICH enchants exist for a player; the hoe menu decides how strong
     * they are. Splitting it that way keeps a handful of progression
     * choices apart from a Token sink you return to constantly.
     */
    public boolean isUnlocked(PlayerData data, String enchantId) {
        for (SkillNode node : plugin.getSkillTreeManager().getNodes("farmtree").values()) {
            if (node.getTarget() == null || !node.getTarget().equalsIgnoreCase(enchantId)) continue;
            if (node.getEffect() != SkillNode.Effect.UNLOCK_ENCHANT) continue;
            if (data.hasUnlocked(node.getId())) return true;
        }
        return false;
    }

    /**
     * A player's level: what they've bought on the hoe, plus any rank of an
     * ENCHANT_POWER node pointing at the same enchant. Zero while it's
     * still locked, whatever has been bought - so re-locking an enchant
     * can never leave a hoe carrying power it isn't entitled to.
     */
    public int levelOf(PlayerData data, String enchantId) {
        Enchant enchant = get(enchantId);
        if (enchant == null || !isUnlocked(data, enchantId)) return 0;

        int level = data.getHoeEnchantLevel(enchant.id());
        level += (int) Math.round(plugin.getSkillTreeManager()
                .totalOf(data, SkillNode.Effect.ENCHANT_POWER, enchantId));
        return Math.min(level, maxLevelFor(data, enchant));
    }

    /**
     * The enchant's ceiling for this player: its configured maximum plus
     * whatever the Enchant Mastery nodes have raised it by. Mastery is the
     * thing that lets a hoe keep growing once every enchant is capped, so
     * the ceiling has to be a read of the tree rather than a constant.
     */
    public int maxLevelFor(PlayerData data, Enchant enchant) {
        if (enchant == null) return 0;
        int bonus = (int) Math.round(plugin.getSkillTreeManager()
                .totalOf(data, SkillNode.Effect.ENCHANT_CAP));
        // base-cap is where an enchant starts, max-level is where it can
        // ever finish, and Enchant Mastery is the whole distance between
        // them. Adding the bonus to max-level instead - which is what this
        // used to do - meant the ceiling was never reachable and mastery
        // bought nothing you could see.
        return Math.min(enchant.maxLevel(), enchant.baseCap() + Math.max(0, bonus));
    }

    public int maxLevelFor(PlayerData data, String enchantId) {
        return maxLevelFor(data, get(enchantId));
    }

    /** Tokens for the next level. Grows so late levels are a real sink. */
    /**
     * Polynomial, not exponential.
     *
     * base + step x level^power. Compounding growth cannot survive ten
     * thousand levels: even 1.0007 reaches 1,100x by the end and 1.007
     * reaches 2 x 10^30, which is more Coins than will ever exist. A
     * squared curve climbs the whole way and still lands somewhere a
     * player can actually pay, which is what makes a 10,000 level enchant
     * a long grind rather than a wall with a sign on it.
     */
    public long costFor(Enchant enchant, int currentLevel) {
        return Math.round(enchant.baseCost()
                + enchant.costStep() * Math.pow(currentLevel, enchant.costPower()));
    }

    /**
     * Buys as many levels as the player can pay for, up to `limit`.
     *
     * Ten thousand levels is unclickable one at a time, so the menu offers
     * this on a shift-click. It walks level by level rather than solving
     * the sum, because the cap and the wallet both have to be rechecked
     * every step anyway.
     */
    public int buyMany(PlayerData data, String enchantId, int limit) {
        int bought = 0;
        while (bought < limit && buy(data, enchantId)) {
            bought++;
        }
        return bought;
    }

    /** "12.5%" or "+1.80x" - how an enchant's power reads in its tooltip. */
    public String describePower(Enchant enchant, int level) {
        // Every enchant is a percentage now, so "level 400 of 1000" means
        // the same thing whichever one you're reading.
        return format(enchant.perLevel() * level);
    }

    /** "+12.5%" - one shape for every enchant's magnitude. */
    public static String format(double power) {
        double percent = power * 100.0;
        String number = percent >= 100 || percent == Math.rint(percent)
                ? String.format("%,.0f", percent)
                : String.format("%.2f", percent);
        return "+" + number + "%";
    }

    /**
     * Buys one level with Tokens. Returns false when it's locked, maxed or
     * unaffordable - the menu already knows which, so it reports it.
     */
    public boolean buy(PlayerData data, String enchantId) {
        Enchant enchant = get(enchantId);
        if (enchant == null || !isUnlocked(data, enchantId)) return false;

        int level = levelOf(data, enchantId);
        if (level >= maxLevelFor(data, enchant)) return false;
        if (!data.spendTokens(costFor(enchant, level))) return false;

        data.setHoeEnchantLevel(enchant.id(), data.getHoeEnchantLevel(enchant.id()) + 1);
        return true;
    }

    /** The enchant's total effect at the player's level - 0 if not unlocked. */
    public double powerOf(PlayerData data, String enchantId) {
        Enchant enchant = get(enchantId);
        if (enchant == null) return 0.0;
        // Proc Chance lifts every enchant at once, so it multiplies the
        // total rather than adding levels - a flat level bonus would be
        // worth wildly different amounts to a 0.02/level enchant and a
        // 0.00004/level one.
        double proc = plugin.getSkillTreeManager()
                .multiplierOf(data, SkillNode.Effect.ENCHANT_PROC)
                * data.boostMultiplier("ENCHANT_PROC");
        return enchant.perLevel() * levelOf(data, enchantId) * proc;
    }

    /** The same figure the skill tree quotes, for one player. */
    public String describePower(PlayerData data, String enchantId) {
        if (get(enchantId) == null) return "+0%";
        // Momentum's number is a ceiling on a live multiplier, not a bonus
        // you're already getting, so it says so rather than pretending to
        // be the same kind of figure as the others.
        if ("MOMENTUM".equalsIgnoreCase(enchantId)) {
            return "up to " + String.format("%.2f", 1.0 + powerOf(data, enchantId)) + "x";
        }
        return format(powerOf(data, enchantId));
    }

    /** Whether this enchant fires on a roll rather than applying always. */
    public boolean isProc(String enchantId) {
        return switch (enchantId == null ? "" : enchantId.toUpperCase()) {
            case "TOKEN_GREED", "SPEED", "MOMENTUM" -> false;
            default -> true;
        };
    }

    public boolean has(PlayerData data, String enchantId) {
        return levelOf(data, enchantId) > 0;
    }
}
