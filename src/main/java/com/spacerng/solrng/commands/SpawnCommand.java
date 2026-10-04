package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /spawn (V343, Leon asked for it).
 *
 * The plugin already knew where spawn was: every join is forced to it and
 * /rngadmin setspawn moves it. There was simply no way for a player to go
 * back there, which matters most inside the Secret Realm and out on the
 * farm, where walking home is not an option.
 *
 * With no spawn set it falls back to the world's own, so the command
 * always does something rather than telling a player off for something an
 * admin has not done.
 */
public class SpawnCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public SpawnCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have a spawn to go to.");
            return true;
        }
        // Inside the Secret Realm this is the way out, and leaving has to
        // go through the realm so the tag, the aura and their own Luck all
        // come back with them.
        if (plugin.getRealmManager() != null && plugin.getRealmManager().inside(player)) {
            plugin.getRealmManager().leave(player);
            return true;
        }
        Location spawn = plugin.getSpawnManager().hasSpawn()
                ? plugin.getSpawnManager().getSpawn()
                : player.getWorld().getSpawnLocation();
        player.teleport(spawn);
        player.sendMessage(ChatColor.GRAY + "Welcome back to spawn.");
        player.playSound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
        return true;
    }
}
