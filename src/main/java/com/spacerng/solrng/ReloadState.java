package com.spacerng.solrng;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a /plugman reload would otherwise forget (V395, Leon): the global
 * Luck boost (store and crowd), the crowd boost's timer, and every
 * player's Momentum. Written to reload-state.yml on disable and read back,
 * then deleted, on enable.
 *
 * Personal potions and everything else on a player already live in the
 * player save, which onDisable writes anyway.
 *
 * A file older than {@link #MAX_AGE_MILLIS} is ignored: that was a real
 * restart, not a reload, and a Momentum run from yesterday is not one to
 * resume.
 */
public final class ReloadState {

    private static final long MAX_AGE_MILLIS = 10 * 60 * 1000L;

    private ReloadState() {
    }

    private static File file(SolRNGPlugin plugin) {
        return new File(plugin.getDataFolder(), "reload-state.yml");
    }

    public static void save(SolRNGPlugin plugin) {
        YamlConfiguration yml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        yml.set("saved-at", now);

        var boost = plugin.getBoostManager();
        if (boost != null && boost.isActive()) {
            yml.set("boost.level", boost.getLevel());
            yml.set("boost.expires-at", boost.expiresAt());
            yml.set("boost.bought-by", boost.getBoughtBy());
        }
        var crowd = plugin.getCrowdBoostManager();
        if (crowd != null) {
            long[] state = crowd.state();
            yml.set("crowd.threshold", state[0]);
            yml.set("crowd.ready-at", state[1]);
            yml.set("crowd.last-trigger-at", state[2]);
        }
        var farm = plugin.getFarmPlotManager();
        if (farm != null) {
            // How long each run had been idle when the plugin went down,
            // counted to the freeze if there was one, since nobody could
            // farm while frozen.
            long until = plugin.frozenSince() > 0 ? plugin.frozenSince() : now;
            farm.momentumState().forEach((uuid, state) -> {
                String path = "momentum." + uuid + ".";
                yml.set(path + "crops", state[0]);
                yml.set(path + "idle", Math.max(0L, until - state[1]));
                yml.set(path + "peak", state[2]);
            });
        }
        try {
            yml.save(file(plugin));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write reload-state.yml: " + ex.getMessage());
        }
    }

    public static void restore(SolRNGPlugin plugin) {
        File file = file(plugin);
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        long savedAt = yml.getLong("saved-at", 0L);
        try {
            if (now - savedAt > MAX_AGE_MILLIS) return;
            // The time the plugin was down is added to a boost, so a
            // reload never eats into what somebody paid for.
            long downtime = Math.max(0L, now - savedAt);
            if (yml.contains("boost.level")) {
                plugin.getBoostManager().restore(yml.getInt("boost.level"),
                        yml.getLong("boost.expires-at") + downtime, yml.getString("boost.bought-by"));
            }
            if (yml.contains("crowd.threshold")) {
                plugin.getCrowdBoostManager().restore(yml.getLong("crowd.threshold"),
                        yml.getLong("crowd.ready-at"), yml.getLong("crowd.last-trigger-at"));
            }
            ConfigurationSection momentum = yml.getConfigurationSection("momentum");
            if (momentum != null) {
                Map<UUID, long[]> runs = new HashMap<>();
                for (String key : momentum.getKeys(false)) {
                    try {
                        runs.put(UUID.fromString(key), new long[]{
                                momentum.getLong(key + ".crops"),
                                now - momentum.getLong(key + ".idle"),
                                momentum.getLong(key + ".peak")});
                    } catch (IllegalArgumentException ignored) {
                        // Not a player id; skip it.
                    }
                }
                plugin.getFarmPlotManager().restoreMomentum(runs);
            }
            plugin.getLogger().info("Reload state restored: boosts and Momentum carried over.");
        } finally {
            if (!file.delete()) file.deleteOnExit();
        }
    }
}
