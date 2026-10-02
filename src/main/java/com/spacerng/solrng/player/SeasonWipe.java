package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.roll.RollItemFactory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The inventories a season reset could not reach (V247).
 *
 * /rngadmin season reset wipes an online player on the spot, but an
 * offline player's inventory lives in the world's own player file, which
 * the server only lets go of while they are online. So their ids are kept
 * in season-wipe.yml and the first join after the reset empties their
 * inventory and ender chest and hands them a Roll item, the same as a
 * brand new player gets.
 */
public final class SeasonWipe {

    private SeasonWipe() {
    }

    private static File file(SolRNGPlugin plugin) {
        return new File(plugin.getDataFolder(), "season-wipe.yml");
    }

    /** Remembers these players for a wipe on their next join. */
    public static void queue(SolRNGPlugin plugin, Collection<UUID> players) {
        Set<String> ids = new LinkedHashSet<>(read(plugin));
        for (UUID uuid : players) ids.add(uuid.toString());
        write(plugin, new ArrayList<>(ids));
    }

    /** Called on join: empties the inventory if this player is still owed a wipe. */
    public static void onJoin(SolRNGPlugin plugin, Player player) {
        if (!file(plugin).exists()) return;
        List<String> ids = read(plugin);
        if (!ids.remove(player.getUniqueId().toString())) return;
        write(plugin, ids);
        player.getInventory().clear();
        player.getEnderChest().clear();
        try {
            player.setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, 0);
        } catch (Exception ignored) {
            // Some setups refuse statistic writes; not worth failing over.
        }
        Stash.give(plugin, player, RollItemFactory.create(plugin, 1));
    }

    private static List<String> read(SolRNGPlugin plugin) {
        File file = file(plugin);
        if (!file.exists()) return new ArrayList<>();
        return new ArrayList<>(YamlConfiguration.loadConfiguration(file).getStringList("pending"));
    }

    private static void write(SolRNGPlugin plugin, List<String> ids) {
        File file = file(plugin);
        if (ids.isEmpty()) {
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("Couldn't delete season-wipe.yml");
            }
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("pending", ids);
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save season-wipe.yml: " + ex.getMessage());
        }
    }
}
