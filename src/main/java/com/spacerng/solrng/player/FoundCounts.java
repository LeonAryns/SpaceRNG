package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How many players have found each drop, for the "Found by" line in
 * /index. Counted once per player, the first time a drop enters their
 * index, and kept in found.yml.
 *
 * The first start without found.yml counts every player file once, off
 * the main thread, so a server with thousands of players doesn't stall.
 * Saved every five minutes and on shutdown rather than on every find.
 */
public final class FoundCounts {

    private final SolRNGPlugin plugin;
    private final File file;
    private final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public FoundCounts(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "found.yml");
    }

    public void load() {
        counts.clear();
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) counts.put(key, yml.getInt(key));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::countPlayerFiles);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);
    }

    private void countPlayerFiles() {
        Map<String, Integer> scanned = new HashMap<>();
        File[] files = new File(plugin.getDataFolder(), "playerdata").listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File player : files) {
                for (String name : YamlConfiguration.loadConfiguration(player).getStringList("discovered-items")) {
                    scanned.merge(name, 1, Integer::sum);
                }
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            // A find recorded while the scan ran is already in counts; keep the larger.
            scanned.forEach((name, count) -> counts.merge(name, count, Math::max));
            dirty = true;
            save();
            plugin.getLogger().info("Counted who found what across " + (files == null ? 0 : files.length)
                    + " player files for /index.");
        });
    }

    /** A player found this drop for the first time. */
    public void record(String itemName) {
        counts.merge(itemName, 1, Integer::sum);
        dirty = true;
    }

    public int count(String itemName) {
        return counts.getOrDefault(itemName, 0);
    }

    public void save() {
        if (!dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        counts.forEach(yml::set);
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save found.yml: " + ex.getMessage());
        }
    }
}
