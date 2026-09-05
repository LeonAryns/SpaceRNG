package com.spacerng.solrng.boost;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Luck readout across the top of the screen. One bar per player,
 * refreshed on a timer.
 *
 * Luck has no ceiling, so the bar can't represent it as a fraction of
 * anything. Instead the fill tracks the global boost's remaining time when
 * one is running — the only part of Luck that's actually a countdown — and
 * sits full otherwise. The number itself lives in the title.
 */
public class LuckBarManager {

    private final SolRNGPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public LuckBarManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        update(player);
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

    public void update(Player player) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        double luck = plugin.getPrestigeManager().effectiveLuck(data);
        BoostManager boost = plugin.getBoostManager();

        StringBuilder title = new StringBuilder();
        title.append(ChatColor.LIGHT_PURPLE).append(ChatColor.BOLD).append("✦ ")
                .append(ChatColor.WHITE).append("Luck ")
                .append(ChatColor.GREEN).append("+").append(String.format("%.2f", luck * 100.0)).append("%");

        if (boost.isActive()) {
            title.append(ChatColor.GRAY).append("  |  ")
                    .append(ChatColor.LIGHT_PURPLE).append(ChatColor.BOLD)
                    .append(BoostManager.formatMultiplier(boost.multiplier()))
                    .append(ChatColor.RESET).append(ChatColor.GRAY).append(" boost ")
                    .append(ChatColor.WHITE).append(boost.timeLeftText());
            bar.setColor(BarColor.PURPLE);
            // Drains over the boost's own window, so the bar is a timer
            // rather than a meaningless full line.
            double total = Math.max(1.0, plugin.getConfig().getInt("boost.duration-seconds", 900));
            bar.setProgress(Math.max(0.0, Math.min(1.0, boost.secondsLeft() / total)));
        } else {
            bar.setColor(BarColor.BLUE);
            bar.setProgress(1.0);
        }

        bar.setTitle(title.toString());
        bar.setVisible(true);
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player);
        }
    }
}
