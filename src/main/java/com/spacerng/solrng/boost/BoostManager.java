package com.spacerng.solrng.boost;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * The global Luck boost. One boost exists for the whole server at a time,
 * bought with store Credits: the first purchase turns it on at 2x, and
 * buying again while it's live doubles it — 2x, 4x, 8x — each step costing
 * more than the last and refreshing the clock.
 *
 * Global on purpose: it's the difference between a store item that helps
 * one person and one the server cheers for. It also makes the escalating
 * price honest, since everyone benefits from someone else paying it.
 *
 * Nothing here is persisted. A boost is a fifteen-minute event; carrying a
 * half-expired one through a restart would be worse than dropping it, and
 * dropping it is the behaviour players can actually predict.
 */
public class BoostManager {

    private final SolRNGPlugin plugin;

    private int level = 0;              // 0 = no boost, 1 = 2x, 2 = 4x, ...
    private long expiresAtMillis = 0L;
    private String boughtBy = null;

    private int durationSeconds = 900;  // 15 minutes
    private long baseCost = 250L;
    private double costGrowth = 2.5;
    private int maxLevel = 5;

    public BoostManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        durationSeconds = config.getInt("boost.duration-seconds", 900);
        baseCost = config.getLong("boost.base-cost-credits", 250L);
        costGrowth = config.getDouble("boost.cost-growth", 2.5);
        maxLevel = config.getInt("boost.max-level", 5);
    }

    /** Expired boosts reset the ladder, so the next buyer starts at 2x again. */
    private void expireIfDue() {
        if (level > 0 && System.currentTimeMillis() >= expiresAtMillis) {
            level = 0;
            boughtBy = null;
            Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                    ChatColor.GRAY + "The global Luck boost has ended."));
        }
    }

    public boolean isActive() {
        expireIfDue();
        return level > 0;
    }

    /** 1.0 when nothing is running, otherwise 2^level. */
    public double multiplier() {
        expireIfDue();
        return level <= 0 ? 1.0 : Math.pow(2, level);
    }

    public int getLevel() {
        expireIfDue();
        return level;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public String getBoughtBy() {
        return boughtBy;
    }

    public long secondsLeft() {
        expireIfDue();
        if (level <= 0) return 0L;
        return Math.max(0L, (expiresAtMillis - System.currentTimeMillis()) / 1000L);
    }

    /** "12:04", or "--:--" when nothing is running. */
    public String timeLeftText() {
        long seconds = secondsLeft();
        if (seconds <= 0) return "--:--";
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** What the NEXT purchase costs, in Credits. */
    public long nextCost() {
        return Math.round(baseCost * Math.pow(costGrowth, getLevel()));
    }

    /** The multiplier the next purchase would put the server on. */
    public double nextMultiplier() {
        return Math.pow(2, Math.min(maxLevel, getLevel() + 1));
    }

    public boolean isMaxed() {
        return getLevel() >= maxLevel;
    }

    /**
     * Buys the next step. Fails (returning false) if the ladder is maxed
     * or the buyer can't cover it — the caller reports why.
     */
    public boolean purchase(Player buyer, PlayerData data) {
        expireIfDue();
        if (isMaxed()) return false;

        long cost = nextCost();
        if (!data.spendPoints(cost)) return false;

        level = Math.min(maxLevel, level + 1);
        expiresAtMillis = System.currentTimeMillis() + durationSeconds * 1000L;
        boughtBy = buyer.getName();

        String banner = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ GLOBAL LUCK BOOST ✦";
        String line = ChatColor.WHITE + buyer.getName() + ChatColor.GRAY + " activated "
                + ChatColor.LIGHT_PURPLE + ChatColor.BOLD + formatMultiplier(multiplier())
                + ChatColor.RESET + ChatColor.GRAY + " Luck for everyone, for "
                + ChatColor.WHITE + (durationSeconds / 60) + " minutes" + ChatColor.GRAY + "!";

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage("");
            online.sendMessage(banner);
            online.sendMessage(line);
            online.sendMessage("");
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }
        plugin.getScoreboardManager().update(buyer);
        return true;
    }

    /** Admin override — sets the boost directly, no Credits, no broadcast. */
    public void force(int level, int minutes, String by) {
        this.level = Math.max(0, Math.min(maxLevel, level));
        this.expiresAtMillis = this.level <= 0 ? 0L : System.currentTimeMillis() + minutes * 60_000L;
        this.boughtBy = this.level <= 0 ? null : by;
    }

    /** "4x" rather than "4.0x". */
    public static String formatMultiplier(double multiplier) {
        return (multiplier == Math.floor(multiplier)
                ? String.valueOf((long) multiplier)
                : String.format("%.1f", multiplier)) + "x";
    }
}
