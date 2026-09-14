package com.spacerng.solrng;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Keeps a server's config.yml in step with the jar without anyone deleting
 * the file.
 *
 * Structural sections are rewritten whenever the on-disk
 * {@code config-version} is behind the jar. Everything else on disk is
 * left alone, so a server owner's item table, prices and tweaks are kept
 * but the structural sections catch up automatically. A "structural"
 * section is one the owner has no reason to have touched by hand: the
 * skill tree layout, the shiny base, the perks roll table. If that ever
 * changes, list it here and bump {@code config-version} in config.yml.
 *
 * Patches are for a single value whose default changed in a section that
 * isn't structural. Each is applied once, and only while the server still
 * holds the old default, so a value somebody picked by hand is never
 * touched. Applied patches are remembered under applied-patches, so a
 * one-line change needs no version bump and rewrites no structural
 * section.
 */
public final class ConfigMigrator {

    /**
     * Sections that will be overwritten when the on-disk config version
     * is behind the jar's. Any section not listed here is preserved
     * even across a version bump.
     */
    private static final List<String> STRUCTURAL = List.of(
            "skilltree", "farmtree", "shiny", "perks", "linked-account");

    private record Patch(String id, String path, Object oldDefault, Object newDefault) {
    }

    private static final List<Patch> PATCHES = List.of(
            // V108: Common's label went from grey to white at Leon's request.
            new Patch("common-label-white", "rarities.COMMON.colors", List.of("&7"), List.of("&f")));

    private ConfigMigrator() {
    }

    /**
     * Runs both steps and saves once if either changed anything. Loud in
     * the log, on purpose: a rewrite the owner didn't make by hand is
     * exactly the kind of thing they have to be able to see afterwards.
     */
    public static void run(SolRNGPlugin plugin) {
        FileConfiguration disk = plugin.getConfig();
        boolean changed = migrateStructural(plugin, disk);
        changed |= applyPatches(plugin, disk);
        if (!changed) return;

        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            disk.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Couldn't save the migrated config.yml: " + ex.getMessage());
        }
    }

    private static boolean migrateStructural(SolRNGPlugin plugin, FileConfiguration disk) {
        int diskVersion = disk.getInt("config-version", 1);

        InputStream defaultsStream = plugin.getResource("config.yml");
        if (defaultsStream == null) return false;

        YamlConfiguration defaults;
        // UTF-8 explicitly: the platform default on a Windows host would
        // mangle every star in the sections copied across.
        try (InputStreamReader reader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Couldn't read the packaged config.yml for migration: " + ex.getMessage());
            return false;
        }
        int jarVersion = defaults.getInt("config-version", 1);

        if (diskVersion >= jarVersion) return false;

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
        return true;
    }

    private static boolean applyPatches(SolRNGPlugin plugin, FileConfiguration disk) {
        List<String> applied = new ArrayList<>(disk.getStringList("applied-patches"));
        boolean changed = false;
        for (Patch patch : PATCHES) {
            if (applied.contains(patch.id())) continue;
            if (Objects.equals(disk.get(patch.path()), patch.oldDefault())) {
                disk.set(patch.path(), patch.newDefault());
                plugin.getLogger().info("Config patch " + patch.id() + ": " + patch.path()
                        + " is now " + patch.newDefault());
            }
            // Marked applied either way, so a value chosen by hand is never revisited.
            applied.add(patch.id());
            changed = true;
        }
        if (changed) disk.set("applied-patches", applied);
        return changed;
    }
}
