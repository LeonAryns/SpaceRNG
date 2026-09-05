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
 * advances the instant its condition is met — including retroactively, so
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
                plugin.getLogger().warning("[SolRNG] Skipped a malformed guide quest: " + entry);
            }
        }
        plugin.getLogger().info("[SolRNG] Loaded " + quests.size() + " starting-guide quests.");
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
        updateBar(player, data);
    }

    public void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            check(player);
        }
    }

    private void reward(Player player, PlayerData data, Quest quest) {
        if (quest.getRewardTokens() > 0) data.addTokens(quest.getRewardTokens());
        if (quest.getRewardMoney() > 0) {
            var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) {
                registration.getProvider().depositPlayer(player, quest.getRewardMoney());
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ GUIDE COMPLETE "
                + ChatColor.RESET + ChatColor.WHITE + quest.getDisplay());
        StringBuilder reward = new StringBuilder();
        if (quest.getRewardTokens() > 0) {
            reward.append(ChatColor.YELLOW).append(String.format("%,d", quest.getRewardTokens())).append(" Tokens");
        }
        if (quest.getRewardMoney() > 0) {
            if (reward.length() > 0) reward.append(ChatColor.GRAY).append(", ");
            reward.append(ChatColor.DARK_GREEN).append("$").append(String.format("%,.0f", quest.getRewardMoney()));
        }
        if (reward.length() > 0) {
            player.sendMessage(ChatColor.GRAY + "Reward: " + reward);
        }

        Quest next = current(player, data);
        if (next == null) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "You've finished the starting guide!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            player.sendMessage(ChatColor.GRAY + "Next: " + ChatColor.YELLOW + next.getDisplay());
            if (!next.getHint().isEmpty()) {
                player.sendMessage(ChatColor.DARK_GRAY + "  " + next.getHint());
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

        // The guide's bar disappears the moment the guide is done — a
        // permanent empty bar is just clutter for a veteran.
        if (quest == null) {
            hide(player.getUniqueId());
            return;
        }

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10));
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
                    + ChatColor.DARK_GRAY + "  —  " + ChatColor.GRAY + "see " + ChatColor.YELLOW + "/guide");
        }
    }

    public void hide(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void removeAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }
}
