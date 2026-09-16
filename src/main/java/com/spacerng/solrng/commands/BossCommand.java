package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /boss: what is up, how much of it is left, and what you have taken off
 * it yourself. The boss bar carries the same numbers, but the bar cannot
 * show the standings and a player who just logged in wants to know
 * whether it is worth running to the farm.
 */
public class BossCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public BossCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can see the boss.");
            return true;
        }
        for (String line : plugin.getBossManager().status(player)) {
            player.sendMessage(line);
        }
        return true;
    }
}
