package com.spacerng.solrng.player;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
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
                String requires = n.getString("requires", "");
                SkillNode.Effect effect = SkillNode.Effect.valueOf(n.getString("effect", "LUCK").toUpperCase());
                double value = n.getDouble("value", 0.0);
                int maxLevel = n.getInt("max-level", 1);

                Map<Rarity, Long> costs = new EnumMap<>(Rarity.class);
                ConfigurationSection costsSection = n.getConfigurationSection("costs");
                if (costsSection != null) {
                    for (String rarityKey : costsSection.getKeys(false)) {
                        Rarity rarity = Rarity.valueOf(rarityKey.toUpperCase());
                        costs.put(rarity, costsSection.getLong(rarityKey));
                    }
                }

                nodes.put(id, new SkillNode(id, display, costs, requires, effect, value, maxLevel));
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

    public boolean canAfford(Player player, SkillNode node, NamespacedKey rarityKey) {
        for (Map.Entry<Rarity, Long> cost : node.getCosts().entrySet()) {
            if (countHeld(player, rarityKey, cost.getKey()) < cost.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean isUnlockable(Player player, PlayerData data, SkillNode node, NamespacedKey rarityKey) {
        if (data.hasUnlocked(node.getId())) return false;
        if (node.getRequires() != null && !data.hasUnlocked(node.getRequires())) return false;
        return canAfford(player, node, rarityKey);
    }

    /**
     * Attempts to purchase a node, paying with rolled drops taken straight
     * out of the player's inventory. For a leveled node (maxLevel > 1) this
     * buys the NEXT level (same cost each time) instead of a one-time
     * unlock. Returns true on success.
     */
    public boolean purchase(Player player, PlayerData data, String nodeId, NamespacedKey rarityKey) {
        SkillNode node = nodes.get(nodeId);
        if (node == null) return false;

        boolean firstPurchase = node.getMaxLevel() > 1
                ? data.getNodeLevel(nodeId) == 0
                : !data.hasUnlocked(nodeId);
        if (node.getMaxLevel() > 1) {
            if (data.getNodeLevel(nodeId) >= node.getMaxLevel()) return false;
        } else if (data.hasUnlocked(nodeId)) {
            return false;
        }
        if (firstPurchase && node.getRequires() != null && !data.hasUnlocked(node.getRequires())) return false;
        if (!canAfford(player, node, rarityKey)) return false;

        for (Map.Entry<Rarity, Long> cost : node.getCosts().entrySet()) {
            consume(player, rarityKey, cost.getKey(), cost.getValue());
            data.addConverted(cost.getKey(), cost.getValue());
        }

        if (node.getMaxLevel() > 1) {
            data.setNodeLevel(nodeId, data.getNodeLevel(nodeId) + 1);
        } else {
            data.getUnlockedNodes().add(nodeId);
        }

        switch (node.getEffect()) {
            case LUCK -> data.addBonusLuck(node.getValue());
            case AUTO_ROLL -> data.setAutoRollIntervalSeconds((int) node.getValue());
            case UNLOCK_AUTO_CONVERT, UNLOCK_FARMING, UNLOCK_ARMOR, UNLOCK_POTION -> { /* gate flags only — checked via data.hasUnlocked() elsewhere */ }
            case ROLL_SPEED -> data.setRollSpeedMultiplier(data.getRollSpeedMultiplier() + node.getValue());
            case BONUS_ROLL_CHANCE -> data.addBonusRollChance(node.getValue());
        }
        return true;
    }

    private long countHeld(Player player, NamespacedKey rarityKey, Rarity rarity) {
        long total = 0L;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String rarityName = stack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (rarity.name().equals(rarityName)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void consume(Player player, NamespacedKey rarityKey, Rarity rarity, long amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        long remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getItemMeta() == null) continue;
            String rarityName = stack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (!rarity.name().equals(rarityName)) continue;

            long take = Math.min(remaining, stack.getAmount());
            stack.setAmount((int) (stack.getAmount() - take));
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
    }
}
