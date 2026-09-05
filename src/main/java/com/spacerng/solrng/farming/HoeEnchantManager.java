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
 * An enchant is unlocked by a node in the farming tree and then LEVELLED by
 * further nodes, so the hoe itself never needs an inventory of upgrade
 * items — its power is entirely a read of what the player has bought. That
 * keeps a bound, undroppable hoe honest: there's nothing to lose, trade or
 * duplicate, and a wipe of the tree wipes the hoe with it.
 *
 * Every enchant's numbers live in config. What each one DOES is applied in
 * FarmPlotManager at harvest time, keyed off the ids below.
 */
public class HoeEnchantManager {

    /** One enchant definition. Level comes from the player's skill nodes. */
    public record Enchant(String id, String display, String description, int maxLevel,
                          double perLevel, String colour) {

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
                    Math.max(1, e.getInt("max-level", 5)),
                    e.getDouble("per-level", 0.0),
                    colourOf(e.getString("color", "&d"))));
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
     * A player's level in an enchant, read straight off their farming tree.
     * UNLOCK_ENCHANT grants level 1; every rank of an ENCHANT_POWER node
     * pointing at the same enchant adds another.
     *
     * Derived rather than stored, so respeccing or an admin wipe can never
     * leave a hoe carrying an enchant the tree no longer justifies.
     */
    public int levelOf(PlayerData data, String enchantId) {
        Enchant enchant = get(enchantId);
        if (enchant == null) return 0;

        int level = 0;
        for (SkillNode node : plugin.getSkillTreeManager().getNodes("farmtree").values()) {
            if (node.getTarget() == null || !node.getTarget().equalsIgnoreCase(enchantId)) continue;

            if (node.getEffect() == SkillNode.Effect.UNLOCK_ENCHANT && data.hasUnlocked(node.getId())) {
                level += 1;
            } else if (node.getEffect() == SkillNode.Effect.ENCHANT_POWER) {
                level += node.getMaxLevel() > 1
                        ? data.getNodeLevel(node.getId())
                        : (data.hasUnlocked(node.getId()) ? 1 : 0);
            }
        }
        return Math.min(level, enchant.maxLevel());
    }

    /** The enchant's total effect at the player's level — 0 if not unlocked. */
    public double powerOf(PlayerData data, String enchantId) {
        Enchant enchant = get(enchantId);
        if (enchant == null) return 0.0;
        return enchant.perLevel() * levelOf(data, enchantId);
    }

    public boolean has(PlayerData data, String enchantId) {
        return levelOf(data, enchantId) > 0;
    }
}
