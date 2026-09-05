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
                          double perLevel, String colour, long baseCost, double costGrowth) {

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
            plugin.getLogger().info("[SolRNG] No hoe enchants configured.");
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
                    Math.max(1, e.getInt("max-level", 5)),
                    e.getDouble("per-level", 0.0),
                    colourOf(e.getString("color", "&d")),
                    e.getLong("base-cost", 25000L),
                    e.getDouble("cost-growth", 1.12)));
        }
        plugin.getLogger().info("[SolRNG] Loaded " + enchants.size() + " hoe enchants.");
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
     * still locked, whatever has been bought — so re-locking an enchant
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
        return enchant.maxLevel() + Math.max(0, bonus);
    }

    public int maxLevelFor(PlayerData data, String enchantId) {
        return maxLevelFor(data, get(enchantId));
    }

    /** Tokens for the next level. Grows so late levels are a real sink. */
    public long costFor(Enchant enchant, int currentLevel) {
        return Math.round(enchant.baseCost() * Math.pow(enchant.costGrowth(), currentLevel));
    }

    /** "12.5%" or "+1.80x" — how an enchant's power reads in its tooltip. */
    public String describePower(Enchant enchant, int level) {
        double power = enchant.perLevel() * level;
        if (enchant.id().equals("TOKEN_GREED") || enchant.id().equals("MOMENTUM")) {
            return "+" + String.format("%.2f", power) + "x";
        }
        return String.format("%.2f", power * 100.0) + "%";
    }

    /**
     * Buys one level with Tokens. Returns false when it's locked, maxed or
     * unaffordable — the menu already knows which, so it reports it.
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

    /** The enchant's total effect at the player's level — 0 if not unlocked. */
    public double powerOf(PlayerData data, String enchantId) {
        Enchant enchant = get(enchantId);
        if (enchant == null) return 0.0;
        // Proc Chance lifts every enchant at once, so it multiplies the
        // total rather than adding levels — a flat level bonus would be
        // worth wildly different amounts to a 0.02/level enchant and a
        // 0.00004/level one.
        double proc = plugin.getSkillTreeManager()
                .multiplierOf(data, SkillNode.Effect.ENCHANT_PROC);
        return enchant.perLevel() * levelOf(data, enchantId) * proc;
    }

    /** The same figure the skill tree quotes, for one player. */
    public String describePower(PlayerData data, String enchantId) {
        Enchant enchant = get(enchantId);
        if (enchant == null) return "0%";
        double power = powerOf(data, enchantId);
        if (enchant.id().equals("TOKEN_GREED") || enchant.id().equals("MOMENTUM")) {
            return "+" + String.format("%.2f", power) + "x";
        }
        return String.format("%.2f", power * 100.0) + "%";
    }

    public boolean has(PlayerData data, String enchantId) {
        return levelOf(data, enchantId) > 0;
    }
}
