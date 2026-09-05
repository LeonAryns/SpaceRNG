package com.spacerng.solrng.daily;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The daily streak: a ladder of days you climb by logging in, with no
 * checkpoints — miss a day and you start again from day one.
 *
 * Days are counted as calendar days in a configured timezone, not as
 * 24-hour windows since the last claim. A rolling window punishes people
 * for playing slightly earlier each day until they eventually "miss" one
 * they were awake for; a calendar day matches what a player means when
 * they say "I played yesterday".
 */
public class DailyManager {

    /** One rung of the streak. */
    public record Day(int day, long tokens, long shards, double money, long credits, String note) {
    }

    private final SolRNGPlugin plugin;
    private final List<Day> days = new ArrayList<>();
    private ZoneId zone = ZoneId.systemDefault();

    public DailyManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        days.clear();
        try {
            zone = ZoneId.of(config.getString("daily.timezone", "UTC"));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[SolRNG] Bad daily.timezone, falling back to UTC.");
            zone = ZoneId.of("UTC");
        }

        int number = 1;
        for (Map<?, ?> entry : config.getMapList("daily.days")) {
            try {
                days.add(new Day(number++,
                        entry.get("tokens") == null ? 0L : Long.parseLong(String.valueOf(entry.get("tokens"))),
                        entry.get("shards") == null ? 0L : Long.parseLong(String.valueOf(entry.get("shards"))),
                        entry.get("money") == null ? 0.0 : Double.parseDouble(String.valueOf(entry.get("money"))),
                        entry.get("credits") == null ? 0L : Long.parseLong(String.valueOf(entry.get("credits"))),
                        entry.get("note") == null ? "" : String.valueOf(entry.get("note"))));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[SolRNG] Skipped a malformed daily reward: " + entry);
            }
        }
        plugin.getLogger().info("[SolRNG] Loaded a " + days.size() + "-day daily streak.");
    }

    public List<Day> getDays() {
        return days;
    }

    public int length() {
        return days.size();
    }

    private long today() {
        return LocalDate.now(zone).toEpochDay();
    }

    /**
     * Brings a stale streak up to date. Called before anything reads it, so
     * a streak broken while the player was offline is already reset by the
     * time they see the menu rather than resetting under their cursor.
     */
    public void refresh(PlayerData data) {
        if (data.getDailyLastClaimDay() <= 0) return;

        long missed = today() - data.getDailyLastClaimDay();
        if (missed > 1) {
            data.setDailyStreak(0);
        }
    }

    public boolean canClaim(PlayerData data) {
        refresh(data);
        return data.getDailyLastClaimDay() != today();
    }

    /** The day number a claim right now would award. */
    public int nextDay(PlayerData data) {
        refresh(data);
        return Math.min(days.size(), data.getDailyStreak() + 1);
    }

    /** How long until the next claim opens, as "6h 12m". */
    public String timeUntilNext(PlayerData data) {
        if (canClaim(data)) return "now";
        var now = java.time.ZonedDateTime.now(zone);
        var midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        long minutes = ChronoUnit.MINUTES.between(now, midnight);
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    public boolean claim(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (days.isEmpty() || !canClaim(data)) return false;

        int day = nextDay(data);
        Day reward = days.get(day - 1);

        data.setDailyStreak(day);
        data.setDailyLastClaimDay(today());
        data.addDailyTotalClaims(1L);

        // Daily Devotion scales what the streak pays. Credits are left out
        // on purpose: they're the paid-store currency, and a skill that
        // multiplied them would make them earnable through gameplay.
        double bonus = plugin.getSkillTreeManager()
                .multiplierOf(data, com.spacerng.solrng.player.SkillNode.Effect.DAILY_BONUS);
        if (reward.tokens() > 0) data.addTokens(Math.round(reward.tokens() * bonus));
        if (reward.shards() > 0) data.addShards(Math.round(reward.shards() * bonus));
        if (reward.credits() > 0) data.addPoints(reward.credits());
        if (reward.money() > 0) {
            var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) {
                registration.getProvider().depositPlayer(player, reward.money() * bonus);
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "DAY " + day + " CLAIMED"
                + ChatColor.RESET + ChatColor.GRAY + "  streak: " + ChatColor.WHITE + day
                + ChatColor.DARK_GRAY + "/" + days.size());
        String text = rewardText(reward);
        if (!text.isEmpty()) player.sendMessage(ChatColor.GRAY + "Reward: " + text);
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        plugin.getScoreboardManager().update(player);
        return true;
    }

    /** "5,000 Tokens, 2 Gems" — blank when a day pays nothing. */
    public String rewardText(Day day) {
        List<String> parts = new ArrayList<>();
        if (day.tokens() > 0) {
            parts.add(ChatColor.YELLOW + String.format("%,d", day.tokens()) + " Tokens");
        }
        if (day.shards() > 0) {
            parts.add(ChatColor.AQUA + String.format("%,d", day.shards()) + " Gems");
        }
        if (day.money() > 0) {
            parts.add(ChatColor.DARK_GREEN + "$" + String.format("%,.0f", day.money()));
        }
        if (day.credits() > 0) {
            parts.add(ChatColor.LIGHT_PURPLE + String.format("%,d", day.credits()) + " Credits");
        }
        return String.join(ChatColor.GRAY + ", ", parts);
    }
}
