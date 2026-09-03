package com.spacerng.solrng.player;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

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
                long cost = n.getLong("cost", 0L);
                String requires = n.getString("requires", "");
                SkillNode.Effect effect = SkillNode.Effect.valueOf(n.getString("effect", "LUCK").toUpperCase());
                double value = n.getDouble("value", 0.0);
                nodes.put(id, new SkillNode(id, display, cost, requires, effect, value));
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

    public boolean isUnlockable(PlayerData data, SkillNode node) {
        if (data.hasUnlocked(node.getId())) return false;
        if (node.getRequires() != null && !data.hasUnlocked(node.getRequires())) return false;
        return data.getPoints() >= node.getCost();
    }

    /**
     * Attempts to purchase a node. Returns true on success.
     * Applies the node's effect directly to the player's data.
     */
    public boolean purchase(PlayerData data, String nodeId) {
        SkillNode node = nodes.get(nodeId);
        if (node == null) return false;
        if (data.hasUnlocked(nodeId)) return false;
        if (node.getRequires() != null && !data.hasUnlocked(node.getRequires())) return false;
        if (!data.spendPoints(node.getCost())) return false;

        data.getUnlockedNodes().add(nodeId);
        switch (node.getEffect()) {
            case LUCK -> data.addBonusLuck(node.getValue());
            case AUTO_ROLL -> data.setAutoRollIntervalSeconds((int) node.getValue());
            case UNLOCK_AUTO_CONVERT -> { /* just gates the /convert auto-toggle UI, nothing to set here */ }
        }
        return true;
    }
}
