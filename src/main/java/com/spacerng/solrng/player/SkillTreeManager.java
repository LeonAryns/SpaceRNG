package com.spacerng.solrng.player;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Skill tree nodes, paid for with Coins (Vault). Rolled drops stay the
 * currency for /armor, potions and farming-hoe upgrades.
 *
 * Every stat a node grants is DERIVED from the node levels a player owns
 * rather than baked into a field at purchase time. That costs a small
 * sum over ~100 nodes whenever Luck is read, and buys the thing that
 * matters far more while the tree is still being balanced: retuning a
 * value in config.yml retunes it for everybody who already bought it,
 * instead of leaving the old number frozen into their save file.
 */
public class SkillTreeManager {

    /** Config sections that hold a tree. Adding one here adds a tree. */
    public static final List<String> TREES = List.of("skilltree", "farmtree");

    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();
    // Per tree: how many pages the menu has, what they're called, and
    // which slots are part of the silhouette on every page.
    private final Map<String, Integer> pageCounts = new LinkedHashMap<>();
    private final Map<String, List<String>> pageNames = new LinkedHashMap<>();
    private final Map<String, Set<Integer>> reserved = new LinkedHashMap<>();
    private final Logger logger;

    public SkillTreeManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Loads every tree in the config. Nodes from all trees live in one flat
     * map because a player's unlocks are one flat set — the tree id is just
     * which menu draws it. That keeps purchase, requirements and saved data
     * identical no matter how many trees exist.
     */
    public void load(FileConfiguration config) {
        nodes.clear();
        pageCounts.clear();
        pageNames.clear();
        reserved.clear();
        for (String tree : TREES) {
            loadTree(config, tree);
        }
        logger.info("[SolRNG] Loaded " + nodes.size() + " skill nodes across " + TREES.size() + " trees.");
    }

