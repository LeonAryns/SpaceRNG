package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /nick for the ranks that carry it. The nick shows in tab and in chat, not
 * on the nametag over the head, so nobody can hide who they are in game.
 */
public class NickCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public NickCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can set a nick.");
            return true;
        }
        if (!plugin.getRankManager().nickCommand()) return false;
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!plugin.getRankManager().has(data, "nick") && !player.hasPermission("solrng.admin")) {
            player.sendMessage(ChatColor.RED + "Nova and Supernova can set a nick. See /ranks.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /nick <name|off>");
            return true;
        }
        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("reset")) {
            data.setNick(null);
            plugin.getRankManager().refreshName(player);
            player.sendMessage(ChatColor.GRAY + "Your nick is off.");
            return true;
        }
        String wanted = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', args[0]));
        if (wanted.length() < 3 || wanted.length() > 16 || !wanted.matches("[A-Za-z0-9_]+")) {
            player.sendMessage(ChatColor.RED + "A nick is 3 to 16 letters, numbers or underscores.");
            return true;
        }
        data.setNick(wanted);
        plugin.getRankManager().refreshName(player);
        player.sendMessage(ChatColor.GREEN + "You are now " + ChatColor.WHITE + wanted + ChatColor.GREEN + ".");
        return true;
    }
}
