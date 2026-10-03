package com.spacerng.solrng.boost;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Free 2x Luck when enough people are on at once.
 *
 * The paid boost rewards one person for spending; this rewards everybody
 * for turning up, which is the half a store item cannot buy. A player
 * deciding whether to log in now or later has a reason to log in now and
 * a reason to tell somebody else to.
 *
 * The bar moves after every hit. Whatever count set it off becomes the
 * floor for the next one: 30 percent more, or five more, whichever is
 * larger. Without that the first busy evening would hand out a boost
 * every couple of hours forever and it would stop meaning anything.
 *
 * Nothing here is persisted, the same call BoostManager makes about the
 * boost itself. The bar also falls back to its starting value after a
 * quiet stretch, or a server that had one good night would sit behind a
 * bar it never reaches again.
 */
public class CrowdBoostManager {

    private final SolRNGPlugin plugin;

    private boolean enabled = true;
    private int startThreshold = 20;
    private int minStep = 5;
    private double growth = 1.30;
    private int cooldownMinutes = 150;
    private int durationMinutes = 30;
    private int resetHours = 24;
    private int callWithin = 5;
    private int callEveryMinutes = 10;
    private long lastCallAt;

    private int threshold;
    private long readyAt;
    private long lastTriggerAt;
    private BukkitTask task;

    public CrowdBoostManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        enabled = config.getBoolean("boost.crowd.enabled", true);
        startThreshold = Math.max(2, config.getInt("boost.crowd.start-threshold", 20));
        minStep = Math.max(1, config.getInt("boost.crowd.min-step", 5));
        growth = Math.max(1.0, config.getDouble("boost.crowd.growth", 1.30));
        cooldownMinutes = Math.max(1, config.getInt("boost.crowd.cooldown-minutes", 60));
        callWithin = Math.max(0, config.getInt("boost.crowd.call-within", 5));
        callEveryMinutes = Math.max(1, config.getInt("boost.crowd.call-every-minutes", 10));
        durationMinutes = Math.max(1, config.getInt("boost.crowd.duration-minutes", 30));
        resetHours = Math.max(1, config.getInt("boost.crowd.reset-hours", 24));
        if (threshold <= 0) threshold = startThreshold;
    }

    /**
     * Checks once a minute rather than on join.
     *
     * A join hook would miss the case that matters most: the server is
     * already over the bar but a paid boost is running, so the free one
     * has to wait for it to end. On a clock it fires the moment the way
     * is clear, with no join needed to nudge it.
     */
    public void start() {
        stop();
        if (!enabled) return;
        threshold = Math.max(startThreshold, threshold);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 30L, 20L * 60L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();

        // A quiet stretch puts the bar back where it started. The clock
        // runs from the last boost, so a server that is busy every night
        // keeps its raised bar and one that went quiet gets a fresh start.
        if (lastTriggerAt > 0L && now - lastTriggerAt > resetHours * 3_600_000L) {
            threshold = startThreshold;
            lastTriggerAt = 0L;
        }

        if (now < readyAt) return;
        int online = Bukkit.getOnlinePlayers().size();
        if (online < threshold) {
            callForPlayers(online, now);
            return;
        }
        if (!plugin.getBoostManager().grantCrowd(online, durationMinutes)) return;

        // The bar moves off what actually turned up, not off the old bar,
        // so a night that blew past the target sets a target worth having.
        threshold = Math.max(online + minStep, (int) Math.ceil(online * growth));
        // V301: a round number to aim for, 25 rather than 26 (Leon's call).
        threshold = ((threshold + 4) / 5) * 5;
        readyAt = now + cooldownMinutes * 60_000L;
        lastTriggerAt = now;
        plugin.getLogger().info("Crowd boost fired at " + online
                + " players. Next one needs " + threshold + ".");
    }

    /**
     * V301: close to the bar, the server is told how many more it takes,
     * at most once every call-every-minutes, so players bring a friend
     * ("15 online, 5 more for 2x Luck"). Not while any boost is running.
     */
    private void callForPlayers(int online, long now) {
        int short_ = threshold - online;
        if (callWithin <= 0 || short_ > callWithin || online < 1) return;
        if (now - lastCallAt < callEveryMinutes * 60_000L) return;
        if (plugin.getBoostManager().isActive()) return;
        lastCallAt = now;
        String line = org.bukkit.ChatColor.AQUA + "" + org.bukkit.ChatColor.BOLD + "✦ " + online + " online"
                + org.bukkit.ChatColor.RESET + org.bukkit.ChatColor.GRAY + "  " + org.bukkit.ChatColor.WHITE + short_
                + org.bukkit.ChatColor.GRAY + (short_ == 1 ? " more player" : " more players") + " and everyone gets "
                + org.bukkit.ChatColor.AQUA + org.bukkit.ChatColor.BOLD + "2x Luck" + org.bukkit.ChatColor.RESET
                + org.bukkit.ChatColor.GRAY + " for " + durationMinutes + " minutes. Bring a friend!";
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(line);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
        }
    }

    // ---------------------------------------------------------------
    // Readouts, for /boost and the admin command
    // ---------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    /** How many have to be on for the next free boost. */
    public int nextThreshold() {
        return Math.max(startThreshold, threshold);
    }

    /** How many more players are needed right now, 0 when the bar is met. */
    public int playersShort() {
        return Math.max(0, nextThreshold() - Bukkit.getOnlinePlayers().size());
    }

    /** Minutes until the cooldown is up, 0 when it already is. */
    public long minutesLeft() {
        long left = readyAt - System.currentTimeMillis();
        return left <= 0L ? 0L : (left + 59_999L) / 60_000L;
    }

    /** Puts the bar back to its starting value, for /rngadmin. */
    public void reset() {
        threshold = startThreshold;
        readyAt = 0L;
        lastTriggerAt = 0L;
    }
}