    private void loadTree(FileConfiguration config, String tree) {
        pageCounts.put(tree, Math.max(1, config.getInt(tree + ".pages", 2)));
        pageNames.put(tree, config.getStringList(tree + ".page-names"));

        Set<Integer> shape = new HashSet<>();
        for (String raw : config.getStringList(tree + ".reserved")) {
            int slot = parseSlot(raw);
            if (slot >= 0) shape.add(slot);
        }
        reserved.put(tree, shape);

        ConfigurationSection section = config.getConfigurationSection(tree + ".nodes");
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
                String icon = n.getString("icon", "RECOVERY_COMPASS");
                String target = n.getString("target", "");
                int interval = n.getInt("interval", 0);
                // Config pages are 1-indexed because that's how they're
                // described when the layout is being designed.
                int page = Math.max(0, n.getInt("page", 1) - 1);

                nodes.put(id, new SkillNode(id, display, moneyCost, requires, effect, value,
                        maxLevel, costGrowth, tree, parseSlot(n.getString("slot", "")), page,
                        icon, target, interval, n.getStringList("requires-all")));
            } catch (Exception ex) {
                logger.warning("[SolRNG] Skipped malformed skill node '" + id + "': " + ex.getMessage());
            }
        }
    }

    /** "5,6" is (column, row), both 1-indexed. -1 when unset or malformed. */
    private int parseSlot(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String[] parts = raw.split(",");
        if (parts.length != 2) return -1;
        try {
            return SkillNode.slotOf(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public int pageCount(String tree) {
        return pageCounts.getOrDefault(tree, 2);
    }

    /** "Foundations" etc., or an empty string when the page has no name. */
    public String pageName(String tree, int page) {
        List<String> names = pageNames.get(tree);
        if (names == null || page < 0 || page >= names.size()) return "";
        return names.get(page);
    }

    public Set<Integer> reservedSlots(String tree) {
        return reserved.getOrDefault(tree, Set.of());
    }

    /** Every node belonging to one tree, in config order. */
    public Map<String, SkillNode> getNodes(String tree) {
        Map<String, SkillNode> out = new LinkedHashMap<>();
        for (Map.Entry<String, SkillNode> entry : nodes.entrySet()) {
            if (entry.getValue().getTree().equals(tree)) out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    /** Every node on one page of one tree. */
    public List<SkillNode> getNodes(String tree, int page) {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            if (node.getTree().equals(tree) && node.getPage() == page) out.add(node);
        }
        return out;
    }

    public Map<String, SkillNode> getNodes() {
        return nodes;
    }

    public SkillNode get(String id) {
        return nodes.get(id);
    }

    // --------------------------------------------------------- derived stats

    /**
     * How many levels of a node the player has. A one-time unlock reads as
     * 1 or 0, so leveled and unleveled nodes can be summed the same way.
     */
    public int levelOf(PlayerData data, SkillNode node) {
        if (node == null) return 0;
        return node.isLeveled() ? Math.min(node.getMaxLevel(), data.getNodeLevel(node.getId()))
                                : (data.hasUnlocked(node.getId()) ? 1 : 0);
    }

    public int levelOf(PlayerData data, String nodeId) {
        return levelOf(data, nodes.get(nodeId));
    }

    /**
     * The total magnitude of one effect across every node that grants it —
     * the single place any bonus is answered from. Because it reads live
     * node levels, changing a value in config.yml immediately changes what
     * everyone who bought it is getting.
     */
    public double totalOf(PlayerData data, SkillNode.Effect effect) {
        double total = 0.0;
        for (SkillNode node : nodes.values()) {
            if (node.getEffect() != effect) continue;
            int level = levelOf(data, node);
            if (level > 0) total += node.getValue() * level;
        }
        return total;
    }

    /** {@code 1.0 + totalOf(...)}, for the effects that read as multipliers. */
    public double multiplierOf(PlayerData data, SkillNode.Effect effect) {
        return 1.0 + totalOf(data, effect);
    }

    /** Every owned node with a given effect — for the ones that aren't a simple sum. */
    public List<SkillNode> owned(PlayerData data, SkillNode.Effect effect) {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            if (node.getEffect() == effect && levelOf(data, node) > 0) out.add(node);
        }
        return out;
    }

    /**
     * The Luck multiplier a Supercharge node grants on this particular
     * roll, or 1.0 when none of them lands on it. Several can be owned at
     * once and the 1000th roll trips both the every-100 and the every-1000
     * one, so the strongest wins rather than the two stacking — a
     * 10x-times-100x roll would be a different order of event entirely.
     */
    public double superchargeFor(PlayerData data, long rollNumber) {
        double best = 1.0;
        for (SkillNode node : owned(data, SkillNode.Effect.SUPERCHARGE)) {
            int interval = node.getInterval();
            if (interval <= 0 || rollNumber % interval != 0) continue;
            best = Math.max(best, node.getValue());
        }
        return best;
    }

    /**
     * The same sum, but only across nodes pointing at one target — a crop
     * id, an enchant id. Lets one effect be repeated per thing it acts on
     * without needing an effect per thing.
     */
    public double totalOf(PlayerData data, SkillNode.Effect effect, String target) {
        if (target == null) return 0.0;
        double total = 0.0;
        for (SkillNode node : nodes.values()) {
            if (node.getEffect() != effect) continue;
            if (node.getTarget() == null || !node.getTarget().equalsIgnoreCase(target)) continue;
            int level = levelOf(data, node);
            if (level > 0) total += node.getValue() * level;
        }
        return total;
    }

    public double multiplierOf(PlayerData data, SkillNode.Effect effect, String target) {
        return 1.0 + totalOf(data, effect, target);
    }

    /** Turns "5,6"-style Luck/Speed sums into the number the menus show. */
    public double skillLuck(PlayerData data) {
        return totalOf(data, SkillNode.Effect.LUCK);
    }

    public double skillSpeed(PlayerData data) {
        return totalOf(data, SkillNode.Effect.ROLL_SPEED);
    }

    private Economy economy() {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    /** What this node's next level costs for this player. */
    public double priceFor(PlayerData data, SkillNode node) {
        return node.costAtLevel(node.isLeveled() ? data.getNodeLevel(node.getId()) : 0);
    }

    /** False when Vault/an economy plugin isn't installed — nothing is buyable then. */
    public boolean canAfford(Player player, PlayerData data, SkillNode node) {
        if (node.usesTokens()) {
            return data.getTokens() >= Math.round(priceFor(data, node));
        }
        Economy economy = economy();
        return economy != null && economy.getBalance(player) >= priceFor(data, node);
    }

    /**
     * Whether a node's prerequisite is satisfied. A leveled prerequisite
     * counts as met once a single level is in it, so branches open up as
     * soon as you've started investing rather than making you max it out.
     */
    public boolean requirementMet(PlayerData data, SkillNode node) {
        return missingRequirements(data, node).isEmpty();
    }

    /**
     * Which prerequisites this player still hasn't got. Returned rather
     * than a bare boolean so a locked node can name what's in the way —
     * "you need something else first" is the least useful thing a tree can
     * say.
     */
    public List<SkillNode> missingRequirements(PlayerData data, SkillNode node) {
        List<SkillNode> missing = new ArrayList<>();
        addIfMissing(data, node.getRequires(), missing);
        for (String id : node.getRequiresAll()) {
            addIfMissing(data, id, missing);
        }
        return missing;
    }

    private void addIfMissing(PlayerData data, String id, List<SkillNode> missing) {
        if (id == null || id.isBlank()) return;
        SkillNode parent = nodes.get(id);
        if (parent == null) {
            // A node id with no definition can only be checked as a flag.
            if (!data.hasUnlocked(id)) missing.add(null);
            return;
        }
        if (levelOf(data, parent) < 1 && !missing.contains(parent)) missing.add(parent);
    }

    /**
     * Attempts to purchase a node, paying with Coins. For a leveled node
     * this buys the NEXT level, whose price grows by the node's
     * cost-growth each time. Returns true on success.
     *
     * Nothing is written into a stat field here: the stats are read back
     * out of the node levels on demand. Only the effects that genuinely
     * are one-way switches (a flag, a crop, a farm multiplier) get applied.
     */
    public boolean purchase(Player player, PlayerData data, String nodeId) {
        SkillNode node = nodes.get(nodeId);
        if (node == null) return false;

        if (node.isLeveled()) {
            if (data.getNodeLevel(nodeId) >= node.getMaxLevel()) return false;
        } else if (data.hasUnlocked(nodeId)) {
            return false;
        }
        if (!requirementMet(data, node)) return false;

        double price = priceFor(data, node);
        if (node.usesTokens()) {
            if (!data.spendTokens(Math.round(price))) return false;
        } else {
            Economy economy = economy();
            if (economy == null || economy.getBalance(player) < price) return false;
            economy.withdrawPlayer(player, price);
        }

        if (node.isLeveled()) {
            data.setNodeLevel(nodeId, data.getNodeLevel(nodeId) + 1);
        } else {
            data.getUnlockedNodes().add(nodeId);
        }

        applySideEffects(data, node);
        return true;
    }

    /**
     * The handful of effects that aren't derived. These are one-way
     * switches on the player rather than magnitudes, so there's nothing to
     * recompute and no drift to worry about.
     */
    public void applySideEffects(PlayerData data, SkillNode node) {
        switch (node.getEffect()) {
            case AUTO_ROLL -> data.setAutoRollEnabled(true);
            case TOKEN_MULTIPLIER -> data.setFarmTokenMultiplier(data.getFarmTokenMultiplier() + node.getValue());
            case UNLOCK_CROP -> {
                if (node.getTarget() != null) data.getUnlockedCrops().add(node.getTarget().toUpperCase());
            }
            case UNLOCK_SHARDS -> data.setCropShardsUnlocked(true);
            default -> { /* derived stats and gate flags — nothing to store */ }
        }
    }
}
