package com.spacerng.solrng;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Level;

/**
 * Rewrites the sections that changed shape whenever the on-disk
 * {@code config-version} is behind the jar. Everything else on disk is
 * left alone, so a server owner's item table, prices and tweaks are
 * kept but the structural sections catch up automatically.
 *
 * A "structural" section is one the player has no reason to have
 * touched by hand: the skill tree layout, the shiny base, the perks
 * roll table. If that ever changes, list it here and bump
 * {@code config-version} in config.yml.
 */
public final class ConfigMigrator {

    /**
     * Sections that will be overwritten when the on-disk config version
     * is behind the jar's. Any section not listed here is preserved
     * even across a version bump.
     */
    private static final List<String> STRUCTURAL = List.of(
            "skilltree", "farmtree", "shiny", "perks");

    private ConfigMigrator() {
    }

    /**
     * Runs the migration if needed. Loud in the log, on purpose - the
     * one-time overwrite of a structural section is exactly the kind of
     * thing an owner has to be able to see afterwards.
     */
    public static void run(SolRNGPlugin plugin) {
        FileConfiguration disk = plugin.getConfig();
        int diskVersion = disk.getInt("config-version", 1);

        InputStream defaultsStream = plugin.getResource("config.yml");
        if (defaultsStream == null) return;

        YamlConfiguration defaults;
        try (InputStreamReader reader = new InputStreamReader(defaultsStream)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Couldn't read the packaged config.yml for migration: " + ex.getMessage());
            return;
        }
        int jarVersion = defaults.getInt("config-version", 1);

        if (diskVersion >= jarVersion) return;

        plugin.getLogger().info("Config on disk is version " + diskVersion
                + ", jar is version " + jarVersion
                + ". Rewriting structural sections in place.");

        for (String section : STRUCTURAL) {
            if (defaults.contains(section)) {
                disk.set(section, defaults.get(section));
                plugin.getLogger().info(" - refreshed section: " + section);
            }
        }
        disk.set("config-version", jarVersion);

        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            disk.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Couldn't save the migrated config.yml: " + ex.getMessage());
        }
    }
}
