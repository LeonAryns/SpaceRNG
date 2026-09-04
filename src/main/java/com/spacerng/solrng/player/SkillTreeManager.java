package com.spacerng.solrng.player;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Skill tree nodes, paid for with Money (Vault). Rolled drops stay the
 * currency for /armor, potions and farming-hoe upgrades.
 */
public class SkillTreeManager {

    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();
    private final Logger logger;

    public SkillTreeManager(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        nodes.clear();
        ConfigurationSection section = config.getConfigurationSection("skilltree.nodes");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection n = section.getConfigurationSection(id);
            if (n == null) continue;
            try {
                String display = n.getString("display", id);
                String requires = n.getString("requires", "");
                SkillNode.Effect effect = SkillNode.Effect.valueOf(n.getString("effect", "LUCK").toUpperCase());
                double value = n.getDouble("value", 0.0);
                int maxLevel = n.getInt("max-level", 1);
                double moneyCost = n.getDouble("cost-money", 0.0);

                double costGrowth = n.getDouble("cost-growth", 1.0);

                nodes.put(id, new SkillNode(id, display, moneyCost, requires, effect, value, maxLevel, costGrowth));
            } catch (Exception ex) {
                logger.warning("[SolRNG] Skipped malformed skill node '" + id + "': " + ex.getMessage());
            }
        }
        logger.info("[SolRNG] Loaded " + nodes.size() + " skill tree nodes.");
    }

    public Map<String, SkillNode> getNodes() {
        return nodes;
    }

    public SkillNode get(String id) {
        return nodes.get(id);
    }

    private Economy economy() {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    /** What this node's next level costs for this player. */
    public double priceFor(PlayerData data, SkillNode node) {
        return node.costAtLevel(node.getMaxLevel() > 1 ? data.getNodeLevel(node.getId()) : 0);
    }

    /** False when Vault/an economy plugin isn't installed — nothing is buyable then. */
    public boolean canAfford(Player player, PlayerData data, SkillNode node) {
        Economy economy = economy();
        return economy != null && economy.getBalance(player) >= priceFor(data, node);
    }

    /**
     * Whether a node's prerequisite is satisfied. A leveled prerequisite
     * counts as met once a single level is in it, so branches open up as
     * soon as you've started investing rather than making you max it out.
     */
    public boolean requirementMet(PlayerData data, SkillNode node) {
        String requires = node.getRequires();
        if (requires == null) return true;
        SkillNode parent = nodes.get(requires);
        if (parent == null) return data.hasUnlocked(requires);
        return parent.getMaxLevel() > 1
                ? data.getNodeLevel(requires) >= 1
                : data.hasUnlocked(requires);
    }

    /**
     * Attempts to purchase a node, paying with Money. For a leveled node
     * (maxLevel > 1) this buys the NEXT level, whose price grows by the
     * node's cost-growth each time. Returns true on success.
     */
    public boolean purchase(Player player, PlayerData data, String nodeId) {
        SkillNode node = nodes.get(nodeId);
        if (node == null) return false;

        if (node.getMaxLevel() > 1) {
            if (data.getNodeLevel(nodeId) >= node.getMaxLevel()) return false;
        } else if (data.hasUnlocked(nodeId)) {
            return false;
        }
        if (!requirementMet(data, node)) return false;

        double price = priceFor(data, node);
        Economy economy = economy();
        if (economy == null || economy.getBalance(player) < price) return false;
        economy.withdrawPlayer(player, price);

        if (node.getMaxLevel() > 1) {
            data.setNodeLevel(nodeId, data.getNodeLevel(nodeId) + 1);
        } else {
            data.getUnlockedNodes().add(nodeId);
        }

        switch (node.getEffect()) {
            case LUCK -> data.addBonusLuck(node.getValue());
            case AUTO_ROLL -> data.setAutoRollEnabled(true);
            case UNLOCK_AUTO_CONVERT, UNLOCK_FARMING, UNLOCK_ARMOR, UNLOCK_POTION, UNLOCK_SHINY -> { /* gate flags only — checked via data.hasUnlocked() elsewhere */ }
            case ROLL_SPEED -> data.setRollSpeedMultiplier(data.getRollSpeedMultiplier() + node.getValue());
            case BONUS_ROLL_CHANCE -> data.addBonusRollChance(node.getValue());
        }
        return true;
    }
}
