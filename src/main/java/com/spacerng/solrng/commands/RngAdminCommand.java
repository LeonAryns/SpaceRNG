package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin <reload|tokens|setspawn>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(ChatColor.GREEN + "SolRNG config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("setspawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can set spawn.");
                return true;
            }
            plugin.getSpawnManager().setSpawn(player.getLocation());
            sender.sendMessage(ChatColor.GREEN + "Spawn set to your current location. Every player will now teleport here on join.");
            return true;
        }

        if (args[0].equalsIgnoreCase("tokens")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /rngadmin tokens <player> <amount>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or offline.");
                return true;
            }
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                return true;
            }
            PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
            data.addTokens(amount);
            plugin.getScoreboardManager().update(target);
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " Tokens to " + target.getName());
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /rngadmin <reload|tokens|setspawn>");
        return true;
    }
}
