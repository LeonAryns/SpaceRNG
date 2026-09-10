package com.spacerng.solrng.quest;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The starting guide: a single ordered run of quests that walks a new
 * player through every system in the plugin, one at a time, with the
 * current step permanently on a boss bar so there's never a moment where
 * you don't know what to do next.
 *
 * It's linear on purpose. A grid of optional objectives is a checklist; a
 * queue of one thing is a tutorial. Only the current step is shown, and it
 * advances the instant its condition is met - including retroactively, so
 * a player who already did something is never asked to do it twice.
 *
 * Progress is derived from live state rather than counted here, so the
 * guide can be reordered, extended or rewritten without migrating anybody.
 */
public class QuestManager {

    private final SolRNGPlugin plugin;
    private final List<Quest> quests = new ArrayList<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public QuestManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        quests.clear();
        List<Map<?, ?>> raw = config.getMapList("guide.quests");
        for (Map<?, ?> entry : raw) {
            try {
                quests.add(new Quest(
                        String.valueOf(entry.get("id")),
                        String.valueOf(entry.get("display")),
                        entry.get("hint") == null ? "" : String.valueOf(entry.get("hint")),
                        Quest.Goal.valueOf(String.valueOf(entry.get("goal")).toUpperCase()),
                        entry.get("target") == null ? null : String.valueOf(entry.get("target")),
                        entry.get("amount") == null ? 1L : Long.parseLong(String.valueOf(entry.get("amount"))),
                        entry.get("tokens") == null ? 0L : Long.parseLong(String.valueOf(entry.get("tokens"))),
                        entry.get("money") == null ? 0.0 : Double.parseDouble(String.valueOf(entry.get("money")))));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipped a malformed guide quest: " + entry);
            }
        }
        plugin.getLogger().info("Loaded " + quests.size() + " starting-guide quests.");
    }

    public List<Quest> getQuests() {
        return quests;
    }

    // --------------------------------------------------------- progress

    /** Where the player is on a quest's goal right now. */
    public long progress(Player player, PlayerData data, Quest quest) {
        return switch (quest.getGoal()) {
            case ROLLS -> data.getTotalRolls();
            case DISCOVERIES -> data.getDiscoveredItems().size();
            case SKILL_NODES -> data.getUnlockedNodes().size() + data.getNodeLevels().size();
            case HAS_NODE -> quest.getTarget() != null
                    && (data.hasUnlocked(quest.getTarget()) || data.getNodeLevel(quest.getTarget()) > 0) ? 1L : 0L;
            case TAG_EQUIPPED -> data.getEquippedTagItemKey() != null ? 1L : 0L;
            case BANKED_DROPS -> {
                long total = 0L;
                for (long amount : data.getDropBank().values()) total += amount;
                yield total;
            }
            case STARFORGE_TIER -> {
                var tier = plugin.getStarforgeManager().tierOf(data);
                yield tier == null ? 0L : tier.getOrder();
            }
            case ARMOR_PIECES -> data.getPurchasedArmorTiers().size();
            case CROPS_HARVESTED -> data.getCropsHarvested();
            case NOVA_TIER -> data.getNovaBestTier();
            case MILESTONES_CLAIMED -> data.getClaimedMilestones().size();
            case LEVEL -> data.getLevel();
            case PRESTIGE -> data.getPrestige();
        };
    }

    public boolean isComplete(Player player, PlayerData data, Quest quest) {
        return progress(player, data, quest) >= quest.getAmount();
    }

    /** The quest the player is on, or null once the guide is finished. */
    public Quest current(Player player, PlayerData data) {
        for (Quest quest : quests) {
            if (!data.hasCompletedQuest(quest.getId())) return quest;
        }
        return null;
    }

    public int completedCount(PlayerData data) {
        int done = 0;
        for (Quest quest : quests) {
            if (data.hasCompletedQuest(quest.getId())) done++;
        }
        return done;
    }

    // ------------------------------------------------------------ ticking

    /**
     * Advances the guide as far as it can go. Loops rather than checking
     * once, so a player who logs in having already met the next three
     * conditions clears all three instead of one per tick.
     */
    public void check(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        Quest quest;
        int guard = 0;
        while ((quest = current(player, data)) != null && guard++ < quests.size()) {
            if (!isComplete(player, data, quest)) break;
            data.markQuestCompleted(quest.getId());
            reward(player, data, quest);
        }
        giveStepGifts(player, data);
        updateBar(player, data);
    }

    /**
     * Hands over what a step needs the moment the player REACHES it.
     *
     * The Nova Core used to arrive when the Nova step was finished, which
     * is backwards: the step is "forge a tier", a forge uses up a Nova
     * Core, and a new player has none. It now arrives with the step, once.
     * The gift is remembered as a completed pseudo-quest, "gift:<id>", which
     * nothing that counts guide progress ever looks at.
     */
    private void giveStepGifts(Player player, PlayerData data) {
        Quest quest = current(player, data);
        if (quest == null) return;
        int cores = plugin.getConfig().getInt("guide.free-nova." + quest.getId(), 0);
        String marker = "gift:" + quest.getId();
        if (cores <= 0 || data.hasCompletedQuest(marker)) return;
        var core = plugin.getConsumableManager().get("nova_core");
        if (core == null) return;

        data.markQuestCompleted(marker);
        plugin.getConsumableManager().give(player, core, cores);
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "+" + cores + " Nova Core"
                + (cores == 1 ? "" : "s") + ChatColor.RESET + ChatColor.GRAY + "  for this step of the guide.");
        plugin.getNovaCoreManager().explainCores(player);
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }

    public void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            check(player);
        }
    }

    /**
     * A step pays nothing at all. The whole guide pays one voucher.
     *
     * Paying per step turned a tutorial into a farming route people
     * rushed for the reward; paying only at the end makes finishing it
     * the point.
     */
    /**
     * Hands over one drop of a named rarity.
     *
     * The Basic Starforge costs 25 Common and 1 Uncommon, and an Uncommon
     * is rare enough early that a new player can stall there with nothing
     * to do about it. One guaranteed Uncommon part way through the guide
     * turns that wall back into a step.
     */
    private void giveGuideDrop(Player player, String rarityName) {
        com.spacerng.solrng.rarity.Rarity rarity;
        try {
            rarity = com.spacerng.solrng.rarity.Rarity.valueOf(rarityName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return;
        }

        java.util.List<com.spacerng.solrng.rarity.RollableItem> pool = new java.util.ArrayList<>();
        for (var item : plugin.getRarityManager().getItems()) {
            if (item.getRarity() == rarity) pool.add(item);
        }
        if (pool.isEmpty()) return;

        var drop = pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
        player.getInventory().addItem(plugin.getRollListener().buildTaggedItem(drop));
        player.sendMessage(ChatColor.GRAY + "  A guaranteed "
                + plugin.getRarityManager().style(rarity, rarity.displayName())
                + ChatColor.GRAY + " drop, for your first Starforge.");
    }

    private void reward(Player player, PlayerData data, Quest quest) {

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ Guide step done  "
                + ChatColor.RESET + ChatColor.WHITE + quest.getDisplay());


        String guaranteed = plugin.getConfig()
                .getString("guide.guaranteed-drop." + quest.getId(), "");
        if (!guaranteed.isEmpty()) {
            giveGuideDrop(player, guaranteed);
        }

        Quest next = current(player, data);
        if (next == null) {
            // The whole guide is done. One free skill node, which is worth
            // most if the player saves it for something expensive.
            int free = plugin.getConfig().getInt("guide.completion-free-skills", 1);
            if (free > 0) {
                data.addFreeSkills(free);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD
                        + "+" + free + " Skilltree Unlock Voucher" + (free == 1 ? "" : "s")
                        + ChatColor.RESET + ChatColor.GRAY
                        + "  Spend it on anything in /skilltree, whatever it costs.");
            }
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "You've finished the starting guide!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            player.sendMessage(ChatColor.GRAY + "Next: " + ChatColor.YELLOW + next.getDisplay());
            if (!next.getHint().isEmpty()) {
                // Not dark grey. The hint is the one line that tells a new
                // player what to actually do, and dark grey on the default
                // chat background is close to unreadable.
                player.sendMessage(ChatColor.GRAY + "  " + ChatColor.ITALIC + next.getHint());
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        }
        player.sendMessage("");
        plugin.getScoreboardManager().update(player);
    }

    // ---------------------------------------------------------- boss bar

    public void show(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        updateBar(player, data);
    }

    private void updateBar(Player player, PlayerData data) {
        Quest quest = current(player, data);

        // The guide's bar disappears the moment the guide is done - a
        // permanent empty bar is just clutter for a veteran.
        if (quest == null) {
            hide(player.getUniqueId());
            return;
        }

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.createBossBar(barKey(uuid), "", BarColor.YELLOW, BarStyle.SEGMENTED_10));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        long progress = Math.min(progress(player, data, quest), quest.getAmount());
        int step = completedCount(data) + 1;

        StringBuilder title = new StringBuilder();
        title.append(ChatColor.YELLOW).append(ChatColor.BOLD).append("GUIDE ")
                .append(ChatColor.DARK_GRAY).append(step).append("/").append(quests.size())
                .append(ChatColor.GRAY).append("  ")
                .append(ChatColor.WHITE).append(quest.getDisplay());
        if (quest.getAmount() > 1) {
            title.append(ChatColor.GRAY).append("  ")
                    .append(ChatColor.AQUA).append(String.format("%,d", progress))
                    .append(ChatColor.GRAY).append("/")
                    .append(ChatColor.AQUA).append(String.format("%,d", quest.getAmount()));
        }

        bar.setTitle(title.toString());
        bar.setProgress(Math.max(0.0, Math.min(1.0, (double) progress / quest.getAmount())));
        bar.setVisible(true);
    }

    /**
     * A periodic chat line for anyone still on the guide. The boss bar is
     * easy to stop seeing after a while; one line every couple of minutes
     * puts the next step back in front of them without nagging.
     */
    public void nudgeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            Quest quest = current(player, data);
            if (quest == null) continue;

            player.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + ChatColor.BOLD + "GUIDE"
                    + ChatColor.RESET + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY + "Next: "
                    + ChatColor.WHITE + quest.getDisplay()
                    + ChatColor.DARK_GRAY + "  -  " + ChatColor.GRAY + "see " + ChatColor.YELLOW + "/guide");
        }
    }

    /**
     * The bar's key.
     *
     * Keyed rather than anonymous on purpose. Bukkit.createBossBar(String,
     * ...) makes a bar the server never records, so once the plugin
     * instance that made it is gone the bar is unreachable - it stays on
     * everyone's screen until they relog, and the next instance cheerfully
     * draws a second one beside it. A keyed bar can be found again from a
     * cold start and cleared.
     */
    private static org.bukkit.NamespacedKey barKey(UUID uuid) {
        return com.spacerng.solrng.SolRNGPlugin.key("bar_guide_" + uuid);
    }

    public void hide(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
        Bukkit.removeBossBar(barKey(uuid));
    }

    public void removeAll() {
        for (UUID uuid : java.util.List.copyOf(bars.keySet())) {
            hide(uuid);
        }
    }
}
