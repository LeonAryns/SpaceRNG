package com.spacerng.solrng.farming;

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
 * The Momentum readout across the top of the screen while a farming run
 * is alive.
 *
 * Momentum is the one farm bonus that can't be bought outright — the
 * enchant only raises the ceiling, and the field is what fills it. That
 * makes it the one number a farmer needs in front of them: a multiplier
 * that quietly builds and then vanishes the moment you stop is cruel if
 * you can't see it.
 *
 * The bar exists only for the duration of a run. It appears on the first
 * harvest and is taken down when the run times out, so it never sits
 * there at 1.00x telling somebody nothing.
 */
public class MomentumBar {

    private final Map<UUID, BossBar> bars = new HashMap<>();

    /**
     * Repaints one player's bar, creating it if the run just started.
     *
     * The fill is the fraction of the CEILING reached rather than of some
     * absolute maximum: a player at level 20 and a player at level 1000
     * should both be able to see themselves getting somewhere.
     */
    public void update(Player player, long crops, double bonus, double cap) {
        if (cap <= 0) {
            hide(player.getUniqueId());
            return;
        }

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        double fraction = Math.max(0.0, Math.min(1.0, bonus / cap));
        bar.setProgress(fraction);
        bar.setColor(fraction >= 1.0 ? BarColor.YELLOW : BarColor.GREEN);
        bar.setTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "MOMENTUM "
                + ChatColor.RESET + ChatColor.WHITE + String.format("%.2f", 1.0 + bonus) + "x"
                + ChatColor.DARK_GRAY + "  │  "
                + ChatColor.GRAY + String.format("%,d", crops) + " crops"
                + ChatColor.DARK_GRAY + "  │  "
                + (fraction >= 1.0
                        ? ChatColor.YELLOW + "at your ceiling"
                        : ChatColor.GRAY + "ceiling " + ChatColor.WHITE
                                + String.format("%.2f", 1.0 + cap) + "x"));
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

    public boolean isShowing(UUID uuid) {
        return bars.containsKey(uuid);
    }
}
