package com.spacerng.solrng.milestone;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-run goals across four tracks. Progress is read from what the plugin
 * already knows rather than being counted separately wherever possible —
 * prestige and index size are already on PlayerData, and playtime comes
 * straight from the vanilla statistic, so none of it can drift out of sync
 * with reality or be lost if a save is rolled back.
 *
 * Only crops harvested needed a new counter, since nothing was tracking it.
 *
 * Tiers are checked on a timer rather than being hooked into every place a
 * value can change; a milestone landing a second late is invisible, and one
 * check covers every track at once.
 */
public class MilestoneManager {

    public static final String PRESTIGE = "prestige";
    public static final String RARITY = "rarity";
    public static final String FARMING = "farming";
    public static final String PLAYTIME = "playtime";

    private final SolRNGPlugin plugin;
    private final Map<String, MilestoneTrack> tracks = new LinkedHashMap<>();

    public MilestoneManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        tracks.clear();
        ConfigurationSection section = config.getConfigurationSection("milestones.tracks");
        if (section == null) {
            plugin.getLogger().warning("[SolRNG] No milestones.tracks configured.");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(id);
            if (t == null) continue;

            Material icon = Material.matchMaterial(t.getString("icon", "PAPER"));
            if (icon == null) icon = Material.PAPER;

            List<MilestoneTrack.Tier> tiers = new ArrayList<>();
            List<Map<?, ?>> raw = t.getMapList("tiers");
            int index = 0;
            for (Map<?, ?> entry : raw) {
                try {
                    long threshold = Long.parseLong(String.valueOf(entry.get("at")));
                    long tokens = entry.get("tokens") == null ? 0L : Long.parseLong(String.valueOf(entry.get("tokens")));
                    long shards = entry.get("shards") == null ? 0L : Long.parseLong(String.valueOf(entry.get("shards")));
                    double money = entry.get("money") == null ? 0.0 : Double.parseDouble(String.valueOf(entry.get("money")));
                    tiers.add(new MilestoneTrack.Tier(index++, threshold, tokens, shards, money));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("[SolRNG] Skipped a malformed milestone tier in '" + id + "': " + entry);
                }
            }

            tracks.put(id, new MilestoneTrack(id,
                    t.getString("display", id),
                    icon,
                    t.getString("unit", ""),
                    t.getString("description", ""),
                    tiers));
        }
        plugin.getLogger().info("[SolRNG] Loaded " + tracks.size() + " milestone tracks.");
    }

    public Map<String, MilestoneTrack> getTracks() {
        return tracks;
    }

    public MilestoneTrack get(String id) {
        return tracks.get(id);
    }

    /**
     * The player's current number on a track. Everything except farming is
     * derived from existing state, so it stays correct no matter what else
     * happens.
     */
    public long progress(Player player, PlayerData data, String trackId) {
        return switch (trackId) {
            case PRESTIGE -> data.getPrestige();
            case RARITY -> data.getDiscoveredItems().size();
            case FARMING -> data.getCropsHarvested();
            // The vanilla statistic counts ticks, not minutes, despite the name.
            case PLAYTIME -> player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72_000L;
            default -> 0L;
        };
    }

    /**
     * Awards anything newly reached. Safe to call as often as you like —
     * a tier already in claimedMilestones is skipped, so nothing double-pays.
     */
    public void check(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        for (MilestoneTrack track : tracks.values()) {
            long progress = progress(player, data, track.getId());
            for (MilestoneTrack.Tier tier : track.getTiers()) {
                if (progress < tier.threshold()) break; // tiers are ascending
                String key = track.keyFor(tier);
                if (data.hasClaimedMilestone(key)) continue;

                data.markMilestoneClaimed(key);
                payOut(player, data, tier);
                announce(player, track, tier);
            }
        }
    }

    public void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            check(player);
        }
    }

    private void payOut(Player player, PlayerData data, MilestoneTrack.Tier tier) {
        if (tier.tokens() > 0) data.addTokens(tier.tokens());
        if (tier.shards() > 0) data.addShards(tier.shards());
        if (tier.money() > 0) {
            var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) {
                registration.getProvider().depositPlayer(player, tier.money());
            }
        }
        plugin.getScoreboardManager().update(player);
    }

    private void announce(Player player, MilestoneTrack track, MilestoneTrack.Tier tier) {
        String header = ChatColor.GOLD + "" + ChatColor.BOLD + "★ MILESTONE ★";
        String line = ChatColor.YELLOW + track.getDisplay() + ChatColor.GRAY + " » "
                + ChatColor.WHITE + String.format("%,d", tier.threshold()) + " " + track.getUnit();

        player.sendMessage("");
        player.sendMessage(header);
        player.sendMessage(line);

        String reward = rewardText(tier);
        if (!reward.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Reward: " + reward);
        }
        player.sendMessage(ChatColor.GRAY + "See them all with " + ChatColor.YELLOW + "/milestones");
        player.sendMessage("");

        Component title = LegacyComponentSerializer.legacySection().deserialize(header);
        Component subtitle = LegacyComponentSerializer.legacySection().deserialize(line);
        player.showTitle(net.kyori.adventure.title.Title.title(title, subtitle,
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(200),
                        java.time.Duration.ofMillis(1800),
                        java.time.Duration.ofMillis(400))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    /** "1,000 Tokens, 5 Shards" — blank when a tier pays nothing. */
    public String rewardText(MilestoneTrack.Tier tier) {
        List<String> parts = new ArrayList<>();
        if (tier.tokens() > 0) {
            parts.add(ChatColor.YELLOW + String.format("%,d", tier.tokens()) + " Tokens");
        }
        if (tier.shards() > 0) {
            parts.add(ChatColor.AQUA + String.format("%,d", tier.shards()) + " Shards");
        }
        if (tier.money() > 0) {
            parts.add(ChatColor.DARK_GREEN + "$" + String.format("%,.0f", tier.money()));
        }
        return String.join(ChatColor.GRAY + ", ", parts);
    }
}
