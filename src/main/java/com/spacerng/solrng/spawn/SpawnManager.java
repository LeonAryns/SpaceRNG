package com.spacerng.solrng.spawn;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Persists a single server spawn location (spawn.yml), separate from
 * Minecraft's own world spawn — vanilla /setworldspawn only actually
 * relocates BRAND NEW players and death respawns with no bed/anchor;
 * everyone else just resumes wherever they last logged off. This instead
 * teleports every player here on every join (see JoinQuitListener), so
 * "spawn" always means the same block regardless of where they quit.
 */
public class SpawnManager {

    private final SolRNGPlugin plugin;
    private final File file;
    private Location spawn;

    public SpawnManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            spawn = null;
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String worldName = yml.getString("world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            plugin.getLogger().warning("[SolRNG] Saved spawn world '" + worldName + "' isn't loaded — ignoring spawn.yml until it is.");
            spawn = null;
            return;
        }
        spawn = new Location(world, yml.getDouble("x"), yml.getDouble("y"), yml.getDouble("z"),
                (float) yml.getDouble("yaw"), (float) yml.getDouble("pitch"));
    }

    public void setSpawn(Location location) {
        this.spawn = location.clone();
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("world", location.getWorld().getName());
        yml.set("x", location.getX());
        yml.set("y", location.getY());
        yml.set("z", location.getZ());
        yml.set("yaw", (double) location.getYaw());
        yml.set("pitch", (double) location.getPitch());
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[SolRNG] Failed to save spawn location: " + e.getMessage());
        }
    }

    public boolean hasSpawn() {
        return spawn != null;
    }

    public Location getSpawn() {
        return spawn == null ? null : spawn.clone();
    }
}
