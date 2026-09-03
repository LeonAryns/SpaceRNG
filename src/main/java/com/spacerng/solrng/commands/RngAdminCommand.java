package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RngAdminCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public RngAdminCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("solrng.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin reload");
            return true;
        }

        plugin.reloadAll();
        sender.sendMessage(ChatColor.GREEN + "SolRNG config reloaded.");
        return true;
    }
}
